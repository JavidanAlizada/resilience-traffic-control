package dev.trafficcontrol.retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable usage examples, same idea as the design-patterns repo's
 * per-pattern {@code App.java}. Lives in test sources, not main -- a
 * library shouldn't ship a {@code main()} demo in its production JAR.
 * Run it directly from an IDE, or: {@code ./gradlew testClasses && java
 * -cp build/classes/java/main:build/classes/java/test
 * dev.trafficcontrol.retry.RetryEngineDemo}
 */
public final class RetryEngineDemo {

    private RetryEngineDemo() {
    }

    public static void main(String[] args) throws Exception {
        syncRetrySucceedsAfterTransientFailures();
        syncRetryExhaustsAndReportsEveryFailure();
        nonRetryablePredicateFailsFast();
        asyncRetryWithFullJitter();
    }

    private static void syncRetrySucceedsAfterTransientFailures() throws Exception {
        System.out.println("-- sync retry, fixed delay, succeeds on the 3rd attempt --");
        AtomicInteger calls = new AtomicInteger();
        try (RetryExecutor executor = Retries.fixedDelay(5, Duration.ofMillis(50))) {
            String result = executor.execute(() -> {
                int attempt = calls.incrementAndGet();
                System.out.println("  attempt " + attempt);
                if (attempt < 3) {
                    throw new IllegalStateException("simulated flaky dependency");
                }
                return "success";
            });
            System.out.println("  result: " + result + "\n");
        }
    }

    private static void syncRetryExhaustsAndReportsEveryFailure() {
        System.out.println("-- sync retry, exponential backoff, exhausts after 3 attempts --");
        try (RetryExecutor executor = Retries.exponentialBackoff(3, Duration.ofMillis(20), Duration.ofMillis(200))) {
            executor.execute(() -> {
                throw new IllegalStateException("dependency is down");
            });
        } catch (RetryExhaustedException e) {
            System.out.println("  exhausted: " + e.getMessage());
            for (Throwable priorFailure : e.getSuppressed()) {
                System.out.println("    prior failure: " + priorFailure.getMessage());
            }
            System.out.println("    final failure (the cause): " + e.getCause().getMessage() + "\n");
        } catch (Exception e) {
            throw new AssertionError("unexpected", e);
        }
    }

    private static void nonRetryablePredicateFailsFast() {
        System.out.println("-- retry predicate rejects IllegalArgumentException, fails on the 1st attempt --");
        AtomicInteger calls = new AtomicInteger();
        RetryConfig config = RetryConfig.builder()
                .maxAttempts(5)
                .retryPredicate(failure -> !(failure instanceof IllegalArgumentException))
                .build();
        try (RetryExecutor executor = Retries.of(config)) {
            executor.execute(() -> {
                calls.incrementAndGet();
                throw new IllegalArgumentException("bad request, retrying would never help");
            });
        } catch (IllegalArgumentException e) {
            System.out.println("  failed fast after " + calls.get() + " attempt(s): " + e.getMessage() + "\n");
        } catch (Exception e) {
            throw new AssertionError("unexpected", e);
        }
    }

    private static void asyncRetryWithFullJitter() throws ExecutionException, InterruptedException, TimeoutException {
        System.out.println("-- async retry, full jitter, non-blocking --");
        AtomicInteger calls = new AtomicInteger();
        try (RetryExecutor executor = Retries.fullJitter(5, Duration.ofMillis(20), Duration.ofMillis(200))) {
            CompletableFuture<String> result = executor.executeAsync(() -> {
                int attempt = calls.incrementAndGet();
                System.out.println("  attempt " + attempt + " (thread: " + Thread.currentThread() + ")");
                CompletableFuture<String> future = new CompletableFuture<>();
                if (attempt < 3) {
                    future.completeExceptionally(new IllegalStateException("simulated flaky dependency"));
                } else {
                    future.complete("success");
                }
                return future;
            });
            System.out.println("  result: " + result.get(5, TimeUnit.SECONDS) + "\n");
        }
    }
}
