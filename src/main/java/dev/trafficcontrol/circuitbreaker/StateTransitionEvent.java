package dev.trafficcontrol.circuitbreaker;

/** The breaker moved from one state to another. atNanos is from the breaker's NanoClock, not wall-clock time. */
public record StateTransitionEvent(CircuitBreaker.State from, CircuitBreaker.State to, long atNanos) {
}
