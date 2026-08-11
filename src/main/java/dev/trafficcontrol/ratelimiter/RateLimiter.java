package dev.trafficcontrol.ratelimiter;

/**
 * A rate limiter admits or rejects a request, never blocks or queues one.
 * Build one through {@link RateLimiters} rather than a concrete class.
 */
public interface RateLimiter {

    default boolean tryAcquire() {
        return tryAcquire(1);
    }

    /** @param permits cost of this request, at least 1 */
    boolean tryAcquire(int permits);
}
