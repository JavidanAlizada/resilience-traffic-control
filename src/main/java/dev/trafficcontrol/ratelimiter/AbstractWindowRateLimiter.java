package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Template Method base for the two window-based algorithms
 * ({@link FixedWindowRateLimiter}, {@link SlidingWindowCounterRateLimiter}).
 * Both are "count requests within a rolling one-second window" — they only
 * differ in how much weight the previous window's count still carries, so
 * that's the one step left abstract.
 *
 * <p>State is an immutable {@link WindowState} snapshot swapped via CAS on
 * an {@link AtomicReference}. Unlike {@link GcraRateLimiter} and
 * {@link TokenBucketRateLimiter}, this allocates a new snapshot on every
 * contended attempt — an explicit, documented trade-off (see doc 02,
 * Section 3b): fine here, since the point of these two algorithms is the
 * window-boundary/blending behavior, not the packing technique.
 */
abstract class AbstractWindowRateLimiter implements RateLimiter {

    private static final long WINDOW_SIZE_NANOS = 1_000_000_000L;

    private final long limitPerWindow;
    private final NanoClock clock;
    private final AtomicReference<WindowState> state;

    AbstractWindowRateLimiter(RateLimiterConfig config) {
        this.limitPerWindow = Math.max(1, Math.round(config.permitsPerSecond()));
        this.clock = config.clock();
        this.state = new AtomicReference<>(new WindowState(clock.nanoTime(), 0, 0));
    }

    @Override
    public final boolean tryAcquire(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, was " + permits);
        }
        while (true) {
            WindowState old = state.get();
            long now = clock.nanoTime();
            WindowState candidate = rollWindow(old, now);

            double windowProgress = clampToUnitInterval(
                    (now - candidate.windowStartNanos()) / (double) WINDOW_SIZE_NANOS);
            long estimated = estimatedCount(candidate.previousCount(), candidate.currentCount(), windowProgress);

            boolean admit = estimated + permits <= limitPerWindow;
            WindowState next = admit ? candidate.withAdditionalCount(permits) : candidate;

            if (next == old) {
                // no rollover happened and nothing to admit — nothing to publish
                return false;
            }
            if (state.compareAndSet(old, next)) {
                return admit;
            }
            // lost the CAS race — retry with a fresh read
        }
    }

    /**
     * The one varying step: given the previous window's count, the current
     * window's count so far, and how far into the current window {@code now}
     * falls (0.0 = just started, close to 1.0 = about to roll), return the
     * estimated request count to compare against the window limit.
     */
    abstract long estimatedCount(long previousCount, long currentCount, double windowProgress);

    private static WindowState rollWindow(WindowState old, long now) {
        long elapsed = now - old.windowStartNanos();
        if (elapsed < WINDOW_SIZE_NANOS) {
            // still the current window (elapsed < 0 means the clock went
            // backward; treat that the same as "no time has passed")
            return old;
        }
        long windowsElapsed = elapsed / WINDOW_SIZE_NANOS;
        long newWindowStart = old.windowStartNanos() + windowsElapsed * WINDOW_SIZE_NANOS;
        if (windowsElapsed == 1) {
            return new WindowState(newWindowStart, old.currentCount(), 0);
        }
        // gap spanning more than one window — the "previous" window is stale too
        return new WindowState(newWindowStart, 0, 0);
    }

    private static double clampToUnitInterval(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record WindowState(long windowStartNanos, long previousCount, long currentCount) {

        WindowState withAdditionalCount(int permits) {
            return new WindowState(windowStartNanos, previousCount, currentCount + permits);
        }
    }
}
