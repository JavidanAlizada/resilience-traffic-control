package dev.trafficcontrol.circuitbreaker;

import java.util.function.Supplier;

/** Turns a CircuitBreakerConfig into a breaker, picking the sliding window from the config's type. */
public final class CircuitBreakerFactory {

    private CircuitBreakerFactory() {
    }

    public static CircuitBreaker create(CircuitBreakerConfig config) {
        return new DefaultCircuitBreaker(config, windowFactory(config));
    }

    // A supplier rather than one window: every HALF_OPEN -> CLOSED transition starts from an empty window.
    private static Supplier<SlidingWindow> windowFactory(CircuitBreakerConfig config) {
        int size = config.slidingWindowSize();
        return switch (config.slidingWindowType()) {
            case COUNT_BASED -> () -> new CountBasedSlidingWindow(size);
            case TIME_BASED -> () -> new TimeBasedSlidingWindow(size, config.clock());
        };
    }
}
