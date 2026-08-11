package dev.trafficcontrol.timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * All three {@link TimeoutScheduler} implementations owe callers the same
 * two guarantees; this contract is run against each of them rather than
 * copy-pasted three times.
 */
abstract class TimeoutSchedulerContractTest {

    abstract TimeoutScheduler createScheduler();

    @Test
    void firesAfterTheDelay() throws InterruptedException {
        CountDownLatch fired = new CountDownLatch(1);
        createScheduler().scheduleTimeout(Duration.ofMillis(20).toNanos(), fired::countDown);
        assertTrue(fired.await(500, TimeUnit.MILLISECONDS), "should have fired well within this margin");
    }

    @Test
    void cancelPreventsFiring() throws InterruptedException {
        AtomicBoolean fired = new AtomicBoolean();
        Cancellable scheduled = createScheduler()
                .scheduleTimeout(Duration.ofMillis(50).toNanos(), () -> fired.set(true));
        scheduled.cancel();

        Thread.sleep(150); // past the delay, to make sure it really never fires

        assertFalse(fired.get());
    }
}
