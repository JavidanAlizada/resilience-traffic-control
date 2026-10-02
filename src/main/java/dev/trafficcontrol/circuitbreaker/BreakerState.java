package dev.trafficcontrol.circuitbreaker;

/**
 * One state of the breaker, holding its own data: the closed state its
 * sliding window, the open state when it opened, the half-open state its
 * trial slots and tallies. A transition replaces the whole object with one
 * CAS, so no state can ever see fields left over from the previous one.
 */
sealed interface BreakerState permits ClosedState, OpenState, HalfOpenState {

    CircuitBreaker.State name();

    /** A permit for one call, or null if the call is rejected. May move the breaker on as a side effect. */
    BreakerPermit tryAcquire(DefaultCircuitBreaker breaker);

    void onResult(DefaultCircuitBreaker breaker, boolean failed, boolean slow);

    void onRelease();

    WindowSnapshot snapshot();
}
