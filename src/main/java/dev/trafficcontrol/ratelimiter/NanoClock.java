package dev.trafficcontrol.ratelimiter;

/** Monotonic time source, swappable so tests don't have to sleep for real. */
@FunctionalInterface
public interface NanoClock {

    NanoClock SYSTEM = System::nanoTime;

    long nanoTime();
}
