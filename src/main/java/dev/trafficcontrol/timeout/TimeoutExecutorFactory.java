package dev.trafficcontrol.timeout;

/** Turns a config's algorithm choice into a wired-up TimeoutExecutor. */
public final class TimeoutExecutorFactory {

    private TimeoutExecutorFactory() {
    }

    public static TimeoutExecutor create(TimeoutConfig config) {
        TimeoutScheduler scheduler = switch (config.algorithm()) {
            case SCHEDULED_EXECUTOR -> ScheduledExecutorServiceTimeoutScheduler.withDaemonThread();
            case VIRTUAL_THREAD -> new VirtualThreadTimeoutScheduler();
            case HASHED_WHEEL -> HashedWheelTimeoutScheduler.withDefaults();
        };
        return new TimeoutExecutor(scheduler, config.syncWorkerExecutor());
    }
}
