package dev.trafficcontrol.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompositeRateLimiterTest {

    // permitsPerSecond=1 everywhere below (not 1000+) so the ~1s refill
    // interval leaves a huge, JVM-warm-up-proof margin against real
    // elapsed test time — these are real-clock smoke tests, not the
    // nanosecond-precision boundary tests the FakeClock-based suites cover.

    @Test
    void admitsOnlyWhenEveryChildAdmits() {
        RateLimiter roomy = RateLimiters.gcra(1, 1000);
        RateLimiter tight = RateLimiters.gcra(1, 1);

        RateLimiter composite = CompositeRateLimiter.allOf(roomy, tight);

        assertTrue(composite.tryAcquire(), "both limiters have capacity");
        assertFalse(composite.tryAcquire(), "the tight limiter is now out of burst capacity");
    }

    @Test
    void rejectsEmptyLimiterList() {
        assertThrows(IllegalArgumentException.class, CompositeRateLimiter::allOf);
    }

    @Test
    void aLaterDenialStillLeavesEarlierLimitersCharged() {
        // Documents the known non-atomicity caveat from the class javadoc:
        // the first limiter's permit is spent even though the composite as a
        // whole rejects the request, because a later child said no.
        RateLimiter first = RateLimiters.gcra(1, 1);
        RateLimiter second = RateLimiters.gcra(1, 1);
        second.tryAcquire(); // exhaust the second limiter's single burst permit up front

        RateLimiter composite = CompositeRateLimiter.allOf(first, second);
        assertFalse(composite.tryAcquire(), "second limiter has no capacity left");

        assertFalse(first.tryAcquire(), "first limiter's permit was already spent by the composite call above");
    }
}
