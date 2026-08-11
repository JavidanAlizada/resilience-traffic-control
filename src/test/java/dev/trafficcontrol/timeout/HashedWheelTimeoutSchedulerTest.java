package dev.trafficcontrol.timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HashedWheelTimeoutSchedulerTest extends TimeoutSchedulerContractTest {

    @Override
    TimeoutScheduler createScheduler() {
        return HashedWheelTimeoutScheduler.withDefaults();
    }

    @Test
    void handlesDelaysSpanningMultipleWheelRotations() throws InterruptedException {
        // wheelSize=4, tickDuration=5ms -> one rotation is 20ms; scheduling
        // 60ms out means the task has to survive 3 full passes of the wheel.
        HashedWheelTimeoutScheduler scheduler = new HashedWheelTimeoutScheduler(Duration.ofMillis(5), 4);
        CountDownLatch fired = new CountDownLatch(1);
        scheduler.scheduleTimeout(Duration.ofMillis(60).toNanos(), fired::countDown);
        assertTrue(fired.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    void concurrentScheduleAndCancelNeverFiresATaskTwice() throws InterruptedException {
        HashedWheelTimeoutScheduler scheduler = new HashedWheelTimeoutScheduler(Duration.ofMillis(2), 16);
        int taskCount = 2000;
        AtomicInteger doubleFires = new AtomicInteger();
        CountDownLatch allScheduled = new CountDownLatch(taskCount);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < taskCount; i++) {
                pool.execute(() -> {
                    AtomicBoolean firedOnce = new AtomicBoolean();
                    long delayNanos = ThreadLocalRandom.current().nextLong(1, 50) * 1_000_000L;
                    Cancellable scheduled = scheduler.scheduleTimeout(delayNanos, () -> {
                        if (!firedOnce.compareAndSet(false, true)) {
                            doubleFires.incrementAndGet();
                        }
                    });
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        scheduled.cancel();
                    }
                    allScheduled.countDown();
                });
            }
            assertTrue(allScheduled.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
        }

        Thread.sleep(200); // let any still-pending timeouts (max ~50ms delay) fire
        assertEquals(0, doubleFires.get(), "no timeout should ever fire more than once");
    }
}
