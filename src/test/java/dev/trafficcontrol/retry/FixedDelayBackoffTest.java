package dev.trafficcontrol.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FixedDelayBackoffTest {

    @Test
    void alwaysReturnsTheSameDelay() {
        BackoffStrategy backoff = new FixedDelayBackoff(Duration.ofMillis(50));

        assertEquals(Duration.ofMillis(50), backoff.nextDelay(1, null));
        assertEquals(Duration.ofMillis(50), backoff.nextDelay(5, Duration.ofSeconds(3)));
    }

    @Test
    void rejectsNonPositiveDelay() {
        assertThrows(IllegalArgumentException.class, () -> new FixedDelayBackoff(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new FixedDelayBackoff(Duration.ofMillis(-1)));
    }
}
