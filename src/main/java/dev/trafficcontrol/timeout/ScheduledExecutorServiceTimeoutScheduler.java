package dev.trafficcontrol.timeout;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Baseline: hands scheduling off to the JDK's own {@code ScheduledThreadPoolExecutor}
 * (a DelayQueue/heap under the hood -- O(log n) to schedule or cancel).
 */
public final class ScheduledExecutorServiceTimeoutScheduler implements TimeoutScheduler {

    private final ScheduledExecutorService executor;

    public ScheduledExecutorServiceTimeoutScheduler(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static ScheduledExecutorServiceTimeoutScheduler withDaemonThread() {
        return new ScheduledExecutorServiceTimeoutScheduler(
                Executors.newSingleThreadScheduledExecutor(DaemonThreads.factory("timeout-scheduled-executor")));
    }

    @Override
    public Cancellable scheduleTimeout(long delayNanos, Runnable onTimeout) {
        ScheduledFuture<?> future = executor.schedule(onTimeout, delayNanos, TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
