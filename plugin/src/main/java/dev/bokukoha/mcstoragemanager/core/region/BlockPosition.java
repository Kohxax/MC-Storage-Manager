package dev.bokukoha.mcstoragemanager.core.region;

/** An immutable integer block coordinate, independent of any server API. */
public record BlockPosition(int x, int y, int z) {
}
