package dev.trafficcontrol.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets exactly permittedCalls trial calls through, then decides from their
 * outcomes whether the dependency has recovered.
 *
 * Trial slots are taken with a decrement-if-positive CAS, so contention can
 * never admit one too many. Each trial call bumps failed/slow before it
 * bumps completed; the thread whose increment makes completed hit the
 * limit therefore sees every other trial's failed/slow update, and is the
 * one that evaluates and moves the breaker on.
 */
final class HalfOpenState implements BreakerState {

    private final long enteredAtNanos;
    private final int permittedCalls;
    private final AtomicInteger slotsLeft;
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger slow = new AtomicInteger();

    HalfOpenState(long enteredAtNanos, int permittedCalls) {
        this.enteredAtNanos = enteredAtNanos;
        this.permittedCalls = permittedCalls;
        this.slotsLeft = new AtomicInteger(permittedCalls);
    }

    @Override
    public CircuitBreaker.State name() {
        return CircuitBreaker.State.HALF_OPEN;
    }

    @Override
    public BreakerPermit tryAcquire(DefaultCircuitBreaker breaker) {
        long maxWait = breaker.maxWaitInHalfOpenNanos();
        long now = breaker.now();
        if (maxWait > 0 && now - enteredAtNanos >= maxWait) {
            // Trial calls never all reported back; give up on them and start a fresh OPEN wait.
            breaker.transition(this, new OpenState(now, snapshot()));
            return breaker.currentState().tryAcquire(breaker);
        }
        while (true) {
            int left = slotsLeft.get();
            if (left == 0) {
                return null;
            }
            if (slotsLeft.compareAndSet(left, left - 1)) {
                return new BreakerPermit(breaker, this);
            }
        }
    }

    @Override
    public void onResult(DefaultCircuitBreaker breaker, boolean failedCall, boolean slowCall) {
        if (failedCall) {
            failed.incrementAndGet();
        }
        if (slowCall) {
            slow.incrementAndGet();
        }
        if (completed.incrementAndGet() != permittedCalls) {
            return;
        }
        WindowSnapshot trial = new WindowSnapshot(permittedCalls, failed.get(), slow.get());
        BreakerState next = breaker.exceedsThresholds(trial)
                ? new OpenState(breaker.now(), trial)
                : new ClosedState(breaker.newWindow());
        breaker.transition(this, next);
    }

    @Override
    public void onRelease() {
        slotsLeft.incrementAndGet();
    }

    @Override
    public WindowSnapshot snapshot() {
        return new WindowSnapshot(completed.get(), failed.get(), slow.get());
    }
}
