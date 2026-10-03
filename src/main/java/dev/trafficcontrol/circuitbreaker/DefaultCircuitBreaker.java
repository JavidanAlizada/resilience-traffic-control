package dev.trafficcontrol.circuitbreaker;

import dev.trafficcontrol.ratelimiter.NanoClock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The state machine's context. All behavior that differs per state lives
 * in the BreakerState objects; this class holds the current one, swaps it
 * by CAS, and wraps calls (execute / executeAsync) around the permit API.
 */
final class DefaultCircuitBreaker implements CircuitBreaker {

    private final CircuitBreakerConfig config;
    private final Supplier<SlidingWindow> windowFactory;
    private final NanoClock clock;
    private final long slowCallNanos;
    private final long waitInOpenNanos;
    private final long maxWaitInHalfOpenNanos;
    private final List<CircuitBreakerListener> listeners;
    private final AtomicReference<BreakerState> state;

    DefaultCircuitBreaker(CircuitBreakerConfig config, Supplier<SlidingWindow> windowFactory) {
        this.config = Objects.requireNonNull(config, "config");
        this.windowFactory = Objects.requireNonNull(windowFactory, "windowFactory");
        this.clock = config.clock();
        this.slowCallNanos = config.slowCallDurationThreshold().toNanos();
        this.waitInOpenNanos = config.waitDurationInOpenState().toNanos();
        this.maxWaitInHalfOpenNanos = config.maxWaitDurationInHalfOpenState().toNanos();
        this.listeners = config.listeners();
        this.state = new AtomicReference<>(new ClosedState(windowFactory.get()));
    }

    @Override
    public Permit acquirePermission() {
        BreakerState current = state.get();
        BreakerPermit permit = current.tryAcquire(this);
        if (permit == null) {
            throw new CallNotPermittedException(state.get().name());
        }
        return permit;
    }

    @Override
    public <T> T execute(Callable<T> call) throws Exception {
        Permit permit = acquirePermission();
        long start = clock.nanoTime();
        T result;
        try {
            result = call.call();
        } catch (InterruptedException e) {
            // The caller gave up; that says nothing about the dependency.
            permit.release();
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            permit.onFailure(clock.nanoTime() - start, e);
            throw e;
        } catch (Error e) {
            // OOM and friends are this JVM's problem, not the dependency's.
            permit.release();
            throw e;
        }
        permit.onSuccess(clock.nanoTime() - start);
        return result;
    }

    @Override
    public <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> call) {
        Permit permit;
        try {
            permit = acquirePermission();
        } catch (CallNotPermittedException e) {
            return CompletableFuture.failedFuture(e);
        }
        long start = clock.nanoTime();
        CompletableFuture<T> future;
        try {
            future = call.get();
        } catch (RuntimeException e) {
            permit.onFailure(clock.nanoTime() - start, e);
            return CompletableFuture.failedFuture(e);
        }
        return future.whenComplete((value, error) -> {
            long elapsed = clock.nanoTime() - start;
            if (error == null) {
                permit.onSuccess(elapsed);
                return;
            }
            Throwable failure = unwrap(error);
            if (failure instanceof CancellationException) {
                permit.release();
            } else {
                permit.onFailure(elapsed, failure);
            }
        });
    }

    @Override
    public State state() {
        return state.get().name();
    }

    @Override
    public CircuitBreakerMetrics metrics() {
        BreakerState current = state.get();
        return CircuitBreakerMetrics.of(current.name(), current.snapshot());
    }

    // ---- used by the BreakerState objects ----

    BreakerState currentState() {
        return state.get();
    }

    /** True only for the one thread whose CAS moved the breaker from "from" to "to"; that thread notifies listeners. */
    boolean transition(BreakerState from, BreakerState to) {
        if (!state.compareAndSet(from, to)) {
            return false;
        }
        if (!listeners.isEmpty()) {
            notifyListeners(new StateTransitionEvent(from.name(), to.name(), now()));
        }
        return true;
    }

    private void notifyListeners(StateTransitionEvent event) {
        for (CircuitBreakerListener listener : listeners) {
            try {
                listener.onStateTransition(event);
            } catch (RuntimeException ignored) {
                // expected: a broken listener must not fail the call that happened to trigger the
                // transition, or stop the listeners after it from hearing about it.
            }
        }
    }

    boolean exceedsThresholds(WindowSnapshot snapshot) {
        return snapshot.failureRate() >= config.failureRateThreshold()
                || snapshot.slowCallRate() >= config.slowCallRateThreshold();
    }

    boolean isSlow(long durationNanos) {
        return durationNanos >= slowCallNanos;
    }

    boolean countsAsFailure(Throwable failure) {
        return config.failurePredicate().test(failure);
    }

    SlidingWindow newWindow() {
        return windowFactory.get();
    }

    long now() {
        return clock.nanoTime();
    }

    int minimumNumberOfCalls() {
        return config.minimumNumberOfCalls();
    }

    int permittedCallsInHalfOpen() {
        return config.permittedCallsInHalfOpenState();
    }

    long waitInOpenNanos() {
        return waitInOpenNanos;
    }

    long maxWaitInHalfOpenNanos() {
        return maxWaitInHalfOpenNanos;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
