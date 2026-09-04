package dev.bokukoha.mcstoragemanager.platform.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP-only adapter for the plugin authentication endpoints. It has no Bukkit dependencies. */
public final class WebAccessClient {
    private static final Pattern URL_FIELD = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private final HttpClient client;
    private final URI apiBaseUrl;
    private final String serverId;
    private final String apiKey;

    public WebAccessClient(URI apiBaseUrl, String serverId, String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), apiBaseUrl, serverId, apiKey);
    }

    WebAccessClient(HttpClient client, URI apiBaseUrl, String serverId, String apiKey) {
        this.client = Objects.requireNonNull(client, "client");
        this.apiBaseUrl = requireHttpUrl(apiBaseUrl);
        this.serverId = requireText(serverId, "serverId");
        this.apiKey = requireText(apiKey, "apiKey");
    }

    public CompletableFuture<String> createLoginLink(UUID minecraftUuid, String currentName, List<String> permissions) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        String payload = "{\"minecraftUuid\":\"" + minecraftUuid + "\",\"currentName\":\""
                + escape(requireText(currentName, "currentName")) + "\",\"permissions\":" + jsonArray(permissions) + "}";
        return send("auth/link", payload).thenApply(WebAccessClient::readUrl);
    }

    public CompletableFuture<Void> revokeSessions(UUID minecraftUuid) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        return send("auth/revoke", "{\"minecraftUuid\":\"" + minecraftUuid + "\"}").thenApply(ignored -> null);
    }

    private CompletableFuture<String> send(String path, String payload) {
        HttpRequest request = HttpRequest.newBuilder(apiBaseUrl.resolve("api/plugin/" + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Server-Id", serverId)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Web API returned HTTP " + response.statusCode());
                    }
                    return response.body();
                });
    }

    private static String readUrl(String responseBody) {
        Matcher matcher = URL_FIELD.matcher(responseBody);
        if (!matcher.find()) throw new IllegalStateException("Web API did not return a login URL");
        String url = unescapeJsonString(matcher.group(1));
        URI parsed = requireHttpUrl(URI.create(url));
        return parsed.toString();
    }

    private static URI requireHttpUrl(URI value) {
        Objects.requireNonNull(value, "apiBaseUrl");
        if (!value.isAbsolute() || !("https".equalsIgnoreCase(value.getScheme()) || "http".equalsIgnoreCase(value.getScheme()))
                || value.getHost() == null) {
            throw new IllegalArgumentException("URL must be an absolute HTTP(S) URL");
        }
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static String jsonArray(List<String> values) {
        Objects.requireNonNull(values, "permissions");
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) json.append(',');
            json.append('\"').append(escape(requireText(values.get(index), "permission"))).append('\"');
        }
        return json.append(']').toString();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping) {
                if (current == '\\') escaping = true;
                else result.append(current);
                continue;
            }
            switch (current) {
                case '\"' -> result.append('\"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                default -> throw new IllegalArgumentException("unsupported JSON escape in login URL");
            }
            escaping = false;
        }
        if (escaping) throw new IllegalArgumentException("unterminated JSON escape in login URL");
        return result.toString();
    }
}
