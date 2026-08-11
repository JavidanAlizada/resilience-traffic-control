package dev.trafficcontrol.ratelimiter;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wraps any {@link RateLimiter} and reports every admit/reject to
 * registered {@link RateLimiterListener}s — the seam a metrics or logging
 * hook plugs into without any of the four algorithms knowing it exists.
 *
 * <p>{@code CopyOnWriteArrayList} because listeners get registered once at
 * setup and then only ever read, concurrently, on every hot-path call.
 */
public final class ObservableRateLimiter implements RateLimiter {

    private final RateLimiter delegate;
    private final List<RateLimiterListener> listeners = new CopyOnWriteArrayList<>();

    public ObservableRateLimiter(RateLimiter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public ObservableRateLimiter addListener(RateLimiterListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    @Override
    public boolean tryAcquire(int permits) {
        boolean admitted = delegate.tryAcquire(permits);
        for (RateLimiterListener listener : listeners) {
            if (admitted) {
                listener.onAdmit(permits);
            } else {
                listener.onReject(permits);
            }
        }
        return admitted;
    }
}
