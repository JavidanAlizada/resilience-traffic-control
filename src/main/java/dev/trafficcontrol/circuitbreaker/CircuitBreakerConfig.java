package dev.trafficcontrol.circuitbreaker;

import dev.trafficcontrol.ratelimiter.NanoClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable, validated settings for a circuit breaker. Defaults follow
 * Resilience4j's where that's reasonable, so nothing behaves surprisingly;
 * the one deliberate difference is maxWaitDurationInHalfOpenState, which
 * defaults to 60s instead of "wait forever" so a lost trial call can't
 * wedge the breaker in HALF_OPEN.
 */
public final class CircuitBreakerConfig {

    private static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 100;

    private final double failureRateThreshold;
    private final double slowCallRateThreshold;
    private final Duration slowCallDurationThreshold;
    private final SlidingWindowType slidingWindowType;
    private final int slidingWindowSize;
    private final int minimumNumberOfCalls;
    private final Duration waitDurationInOpenState;
    private final int permittedCallsInHalfOpenState;
    private final Duration maxWaitDurationInHalfOpenState;
    private final Predicate<Throwable> failurePredicate;
    private final NanoClock clock;
    private final List<CircuitBreakerListener> listeners;

    private CircuitBreakerConfig(Builder builder, int minimumNumberOfCalls) {
        this.failureRateThreshold = builder.failureRateThreshold;
        this.slowCallRateThreshold = builder.slowCallRateThreshold;
        this.slowCallDurationThreshold = builder.slowCallDurationThreshold;
        this.slidingWindowType = builder.slidingWindowType;
        this.slidingWindowSize = builder.slidingWindowSize;
        this.minimumNumberOfCalls = minimumNumberOfCalls;
        this.waitDurationInOpenState = builder.waitDurationInOpenState;
        this.permittedCallsInHalfOpenState = builder.permittedCallsInHalfOpenState;
        this.maxWaitDurationInHalfOpenState = builder.maxWaitDurationInHalfOpenState;
        this.failurePredicate = builder.failurePredicate;
        this.clock = builder.clock;
        this.listeners = List.copyOf(builder.listeners);
    }

    /** Percentage in (0, 100]: trip when at least this share of calls in the window failed. */
    public double failureRateThreshold() {
        return failureRateThreshold;
    }

    /** Percentage in (0, 100]: trip when at least this share of calls were slow. 100 means "only if every call was". */
    public double slowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    public Duration slowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    public SlidingWindowType slidingWindowType() {
        return slidingWindowType;
    }

    /** Calls for COUNT_BASED, seconds for TIME_BASED. */
    public int slidingWindowSize() {
        return slidingWindowSize;
    }

    /** No trip decision is made until the window holds at least this many calls. */
    public int minimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public Duration waitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    public int permittedCallsInHalfOpenState() {
        return permittedCallsInHalfOpenState;
    }

    /** Zero means wait for the trial calls forever. */
    public Duration maxWaitDurationInHalfOpenState() {
        return maxWaitDurationInHalfOpenState;
    }

    public Predicate<Throwable> failurePredicate() {
        return failurePredicate;
    }

    public NanoClock clock() {
        return clock;
    }

    /** Unmodifiable, in registration order. */
    public List<CircuitBreakerListener> listeners() {
        return listeners;
    }

    public static CircuitBreakerConfig ofDefaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private double failureRateThreshold = 50;
        private double slowCallRateThreshold = 100;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(60);
        private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;
        private int slidingWindowSize = 100;
        private Integer minimumNumberOfCalls;
        private Duration waitDurationInOpenState = Duration.ofSeconds(60);
        private int permittedCallsInHalfOpenState = 10;
        private Duration maxWaitDurationInHalfOpenState = Duration.ofSeconds(60);
        private Predicate<Throwable> failurePredicate = failure -> true;
        private NanoClock clock = NanoClock.SYSTEM;
        private final List<CircuitBreakerListener> listeners = new ArrayList<>();

        private Builder() {
        }

        public Builder failureRateThreshold(double percent) {
            this.failureRateThreshold = requirePercent(percent, "failureRateThreshold");
            return this;
        }

        public Builder slowCallRateThreshold(double percent) {
            this.slowCallRateThreshold = requirePercent(percent, "slowCallRateThreshold");
            return this;
        }

        public Builder slowCallDurationThreshold(Duration threshold) {
            this.slowCallDurationThreshold = requirePositive(threshold, "slowCallDurationThreshold");
            return this;
        }

        public Builder slidingWindowType(SlidingWindowType type) {
            this.slidingWindowType = Objects.requireNonNull(type, "slidingWindowType");
            return this;
        }

        public Builder slidingWindowSize(int size) {
            this.slidingWindowSize = requireAtLeastOne(size, "slidingWindowSize");
            return this;
        }

        /** If left unset, defaults to 100, capped at the window size for a count-based window. */
        public Builder minimumNumberOfCalls(int calls) {
            this.minimumNumberOfCalls = requireAtLeastOne(calls, "minimumNumberOfCalls");
            return this;
        }

        public Builder waitDurationInOpenState(Duration wait) {
            this.waitDurationInOpenState = requirePositive(wait, "waitDurationInOpenState");
            return this;
        }

        public Builder permittedCallsInHalfOpenState(int calls) {
            this.permittedCallsInHalfOpenState = requireAtLeastOne(calls, "permittedCallsInHalfOpenState");
            return this;
        }

        public Builder maxWaitDurationInHalfOpenState(Duration wait) {
            Objects.requireNonNull(wait, "maxWaitDurationInHalfOpenState");
            if (wait.isNegative()) {
                throw new IllegalArgumentException("maxWaitDurationInHalfOpenState must be >= 0, was " + wait);
            }
            this.maxWaitDurationInHalfOpenState = wait;
            return this;
        }

        /** Which failures count against the dependency. One that doesn't match is recorded as a success. */
        public Builder failurePredicate(Predicate<Throwable> predicate) {
            this.failurePredicate = Objects.requireNonNull(predicate, "failurePredicate");
            return this;
        }

        public Builder clock(NanoClock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /** Adds a listener; call once per listener. Every breaker built from this config notifies all of them. */
        public Builder listener(CircuitBreakerListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        public CircuitBreakerConfig build() {
            boolean countBased = slidingWindowType == SlidingWindowType.COUNT_BASED;
            int minimumCalls;
            if (minimumNumberOfCalls == null) {
                minimumCalls = countBased
                        ? Math.min(DEFAULT_MINIMUM_NUMBER_OF_CALLS, slidingWindowSize)
                        : DEFAULT_MINIMUM_NUMBER_OF_CALLS;
            } else {
                minimumCalls = minimumNumberOfCalls;
            }
            if (countBased && minimumCalls > slidingWindowSize) {
                // The window could never hold enough calls, so the breaker could never trip.
                throw new IllegalArgumentException("minimumNumberOfCalls (" + minimumCalls
                        + ") can't exceed a count-based slidingWindowSize (" + slidingWindowSize + ")");
            }
            return new CircuitBreakerConfig(this, minimumCalls);
        }

        private static double requirePercent(double percent, String name) {
            if (!(percent > 0 && percent <= 100)) {
                throw new IllegalArgumentException(name + " must be in (0, 100], was " + percent);
            }
            return percent;
        }

        private static Duration requirePositive(Duration duration, String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive, was " + duration);
            }
            return duration;
        }

        private static int requireAtLeastOne(int value, String name) {
            if (value < 1) {
                throw new IllegalArgumentException(name + " must be >= 1, was " + value);
            }
            return value;
        }
    }
}
