package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Contended admission against a frozen clock: with time held still, the
 * total number of admissions across every thread is a fixed number
 * (the burst capacity) regardless of thread count or interleaving. That
 * makes the assertion below exact, not a race-prone approximation — the
 * accounting invariant this milestone's proposal calls for (doc 02,
 * Section 8).
 */
class GcraRateLimiterConcurrentTest {

    @Test
    void totalAdmissionsNeverExceedsBurstCapacityUnderContention() throws InterruptedException {
        long burstCapacity = 200;
        FakeClock frozenClock = new FakeClock(0); // never advanced during the test
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.GCRA)
                .permitsPerSecond(1_000)
                .burstCapacity(burstCapacity)
                .clock(frozenClock)
                .build();
        RateLimiter limiter = RateLimiterFactory.create(config);

        int threadCount = 32;
        int attemptsPerThread = 500;
        AtomicInteger totalAdmitted = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int t = 0; t < threadCount; t++) {
                pool.execute(() -> {
                    await(startGate);
                    int admitted = 0;
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire()) {
                            admitted++;
                        }
                    }
                    totalAdmitted.addAndGet(admitted);
                });
            }
            startGate.countDown();
        } finally {
            pool.shutdown();
            assertEquals(true, pool.awaitTermination(30, TimeUnit.SECONDS), "threads did not finish in time");
        }

        assertEquals(burstCapacity, totalAdmitted.get(),
                "with the clock frozen, exactly burstCapacity requests can ever be admitted");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
