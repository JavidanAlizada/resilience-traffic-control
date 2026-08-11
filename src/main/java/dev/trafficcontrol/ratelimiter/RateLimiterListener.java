package dev.trafficcontrol.ratelimiter;

/**
 * Gets told about admit/reject decisions on an {@link ObservableRateLimiter}.
 * Both methods default to doing nothing, so a listener only has to
 * override what it actually cares about.
 */
public interface RateLimiterListener {

    default void onAdmit(int permits) {
    }

    default void onReject(int permits) {
    }
}
