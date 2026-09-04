package dev.bokukoha.mcstoragemanager.core.sync;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A durable request payload. Its id and revision stay unchanged across retries. */
public record SyncBatch(
        UUID id,
        long revision,
        List<ContainerSnapshot> containers,
        int failureCount,
        Instant nextAttemptAt
) {
    public SyncBatch {
        Objects.requireNonNull(id, "id");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
        if (containers.isEmpty()) {
            throw new IllegalArgumentException("containers must not be empty");
        }
        if (containers.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("containers must not contain null");
        }
        Set<ContainerId> ids = new HashSet<>();
        for (ContainerSnapshot container : containers) {
            if (!ids.add(container.containerId())) {
                throw new IllegalArgumentException("containers must not contain a container twice");
            }
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount must not be negative");
        }
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    public boolean isReadyAt(Instant now) {
        return !nextAttemptAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    public SyncBatch afterFailure(Instant failedAt, RetryBackoff backoff) {
        Objects.requireNonNull(failedAt, "failedAt");
        Objects.requireNonNull(backoff, "backoff");
        int nextFailureCount = Math.addExact(failureCount, 1);
        return new SyncBatch(id, revision, containers, nextFailureCount,
                failedAt.plus(backoff.delayAfterFailure(nextFailureCount)));
    }
}
