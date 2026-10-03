package dev.trafficcontrol.circuitbreaker;

import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.CLOSED;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Contended breakers against a frozen clock, so every count asserted here
 * is exact: with time standing still nothing can expire or time out
 * between threads.
 */
class CircuitBreakerConcurrentTest {

    private static final int THREADS = 32;
    private static final long WAIT_NANOS = Duration.ofSeconds(10).toNanos();

    private final FakeClock clock = new FakeClock(0);

    private final List<StateTransitionEvent> events = new CopyOnWriteArrayList<>();

    private CircuitBreaker breaker(int windowSize, int permittedInHalfOpen) {
        return CircuitBreakers.of(CircuitBreakerConfig.builder()
                .slidingWindowSize(windowSize)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofNanos(WAIT_NANOS))
                .permittedCallsInHalfOpenState(permittedInHalfOpen)
                .maxWaitDurationInHalfOpenState(Duration.ZERO)
                .clock(clock)
                .listener(events::add)
                .build());
    }

    private static void trip(CircuitBreaker breaker, int windowSize) {
        for (int i = 0; i < windowSize; i++) {
            breaker.acquirePermission().onFailure(0, new IOException());
        }
        assertEquals(OPEN, breaker.state());
    }

    @RepeatedTest(5)
    void halfOpenAdmitsExactlyPermittedCallsUnderContention() throws InterruptedException {
        int permitted = 50;
        int attemptsPerThread = 200;
        CircuitBreaker breaker = breaker(10, permitted);
        trip(breaker, 10);
        clock.advance(WAIT_NANOS); // every thread now races the OPEN -> HALF_OPEN transition too

        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        runConcurrently(() -> {
            for (int i = 0; i < attemptsPerThread; i++) {
                try {
                    breaker.acquirePermission();
                    admitted.incrementAndGet();
                } catch (CallNotPermittedException e) {
                    rejected.incrementAndGet();
                }
            }
        });

        assertEquals(permitted, admitted.get());
        assertEquals(THREADS * attemptsPerThread, admitted.get() + rejected.get());
        assertEquals(HALF_OPEN, breaker.state());
    }

    @RepeatedTest(5)
    void concurrentTrialResultsCloseTheBreakerOnce() throws InterruptedException {
        CircuitBreaker breaker = breaker(10, THREADS * 4);
        trip(breaker, 10);
        clock.advance(WAIT_NANOS);
        List<CircuitBreaker.Permit> permits = acquireAll(breaker, THREADS * 4);

        AtomicInteger next = new AtomicInteger();
        runConcurrently(() -> {
            for (int i = 0; i < 4; i++) {
                permits.get(next.getAndIncrement()).onSuccess(0);
            }
        });

        assertEquals(CLOSED, breaker.state());
        assertEquals(0, breaker.metrics().totalCalls(), "a fresh window, untouched by the trial calls");
    }

    @RepeatedTest(5)
    void concurrentFailingTrialResultsReopenTheBreaker() throws InterruptedException {
        CircuitBreaker breaker = breaker(10, THREADS * 4);
        trip(breaker, 10);
        clock.advance(WAIT_NANOS);
        List<CircuitBreaker.Permit> permits = acquireAll(breaker, THREADS * 4);

        AtomicInteger next = new AtomicInteger();
        runConcurrently(() -> {
            for (int i = 0; i < 4; i++) {
                int index = next.getAndIncrement();
                if (index % 2 == 0) {
                    permits.get(index).onFailure(0, new IOException());
                } else {
                    permits.get(index).onSuccess(0);
                }
            }
        });

        assertEquals(OPEN, breaker.state());
        assertEquals(new CircuitBreakerMetrics(OPEN, THREADS * 4, THREADS * 2, 0, 50.0, 0.0), breaker.metrics(),
                "every trial result was seen by the thread that evaluated them");
    }

    @RepeatedTest(5)
    void failingTrafficTripsAndNoCallIsLostOrDoubleCounted() throws InterruptedException {
        int windowSize = 100;
        int attemptsPerThread = 500;
        CircuitBreaker breaker = breaker(windowSize, 10);
        AtomicInteger ran = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger rethrown = new AtomicInteger();

        runConcurrently(() -> {
            for (int i = 0; i < attemptsPerThread; i++) {
                try {
                    breaker.execute(() -> {
                        ran.incrementAndGet();
                        throw new IOException("down");
                    });
                } catch (CallNotPermittedException e) {
                    rejected.incrementAndGet();
                } catch (Exception dependencyFailure) {
                    rethrown.incrementAndGet();
                }
            }
        });

        assertEquals(OPEN, breaker.state());
        assertEquals(THREADS * attemptsPerThread, ran.get() + rejected.get());
        assertEquals(ran.get(), rethrown.get(), "every call that ran surfaced its own failure");
        assertTrue(ran.get() >= windowSize, "needed a full window of failures to trip, ran " + ran.get());
    }

    @RepeatedTest(5)
    void manyThreadsCrossingTheThresholdTripExactlyOnce() throws InterruptedException {
        CircuitBreaker breaker = breaker(100, 10);

        runConcurrently(() -> {
            for (int i = 0; i < 100; i++) {
                try {
                    breaker.acquirePermission().onFailure(0, new IOException());
                } catch (CallNotPermittedException e) {
                    // expected once the breaker has opened
                }
            }
        });

        assertEquals(List.of(new StateTransitionEvent(CLOSED, OPEN, 0)), events);
    }

    @RepeatedTest(5)
    void manyThreadsLeavingOpenAtOnceProduceOneHalfOpenTransition() throws InterruptedException {
        CircuitBreaker breaker = breaker(10, 1_000);
        trip(breaker, 10);
        clock.advance(WAIT_NANOS);
        events.clear();

        runConcurrently(() -> {
            for (int i = 0; i < 10; i++) {
                breaker.acquirePermission();
            }
        });

        assertEquals(List.of(new StateTransitionEvent(OPEN, HALF_OPEN, WAIT_NANOS)), events);
    }

    private static List<CircuitBreaker.Permit> acquireAll(CircuitBreaker breaker, int count) {
        List<CircuitBreaker.Permit> permits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            permits.add(breaker.acquirePermission());
        }
        return permits;
    }

    private static void runConcurrently(Runnable work) throws InterruptedException {
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
                    work.run();
                });
            }
            startGate.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "threads did not finish in time");
        }
    }
}
