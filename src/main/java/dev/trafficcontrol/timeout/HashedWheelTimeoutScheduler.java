package dev.trafficcontrol.timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Hand-rolled timer wheel, Netty-style: a ring of buckets, one background
 * thread advancing a tick and firing whatever's due. O(1) to schedule
 * (append to a bucket) instead of the O(log n) a heap-based scheduler
 * needs, at the cost of firing precision bounded by one tick duration --
 * a known, accepted trade-off for timer wheels, not a bug.
 *
 * This one doesn't go through NanoClock: the tick thread's sleeping is
 * real wall-clock time no matter what abstraction sits on top of it, so
 * injecting a fake clock here wouldn't make it any more testable.
 */
public final class HashedWheelTimeoutScheduler implements TimeoutScheduler {

    private final long tickDurationNanos;
    private final int wheelSize;
    private final List<Queue<TimerTask>> wheel;
    private final AtomicLong currentTick = new AtomicLong();
    private final Thread tickThread;
    private volatile boolean running = true;

    public HashedWheelTimeoutScheduler(Duration tickDuration, int wheelSize) {
        if (tickDuration.isZero() || tickDuration.isNegative()) {
            throw new IllegalArgumentException("tickDuration must be positive");
        }
        if (Integer.bitCount(wheelSize) != 1) {
            throw new IllegalArgumentException("wheelSize must be a power of two, was " + wheelSize);
        }
        this.tickDurationNanos = tickDuration.toNanos();
        this.wheelSize = wheelSize;
        this.wheel = new ArrayList<>(wheelSize);
        for (int i = 0; i < wheelSize; i++) {
            wheel.add(new ConcurrentLinkedQueue<>());
        }
        this.tickThread = DaemonThreads.factory("hashed-wheel-timer").newThread(this::runTickLoop);
        this.tickThread.start();
    }

    public static HashedWheelTimeoutScheduler withDefaults() {
        return new HashedWheelTimeoutScheduler(Duration.ofMillis(10), 512);
    }

    @Override
    public Cancellable scheduleTimeout(long delayNanos, Runnable onTimeout) {
        long ticksAhead = Math.max(1, ceilingDivide(delayNanos, tickDurationNanos));
        long targetTick = currentTick.get() + ticksAhead;
        int bucketIndex = (int) (targetTick & (wheelSize - 1));
        long rounds = ticksAhead / wheelSize;
        TimerTask task = new TimerTask(onTimeout, rounds);
        wheel.get(bucketIndex).add(task);
        return () -> task.cancelled = true;
    }

    /** Stops the tick thread. Pending, un-fired tasks are simply dropped. */
    @Override
    public void close() {
        running = false;
        tickThread.interrupt();
    }

    private void runTickLoop() {
        long startNanos = System.nanoTime();
        long tick = 0;
        while (running) {
            long deadlineNanos = startNanos + (tick + 1) * tickDurationNanos;
            long sleepNanos = deadlineNanos - System.nanoTime();
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
            if (!running) {
                return;
            }
            currentTick.set(tick);
            fireBucket((int) (tick & (wheelSize - 1)));
            tick++;
        }
    }

    private void fireBucket(int bucketIndex) {
        Iterator<TimerTask> tasks = wheel.get(bucketIndex).iterator();
        while (tasks.hasNext()) {
            TimerTask task = tasks.next();
            if (task.cancelled) {
                tasks.remove();
            } else if (task.rounds <= 0) {
                tasks.remove();
                task.action.run();
            } else {
                task.rounds--;
            }
        }
    }

    private static long ceilingDivide(long numerator, long denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    /**
     * rounds is only ever touched by the single tick thread: the initial
     * value is published safely via the bucket's ConcurrentLinkedQueue.add,
     * and nothing else reads or writes it after that, so it doesn't need
     * to be volatile. cancelled genuinely crosses threads (a producer
     * thread cancels, the tick thread reads it), so that one does.
     */
    private static final class TimerTask {
        final Runnable action;
        long rounds;
        volatile boolean cancelled;

        TimerTask(Runnable action, long rounds) {
            this.action = action;
            this.rounds = rounds;
        }
    }
}
