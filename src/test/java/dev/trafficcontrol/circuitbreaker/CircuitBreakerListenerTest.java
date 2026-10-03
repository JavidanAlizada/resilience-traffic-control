package dev.trafficcontrol.circuitbreaker;

import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.CLOSED;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CircuitBreakerListenerTest {

    private static final long WAIT_NANOS = Duration.ofSeconds(10).toNanos();

    private final FakeClock clock = new FakeClock(0);
    private final List<StateTransitionEvent> events = new ArrayList<>();

    private CircuitBreakerConfig.Builder config() {
        return CircuitBreakerConfig.builder()
                .slidingWindowSize(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofNanos(WAIT_NANOS))
                .permittedCallsInHalfOpenState(1)
                .clock(clock)
                .listener(events::add);
    }

    private static void fail(CircuitBreaker breaker, int times) {
        for (int i = 0; i < times; i++) {
            breaker.acquirePermission().onFailure(0, new IOException());
        }
    }

    @Test
    void reportsEachTransitionOfAFullCycleWithItsTime() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());

        fail(breaker, 2);
        clock.advance(WAIT_NANOS);
        breaker.acquirePermission().onSuccess(0);

        assertEquals(List.of(
                new StateTransitionEvent(CLOSED, OPEN, 0),
                new StateTransitionEvent(OPEN, HALF_OPEN, WAIT_NANOS),
                new StateTransitionEvent(HALF_OPEN, CLOSED, WAIT_NANOS)), events);
    }

    @Test
    void reportsAFailedTrialReopening() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());
        fail(breaker, 2);
        clock.advance(WAIT_NANOS);
        events.clear();

        fail(breaker, 1);

        assertEquals(List.of(
                new StateTransitionEvent(OPEN, HALF_OPEN, WAIT_NANOS),
                new StateTransitionEvent(HALF_OPEN, OPEN, WAIT_NANOS)), events);
    }

    @Test
    void reportsHalfOpenGivingUpOnAbandonedTrials() {
        long maxWait = Duration.ofSeconds(5).toNanos();
        CircuitBreaker breaker = CircuitBreakers.of(config()
                .maxWaitDurationInHalfOpenState(Duration.ofNanos(maxWait)).build());
        fail(breaker, 2);
        clock.advance(WAIT_NANOS);
        breaker.acquirePermission(); // never reports back
        events.clear();

        clock.advance(maxWait);
        assertThrows(CallNotPermittedException.class, breaker::acquirePermission);

        assertEquals(List.of(new StateTransitionEvent(HALF_OPEN, OPEN, WAIT_NANOS + maxWait)), events);
    }

    @Test
    void noEventsWithoutATransition() {
        CircuitBreaker breaker = CircuitBreakers.of(config().build());
        breaker.acquirePermission().onSuccess(0);
        breaker.acquirePermission().onSuccess(0);
        fail(breaker, 1); // 1 of the last 2: trips
        events.clear();

        assertThrows(CallNotPermittedException.class, breaker::acquirePermission);
        assertEquals(OPEN, breaker.state());
        assertTrue(events.isEmpty(), "rejections while OPEN are not transitions");
    }

    @Test
    void listenerCanReadWhyTheBreakerOpened() {
        List<CircuitBreakerMetrics> seen = new ArrayList<>();
        CircuitBreaker[] breaker = new CircuitBreaker[1];
        breaker[0] = CircuitBreakers.of(config().listener(event -> seen.add(breaker[0].metrics())).build());

        fail(breaker[0], 2);

        assertEquals(List.of(new CircuitBreakerMetrics(OPEN, 2, 2, 0, 100.0, 0.0)), seen);
    }

    @Test
    void throwingListenerDoesNotFailTheCallOrSilenceLaterListeners() throws Exception {
        List<String> order = new ArrayList<>();
        CircuitBreaker breaker = CircuitBreakers.of(config()
                .listener(event -> order.add("first"))
                .listener(event -> {
                    throw new IllegalStateException("broken listener");
                })
                .listener(event -> order.add("third"))
                .build());

        breaker.execute(() -> "ok");
        IOException failure = assertThrows(IOException.class, () -> breaker.execute(() -> {
            throw new IOException("the dependency's failure, not the listener's");
        }));

        assertEquals("the dependency's failure, not the listener's", failure.getMessage());
        assertEquals(OPEN, breaker.state());
        assertEquals(List.of("first", "third"), order);
    }

    @Test
    void configListenersAreImmutableAndNonNull() {
        CircuitBreakerConfig config = config().build();

        assertThrows(UnsupportedOperationException.class, () -> config.listeners().add(events::add));
        assertThrows(NullPointerException.class, () -> CircuitBreakerConfig.builder().listener(null));
    }
}
