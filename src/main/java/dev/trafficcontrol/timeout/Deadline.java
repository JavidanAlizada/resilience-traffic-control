package dev.trafficcontrol.timeout;

import dev.trafficcontrol.ratelimiter.NanoClock;
import java.time.Duration;
import java.util.Objects;

/**
 * A time budget that shrinks as it's passed along. Hand one of these
 * through a call chain instead of a fixed duration, and each hop reads
 * {@link #remaining()} at its own point in time instead of getting a fresh
 * timeout that ignores how long the earlier hops already took.
 */
public final class Deadline {

    private final NanoClock clock;
    private final long expiresAtNanos;

    private Deadline(NanoClock clock, long expiresAtNanos) {
        this.clock = clock;
        this.expiresAtNanos = expiresAtNanos;
    }

    public static Deadline after(Duration timeout, NanoClock clock) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(clock, "clock");
        return new Deadline(clock, clock.nanoTime() + timeout.toNanos());
    }

    /** Never negative -- a deadline in the past just reads as zero remaining. */
    public Duration remaining() {
        long remainingNanos = expiresAtNanos - clock.nanoTime();
        return Duration.ofNanos(Math.max(0, remainingNanos));
    }

    public boolean isExpired() {
        return remaining().isZero();
    }
}
