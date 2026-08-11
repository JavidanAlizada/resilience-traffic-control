package dev.trafficcontrol.timeout;

import java.util.concurrent.TimeUnit;

/**
 * One virtual thread per pending timeout: sleep for the delay, then fire
 * unless interrupted first. Platform threads made this pattern a bad idea
 * at any real scale; virtual threads are cheap enough (KB, not MB, and
 * M:N onto carrier threads) that it's worth having as a real option, not
 * just a curiosity.
 *
 * <p>Cancellation races the firing the same way {@code Future.cancel} does
 * on an already-running task: if {@code cancel()} lands after the sleep has
 * already woken up, the timeout may still fire. Best-effort, not a
 * guarantee -- same posture as the other two schedulers.
 */
public final class VirtualThreadTimeoutScheduler implements TimeoutScheduler {

    @Override
    public Cancellable scheduleTimeout(long delayNanos, Runnable onTimeout) {
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                TimeUnit.NANOSECONDS.sleep(delayNanos);
                onTimeout.run();
            } catch (InterruptedException e) {
                // expected when cancel() interrupts the sleep before it fires
            }
        });
        return thread::interrupt;
    }
}
