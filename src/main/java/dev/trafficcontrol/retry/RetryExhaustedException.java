package dev.trafficcontrol.retry;

/**
 * Thrown when every configured attempt failed. Every prior attempt's
 * failure is attached via getSuppressed() (the last one is the cause) --
 * the JDK's own mechanism for "here's the primary failure plus everything
 * that led up to it," not a bespoke errors() accessor.
 */
public final class RetryExhaustedException extends Exception {

    RetryExhaustedException(int attempts, Throwable lastFailure) {
        super("retry exhausted after " + attempts + " attempt(s)", lastFailure);
    }
}
