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
    private static final int MAX_ERROR_BODY_LENGTH = 1024;
    private static final Pattern URL_FIELD = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private final HttpClient client;
    private final URI apiBaseUrl;
    private final String serverId;
    private final String apiKey;

    public WebAccessClient(URI apiBaseUrl, String serverId, String apiKey) {
        this(HttpClient.newBuilder()
                // Nitro's HTTP development server resets Java's cleartext HTTP/2 upgrade request.
                // HTTP/1.1 works for both local HTTP development and production HTTPS endpoints.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build(), apiBaseUrl, serverId, apiKey);
    }

    WebAccessClient(HttpClient client, URI apiBaseUrl, String serverId, String apiKey) {
        this.client = Objects.requireNonNull(client, "client");
        this.apiBaseUrl = normalizeApiBaseUrl(apiBaseUrl);
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
                .handle((response, error) -> {
                    if (error != null) {
                        Throwable cause = unwrap(error);
                        String detail = cause.getMessage();
                        throw new IllegalStateException("Web API request failed: POST " + request.uri() + " ("
                                + cause.getClass().getSimpleName()
                                + (detail == null || detail.isBlank() ? "" : ": " + detail) + ")", cause);
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        String requestId = response.headers().firstValue("x-request-id").orElse("not provided");
                        throw new IllegalStateException("Web API returned HTTP " + response.statusCode()
                                + " for POST " + request.uri() + " (request ID: " + requestId
                                + ", response: " + errorBody(response.body()) + ")");
                    }
                    return response.body();
                });
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String errorBody(String body) {
        if (body == null || body.isBlank()) return "<empty>";
        String sanitized = body.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (sanitized.length() <= MAX_ERROR_BODY_LENGTH) return sanitized;
        return sanitized.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }

    static String readUrl(String responseBody) {
        Matcher matcher = URL_FIELD.matcher(responseBody);
        if (!matcher.find()) throw new IllegalStateException("Web API did not return a login URL");
        String url = unescapeJsonString(matcher.group(1));
        URI parsed = requireHttpUrl(URI.create(url), "login URL");
        return parsed.toString();
    }

    private static URI normalizeApiBaseUrl(URI value) {
        URI validated = requireHttpUrl(value, "apiBaseUrl");
        String text = validated.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    private static URI requireHttpUrl(URI value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.isAbsolute() || !("https".equalsIgnoreCase(value.getScheme()) || "http".equalsIgnoreCase(value.getScheme()))
                || value.getHost() == null) {
            throw new IllegalArgumentException("URL must be an absolute HTTP(S) URL");
        }
        return value;
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
