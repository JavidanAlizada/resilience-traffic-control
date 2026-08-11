package dev.trafficcontrol.ratelimiter;

/**
 * Weights the previous window's count by how much of it still overlaps a
 * one-window lookback, instead of dropping it at the boundary like
 * {@link FixedWindowRateLimiter} does. Assumes requests were spread evenly
 * across that window — an approximation, but the one most API gateways
 * actually run.
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
