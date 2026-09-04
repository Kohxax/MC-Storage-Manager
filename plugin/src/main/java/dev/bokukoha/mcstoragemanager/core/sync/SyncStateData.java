package dev.bokukoha.mcstoragemanager.core.sync;

import java.util.List;
import java.util.Objects;

/** Complete durable state required to resume outbound synchronization after a restart. */
public record SyncStateData(
        long nextRevision,
        List<ContainerSnapshotData> dirtyContainers,
        List<SyncBatchData> pendingBatches
) {
    public SyncStateData {
        if (nextRevision < 0) {
            throw new IllegalArgumentException("nextRevision must not be negative");
        }
        dirtyContainers = List.copyOf(Objects.requireNonNull(dirtyContainers, "dirtyContainers"));
        pendingBatches = List.copyOf(Objects.requireNonNull(pendingBatches, "pendingBatches"));
    }

    public static SyncStateData empty() {
        return new SyncStateData(0, List.of(), List.of());
    }
}
