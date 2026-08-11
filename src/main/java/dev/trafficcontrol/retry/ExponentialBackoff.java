package dev.trafficcontrol.retry;

import java.time.Duration;

/** Exponential growth, no randomization -- the default. See the README for why jitter isn't the default. */
public final class ExponentialBackoff extends AbstractExponentialBackoff {

    public ExponentialBackoff(Duration baseDelay, Duration maxDelay) {
        super(baseDelay, maxDelay);
    }

    @Override
    Duration randomize(Duration cap) {
        return cap;
    }
}
