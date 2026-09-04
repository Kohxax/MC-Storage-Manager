package dev.bokukoha.mcstoragemanager.core.region;

import java.util.Objects;

/** The existing region that shares at least one block with a registration candidate. */
public record RegionConflict(RegisteredRegion existing) {
    public RegionConflict {
        Objects.requireNonNull(existing, "existing");
    }
}
