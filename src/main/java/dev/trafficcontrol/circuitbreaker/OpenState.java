package dev.trafficcontrol.circuitbreaker;

/**
 * Rejects everything (Balking) until the wait duration has passed. There's
 * no timer: the first acquire after the wait moves the breaker to
 * HALF_OPEN itself, so state() keeps reporting OPEN on an idle breaker
 * until the next call arrives.
 */
final class OpenState implements BreakerState {

    private final long openedAtNanos;
    private final WindowSnapshot windowAtTrip;

    OpenState(long openedAtNanos, WindowSnapshot windowAtTrip) {
        this.openedAtNanos = openedAtNanos;
        this.windowAtTrip = windowAtTrip;
    }

    @Override
    public CircuitBreaker.State name() {
        return CircuitBreaker.State.OPEN;
    }

    @Override
    public BreakerPermit tryAcquire(DefaultCircuitBreaker breaker) {
        long now = breaker.now();
        if (now - openedAtNanos < breaker.waitInOpenNanos()) {
            return null;
        }
        // Whether this thread wins the CAS or another one already moved the
        // breaker on, the call is decided by whatever state is current now.
        breaker.transition(this, new HalfOpenState(now, breaker.permittedCallsInHalfOpen()));
        return breaker.currentState().tryAcquire(breaker);
    }

    @Override
    public void onResult(DefaultCircuitBreaker breaker, boolean failed, boolean slow) {
        // Never issues permits, so nothing is ever recorded here.
    }

    @Override
    public void onRelease() {
    }

    @Override
    public WindowSnapshot snapshot() {
        return windowAtTrip;
    }
}
