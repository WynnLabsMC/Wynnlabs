package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.List;

/**
 * Breeding estimate. Only the parents' LIMITS shape the child: per stat, child limit = the parents' average limit
 * + bonus +- spread and child max = average limit + max bonus +- spread; child levels = the higher parent's level
 * +- spread. Every number comes from {@link BreedConstants} (estimates, not verified) and every result is a range.
 */
public final class Breeding {
    private Breeding() {
    }

    public static BreedConstants constants() {
        return BreedConstants.ESTIMATE;
    }

    public static BreedEstimate estimate(MountStats a, MountStats b) {
        return estimate(a, b, constants());
    }

    public static BreedEstimate estimate(MountStats a, MountStats b, BreedConstants k) {
        List<BreedEstimate.StatRange> out = new ArrayList<>();
        int potLo = 0, potHi = 0, limLo = 0, limHi = 0;
        for (MountStat s : MountStat.values()) {
            int i = s.ordinal();
            int sum = a.limit(i) + b.limit(i);
            int avgLo = sum / 2, avgHi = (sum + 1) / 2;
            Range max = new Range(avgLo + k.maxBonus() - k.maxSpread(), avgHi + k.maxBonus() + k.maxSpread())
                    .clamp(1, k.statCap());
            Range limit = new Range(avgLo + k.limitBonus() - k.limitSpread(), avgHi + k.limitBonus() + k.limitSpread())
                    .clamp(1, k.statCap());
            limit = new Range(Math.min(limit.low(), max.low()), Math.min(limit.high(), max.high()));
            int top = Math.max(a.level(i), b.level(i));
            Range level = new Range(top - k.levelSpread(), top + k.levelSpread()).clamp(1, limit.high());
            level = new Range(level.low(), Math.min(level.high(), limit.high()));
            out.add(new BreedEstimate.StatRange(s, level, limit, max));
            potLo += max.low();
            potHi += max.high();
            limLo += limit.low();
            limHi += limit.high();
        }
        int sa = a.limitSum(), sb = b.limitSum();
        double closeness = Math.max(sa, sb) == 0 ? 1 : 1 - Math.abs(sa - sb) / (double) Math.max(sa, sb);
        boolean eligible = sa >= 8 * k.eligibleAvgLimit() && sb >= 8 * k.eligibleAvgLimit();
        return new BreedEstimate(eligible, out, new Range(potLo, potHi), new Range(limLo / 8, (limHi + 7) / 8),
                closeness, k.breedSec());
    }
}
