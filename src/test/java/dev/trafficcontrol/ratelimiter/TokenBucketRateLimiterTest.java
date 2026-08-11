package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    // permitsPerSecond=10, burstCapacity=5 -> 1 token refills every 100ms.
    private static RateLimiter newLimiter(FakeClock clock) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.TOKEN_BUCKET)
                .permitsPerSecond(10)
                .burstCapacity(5)
                .clock(clock)
                .build();
        return RateLimiterFactory.create(config);
    }

    @Test
    void admitsUpToBurstCapacityThenDenies() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "token " + i + " of the initial burst");
        }
        assertFalse(limiter.tryAcquire(), "bucket exhausted");
    }

    @Test
    void refillsAtMillisecondGranularity() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire();
        }
        assertFalse(limiter.tryAcquire());

        clock.advance(99_000_000L); // 99ms — one millisecond short of a whole token
        assertFalse(limiter.tryAcquire(), "sub-token elapsed time doesn't refill");

        clock.advance(1_000_000L); // now 100ms total — exactly one refill interval
        assertTrue(limiter.tryAcquire(), "exactly one token available");
        assertFalse(limiter.tryAcquire(), "no further tokens without more elapsed time");
    }

    @Test
    void permitCostGreaterThanOneConsumesMultipleTokens() {
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = newLimiter(clock);

        assertTrue(limiter.tryAcquire(3), "3 of 5 initial tokens");
        assertFalse(limiter.tryAcquire(3), "only 2 tokens remain");
        assertTrue(limiter.tryAcquire(2), "the remaining 2 tokens");
    }

    @Test
    void rejectsNonPositivePermits() {
        RateLimiter limiter = newLimiter(new FakeClock(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
    }

    @Test
    void rejectsBurstCapacityTooLargeForPackedRepresentation() {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.TOKEN_BUCKET)
                .permitsPerSecond(10)
                .burstCapacity(Integer.MAX_VALUE + 1L)
                .clock(new FakeClock(0))
                .build();
        assertThrows(IllegalArgumentException.class, () -> RateLimiterFactory.create(config));
    }
}
