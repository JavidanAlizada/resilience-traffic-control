package dev.trafficcontrol.timeout;

class ScheduledExecutorServiceTimeoutSchedulerTest extends TimeoutSchedulerContractTest {

    @Override
    TimeoutScheduler createScheduler() {
        return ScheduledExecutorServiceTimeoutScheduler.withDaemonThread();
    }
}
