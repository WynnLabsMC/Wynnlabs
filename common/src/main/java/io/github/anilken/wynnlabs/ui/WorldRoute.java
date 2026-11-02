package io.github.anilken.wynnlabs.ui;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.zip.GZIPInputStream;

/**
 * A coarse map of the walkable world, generated offline from the game's terrain and fetched from
 * our repository when first needed ({@link WorldRouteData}): the overworld in cells of
 * {@link #cell} blocks a side, each with the feet height on its surface and what it is (ground,
 * water, lava, or unknown where nothing was mapped). The guide line plans over it when the
 * compass target lies beyond the loaded area:
 * an A* over the cells ({@link #plan}) finds the way round mountains and sealed canyons that the
 * block-level route cannot see, the way becomes a corridor of waypoints ({@link #corridor}), and
 * the block-level route aims at the farthest waypoint inside the loaded area ({@link #handoff}).
 *
 * <p>Whether a player gets from a cell into each of its four neighbours was decided when the map
 * was made, block by block with the game's step rule (a block up, any drop, water swum, never lava),
 * so a narrow stair or a gate in a wall counts though the cells' heights differ; the map holds it as
 * four direction bits a cell ({@link #open}). Water costs {@link #WATER_COST} times ground, a climb
 * {@link #UP_COST} a block more and a deep drop {@link #DROP_COST} a block. Cells with an
 * invisible wall (Wynncraft's barrier blocks at walk height, recorded in the game and folded in
 * offline) are impassable, and the hand-made walls of {@link WorldGates} may not be crossed away
 * from a gate. Roads under the mountains are the wiki's corridors: from a gate (an access point)
 * to the Location's point, walked at the cost of flat ground through the rock the map shows over
 * them ({@link #gates}); a gate is never a free way through rock on its own. Pure: no Minecraft
 * types, so it is tested on hand-made grids.
 *
 * <p>File: gzip over "WLWR", a version byte (3), the cell size, the tile size (cells a side), then
 * big-endian ints: the height base, the origin tile x and z, the tiles wide and high; a bitmap of
 * the tiles present (row-major, z outer, bit i of byte i / 8); then each present tile in that
 * order: tile² height bytes (0 unknown, else y - base + 1), tile² / 4 kind bytes (two bits a cell,
 * cell i in bits 2 (i mod 4)), tile² / 2 edge bytes (four bits a cell, cell i in nibble i mod 2:
 * bit 0 east +x, 1 south +z, 2 west -x, 3 north -z), tile² / 8 wall bytes (one bit a cell, cell i
 * in bit i mod 8: impassable).
 */
final class WorldRoute {

    /** No height: the cell is unknown, lava, or off the map. */
    static final int UNKNOWN = Integer.MIN_VALUE;
    /** A cell's kind. */
    static final int NONE = 0;
    static final int GROUND = 1;
    static final int WATER = 2;
    static final int LAVA = 3;
    /** The direction bits of a cell's edges. */
    static final int EAST = 1;
    static final int SOUTH = 2;
    static final int WEST = 4;
    static final int NORTH = 8;
    /** A step onto water costs this much of one onto ground. */
    static final double WATER_COST = 3;
    /** A step up costs this much more a block, a drop deeper than a walk this much more a block. */
    static final double UP_COST = 0.5;
    static final double DROP_COST = 0.15;
    /** The clock is read once every this many cells looked at. */
    private static final int CLOCK_EVERY = 1024;
    /** Where the target's cell cannot be stood on, one up to this many cells away is aimed at. */
    private static final int NEAR = 2;
    private static final int[][] MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double DIAG = Math.sqrt(2);

