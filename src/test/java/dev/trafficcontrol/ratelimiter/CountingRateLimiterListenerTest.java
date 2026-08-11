package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CountingRateLimiterListenerTest {

    @Test
    void tallyMatchesWhatTheDelegateActuallyDecided() {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.GCRA)
                .permitsPerSecond(1)
                .burstCapacity(2)
                .clock(new FakeClock(0))
                .build();
        CountingRateLimiterListener counts = new CountingRateLimiterListener();
        RateLimiter limiter = RateLimiters.observed(RateLimiterFactory.create(config), counts);

        limiter.tryAcquire(); // admitted
        limiter.tryAcquire(); // admitted, burst now exhausted
        limiter.tryAcquire(); // rejected
        limiter.tryAcquire(); // rejected

        assertEquals(2, counts.admittedCount());
        assertEquals(2, counts.rejectedCount());
    }
}
