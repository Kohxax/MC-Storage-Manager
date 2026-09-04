package dev.bokukoha.mcstoragemanager.core.region;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Port for region persistence. Implementations belong in the platform layer. */
public interface RegionStore {
    Collection<RegisteredRegionData> loadAll();

    void save(RegisteredRegionData region);

    void delete(UUID regionId);

    default Optional<RegisteredRegionData> findById(UUID regionId) {
        return loadAll().stream().filter(region -> region.id().equals(regionId)).findFirst();
    }
}
