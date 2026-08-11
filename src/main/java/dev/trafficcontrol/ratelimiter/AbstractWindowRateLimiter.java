package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared rollover logic for the two window-based algorithms — both are
 * "count requests in a rolling one-second window," they just disagree on
 * how much of the previous window still counts, which is the one method
 * left abstract.
 */
abstract class AbstractWindowRateLimiter extends AbstractRateLimiter {

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
        requireValidPermits(permits);
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
                return false; // nothing rolled over and nothing to admit — nothing to publish
            }
            if (state.compareAndSet(old, next)) {
                return admit;
            }
            // lost the CAS race — retry with a fresh read
        }
    }

    /** The one varying step: how much of {@code previousCount} still counts at this point in the window. */
    abstract long estimatedCount(long previousCount, long currentCount, double windowProgress);

    private static WindowState rollWindow(WindowState old, long now) {
        long elapsed = now - old.windowStartNanos();
        if (elapsed < WINDOW_SIZE_NANOS) {
            // still the current window; a negative elapsed (clock went backward)
            // is treated the same as "no time has passed"
            return old;
        }
        long windowsElapsed = elapsed / WINDOW_SIZE_NANOS;
        long newWindowStart = old.windowStartNanos() + windowsElapsed * WINDOW_SIZE_NANOS;
        if (windowsElapsed == 1) {
            return new WindowState(newWindowStart, old.currentCount(), 0);
        }
        return new WindowState(newWindowStart, 0, 0); // idle gap — the "previous" window is stale too
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
