package dev.trafficcontrol.ratelimiter;

/** Admission algorithms selectable through RateLimiterConfig. */
public enum RateLimiterAlgorithm {

    /** Naive baseline. Kept to demonstrate its own window-boundary burst flaw. */
    FIXED_WINDOW,

    /** Approximate, O(1)-memory blend of the current and previous fixed window. */
    SLIDING_WINDOW_COUNTER,

    /** Packed-state CAS token bucket; comparison baseline for GCRA. */
    TOKEN_BUCKET,

    /** Flagship: single-field, single-CAS Generic Cell Rate Algorithm. */
    GCRA
}
