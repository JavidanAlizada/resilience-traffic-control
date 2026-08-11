package dev.trafficcontrol.retry;

import java.time.Duration;

/** Front door for this package, same shape as RateLimiters and covering the common cases in one line. */
public final class Retries {

    private Retries() {
    }

    public static RetryExecutor of(RetryConfig config) {
        return RetryExecutorFactory.create(config);
    }

    public static RetryExecutor fixedDelay(int maxAttempts, Duration delay) {
        return of(RetryConfig.builder()
                .maxAttempts(maxAttempts)
                .backoffStrategy(new FixedDelayBackoff(delay))
                .build());
    }

    public static RetryExecutor exponentialBackoff(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        return of(RetryConfig.builder()
                .maxAttempts(maxAttempts)
                .backoffStrategy(new ExponentialBackoff(baseDelay, maxDelay))
                .build());
    }

    public static RetryExecutor fullJitter(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        return of(RetryConfig.builder()
                .maxAttempts(maxAttempts)
                .backoffStrategy(new FullJitterBackoff(baseDelay, maxDelay))
                .build());
    }

    public static RetryExecutor equalJitter(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        return of(RetryConfig.builder()
                .maxAttempts(maxAttempts)
                .backoffStrategy(new EqualJitterBackoff(baseDelay, maxDelay))
                .build());
    }

    public static RetryExecutor decorrelatedJitter(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        return of(RetryConfig.builder()
                .maxAttempts(maxAttempts)
                .backoffStrategy(new DecorrelatedJitterBackoff(baseDelay, maxDelay))
                .build());
    }
}
