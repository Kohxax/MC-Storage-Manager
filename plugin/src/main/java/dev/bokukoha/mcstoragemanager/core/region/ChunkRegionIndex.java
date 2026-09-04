package dev.bokukoha.mcstoragemanager.core.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory broad-phase index for regions. Each region is recorded in every chunk it intersects;
 * callers must still use {@link Cuboid#overlaps(Cuboid)} for the exact three-dimensional test.
 */
public final class ChunkRegionIndex {
    private final Map<UUID, RegisteredRegion> regionsById = new HashMap<>();
    private final Map<WorldIdentity, Map<ChunkPosition, Set<UUID>>> idsByWorldAndChunk = new HashMap<>();

    public void add(RegisteredRegion region) {
        Objects.requireNonNull(region, "region");
        if (regionsById.containsKey(region.id())) {
            throw new IllegalArgumentException("A region with this id is already indexed: " + region.id());
        }
        regionsById.put(region.id(), region);
        forEachChunk(region.cuboid(), (chunkX, chunkZ) -> idsByWorldAndChunk
                .computeIfAbsent(region.world(), ignored -> new HashMap<>())
                .computeIfAbsent(new ChunkPosition(chunkX, chunkZ), ignored -> new LinkedHashSet<>())
                .add(region.id()));
    }

    public boolean remove(UUID regionId) {
        RegisteredRegion region = regionsById.remove(regionId);
        if (region == null) {
            return false;
        }
        Map<ChunkPosition, Set<UUID>> chunks = idsByWorldAndChunk.get(region.world());
        forEachChunk(region.cuboid(), (chunkX, chunkZ) -> {
            ChunkPosition chunk = new ChunkPosition(chunkX, chunkZ);
            Set<UUID> ids = chunks.get(chunk);
            if (ids != null) {
                ids.remove(regionId);
                if (ids.isEmpty()) {
                    chunks.remove(chunk);
                }
            }
        });
        if (chunks.isEmpty()) {
            idsByWorldAndChunk.remove(region.world());
        }
        return true;
    }

    public List<RegisteredRegion> candidates(WorldIdentity world, Cuboid cuboid) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(cuboid, "cuboid");
        Map<ChunkPosition, Set<UUID>> chunks = idsByWorldAndChunk.get(world);
        if (chunks == null) {
            return List.of();
        }
        Set<UUID> candidateIds = new LinkedHashSet<>();
        forEachChunk(cuboid, (chunkX, chunkZ) -> {
            Set<UUID> ids = chunks.get(new ChunkPosition(chunkX, chunkZ));
            if (ids != null) {
                candidateIds.addAll(ids);
            }
        });
        List<RegisteredRegion> candidates = new ArrayList<>(candidateIds.size());
        for (UUID id : candidateIds) {
            RegisteredRegion region = regionsById.get(id);
            if (region != null) {
                candidates.add(region);
            }
        }
        return List.copyOf(candidates);
    }

    public Collection<RegisteredRegion> all() {
        return List.copyOf(regionsById.values());
    }

    private static void forEachChunk(Cuboid cuboid, ChunkVisitor visitor) {
        for (long chunkX = cuboid.minimumChunkX(); chunkX <= cuboid.maximumChunkX(); chunkX++) {
            for (long chunkZ = cuboid.minimumChunkZ(); chunkZ <= cuboid.maximumChunkZ(); chunkZ++) {
                visitor.visit((int) chunkX, (int) chunkZ);
            }
        }
    }

    @FunctionalInterface
    private interface ChunkVisitor {
        void visit(int chunkX, int chunkZ);
    }
}
