package dev.trafficcontrol.ratelimiter;

/**
 * Admission algorithms selectable through {@link RateLimiterConfig}. See
 * docs/algorithms/ for the correctness and memory-model writeup of each.
 */
public enum RateLimiterAlgorithm {

    /** Naive baseline. Kept to demonstrate its own window-boundary burst flaw. */
    FIXED_WINDOW,

    /** Approximate, O(1)-memory blend of the current and previous fixed window. */
    SLIDING_WINDOW_COUNTER,

    /** Packed-state CAS token bucket; comparison baseline for {@link #GCRA}. */
    TOKEN_BUCKET,

    /** Flagship: single-field, single-CAS Generic Cell Rate Algorithm. */
    GCRA
}
