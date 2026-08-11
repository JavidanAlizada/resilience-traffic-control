package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RateLimiterConfigTest {

    @Test
    void buildsWithValidValues() {
        FakeClock clock = new FakeClock(0);
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.TOKEN_BUCKET)
                .permitsPerSecond(10)
                .burstCapacity(20)
                .clock(clock)
                .build();

        assertEquals(RateLimiterAlgorithm.TOKEN_BUCKET, config.algorithm());
        assertEquals(10, config.permitsPerSecond());
        assertEquals(20, config.burstCapacity());
        assertSame(clock, config.clock());
    }

    @Test
    void defaultsToGcraAndSystemClock() {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .permitsPerSecond(5)
                .burstCapacity(5)
                .build();

        assertEquals(RateLimiterAlgorithm.GCRA, config.algorithm());
        assertSame(NanoClock.SYSTEM, config.clock());
    }

    @Test
    void rejectsMissingPermitsPerSecond() {
        assertThrows(IllegalArgumentException.class, () ->
                RateLimiterConfig.builder().burstCapacity(5).build());
    }

    @Test
    void rejectsNonPositivePermitsPerSecond() {
        assertThrows(IllegalArgumentException.class, () ->
                RateLimiterConfig.builder().permitsPerSecond(0).burstCapacity(5).build());
        assertThrows(IllegalArgumentException.class, () ->
                RateLimiterConfig.builder().permitsPerSecond(-1).burstCapacity(5).build());
    }

    @Test
    void rejectsBurstCapacityBelowOne() {
        assertThrows(IllegalArgumentException.class, () ->
                RateLimiterConfig.builder().permitsPerSecond(5).burstCapacity(0).build());
    }

    @Test
    void rejectsNullAlgorithmAndClock() {
        RateLimiterConfig.Builder builder = RateLimiterConfig.builder();
        assertThrows(NullPointerException.class, () -> builder.algorithm(null));
        assertThrows(NullPointerException.class, () -> builder.clock(null));
    }
}
