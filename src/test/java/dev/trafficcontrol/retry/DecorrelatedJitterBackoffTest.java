package dev.trafficcontrol.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DecorrelatedJitterBackoffTest {

    @Test
    void firstCallUsesBaseDelayAsTheBasisWhenThereIsNoPreviousDelay() {
        BackoffStrategy backoff = new DecorrelatedJitterBackoff(Duration.ofMillis(100), Duration.ofSeconds(10));
        long lowNanos = Duration.ofMillis(100).toNanos();
        long highNanos = Duration.ofMillis(300).toNanos(); // basis(100ms) * 3

        for (int i = 0; i < 1000; i++) {
            long sampleNanos = backoff.nextDelay(1, null).toNanos();
            assertTrue(sampleNanos >= lowNanos && sampleNanos <= highNanos,
                    "sample " + sampleNanos + " outside [" + lowNanos + ", " + highNanos + "]");
        }
    }

    @Test
    void laterCallsGrowTheRangeFromThePreviousDelay() {
        BackoffStrategy backoff = new DecorrelatedJitterBackoff(Duration.ofMillis(100), Duration.ofSeconds(10));
        Duration previous = Duration.ofMillis(500);
        long lowNanos = Duration.ofMillis(100).toNanos();
        long highNanos = Duration.ofMillis(1500).toNanos(); // previous(500ms) * 3

        for (int i = 0; i < 1000; i++) {
            long sampleNanos = backoff.nextDelay(3, previous).toNanos();
            assertTrue(sampleNanos >= lowNanos && sampleNanos <= highNanos,
                    "sample " + sampleNanos + " outside [" + lowNanos + ", " + highNanos + "]");
        }
    }

    @Test
    void neverExceedsMaxDelayEvenWhenThePreviousDelayIsLarge() {
        BackoffStrategy backoff = new DecorrelatedJitterBackoff(Duration.ofMillis(100), Duration.ofSeconds(10));
        Duration previous = Duration.ofSeconds(5); // *3 = 15s, exceeds the 10s maxDelay

        for (int i = 0; i < 1000; i++) {
            long sampleNanos = backoff.nextDelay(5, previous).toNanos();
            assertTrue(sampleNanos <= Duration.ofSeconds(10).toNanos());
        }
    }

    @Test
    void collapsesToBaseDelayWhenMaxDelayEqualsBaseDelay() {
        BackoffStrategy backoff = new DecorrelatedJitterBackoff(Duration.ofMillis(100), Duration.ofMillis(100));
        assertEquals(Duration.ofMillis(100), backoff.nextDelay(1, null));
        assertEquals(Duration.ofMillis(100), backoff.nextDelay(4, Duration.ofMillis(100)));
    }
}