    /** Blocks a cell is on a side, cells a tile is on a side. */
    final int cell;
    final int tile;
    private final int base;
    private final int originX;
    private final int originZ;
    private final int wide;
    private final int high;
    /** Per tile (row-major, z outer; null: not mapped): the height bytes, the packed kinds, the packed edge bits and the wall bits. */
    private final byte[][] heights;
    private final byte[][] kinds;
    private final byte[][] edges;
    private final byte[][] walls;
    /** How many cells are impassable. */
    private final int impassable;
    /** The hand-made walls, {@link WorldGates#NONE} without any. */
    private volatile WorldGates gates = WorldGates.NONE;
    /** The corridors: by the key of a cell, the cells {cx, cz, length in cells} a route may go on to from it. */
    private volatile java.util.Map<Long, List<int[]>> tunnels = java.util.Map.of();

    /** A way over the cells {cx, cz}, start first, whether it ends at the target's cell, the cells looked at and the time taken. */
    record Plan(List<int[]> cells, boolean reached, int expanded, long millis) {}

    private record Node(int x, int z, double g, double f, Node parent) {}

    WorldRoute(int cell, int tile, int base, int originX, int originZ, int wide, int high,
               byte[][] heights, byte[][] kinds, byte[][] edges, byte[][] walls) {
        this.cell = cell;
        this.tile = tile;
        this.base = base;
        this.originX = originX;
        this.originZ = originZ;
        this.wide = wide;
        this.high = high;
        this.heights = heights;
        this.kinds = kinds;
        this.edges = edges;
        this.walls = walls;
        int n = 0;
        for (byte[] w : walls) {
            if (w != null) {
                for (byte b : w) {
                    n += Integer.bitCount(b & 0xFF);
                }
            }
        }
        this.impassable = n;
    }

    /**
     * The region entrances, corridors and walls the plan keeps to. Each corridor joins the cell of
     * its access point to the cell of its location point, both ways, at the cost of walking its
     * length: the route goes in at the gate and out where the road under the mountain opens. An end
     * on a cell that cannot be stood on is moved to the nearest walkable one within {@link #NEAR};
     * with none it stays where it is, and joins only the corridors that meet there (one access
     * point to the location point, the location point on to another access point).
     */
    void gates(WorldGates g) {
        gates = g == null ? WorldGates.NONE : g;
        java.util.Map<Long, List<int[]>> t = new java.util.HashMap<>();
        for (WorldGates.Corridor c : gates.corridors()) {
            int[] a = end(c.fromX(), c.fromZ());
            int[] b = end(c.toX(), c.toZ());
            if (a[0] != b[0] || a[1] != b[1]) {
                join(t, a, b);
            }
        }
        tunnels = t;
    }

    /** A corridor's end in cells: its own cell when it can be stood on, else the nearest within {@link #NEAR}, else its own. */
    private int[] end(double x, double z) {
        int cx = cellOf(x);
        int cz = cellOf(z);
        int[] m = aim(cx, cz);
        return m != null ? m : new int[] {cx, cz};
    }

    /** How many corridors the plan may take. */
    int tunnels() {
        int n = 0;
        for (List<int[]> l : tunnels.values()) {
            n += l.size();
        }
        return n / 2;
    }

