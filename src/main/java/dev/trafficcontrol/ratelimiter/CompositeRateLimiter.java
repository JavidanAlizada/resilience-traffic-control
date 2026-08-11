package dev.trafficcontrol.ratelimiter;

import java.util.List;

/**
 * Requires every child limiter to admit — the common "100/sec AND
 * 1000/min" style of tiered limiting.
 *
 * <p>Honest caveat: {@code tryAcquire} isn't atomic across children. If
 * the third of four limiters says no, the first two have already spent a
 * permit for a request that ends up rejected overall. Milestone 1's
 * {@code RateLimiter} contract is a single non-blocking check with no
 * reserve/commit step to undo that with, so a small amount of wasted
 * capacity under composition is a known, accepted limitation, not a bug —
 * put the tightest, cheapest-to-fail limiter first to minimize it.
 */
public final class CompositeRateLimiter implements RateLimiter {

    private final List<RateLimiter> limiters;

    private CompositeRateLimiter(List<RateLimiter> limiters) {
        this.limiters = limiters;
    }

    public static CompositeRateLimiter allOf(RateLimiter... limiters) {
        if (limiters.length == 0) {
            throw new IllegalArgumentException("at least one limiter is required");
        }
        return new CompositeRateLimiter(List.of(limiters));
    }

    @Override
    public boolean tryAcquire(int permits) {
        for (RateLimiter limiter : limiters) {
            if (!limiter.tryAcquire(permits)) {
                return false;
            }
        }
        return true;
    }
}
