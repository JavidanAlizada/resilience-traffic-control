package dev.trafficcontrol.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.trafficcontrol.retry.FixedDelayBackoff;
import dev.trafficcontrol.retry.Retries;
import dev.trafficcontrol.retry.RetryConfig;
import dev.trafficcontrol.retry.RetryExecutor;
import dev.trafficcontrol.retry.RetryExhaustedException;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Hand-wired Retry + CircuitBreaker, in both orders. The general ordering
 * layer is Milestone 5; this pins down the two behaviors that matter now.
 */
class CircuitBreakerRetryCompositionTest {

    private static final int WINDOW = 4;
    private static final int MAX_ATTEMPTS = 10;

    private final AtomicInteger dependencyCalls = new AtomicInteger();

    private static CircuitBreaker breaker() {
        return CircuitBreakers.of(CircuitBreakerConfig.builder()
                .slidingWindowSize(WINDOW)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());
    }

    private static RetryConfig.Builder retry() {
        return RetryConfig.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .backoffStrategy(new FixedDelayBackoff(Duration.ofMillis(1)));
    }

    private String alwaysDown() throws IOException {
        dependencyCalls.incrementAndGet();
        throw new IOException("down");
    }

    @Test
    void retryStopsAsSoonAsTheBreakerOpens() {
        CircuitBreaker breaker = breaker();
        RetryConfig config = retry()
                .retryPredicate(failure -> !(failure instanceof CallNotPermittedException))
                .build();

        try (RetryExecutor retry = Retries.of(config)) {
            CallNotPermittedException rejected = assertThrows(CallNotPermittedException.class,
                    () -> retry.execute(() -> breaker.execute(this::alwaysDown)));

            assertEquals(WINDOW, dependencyCalls.get(), "only the calls it took to trip reached the dependency");
            assertEquals(WINDOW, rejected.getSuppressed().length, "the real failures are attached");
        }
    }

    @Test
    void withoutThePredicateRetryBurnsItsWholeBudgetOnRejections() {
        CircuitBreaker breaker = breaker();

        try (RetryExecutor retry = Retries.of(retry().build())) {
            RetryExhaustedException exhausted = assertThrows(RetryExhaustedException.class,
                    () -> retry.execute(() -> breaker.execute(this::alwaysDown)));

            assertEquals(WINDOW, dependencyCalls.get());
            assertInstanceOf(CallNotPermittedException.class, exhausted.getCause(),
                    MAX_ATTEMPTS - WINDOW + " attempts were wasted on an open breaker");
        }
    }

    @Test
    void breakerOutsideRetrySeesAWholeRetrySequenceAsOneCall() throws Exception {
        CircuitBreaker breaker = breaker();

        try (RetryExecutor retry = Retries.of(retry().maxAttempts(3).build())) {
            String result = breaker.execute(() -> retry.execute(() -> {
                if (dependencyCalls.incrementAndGet() < 3) {
                    throw new IOException("flaky");
                }
                return "ok";
            }));

            assertEquals("ok", result);
            assertEquals(3, dependencyCalls.get());
            assertEquals(1, breaker.metrics().totalCalls(), "two failures hidden inside one retried call");
            assertEquals(0, breaker.metrics().failedCalls());
        }
    }

    @Test
    void asyncRetryStopsAsSoonAsTheBreakerOpens() throws InterruptedException {
        CircuitBreaker breaker = breaker();
        RetryConfig config = retry()
                .retryPredicate(failure -> !(failure instanceof CallNotPermittedException))
                .build();

        try (RetryExecutor retry = Retries.of(config)) {
            CompletableFuture<String> result = retry.executeAsync(() -> breaker.executeAsync(() -> {
                dependencyCalls.incrementAndGet();
                return CompletableFuture.failedFuture(new IOException("down"));
            }));

            ExecutionException thrown = assertThrows(ExecutionException.class,
                    () -> result.get(5, TimeUnit.SECONDS));
            assertInstanceOf(CallNotPermittedException.class, thrown.getCause());
            assertEquals(WINDOW, dependencyCalls.get());
        }
    }
}
