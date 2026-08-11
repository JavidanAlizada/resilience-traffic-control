package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The one listener this package ships directly: tallies admitted and
 * rejected permits so something real is wired to {@link RateLimiterListener}
 * out of the box, not just test doubles. Good enough to point a metrics
 * exporter at; swap in your own listener for anything fancier.
 */
public final class CountingRateLimiterListener implements RateLimiterListener {

    private final AtomicLong admitted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    @Override
    public void onAdmit(int permits) {
        admitted.addAndGet(permits);
    }

    @Override
    public void onReject(int permits) {
        rejected.addAndGet(permits);
    }

    public long admittedCount() {
        return admitted.get();
    }

    public long rejectedCount() {
        return rejected.get();
    }
}
