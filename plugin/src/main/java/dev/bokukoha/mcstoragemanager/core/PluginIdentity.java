package dev.bokukoha.mcstoragemanager.core;

/** Paper-independent plugin identity used by application code and tests. */
public final class PluginIdentity {
    private static final String DISPLAY_NAME = "MC-Storage-Manager";

    private PluginIdentity() {
    }

    public static String displayName() {
        return DISPLAY_NAME;
    }
}
