package dev.trafficcontrol.circuitbreaker;

import java.time.Duration;

/** Front door for this package, same shape as RateLimiters and Retries. */
public final class CircuitBreakers {

    private CircuitBreakers() {
    }

    public static CircuitBreaker of(CircuitBreakerConfig config) {
        return CircuitBreakerFactory.create(config);
    }

    public static CircuitBreaker ofDefaults() {
        return of(CircuitBreakerConfig.ofDefaults());
    }

    /** Trip when failureRatePercent of the last windowSize calls failed; stay open for openFor. */
    public static CircuitBreaker countBased(int windowSize, double failureRatePercent, Duration openFor) {
        return of(CircuitBreakerConfig.builder()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .failureRateThreshold(failureRatePercent)
                .waitDurationInOpenState(openFor)
                .build());
    }

    /** Trip when failureRatePercent of the calls in the last windowSeconds failed; stay open for openFor. */
    public static CircuitBreaker timeBased(int windowSeconds, double failureRatePercent, Duration openFor) {
        return of(CircuitBreakerConfig.builder()
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(windowSeconds)
                .failureRateThreshold(failureRatePercent)
                .waitDurationInOpenState(openFor)
                .build());
    }
}
