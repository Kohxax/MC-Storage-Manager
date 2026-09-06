package dev.bokukoha.mcstoragemanager.platform.sync;

import java.util.regex.Pattern;

/** Formats remote failures for ordinary logs without exposing credentials or huge responses. */
final class HttpDiagnostics {
    private static final int MAX_BODY_LENGTH = 256;
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)([\\\"']?(?:authorization|api[-_ ]?key|token|access[-_ ]?token|refresh[-_ ]?token|password|secret)[\\\"']?)"
                    + "(\\s*[=:]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^,}\\s]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");

    private HttpDiagnostics() { }

    static String body(String body) {
        if (body == null || body.isBlank()) return "<empty>";
        String sanitized = body.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        sanitized = SECRET_FIELD.matcher(sanitized).replaceAll("$1$2<redacted>");
        sanitized = BEARER.matcher(sanitized).replaceAll("Bearer <redacted>");
        if (sanitized.length() > MAX_BODY_LENGTH) sanitized = sanitized.substring(0, MAX_BODY_LENGTH) + "…";
        return sanitized;
    }

    static String exception(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        if (cause instanceof HttpFailure failure) {
            return "status=" + failure.status + " body=" + body(failure.responseBody);
        }
        String type = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? type : type + ": " + body(message);
    }

    static final class HttpFailure extends RuntimeException {
        private final int status;
        private final String responseBody;

        HttpFailure(int status, String responseBody) {
            super("HTTP " + status);
            this.status = status;
            this.responseBody = responseBody;
        }
    }
}
