package dev.trafficcontrol.circuitbreaker;

import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static dev.trafficcontrol.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

/** The Decorator path: execute / executeAsync time the call and record its outcome. */
class CircuitBreakerExecuteTest {

    private static final long SECOND = Duration.ofSeconds(1).toNanos();

    private final FakeClock clock = new FakeClock(0);

    private CircuitBreaker breaker(int permittedInHalfOpen) {
        return CircuitBreakers.of(CircuitBreakerConfig.builder()
                .slidingWindowSize(2)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedCallsInHalfOpenState(permittedInHalfOpen)
                .clock(clock)
                .build());
    }

    private void trip(CircuitBreaker breaker) {
        for (int i = 0; i < 2; i++) {
            assertThrows(IOException.class, () -> breaker.execute(() -> {
                throw new IOException("down");
            }));
        }
        assertEquals(OPEN, breaker.state());
    }

    @Test
    void returnsTheResultAndRecordsASuccess() throws Exception {
        CircuitBreaker breaker = breaker(1);

        assertEquals("ok", breaker.execute(() -> "ok"));
        assertEquals(1, breaker.metrics().totalCalls());
        assertEquals(0, breaker.metrics().failedCalls());
    }

    @Test
    void rethrowsTheSameFailureAndRecordsIt() {
        CircuitBreaker breaker = breaker(1);
        IOException failure = new IOException("down");

        IOException thrown = assertThrows(IOException.class, () -> breaker.execute(() -> {
            throw failure;
        }));

        assertSame(failure, thrown);
        assertEquals(1, breaker.metrics().failedCalls());
    }

    @Test
    void timesTheCallWithTheInjectedClock() throws Exception {
        CircuitBreaker breaker = breaker(1);

        breaker.execute(() -> {
            clock.advance(SECOND);
            return "slow";
        });

        assertEquals(1, breaker.metrics().slowCalls());
    }

    @Test
    void openBreakerDoesNotRunTheCall() {
        CircuitBreaker breaker = breaker(1);
        trip(breaker);
        boolean[] ran = {false};

        assertThrows(CallNotPermittedException.class, () -> breaker.execute(() -> {
            ran[0] = true;
            return null;
        }));
        assertFalse(ran[0]);
    }

    @Test
    void interruptionReleasesThePermitAndKeepsTheFlag() {
        CircuitBreaker breaker = breaker(1);
        trip(breaker);
        clock.advance(10 * SECOND);

        assertThrows(InterruptedException.class, () -> breaker.execute(() -> {
            throw new InterruptedException();
        }));

        assertTrue(Thread.interrupted(), "interrupt flag restored (and cleared here)");
        assertEquals(HALF_OPEN, breaker.state());
        assertEquals(0, breaker.metrics().totalCalls(), "not counted as a trial result");
        breaker.acquirePermission(); // the trial slot came back
    }

    @Test
    void errorsReleaseThePermitInsteadOfCountingAsFailures() {
        CircuitBreaker breaker = breaker(1);

        assertThrows(StackOverflowError.class, () -> breaker.execute(() -> {
            throw new StackOverflowError();
        }));

        assertEquals(0, breaker.metrics().totalCalls());
    }

    @Test
    void asyncSuccessAndFailureAreRecorded() {
        CircuitBreaker breaker = breaker(1);

        assertEquals("ok", breaker.executeAsync(() -> CompletableFuture.completedFuture("ok")).join());
        CompletableFuture<String> failed = breaker.executeAsync(
                () -> CompletableFuture.failedFuture(new IOException("down")));

        CompletionException thrown = assertThrows(CompletionException.class, failed::join);
        assertInstanceOf(IOException.class, thrown.getCause());
        assertEquals(1, breaker.metrics().failedCalls());
        assertEquals(OPEN, breaker.state());
    }

    @Test
    void asyncRejectionIsAFailedFutureNotAThrow() {
        CircuitBreaker breaker = breaker(1);
        trip(breaker);

        CompletableFuture<String> rejected = breaker.executeAsync(() -> CompletableFuture.completedFuture("x"));

        CompletionException thrown = assertThrows(CompletionException.class, rejected::join);
        assertInstanceOf(CallNotPermittedException.class, thrown.getCause());
    }

    @Test
    void asyncSupplierThrowingIsRecordedAsAFailure() {
        CircuitBreaker breaker = breaker(1);

        CompletableFuture<String> result = breaker.executeAsync(() -> {
            throw new IllegalStateException("couldn't even start");
        });

        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, breaker.metrics().failedCalls());
    }

    @Test
    void asyncCancellationReleasesTheTrialSlot() {
        CircuitBreaker breaker = breaker(1);
        trip(breaker);
        clock.advance(10 * SECOND);
        CompletableFuture<String> pending = new CompletableFuture<>();

        breaker.executeAsync(() -> pending);
        assertThrows(CallNotPermittedException.class, breaker::acquirePermission, "only slot is taken");
        pending.cancel(false);

        breaker.acquirePermission();
        assertEquals(0, breaker.metrics().totalCalls());
    }
}
