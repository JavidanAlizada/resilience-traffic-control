package dev.trafficcontrol.ratelimiter;

/**
 * Approximate, O(1)-memory rate limiter: weights the previous window's
 * count by how much of it still falls inside a one-window lookback from
 * now, instead of discarding it outright at the boundary like
 * {@link FixedWindowRateLimiter}. Assumes requests were spread uniformly
 * across the previous window — an approximation, not an exact sliding-log,
 * but the same one most production API gateways actually run.
 */
final class SlidingWindowCounterRateLimiter extends AbstractWindowRateLimiter {

    SlidingWindowCounterRateLimiter(RateLimiterConfig config) {
        super(config);
    }

    @Override
    long estimatedCount(long previousCount, long currentCount, double windowProgress) {
        double overlapFraction = 1.0 - windowProgress;
        return currentCount + Math.round(previousCount * overlapFraction);
    }
}
