package dev.bokukoha.mcstoragemanager.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldIdentityTest {
    @Test
    void usesWorldNameAndDimensionForTheRegionNamespaceButRetainsTheInstanceUuid() {
        UUID id = UUID.randomUUID();
        WorldIdentity first = new WorldIdentity(id, "world", "minecraft:overworld");
        WorldIdentity sameNamespaceDifferentInstance = new WorldIdentity(UUID.randomUUID(), "world", "minecraft:overworld");
        WorldIdentity renamed = new WorldIdentity(id, "spawn", "minecraft:overworld");

        assertEquals(first, sameNamespaceDifferentInstance);
        assertTrue(first.isSameWorld(sameNamespaceDifferentInstance));
        assertFalse(first.hasSameInstance(sameNamespaceDifferentInstance));
        assertFalse(first.isSameWorld(renamed));
        assertTrue(first.hasSameInstance(renamed));
    }
}
