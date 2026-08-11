package dev.trafficcontrol.timeout;

/** Which {@link TimeoutScheduler} backs the async path -- see docs/algorithms/timeout-management.md. */
public enum TimeoutAlgorithm {

    /** JDK {@code ScheduledThreadPoolExecutor}, O(log n) schedule/cancel. */
    SCHEDULED_EXECUTOR,

    /** One virtual thread per pending timeout. */
    VIRTUAL_THREAD,

    /** Flagship: hand-rolled timer wheel, O(1) schedule. */
    HASHED_WHEEL
}
