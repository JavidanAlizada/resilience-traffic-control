package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only {@link NanoClock} driven programmatically, so time-based tests
 * never {@code Thread.sleep} — every algorithm in this package is testable
 * by advancing this clock deterministically instead.
 */
final class FakeClock implements NanoClock {

    private final AtomicLong nanos;

    FakeClock(long startNanos) {
        this.nanos = new AtomicLong(startNanos);
    }

    @Override
    public long nanoTime() {
        return nanos.get();
    }

    void advance(long deltaNanos) {
        nanos.addAndGet(deltaNanos);
    }

    void set(long newNanos) {
        nanos.set(newNanos);
    }
}
