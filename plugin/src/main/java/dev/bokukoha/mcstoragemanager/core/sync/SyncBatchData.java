package dev.bokukoha.mcstoragemanager.core.sync;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistence DTO for a batch that has not been acknowledged yet. */
public record SyncBatchData(
        UUID id,
        long revision,
        List<ContainerSnapshotData> containers,
        int failureCount,
        Instant nextAttemptAt
) {
    public SyncBatchData {
        Objects.requireNonNull(id, "id");
        containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    public SyncBatch toBatch() {
        return new SyncBatch(id, revision, containers.stream().map(ContainerSnapshotData::toSnapshot).toList(),
                failureCount, nextAttemptAt);
    }

    public static SyncBatchData from(SyncBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return new SyncBatchData(batch.id(), batch.revision(),
                batch.containers().stream().map(ContainerSnapshotData::from).toList(), batch.failureCount(),
                batch.nextAttemptAt());
    }
}
