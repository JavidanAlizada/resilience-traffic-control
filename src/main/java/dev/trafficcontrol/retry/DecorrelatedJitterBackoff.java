package dev.trafficcontrol.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AWS's decorrelated jitter: {@code random(baseDelay, previousDelay * 3)}, capped at
 * {@code maxDelay}. Needs the previous delay as input, not just the attempt count, so it
 * doesn't fit {@link AbstractExponentialBackoff}'s shape -- implements {@link BackoffStrategy}
 * directly instead.
 */
public final class DecorrelatedJitterBackoff implements BackoffStrategy {

    private final Duration baseDelay;
    private final Duration maxDelay;

    public DecorrelatedJitterBackoff(Duration baseDelay, Duration maxDelay) {
        BackoffValidation.requirePositiveRange(baseDelay, maxDelay);
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public Duration nextDelay(int attempt, Duration previousDelay) {
        Duration basis = previousDelay == null ? baseDelay : previousDelay;
        long lowNanos = baseDelay.toNanos();
        long highNanos = Math.min(basis.toNanos() * 3, maxDelay.toNanos());
        if (highNanos <= lowNanos) {
            return Duration.ofNanos(lowNanos);
        }
        return Duration.ofNanos(ThreadLocalRandom.current().nextLong(lowNanos, highNanos + 1));
    }
}
