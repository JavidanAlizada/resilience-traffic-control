package dev.trafficcontrol.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryExecutorTest {

    @Test
    void succeedsOnFirstAttemptWithoutRetrying() throws Exception {
        try (RetryExecutor executor = Retries.fixedDelay(3, Duration.ofMillis(5))) {
            AtomicInteger calls = new AtomicInteger();
            String result = executor.execute(() -> {
                calls.incrementAndGet();
                return "ok";
            });

            assertEquals("ok", result);
            assertEquals(1, calls.get());
        }
    }

    @Test
    void retriesUntilSuccessWithinBudget() throws Exception {
        try (RetryExecutor executor = Retries.fixedDelay(3, Duration.ofMillis(5))) {
            AtomicInteger calls = new AtomicInteger();
            String result = executor.execute(() -> {
                if (calls.incrementAndGet() < 3) {
                    throw new IllegalStateException("not yet");
                }
                return "ok";
            });

            assertEquals("ok", result);
            assertEquals(3, calls.get());
        }
    }

    @Test
    void exhaustionCarriesEveryPriorFailureAsSuppressed() {
        try (RetryExecutor executor = Retries.fixedDelay(3, Duration.ofMillis(5))) {
            RetryExhaustedException thrown = assertThrows(RetryExhaustedException.class, () ->
                    executor.execute(() -> {
                        throw new IllegalStateException("always fails");
                    }));

            assertInstanceOf(IllegalStateException.class, thrown.getCause());
            assertEquals(2, thrown.getSuppressed().length, "2 prior failures before the 3rd, final one");
        }
    }

    @Test
    void nonRetryablePredicateStopsImmediatelyWithTheOriginalException() {
        RetryConfig config = RetryConfig.builder()
                .maxAttempts(5)
                .backoffStrategy(new FixedDelayBackoff(Duration.ofMillis(5)))
                .retryPredicate(failure -> false)
                .build();
        try (RetryExecutor executor = Retries.of(config)) {
            AtomicInteger calls = new AtomicInteger();
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                    executor.execute(() -> {
                        calls.incrementAndGet();
                        throw new IllegalStateException("not retryable");
                    }));

            assertEquals(1, calls.get(), "predicate said no on the very first failure");
            assertEquals(0, thrown.getSuppressed().length);
        }
    }

    @Test
    void asyncRetriesUntilSuccessWithoutBlockingAThread() throws Exception {
        try (RetryExecutor executor = Retries.fixedDelay(3, Duration.ofMillis(5))) {
            AtomicInteger calls = new AtomicInteger();
            CompletableFuture<String> result = executor.executeAsync(() -> {
                CompletableFuture<String> future = new CompletableFuture<>();
                if (calls.incrementAndGet() < 3) {
                    future.completeExceptionally(new IllegalStateException("not yet"));
                } else {
                    future.complete("ok");
                }
                return future;
            });

            assertEquals("ok", result.get(2, TimeUnit.SECONDS));
            assertEquals(3, calls.get());
        }
    }

    @Test
    void asyncExhaustionCompletesExceptionallyWithRetryExhaustedException() {
        try (RetryExecutor executor = Retries.fixedDelay(3, Duration.ofMillis(5))) {
            CompletableFuture<String> result = executor.executeAsync(() -> {
                CompletableFuture<String> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("always fails"));
                return future;
            });

            ExecutionException thrown =
                    assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertInstanceOf(RetryExhaustedException.class, thrown.getCause());
            assertEquals(2, thrown.getCause().getSuppressed().length);
        }
    }
}
