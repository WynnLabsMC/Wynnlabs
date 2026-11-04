package io.github.anilken.wynnlabs.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Builds the mod's background workers: daemon threads, so none of them keeps the game from
 * closing. {@link #single}, {@link #singleLow} and {@link #scheduled} name their thread
 * {@code wynnlabs-<name>}; {@link #factory} takes the whole thread name, for the workers whose
 * names (shown in /perf and crash logs) predate that rule or that are not a single thread.
 */
public final class Workers {

    private static final String PREFIX = "wynnlabs-";

    private Workers() {}

    /** One thread, one job at a time, at the priority of the thread that starts it. */
    public static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(factory(PREFIX + name, false));
    }

    /** One thread at the lowest priority, for long searches that must not slow the game. */
    public static ExecutorService singleLow(String name) {
        return Executors.newSingleThreadExecutor(factory(PREFIX + name, true));
    }

    /** One thread that runs delayed jobs. */
    public static ScheduledExecutorService scheduled(String name) {
        return Executors.newSingleThreadScheduledExecutor(factory(PREFIX + name, false));
    }

    /**
     * Daemon threads named {@code threadName}; {@code low} puts them at the lowest priority,
     * otherwise they keep the priority of the thread that makes them.
     */
    public static ThreadFactory factory(String threadName, boolean low) {
        return r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            if (low) {
                t.setPriority(Thread.MIN_PRIORITY);
            }
            return t;
        };
    }
}
