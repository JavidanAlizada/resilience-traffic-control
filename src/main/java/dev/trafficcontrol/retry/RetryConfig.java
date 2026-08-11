package dev.trafficcontrol.retry;

import dev.trafficcontrol.timeout.HashedWheelTimeoutScheduler;
import dev.trafficcontrol.timeout.TimeoutScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable, validated settings for a {@link RetryExecutor}. The
 * {@code scheduler} is the same {@link TimeoutScheduler} Milestone 2
 * built -- async retry needs exactly the "run this later, cancellable"
 * capability a timeout does, so it's reused rather than rebuilt.
 */
public final class RetryConfig {

    private final int maxAttempts;
    private final BackoffStrategy backoffStrategy;
    private final Predicate<Throwable> retryPredicate;
    private final TimeoutScheduler scheduler;

    private RetryConfig(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.backoffStrategy = builder.backoffStrategy;
        this.retryPredicate = builder.retryPredicate;
        this.scheduler = builder.scheduler;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public BackoffStrategy backoffStrategy() {
        return backoffStrategy;
    }

    public Predicate<Throwable> retryPredicate() {
        return retryPredicate;
    }

    public TimeoutScheduler scheduler() {
        return scheduler;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int maxAttempts = 3;
        private BackoffStrategy backoffStrategy =
                new ExponentialBackoff(Duration.ofMillis(100), Duration.ofSeconds(10));
        private Predicate<Throwable> retryPredicate = failure -> true;
        private TimeoutScheduler scheduler;

        private Builder() {
        }

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1, was " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder backoffStrategy(BackoffStrategy backoffStrategy) {
            this.backoffStrategy = Objects.requireNonNull(backoffStrategy, "backoffStrategy");
            return this;
        }

        /** No inference from exception type -- the caller states explicitly what's worth retrying. */
        public Builder retryPredicate(Predicate<Throwable> retryPredicate) {
            this.retryPredicate = Objects.requireNonNull(retryPredicate, "retryPredicate");
            return this;
        }

        public Builder scheduler(TimeoutScheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        public RetryConfig build() {
            if (scheduler == null) {
                scheduler = HashedWheelTimeoutScheduler.withDefaults();
            }
            return new RetryConfig(this);
        }
    }
}
