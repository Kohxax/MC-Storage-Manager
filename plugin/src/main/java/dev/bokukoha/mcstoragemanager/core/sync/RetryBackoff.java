package dev.bokukoha.mcstoragemanager.core.sync;

import java.time.Duration;
import java.util.Objects;

/** Bounded exponential retry delay policy. */
public final class RetryBackoff {
    private final Duration initialDelay;
    private final Duration maximumDelay;

    public RetryBackoff(Duration initialDelay, Duration maximumDelay) {
        this.initialDelay = requireNonNegative(initialDelay, "initialDelay");
        this.maximumDelay = requireNonNegative(maximumDelay, "maximumDelay");
        if (initialDelay.compareTo(maximumDelay) > 0) {
            throw new IllegalArgumentException("initialDelay must not exceed maximumDelay");
        }
    }

    public Duration delayAfterFailure(int failureCount) {
        if (failureCount <= 0) {
            throw new IllegalArgumentException("failureCount must be positive");
        }
        long initialMillis = initialDelay.toMillis();
        long maximumMillis = maximumDelay.toMillis();
        long delay = initialMillis;
        for (int failure = 1; failure < failureCount && delay < maximumMillis; failure++) {
            delay = delay > maximumMillis / 2 ? maximumMillis : delay * 2;
        }
        return Duration.ofMillis(Math.min(delay, maximumMillis));
    }

    public Duration initialDelay() {
        return initialDelay;
    }

    public Duration maximumDelay() {
        return maximumDelay;
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
