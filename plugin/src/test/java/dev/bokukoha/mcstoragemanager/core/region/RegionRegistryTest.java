package dev.bokukoha.mcstoragemanager.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegionRegistryTest {
    private static final WorldIdentity OVERWORLD = new WorldIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "world", "minecraft:overworld");
    private static final WorldIdentity NETHER = new WorldIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), "world_nether", "minecraft:the_nether");
    private static final UUID FIRST_OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_OWNER = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void rejectsOverlapEvenWhenTheOwnersDiffer() {
        RegionRegistry registry = new RegionRegistry();
        registry.register(region("first", FIRST_OWNER, OVERWORLD, new Cuboid(0, 0, 0, 15, 10, 15)));

        RegionOverlapException exception = assertThrows(RegionOverlapException.class,
                () -> registry.register(region("second", SECOND_OWNER, OVERWORLD, new Cuboid(15, 0, 15, 30, 10, 30))));

        assertEquals(1, exception.conflicts().size());
        assertEquals("first", exception.conflicts().getFirst().existing().name());
    }

    @Test
    void permitsAdjacentRegionsAndRegionsInAnotherWorld() {
        RegionRegistry registry = new RegionRegistry();
        registry.register(region("first", FIRST_OWNER, OVERWORLD, new Cuboid(0, 0, 0, 15, 10, 15)));

        registry.register(region("next", SECOND_OWNER, OVERWORLD, new Cuboid(16, 0, 0, 31, 10, 15)));
        registry.register(region("nether", SECOND_OWNER, NETHER, new Cuboid(0, 0, 0, 15, 10, 15)));

        assertEquals(3, registry.all().size());
    }

    @Test
    void permitsSameCoordinatesWhenEitherWorldNameOrDimensionDiffers() {
        RegionRegistry registry = new RegionRegistry();
        Cuboid cuboid = new Cuboid(0, 0, 0, 15, 10, 15);
        registry.register(region("overworld", FIRST_OWNER, OVERWORLD, cuboid));
        registry.register(region("other-name", SECOND_OWNER,
                new WorldIdentity(UUID.randomUUID(), "world_copy", "minecraft:overworld"), cuboid));
        registry.register(region("other-dimension", SECOND_OWNER,
                new WorldIdentity(UUID.randomUUID(), "world", "minecraft:the_nether"), cuboid));

        assertEquals(3, registry.all().size());
    }

    @Test
    void rejectsSameNameAndDimensionEvenWhenPersistedUuidDiffers() {
        RegionRegistry registry = new RegionRegistry();
        Cuboid cuboid = new Cuboid(0, 0, 0, 15, 10, 15);
        registry.register(region("first", FIRST_OWNER, OVERWORLD, cuboid));

        assertThrows(RegionOverlapException.class, () -> registry.register(region("recreated", SECOND_OWNER,
                new WorldIdentity(UUID.randomUUID(), "world", "minecraft:overworld"), cuboid)));
    }

    @Test
    void usesChunkCandidatesButPerformsExactThreeDimensionalCheck() {
        RegionRegistry registry = new RegionRegistry();
        registry.register(region("low", FIRST_OWNER, OVERWORLD, new Cuboid(-17, 0, -1, 0, 2, 15)));
        RegisteredRegion high = region("high", SECOND_OWNER, OVERWORLD, new Cuboid(-16, 20, 0, 0, 22, 15));

        assertTrue(registry.findConflicts(high).isEmpty());
        registry.register(high);
        assertEquals(2, registry.findByOwner(FIRST_OWNER).size() + registry.findByOwner(SECOND_OWNER).size());
    }

    @Test
    void canRemoveAnIndexedRegion() {
        RegionRegistry registry = new RegionRegistry();
        RegisteredRegion region = region("remove", FIRST_OWNER, OVERWORLD, new Cuboid(0, 0, 0, 15, 1, 15));
        registry.register(region);

        assertTrue(registry.unregister(region.id()));
        assertFalse(registry.unregister(region.id()));
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void findsTheRegionContainingOneBlockThroughTheChunkIndex() {
        RegionRegistry registry = new RegionRegistry();
        RegisteredRegion region = region("lookup", FIRST_OWNER, OVERWORLD, new Cuboid(-17, 0, -17, -1, 3, -1));
        registry.register(region);

        assertEquals(region, registry.findContaining(OVERWORLD, new BlockPosition(-16, 2, -16)).orElseThrow());
        assertTrue(registry.findContaining(OVERWORLD, new BlockPosition(0, 2, 0)).isEmpty());
    }

    @Test
    void doesNotRouteEventsFromARecreatedWorldIntoTheSavedRegion() {
        RegionRegistry registry = new RegionRegistry();
        registry.register(region("lookup", FIRST_OWNER, OVERWORLD, new Cuboid(0, 0, 0, 2, 2, 2)));
        WorldIdentity recreated = new WorldIdentity(UUID.randomUUID(), OVERWORLD.name(), OVERWORLD.dimension());

        assertTrue(registry.findContaining(recreated, new BlockPosition(1, 1, 1)).isEmpty());
    }

    @Test
    void dataRoundTripPreservesTheAggregate() {
        RegisteredRegion original = new RegisteredRegion(UUID.randomUUID(), "warehouse", FIRST_OWNER, OVERWORLD,
                new Cuboid(0, 0, 0, 2, 2, 2), List.of(new BlockPosition(1, 1, 1)), Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(original, RegisteredRegion.fromData(original.toData()));
    }

    @Test
    void rejectsContainersOutsideTheRegisteredBounds() {
        assertThrows(IllegalArgumentException.class, () -> new RegisteredRegion(UUID.randomUUID(), "invalid", FIRST_OWNER,
                OVERWORLD, new Cuboid(0, 0, 0, 0, 0, 0), List.of(new BlockPosition(1, 0, 0)), Instant.now()));
    }

    private static RegisteredRegion region(String name, UUID owner, WorldIdentity world, Cuboid cuboid) {
        return new RegisteredRegion(UUID.nameUUIDFromBytes(name.getBytes()), name, owner, world, cuboid,
                List.of(), Instant.parse("2026-01-01T00:00:00Z"));
    }
}
