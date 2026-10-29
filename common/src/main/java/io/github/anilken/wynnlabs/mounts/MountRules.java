package io.github.anilken.wynnlabs.mounts;

/** Game facts for feeding (verified by replaying 108 reference plans step for step). */
public final class MountRules {
    /** Material levels that exist, ascending. */
    public static final int[] LEVELS = {1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 105, 110, 115};

    private MountRules() {
    }

    /**
     * Feed timer in seconds, from the sum of the eight limits BEFORE the feed: by the average rounded up,
     * 11 or less 1m, 12 5m, 13 15m, 14 30m, 15 1h, 16 2h, 17 3h, 18 4h, 19 5h, 20 or more 6h.
     */
    public static int timerSec(int limitSum) {
        int a = (limitSum + 7) / 8;
        if (a <= 11) {
            return 60;
        }
        return switch (a) {
            case 12 -> 300;
            case 13 -> 900;
            case 14 -> 1800;
            case 15 -> 3600;
            case 16 -> 7200;
            case 17 -> 10800;
            case 18 -> 14400;
            case 19 -> 18000;
            default -> 21600;
        };
    }

    /** Points as (base100 + perLevel100 * L) / 100 rounded half up. */
    static int roundPoints(int base100, int perLevel100, int level) {
        return (base100 + perLevel100 * level + 50) / 100;
    }
}
