package dev.trafficcontrol.ratelimiter;

/**
 * Strategy interface every algorithm in this package implements. Callers
 * depend only on this; the concrete algorithm is chosen through
 * {@link RateLimiterConfig} and {@link RateLimiterFactory}, never referenced
 * directly.
 *
 * <p>Both methods are non-blocking: a request that would exceed the
 * configured rate is rejected immediately, never queued or parked.
 */
public interface RateLimiter {

    /**
     * Equivalent to {@code tryAcquire(1)}.
     */
    default boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * @param permits how many permits this call is worth, must be {@code >= 1}
     * @return {@code true} if admitted, {@code false} if the request would
     *     exceed the configured rate
     */
    boolean tryAcquire(int permits);
}
