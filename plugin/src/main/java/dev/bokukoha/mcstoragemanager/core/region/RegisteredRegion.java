package dev.bokukoha.mcstoragemanager.core.region;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A complete storage-region aggregate. */
public record RegisteredRegion(
        UUID id,
        String name,
        UUID ownerId,
        WorldIdentity world,
        Cuboid cuboid,
        List<BlockPosition> containers,
        Instant createdAt
) {
    public RegisteredRegion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(cuboid, "cuboid");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
        if (new HashSet<>(containers).size() != containers.size()) {
            throw new IllegalArgumentException("containers must not contain duplicates");
        }
        if (containers.stream().anyMatch(position -> !cuboid.contains(position))) {
            throw new IllegalArgumentException("every container must be inside the cuboid");
        }
    }

    public RegisteredRegionData toData() {
        return new RegisteredRegionData(id, name, ownerId, world.uuid(), world.name(), world.dimension(),
                cuboid, containers, createdAt);
    }

    public static RegisteredRegion fromData(RegisteredRegionData data) {
        Objects.requireNonNull(data, "data");
        return new RegisteredRegion(data.id(), data.name(), data.ownerId(),
                new WorldIdentity(data.worldUuid(), data.worldName(), data.dimension()), data.cuboid(),
                data.containers(), data.createdAt());
    }
}
