package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObservableRateLimiterTest {

    @Test
    void notifiesOnAdmitAndOnReject() {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.GCRA)
                .permitsPerSecond(1)
                .burstCapacity(1)
                .clock(new FakeClock(0))
                .build();
        ObservableRateLimiter limiter = new ObservableRateLimiter(RateLimiterFactory.create(config));

        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        limiter.addListener(new RateLimiterListener() {
            @Override
            public void onAdmit(int permits) {
                admitted.addAndGet(permits);
            }

            @Override
            public void onReject(int permits) {
                rejected.addAndGet(permits);
            }
        });

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        assertEquals(1, admitted.get());
        assertEquals(1, rejected.get());
    }

    @Test
    void notifiesEveryRegisteredListener() {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(RateLimiterAlgorithm.GCRA)
                .permitsPerSecond(1)
                .burstCapacity(1)
                .clock(new FakeClock(0))
                .build();
        ObservableRateLimiter limiter = new ObservableRateLimiter(RateLimiterFactory.create(config));

        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        limiter.addListener(new RateLimiterListener() {
            @Override
            public void onAdmit(int permits) {
                first.incrementAndGet();
            }
        });
        limiter.addListener(new RateLimiterListener() {
            @Override
            public void onAdmit(int permits) {
                second.incrementAndGet();
            }
        });

        limiter.tryAcquire();

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }
}
