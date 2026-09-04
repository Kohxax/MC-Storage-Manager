package dev.bokukoha.mcstoragemanager;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import dev.bokukoha.mcstoragemanager.core.PluginIdentity;
import dev.bokukoha.mcstoragemanager.core.region.RegisteredRegion;
import dev.bokukoha.mcstoragemanager.core.region.RegisteredRegionData;
import dev.bokukoha.mcstoragemanager.core.region.RegionRegistry;
import dev.bokukoha.mcstoragemanager.core.region.WorldIdentity;
import dev.bokukoha.mcstoragemanager.core.sync.ContainerSyncService;
import dev.bokukoha.mcstoragemanager.core.sync.RetryBackoff;
import dev.bokukoha.mcstoragemanager.platform.PaperPlatform;
import dev.bokukoha.mcstoragemanager.platform.WorldEditSelectionReader;
import dev.bokukoha.mcstoragemanager.platform.command.StorageCommand;
import dev.bokukoha.mcstoragemanager.platform.container.ContainerChangeListener;
import dev.bokukoha.mcstoragemanager.platform.container.InventorySnapshotter;
import dev.bokukoha.mcstoragemanager.platform.container.TrackedContainer;
import dev.bokukoha.mcstoragemanager.platform.region.RegionCreationCoordinator;
import dev.bokukoha.mcstoragemanager.platform.region.RegionRegistrationLimits;
import dev.bokukoha.mcstoragemanager.platform.region.YamlRegionStore;
import dev.bokukoha.mcstoragemanager.platform.sync.ContainerBatchSender;
import dev.bokukoha.mcstoragemanager.platform.sync.ChangePoller;
import dev.bokukoha.mcstoragemanager.platform.sync.RegionSyncSender;
import dev.bokukoha.mcstoragemanager.platform.sync.YamlSyncStateStore;
import dev.bokukoha.mcstoragemanager.platform.web.WebAccessClient;
import dev.bokukoha.mcstoragemanager.platform.web.WebAccessService;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point for the Paper plugin. */
public final class MCStorageManagerPlugin extends JavaPlugin {
    private ContainerBatchSender batchSender;
    private RegionSyncSender regionSyncSender;
    private ChangePoller changePoller;
    @Override
    public void onEnable() {
        PaperPlatform platform = new PaperPlatform(this);
        if (!platform.isWorldEditAvailable()) {
            getLogger().severe("WorldEdit is required but was not found; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Plugin installedWorldEdit = getServer().getPluginManager().getPlugin("WorldEdit");
        if (!(installedWorldEdit instanceof WorldEditPlugin worldEdit)) {
            getLogger().severe("The installed WorldEdit plugin does not expose the expected Bukkit API; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        RegionRegistrationLimits limits;
        try {
            limits = new RegionRegistrationLimits(
                    getConfig().getLong("limits.max-volume"),
                    getConfig().getLong("limits.max-edge-length"),
                    getConfig().getInt("limits.max-containers-per-region"),
                    getConfig().getInt("limits.max-regions-per-player"),
                    getConfig().getInt("initial-scan.blocks-per-tick"));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid region registration limits in config.yml: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        File regionsFile = new File(getDataFolder(), getConfig().getString("storage.regions-file", "regions.yml"));
        YamlRegionStore store = new YamlRegionStore(regionsFile, getLogger());
        RegionRegistry registry = new RegionRegistry();
        var invalidRegions = new ArrayList<RegisteredRegionData>();
        for (var data : store.loadAll()) {
            var liveWorld = getServer().getWorld(data.worldUuid());
            if (liveWorld == null) {
                boolean replaced = getServer().getWorlds().stream().anyMatch(world ->
                        world.getName().equals(data.worldName()) && world.getKey().toString().equals(data.dimension()));
                getLogger().warning("Saved region " + data.id() + " remains disabled: "
                        + (replaced ? "a different world now has its saved name and dimension." : "its world UUID is not loaded."));
                invalidRegions.add(data);
                continue;
            }
            try {
                WorldIdentity liveIdentity = new WorldIdentity(liveWorld.getUID(), liveWorld.getName(),
                        liveWorld.getKey().toString());
                RegisteredRegion region = new RegisteredRegion(data.id(), data.name(), data.ownerId(), liveIdentity,
                        data.cuboid(), data.containers(), data.createdAt());
                registry.register(region);
                if (!liveIdentity.name().equals(data.worldName()) || !liveIdentity.dimension().equals(data.dimension())) {
                    store.save(region.toData());
                    getLogger().info("Updated saved world name/dimension for region " + data.id() + ".");
                }
            } catch (RuntimeException exception) {
                getLogger().warning("Skipping saved region " + data.id() + ": " + exception.getMessage());
            }
        }

        ContainerSyncService syncService;
        try {
            syncService = new ContainerSyncService(getConfig().getInt("sync.max-batch-containers"),
                    new RetryBackoff(Duration.ofSeconds(getConfig().getLong("sync.retry-initial-seconds")),
                            Duration.ofSeconds(getConfig().getLong("sync.retry-max-seconds"))),
                    new YamlSyncStateStore(new File(getDataFolder(),
                            getConfig().getString("sync.queue-file", "pending-sync.yml")), getLogger()));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid sync configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        RegionCreationCoordinator coordinator = new RegionCreationCoordinator(this, registry, store, limits,
                region -> {
                    queueInitialSnapshots(region, syncService);
                    if (regionSyncSender != null) {
                        regionSyncSender.syncNewRegion(region, getServer().getPlayer(region.ownerId()));
                    }
                });
        PluginCommand storageCommand = getCommand("storage");
        if (storageCommand == null) {
            getLogger().severe("storage command is missing from plugin.yml; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        storageCommand.setExecutor(new StorageCommand(new WorldEditSelectionReader(worldEdit), coordinator,
                createWebAccessService()));
        getServer().getPluginManager().registerEvents(new ContainerChangeListener(this, registry, syncService), this);

        if (getConfig().getBoolean("sync.enabled")) {
            try {
                String endpoint = getConfig().getString("sync.endpoint", "");
                String apiKey = getConfig().getString("sync.api-key", "");
                String serverId = getConfig().getString("sync.server-id", "");
                if (endpoint.isBlank() || apiKey.isBlank() || serverId.isBlank()) {
                    throw new IllegalArgumentException("endpoint, server-id, and api-key are required");
                }
                batchSender = new ContainerBatchSender(this, syncService, URI.create(endpoint), apiKey,
                        serverId,
                        regionId -> registry.findById(regionId).map(region -> region.world().uuid().toString()).orElse(null),
                        getConfig().getInt("sync.interval-seconds"));
                String publicApiUrl = getConfig().getString("web.public-api-url", "");
                URI regionApiUrl = publicApiUrl.isBlank() ? publicBaseFromEndpoint(URI.create(endpoint)) : URI.create(publicApiUrl);
                regionSyncSender = new RegionSyncSender(this, registry, regionApiUrl,
                        getConfig().getString("sync.server-id", ""), apiKey, getConfig().getInt("sync.interval-seconds"));
                invalidRegions.forEach(regionSyncSender::syncInvalidRegion);
                changePoller = new ChangePoller(this, registry, store, regionApiUrl,
                        getConfig().getString("sync.server-id", ""), apiKey, getConfig().getInt("sync.interval-seconds"));
            } catch (IllegalArgumentException exception) {
                getLogger().severe("Sync is enabled but invalid: " + exception.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        getLogger().info(PluginIdentity.displayName() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (batchSender != null) batchSender.stop();
        if (regionSyncSender != null) regionSyncSender.stop();
        if (changePoller != null) changePoller.stop();
    }

    private void queueInitialSnapshots(RegisteredRegion region, ContainerSyncService syncService) {
        var world = getServer().getWorld(region.world().uuid());
        if (world == null) return;
        for (var position : region.containers()) {
            var state = world.getBlockAt(position.x(), position.y(), position.z()).getState();
            if (TrackedContainer.isTracked(state) && state instanceof org.bukkit.inventory.InventoryHolder holder) {
                syncService.markDirty(InventorySnapshotter.snapshot(region.id(), position,
                        TrackedContainer.containerType(state), holder.getInventory()));
            }
        }
    }

    private WebAccessService createWebAccessService() {
        String publicApiUrl = getConfig().getString("web.public-api-url", "");
        String serverId = getConfig().getString("sync.server-id", "");
        String apiKey = getConfig().getString("sync.api-key", "");
        if (publicApiUrl.isBlank() || serverId.isBlank() || apiKey.isBlank()) {
            getLogger().info("Web login commands are disabled until web.public-api-url, sync.server-id, and sync.api-key are configured.");
            return null;
        }
        try {
            return new WebAccessService(this, new WebAccessClient(URI.create(publicApiUrl), serverId, apiKey));
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Web login commands are disabled: " + exception.getMessage());
            return null;
        }
    }

    private static URI publicBaseFromEndpoint(URI endpoint) {
        if (!endpoint.isAbsolute() || endpoint.getHost() == null) {
            throw new IllegalArgumentException("sync.endpoint must be an absolute URL");
        }
        return URI.create(endpoint.getScheme() + "://" + endpoint.getAuthority() + "/");
    }
}
