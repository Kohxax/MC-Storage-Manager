package dev.bokukoha.mcstoragemanager.core.sync;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** API-independent persistence DTO for a container snapshot. */
public record ContainerSnapshotData(
        UUID regionId,
        int x,
        int y,
        int z,
        String containerType,
        List<ItemAmountData> items,
        Instant observedAt,
        boolean deleted
) {
    public ContainerSnapshotData {
        Objects.requireNonNull(regionId, "regionId");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(observedAt, "observedAt");
    }

    public ContainerSnapshotData(
            UUID regionId, int x, int y, int z, String containerType, List<ItemAmountData> items, Instant observedAt) {
        this(regionId, x, y, z, containerType, items, observedAt, false);
    }

    public ContainerSnapshot toSnapshot() {
        return new ContainerSnapshot(new ContainerId(regionId, new BlockPosition(x, y, z)), containerType,
                items.stream().map(ItemAmountData::toItemAmount).toList(), observedAt, deleted);
    }

    public static ContainerSnapshotData from(ContainerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new ContainerSnapshotData(snapshot.containerId().regionId(), snapshot.containerId().position().x(),
                snapshot.containerId().position().y(), snapshot.containerId().position().z(), snapshot.containerType(),
                snapshot.items().stream().map(ItemAmountData::from).toList(), snapshot.observedAt(), snapshot.deleted());
    }
}
