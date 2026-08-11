package dev.trafficcontrol.retry;

/** Turns a RetryConfig into a wired-up RetryExecutor. */
public final class RetryExecutorFactory {

    private RetryExecutorFactory() {
    }

    public static RetryExecutor create(RetryConfig config) {
        return new RetryExecutor(config);
    }
}
