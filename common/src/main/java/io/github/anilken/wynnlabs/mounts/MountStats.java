package io.github.anilken.wynnlabs.mounts;

import java.util.List;

/**
 * The eight stats of a mount, indexed by {@link MountStat#ordinal()}. Potential is the sum of the eight maxes
 * (a fresh mount: 8 x 30 = 240).
 */
public record MountStats(List<StatLine> stats) {
    public static final int BASE_LEVEL = 1;
    public static final int BASE_LIMIT = 10;
    public static final int BASE_MAX = 30;

    public MountStats {
        if (stats.size() != MountStat.COUNT) {
            throw new IllegalArgumentException("a mount has 8 stats, got " + stats.size());
        }
        stats = List.copyOf(stats);
    }

    /** A freshly bought mount: level 1, limit 10, max 30 everywhere. */
    public static MountStats fresh() {
        return uniform(BASE_LEVEL, BASE_LIMIT, BASE_MAX);
    }

    public static MountStats uniform(int level, int limit, int max) {
        StatLine l = new StatLine(level, limit, max);
        return new MountStats(List.of(l, l, l, l, l, l, l, l));
    }

    /** Builds from three arrays in {@link MountStat} order. */
    public static MountStats of(int[] level, int[] limit, int[] max) {
        StatLine[] out = new StatLine[MountStat.COUNT];
        for (int i = 0; i < out.length; i++) {
            out[i] = new StatLine(level[i], limit[i], max[i]);
        }
        return new MountStats(List.of(out));
    }

    public StatLine get(MountStat s) {
        return stats.get(s.ordinal());
    }

    public int level(int i) {
        return stats.get(i).level();
    }

    public int limit(int i) {
        return stats.get(i).limit();
    }

    public int max(int i) {
        return stats.get(i).max();
    }

    public int limitSum() {
        int s = 0;
        for (StatLine l : stats) {
            s += l.limit();
        }
        return s;
    }

    public int maxSum() {
        int s = 0;
        for (StatLine l : stats) {
            s += l.max();
        }
        return s;
    }

    /** Potential = sum of the eight maxes. */
    public int potential() {
        return maxSum();
    }

    /** Average limit (exact, not rounded). */
    public double averageLimit() {
        return limitSum() / 8.0;
    }

    /** Highest current level: the feed gate (a material of level L needs gate >= L). */
    public int gate() {
        int g = 0;
        for (StatLine l : stats) {
            g = Math.max(g, l.level());
        }
        return g;
    }

    /** Every limit at its max. */
    public boolean fullLimits() {
        for (StatLine l : stats) {
            if (l.limit() < l.max()) {
                return false;
            }
        }
        return true;
    }

    /** Old enough to breed: average limit at least {@link Breeding#constants()} eligibility (20). */
    public boolean breedReady() {
        return limitSum() >= 8 * Breeding.constants().eligibleAvgLimit();
    }

    /** Seconds of the feed timer a feed would start now (by the rounded-up average limit). */
    public int feedTimerSec() {
        return MountRules.timerSec(limitSum());
    }

    /** Limit gain per stat ({@link MountStat} order) from feeding this material now, after the max cap. */
    public int[] gainsFrom(Material m) {
        MountStats after = fed(m);
        int[] g = new int[8];
        for (int i = 0; i < 8; i++) {
            g[i] = after.limit(i) - limit(i);
        }
        return g;
    }

    /** These stats after feeding one material (limits capped at max; levels unchanged). */
    public MountStats fed(Material m) {
        int[] lv = new int[8], li = new int[8], mx = new int[8];
        for (int i = 0; i < 8; i++) {
            lv[i] = level(i);
            li[i] = limit(i);
            mx[i] = max(i);
        }
        int[] p = m.kind().points(m.level());
        for (int j = 0; j < p.length; j++) {
            int s = m.kind().stats().get(j).ordinal();
            li[s] = Math.min(mx[s], li[s] + p[j]);
        }
        return of(lv, li, mx);
    }
}
