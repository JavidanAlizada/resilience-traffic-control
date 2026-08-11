package dev.trafficcontrol.ratelimiter;

/**
 * Factory Method: maps {@link RateLimiterConfig#algorithm()} to the
 * matching concrete {@link RateLimiter}. This is the one place that knows
 * about the concrete implementation classes — everything else in this
 * package (and every caller) depends only on the {@link RateLimiter}
 * interface.
 */
public final class RateLimiterFactory {

    private RateLimiterFactory() {
    }

    public static RateLimiter create(RateLimiterConfig config) {
        return switch (config.algorithm()) {
            case GCRA -> new GcraRateLimiter(config);
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(config);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(config);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(config);
        };
    }
}
