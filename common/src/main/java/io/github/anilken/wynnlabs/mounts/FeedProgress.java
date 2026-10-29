package io.github.anilken.wynnlabs.mounts;

import java.util.List;

/**
 * How far a mount has come along its plan: following the plan never makes it stale. The mount
 * now (its stats with its queued foods on top) is compared with the plan's prediction after each
 * of its first k steps; when it matches some (the same limits, levels at least the trained ones,
 * and the foods in its queue the plan's own, in order) the plan is on track at the furthest such k.
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
        // the furthest step it matches: rides already done match the steps before them too
        List<FeedStep> steps = plan.steps();
        MountStats predicted = planStart;
        int reached = -1;
        for (int k = 0; k <= steps.size(); k++) {
            if (k > 0) {
                predicted = apply(predicted, steps.get(k - 1));
            }
            if (matches(here, predicted) && queueIsThePlans(steps, k, queued)) {
                reached = k;
            }
        }
        return reached;
    }

    /** Whether a step is a ride already done: the stat's level now is at least its target. */
    public static boolean rideDone(FeedStep s, MountStats now) {
        return s != null && !s.isFeed() && s.stat() != null && now != null && now.level(s.stat().ordinal()) >= s.toLevel();
    }

    /**
     * The plan as it stands now ({@code now}: the mount's stats; {@code secPerLevel}: riding time a
     * level): the rides already done left out, and each ride left only as long as its levels still
     * to go, never longer than planned. The same plan when nothing changes.
     */
    public static FeedPlan pending(FeedPlan p, MountStats now, int secPerLevel) {
        if (p == null || now == null) {
            return p;
        }
        List<FeedStep> out = new java.util.ArrayList<>(p.steps().size());
        long cut = 0;
        long cutCovered = 0;
        boolean changed = false;
        for (FeedStep s : p.steps()) {
            if (s.isFeed() || s.stat() == null) {
                out.add(s);
                continue;
            }
            if (rideDone(s, now)) {
                cut += s.durationSec() - s.coveredSec();
                cutCovered += s.coveredSec();
                changed = true;
                continue;
            }
            int levels = s.toLevel() - now.level(s.stat().ordinal());
            long dur = secPerLevel > 0 ? Math.min(s.durationSec(), (long) levels * secPerLevel) : s.durationSec();
            if (dur < s.durationSec()) {
                long covered = Math.min(s.coveredSec(), dur);
                cut += (s.durationSec() - s.coveredSec()) - (dur - covered);
                cutCovered += s.coveredSec() - covered;
                out.add(FeedStep.train(s.stat(), s.toLevel(), dur, covered));
                changed = true;
            } else {
                out.add(s);
            }
        }
        if (!changed) {
            return p;
        }
        return new FeedPlan(p.goalReached(), p.proven(), out, Math.max(0, p.totalTimeSec() - cut), p.feedTimeSec(),
                Math.max(0, p.trainingTimeSec() - cut), Math.max(0, p.coveredTrainingSec() - cutCovered), p.end(), p.explored(),
                p.source());
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
