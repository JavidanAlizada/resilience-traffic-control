package dev.trafficcontrol.ratelimiter;

/**
 * Front door for this package. Covers the common cases in one line;
 * RateLimiterConfig.builder() is still there for anything these
 * shortcuts don't cover.
 */
public final class RateLimiters {

    private RateLimiters() {
    }

    public static RateLimiter of(RateLimiterConfig config) {
        return RateLimiterFactory.create(config);
    }

    public static RateLimiter gcra(double permitsPerSecond, long burstCapacity) {
        return of(configFor(RateLimiterAlgorithm.GCRA, permitsPerSecond, burstCapacity));
    }

    public static RateLimiter tokenBucket(double permitsPerSecond, long burstCapacity) {
        return of(configFor(RateLimiterAlgorithm.TOKEN_BUCKET, permitsPerSecond, burstCapacity));
    }

    public static RateLimiter fixedWindow(double permitsPerSecond) {
        return of(configFor(RateLimiterAlgorithm.FIXED_WINDOW, permitsPerSecond, 1));
    }

    public static RateLimiter slidingWindowCounter(double permitsPerSecond) {
        return of(configFor(RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER, permitsPerSecond, 1));
    }

    /** Rate limiting turned off. */
    public static RateLimiter unlimited() {
        return NoOpRateLimiter.INSTANCE;
    }

    private static RateLimiterConfig configFor(RateLimiterAlgorithm algorithm, double permitsPerSecond,
            long burstCapacity) {
        return RateLimiterConfig.builder()
                .algorithm(algorithm)
                .permitsPerSecond(permitsPerSecond)
                .burstCapacity(burstCapacity)
                .build();
    }
}
