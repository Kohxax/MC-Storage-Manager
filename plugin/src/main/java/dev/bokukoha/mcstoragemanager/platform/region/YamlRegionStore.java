package dev.bokukoha.mcstoragemanager.platform.region;

import dev.bokukoha.mcstoragemanager.core.region.BlockPosition;
import dev.bokukoha.mcstoragemanager.core.region.Cuboid;
import dev.bokukoha.mcstoragemanager.core.region.RegisteredRegionData;
import dev.bokukoha.mcstoragemanager.core.region.RegionStore;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Local YAML implementation for regions while the remote API is not enabled. */
public final class YamlRegionStore implements RegionStore {
    private static final String ROOT = "regions";

    private final File file;
    private final Logger logger;
    private YamlConfiguration configuration;

    public YamlRegionStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public Collection<RegisteredRegionData> loadAll() {
        ConfigurationSection root = configuration.getConfigurationSection(ROOT);
        if (root == null) {
            return List.of();
        }
        List<RegisteredRegionData> regions = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                regions.add(read(UUID.fromString(id), section));
            } catch (RuntimeException exception) {
                logger.warning("Skipping invalid saved region " + id + ": " + exception.getMessage());
            }
        }
        return List.copyOf(regions);
    }

    @Override
    public void save(RegisteredRegionData region) {
        String path = ROOT + "." + region.id();
        configuration.set(path + ".name", region.name());
        configuration.set(path + ".owner-id", region.ownerId().toString());
        configuration.set(path + ".world.uuid", region.worldUuid().toString());
        configuration.set(path + ".world.name", region.worldName());
        configuration.set(path + ".world.dimension", region.dimension());
        configuration.set(path + ".bounds.min-x", region.cuboid().minX());
        configuration.set(path + ".bounds.min-y", region.cuboid().minY());
        configuration.set(path + ".bounds.min-z", region.cuboid().minZ());
        configuration.set(path + ".bounds.max-x", region.cuboid().maxX());
        configuration.set(path + ".bounds.max-y", region.cuboid().maxY());
        configuration.set(path + ".bounds.max-z", region.cuboid().maxZ());
        configuration.set(path + ".created-at", region.createdAt().toString());
        configuration.set(path + ".containers", region.containers().stream()
                .map(position -> position.x() + "," + position.y() + "," + position.z()).toList());
        saveFile();
    }

    @Override
    public void delete(UUID regionId) {
        configuration.set(ROOT + "." + regionId, null);
        saveFile();
    }

    private RegisteredRegionData read(UUID id, ConfigurationSection section) {
        Cuboid cuboid = new Cuboid(
                section.getInt("bounds.min-x"), section.getInt("bounds.min-y"), section.getInt("bounds.min-z"),
                section.getInt("bounds.max-x"), section.getInt("bounds.max-y"), section.getInt("bounds.max-z"));
        List<BlockPosition> containers = section.getStringList("containers").stream()
                .map(YamlRegionStore::parsePosition).toList();
        return new RegisteredRegionData(
                id,
                required(section, "name"),
                UUID.fromString(required(section, "owner-id")),
                UUID.fromString(required(section, "world.uuid")),
                required(section, "world.name"),
                required(section, "world.dimension"),
                cuboid,
                containers,
                Instant.parse(required(section, "created-at")));
    }

    private static BlockPosition parsePosition(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid container position: " + value);
        }
        return new BlockPosition(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static String required(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + path);
        }
        return value;
    }

    private void saveFile() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save local region data", exception);
        }
    }
}
