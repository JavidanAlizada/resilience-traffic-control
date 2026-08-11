package dev.trafficcontrol.ratelimiter;

/** Turns a config's algorithm choice into a concrete {@link RateLimiter}. */
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
