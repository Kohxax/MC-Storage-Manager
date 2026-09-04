package dev.bokukoha.mcstoragemanager.platform.region;

/** Validated registration limits loaded from the plugin configuration. */
public record RegionRegistrationLimits(
        long maxVolume,
        long maxEdgeLength,
        int maxContainersPerRegion,
        int maxRegionsPerPlayer,
        int blocksPerTick
) {
    public RegionRegistrationLimits {
        if (maxVolume < 1 || maxEdgeLength < 1 || maxContainersPerRegion < 1
                || maxRegionsPerPlayer < 1 || blocksPerTick < 1) {
            throw new IllegalArgumentException("Registration limits must be positive");
        }
    }
}
