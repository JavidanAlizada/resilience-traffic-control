package dev.trafficcontrol.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Randomized property test: drive the real breaker and a deliberately
 * naive single-threaded model with the same random sequence of successes,
 * failures and clock jumps, and require them to agree after every step on
 * the state, on whether the call was admitted, and on the reported counts.
 */
class CircuitBreakerModelTest {

    private static final long WAIT = 1_000;

    // Seeded so a failing run is reproducible.
    private final Random random = new Random(7);

    @Test
    void breakerMatchesReferenceModel() {
        for (int run = 0; run < 500; run++) {
            int windowSize = 1 + random.nextInt(12);
            int minimumCalls = 1 + random.nextInt(windowSize);
            int permittedInHalfOpen = 1 + random.nextInt(5);
            double threshold = 1 + random.nextInt(100);

            FakeClock clock = new FakeClock(random.nextLong());
            CircuitBreaker breaker = CircuitBreakers.of(CircuitBreakerConfig.builder()
                    .slidingWindowSize(windowSize)
                    .minimumNumberOfCalls(minimumCalls)
                    .failureRateThreshold(threshold)
                    .permittedCallsInHalfOpenState(permittedInHalfOpen)
                    .waitDurationInOpenState(Duration.ofNanos(WAIT))
                    .maxWaitDurationInHalfOpenState(Duration.ZERO)
                    .clock(clock)
                    .build());
            Model model = new Model(windowSize, minimumCalls, permittedInHalfOpen, threshold);

            for (int step = 0; step < 200; step++) {
                String where = "run " + run + ", step " + step;
                if (random.nextInt(4) == 0) {
                    clock.advance(random.nextInt((int) (2 * WAIT)));
                    continue;
                }
                boolean failed = random.nextBoolean();
                boolean expectAdmitted = model.call(clock.nanoTime(), failed);
                boolean admitted;
                try {
                    CircuitBreaker.Permit permit = breaker.acquirePermission();
                    admitted = true;
                    if (failed) {
                        permit.onFailure(0, new IOException());
                    } else {
                        permit.onSuccess(0);
                    }
                } catch (CallNotPermittedException e) {
                    admitted = false;
                }

                assertEquals(expectAdmitted, admitted, where);
                assertEquals(model.state, breaker.state(), where);
                assertEquals(model.reportedTotal(), breaker.metrics().totalCalls(), where);
                assertEquals(model.reportedFailed(), breaker.metrics().failedCalls(), where);
            }
        }
    }

    /** The breaker's rules written as plainly as possible, with no concurrency to worry about. */
    private static final class Model {

        private final int windowSize;
        private final int minimumCalls;
        private final int permittedInHalfOpen;
        private final double threshold;

        private CircuitBreaker.State state = CircuitBreaker.State.CLOSED;
        private final Deque<Boolean> window = new ArrayDeque<>();
        private long openedAt;
        private int tripTotal;
        private int tripFailed;
        private int trialsDone;
        private int trialsFailed;

        Model(int windowSize, int minimumCalls, int permittedInHalfOpen, double threshold) {
            this.windowSize = windowSize;
            this.minimumCalls = minimumCalls;
            this.permittedInHalfOpen = permittedInHalfOpen;
            this.threshold = threshold;
        }

        /** Returns whether the call is admitted; if it is, applies its outcome. */
        boolean call(long now, boolean failed) {
            if (state == CircuitBreaker.State.OPEN) {
                if (now - openedAt < WAIT) {
                    return false;
                }
                state = CircuitBreaker.State.HALF_OPEN;
                trialsDone = 0;
                trialsFailed = 0;
            }
            if (state == CircuitBreaker.State.CLOSED) {
                window.addLast(failed);
                if (window.size() > windowSize) {
                    window.removeFirst();
                }
                int failures = (int) window.stream().filter(f -> f).count();
                if (window.size() >= minimumCalls && failures * 100.0 / window.size() >= threshold) {
                    open(now, window.size(), failures);
                }
                return true;
            }
            // HALF_OPEN: results come back immediately here, so a trial slot is always free.
            trialsDone++;
            trialsFailed += failed ? 1 : 0;
            if (trialsDone == permittedInHalfOpen) {
                if (trialsFailed * 100.0 / trialsDone >= threshold) {
                    open(now, trialsDone, trialsFailed);
                } else {
                    state = CircuitBreaker.State.CLOSED;
                    window.clear();
                }
            }
            return true;
        }

        private void open(long now, int total, int failed) {
            state = CircuitBreaker.State.OPEN;
            openedAt = now;
            tripTotal = total;
            tripFailed = failed;
        }

        int reportedTotal() {
            return switch (state) {
                case CLOSED -> window.size();
                case OPEN -> tripTotal;
                case HALF_OPEN -> trialsDone;
            };
        }

        int reportedFailed() {
            return switch (state) {
                case CLOSED -> (int) window.stream().filter(f -> f).count();
                case OPEN -> tripFailed;
                case HALF_OPEN -> trialsFailed;
            };
        }
    }
}
