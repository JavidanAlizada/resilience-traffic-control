package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlidingWindowCounterRateLimiterTest {

    // permitsPerSecond=2 -> limit of 2 requests per rolling one-second window.
    private static RateLimiter newLimiter(FakeClock clock) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER)
                .permitsPerSecond(2)
                .burstCapacity(1) // ignored by window algorithms
                .clock(clock)
                .build();
        return RateLimiterFactory.create(config);
    }

    @Test
    void admitsUpToLimitWithinASingleWindow() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void deniesBoundaryStraddlingBurstThatFixedWindowWouldAdmit() {
        // Same scenario as FixedWindowRateLimiterTest's boundary-burst-flaw test —
        // the sliding counter is exactly what fixes it.
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        clock.set(999_999_999L);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire(), "window 1 now at its limit of 2");

        clock.set(1_000_000_000L); // 1ns later, new window — but previous window's
                                    // weight is still ~fully counted at this instant
        assertFalse(limiter.tryAcquire(),
                "denied: the previous window's count still carries full weight right at the boundary");
    }

    @Test
    void admitsAgainAsThePreviousWindowsWeightDecaysThroughTheWindow() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);
        limiter.tryAcquire();
        limiter.tryAcquire(); // window 1 at its limit of 2

        clock.set(1_000_000_000L); // start of window 2, previous weight ~2
        assertFalse(limiter.tryAcquire());

        clock.set(1_500_000_000L); // halfway through window 2, previous weight ~1
        assertTrue(limiter.tryAcquire(), "previous window's contribution has decayed enough to admit one more");
        assertFalse(limiter.tryAcquire(), "but not two more");
    }
}
