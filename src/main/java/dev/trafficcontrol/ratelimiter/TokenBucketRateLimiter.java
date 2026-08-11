package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Classic token bucket, packed into a single {@link AtomicLong} so it stays
 * a one-CAS design like {@link GcraRateLimiter} — this is the "why not just
 * do this instead of GCRA" comparison baseline (see ADR-003).
 *
 * <p>A token bucket naturally has two fields (token count, last-refill
 * time), which don't fit one CAS-able word without bit-packing. Layout
 * here: high 32 bits = whole token count, low 32 bits = last-refill time in
 * milliseconds, truncated to {@code int}. Comparing truncated millis with
 * plain {@code int} subtraction is the same wraparound-safe trick TCP uses
 * for sequence numbers — correct as long as the true elapsed time between
 * two calls never exceeds ~24.8 days ({@code Integer.MAX_VALUE} ms), which
 * is a large margin for a limiter that's actually being called.
 *
 * <p>Trade-off this buys: millisecond, not nanosecond, refill precision,
 * and up to one refill interval of "leaked" time is discarded whenever
 * tokens are added (the refill reference point resets to {@code now}
 * instead of carrying the sub-token remainder forward). GCRA has neither
 * limitation, precisely because it needs no packing at all — the point of
 * building this class is to make that trade-off concrete instead of just
 * asserting it.
 */
final class TokenBucketRateLimiter implements RateLimiter {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final double permitsPerSecond;
    private final int burstCapacity;
    private final NanoClock clock;
    private final AtomicLong state;

    TokenBucketRateLimiter(RateLimiterConfig config) {
        if (config.burstCapacity() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "burstCapacity too large for the packed representation: " + config.burstCapacity());
        }
        this.permitsPerSecond = config.permitsPerSecond();
        this.burstCapacity = (int) config.burstCapacity();
        this.clock = config.clock();
        int startMillis = (int) (clock.nanoTime() / NANOS_PER_MILLI);
        this.state = new AtomicLong(pack(burstCapacity, startMillis));
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, was " + permits);
        }
        while (true) {
            long oldState = state.get();
            int oldTokens = unpackTokens(oldState);
            int oldMillis = unpackMillis(oldState);

            int nowMillis = (int) (clock.nanoTime() / NANOS_PER_MILLI);
            int elapsedMillis = Math.max(0, nowMillis - oldMillis);
            int tokensAdded = (int) Math.floor(elapsedMillis * permitsPerSecond / 1000.0);

            int refilledTokens = Math.min(burstCapacity, oldTokens + tokensAdded);
            // Only advance the refill reference point when tokens actually accrued,
            // otherwise we'd lose the sub-token elapsed time on every no-op call.
            int refilledMillis = tokensAdded > 0 ? nowMillis : oldMillis;

            long newState;
            boolean admit = refilledTokens >= permits;
            if (admit) {
                newState = pack(refilledTokens - permits, refilledMillis);
            } else {
                newState = pack(refilledTokens, refilledMillis);
            }

            if (state.compareAndSet(oldState, newState)) {
                return admit;
            }
            // lost the CAS race — retry with a fresh read
        }
    }

    private static long pack(int tokens, int millisLow32) {
        return ((long) tokens << 32) | (millisLow32 & 0xFFFFFFFFL);
    }

    private static int unpackTokens(long packed) {
        return (int) (packed >>> 32);
    }

    private static int unpackMillis(long packed) {
        return (int) packed;
    }
}
