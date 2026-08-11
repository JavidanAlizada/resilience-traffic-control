package dev.trafficcontrol.retry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EqualJitterBackoffTest {

    @Test
    void staysWithinHalfCapToCapAcrossManySamples() {
        BackoffStrategy backoff = new EqualJitterBackoff(Duration.ofMillis(100), Duration.ofMillis(1000));
        long capNanos = Duration.ofMillis(800).toNanos(); // exponentialCap(4) = 100 * 2^3 = 800ms
        long halfNanos = capNanos / 2;

        for (int i = 0; i < 1000; i++) {
            long sampleNanos = backoff.nextDelay(4, null).toNanos();
            assertTrue(sampleNanos >= halfNanos && sampleNanos < capNanos,
                    "sample " + sampleNanos + " outside [" + halfNanos + ", " + capNanos + ")");
        }
    }
}
