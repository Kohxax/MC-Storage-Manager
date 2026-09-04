package dev.bokukoha.mcstoragemanager.platform.sync;

import dev.bokukoha.mcstoragemanager.core.sync.ContainerSnapshot;
import dev.bokukoha.mcstoragemanager.core.sync.ContainerSyncService;
import dev.bokukoha.mcstoragemanager.core.sync.ItemAmount;
import dev.bokukoha.mcstoragemanager.core.sync.SyncBatch;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Sends durable batches without ever reading Bukkit state from an asynchronous thread. */
public final class ContainerBatchSender {
    private final JavaPlugin plugin;
    private final ContainerSyncService syncService;
    private final URI endpoint;
    private final String apiKey;
    private final String serverId;
    private final Function<UUID, String> worldUuidByRegion;
    private final HttpClient httpClient;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private BukkitTask task;

    public ContainerBatchSender(JavaPlugin plugin, ContainerSyncService syncService, URI endpoint, String apiKey,
                                String serverId, Function<UUID, String> worldUuidByRegion, int intervalSeconds) {
        if (intervalSeconds <= 0) throw new IllegalArgumentException("intervalSeconds must be positive");
        this.plugin = plugin;
        this.syncService = syncService;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.serverId = serverId;
        this.worldUuidByRegion = worldUuidByRegion;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        long intervalTicks = Math.multiplyExact(intervalSeconds, 20L);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::sendIfReady, intervalTicks,
                intervalTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void sendIfReady() {
        if (!inFlight.compareAndSet(false, true)) return;
        try {
            var optionalBatch = syncService.nextBatch(Instant.now());
            if (optionalBatch.isEmpty()) {
                inFlight.set(false);
                return;
            }
            SyncBatch batch = optionalBatch.get();
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .header("Idempotency-Key", batch.id().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(batch, worldUuidByRegion)));
            if (!serverId.isBlank()) request.header("X-Server-Id", serverId);
            httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        try {
                            if (error == null && response.statusCode() >= 200 && response.statusCode() < 300) {
                                syncService.acknowledge(batch.id());
                            } else {
                                syncService.recordFailure(batch.id(), Instant.now());
                                String detail = error == null ? "HTTP " + response.statusCode() : error.getClass().getSimpleName();
                                plugin.getLogger().fine("Storage sync batch " + batch.id() + " will retry after " + detail);
                            }
                        } finally {
                            inFlight.set(false);
                        }
                    });
        } catch (RuntimeException exception) {
            inFlight.set(false);
            plugin.getLogger().warning("Could not prepare storage sync batch: " + exception.getMessage());
        }
    }

    private static String toJson(SyncBatch batch, Function<UUID, String> worldUuidByRegion) {
        String worldUuid = worldUuidByRegion.apply(batch.containers().getFirst().containerId().regionId());
        if (worldUuid == null || worldUuid.isBlank()) {
            throw new IllegalStateException("cannot send a batch for a region that is no longer registered");
        }
        StringBuilder json = new StringBuilder("{\"regionId\":\"")
                .append(batch.containers().getFirst().containerId().regionId()).append("\",\"idempotencyKey\":\"")
                .append(batch.id()).append("\",\"containers\":[");
        for (int index = 0; index < batch.containers().size(); index++) {
            if (index > 0) json.append(',');
            appendContainer(json, batch.containers().get(index), worldUuid);
        }
        return json.append("]}").toString();
    }

    private static void appendContainer(StringBuilder json, ContainerSnapshot snapshot, String worldUuid) {
        var position = snapshot.containerId().position();
        json.append("{\"worldUuid\":\"").append(escape(worldUuid)).append("\",\"x\":").append(position.x())
                .append(",\"y\":").append(position.y()).append(",\"z\":").append(position.z())
                .append(",\"containerType\":\"").append(escape(snapshot.containerType())).append("\"");
        // The web endpoint should treat deleted=true as a physical container deletion. It is kept
        // explicit rather than overloading an empty inventory, which is a valid container state.
        if (snapshot.deleted()) json.append(",\"deleted\":true");
        json.append(",\"items\":[");
        for (int index = 0; index < snapshot.items().size(); index++) {
            if (index > 0) json.append(',');
            appendItem(json, snapshot.items().get(index));
        }
        json.append("]}");
    }

    private static void appendItem(StringBuilder json, ItemAmount item) {
        json.append("{\"itemKey\":\"").append(escape(item.itemKey())).append("\",\"amount\":")
                .append(item.amount());
        if (!item.variant().isEmpty()) {
            // The initial web schema has one variant key. Preserve deterministic extension data
            // until a richer component serializer is introduced.
            json.append(",\"variantKey\":\"").append(escape(item.variant().toString())).append("\"");
        }
        json.append('}');
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }
}
