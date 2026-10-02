package dev.trafficcontrol.circuitbreaker;

/**
 * The breaker refused the call without making it. Unchecked, and its own
 * type, so a retry predicate can tell "the breaker said no" apart from a
 * real dependency failure and stop retrying.
 */
public final class CallNotPermittedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CircuitBreaker.State state;

    CallNotPermittedException(CircuitBreaker.State state) {
        super("circuit breaker is " + state + ", call not permitted");
        this.state = state;
    }

    public CircuitBreaker.State state() {
        return state;
    }
}
