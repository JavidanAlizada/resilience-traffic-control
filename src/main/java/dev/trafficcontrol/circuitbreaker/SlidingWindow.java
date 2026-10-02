package dev.trafficcontrol.circuitbreaker;

/** Recent call outcomes the breaker's trip decision is based on. */
interface SlidingWindow {

    /** Slowness is independent of failure: a slow call can still be a success. */
    void record(boolean failed, boolean slow);

    WindowSnapshot snapshot();
}
