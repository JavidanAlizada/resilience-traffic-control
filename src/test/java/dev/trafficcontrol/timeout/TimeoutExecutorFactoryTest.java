package dev.trafficcontrol.timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TimeoutExecutorFactoryTest {

    @Test
    void wiresUpAWorkingExecutorForEveryAlgorithm() throws Exception {
        for (TimeoutAlgorithm algorithm : TimeoutAlgorithm.values()) {
            TimeoutConfig config = TimeoutConfig.builder().algorithm(algorithm).build();
            try (TimeoutExecutor executor = TimeoutExecutorFactory.create(config)) {
                assertEquals("ok", executor.execute(Duration.ofMillis(200), () -> "ok"));
            }
        }
    }
}
