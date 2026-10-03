package dev.trafficcontrol.circuitbreaker;

/**
 * Told about every state transition, synchronously, on the thread whose
 * CAS made it -- which is usually a caller's thread in the middle of a
 * request, so keep it fast and hand anything slow off elsewhere.
 *
 * Each transition is reported exactly once. Two transitions made by
 * different threads in quick succession can be reported out of order;
 * from/to on each event says which is which. A listener that throws is
 * ignored, so it can never fail the call that triggered the transition.
 */
@FunctionalInterface
public interface CircuitBreakerListener {

    void onStateTransition(StateTransitionEvent event);
}
