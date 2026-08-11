package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimitersTest {

    @Test
    void unlimitedAlwaysAdmits() {
        RateLimiter limiter = RateLimiters.unlimited();
        for (int i = 0; i < 1000; i++) {
            assertTrue(limiter.tryAcquire());
        }
    }

    @Test
    void unlimitedStillRejectsInvalidPermits() {
        RateLimiter limiter = RateLimiters.unlimited();
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
    }

    @Test
    void gcraShortcutBehavesLikeTheBuilderEquivalent() {
        // permitsPerSecond=1, not 1000+: a ~1s refill interval leaves a huge
        // margin against real elapsed test time, so this stays deterministic
        // on a real clock without needing a FakeClock for a smoke test.
        RateLimiter limiter = RateLimiters.gcra(1, 2);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }
}
