package dev.trafficcontrol.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExponentialBackoffTest {

    @Test
    void doublesEachAttemptThenCapsAtMaxDelay() {
        BackoffStrategy backoff = new ExponentialBackoff(Duration.ofMillis(100), Duration.ofMillis(1000));

        assertEquals(Duration.ofMillis(100), backoff.nextDelay(1, null));
        assertEquals(Duration.ofMillis(200), backoff.nextDelay(2, null));
        assertEquals(Duration.ofMillis(400), backoff.nextDelay(3, null));
        assertEquals(Duration.ofMillis(800), backoff.nextDelay(4, null));
        assertEquals(Duration.ofMillis(1000), backoff.nextDelay(5, null), "1600ms would exceed maxDelay");
        assertEquals(Duration.ofMillis(1000), backoff.nextDelay(10, null), "stays capped for later attempts");
    }

    @Test
    void rejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoff(Duration.ZERO, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoff(Duration.ofSeconds(2), Duration.ofSeconds(1)));
    }
}
