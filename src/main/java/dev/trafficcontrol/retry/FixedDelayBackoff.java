package dev.trafficcontrol.retry;

import java.time.Duration;

/** Same wait every time. The naive baseline -- correct, but see the README for why it's a bad default. */
public final class FixedDelayBackoff implements BackoffStrategy {

    private final Duration delay;

    public FixedDelayBackoff(Duration delay) {
        if (delay.isNegative() || delay.isZero()) {
            throw new IllegalArgumentException("delay must be positive, was " + delay);
        }
        this.delay = delay;
    }

    @Override
    public Duration nextDelay(int attempt, Duration previousDelay) {
        return delay;
    }
}
