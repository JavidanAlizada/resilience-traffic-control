package dev.trafficcontrol.circuitbreaker;

import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.CLOSED;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End to end against a simulated dependency that goes down, gets slow and
 * recovers. The simulated latency moves the fake clock, so "slow" is exact
 * and nothing sleeps.
 */
class CircuitBreakerFailureInjectionTest {

    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    private enum Mode {
        HEALTHY,
        DOWN,
        SLOW,
        SLOW_THEN_FAIL
    }

    private final FakeClock clock = new FakeClock(0);
    private final List<StateTransitionEvent> events = new ArrayList<>();
    private Mode mode = Mode.HEALTHY;
    private int dependencyCalls;

    private final CircuitBreaker breaker = CircuitBreakers.of(CircuitBreakerConfig.builder()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(1))
            .slowCallRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedCallsInHalfOpenState(3)
            .clock(clock)
            .listener(events::add)
            .build());

    private String dependency() throws IOException {
        dependencyCalls++;
        switch (mode) {
            case DOWN -> throw new IOException("connection refused");
            case SLOW -> clock.advance(2 * SECOND);
            case SLOW_THEN_FAIL -> {
                clock.advance(2 * SECOND);
                throw new IOException("timed out upstream");
            }
            case HEALTHY -> {
                // instant success
            }
        }
        return "ok";
    }

    /** Sends n requests through the breaker; returns how many it let through. */
    private int send(int n) {
        int admitted = 0;
        for (int i = 0; i < n; i++) {
            try {
                breaker.execute(this::dependency);
                admitted++;
            } catch (CallNotPermittedException e) {
                // expected while the breaker is shielding the dependency
            } catch (Exception dependencyFailure) {
                admitted++;
            }
        }
        return admitted;
    }

    @Test
    void outageTripsShieldsTheDependencyAndRecovers() {
        assertEquals(20, send(20), "healthy: everything goes through");

        mode = Mode.DOWN;
        send(20);
        assertEquals(OPEN, breaker.state());

        int callsBefore = dependencyCalls;
        assertEquals(0, send(100), "OPEN: nothing reaches the dependency");
        assertEquals(callsBefore, dependencyCalls);

        clock.advance(30 * SECOND); // still down at the first probe
        assertEquals(3, send(10), "HALF_OPEN: exactly the trial calls get through");
        assertEquals(OPEN, breaker.state());

        mode = Mode.HEALTHY;
        clock.advance(30 * SECOND);
        assertEquals(10, send(10), "recovered: 3 trials close it, the rest flow normally");
        assertEquals(CLOSED, breaker.state());

        assertEquals(List.of(
                new StateTransitionEvent(CLOSED, OPEN, 0),
                new StateTransitionEvent(OPEN, HALF_OPEN, 30 * SECOND),
                new StateTransitionEvent(HALF_OPEN, OPEN, 30 * SECOND),
                new StateTransitionEvent(OPEN, HALF_OPEN, 60 * SECOND),
                new StateTransitionEvent(HALF_OPEN, CLOSED, 60 * SECOND)), events);
    }

    @Test
    void slowButSuccessfulDependencyStillTrips() {
        mode = Mode.SLOW;

        send(10);

        assertEquals(OPEN, breaker.state());
        assertEquals(0, breaker.metrics().failedCalls(), "no call failed");
        assertEquals(10, breaker.metrics().slowCalls());
    }

    @Test
    void dependencyThatFailsAfterADelayCountsAsBothSlowAndFailed() {
        mode = Mode.SLOW_THEN_FAIL;

        send(10);

        CircuitBreakerMetrics metrics = breaker.metrics();
        assertEquals(OPEN, metrics.state());
        assertEquals(10, metrics.failedCalls());
        assertEquals(10, metrics.slowCalls());
    }
}
