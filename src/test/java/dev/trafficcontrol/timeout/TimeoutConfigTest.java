package dev.trafficcontrol.timeout;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TimeoutConfigTest {

    @Test
    void defaultsToHashedWheelAndAVirtualThreadWorkerExecutor() {
        TimeoutConfig config = TimeoutConfig.builder().build();

        assertSame(TimeoutAlgorithm.HASHED_WHEEL, config.algorithm());
        assertNotNull(config.syncWorkerExecutor());
    }

    @Test
    void acceptsExplicitAlgorithmAndWorkerExecutor() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TimeoutConfig config = TimeoutConfig.builder()
                .algorithm(TimeoutAlgorithm.SCHEDULED_EXECUTOR)
                .syncWorkerExecutor(executor)
                .build();

        assertSame(TimeoutAlgorithm.SCHEDULED_EXECUTOR, config.algorithm());
        assertSame(executor, config.syncWorkerExecutor());
        executor.shutdown();
    }

    @Test
    void rejectsNullAlgorithmAndWorkerExecutor() {
        TimeoutConfig.Builder builder = TimeoutConfig.builder();
        assertThrows(NullPointerException.class, () -> builder.algorithm(null));
        assertThrows(NullPointerException.class, () -> builder.syncWorkerExecutor(null));
    }
}
