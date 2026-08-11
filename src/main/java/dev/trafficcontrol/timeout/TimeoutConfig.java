package dev.trafficcontrol.timeout;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Immutable, validated settings for a TimeoutExecutor. */
public final class TimeoutConfig {

    private final TimeoutAlgorithm algorithm;
    private final ExecutorService syncWorkerExecutor;

    private TimeoutConfig(Builder builder) {
        this.algorithm = builder.algorithm;
        this.syncWorkerExecutor = builder.syncWorkerExecutor;
    }

    public TimeoutAlgorithm algorithm() {
        return algorithm;
    }

    public ExecutorService syncWorkerExecutor() {
        return syncWorkerExecutor;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private TimeoutAlgorithm algorithm = TimeoutAlgorithm.HASHED_WHEEL;
        private ExecutorService syncWorkerExecutor;

        private Builder() {
        }

        public Builder algorithm(TimeoutAlgorithm algorithm) {
            this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
            return this;
        }

        /** Where synchronous execute calls actually run. Defaults to a virtual-thread-per-task executor. */
        public Builder syncWorkerExecutor(ExecutorService syncWorkerExecutor) {
            this.syncWorkerExecutor = Objects.requireNonNull(syncWorkerExecutor, "syncWorkerExecutor");
            return this;
        }

        public TimeoutConfig build() {
            if (syncWorkerExecutor == null) {
                syncWorkerExecutor = Executors.newVirtualThreadPerTaskExecutor();
            }
            return new TimeoutConfig(this);
        }
    }
}
