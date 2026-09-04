package dev.bokukoha.mcstoragemanager.core.region;

import java.util.Objects;
import java.util.UUID;

/**
 * Region namespace identity. The world name and dimension form the overlap/index key. UUID is
 * retained to reject a deleted-and-recreated world when persisted registrations are restored.
 */
public final class WorldIdentity {
    private final UUID uuid;
    private final String name;
    private final String dimension;

    public WorldIdentity(UUID uuid, String name, String dimension) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = requireText(name, "name");
        this.dimension = requireText(dimension, "dimension");
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public String dimension() {
        return dimension;
    }

    public boolean isSameWorld(WorldIdentity other) {
        return hasSameRegistrationKey(other);
    }

    public boolean hasSameRegistrationKey(WorldIdentity other) {
        return other != null && name.equals(other.name) && dimension.equals(other.dimension);
    }

    public boolean hasSameInstance(WorldIdentity other) {
        return other != null && uuid.equals(other.uuid);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof WorldIdentity other && hasSameRegistrationKey(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, dimension);
    }

    @Override
    public String toString() {
        return name + " (" + dimension + ", " + uuid + ")";
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
