package dev.bokukoha.mcstoragemanager.platform.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class WebAccessClientTest {
    @Test
    void createLoginLinkPreservesReturnedUrlWithoutAppendingSlash() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                "{\"url\":\"http://127.0.0.1:3000/auth/redeem?token=abc\"}");
        WebAccessClient client = new WebAccessClient(
                httpClient,
                URI.create("http://127.0.0.1:3000"),
                "server-id",
                "secret");

        String link = client.createLoginLink(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "Steve",
                List.of("storage.web.login"))
                .join();

        assertEquals("http://127.0.0.1:3000/auth/redeem?token=abc", link);
        assertEquals("http://127.0.0.1:3000/api/plugin/auth/link", httpClient.request.uri().toString());
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final String responseBody;
        private HttpRequest request;

        private RecordingHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            throw new AssertionError("createLoginLink should use sendAsync");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler) {
            this.request = request;
            HttpResponse<String> response = new StubHttpResponse(request, responseBody);
            return CompletableFuture.completedFuture((HttpResponse<T>) response);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, handler);
        }
    }

    private static final class StubHttpResponse implements HttpResponse<String> {
        private final HttpRequest request;
        private final String body;

        private StubHttpResponse(HttpRequest request, String body) {
            this.request = request;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
