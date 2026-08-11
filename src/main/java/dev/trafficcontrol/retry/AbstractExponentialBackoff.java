package dev.trafficcontrol.retry;

import java.time.Duration;

/**
 * Shared shape for the three exponential-family strategies
 * ({@link ExponentialBackoff}, {@link FullJitterBackoff},
 * {@link EqualJitterBackoff}): compute {@code min(maxDelay, baseDelay *
 * 2^(attempt-1))}, then let the subclass decide how to randomize within
 * it. {@link DecorrelatedJitterBackoff} needs the previous delay, not just
 * the attempt count, so it doesn't share this shape and implements
 * {@link BackoffStrategy} directly instead of being forced in here.
 */
abstract class AbstractExponentialBackoff implements BackoffStrategy {

    private final Duration baseDelay;
    private final Duration maxDelay;

    AbstractExponentialBackoff(Duration baseDelay, Duration maxDelay) {
        BackoffValidation.requirePositiveRange(baseDelay, maxDelay);
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public final Duration nextDelay(int attempt, Duration previousDelay) {
        return randomize(exponentialCap(attempt));
    }

    abstract Duration randomize(Duration cap);

    private Duration exponentialCap(int attempt) {
        double raw = baseDelay.toNanos() * Math.pow(2, attempt - 1);
        long cappedNanos = (long) Math.min(raw, maxDelay.toNanos());
        return Duration.ofNanos(cappedNanos);
    }
}
