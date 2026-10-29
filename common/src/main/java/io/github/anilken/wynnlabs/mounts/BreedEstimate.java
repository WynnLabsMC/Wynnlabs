package io.github.anilken.wynnlabs.mounts;

import java.util.List;

/**
 * Estimated child of two parents. Every number is a range (see {@link BreedConstants}).
 *
 * @param eligible   both parents can breed now (average limit at least the eligibility)
 * @param stats      per stat in {@link MountStat} order: level, limit and max ranges
 * @param potential  sum of the max ranges
 * @param closeness  0..1, 1 when the parents' limits are equal (parents should be high AND close)
 * @param durationSec breeding time
 */
public record BreedEstimate(boolean eligible, List<StatRange> stats, Range potential, Range avgLimit, double closeness,
                            long durationSec) {
    public record StatRange(MountStat stat, Range level, Range limit, Range max) {
    }

    public BreedEstimate {
        stats = List.copyOf(stats);
    }

    /** The middle of every range, as stats the feed planner can start from. */
    public MountStats expected() {
        int[] lv = new int[8], li = new int[8], mx = new int[8];
        for (int i = 0; i < 8; i++) {
            StatRange s = stats.get(i);
            mx[i] = s.max().mid();
            li[i] = Math.min(s.limit().mid(), mx[i]);
            lv[i] = Math.min(s.level().mid(), li[i]);
        }
        return MountStats.of(lv, li, mx);
    }
}
