package dev.bokukoha.mcstoragemanager.platform.sync;

import dev.bokukoha.mcstoragemanager.core.region.RegionRegistry;
import dev.bokukoha.mcstoragemanager.core.region.RegionStore;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Polls web-originated region changes. Network parsing is async; registry/YAML changes run on the main thread. */
public final class ChangePoller {
    private final JavaPlugin plugin;
    private final RegionRegistry registry;
    private final RegionStore store;
    private final URI apiBase;
    private final String serverId;
    private final String apiKey;
    private final HttpClient client;
    private final AtomicBoolean polling = new AtomicBoolean();
    private BukkitTask task;

    public ChangePoller(JavaPlugin plugin, RegionRegistry registry, RegionStore store, URI publicApiUrl,
                        String serverId, String apiKey, int intervalSeconds) {
        if (intervalSeconds <= 0) throw new IllegalArgumentException("intervalSeconds must be positive");
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.apiBase = normalize(publicApiUrl);
        this.serverId = requireText(serverId, "serverId");
        this.apiKey = requireText(apiKey, "apiKey");
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15)).build();
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::poll, 40L,
                Math.multiplyExact(intervalSeconds, 20L));
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void poll() {
        if (!polling.compareAndSet(false, true)) return;
        request("changes?limit=50", "GET", null).whenComplete((body, error) -> {
            polling.set(false);
            if (error != null) {
                plugin.getLogger().warning("Could not poll storage changes: " + HttpDiagnostics.exception(error));
                return;
            }
            try {
                List<RemoteChange> changes = parseChanges(body);
                for (RemoteChange change : changes) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> applyOnMainThread(change));
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not parse storage changes: " + HttpDiagnostics.exception(exception));
            }
        });
    }

    private void applyOnMainThread(RemoteChange change) {
        boolean success = false;
        String result;
        try {
            switch (change.operation) {
                case "region.update" -> {
                    Object rawName = value(change.payload, "patch", Map.class).get("name");
                    if (!(rawName instanceof String name) || name.isBlank()) {
                        throw new IllegalArgumentException("region.update requires a non-blank patch.name");
                    }
                    String regionId = text(change.payload, "id");
                    var renamed = registry.rename(java.util.UUID.fromString(regionId), name)
                            .orElseThrow(() -> new IllegalArgumentException("local region does not exist"));
                    store.save(renamed.toData());
                    result = "{\"regionId\":\"" + escape(regionId) + "\",\"name\":\"" + escape(name) + "\"}";
                    success = true;
                }
                case "region.delete" -> {
                    String regionId = text(change.payload, "id");
                    java.util.UUID id = java.util.UUID.fromString(regionId);
                    registry.unregister(id);
                    store.delete(id);
                    result = "{\"regionId\":\"" + escape(regionId) + "\",\"deleted\":true}";
                    success = true;
                }
                default -> throw new IllegalArgumentException("unsupported operation " + change.operation);
            }
        } catch (RuntimeException exception) {
            result = "{\"error\":\"" + escape(exception.getMessage() == null ? "change failed" : exception.getMessage()) + "\"}";
        }
        postResult(change, success, result);
    }

    private void postResult(RemoteChange change, boolean success, String result) {
        String payload = "{\"revision\":" + change.revision + ",\"success\":" + success + ",\"result\":" + result + "}";
        request("changes/" + URLEncoder.encode(change.id, StandardCharsets.UTF_8) + "/result", "POST", payload)
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not acknowledge storage change id=" + change.id + ": "
                            + HttpDiagnostics.exception(error));
                    return null;
                });
    }

    private java.util.concurrent.CompletableFuture<String> request(String path, String method, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(apiBase.resolve("api/plugin/" + path))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + apiKey)
                .header("X-Server-Id", serverId);
        if ("POST".equals(method)) request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        else request.GET();
        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpDiagnostics.HttpFailure(response.statusCode(), response.body());
            }
            return response.body();
        });
    }

    @SuppressWarnings("unchecked")
    private static List<RemoteChange> parseChanges(String response) {
        Object root = SimpleJson.parse(response);
        Map<String, Object> data = value(root, "data", Map.class);
        Object rawChanges = data.get("changes");
        if (!(rawChanges instanceof List<?> values)) throw new IllegalArgumentException("response data.changes is missing");
        List<RemoteChange> changes = new ArrayList<>();
        for (Object raw : values) {
            if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("change must be an object");
            Map<String, Object> change = (Map<String, Object>) map;
            Object rawPayload = change.get("payload");
            if (!(rawPayload instanceof Map<?, ?> payload)) throw new IllegalArgumentException("change payload must be an object");
            changes.add(new RemoteChange(text(change, "id"), text(change, "operation"), number(change, "revision"),
                    (Map<String, Object>) payload));
        }
        return List.copyOf(changes);
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(Object source, String key, Class<T> type) {
        if (!(source instanceof Map<?, ?> map) || !type.isInstance(map.get(key))) {
            throw new IllegalArgumentException("missing " + key);
        }
        return (T) map.get(key);
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("missing " + key);
        return text;
    }

    private static long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("missing " + key);
        return number.longValue();
    }

    private static URI normalize(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null) throw new IllegalArgumentException("invalid publicApiUrl");
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private record RemoteChange(String id, String operation, long revision, Map<String, Object> payload) { }
}
