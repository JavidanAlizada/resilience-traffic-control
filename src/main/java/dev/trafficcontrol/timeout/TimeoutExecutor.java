package dev.trafficcontrol.timeout;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Bounds how long a call is allowed to take. Sync and async go through
 * genuinely different mechanisms, not an accident: a sync call already has
 * a thread blocked waiting for it (Future.get(timeout) is enough), while
 * async needs an active TimeoutScheduler since nothing is parked waiting
 * to notice the deadline passed.
 */
public final class TimeoutExecutor implements AutoCloseable {

    private final TimeoutScheduler scheduler;
    private final ExecutorService syncWorkerExecutor;

    public TimeoutExecutor(TimeoutScheduler scheduler, ExecutorService syncWorkerExecutor) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.syncWorkerExecutor = Objects.requireNonNull(syncWorkerExecutor, "syncWorkerExecutor");
    }

    /** Runs call on the worker executor; cancels (best-effort) and throws if it outruns timeout. */
    public <T> T execute(Duration timeout, Callable<T> call)
            throws ExecutionException, InterruptedException, TimeoutException {
        Future<T> future = syncWorkerExecutor.submit(call);
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    public <T> T execute(Deadline deadline, Callable<T> call)
            throws ExecutionException, InterruptedException, TimeoutException {
        return execute(deadline.remaining(), call);
    }

    /** Starts call immediately; a timeout that fires first completes the future exceptionally. */
    public <T> CompletableFuture<T> executeAsync(Duration timeout, Supplier<CompletableFuture<T>> call) {
        CompletableFuture<T> future = call.get();
        Cancellable scheduled = scheduler.scheduleTimeout(timeout.toNanos(),
                () -> future.completeExceptionally(new TimeoutException("timed out after " + timeout)));
        future.whenComplete((result, error) -> scheduled.cancel());
        return future;
    }

    public <T> CompletableFuture<T> executeAsync(Deadline deadline, Supplier<CompletableFuture<T>> call) {
        return executeAsync(deadline.remaining(), call);
    }

    /**
     * Releases the scheduler's background resources. Doesn't touch
     * syncWorkerExecutor -- that one's lifecycle belongs to whoever
     * supplied it (possibly a shared, caller-owned pool), not to this
     * class.
     */
    @Override
    public void close() {
        scheduler.close();
    }
}
