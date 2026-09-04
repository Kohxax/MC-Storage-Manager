package dev.bokukoha.mcstoragemanager.core.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Coordinates durable, at-least-once delivery of complete container snapshots.
 * Transport adapters send a returned {@link SyncBatch}, then call acknowledge or recordFailure.
 */
public final class ContainerSyncService {
    private final int maximumBatchSize;
    private final RetryBackoff retryBackoff;
    private final SyncStateStore stateStore;
    private final Supplier<UUID> batchIdSupplier;
    private final Map<ContainerId, ContainerSnapshot> dirtyContainers = new LinkedHashMap<>();
    private final Map<UUID, SyncBatch> pendingBatches = new LinkedHashMap<>();
    private long nextRevision;

    public ContainerSyncService(int maximumBatchSize, RetryBackoff retryBackoff, SyncStateStore stateStore) {
        this(maximumBatchSize, retryBackoff, stateStore, UUID::randomUUID);
    }

    public ContainerSyncService(int maximumBatchSize, RetryBackoff retryBackoff, SyncStateStore stateStore,
                                Supplier<UUID> batchIdSupplier) {
        if (maximumBatchSize <= 0) {
            throw new IllegalArgumentException("maximumBatchSize must be positive");
        }
        this.maximumBatchSize = maximumBatchSize;
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.batchIdSupplier = Objects.requireNonNull(batchIdSupplier, "batchIdSupplier");
        restore(stateStore.load().orElseGet(SyncStateData::empty));
    }

    /** Replaces any unsent update for this container with its newest complete snapshot. */
    public synchronized void markDirty(ContainerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        dirtyContainers.remove(snapshot.containerId());
        dirtyContainers.put(snapshot.containerId(), snapshot);
        persist();
    }

    /**
     * Returns the oldest retryable pending batch, or converts up to the configured limit of dirty
     * snapshots into a new durable batch. Calling this method does not mark the batch as sent.
     */
    public synchronized Optional<SyncBatch> nextBatch(Instant now) {
        Objects.requireNonNull(now, "now");
        Optional<SyncBatch> oldestPending = pendingBatches.values().stream()
                .min(Comparator.comparingLong(SyncBatch::revision));
        if (oldestPending.isPresent()) {
            SyncBatch batch = oldestPending.get();
            return batch.isReadyAt(now) ? Optional.of(batch) : Optional.empty();
        }
        if (dirtyContainers.isEmpty()) {
            return Optional.empty();
        }

        List<ContainerSnapshot> snapshots = new ArrayList<>(maximumBatchSize);
        UUID regionId = dirtyContainers.values().iterator().next().containerId().regionId();
        var iterator = dirtyContainers.entrySet().iterator();
        while (iterator.hasNext() && snapshots.size() < maximumBatchSize) {
            ContainerSnapshot snapshot = iterator.next().getValue();
            // The HTTP contract has one regionId per request. Keeping a durable batch scoped to
            // one region also makes retries unambiguous when a region is later removed remotely.
            if (snapshot.containerId().regionId().equals(regionId)) {
                snapshots.add(snapshot);
                iterator.remove();
            }
        }
        SyncBatch batch = new SyncBatch(nextBatchId(), nextRevision++, snapshots, 0, now);
        pendingBatches.put(batch.id(), batch);
        persist();
        return Optional.of(batch);
    }

    /** Marks a batch delivered. Repeating the acknowledgement is harmless. */
    public synchronized boolean acknowledge(UUID batchId) {
        Objects.requireNonNull(batchId, "batchId");
        if (pendingBatches.remove(batchId) == null) {
            return false;
        }
        persist();
        return true;
    }

    /** Schedules a retry using the configured bounded exponential backoff. */
    public synchronized boolean recordFailure(UUID batchId, Instant failedAt) {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(failedAt, "failedAt");
        SyncBatch previous = pendingBatches.get(batchId);
        if (previous == null) {
            return false;
        }
        pendingBatches.put(batchId, previous.afterFailure(failedAt, retryBackoff));
        persist();
        return true;
    }

    public synchronized int dirtyCount() {
        return dirtyContainers.size();
    }

    public synchronized List<SyncBatch> pendingBatches() {
        return pendingBatches.values().stream().sorted(Comparator.comparingLong(SyncBatch::revision)).toList();
    }

    public synchronized SyncStateData stateData() {
        return new SyncStateData(nextRevision,
                dirtyContainers.values().stream().map(ContainerSnapshotData::from).toList(),
                pendingBatches().stream().map(SyncBatchData::from).toList());
    }

    private UUID nextBatchId() {
        UUID batchId = Objects.requireNonNull(batchIdSupplier.get(), "batchIdSupplier result");
        if (pendingBatches.containsKey(batchId)) {
            throw new IllegalStateException("batchIdSupplier produced a pending batch id");
        }
        return batchId;
    }

    private void restore(SyncStateData state) {
        long greatestRevision = -1;
        for (ContainerSnapshotData data : state.dirtyContainers()) {
            ContainerSnapshot snapshot = data.toSnapshot();
            if (dirtyContainers.putIfAbsent(snapshot.containerId(), snapshot) != null) {
                throw new IllegalArgumentException("persisted dirty queue contains duplicate containers");
            }
        }
        for (SyncBatchData data : state.pendingBatches()) {
            SyncBatch batch = data.toBatch();
            if (pendingBatches.putIfAbsent(batch.id(), batch) != null) {
                throw new IllegalArgumentException("persisted state contains duplicate batch ids");
            }
            greatestRevision = Math.max(greatestRevision, batch.revision());
        }
        if (state.nextRevision() <= greatestRevision) {
            throw new IllegalArgumentException("nextRevision must be greater than every pending batch revision");
        }
        nextRevision = state.nextRevision();
    }

    private void persist() {
        stateStore.save(stateData());
    }
}
