package dev.trafficcontrol.ratelimiter;

/**
 * One counter, reset every window boundary — kept on purpose to show its
 * own flaw: a burst straddling a boundary can slip through at up to 2x the
 * configured rate. Don't reach for this one.
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
