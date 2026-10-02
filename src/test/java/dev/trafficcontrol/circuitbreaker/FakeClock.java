package dev.trafficcontrol.circuitbreaker;

import dev.trafficcontrol.ratelimiter.NanoClock;
import java.util.concurrent.atomic.AtomicLong;

/** Same test double as the ratelimiter and timeout packages' -- duplicated on purpose, it's a few lines. */
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
