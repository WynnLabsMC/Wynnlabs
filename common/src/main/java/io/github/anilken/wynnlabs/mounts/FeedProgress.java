package io.github.anilken.wynnlabs.mounts;

import java.util.List;

/**
 * How far a mount has come along its plan: following the plan never makes it stale. The mount
 * now (its stats with its queued foods on top) is compared with the plan's prediction after each
 * of its first k steps; when it matches one (the same limits, levels at least the trained ones,
 * and the foods in its queue the plan's own, in order) the plan is on track at step k.
 */
public final class FeedProgress {

    private FeedProgress() {}

    /**
     * The step the mount has reached ({@code planStart}: the stats the plan was made from, its queue
     * then on top; {@code now}: the stats read now; {@code queued}: the foods in its queue now), or -1
     * when it matches no step (another food, other stats, another mount): then the plan is stale.
     */
    public static int of(FeedPlan plan, MountStats planStart, MountStats now, List<Material> queued) {
        if (plan == null || planStart == null || now == null) {
            return -1;
        }
        MountStats here = now;
        for (Material m : queued) {
            here = here.fed(m);
        }
        List<FeedStep> steps = plan.steps();
        MountStats predicted = planStart;
        for (int k = 0; k <= steps.size(); k++) {
            if (k > 0) {
                predicted = apply(predicted, steps.get(k - 1));
            }
            if (matches(here, predicted) && queueIsThePlans(steps, k, queued)) {
                return k;
            }
        }
        return -1;
    }

    /** One step's effect: a food raises the limits; a ride raises a stat's level to its target. */
    static MountStats apply(MountStats s, FeedStep step) {
        if (step.isFeed()) {
            return s.fed(step.material());
        }
        if (step.stat() == null) {
            return s;
        }
        int[] lv = new int[MountStat.COUNT];
        int[] li = new int[MountStat.COUNT];
        int[] mx = new int[MountStat.COUNT];
        for (int i = 0; i < MountStat.COUNT; i++) {
            lv[i] = s.level(i);
            li[i] = s.limit(i);
            mx[i] = s.max(i);
        }
        int i = step.stat().ordinal();
        lv[i] = Math.max(lv[i], step.toLevel());
        return MountStats.of(lv, li, mx);
    }

    /** The same limits and maxes, and every level at least the predicted one (riding ahead is fine). */
    private static boolean matches(MountStats now, MountStats predicted) {
        for (int i = 0; i < MountStat.COUNT; i++) {
            if (now.limit(i) != predicted.limit(i) || now.max(i) != predicted.max(i) || now.level(i) < predicted.level(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the foods in the queue now are the plan's, in order: the queue's last foods are the last
     * foods of the plan's first k steps (a food queued before the plan was made may still be ahead of them).
     */
    private static boolean queueIsThePlans(List<FeedStep> steps, int k, List<Material> queued) {
        java.util.List<Material> planned = new java.util.ArrayList<>();
        for (int i = 0; i < k; i++) {
            if (steps.get(i).isFeed()) {
                planned.add(steps.get(i).material());
            }
        }
        int n = Math.min(planned.size(), queued.size());
        for (int i = 1; i <= n; i++) {
            if (!planned.get(planned.size() - i).equals(queued.get(queued.size() - i))) {
                return false;
            }
        }
        return true;
    }
}
