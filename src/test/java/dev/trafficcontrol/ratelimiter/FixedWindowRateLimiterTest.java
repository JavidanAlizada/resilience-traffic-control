package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    // permitsPerSecond=2 -> limit of 2 requests per fixed one-second window.
    private static RateLimiter newLimiter(FakeClock clock) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.FIXED_WINDOW)
                .permitsPerSecond(2)
                .burstCapacity(1) // ignored by window algorithms, see RateLimiterConfig javadoc
                .clock(clock)
                .build();
        return RateLimiterFactory.create(config);
    }

    @Test
    void admitsUpToLimitPerWindowThenDenies() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "3rd request in the same window exceeds the limit of 2");
    }

    @Test
    void boundaryBurstFlaw_admitsDoubleTheRateAcrossAWindowBoundary() {
        // This is the documented flaw (doc 02, Section 2): the algorithm has no
        // memory of the previous window, so a burst straddling a boundary can
        // admit up to 2x the configured rate within a real span of nanoseconds.
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        clock.set(999_999_999L); // 1ns before window 1 closes
        assertTrue(limiter.tryAcquire(), "1st request, tail of window 1");
        assertTrue(limiter.tryAcquire(), "2nd request, tail of window 1 — window 1 now at its limit of 2");

        clock.set(1_000_000_000L); // 1ns later: window 2 has begun
        assertTrue(limiter.tryAcquire(), "3rd request, head of window 2 — the flaw: window 1's count is discarded");
        assertTrue(limiter.tryAcquire(), "4th request — 4 requests admitted in a 1ns span against a 2/sec limit");
    }

    @Test
    void windowResetsIndependentlyOfRequestVolume() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertFalse(limiter.tryAcquire());

        clock.advance(1_000_000_000L);
        assertTrue(limiter.tryAcquire(), "new window, count reset");
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "limit reached again in the new window");
    }
}
