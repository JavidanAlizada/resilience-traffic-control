package dev.trafficcontrol.ratelimiter;

/** Shared home for the one thing every leaf algorithm needs to check before doing real work. */
abstract class AbstractRateLimiter implements RateLimiter {

    static void requireValidPermits(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, was " + permits);
        }
    }
}
