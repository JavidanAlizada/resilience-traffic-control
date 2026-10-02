package dev.trafficcontrol.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * The ring buffer's running counters can drift from each other while
 * writers are in flight, but must equal a brute-force recount of the slots
 * once every writer has finished.
 */
class CountBasedSlidingWindowConcurrentTest {

    private static final int THREADS = 32;

    @RepeatedTest(5)
    void countersMatchRecountOnceWritersFinish() throws InterruptedException {
        // Small window, many writers: lots of threads wrapping onto the same slots.
        CountBasedSlidingWindow window = new CountBasedSlidingWindow(16);

        runConcurrently(2_000, () -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            window.record(random.nextBoolean(), random.nextBoolean());
        });

        assertEquals(window.recount(), window.snapshot());
        assertEquals(16, window.snapshot().totalCalls());
    }

    @Test
    void allFailuresFillTheWindowWithFailures() throws InterruptedException {
        CountBasedSlidingWindow window = new CountBasedSlidingWindow(100);

        runConcurrently(1_000, () -> window.record(true, false));

        assertEquals(new WindowSnapshot(100, 100, 0), window.snapshot());
    }

    private static void runConcurrently(int recordsPerThread, Runnable record) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int t = 0; t < THREADS; t++) {
                pool.execute(() -> {
                    try {
                        startGate.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < recordsPerThread; i++) {
                        record.run();
                    }
                });
            }
            startGate.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "threads did not finish in time");
        }
    }
}
