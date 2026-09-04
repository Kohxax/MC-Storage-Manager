package dev.bokukoha.mcstoragemanager.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PluginIdentityTest {
    @Test
    void exposesTheConfiguredPluginName() {
        assertEquals("MC-Storage-Manager", PluginIdentity.displayName());
    }
}
