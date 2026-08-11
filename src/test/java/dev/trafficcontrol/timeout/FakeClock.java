package dev.trafficcontrol.timeout;

import dev.trafficcontrol.ratelimiter.NanoClock;
import java.util.concurrent.atomic.AtomicLong;

/** Same test double as the ratelimiter package's -- duplicated on purpose, it's a few lines, not worth sharing. */
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
}
