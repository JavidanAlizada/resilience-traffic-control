package dev.trafficcontrol.retry;

import java.time.Duration;

/** Shared validation for the four backoff strategies built around a base/max delay pair. */
final class BackoffValidation {

    private BackoffValidation() {
    }

    static void requirePositiveRange(Duration baseDelay, Duration maxDelay) {
        if (baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("baseDelay must be positive, was " + baseDelay);
        }
        if (maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= baseDelay, was " + maxDelay);
        }
    }
}
