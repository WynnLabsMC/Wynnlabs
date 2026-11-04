package io.github.anilken.wynnlabs.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every worker runs on a daemon thread with the name and priority it had before the shared factory. */
class WorkersTest {

    /** Name, daemon flag and priority of the thread a job runs on. */
    private record Seen(String name, boolean daemon, int priority) {}

    private static Seen run(ExecutorService exec) throws Exception {
        try {
            return exec.submit(() -> {
                Thread t = Thread.currentThread();
                return new Seen(t.getName(), t.isDaemon(), t.getPriority());
            }).get(10, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void singleIsANamedDaemonAtTheCallersPriority() throws Exception {
        assertEquals(new Seen("wynnlabs-box", true, Thread.currentThread().getPriority()), run(Workers.single("box")));
    }

    @Test
    void singleLowRunsAtTheLowestPriority() throws Exception {
        assertEquals(new Seen("wynnlabs-roadmap", true, Thread.MIN_PRIORITY), run(Workers.singleLow("roadmap")));
    }

    @Test
    void factoryKeepsTheWholeName() {
        Thread normal = Workers.factory("WynnLabs saver", false).newThread(() -> { });
        assertEquals("WynnLabs saver", normal.getName());
        assertTrue(normal.isDaemon());
        assertEquals(Thread.currentThread().getPriority(), normal.getPriority());

        Thread low = Workers.factory("WynnLabs mount planner", true).newThread(() -> { });
        assertEquals("WynnLabs mount planner", low.getName());
        assertTrue(low.isDaemon());
        assertEquals(Thread.MIN_PRIORITY, low.getPriority());
    }
}
