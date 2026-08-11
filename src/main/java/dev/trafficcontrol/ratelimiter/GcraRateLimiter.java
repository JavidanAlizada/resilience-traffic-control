package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic Cell Rate Algorithm — mathematically equivalent to a token
 * bucket, but its entire state collapses into one monotonically increasing
 * timestamp: the theoretical arrival time (TAT) at which the "bucket" would
 * be fully drained if no further requests arrive. That's what lets it live
 * in a single {@link AtomicLong} with no packing tricks.
 *
 * <p>Admission is a CAS loop over {@code tat}:
 * <pre>
 *   now    = clock.nanoTime()
 *   oldTat = tat.get()
 *   base   = max(oldTat, now)
 *   if base - now &gt; burstToleranceNanos: deny
 *   else: CAS(oldTat -&gt; base + cost); success = admit
 * </pre>
 *
 * <p><b>Progress guarantee:</b> lock-free, not wait-free — on any contended
 * round at least one thread's CAS succeeds, but no single thread has a
 * bound on how many times it can lose the race.
 *
 * <p><b>ABA:</b> doesn't apply. {@code cost} is always positive, so every
 * successful CAS strictly increases {@code tat} — it can never return to a
 * previously observed value, unlike a reused/recycled reference.
 */
final class GcraRateLimiter implements RateLimiter {

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
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, was " + permits);
        }
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
            // lost the CAS race — someone else updated tat, retry with fresh now/oldTat
        }
    }
}
