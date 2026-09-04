package dev.bokukoha.mcstoragemanager.core.sync;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import java.util.Objects;
import java.util.UUID;

/** Stable identity of a scanned container within a registered region. */
public record ContainerId(UUID regionId, BlockPosition position) {
    public ContainerId {
        Objects.requireNonNull(regionId, "regionId");
        Objects.requireNonNull(position, "position");
    }
}
