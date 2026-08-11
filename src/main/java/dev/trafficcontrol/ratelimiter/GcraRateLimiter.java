package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * GCRA rate limiter. All the state lives in one {@code AtomicLong} — the
 * "theoretical arrival time" — which is what lets admission be a single
 * CAS loop with nothing to pack, unlike a token bucket's separate
 * tokens/timestamp fields.
 */
final class GcraRateLimiter extends AbstractRateLimiter {

    private final long emissionIntervalNanos;
    private final long burstToleranceNanos;
    private final NanoClock clock;
    private final AtomicLong theoreticalArrivalTime;

    GcraRateLimiter(RateLimiterConfig config) {
        this.emissionIntervalNanos = Math.round(1_000_000_000.0 / config.permitsPerSecond());
        this.burstToleranceNanos = (config.burstCapacity() - 1) * emissionIntervalNanos;
        this.clock = config.clock();
        this.theoreticalArrivalTime = new AtomicLong(clock.nanoTime());
    }

    @Override
    public boolean tryAcquire(int permits) {
        requireValidPermits(permits);
        long cost = permits * emissionIntervalNanos;
        while (true) {
            long now = clock.nanoTime();
            long oldTat = theoreticalArrivalTime.get();
            long base = Math.max(oldTat, now);
            if (base - now > burstToleranceNanos) {
                return false;
            }
            long newTat = base + cost;
            if (theoreticalArrivalTime.compareAndSet(oldTat, newTat)) {
                return true;
            }
            // someone else won the CAS race — retry with a fresh read
        }
    }
}
