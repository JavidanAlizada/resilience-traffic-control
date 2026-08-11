package dev.trafficcontrol.timeout;

class VirtualThreadTimeoutSchedulerTest extends TimeoutSchedulerContractTest {

    @Override
    TimeoutScheduler createScheduler() {
        return new VirtualThreadTimeoutScheduler();
    }
}
