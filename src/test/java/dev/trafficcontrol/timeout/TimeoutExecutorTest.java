package dev.trafficcontrol.timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TimeoutExecutorTest {

    private final TimeoutExecutor executor = TimeoutExecutorFactory.create(TimeoutConfig.builder().build());

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void syncExecuteReturnsTheResultWhenWithinBudget() throws Exception {
        String result = executor.execute(Duration.ofMillis(200), () -> "ok");
        assertEquals("ok", result);
    }

    @Test
    void syncExecuteThrowsAndInterruptsTheWorkerOnTimeout() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();

        assertThrows(TimeoutException.class, () -> executor.execute(Duration.ofMillis(20), () -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
            return "unreachable";
        }));

        Thread.sleep(100); // give the worker a moment to actually process the interrupt
        assertTrue(interrupted.get(), "the worker should have been interrupted (best-effort cancellation)");
    }

    @Test
    void asyncCompletesNormallyWhenTheFutureFinishesInTime() throws Exception {
        CompletableFuture<String> result =
                executor.executeAsync(Duration.ofMillis(200), () -> CompletableFuture.completedFuture("ok"));
        assertEquals("ok", result.get(1, TimeUnit.SECONDS));
    }

    @Test
    void asyncCompletesExceptionallyWhenTheTimeoutFiresFirst() {
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();
        CompletableFuture<String> result = executor.executeAsync(Duration.ofMillis(20), () -> neverCompletes);

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> result.get(1, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, thrown.getCause());
    }

    @Test
    void syncExecuteAcceptsADeadline() throws Exception {
        Deadline deadline = Deadline.after(Duration.ofMillis(200), new FakeClock(0));
        assertEquals("ok", executor.execute(deadline, () -> "ok"));
    }

    @Test
    void asyncExecuteAcceptsADeadline() throws Exception {
        Deadline deadline = Deadline.after(Duration.ofMillis(200), new FakeClock(0));
        CompletableFuture<String> result =
                executor.executeAsync(deadline, () -> CompletableFuture.completedFuture("ok"));
        assertEquals("ok", result.get(1, TimeUnit.SECONDS));
    }
}
