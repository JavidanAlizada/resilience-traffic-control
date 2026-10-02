package dev.trafficcontrol.circuitbreaker;

/**
 * Point-in-time view of a breaker. In CLOSED the counts are the sliding
 * window's; in OPEN they're the window as it was when the breaker tripped;
 * in HALF_OPEN they're the trial calls recorded so far.
 */
public record CircuitBreakerMetrics(
        CircuitBreaker.State state,
        int totalCalls,
        int failedCalls,
        int slowCalls,
        double failureRate,
        double slowCallRate) {

    static CircuitBreakerMetrics of(CircuitBreaker.State state, WindowSnapshot snapshot) {
        return new CircuitBreakerMetrics(state, snapshot.totalCalls(), snapshot.failedCalls(),
                snapshot.slowCalls(), snapshot.failureRate(), snapshot.slowCallRate());
    }
}
