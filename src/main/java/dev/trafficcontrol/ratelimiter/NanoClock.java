package dev.trafficcontrol.ratelimiter;

/**
 * Seam for monotonic time, so every algorithm in this package can be driven
 * by a fake clock in tests instead of {@code Thread.sleep}. {@link #SYSTEM}
 * is the production strategy; tests supply their own.
 */
@FunctionalInterface
public interface NanoClock {

    NanoClock SYSTEM = System::nanoTime;

    long nanoTime();
}
