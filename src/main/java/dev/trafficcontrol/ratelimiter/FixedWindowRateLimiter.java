package dev.trafficcontrol.ratelimiter;

/**
 * Naive baseline: one counter reset every window boundary. Deliberately
 * kept, not to be reached for — see docs/algorithms/sliding-window-rate-limiter.md
 * for the boundary-burst flaw this demonstrates: up to 2x the configured
 * rate can be admitted in a short span straddling a window boundary,
 * because the previous window's count is discarded outright instead of
 * blended in.
 */
final class FixedWindowRateLimiter extends AbstractWindowRateLimiter {

    FixedWindowRateLimiter(RateLimiterConfig config) {
        super(config);
    }

    @Override
    long estimatedCount(long previousCount, long currentCount, double windowProgress) {
        return currentCount;
    }
}
