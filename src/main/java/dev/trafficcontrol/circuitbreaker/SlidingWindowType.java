package dev.trafficcontrol.circuitbreaker;

/** Which SlidingWindow the breaker's trip decision is based on. */
public enum SlidingWindowType {

    /** Default: the last N calls, lock-free ring buffer. slidingWindowSize is a call count. */
    COUNT_BASED,

    /** Calls from the last N seconds, per-second buckets behind a lock. slidingWindowSize is in seconds. */
    TIME_BASED
}
