package dev.trafficcontrol.circuitbreaker;

import dev.trafficcontrol.ratelimiter.NanoClock;
import java.util.Objects;

/**
 * Outcomes from the last N seconds, one bucket per second.
 *
 * Unlike the count-based window this one takes a lock. Rolling a bucket
 * over to a new second has to clear it while other writers may still be
 * adding to it; doing that lock-free means either packing epoch and all
 * three counts into one long (too few bits per count) or swapping in a
 * freshly allocated bucket on every record. A short critical section is
 * the simpler correct answer here.
 */
final class TimeBasedSlidingWindow implements SlidingWindow {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int windowSeconds;
    private final NanoClock clock;
    private final int[] bucketTotal;
    private final int[] bucketFailed;
    private final int[] bucketSlow;
    private long headSecond;
    private int totalCalls;
    private int failedCalls;
    private int slowCalls;

    TimeBasedSlidingWindow(int windowSeconds, NanoClock clock) {
        if (windowSeconds < 1) {
            throw new IllegalArgumentException("windowSeconds must be >= 1, was " + windowSeconds);
        }
        this.windowSeconds = windowSeconds;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.bucketTotal = new int[windowSeconds];
        this.bucketFailed = new int[windowSeconds];
        this.bucketSlow = new int[windowSeconds];
        this.headSecond = currentSecond();
    }

    @Override
    public synchronized void record(boolean failed, boolean slow) {
        long now = currentSecond();
        expireUpTo(now);
        int bucket = bucketFor(now);
        bucketTotal[bucket]++;
        totalCalls++;
        if (failed) {
            bucketFailed[bucket]++;
            failedCalls++;
        }
        if (slow) {
            bucketSlow[bucket]++;
            slowCalls++;
        }
    }

    @Override
    public synchronized WindowSnapshot snapshot() {
        expireUpTo(currentSecond());
        return new WindowSnapshot(totalCalls, failedCalls, slowCalls);
    }

    // Clears every bucket whose second has fallen out of (now - windowSeconds, now].
    private void expireUpTo(long now) {
        if (now <= headSecond) {
            return;
        }
        long toClear = Math.min(now - headSecond, windowSeconds);
        for (long second = headSecond + 1; second <= headSecond + toClear; second++) {
            int bucket = bucketFor(second);
            totalCalls -= bucketTotal[bucket];
            failedCalls -= bucketFailed[bucket];
            slowCalls -= bucketSlow[bucket];
            bucketTotal[bucket] = 0;
            bucketFailed[bucket] = 0;
            bucketSlow[bucket] = 0;
        }
        headSecond = now;
    }

    private int bucketFor(long second) {
        return (int) Math.floorMod(second, (long) windowSeconds);
    }

    private long currentSecond() {
        return Math.floorDiv(clock.nanoTime(), NANOS_PER_SECOND);
    }
}
