package io.github.anilken.wynnlabs.mounts;

import java.util.concurrent.CompletableFuture;

/**
 * A feed plan being worked out on the planner thread. {@link #current()} is the best plan so far: a quick
 * estimate ({@code proven=false}, source "estimate") within a moment, replaced by the exact plan
 * ({@code proven=true}) when the search finishes.
 */
public final class FeedJob {
    private final CompletableFuture<FeedPlan> result = new CompletableFuture<>();
    private volatile FeedPlan current;
    private volatile boolean cancelled;
    private final FeedPlanner.Stats stats = new FeedPlanner.Stats();

    FeedJob() {
    }

    /** Best plan so far, or null in the first moment. */
    public FeedPlan current() {
        return current;
    }

    /** The search finished (or failed or was cancelled); {@link #current()} is final. */
    public boolean done() {
        return result.isDone();
    }

    /** Completes with the final plan (exact when proven). */
    public CompletableFuture<FeedPlan> result() {
        return result;
    }

    /** Live search progress: {@code stats().explored()} states expanded, {@code stats().stored()} held. */
    public FeedPlanner.Stats stats() {
        return stats;
    }

    /** True once the current plan is the proven optimum. */
    public boolean proven() {
        FeedPlan p = current;
        return p != null && p.proven();
    }

    /** Stops the search; the job then completes with the best plan so far. */
    public void cancel() {
        cancelled = true;
    }

    boolean cancelled() {
        return cancelled;
    }

    void offer(FeedPlan p) {
        if (p != null && !result.isDone()) {
            current = p;
        }
    }

    void finish(FeedPlan p) {
        if (p != null) {
            current = p;
        }
        result.complete(current);
    }

    void fail(Throwable t) {
        if (current != null) {
            result.complete(current);
        } else {
            result.completeExceptionally(t);
        }
    }
}
