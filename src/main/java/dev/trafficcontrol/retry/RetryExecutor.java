package dev.trafficcontrol.retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Decorates a call with retry behavior -- the same shape as the reference
 * {@code Retry<T> implements BusinessOperation<T>} pattern, just split
 * into a sync and an async path.
 *
 * <p>{@link #close()} closes the configured {@link RetryConfig#scheduler()}.
 * If that scheduler was supplied by the caller (rather than defaulted) and
 * is still needed elsewhere, don't close this executor -- build a
 * dedicated scheduler for it instead, same posture as
 * {@code TimeoutExecutor}.
 */
public final class RetryExecutor implements AutoCloseable {

    private final RetryConfig config;

    public RetryExecutor(RetryConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Blocks the calling thread between attempts -- a synchronous retry is a call the caller accepts blocking on. */
    public <T> T execute(Callable<T> call) throws Exception {
        List<Throwable> priorFailures = new ArrayList<>();
        int attempt = 0;
        Duration previousDelay = null;
        while (true) {
            attempt++;
            try {
                return call.call();
            } catch (Exception failure) {
                if (!config.retryPredicate().test(failure)) {
                    priorFailures.forEach(failure::addSuppressed);
                    throw failure;
                }
                if (attempt >= config.maxAttempts()) {
                    throw exhausted(attempt, failure, priorFailures);
                }
                priorFailures.add(failure);
                Duration delay = config.backoffStrategy().nextDelay(attempt, previousDelay);
                previousDelay = delay;
                TimeUnit.NANOSECONDS.sleep(delay.toNanos());
            }
        }
    }

    /** Non-blocking: the next attempt is scheduled via {@link RetryConfig#scheduler()}, not slept on a thread. */
    public <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> call) {
        CompletableFuture<T> result = new CompletableFuture<>();
        attemptAsync(call, result, 1, null, new ArrayList<>());
        return result;
    }

    private <T> void attemptAsync(Supplier<CompletableFuture<T>> call, CompletableFuture<T> result, int attempt,
            Duration previousDelay, List<Throwable> priorFailures) {
        call.get().whenComplete((value, error) -> {
            if (error == null) {
                result.complete(value);
                return;
            }
            Throwable failure = unwrap(error);
            if (!config.retryPredicate().test(failure)) {
                priorFailures.forEach(failure::addSuppressed);
                result.completeExceptionally(failure);
                return;
            }
            if (attempt >= config.maxAttempts()) {
                result.completeExceptionally(exhausted(attempt, failure, priorFailures));
                return;
            }
            priorFailures.add(failure);
            Duration delay = config.backoffStrategy().nextDelay(attempt, previousDelay);
            config.scheduler().scheduleTimeout(delay.toNanos(),
                    () -> attemptAsync(call, result, attempt + 1, delay, priorFailures));
        });
    }

    private static RetryExhaustedException exhausted(int attempt, Throwable last, List<Throwable> priorFailures) {
        RetryExhaustedException exhausted = new RetryExhaustedException(attempt, last);
        priorFailures.forEach(exhausted::addSuppressed);
        return exhausted;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    @Override
    public void close() {
        config.scheduler().close();
    }
}
