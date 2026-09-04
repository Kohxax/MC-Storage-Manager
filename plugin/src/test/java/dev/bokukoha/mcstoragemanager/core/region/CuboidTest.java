package dev.bokukoha.mcstoragemanager.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CuboidTest {
    @Test
    void normalizesBothCornersAndCalculatesClosedIntervalMetrics() {
        Cuboid cuboid = Cuboid.between(new BlockPosition(4, 8, 12), new BlockPosition(2, 6, 10));

        assertEquals(new Cuboid(2, 6, 10, 4, 8, 12), cuboid);
        assertEquals(3, cuboid.lengthX());
        assertEquals(3, cuboid.lengthY());
        assertEquals(3, cuboid.lengthZ());
        assertEquals(27, cuboid.volume());
        assertTrue(cuboid.contains(new BlockPosition(2, 6, 10)));
        assertTrue(cuboid.contains(new BlockPosition(4, 8, 12)));
        assertFalse(cuboid.contains(new BlockPosition(5, 8, 12)));
    }

    @Test
    void treatsAdjacentBlockFacesAsNonOverlapping() {
        Cuboid left = new Cuboid(0, 0, 0, 15, 10, 15);
        Cuboid right = new Cuboid(16, 0, 0, 31, 10, 15);

        assertFalse(left.overlaps(right));
        assertTrue(left.overlaps(new Cuboid(15, 0, 0, 20, 10, 15)));
    }

    @Test
    void mapsNegativeCoordinatesToMinecraftChunkCoordinates() {
        Cuboid cuboid = new Cuboid(-17, 0, -1, 16, 0, 31);

        assertEquals(-2, cuboid.minimumChunkX());
        assertEquals(1, cuboid.maximumChunkX());
        assertEquals(-1, cuboid.minimumChunkZ());
        assertEquals(1, cuboid.maximumChunkZ());
    }

    @Test
    void rejectsUnnormalizedDirectConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new Cuboid(2, 0, 0, 1, 0, 0));
    }
}
