package dev.bokukoha.mcstoragemanager.core.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ContainerSyncServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final UUID REGION = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FIRST_BATCH = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_BATCH = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void itemAmountRequiresNamespacedKeyAndPreservesVariantAsImmutableExtensionData() {
        ItemAmount item = new ItemAmount("minecraft:diamond", 2, Map.of("custom_model_data", "12"));

        assertEquals("12", item.variant().get("custom_model_data"));
        assertThrows(UnsupportedOperationException.class, () -> item.variant().put("other", "value"));
        assertThrows(IllegalArgumentException.class, () -> new ItemAmount("diamond", 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemAmount("minecraft:diamond", 0, Map.of()));
    }

    @Test
    void latestDirtySnapshotReplacesEarlierSnapshotForTheSameContainer() {
        MemoryStore store = new MemoryStore();
        ContainerSyncService service = service(2, store, ids(FIRST_BATCH));
        ContainerId id = containerId(0);

        service.markDirty(snapshot(id, "minecraft:cobblestone", 1));
        service.markDirty(snapshot(id, "minecraft:diamond", 3));
        SyncBatch batch = service.nextBatch(NOW).orElseThrow();

        assertEquals(1, batch.containers().size());
        assertEquals("minecraft:diamond", batch.containers().getFirst().items().getFirst().itemKey());
        assertEquals(3, batch.containers().getFirst().items().getFirst().amount());
        assertEquals(0, service.dirtyCount());
    }

    @Test
    void createsBatchesAtTheConfiguredUpperLimitWithMonotonicRevisions() {
        ContainerSyncService service = service(2, new MemoryStore(), ids(FIRST_BATCH, SECOND_BATCH));
        service.markDirty(snapshot(containerId(0), "minecraft:stone", 1));
        service.markDirty(snapshot(containerId(1), "minecraft:dirt", 1));
        service.markDirty(snapshot(containerId(2), "minecraft:grass_block", 1));

        SyncBatch first = service.nextBatch(NOW).orElseThrow();
        assertEquals(FIRST_BATCH, first.id());
        assertEquals(0, first.revision());
        assertEquals(2, first.containers().size());
        assertTrue(service.acknowledge(first.id()));

        SyncBatch second = service.nextBatch(NOW).orElseThrow();
        assertEquals(SECOND_BATCH, second.id());
        assertEquals(1, second.revision());
        assertEquals(1, second.containers().size());
    }

    @Test
    void failureKeepsIdAndRevisionAndAppliesExponentialBackoffBeforeNewerWork() {
        ContainerSyncService service = service(10, new MemoryStore(), ids(FIRST_BATCH));
        service.markDirty(snapshot(containerId(0), "minecraft:stone", 1));
        SyncBatch original = service.nextBatch(NOW).orElseThrow();

        assertTrue(service.recordFailure(original.id(), NOW));
        service.markDirty(snapshot(containerId(1), "minecraft:diamond", 1));
        assertTrue(service.nextBatch(NOW.plusSeconds(1)).isEmpty());

        SyncBatch retried = service.nextBatch(NOW.plusSeconds(2)).orElseThrow();
        assertEquals(original.id(), retried.id());
        assertEquals(original.revision(), retried.revision());
        assertEquals(1, retried.failureCount());
        assertEquals(NOW.plusSeconds(2), retried.nextAttemptAt());
    }

    @Test
    void failedRegionDoesNotStarveDirtyWorkFromAnotherRegion() {
        ContainerSyncService service = service(10, new MemoryStore(), ids(FIRST_BATCH, SECOND_BATCH));
        service.markDirty(snapshot(containerId(0), "minecraft:stone", 1));
        SyncBatch failed = service.nextBatch(NOW).orElseThrow();
        assertTrue(service.recordFailure(failed.id(), NOW));

        UUID otherRegionId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        ContainerId otherContainer = new ContainerId(otherRegionId, new BlockPosition(0, 64, 0));
        service.markDirty(snapshot(otherContainer, "minecraft:dirt", 1));

        SyncBatch other = service.nextBatch(NOW.plusSeconds(1)).orElseThrow();
        assertEquals(otherRegionId, other.containers().getFirst().containerId().regionId());
        assertEquals(SECOND_BATCH, other.id());
    }

    @Test
    void newerDirtySnapshotWaitsForOlderPendingBatchFromTheSameRegion() {
        ContainerSyncService service = service(10, new MemoryStore(), ids(FIRST_BATCH, SECOND_BATCH));
        ContainerId id = containerId(0);
        service.markDirty(snapshot(id, "minecraft:stone", 1));
        SyncBatch first = service.nextBatch(NOW).orElseThrow();
        assertTrue(service.recordFailure(first.id(), NOW));
        service.markDirty(snapshot(id, "minecraft:diamond", 2));

        assertTrue(service.nextBatch(NOW.plusSeconds(1)).isEmpty());
        assertEquals(first.id(), service.nextBatch(NOW.plusSeconds(2)).orElseThrow().id());
    }

    @Test
    void acknowledgedBatchIsRemovedAndRepeatedAcknowledgementIsIdempotent() {
        ContainerSyncService service = service(10, new MemoryStore(), ids(FIRST_BATCH));
        service.markDirty(snapshot(containerId(0), "minecraft:stone", 1));
        SyncBatch batch = service.nextBatch(NOW).orElseThrow();

        assertTrue(service.acknowledge(batch.id()));
        assertFalse(service.acknowledge(batch.id()));
        assertTrue(service.pendingBatches().isEmpty());
    }

    @Test
    void stateRoundTripResumesDirtyQueueAndUnacknowledgedBatchWithoutChangingIdOrRevision() {
        MemoryStore store = new MemoryStore();
        ContainerSyncService original = service(1, store, ids(FIRST_BATCH));
        original.markDirty(snapshot(containerId(0), "minecraft:stone", 1));
        SyncBatch pending = original.nextBatch(NOW).orElseThrow();
        original.markDirty(snapshot(containerId(1), "minecraft:diamond", 2));
        original.recordFailure(pending.id(), NOW);

        ContainerSyncService restored = service(1, store, ids(SECOND_BATCH));
        assertEquals(1, restored.dirtyCount());
        SyncBatch retry = restored.nextBatch(NOW.plusSeconds(2)).orElseThrow();
        assertEquals(pending.id(), retry.id());
        assertEquals(pending.revision(), retry.revision());
        assertTrue(restored.acknowledge(retry.id()));

        SyncBatch newBatch = restored.nextBatch(NOW.plusSeconds(2)).orElseThrow();
        assertEquals(SECOND_BATCH, newBatch.id());
        assertEquals(1, newBatch.revision());
        assertEquals("minecraft:diamond", newBatch.containers().getFirst().items().getFirst().itemKey());
    }

    @Test
    void rejectsCorruptPersistedStateThatCouldReuseAPendingRevision() {
        ContainerSnapshotData snapshot = ContainerSnapshotData.from(snapshot(containerId(0), "minecraft:stone", 1));
        SyncStateData corrupt = new SyncStateData(4, List.of(),
                List.of(new SyncBatchData(FIRST_BATCH, 4, List.of(snapshot), 0, NOW)));
        MemoryStore store = new MemoryStore();
        store.state = Optional.of(corrupt);

        assertThrows(IllegalArgumentException.class, () -> service(1, store, ids(SECOND_BATCH)));
    }

    @Test
    void backoffDoublesAndSaturatesAtConfiguredMaximum() {
        RetryBackoff backoff = new RetryBackoff(Duration.ofSeconds(2), Duration.ofSeconds(5));

        assertEquals(Duration.ofSeconds(2), backoff.delayAfterFailure(1));
        assertEquals(Duration.ofSeconds(4), backoff.delayAfterFailure(2));
        assertEquals(Duration.ofSeconds(5), backoff.delayAfterFailure(3));
        assertEquals(Duration.ofSeconds(5), backoff.delayAfterFailure(100));
    }

    @Test
    void snapshotDataRoundTripIncludesAnEmptyContainer() {
        ContainerSnapshot empty = new ContainerSnapshot(containerId(0), "minecraft:barrel", List.of(), NOW);
        assertEquals(empty, ContainerSnapshotData.from(empty).toSnapshot());
    }

    @Test
    void deletionSnapshotRoundTripsAndCannotContainItems() {
        ContainerSnapshot deleted = new ContainerSnapshot(containerId(0), "minecraft:chest", List.of(), NOW, true);

        assertTrue(ContainerSnapshotData.from(deleted).toSnapshot().deleted());
        assertThrows(IllegalArgumentException.class, () -> new ContainerSnapshot(containerId(0), "minecraft:chest",
                List.of(new ItemAmount("minecraft:stone", 1, Map.of())), NOW, true));
    }

    @Test
    void batchesDoNotMixRegionsBecauseTheRemoteContractHasOneRegionId() {
        ContainerSyncService service = service(10, new MemoryStore(), ids(FIRST_BATCH, SECOND_BATCH));
        ContainerId first = containerId(0);
        ContainerId otherRegion = new ContainerId(UUID.fromString("10000000-0000-0000-0000-000000000002"),
                new BlockPosition(1, 64, 0));
        service.markDirty(snapshot(first, "minecraft:stone", 1));
        service.markDirty(snapshot(otherRegion, "minecraft:dirt", 1));

        SyncBatch firstBatch = service.nextBatch(NOW).orElseThrow();
        assertEquals(List.of(REGION), firstBatch.containers().stream().map(value -> value.containerId().regionId()).toList());
        service.acknowledge(firstBatch.id());
        assertEquals(otherRegion.regionId(), service.nextBatch(NOW).orElseThrow().containers().getFirst().containerId().regionId());
    }

    private static ContainerSyncService service(int maximumBatchSize, MemoryStore store, Supplier<UUID> ids) {
        return new ContainerSyncService(maximumBatchSize, new RetryBackoff(Duration.ofSeconds(2), Duration.ofSeconds(30)),
                store, ids);
    }

    private static Supplier<UUID> ids(UUID... ids) {
        ArrayDeque<UUID> queue = new ArrayDeque<>(List.of(ids));
        return queue::removeFirst;
    }

    private static ContainerId containerId(int x) {
        return new ContainerId(REGION, new BlockPosition(x, 64, 0));
    }

    private static ContainerSnapshot snapshot(ContainerId id, String key, long amount) {
        return new ContainerSnapshot(id, "minecraft:chest", List.of(new ItemAmount(key, amount, Map.of())), NOW);
    }

    private static final class MemoryStore implements SyncStateStore {
        private Optional<SyncStateData> state = Optional.empty();

        @Override
        public Optional<SyncStateData> load() {
            return state;
        }

        @Override
        public void save(SyncStateData state) {
            this.state = Optional.of(state);
        }
    }
}
