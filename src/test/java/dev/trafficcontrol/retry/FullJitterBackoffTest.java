package dev.trafficcontrol.retry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FullJitterBackoffTest {

    @Test
    void staysWithinZeroToCapAcrossManySamples() {
        BackoffStrategy backoff = new FullJitterBackoff(Duration.ofMillis(100), Duration.ofMillis(1000));
        long capNanos = Duration.ofMillis(800).toNanos(); // exponentialCap(4) = 100 * 2^3 = 800ms

        for (int i = 0; i < 1000; i++) {
            long sampleNanos = backoff.nextDelay(4, null).toNanos();
            assertTrue(sampleNanos >= 0 && sampleNanos < capNanos,
                    "sample " + sampleNanos + " outside [0, " + capNanos + ")");
        }
    }
}
