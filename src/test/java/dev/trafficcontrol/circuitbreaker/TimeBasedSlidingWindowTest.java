package dev.trafficcontrol.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TimeBasedSlidingWindowTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void outcomesWithinTheSameSecondAccumulate() {
        FakeClock clock = new FakeClock(0);
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(10, clock);
        window.record(true, false);
        clock.advance(SECOND / 2);
        window.record(false, true);

        assertEquals(new WindowSnapshot(2, 1, 1), window.snapshot());
    }

    @Test
    void outcomeStaysUntilExactlyWindowSecondsLater() {
        FakeClock clock = new FakeClock(0);
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(5, clock);
        window.record(true, false);

        clock.advance(5 * SECOND - 1);
        assertEquals(new WindowSnapshot(1, 1, 0), window.snapshot(), "one nanosecond before expiry");

        clock.advance(1);
        assertEquals(WindowSnapshot.EMPTY, window.snapshot(), "exactly at expiry");
    }

    @Test
    void onlyExpiredSecondsAreDropped() {
        FakeClock clock = new FakeClock(0);
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(3, clock);
        window.record(true, false);   // second 0
        clock.advance(SECOND);
        window.record(false, false);  // second 1
        clock.advance(SECOND);
        window.record(false, true);   // second 2

        clock.advance(SECOND);        // second 3: second 0 falls out
        assertEquals(new WindowSnapshot(2, 0, 1), window.snapshot());
    }

    @Test
    void longIdleGapClearsEverything() {
        FakeClock clock = new FakeClock(0);
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(3, clock);
        window.record(true, true);
        window.record(true, true);

        clock.advance(1_000 * SECOND);
        window.record(false, false);

        assertEquals(new WindowSnapshot(1, 0, 0), window.snapshot());
    }

    @Test
    void handlesNegativeNanoTimeOrigin() {
        // System.nanoTime() may legitimately be negative.
        FakeClock clock = new FakeClock(-SECOND / 2);
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(2, clock);
        window.record(true, false);

        clock.advance(SECOND);
        assertEquals(1, window.snapshot().totalCalls());
        clock.advance(SECOND);
        assertEquals(0, window.snapshot().totalCalls());
    }

    @Test
    void concurrentRecordsAreAllCountedWithClockFrozen() throws InterruptedException {
        TimeBasedSlidingWindow window = new TimeBasedSlidingWindow(10, new FakeClock(0));
        int threads = 32;
        int perThread = 1_000;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                boolean failing = t % 2 == 0;
                pool.execute(() -> {
                    try {
                        startGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < perThread; i++) {
                        window.record(failing, false);
                    }
                });
            }
            startGate.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "threads did not finish in time");
        }

        assertEquals(new WindowSnapshot(threads * perThread, threads * perThread / 2, 0), window.snapshot());
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> new TimeBasedSlidingWindow(0, new FakeClock(0)));
    }
}
