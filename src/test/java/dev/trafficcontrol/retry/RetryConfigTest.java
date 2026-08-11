package dev.trafficcontrol.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryConfigTest {

    @Test
    void defaultsToThreeAttemptsExponentialBackoffAndRetryEverything() {
        RetryConfig config = RetryConfig.builder().build();

        assertEquals(3, config.maxAttempts());
        assertNotNull(config.backoffStrategy());
        assertNotNull(config.scheduler());
        assertTrue(config.retryPredicate().test(new RuntimeException()));
        assertTrue(config.retryPredicate().test(new Exception()));
    }

    @Test
    void rejectsNonPositiveMaxAttempts() {
        RetryConfig.Builder builder = RetryConfig.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.maxAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maxAttempts(-1));
    }

    @Test
    void rejectsNullArguments() {
        RetryConfig.Builder builder = RetryConfig.builder();
        assertThrows(NullPointerException.class, () -> builder.backoffStrategy(null));
        assertThrows(NullPointerException.class, () -> builder.retryPredicate(null));
        assertThrows(NullPointerException.class, () -> builder.scheduler(null));
    }

    @Test
    void acceptsExplicitOverrides() {
        BackoffStrategy backoff = new FixedDelayBackoff(Duration.ofMillis(10));
        RetryConfig config = RetryConfig.builder()
                .maxAttempts(5)
                .backoffStrategy(backoff)
                .retryPredicate(failure -> failure instanceof IllegalStateException)
                .build();

        assertEquals(5, config.maxAttempts());
        assertEquals(backoff, config.backoffStrategy());
        assertTrue(config.retryPredicate().test(new IllegalStateException()));
        assertFalse(config.retryPredicate().test(new RuntimeException()));
    }
}
