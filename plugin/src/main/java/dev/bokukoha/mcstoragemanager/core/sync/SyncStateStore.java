package dev.bokukoha.mcstoragemanager.core.sync;

import java.util.Optional;

/** Port for atomically persisting the queue and unacknowledged batches. */
public interface SyncStateStore {
    Optional<SyncStateData> load();

    void save(SyncStateData state);
}
