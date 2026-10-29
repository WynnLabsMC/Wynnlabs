package io.github.anilken.wynnlabs.mounts;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What the feed planner aims for. Full limits: every limit at its max. Custom: each target stat reaches its target
 * limit (its max when the target is missing or higher) and the limit sum reaches 8 x {@code minAvgLimit}.
 */
public record FeedGoal(boolean fullLimits, Map<MountStat, Integer> targets, int minAvgLimit) {
    public FeedGoal {
        EnumMap<MountStat, Integer> t = new EnumMap<>(MountStat.class);
        if (targets != null) {
            t.putAll(targets);
        }
        targets = Collections.unmodifiableMap(t);
    }

    public static FeedGoal full() {
        return new FeedGoal(true, Map.of(), 0);
    }

    /** Old enough to breed: average limit at least 20, no stat targets. */
    public static FeedGoal breedReady() {
        return new FeedGoal(false, Map.of(), Breeding.constants().eligibleAvgLimit());
    }

    /** Custom goal; a null target value means "that stat's max". */
    public static FeedGoal custom(Map<MountStat, Integer> targets, int minAvgLimit) {
        return new FeedGoal(false, targets, minAvgLimit);
    }

    /** Required limit per stat for these stats (0 = no requirement). */
    public int[] need(MountStats s) {
        int[] need = new int[MountStat.COUNT];
        for (int i = 0; i < need.length; i++) {
            if (fullLimits) {
                need[i] = s.max(i);
            } else if (targets.containsKey(MountStat.of(i))) {
                Integer v = targets.get(MountStat.of(i));
                need[i] = v == null ? s.max(i) : Math.min(v, s.max(i));
            }
        }
        return need;
    }

    public boolean reachedBy(MountStats s) {
        int[] need = need(s);
        for (int i = 0; i < need.length; i++) {
            if (s.limit(i) < need[i]) {
                return false;
            }
        }
        return fullLimits || s.limitSum() >= 8 * minAvgLimit;
    }
}
