package dev.trafficcontrol.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CircuitBreakerConfigTest {

    @Test
    void defaults() {
        CircuitBreakerConfig config = CircuitBreakerConfig.ofDefaults();

        assertEquals(50.0, config.failureRateThreshold());
        assertEquals(100.0, config.slowCallRateThreshold());
        assertEquals(Duration.ofSeconds(60), config.slowCallDurationThreshold());
        assertEquals(SlidingWindowType.COUNT_BASED, config.slidingWindowType());
        assertEquals(100, config.slidingWindowSize());
        assertEquals(100, config.minimumNumberOfCalls());
        assertEquals(Duration.ofSeconds(60), config.waitDurationInOpenState());
        assertEquals(10, config.permittedCallsInHalfOpenState());
        assertEquals(Duration.ofSeconds(60), config.maxWaitDurationInHalfOpenState());
    }

    @Test
    void defaultMinimumCallsIsCappedAtACountBasedWindowSize() {
        assertEquals(20, CircuitBreakerConfig.builder().slidingWindowSize(20).build().minimumNumberOfCalls());
    }

    @Test
    void defaultMinimumCallsIsNotCappedForATimeBasedWindow() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(20)
                .build();

        assertEquals(100, config.minimumNumberOfCalls(), "20 seconds can hold far more than 20 calls");
    }

    @Test
    void explicitMinimumCallsAboveACountBasedWindowIsRejected() {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(11);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void validationDoesNotDependOnSetterOrder() {
        CircuitBreakerConfig config = CircuitBreakerConfig.builder()
                .minimumNumberOfCalls(150)
                .slidingWindowSize(200)
                .build();

        assertEquals(150, config.minimumNumberOfCalls());
    }

    @Test
    void ratesMustBeInZeroExclusiveToHundredInclusive() {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.failureRateThreshold(0));
        assertThrows(IllegalArgumentException.class, () -> builder.failureRateThreshold(100.01));
        assertThrows(IllegalArgumentException.class, () -> builder.failureRateThreshold(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> builder.slowCallRateThreshold(-1));
        builder.failureRateThreshold(100).slowCallRateThreshold(0.5);
    }

    @Test
    void durationsMustBePositive() {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.waitDurationInOpenState(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.slowCallDurationThreshold(Duration.ofMillis(-1)));
        assertThrows(NullPointerException.class, () -> builder.waitDurationInOpenState(null));
    }

    @Test
    void maxWaitInHalfOpenAllowsZeroButNotNegative() {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder();

        builder.maxWaitDurationInHalfOpenState(Duration.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> builder.maxWaitDurationInHalfOpenState(Duration.ofSeconds(-1)));
    }

    @Test
    void countsMustBeAtLeastOne() {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.slidingWindowSize(0));
        assertThrows(IllegalArgumentException.class, () -> builder.minimumNumberOfCalls(0));
        assertThrows(IllegalArgumentException.class, () -> builder.permittedCallsInHalfOpenState(0));
    }

    @Test
    void referencesMustNotBeNull() {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder();

        assertThrows(NullPointerException.class, () -> builder.slidingWindowType(null));
        assertThrows(NullPointerException.class, () -> builder.failurePredicate(null));
        assertThrows(NullPointerException.class, () -> builder.clock(null));
    }

    @Test
    void facadeAppliesItsArguments() {
        CircuitBreaker countBased = CircuitBreakers.countBased(4, 50, Duration.ofSeconds(1));
        for (int i = 0; i < 2; i++) {
            countBased.acquirePermission().onSuccess(0);
            countBased.acquirePermission().onFailure(0, new RuntimeException());
        }
        assertEquals(CircuitBreaker.State.OPEN, countBased.state());

        assertEquals(CircuitBreaker.State.CLOSED, CircuitBreakers.ofDefaults().state());
        assertEquals(CircuitBreaker.State.CLOSED,
                CircuitBreakers.timeBased(10, 50, Duration.ofSeconds(1)).state());
    }
}