    private static void join(java.util.Map<Long, List<int[]>> t, int[] a, int[] b) {
        int len = (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]));
        t.computeIfAbsent(key(a[0], a[1]), k -> new ArrayList<>()).add(new int[] {b[0], b[1], len});
        t.computeIfAbsent(key(b[0], b[1]), k -> new ArrayList<>()).add(new int[] {a[0], a[1], len});
    }

    WorldGates gates() {
        return gates;
    }

    /** How many cells an invisible wall makes impassable. */
    int impassable() {
        return impassable;
    }

    /** Reads a map in the file format from {@code gz} (gzip). */
    static WorldRoute load(InputStream gz) throws IOException {
        DataInputStream in = new DataInputStream(new GZIPInputStream(gz, 1 << 16));
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'W' || magic[1] != 'L' || magic[2] != 'W' || magic[3] != 'R') {
            throw new IOException("not a world route map");
        }
        int version = in.readUnsignedByte();
        if (version != 3) {
            throw new IOException("world route map version " + version);
        }
        int cell = in.readUnsignedByte();
        int tile = in.readUnsignedByte();
        int base = in.readInt();
        int ox = in.readInt();
        int oz = in.readInt();
        int wide = in.readInt();
        int high = in.readInt();
        if (cell < 1 || tile < 4 || tile % 4 != 0 || wide < 1 || high < 1 || (long) wide * high > 1 << 24) {
            throw new IOException("world route map header out of range");
        }
        int n = wide * high;
        byte[] bitmap = new byte[(n + 7) / 8];
        in.readFully(bitmap);
        byte[][] heights = new byte[n][];
        byte[][] kinds = new byte[n][];
        byte[][] edges = new byte[n][];
        byte[][] walls = new byte[n][];
        for (int i = 0; i < n; i++) {
            if ((bitmap[i >> 3] >> (i & 7) & 1) != 0) {
                heights[i] = new byte[tile * tile];
                kinds[i] = new byte[tile * tile / 4];
                edges[i] = new byte[tile * tile / 2];
                walls[i] = new byte[tile * tile / 8];
                in.readFully(heights[i]);
                in.readFully(kinds[i]);
                in.readFully(edges[i]);
                in.readFully(walls[i]);
            }
        }
        return new WorldRoute(cell, tile, base, ox, oz, wide, high, heights, kinds, edges, walls);
    }

    /** The cell a block coordinate lies in. */
    int cellOf(double block) {
        return Math.floorDiv((int) Math.floor(block), cell);
    }

    /** The block coordinate of a cell's centre. */
    double centre(int c) {
        return c * (double) cell + cell / 2.0;
    }

    /** How many tiles are mapped. */
    int tiles() {
        int n = 0;
        for (byte[] h : heights) {
            if (h != null) {
                n++;
            }
        }
        return n;
    }

    /** The tile index of a cell, -1 off the map or not mapped. */
    private int tileOf(int cx, int cz) {
        int tx = Math.floorDiv(cx, tile) - originX;
        int tz = Math.floorDiv(cz, tile) - originZ;
        if (tx < 0 || tz < 0 || tx >= wide || tz >= high) {
            return -1;
        }
        int t = tz * wide + tx;
        return heights[t] == null ? -1 : t;
    }

    private int index(int cx, int cz) {
        return Math.floorMod(cz, tile) * tile + Math.floorMod(cx, tile);
    }

    /** The cell's kind: {@link #GROUND}, {@link #WATER}, {@link #LAVA}, or {@link #NONE} where unknown or off the map. */
    int kind(int cx, int cz) {
        int t = tileOf(cx, cz);
        if (t < 0) {
            return NONE;
        }
        int i = index(cx, cz);
        return kinds[t][i >> 2] >> (2 * (i & 3)) & 3;
    }

    /** The feet height on the cell's surface (the water's surface on water), {@link #UNKNOWN} without one. */
    int height(int cx, int cz) {
        int t = tileOf(cx, cz);
        if (t < 0) {
            return UNKNOWN;
        }
        int h = heights[t][index(cx, cz)] & 0xFF;
        return h == 0 ? UNKNOWN : h - 1 + base;
    }

    /** Whether an invisible wall stands in the cell. */
    boolean wall(int cx, int cz) {
        int t = tileOf(cx, cz);
        if (t < 0) {
            return false;
        }
        int i = index(cx, cz);
        return (walls[t][i >> 3] >> (i & 7) & 1) != 0;
    }

    /** Whether the cell can be stood or swum on: ground or water with a height, and no wall. */
    boolean walkable(int cx, int cz) {
        int k = kind(cx, cz);
        return (k == GROUND || k == WATER) && height(cx, cz) != UNKNOWN && !wall(cx, cz);
    }

    /** Whether a player gets from the cell into its neighbour in {@code direction} ({@link #EAST} ...). */
    boolean open(int cx, int cz, int direction) {
        int t = tileOf(cx, cz);
        if (t < 0) {
            return false;
        }
        int i = index(cx, cz);
        return (edges[t][i >> 1] >> (4 * (i & 1)) & direction) != 0;
    }

    /** Whether a player gets from cell (fx, fz) into the cell {@code dx}, {@code dz} (each -1, 0 or 1, not both 0) away. */
    boolean open(int fx, int fz, int dx, int dz) {
        if (dx != 0 && dz != 0) {
            // a diagonal step: round either corner, into a cell that can be stood on
            return walkable(fx + dx, fz + dz)
                    && (open(fx, fz, dx, 0) && open(fx + dx, fz, 0, dz) || open(fx, fz, 0, dz) && open(fx, fz + dz, dx, 0));
        }
        return open(fx, fz, dx > 0 ? EAST : dx < 0 ? WEST : dz > 0 ? SOUTH : NORTH);
    }

    /**
     * What a step from cell (fx, fz) onto its neighbour (tx, tz) costs for a flat distance of
     * {@code dist} cells; negative when the step cannot be taken (unknown, lava, a wall, a cliff).
     */
    double step(int fx, int fz, int tx, int tz, double dist) {
        int k = kind(tx, tz);
        if (k != GROUND && k != WATER || wall(tx, tz) || !open(fx, fz, tx - fx, tz - fz)
                || gates.blocked(centre(fx), centre(fz), centre(tx), centre(tz))) {
            return -1;
        }
        int from = height(fx, fz);
        int to = height(tx, tz);
        if (from == UNKNOWN || to == UNKNOWN) {
            return -1;
        }
        int dy = to - from;
        double c = dist * (k == WATER ? WATER_COST : 1);
        if (dy > 0) {
            c += UP_COST * dy;
        } else if (-dy > 3) {
            c += DROP_COST * -dy;
        }
        return c;
    }

    /**
     * The way from cell (sx, sz) to cell (tx, tz) (or, where that cannot be stood on, the nearest
     * cell within {@link #NEAR} that can), looking at most at {@code budget} cells and for
     * {@code maxMillis}; null when the start is not walkable or the target has no cell to aim
     * at. Short of the target it ends at the cell the estimate puts nearest, {@code reached} false.
     */
    Plan plan(int sx, int sz, int tx, int tz, int budget, long maxMillis) {
        long t0 = System.nanoTime();
        if (!walkable(sx, sz)) {
            return null;
        }
        int[] goal = aim(tx, tz);
        if (goal == null) {
            return null;
        }
        int gx = goal[0];
        int gz = goal[1];
        long deadline = t0 + maxMillis * 1_000_000;
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap(Math.min(budget, 1 << 16));
        best.defaultReturnValue(Double.POSITIVE_INFINITY);
        Node start = new Node(sx, sz, 0, octile(sx, sz, gx, gz), null);
        open.add(start);
        best.put(key(sx, sz), 0.0);
        Node nearest = start;
        double nearestH = octile(sx, sz, gx, gz);
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            if (expanded % CLOCK_EVERY == 0 && expanded > 0 && System.nanoTime() > deadline) {
                break;
            }
            Node n = open.poll();
            if (best.get(key(n.x(), n.z())) < n.g()) {
                continue;
            }
            double hn = octile(n.x(), n.z(), gx, gz);
            if (hn < nearestH) {
                nearest = n;
                nearestH = hn;
            }
            if (n.x() == gx && n.z() == gz) {
                return new Plan(trace(n), true, expanded, (System.nanoTime() - t0) / 1_000_000);
            }
            expanded++;
            for (int[] m : MOVES) {
                int nx = n.x() + m[0];
                int nz = n.z() + m[1];
                boolean diagonal = m[0] != 0 && m[1] != 0;
                double c = step(n.x(), n.z(), nx, nz, diagonal ? DIAG : 1);
                if (c < 0) {
                    continue;
                }
                double g = n.g() + c;
                long k = key(nx, nz);
                if (g < best.get(k)) {
                    best.put(k, g);
                    open.add(new Node(nx, nz, g, g + octile(nx, nz, gx, gz), n));
                }
            }
            // a corridor under the mountain from a gate: flat walking, no height checks inside
            List<int[]> through = tunnels.get(key(n.x(), n.z()));
            if (through != null) {
                for (int[] t : through) {
                    double g = n.g() + t[2];
                    long k = key(t[0], t[1]);
                    if (g < best.get(k)) {
                        best.put(k, g);
                        open.add(new Node(t[0], t[1], g, g + octile(t[0], t[1], gx, gz), n));
                    }
                }
            }
        }
        return new Plan(trace(nearest), false, expanded, (System.nanoTime() - t0) / 1_000_000);
    }

    /** The target's cell when it can be stood on, else the nearest walkable cell within {@link #NEAR}; null with none. */
    private int[] aim(int tx, int tz) {
        if (walkable(tx, tz)) {
            return new int[] {tx, tz};
        }
        for (int r = 1; r <= NEAR; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && walkable(tx + dx, tz + dz)) {
                        return new int[] {tx + dx, tz + dz};
                    }
                }
            }
        }
        return null;
    }

    /**
     * The plan's cells as points {x, y, z} on the ground to draw: each cell's centre at its surface
     * height plus {@code lift}; through a passage, where the surface is the mountain over it, the
     * height stays that of the mouth.
     */
    List<double[]> line(List<int[]> cells, double lift) {
        List<double[]> out = new ArrayList<>(cells.size());
        int last = 0;
        for (int[] c : cells) {
            int h = height(c[0], c[1]);
            if (h != UNKNOWN && walkable(c[0], c[1])) {
                last = h;
            }
            out.add(new double[] {centre(c[0]), last + lift, centre(c[1])});
        }
        return out;
    }

    /**
     * Waypoints along the plan about {@code spacing} blocks apart, each {x, feetY, z} at a cell's
     * centre, the start left out and the last cell always in; empty for a plan of one cell.
     */
    List<double[]> corridor(List<int[]> cells, double spacing) {
        List<double[]> out = new ArrayList<>();
        double since = 0;
        for (int i = 1; i < cells.size(); i++) {
            int[] a = cells.get(i - 1);
            int[] b = cells.get(i);
            since += Math.hypot(b[0] - a[0], b[1] - a[1]) * cell;
            if (since >= spacing || i == cells.size() - 1) {
                out.add(new double[] {centre(b[0]), height(b[0], b[1]), centre(b[1])});
                since = 0;
            }
        }
        return out;
    }

    /** Whether the ground at a block column can be routed over now (its chunk is loaded). */
    @FunctionalInterface
    interface Loaded {
        boolean at(int x, int z);
    }

    /**
     * The index of the farthest waypoint along the corridor that lies within {@code box} blocks
     * of (px, pz) each way and on {@code loaded} ground: where the block-level route aims so the
     * line follows the corridor; -1 when none does.
     */
    static int handoff(List<double[]> waypoints, double px, double pz, int box, Loaded loaded) {
        int best = -1;
        for (int i = 0; i < waypoints.size(); i++) {
            double[] w = waypoints.get(i);
            if (Math.abs(w[0] - px) <= box && Math.abs(w[2] - pz) <= box
                    && loaded.at((int) Math.floor(w[0]), (int) Math.floor(w[2]))) {
                best = i;
            }
        }
        return best;
    }

    /** The index of the plan cell nearest to block (x, z), seen flat. */
    int nearestCell(List<int[]> cells, double x, double z) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            double d = sq(centre(c[0]) - x) + sq(centre(c[1]) - z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static List<int[]> trace(Node n) {
        List<int[]> out = new ArrayList<>();
        for (Node c = n; c != null; c = c.parent()) {
            out.add(new int[] {c.x(), c.z()});
        }
        Collections.reverse(out);
        return out;
    }

    private static double octile(int x, int z, int tx, int tz) {
        int dx = Math.abs(tx - x);
        int dz = Math.abs(tz - z);
        return Math.max(dx, dz) + (DIAG - 1) * Math.min(dx, dz);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static double sq(double v) {
        return v * v;
    }
}
