package dev.trafficcontrol.timeout;

/**
 * Runs {@code onTimeout} once, after {@code delayNanos}, unless cancelled
 * first. The strategy interface behind the three interchangeable scheduler
 * implementations.
 */
public interface TimeoutScheduler {

    Cancellable scheduleTimeout(long delayNanos, Runnable onTimeout);

    /** Releases background resources, if this implementation owns any. No-op by default. */
    default void close() {
    }
}
