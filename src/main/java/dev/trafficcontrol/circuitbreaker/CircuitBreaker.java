package dev.trafficcontrol.circuitbreaker;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Stops calling a dependency once it looks unhealthy, then lets a few trial
 * calls through to find out whether it has recovered.
 *
 * Most callers want execute/executeAsync. The permit API is for calls that
 * can't be wrapped in a Callable: acquire a permit, make the call, then
 * report exactly one outcome on it.
 */
public interface CircuitBreaker {

    enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    /** Throws CallNotPermittedException if the breaker is OPEN, or HALF_OPEN with no trial slots left. */
    Permit acquirePermission();

    <T> T execute(Callable<T> call) throws Exception;

    /** Never throws: a rejected call comes back as a future failed with CallNotPermittedException. */
    <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> call);

    State state();

    CircuitBreakerMetrics metrics();

    /**
     * One admitted call. Only the first of onSuccess / onFailure / release
     * counts; later calls are ignored. The outcome is recorded against the
     * state that admitted the call, so a call that outlives a transition
     * can't leak into the next state's statistics.
     */
    interface Permit {

        void onSuccess(long durationNanos);

        void onFailure(long durationNanos, Throwable failure);

        /** The call never happened or was cancelled: record nothing and give back a HALF_OPEN trial slot. */
        void release();
    }
}
