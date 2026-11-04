package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.quests.Nearest;

/**
 * The world route map ({@link WorldRoute}) as the nearest list uses it: walking lengths over block
 * coordinates from one flood ({@link WorldRoute#reach}), the map fetched the way the guide line
 * fetches it ({@link WorldRouteData#map}), and its state. The guide line itself is untouched.
 */
public final class RouteReach {

    /** One flood looks at most at this many cells, ... */
    public static final int BUDGET = 1_000_000;
    /** ... for at most this long. */
    public static final long MAX_MILLIS = 1500;

    private RouteReach() {}

    /**
     * The map as a router, fetching it first when it is not on disk yet (the same throttled,
     * hash-checked download the guide line starts); null while there is none. On the caller's
     * thread, so only ever a worker's.
     */
    public static Nearest.Router fetch() {
        WorldRoute m = WorldRouteData.map();
        return m == null ? null : over(m);
    }

    /** The map's state: MISSING, DOWNLOADING, READY or FAILED. */
    public static String state() {
        return WorldRouteData.status().state().name();
    }

    /** The map's hash prefix in memory (null: none yet): a new map makes the list stale. */
    public static String hash() {
        return WorldRouteData.status().hash();
    }

    /** A router over {@code map}: block coordinates to cells, one flood of {@link #BUDGET} cells / {@link #MAX_MILLIS}. */
    static Nearest.Router over(WorldRoute map) {
        return (px, pz, at, want) -> {
            int[][] cells = new int[at.length][];
            for (int i = 0; i < at.length; i++) {
                cells[i] = new int[] {map.cellOf(at[i][0]), map.cellOf(at[i][1])};
            }
            WorldRoute.Reaches r = map.reach(map.cellOf(px), map.cellOf(pz), cells, want, BUDGET, MAX_MILLIS);
            double[] out = new double[at.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = r.each()[i] == null ? -1 : r.each()[i].length();
            }
            return out;
        };
    }
}
