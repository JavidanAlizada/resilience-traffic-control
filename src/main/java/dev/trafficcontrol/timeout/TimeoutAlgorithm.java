package dev.trafficcontrol.timeout;

/** Which TimeoutScheduler backs the async path. */
public enum TimeoutAlgorithm {

    /** JDK ScheduledThreadPoolExecutor, O(log n) schedule/cancel. */
    SCHEDULED_EXECUTOR,

    /** One virtual thread per pending timeout. */
    VIRTUAL_THREAD,

    /** Flagship: hand-rolled timer wheel, O(1) schedule. */
    HASHED_WHEEL
}
