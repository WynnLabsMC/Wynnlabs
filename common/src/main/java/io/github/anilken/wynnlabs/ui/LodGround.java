package io.github.anilken.wynnlabs.ui;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * The guide route's ground past the loaded chunks, from Distant Horizons' LOD surface: inside the
 * loaded area the exact block rules ({@code exact}) still hold; outside it a column's ground is
 * its LOD surface, with two free blocks above taken for granted. A step there goes at most one
 * block up and down to {@link GuidePath#DROP}, water is walked on (swum) at {@link #WATER} the
 * cost, and lava is never stepped on. Coarse: no ladders, doors or roads out there. Pure: no
 * Minecraft types.
 */
final class LodGround implements GuidePath.Walkable, GuideLine.Ground, FarPath.Coarse {

    /** A step onto water out in the LOD area costs this much of a plain one. */
    static final double WATER = 3;
    /** No LOD data at a column. */
    static final int NO_DATA = Integer.MIN_VALUE;
    /** A column's surface kinds. */
    static final int GROUND = 0;
    static final int WET = 1;
    static final int LAVA = 2;

    /** Whether column (x, z) is in a loaded chunk, where the exact rules hold. */
    @FunctionalInterface
    interface Loaded {
        boolean loaded(int x, int z);
    }

    /** A column's LOD surface, {@link #pack packed}, or {@link #NO_DATA}. */
    @FunctionalInterface
    interface Columns {
        int surface(int x, int z);
    }

    private final GuidePath.Walkable exact;
    private final GuideLine.Ground heights;
    private final Loaded loaded;
    private final Columns lod;

    LodGround(GuidePath.Walkable exact, GuideLine.Ground heights, Loaded loaded, Columns lod) {
        this.exact = exact;
        this.heights = heights;
        this.loaded = loaded;
        this.lod = lod;
    }

    /** A surface: feet height on it, its kind and the LOD detail level (0: a block, at most 15). */
    static int pack(int feet, int kind, int detail) {
        return feet << 6 | (Math.min(15, Math.max(0, detail)) << 2) | kind;
    }

    static int feet(int packed) {
        return packed >> 6;
    }

    static int kind(int packed) {
        return packed & 3;
    }

    static int detail(int packed) {
        return packed >> 2 & 15;
    }

    @Override
    public int stand(int x, int z, int fromY) {
        if (loaded.loaded(x, z)) {
            return exact.stand(x, z, fromY);
        }
        int s = lod.surface(x, z);
        if (s == NO_DATA || kind(s) == LAVA) {
            return GuidePath.NONE;
        }
        int y = feet(s);
        return y - fromY > 1 || fromY - y > GuidePath.DROP ? GuidePath.NONE : y;
    }

    @Override
    public int climb(int x, int z, int y, int dy) {
        return loaded.loaded(x, z) ? exact.climb(x, z, y, dy) : GuidePath.NONE;
    }

    @Override
    public double cost(int x, int y, int z) {
        if (loaded.loaded(x, z)) {
            return exact.cost(x, y, z);
        }
        int s = lod.surface(x, z);
        return s != NO_DATA && kind(s) == WET ? WATER : 1;
    }

    /** Out in the LOD area the route steps two blocks at a time. */
    @Override
    public boolean coarse(int x, int z) {
        return !loaded.loaded(x, z);
    }

    /** The ground height of a column for the straight guess: the loaded one's, else the LOD surface. */
    @Override
    public int height(int x, int z) {
        if (loaded.loaded(x, z)) {
            return heights.height(x, z);
        }
        int s = lod.surface(x, z);
        return s == NO_DATA ? GuideLine.UNKNOWN : feet(s);
    }

    /** The LOD surface's feet height at a column, {@link GuidePath#NONE} with no data or lava. */
    int surface(int x, int z) {
        int s = lod.surface(x, z);
        return s == NO_DATA || kind(s) == LAVA ? GuidePath.NONE : feet(s);
    }

    /**
     * LOD columns kept for {@code ttlMillis} (all of them dropped together then): the route
     * looks at the same columns every round. Counts the columns read from {@code source} and the
     * finest detail level seen since {@link #round}. One thread only.
     */
    static final class Cache implements Columns {
        private final Columns source;
        private final long ttl;
        private final Long2IntOpenHashMap map = new Long2IntOpenHashMap();
        private long since = System.nanoTime();
        private int reads;
        private int finest = -1;

        Cache(Columns source, long ttlMillis) {
            this.source = source;
            this.ttl = ttlMillis * 1_000_000;
        }

        @Override
        public int surface(int x, int z) {
            long k = (long) x << 32 | (z & 0xFFFFFFFFL);
            int s;
            if (map.containsKey(k)) {
                s = map.get(k);
            } else {
                s = source.surface(x, z);
                reads++;
                map.put(k, s);
            }
            if (s != NO_DATA && (finest < 0 || detail(s) < finest)) {
                finest = detail(s);
            }
            return s;
        }

        /** A new round of reads: the counts start over, and the columns too once they are old. */
        void round() {
            reads = 0;
            finest = -1;
            if (System.nanoTime() - since > ttl) {
                map.clear();
                since = System.nanoTime();
            }
        }

        /** Columns read from the source this round (not from the cache). */
        int reads() {
            return reads;
        }

        /** The finest detail level read this round, -1 with none. */
        int finest() {
            return finest;
        }
    }
}
