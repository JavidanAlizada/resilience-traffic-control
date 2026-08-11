package dev.trafficcontrol.timeout;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Small shared helper so the two background-thread schedulers don't each roll their own. */
final class DaemonThreads {

    private DaemonThreads() {
    }

    static ThreadFactory factory(String namePrefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
