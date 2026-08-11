package dev.trafficcontrol.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Half the cap guaranteed, plus jitter on top of that -- less spread than full jitter, more predictable floor. */
public final class EqualJitterBackoff extends AbstractExponentialBackoff {

    public EqualJitterBackoff(Duration baseDelay, Duration maxDelay) {
        super(baseDelay, maxDelay);
    }

    @Override
    Duration randomize(Duration cap) {
        long half = cap.toNanos() / 2;
        long jitter = half <= 0 ? 0 : ThreadLocalRandom.current().nextLong(half);
        return Duration.ofNanos(half + jitter);
    }
}
