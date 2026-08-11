package dev.trafficcontrol.ratelimiter;

/**
 * Always admits. Exists so "rate limiting is off for this environment"
 * can be a config choice (RateLimiters.unlimited()) instead of a null
 * check scattered through every caller.
 */
final class NoOpRateLimiter extends AbstractRateLimiter {

    static final RateLimiter INSTANCE = new NoOpRateLimiter();

    private NoOpRateLimiter() {
    }

    @Override
    public boolean tryAcquire(int permits) {
        requireValidPermits(permits);
        return true;
    }
}
