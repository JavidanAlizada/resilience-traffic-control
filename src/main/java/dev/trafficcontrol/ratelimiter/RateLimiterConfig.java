package dev.trafficcontrol.ratelimiter;

import java.util.Objects;

/**
 * Immutable, validated settings for a {@link RateLimiter} — invalid values
 * fail in {@link Builder#build()}, not on the first live request.
 *
 * <p>Note: {@code burstCapacity} only means something for GCRA and
 * TOKEN_BUCKET (the classic burst allowance). The window algorithms use a
 * fixed one-second window sized by {@code permitsPerSecond} and ignore it.
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

    /** Fails fast on bad input instead of letting a broken config reach production. */
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
