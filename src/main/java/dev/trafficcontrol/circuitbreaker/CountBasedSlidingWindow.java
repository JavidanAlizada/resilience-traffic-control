package dev.trafficcontrol.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The last N outcomes in a lock-free ring buffer.
 *
 * Each writer claims a sequence number, swaps its outcome into the slot
 * with getAndSet, and adjusts the running counters by the difference
 * between what it wrote and what it actually evicted. Because every slot's
 * history is a single total order of swaps, each outcome is added once and
 * subtracted once, so once writers go quiet the counters equal a recount of
 * the slots. While writers are in flight the three counters can be briefly
 * out of step with each other (skew bounded by the number of concurrent
 * writers) -- accepted, since a trip decision is statistical anyway.
 */
final class CountBasedSlidingWindow implements SlidingWindow {

    private static final int EMPTY = 0;
    private static final int PRESENT = 1;
    private static final int FAILED = 1 << 1;
    private static final int SLOW = 1 << 2;

    private final int size;
    private final AtomicIntegerArray slots;
    private final AtomicLong cursor = new AtomicLong();
    private final AtomicInteger totalCalls = new AtomicInteger();
    private final AtomicInteger failedCalls = new AtomicInteger();
    private final AtomicInteger slowCalls = new AtomicInteger();

    CountBasedSlidingWindow(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1, was " + size);
        }
        this.size = size;
        this.slots = new AtomicIntegerArray(size);
    }

    @Override
    public void record(boolean failed, boolean slow) {
        int outcome = PRESENT | (failed ? FAILED : 0) | (slow ? SLOW : 0);
        int slot = (int) (cursor.getAndIncrement() % size);
        int evicted = slots.getAndSet(slot, outcome);

        if (evicted == EMPTY) {
            totalCalls.incrementAndGet();
        }
        adjust(failedCalls, outcome, evicted, FAILED);
        adjust(slowCalls, outcome, evicted, SLOW);
    }

    private static void adjust(AtomicInteger counter, int added, int evicted, int flag) {
        int delta = ((added & flag) != 0 ? 1 : 0) - ((evicted & flag) != 0 ? 1 : 0);
        if (delta != 0) {
            counter.addAndGet(delta);
        }
    }

    @Override
    public WindowSnapshot snapshot() {
        return new WindowSnapshot(totalCalls.get(), failedCalls.get(), slowCalls.get());
    }

    /** Brute-force count of the slots, for checking the running counters in tests. */
    WindowSnapshot recount() {
        int total = 0;
        int failed = 0;
        int slow = 0;
        for (int i = 0; i < size; i++) {
            int outcome = slots.get(i);
            if (outcome != EMPTY) {
                total++;
                failed += (outcome & FAILED) != 0 ? 1 : 0;
                slow += (outcome & SLOW) != 0 ? 1 : 0;
            }
        }
        return new WindowSnapshot(total, failed, slow);
    }
}
