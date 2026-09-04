package dev.bokukoha.mcstoragemanager.core.region;

/** A normalized, closed three-dimensional interval of integer block coordinates. */
public record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Cuboid {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Cuboid bounds must be normalized");
        }
    }

    public static Cuboid between(BlockPosition first, BlockPosition second) {
        return new Cuboid(
                Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z()),
                Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z())
        );
    }

    public long lengthX() {
        return (long) maxX - minX + 1;
    }

    public long lengthY() {
        return (long) maxY - minY + 1;
    }

    public long lengthZ() {
        return (long) maxZ - minZ + 1;
    }

    /** Returns the number of blocks, or throws when it cannot be represented as a long. */
    public long volume() {
        return Math.multiplyExact(Math.multiplyExact(lengthX(), lengthY()), lengthZ());
    }

    public boolean contains(BlockPosition position) {
        return position.x() >= minX && position.x() <= maxX
                && position.y() >= minY && position.y() <= maxY
                && position.z() >= minZ && position.z() <= maxZ;
    }

    /**
     * Tests for a shared block. Adjacent faces, for example x=15 and x=16, do not overlap.
     */
    public boolean overlaps(Cuboid other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public int minimumChunkX() {
        return Math.floorDiv(minX, 16);
    }

    public int maximumChunkX() {
        return Math.floorDiv(maxX, 16);
    }

    public int minimumChunkZ() {
        return Math.floorDiv(minZ, 16);
    }

    public int maximumChunkZ() {
        return Math.floorDiv(maxZ, 16);
    }
}
