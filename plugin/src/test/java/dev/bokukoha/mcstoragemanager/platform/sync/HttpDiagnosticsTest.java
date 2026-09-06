package dev.bokukoha.mcstoragemanager.platform.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpDiagnosticsTest {
    @Test
    void redactsCredentialFieldsAndBoundsResponseBody() {
        String detail = HttpDiagnostics.body("{\"apiKey\":\"do-not-log\",\"message\":\""
                + "x".repeat(400) + "\"}");

        assertFalse(detail.contains("do-not-log"));
        assertTrue(detail.contains("<redacted>"));
        assertTrue(detail.length() <= 257);
    }

    @Test
    void includesStatusAndSanitizedBodyForHttpFailures() {
        String detail = HttpDiagnostics.exception(new HttpDiagnostics.HttpFailure(503,
                "{\"token\":\"do-not-log\",\"error\":\"busy\"}"));

        assertTrue(detail.contains("status=503"));
        assertTrue(detail.contains("busy"));
        assertFalse(detail.contains("do-not-log"));
    }
}
