package io.github.anilken.wynnlabs.coach;

/**
 * How often the survival warning may speak. In a raid or a boss fight health drops fast all the
 * time with a healer around, so there the warning keeps quiet (no sound) and shows at most every
 * 15 s; elsewhere it beeps and may repeat every 4 s.
 */
final class SurvivalPace {

    private SurvivalPace() {}

    /** Whether a warning may show now, {@code warnedAt} being the last one (epoch ms); quiet in a raid or boss fight. */
    static boolean due(long now, long warnedAt, boolean quiet) {
        return now - warnedAt >= (quiet ? 15_000 : 4_000);
    }

    /** Whether the warning plays its sound (not in a raid or boss fight). */
    static boolean sound(boolean quiet) {
        return !quiet;
    }
}
