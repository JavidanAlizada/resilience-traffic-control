package dev.trafficcontrol.circuitbreaker;

/** Every call goes through; outcomes feed the sliding window, which decides when to trip. */
final class ClosedState implements BreakerState {

    private final SlidingWindow window;

    ClosedState(SlidingWindow window) {
        this.window = window;
    }

    @Override
    public CircuitBreaker.State name() {
        return CircuitBreaker.State.CLOSED;
    }

    @Override
    public BreakerPermit tryAcquire(DefaultCircuitBreaker breaker) {
        return new BreakerPermit(breaker, this);
    }

    @Override
    public void onResult(DefaultCircuitBreaker breaker, boolean failed, boolean slow) {
        window.record(failed, slow);
        WindowSnapshot snapshot = window.snapshot();
        if (snapshot.totalCalls() >= breaker.minimumNumberOfCalls() && breaker.exceedsThresholds(snapshot)) {
            // Many threads can get here at once; the CAS lets exactly one of them trip the breaker.
            breaker.transition(this, new OpenState(breaker.now(), snapshot));
        }
    }

    @Override
    public void onRelease() {
    }

    @Override
    public WindowSnapshot snapshot() {
        return window.snapshot();
    }
}
