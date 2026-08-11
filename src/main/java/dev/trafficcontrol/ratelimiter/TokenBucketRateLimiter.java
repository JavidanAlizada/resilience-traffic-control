package dev.trafficcontrol.ratelimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Classic token bucket, packed into one {@code AtomicLong} (tokens in the
 * high 32 bits, last-refill millis in the low 32) so it stays a one-CAS
 * design like {@link GcraRateLimiter}. This is the "why not just do this"
 * comparison baseline — see docs/algorithms/token-bucket-and-gcra.md for
 * the precision it gives up to make the packing work.
 */
final class TokenBucketRateLimiter extends AbstractRateLimiter {

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
        requireValidPermits(permits);
        while (true) {
            long oldState = state.get();
            int oldTokens = unpackTokens(oldState);
            int oldMillis = unpackMillis(oldState);

            // Plain int subtraction here wraps the same way TCP sequence numbers
            // do, which is fine as long as the real gap between calls stays
            // under ~24.8 days.
            int nowMillis = (int) (clock.nanoTime() / NANOS_PER_MILLI);
            int elapsedMillis = Math.max(0, nowMillis - oldMillis);
            int tokensAdded = (int) Math.floor(elapsedMillis * permitsPerSecond / 1000.0);

            int refilledTokens = Math.min(burstCapacity, oldTokens + tokensAdded);
            // Only move the refill clock forward when a token actually accrued,
            // otherwise every no-op call would erase the sub-token remainder.
            int refilledMillis = tokensAdded > 0 ? nowMillis : oldMillis;

            boolean admit = refilledTokens >= permits;
            long newState = admit
                    ? pack(refilledTokens - permits, refilledMillis)
                    : pack(refilledTokens, refilledMillis);

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
