package dev.bokukoha.mcstoragemanager.core.region;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for conflict-safe region registration. Conflict checks deliberately span
 * all owners: ownership never makes two regions eligible to occupy the same block.
 */
public final class RegionRegistry {
    private final ChunkRegionIndex index = new ChunkRegionIndex();

    public void register(RegisteredRegion region) {
        Objects.requireNonNull(region, "region");
        if (findById(region.id()).isPresent()) {
            throw new IllegalArgumentException("A region with this id is already registered: " + region.id());
        }
        List<RegionConflict> conflicts = findConflicts(region);
        if (!conflicts.isEmpty()) {
            throw new RegionOverlapException(conflicts);
        }
        index.add(region);
    }

    public List<RegionConflict> findConflicts(RegisteredRegion candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return index.candidates(candidate.world(), candidate.cuboid()).stream()
                .filter(existing -> existing.world().isSameWorld(candidate.world()))
                .filter(existing -> existing.cuboid().overlaps(candidate.cuboid()))
                .map(RegionConflict::new)
                .sorted(Comparator.comparing(conflict -> conflict.existing().id()))
                .toList();
    }

    public Optional<RegisteredRegion> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return index.all().stream().filter(region -> region.id().equals(id)).findFirst();
    }

    public List<RegisteredRegion> findByOwner(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return index.all().stream().filter(region -> region.ownerId().equals(ownerId)).toList();
    }

    /** Returns the region containing a block position, if any. Overlap prevention makes it unique. */
    public Optional<RegisteredRegion> findContaining(WorldIdentity world, BlockPosition position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        Cuboid point = new Cuboid(position.x(), position.y(), position.z(), position.x(), position.y(), position.z());
        return index.candidates(world, point).stream()
                // Event routing must bind to the exact world instance. Name + dimension is the
                // registration namespace, but a deleted/recreated world may reuse both values.
                .filter(region -> region.world().hasSameInstance(world))
                .filter(region -> region.cuboid().contains(position))
                .findFirst();
    }

    public boolean unregister(UUID id) {
        return index.remove(Objects.requireNonNull(id, "id"));
    }

    /** Replaces only the display name while preserving the registration's world and bounds. */
    public Optional<RegisteredRegion> rename(UUID id, String name) {
        RegisteredRegion previous = findById(Objects.requireNonNull(id, "id")).orElse(null);
        if (previous == null) return Optional.empty();
        RegisteredRegion renamed = new RegisteredRegion(previous.id(), name, previous.ownerId(), previous.world(),
                previous.cuboid(), previous.containers(), previous.createdAt());
        index.remove(id);
        index.add(renamed);
        return Optional.of(renamed);
    }

    public List<RegisteredRegion> all() {
        return index.all().stream().sorted(Comparator.comparing(RegisteredRegion::createdAt)).toList();
    }
}
