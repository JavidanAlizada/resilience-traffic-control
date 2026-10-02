package dev.trafficcontrol.circuitbreaker;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Remembers which state admitted the call and reports back to that state,
 * not to whatever is current when the call finishes. If the breaker has
 * moved on, the old state object is no longer reachable from the breaker,
 * so a late result lands somewhere harmless instead of polluting, say, the
 * next HALF_OPEN's trial tally.
 */
final class BreakerPermit implements CircuitBreaker.Permit {

    private final DefaultCircuitBreaker breaker;
    private final BreakerState issuedBy;
    private final AtomicBoolean done = new AtomicBoolean();

    BreakerPermit(DefaultCircuitBreaker breaker, BreakerState issuedBy) {
        this.breaker = breaker;
        this.issuedBy = issuedBy;
    }

    @Override
    public void onSuccess(long durationNanos) {
        if (done.compareAndSet(false, true)) {
            issuedBy.onResult(breaker, false, breaker.isSlow(durationNanos));
        }
    }

    @Override
    public void onFailure(long durationNanos, Throwable failure) {
        if (done.compareAndSet(false, true)) {
            boolean countsAsFailure = breaker.countsAsFailure(failure);
            issuedBy.onResult(breaker, countsAsFailure, breaker.isSlow(durationNanos));
        }
    }

    @Override
    public void release() {
        if (done.compareAndSet(false, true)) {
            issuedBy.onRelease();
        }
    }
}
