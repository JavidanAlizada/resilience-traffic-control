package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class RateLimiterFactoryTest {

    @Test
    void createsMatchingImplementationPerAlgorithm() {
        assertInstanceOf(GcraRateLimiter.class, create(RateLimiterAlgorithm.GCRA));
        assertInstanceOf(TokenBucketRateLimiter.class, create(RateLimiterAlgorithm.TOKEN_BUCKET));
        assertInstanceOf(FixedWindowRateLimiter.class, create(RateLimiterAlgorithm.FIXED_WINDOW));
        assertInstanceOf(SlidingWindowCounterRateLimiter.class, create(RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER));
    }

    private static RateLimiter create(RateLimiterAlgorithm algorithm) {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(algorithm)
                .permitsPerSecond(10)
                .burstCapacity(10)
                .clock(new FakeClock(0))
                .build();
        return RateLimiterFactory.create(config);
    }
}
