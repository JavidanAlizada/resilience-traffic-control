package dev.trafficcontrol.ratelimiter;

import java.util.Objects;

/**
 * Immutable, validated configuration for a {@link RateLimiter}. Built
 * through {@link Builder} so an invalid combination fails at construction
 * time, not on the first {@code tryAcquire} call deep in a request path.
 *
 * <p>{@code burstCapacity} means different things depending on
 * {@code algorithm}: for {@link RateLimiterAlgorithm#GCRA} and
 * {@link RateLimiterAlgorithm#TOKEN_BUCKET} it's the classic token-bucket
 * burst allowance. {@link RateLimiterAlgorithm#FIXED_WINDOW} and
 * {@link RateLimiterAlgorithm#SLIDING_WINDOW_COUNTER} use a fixed
 * one-second window with {@code permitsPerSecond} (rounded) as the
 * per-window limit and ignore {@code burstCapacity} — window algorithms
 * have no burst concept distinct from the window limit itself.
 */
public final class RateLimiterConfig {

    private final RateLimiterAlgorithm algorithm;
    private final double permitsPerSecond;
    private final long burstCapacity;
    private final NanoClock clock;

    private RateLimiterConfig(Builder builder) {
        this.algorithm = builder.algorithm;
        this.permitsPerSecond = builder.permitsPerSecond;
        this.burstCapacity = builder.burstCapacity;
        this.clock = builder.clock;
    }

    public RateLimiterAlgorithm algorithm() {
        return algorithm;
    }

    public double permitsPerSecond() {
        return permitsPerSecond;
    }

    public long burstCapacity() {
        return burstCapacity;
    }

    public NanoClock clock() {
        return clock;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Validating builder — see class-level docs for what gets checked. */
    public static final class Builder {

        private RateLimiterAlgorithm algorithm = RateLimiterAlgorithm.GCRA;
        private double permitsPerSecond = Double.NaN;
        private long burstCapacity = -1;
        private NanoClock clock = NanoClock.SYSTEM;

        private Builder() {
        }

        public Builder algorithm(RateLimiterAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
            return this;
        }

        public Builder permitsPerSecond(double permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
            return this;
        }

        public Builder burstCapacity(long burstCapacity) {
            this.burstCapacity = burstCapacity;
            return this;
        }

        public Builder clock(NanoClock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public RateLimiterConfig build() {
            if (Double.isNaN(permitsPerSecond) || permitsPerSecond <= 0) {
                throw new IllegalArgumentException(
                        "permitsPerSecond must be > 0, was " + permitsPerSecond);
            }
            if (burstCapacity < 1) {
                throw new IllegalArgumentException(
                        "burstCapacity must be >= 1, was " + burstCapacity);
            }
            return new RateLimiterConfig(this);
        }
    }
}
