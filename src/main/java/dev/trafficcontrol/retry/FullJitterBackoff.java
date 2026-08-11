package dev.trafficcontrol.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Picks uniformly from [0, cap] -- the widest spread, best at breaking up synchronized retry storms. */
public final class FullJitterBackoff extends AbstractExponentialBackoff {

    public FullJitterBackoff(Duration baseDelay, Duration maxDelay) {
        super(baseDelay, maxDelay);
    }

    @Override
    Duration randomize(Duration cap) {
        return Duration.ofNanos(ThreadLocalRandom.current().nextLong(cap.toNanos()));
    }
}
