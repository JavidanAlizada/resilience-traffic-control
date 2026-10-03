package dev.trafficcontrol.circuitbreaker;

import dev.trafficcontrol.retry.FixedDelayBackoff;
import dev.trafficcontrol.retry.Retries;
import dev.trafficcontrol.retry.RetryConfig;
import dev.trafficcontrol.retry.RetryExecutor;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable usage examples against the real clock, same idea as
 * RetryEngineDemo. Lives in test sources so the library JAR doesn't ship
 * a main(). Run it from an IDE, or:
 * ./gradlew testClasses && java -cp build/classes/java/main:build/classes/java/test
 * dev.trafficcontrol.circuitbreaker.CircuitBreakerDemo
 */
public final class CircuitBreakerDemo {

    private static final long START = System.nanoTime();

    private CircuitBreakerDemo() {
    }

    public static void main(String[] args) throws Exception {
        tripRejectAndRecover();
        slowCallsTripWithoutAnyFailure();
        retryStopsOnceTheBreakerOpens();
        asyncRejectionIsAFailedFuture();
    }

    private static CircuitBreakerConfig.Builder demoConfig() {
        return CircuitBreakerConfig.builder()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(200))
                .permittedCallsInHalfOpenState(3)
                .listener(event -> System.out.printf("    [%4d ms] %s -> %s%n",
                        TimeUnit.NANOSECONDS.toMillis(event.atNanos() - START), event.from(), event.to()));
    }

    private static void tripRejectAndRecover() throws Exception {
        System.out.println("-- outage: trips at 50% failures, rejects while OPEN, recovers via HALF_OPEN --");
        CircuitBreaker breaker = CircuitBreakers.of(demoConfig().build());
        AtomicBoolean down = new AtomicBoolean();
        AtomicInteger reachedDependency = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            call(breaker, down, reachedDependency);
        }
        down.set(true);
        for (int i = 0; i < 10; i++) {
            call(breaker, down, reachedDependency);
        }
        System.out.println("  state: " + breaker.state() + ", metrics at trip: " + breaker.metrics());

        int before = reachedDependency.get();
        int rejected = 0;
        for (int i = 0; i < 100; i++) {
            if (!call(breaker, down, reachedDependency)) {
                rejected++;
            }
        }
        System.out.println("  100 calls while OPEN: " + rejected + " rejected, "
                + (reachedDependency.get() - before) + " reached the dependency");

        down.set(false);
        Thread.sleep(250);
        System.out.println("  dependency back up, wait elapsed; next calls are trial calls:");
        for (int i = 0; i < 5; i++) {
            call(breaker, down, reachedDependency);
        }
        System.out.println("  state: " + breaker.state() + "\n");
    }

    /** Returns false if the breaker rejected the call. */
    private static boolean call(CircuitBreaker breaker, AtomicBoolean down, AtomicInteger reachedDependency) {
        try {
            breaker.execute(() -> {
                reachedDependency.incrementAndGet();
                if (down.get()) {
                    throw new IOException("connection refused");
                }
                return "ok";
            });
            return true;
        } catch (CallNotPermittedException e) {
            return false;
        } catch (Exception dependencyFailure) {
            return true;
        }
    }

    private static void slowCallsTripWithoutAnyFailure() throws Exception {
        System.out.println("-- slow dependency: every call succeeds, but takes 30 ms against a 20 ms threshold --");
        CircuitBreaker breaker = CircuitBreakers.of(demoConfig()
                .slowCallDurationThreshold(Duration.ofMillis(20))
                .slowCallRateThreshold(80)
                .build());

        for (int i = 0; i < 10; i++) {
            breaker.execute(() -> {
                Thread.sleep(30);
                return "ok, eventually";
            });
        }
        System.out.println("  state: " + breaker.state() + ", metrics: " + breaker.metrics() + "\n");
    }

    private static void retryStopsOnceTheBreakerOpens() {
        System.out.println("-- Retry(CircuitBreaker(call)): retry gives up as soon as the breaker opens --");
        CircuitBreaker breaker = CircuitBreakers.of(demoConfig().slidingWindowSize(4).build());
        RetryConfig retryConfig = RetryConfig.builder()
                .maxAttempts(10)
                .backoffStrategy(new FixedDelayBackoff(Duration.ofMillis(10)))
                .retryPredicate(failure -> !(failure instanceof CallNotPermittedException))
                .build();
        AtomicInteger attempts = new AtomicInteger();

        try (RetryExecutor retry = Retries.of(retryConfig)) {
            retry.execute(() -> breaker.execute(() -> {
                System.out.println("  attempt " + attempts.incrementAndGet() + " reaches the dependency");
                throw new IOException("down");
            }));
        } catch (CallNotPermittedException e) {
            System.out.println("  stopped after " + attempts.get() + " of 10 attempts: " + e.getMessage()
                    + " (" + e.getSuppressed().length + " real failures attached)\n");
        } catch (Exception e) {
            throw new AssertionError("unexpected", e);
        }
    }

    private static void asyncRejectionIsAFailedFuture() {
        System.out.println("-- async: an OPEN breaker returns a failed future instead of throwing --");
        CircuitBreaker breaker = CircuitBreakers.of(demoConfig().slidingWindowSize(2).build());
        for (int i = 0; i < 2; i++) {
            breaker.executeAsync(() -> CompletableFuture.failedFuture(new IOException("down")));
        }

        CompletableFuture<String> rejected = breaker.executeAsync(() -> CompletableFuture.completedFuture("x"));
        rejected.exceptionally(failure -> {
            System.out.println("  future failed with: " + failure);
            return null;
        }).join();
    }
}
