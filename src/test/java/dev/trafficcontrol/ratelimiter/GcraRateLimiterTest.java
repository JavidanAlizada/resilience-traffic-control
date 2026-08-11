package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GcraRateLimiterTest {

    // permitsPerSecond=1 -> emissionInterval = 1s; burstCapacity=2 -> tolerance = 1s.
    // Chosen so every boundary lands on a clean, human-checkable nanosecond value.
    private static RateLimiter newLimiter(FakeClock clock) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.GCRA)
                .permitsPerSecond(1)
                .burstCapacity(2)
                .clock(clock)
                .build();
        return RateLimiterFactory.create(config);
    }

    @Test
    void admitsUpToBurstCapacityThenDenies() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        assertTrue(limiter.tryAcquire(), "1st request, bucket starts full");
        assertTrue(limiter.tryAcquire(), "2nd request, still within burst capacity of 2");
        assertFalse(limiter.tryAcquire(), "3rd request exceeds burst capacity with zero elapsed time");
    }

    @Test
    void deniesOneNanosecondBeforeRefillAndAdmitsExactlyAtIt() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertFalse(limiter.tryAcquire(), "burst exhausted");

        clock.set(999_999_999L); // 1ns short of the 1s refill interval
        assertFalse(limiter.tryAcquire(), "one nanosecond too early");

        clock.set(1_000_000_000L); // exactly one emission interval elapsed
        assertTrue(limiter.tryAcquire(), "exactly at the refill boundary");
    }

    @Test
    void refillsOverSimulatedTimeWithoutSleeping() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertFalse(limiter.tryAcquire());

        clock.advance(1_000_000_000L);
        assertTrue(limiter.tryAcquire(), "one emission interval later, exactly one more permit available");
        assertFalse(limiter.tryAcquire(), "no further permits without more elapsed time");
    }

    @Test
    void permitCostGreaterThanOneConsumesMultipleTokens() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        assertTrue(limiter.tryAcquire(2), "costs both burst tokens at once");
        assertFalse(limiter.tryAcquire(1), "no tokens left");
    }

    @Test
    void rejectsNonPositivePermits() {
        RateLimiter limiter = newLimiter(new FakeClock(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(-1));
    }
}
