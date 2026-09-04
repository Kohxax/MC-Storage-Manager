package dev.bokukoha.mcstoragemanager.core.region;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** API-independent persistence DTO; adapters may map it to YAML, SQL, or another format. */
public record RegisteredRegionData(
        UUID id,
        String name,
        UUID ownerId,
        UUID worldUuid,
        String worldName,
        String dimension,
        Cuboid cuboid,
        List<BlockPosition> containers,
        Instant createdAt
) {
    public RegisteredRegionData {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(worldUuid, "worldUuid");
        Objects.requireNonNull(cuboid, "cuboid");
        Objects.requireNonNull(createdAt, "createdAt");
        containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
    }
}
