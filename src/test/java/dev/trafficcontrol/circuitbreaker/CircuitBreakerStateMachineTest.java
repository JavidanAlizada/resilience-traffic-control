package dev.trafficcontrol.circuitbreaker;

import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.CLOSED;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Every transition and timing edge, driven through the permit API with a fake clock -- no sleeps. */
class CircuitBreakerStateMachineTest {

    private static final long WAIT_NANOS = Duration.ofSeconds(10).toNanos();

    private final FakeClock clock = new FakeClock(0);

    private CircuitBreakerConfig.Builder config() {
        return CircuitBreakerConfig.builder()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofNanos(WAIT_NANOS))
                .permittedCallsInHalfOpenState(3)
                .maxWaitDurationInHalfOpenState(Duration.ZERO)
                .clock(clock);
    }

    private static void record(CircuitBreaker breaker, int successes, int failures) {
        for (int i = 0; i < successes; i++) {
            breaker.acquirePermission().onSuccess(0);
        }
        for (int i = 0; i < failures; i++) {
            breaker.acquirePermission().onFailure(0, new RuntimeException("boom"));
        }
    }

    private CircuitBreaker openBreaker() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());
        record(breaker, 0, 10);
        assertEquals(OPEN, breaker.state());
        return breaker;
    }

    private CircuitBreaker halfOpenBreaker() {
        CircuitBreaker breaker = openBreaker();
        clock.advance(WAIT_NANOS);
        return breaker;
    }

    @Test
    void startsClosed() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());

        assertEquals(CLOSED, breaker.state());
        assertEquals(new CircuitBreakerMetrics(CLOSED, 0, 0, 0, 0.0, 0.0), breaker.metrics());
    }

    @Test
    void failureRateOneCallUnderThresholdStaysClosed() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());
        record(breaker, 6, 4); // 40%

        assertEquals(CLOSED, breaker.state());
    }

    @Test
    void failureRateExactlyAtThresholdTrips() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());
        record(breaker, 5, 5); // 50%

        assertEquals(OPEN, breaker.state());
    }

    @Test
    void noTripUntilMinimumNumberOfCallsRecorded() {
        CircuitBreaker breaker = CircuitBreakers.of(config().minimumNumberOfCalls(5).build());
        record(breaker, 0, 4);
        assertEquals(CLOSED, breaker.state(), "100% failures, but only 4 of the 5 required calls");

        record(breaker, 0, 1);
        assertEquals(OPEN, breaker.state());
    }

    @Test
    void slowCallRateTripsEvenWhenEverythingSucceeds() {
        CircuitBreaker breaker = CircuitBreakers.of(config()
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .slowCallRateThreshold(50)
                .build());
        long slow = Duration.ofSeconds(1).toNanos();
        for (int i = 0; i < 4; i++) {
            breaker.acquirePermission().onSuccess(slow);
        }
        for (int i = 0; i < 5; i++) {
            breaker.acquirePermission().onSuccess(slow - 1); // one nanosecond under: not slow
        }
        assertEquals(CLOSED, breaker.state());

        breaker.acquirePermission().onSuccess(slow);
        assertEquals(OPEN, breaker.state());
    }

    @Test
    void openRejectsAndReportsTheWindowAtTrip() {
        CircuitBreaker breaker = openBreaker();

        CallNotPermittedException rejected = assertThrows(CallNotPermittedException.class,
                breaker::acquirePermission);
        assertEquals(OPEN, rejected.state());
        assertEquals(new CircuitBreakerMetrics(OPEN, 10, 10, 0, 100.0, 0.0), breaker.metrics());
    }

    @Test
    void openWaitBoundary() {
        CircuitBreaker breaker = openBreaker();

        clock.advance(WAIT_NANOS - 1);
        assertThrows(CallNotPermittedException.class, breaker::acquirePermission, "1ns before the wait ends");
        assertEquals(OPEN, breaker.state());

        clock.advance(1);
        breaker.acquirePermission();
        assertEquals(HALF_OPEN, breaker.state());
    }

    @Test
    void idleOpenBreakerStaysOpenUntilTheNextCall() {
        CircuitBreaker breaker = openBreaker();
        clock.advance(10 * WAIT_NANOS);

        assertEquals(OPEN, breaker.state(), "no timer: the transition happens on the next acquire");
    }

    @Test
    void halfOpenAdmitsExactlyPermittedCalls() {
        CircuitBreaker breaker = halfOpenBreaker();
        for (int i = 0; i < 3; i++) {
            breaker.acquirePermission();
        }

        CallNotPermittedException rejected = assertThrows(CallNotPermittedException.class,
                breaker::acquirePermission);
        assertEquals(HALF_OPEN, rejected.state());
    }

    @Test
    void healthyTrialCallsCloseWithAnEmptyWindow() {
        CircuitBreaker breaker = halfOpenBreaker();
        record(breaker, 2, 1); // 33%, under the 50% threshold

        assertEquals(CLOSED, breaker.state());
        assertEquals(0, breaker.metrics().totalCalls());
    }

    @Test
    void unhealthyTrialCallsReopenAndRestartTheWait() {
        CircuitBreaker breaker = halfOpenBreaker();
        record(breaker, 1, 2); // 66%

        assertEquals(OPEN, breaker.state());
        clock.advance(WAIT_NANOS - 1);
        assertThrows(CallNotPermittedException.class, breaker::acquirePermission,
                "the wait restarts from the moment of reopening");
        clock.advance(1);
        breaker.acquirePermission();
        assertEquals(HALF_OPEN, breaker.state());
    }

    @Test
    void halfOpenMetricsShowTrialCallsSoFar() {
        CircuitBreaker breaker = halfOpenBreaker();
        record(breaker, 1, 1);

        assertEquals(new CircuitBreakerMetrics(HALF_OPEN, 2, 1, 0, 50.0, 0.0), breaker.metrics());
    }

    @Test
    void abandonedTrialCallsReopenAfterMaxWait() {
        long maxWait = Duration.ofSeconds(5).toNanos();
        CircuitBreaker breaker = CircuitBreakers.of(config()
                .maxWaitDurationInHalfOpenState(Duration.ofNanos(maxWait)).build());
        record(breaker, 0, 10);
        clock.advance(WAIT_NANOS);
        breaker.acquirePermission(); // a trial call that never reports back

        clock.advance(maxWait - 1);
        breaker.acquirePermission();
        assertEquals(HALF_OPEN, breaker.state());

        clock.advance(1);
        assertThrows(CallNotPermittedException.class, breaker::acquirePermission);
        assertEquals(OPEN, breaker.state());
    }

    @Test
    void zeroMaxWaitMeansHalfOpenWaitsForever() {
        CircuitBreaker breaker = halfOpenBreaker();
        for (int i = 0; i < 3; i++) {
            breaker.acquirePermission();
        }
        clock.advance(1_000 * WAIT_NANOS);

        assertThrows(CallNotPermittedException.class, breaker::acquirePermission);
        assertEquals(HALF_OPEN, breaker.state());
    }

    @Test
    void lateResultFromAnEarlierStateIsNotCountedAsATrial() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());
        CircuitBreaker.Permit slowClosedCall = breaker.acquirePermission();
        record(breaker, 0, 10);
        clock.advance(WAIT_NANOS);
        breaker.acquirePermission().onSuccess(0); // first trial call, moves to HALF_OPEN

        slowClosedCall.onFailure(0, new RuntimeException("finished late"));

        assertEquals(1, breaker.metrics().totalCalls(), "only the real trial call is counted");
        record(breaker, 2, 0);
        assertEquals(CLOSED, breaker.state());
    }

    @Test
    void onlyTheFirstOutcomeOnAPermitCounts() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());
        CircuitBreaker.Permit permit = breaker.acquirePermission();

        permit.onFailure(0, new RuntimeException());
        permit.onFailure(0, new RuntimeException());
        permit.onSuccess(0);
        permit.release();

        assertEquals(new CircuitBreakerMetrics(CLOSED, 1, 1, 0, 100.0, 0.0), breaker.metrics());
    }

    @Test
    void releaseGivesBackATrialSlotAndRecordsNothing() {
        CircuitBreaker breaker = halfOpenBreaker();
        CircuitBreaker.Permit first = breaker.acquirePermission();
        breaker.acquirePermission();
        breaker.acquirePermission();
        assertThrows(CallNotPermittedException.class, breaker::acquirePermission);

        first.release();

        breaker.acquirePermission();
        assertEquals(0, breaker.metrics().totalCalls());
    }

    @Test
    void failuresNotMatchingThePredicateCountAsSuccesses() {
        CircuitBreaker breaker = CircuitBreakers.of(config()
                .failurePredicate(failure -> !(failure instanceof IllegalArgumentException))
                .build());

        for (int i = 0; i < 10; i++) {
            breaker.acquirePermission().onFailure(0, new IllegalArgumentException("caller's fault"));
        }

        assertEquals(CLOSED, breaker.state());
        assertEquals(new CircuitBreakerMetrics(CLOSED, 10, 0, 0, 0.0, 0.0), breaker.metrics());
    }

    @Test
    void timeBasedBreakerTripsAndForgetsOldFailures() {
        long second = Duration.ofSeconds(1).toNanos();
        CircuitBreaker breaker = CircuitBreakers.of(config()
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(4)
                .build());
        record(breaker, 0, 3);
        clock.advance(5 * second); // those three age out
        record(breaker, 3, 1);
        assertEquals(CLOSED, breaker.state(), "25% over the current window");

        record(breaker, 0, 2); // 3 of 6
        assertEquals(OPEN, breaker.state());
    }
}
