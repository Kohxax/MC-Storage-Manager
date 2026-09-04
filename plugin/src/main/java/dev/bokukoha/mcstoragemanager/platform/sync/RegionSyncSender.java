package dev.bokukoha.mcstoragemanager.platform.sync;

import dev.bokukoha.mcstoragemanager.core.region.RegisteredRegion;
import dev.bokukoha.mcstoragemanager.core.region.RegisteredRegionData;
import dev.bokukoha.mcstoragemanager.core.region.RegionRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Periodically upserts local region metadata before container batches are delivered. */
public final class RegionSyncSender {
    private static final List<String> RELEVANT_PERMISSIONS = List.of(
            "storage.region.create", "storage.region.manage.own", "storage.region.manage.any",
            "storage.web.login", "storage.admin");
    private final JavaPlugin plugin;
    private final RegionRegistry regions;
    private final URI endpoint;
    private final String serverId;
    private final String apiKey;
    private final HttpClient client;
    private final ConcurrentHashMap<UUID, AtomicBoolean> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> invalidPayloads = new ConcurrentHashMap<>();
    private BukkitTask task;

    public RegionSyncSender(JavaPlugin plugin, RegionRegistry regions, URI publicApiUrl, String serverId, String apiKey,
                            int intervalSeconds) {
        if (intervalSeconds <= 0) throw new IllegalArgumentException("intervalSeconds must be positive");
        this.plugin = plugin;
        this.regions = regions;
        this.endpoint = apiBase(publicApiUrl).resolve("api/plugin/regions");
        this.serverId = requireText(serverId, "serverId");
        this.apiKey = requireText(apiKey, "apiKey");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        long period = Math.multiplyExact(intervalSeconds, 20L);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::syncAllFromMainThread, 20L, period);
    }

    /** Called after a successful local registration, while the owner is still available on the main thread. */
    public void syncNewRegion(RegisteredRegion region, Player owner) {
        send(capture(region, owner));
    }

    /** Invalid saved regions remain absent from the local registry but are visible to the web API. */
    public void syncInvalidRegion(RegisteredRegionData region) {
        var bounds = region.cuboid();
        String json = "{\"id\":\"" + region.id() + "\",\"ownerMinecraftUuid\":\"" + region.ownerId()
                + "\",\"name\":\"" + RegionPayload.escape(region.name()) + "\",\"worldUuid\":\"" + region.worldUuid()
                + "\",\"worldName\":\"" + RegionPayload.escape(region.worldName()) + "\",\"dimensionKey\":\""
                + RegionPayload.escape(region.dimension()) + "\",\"bounds\":{\"minX\":" + bounds.minX()
                + ",\"minY\":" + bounds.minY() + ",\"minZ\":" + bounds.minZ() + ",\"maxX\":" + bounds.maxX()
                + ",\"maxY\":" + bounds.maxY() + ",\"maxZ\":" + bounds.maxZ() + "},\"status\":\"invalid\"}";
        invalidPayloads.put(region.id(), json);
        send(region.id(), json);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void syncAllFromMainThread() {
        // Bukkit player data is captured here. The HTTP callback never touches Bukkit state.
        for (RegisteredRegion region : new java.util.ArrayList<>(regions.all())) {
            send(capture(region, null));
        }
        invalidPayloads.forEach(this::send);
    }

    private RegionPayload capture(RegisteredRegion region, Player newOwner) {
        String ownerCurrentName = null;
        List<String> ownerPermissions = null;
        if (newOwner != null && newOwner.getUniqueId().equals(region.ownerId())) {
            ownerCurrentName = newOwner.getName();
            ownerPermissions = RELEVANT_PERMISSIONS.stream().filter(newOwner::hasPermission).toList();
        }
        return new RegionPayload(region, ownerCurrentName, ownerPermissions);
    }

    private void send(RegionPayload payload) {
        send(payload.region.id(), payload.toJson());
    }

    private void send(UUID regionId, String payload) {
        AtomicBoolean active = inFlight.computeIfAbsent(regionId, ignored -> new AtomicBoolean());
        if (!active.compareAndSet(false, true)) return;
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Server-Id", serverId)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            active.set(false);
            if (error != null || response.statusCode() < 200 || response.statusCode() >= 300) {
                String detail = error == null ? "HTTP " + response.statusCode() : error.getClass().getSimpleName();
                plugin.getLogger().fine("Region sync for " + regionId + " will retry: " + detail);
            }
        });
    }

    private static URI apiBase(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null
                || !("https".equalsIgnoreCase(value.getScheme()) || "http".equalsIgnoreCase(value.getScheme()))) {
            throw new IllegalArgumentException("publicApiUrl must be an absolute HTTP(S) URL");
        }
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private record RegionPayload(RegisteredRegion region, String ownerCurrentName, List<String> ownerPermissions) {
        private String toJson() {
            var bounds = region.cuboid();
            StringBuilder json = new StringBuilder("{\"id\":\"").append(region.id())
                    .append("\",\"ownerMinecraftUuid\":\"").append(region.ownerId())
                    .append("\",\"name\":\"").append(escape(region.name()))
                    .append("\",\"worldUuid\":\"").append(region.world().uuid())
                    .append("\",\"worldName\":\"").append(escape(region.world().name()))
                    .append("\",\"dimensionKey\":\"").append(escape(region.world().dimension()))
                    .append("\",\"bounds\":{\"minX\":").append(bounds.minX())
                    .append(",\"minY\":").append(bounds.minY()).append(",\"minZ\":").append(bounds.minZ())
                    .append(",\"maxX\":").append(bounds.maxX()).append(",\"maxY\":").append(bounds.maxY())
                    .append(",\"maxZ\":").append(bounds.maxZ()).append("},\"status\":\"active\"");
            if (ownerCurrentName != null) {
                json.append(",\"ownerCurrentName\":\"").append(escape(ownerCurrentName)).append('\"');
            }
            if (ownerPermissions != null) {
                json.append(",\"ownerPermissions\":[");
                for (int index = 0; index < ownerPermissions.size(); index++) {
                    if (index > 0) json.append(',');
                    json.append('\"').append(escape(ownerPermissions.get(index))).append('\"');
                }
                json.append(']');
            }
            return json.append('}').toString();
        }

        static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}
