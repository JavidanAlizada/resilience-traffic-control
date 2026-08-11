package dev.trafficcontrol.retry;

/** Turns a {@link RetryConfig} into a wired-up {@link RetryExecutor}. */
public final class RetryExecutorFactory {

    private RetryExecutorFactory() {
    }

    public static RetryExecutor create(RetryConfig config) {
        return new RetryExecutor(config);
    }
}
