package io.github.anilken.wynnlabs.mounts;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The one low-priority planner thread. Jobs run by priority (the mount the player works on now first, then road
 * maps, then prefetching), and a running search gives the CPU back in slices: after {@code sliceMs} of work it
 * sleeps {@code pauseMs}, so the game never loses a whole core to planning. Calls made off this thread (tests,
 * the debug API) run at full speed.
 */
final class FeedWorker {
    static final int NOW = 0;
    static final int ROAD = 1;
    static final int PREFETCH = 2;

    private record Task(int priority, long seq, Runnable run) implements Comparable<Task> {
        @Override
        public int compareTo(Task o) {
            return priority != o.priority ? Integer.compare(priority, o.priority) : Long.compare(seq, o.seq);
        }
    }

    private static final PriorityBlockingQueue<Task> QUEUE = new PriorityBlockingQueue<>();
    private static final AtomicLong SEQ = new AtomicLong();
    private static final ThreadMXBean MX = ManagementFactory.getThreadMXBean();
    private static volatile int sliceMs = 8;
    private static volatile int pauseMs = 12;
    private static volatile Thread thread;
    // pacing state of the worker thread (only touched by it)
    private static long sliceStartNs;
    private static long sliceStartCpu;
    /** Longest CPU time of one slice so far (ns), and slices run: for the debug API and tests. */
    static volatile long maxSliceCpuNs;
    static volatile long slices;
    /** Slices that ran past twice the slice length (a single step that could not be split). */
    static volatile long longSlices;

    private FeedWorker() {
    }

    static void pacing(int slice, int pause) {
        sliceMs = Math.max(1, slice);
        pauseMs = Math.max(0, pause);
    }

    static void submit(int priority, Runnable r) {
        start();
        QUEUE.add(new Task(priority, SEQ.incrementAndGet(), r));
    }

    private static synchronized void start() {
        // a thread that died (anything escaping the loop) is replaced: its jobs would wait for ever
        if (thread != null && thread.isAlive()) {
            return;
        }
        Thread t = new Thread(FeedWorker::loop, "WynnLabs mount planner");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        thread = t;
        t.start();
    }

    private static void loop() {
        for (;;) {
            Task task;
            try {
                task = QUEUE.take();
            } catch (InterruptedException e) {
                continue;                       // an interrupt must not end the one planner thread
            }
            try {
                sliceStartNs = System.nanoTime();
                sliceStartCpu = cpu();
                task.run().run();
            } catch (Throwable t) {
                System.out.println("[WynnLabs] mount planner job failed: " + t);
            }
        }
    }

    /** Called often by the searches: on the worker thread, sleeps once the current slice is used up. */
    static void tick() {
        if (Thread.currentThread() != thread) {
            return;
        }
        long now = System.nanoTime();
        if (now - sliceStartNs < sliceMs * 1_000_000L) {
            return;
        }
        long c = cpu();
        maxSliceCpuNs = Math.max(maxSliceCpuNs, c - sliceStartCpu);
        slices++;
        if (c - sliceStartCpu > 2L * sliceMs * 1_000_000L) {
            longSlices++;
        }
        if (pauseMs > 0) {
            try {
                Thread.sleep(pauseMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        sliceStartNs = System.nanoTime();
        sliceStartCpu = cpu();
    }

    private static long cpu() {
        try {
            return MX.isCurrentThreadCpuTimeSupported() ? MX.getCurrentThreadCpuTime() : System.nanoTime();
        } catch (UnsupportedOperationException e) {
            return System.nanoTime();
        }
    }
}
