package io.github.anilken.wynnlabs.ui;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The guide line's walking route: an A* over block columns. A step goes to one of the eight
 * neighbouring columns where there is ground to stand on at most one block up or three down
 * ({@link Walkable}); a diagonal step needs both columns beside it walkable, so it never cuts a
 * corner, and ladders and vines are climbed one block at a time. Roads and stairs cost less, so
 * the route keeps to them. The goal is the target's column and floor. The search is bounded (a
 * box around the start, a node budget); when it cannot reach the target it returns the route to
 * the reachable spot nearest the target. Pure: no Minecraft types.
 */
final class GuidePath {

    /** No ground to stand on from here. */
    static final int NONE = Integer.MIN_VALUE;
    /** The target counts as reached within this flat distance, in blocks, ... */
    static final double REACH = 1.5;
    /** ... and this close in feet height, so a chest up a tower is not reached from the floor under it. */
    static final int REACH_Y = 2;
    /** A block of height difference weighs this much in the distance estimate, ... */
    static final double H_Y = 0.6;
    /** ... and a whole block, counted in full, when the target is more than this far above or below. */
    static final int STEEP = 6;
    /** Deepest step down (no fall damage in Wynncraft): off a roof, a balcony or a ledge to the ground. */
    static final int DROP = 40;
    /** The clock is read once every this many spots looked at. */
    private static final int CLOCK_EVERY = 512;
    /** A step down deeper than a walk (3) costs this much more a block, so stairs still win. */
    static final double DROP_COST = 0.15;
    /** A step onto a road or stair costs this much of a plain one, so routes keep to roads. */
    static final double ROAD = 0.7;
    /** A step a block up (a jump) costs this much more. */
    static final double RISE = 0.5;
    /** When the search runs out of spots, this many of the spots it reached nearest the target are reported. */
    static final int STUCK = 8;
    /** An exit's walking cost from the player weighs this much against its flat distance to the target. */
    static final double EXIT_WALK = 0.5;
    /** Exit kinds: on the edge of the search box, or next to a column whose chunk is not loaded. */
    static final String EXIT_BOX = "box";
    static final String EXIT_UNLOADED = "unloaded";
    /**
     * The distance estimate is scaled by this: below 1 so a road a little aside is still tried
     * before the plain straight way wins, above {@link #ROAD} so open ground is not searched wide.
     */
    private static final double H_WEIGHT = 0.8;

    /** The ground of a column, seen from a step at feet height {@code fromY}. */
    @FunctionalInterface
    interface Walkable {
        /**
         * The feet height to stand at in column ({@code x}, {@code z}) when stepping there from
         * feet height {@code fromY}: ground below (or water to swim in), two free blocks, no lava, at most
         * one block up and three down, or deeper (down to {@link #DROP}) where the column below is
         * open all the way; {@link #NONE} when there is none (or the column is unknown).
         */
        int stand(int x, int z, int fromY);

        /**
         * The feet height after climbing one block up ({@code dy} 1) or down (-1) from {@code y} in
         * the same column (a ladder, vines, scaffolding); {@link #NONE} when there is nothing to climb.
         */
        default int climb(int x, int z, int y, int dy) {
            return NONE;
        }

        /** What a step onto feet ({@code x}, {@code y}, {@code z}) costs against a plain one: {@link #ROAD} on roads and stairs. */
        default double cost(int x, int y, int z) {
            return 1;
        }

        /** What a step a block up onto feet ({@code x}, {@code y}, {@code z}) costs on top of the step: {@link #RISE}. */
        default double rise(int x, int y, int z) {
            return RISE;
        }

        /**
         * Why there is nowhere to stand in column ({@code x}, {@code z}) stepping from feet height
         * {@code fromY} ({@link #stand} gave {@link #NONE}), in a few words: no ground, head
         * blocked, drop too far, lava, ...
         */
        default String why(int x, int z, int fromY) {
            return "no ground";
        }

        /** Whether column ({@code x}, {@code z}) is not loaded, seen from feet height {@code fromY}: the ground may go on there. */
        default boolean unknown(int x, int z, int fromY) {
            return false;
        }
    }

    /**
     * A route's spots {x, feetY, z}, start first, whether it ends at the target, and how many
     * spots the search looked at. {@code enclosed}: the search ran out of spots to go to before
     * its budget (the walkable ground around the start is closed in); then {@code columns} is how
     * many columns it reached and {@code stuck} the reached spots nearest the target, with why
     * each one's neighbours could not be stepped to. {@code exit} (null: none, the ground is
     * sealed) is where the walkable ground goes on past what the search could see, which the
     * route then leads to, and {@code exitKind} why it could not see further ({@link #EXIT_BOX},
     * {@link #EXIT_UNLOADED}).
     */
    record Route(List<int[]> nodes, boolean reached, int expanded, boolean enclosed, int columns, List<Stuck> stuck,
                 int[] exit, String exitKind) {
        Route(List<int[]> nodes, boolean reached, int expanded) {
            this(nodes, reached, expanded, false, 0, List.of(), null, null);
        }
    }

    /** A spot reached near the target, and its neighbour columns that could not be stepped to: direction (N, NE, ...) to why. */
    record Stuck(int x, int y, int z, Map<String, String> rejected) {}

    /** One candidate's search in {@link #findFirst}: its index, its route, and whether that came from an earlier search. */
    record Try(int index, Route route, boolean cached) {}

    /** The candidates searched in order, and the one led to: the first reached, else the nearest (0). */
    record Choice(int chosen, List<Try> tried) {
        Route route() {
            return tried.get(chosen).route();
        }
    }

    /** A candidate's failed route that still holds (the player has not moved far), or null to search again. */
    @FunctionalInterface
    interface Known {
        Route failed(int index);
    }

    private record Node(int x, int y, int z, double g, double f, Node parent) {}

    /** What one search aims at: the target's feet and how height weighs in the estimate. */
    private record Goal(int tx, int ty, int tz, boolean steep) {
        double h(int x, int y, int z) {
            return GuidePath.h(x, y, z, tx, ty, tz, steep);
        }
    }

    private static final int[][] MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /** The moves' compass names (+x east, +z south). */
    private static final String[] DIRS = {"E", "W", "S", "N", "SE", "NE", "SW", "NW"};
    private static final double DIAG = Math.sqrt(2);

    private GuidePath() {}

    /**
     * Feet height to aim for at a target whose block is at height {@code y} in column (x, z): a
     * standable spot within 2 of it, else within 4 (a chest in a wall niche, a rough pin height),
     * the nearest first; {@code y} itself when there is none.
     */
    static int feet(Walkable w, int x, int z, int y) {
        int best = NONE;
        // stand(fromY) looks from fromY + 1 down to fromY - 3
        for (int from : new int[] {y + 1, y + 3, y - 1}) {
            int f = w.stand(x, z, from);
            if (f != NONE && Math.abs(f - y) <= 4 && (best == NONE || Math.abs(f - y) < Math.abs(best - y))) {
                best = f;
            }
        }
        return best == NONE ? y : best;
    }

    /**
     * The ground under a rider at feet height {@code y} in column (x, z): the highest standable spot
     * there at or below {@code y}, looked for from the column's height {@code top} (the heightmap's
     * first free block, {@link GuideLine#UNKNOWN} where not loaded) or from {@code y} when that is
     * lower (gliding under an arch), down to {@code floor}, however deep; null with none. Where the
     * ground route starts while riding or gliding.
     */
    static int[] surface(Walkable w, int x, int z, int y, int top, int floor) {
        if (top == GuideLine.UNKNOWN) {
            return null;
        }
        // stand(fromY) looks from fromY + 1 down to fromY - 3, then down an open column
        for (int from = Math.min(top, y) - 1; from + 1 >= floor; from -= 4) {
            int f = w.stand(x, z, from);
            if (f != NONE) {
                return new int[] {x, f, z};
            }
        }
        return null;
    }

    /**
     * The route from feet ({@code sx}, {@code sy}, {@code sz}) to feet ({@code tx}, {@code ty},
     * {@code tz}), expanding at most {@code budget} spots inside {@code box} blocks of the start;
     * null when the start itself has no ground. Short of the target it ends at the spot the
     * estimate puts nearest, height included, so it climbs toward the right floor.
     */
    static Route find(Walkable w, int sx, int sy, int sz, int tx, int ty, int tz, int box, int budget) {
        return find(w, sx, sy, sz, tx, ty, tz, box, budget, Long.MAX_VALUE);
    }

    /** {@link #find}, also stopping after {@code maxMillis} of wall-clock time. */
    static Route find(Walkable w, int sx, int sy, int sz, int tx, int ty, int tz, int box, int budget, long maxMillis) {
        int y0 = w.stand(sx, sz, sy);
        if (y0 == NONE) {
            return null;
        }
        long deadline = maxMillis == Long.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + maxMillis * 1_000_000;
        Goal goal = new Goal(tx, ty, tz, Math.abs(y0 - ty) > STEEP);
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap(Math.min(budget, 1 << 16));
        best.defaultReturnValue(Double.POSITIVE_INFINITY);
        Node start = new Node(sx, y0, sz, 0, H_WEIGHT * goal.h(sx, y0, sz), null);
        open.add(start);
        best.put(key(sx, y0, sz), 0.0);
        Node nearest = start;
        double nearestH = goal.h(sx, y0, sz);
        int expanded = 0;
        LongOpenHashSet columns = new LongOpenHashSet();
        // the reached spots nearest the target, farthest on top, for a report when closed in
        PriorityQueue<Node> close = new PriorityQueue<>(Comparator.comparingDouble((Node a) -> -dist(a, tx, ty, tz)));
        boolean outOfTime = false;
        // where the ground goes on past what can be seen: the best one, should the search close in
        Node exit = null;
        String exitKind = null;
        double exitScore = Double.POSITIVE_INFINITY;
        while (!open.isEmpty() && expanded < budget) {
            if (expanded % CLOCK_EVERY == 0 && expanded > 0 && System.nanoTime() > deadline) {
                outOfTime = true;
                break;
            }
            Node n = open.poll();
            if (best.get(key(n.x(), n.y(), n.z())) < n.g()) {
                continue;                               // a shorter way here was already taken
            }
            double hn = goal.h(n.x(), n.y(), n.z());
            if (hn < nearestH) {
                nearest = n;
                nearestH = hn;
            }
            if (flat(n.x(), n.z(), tx, tz) <= REACH && Math.abs(n.y() - ty) <= REACH_Y) {
                return new Route(trace(n), true, expanded);
            }
            expanded++;
            columns.add(key(n.x(), 0, n.z()));
            close.add(n);
            if (close.size() > STUCK) {
                close.poll();
            }
            for (int[] m : MOVES) {
                int nx = n.x() + m[0];
                int nz = n.z() + m[1];
                if (Math.abs(nx - sx) > box || Math.abs(nz - sz) > box) {
                    double score = flat(n.x(), n.z(), tx, tz) + EXIT_WALK * n.g();
                    if (score < exitScore) {
                        exit = n;
                        exitKind = EXIT_BOX;
                        exitScore = score;
                    }
                    continue;
                }
                boolean diagonal = m[0] != 0 && m[1] != 0;
                if (diagonal && (w.stand(n.x() + m[0], n.z(), n.y()) == NONE
                        || w.stand(n.x(), n.z() + m[1], n.y()) == NONE)) {
                    continue;
                }
                int ny = w.stand(nx, nz, n.y());
                if (ny == NONE) {
                    if (w.unknown(nx, nz, n.y())) {
                        double score = flat(n.x(), n.z(), tx, tz) + EXIT_WALK * n.g();
                        if (score < exitScore) {
                            exit = n;
                            exitKind = EXIT_UNLOADED;
                            exitScore = score;
                        }
                    }
                    continue;
                }
                int drop = n.y() - ny;
                double g = n.g() + (diagonal ? DIAG : 1) * w.cost(nx, ny, nz) + (ny > n.y() ? w.rise(nx, ny, nz) : 0)
                        + (drop > 3 ? DROP_COST * drop : 0);
                push(open, best, n, nx, ny, nz, g, goal);
            }
            for (int dy = -1; dy <= 1; dy += 2) {
                int ny = w.climb(n.x(), n.z(), n.y(), dy);
                if (ny != NONE) {
                    push(open, best, n, n.x(), ny, n.z(), n.g() + (dy > 0 ? 1.5 : 1) * w.cost(n.x(), ny, n.z()), goal);
                }
            }
        }
        if (open.isEmpty() && !outOfTime) {
            List<Node> near = new ArrayList<>(close);
            near.sort(Comparator.comparingDouble(a -> dist(a, tx, ty, tz)));
            List<Stuck> stuck = new ArrayList<>();
            for (Node s : near) {
                stuck.add(new Stuck(s.x(), s.y(), s.z(), rejected(w, s, sx, sz, box)));
            }
            // closed in: lead to where the ground goes on out of sight, else to the spot nearest the target
            Node end = exit != null ? exit : nearest;
            int[] at = exit == null ? null : new int[] {exit.x(), exit.y(), exit.z()};
            return new Route(trace(end), false, expanded, true, columns.size(), List.copyOf(stuck), at, exitKind);
        }
        return new Route(trace(nearest), false, expanded);
    }

    /** The neighbour columns of a spot that cannot be stepped to, and why. */
    private static Map<String, String> rejected(Walkable w, Node n, int sx, int sz, int box) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < MOVES.length; i++) {
            int[] m = MOVES[i];
            int nx = n.x() + m[0];
            int nz = n.z() + m[1];
            if (Math.abs(nx - sx) > box || Math.abs(nz - sz) > box) {
                out.put(DIRS[i], "outside the search box");
            } else if (w.stand(nx, nz, n.y()) == NONE) {
                out.put(DIRS[i], w.why(nx, nz, n.y()));
            } else if (m[0] != 0 && m[1] != 0 && (w.stand(n.x() + m[0], n.z(), n.y()) == NONE
                    || w.stand(n.x(), n.z() + m[1], n.y()) == NONE)) {
                out.put(DIRS[i], "corner");
            }
        }
        return out;
    }

    private static double dist(Node n, int tx, int ty, int tz) {
        return Math.sqrt((double) (n.x() - tx) * (n.x() - tx) + (double) (n.y() - ty) * (n.y() - ty)
                + (double) (n.z() - tz) * (n.z() - tz));
    }

    /**
     * Several targets {x, feetY, z}, nearest first: each is searched in turn, at most {@code each}
     * spots, all of them together at most {@code total} spots and {@code maxMillis}; the first one
     * reached is led to. A target behind rock (a chest under a city whose way in lies outside the
     * loaded area) does not hold the line when another one can be walked to. With none reached the
     * nearest's partial route is kept. Null when the start has no ground or nothing was searched.
     */
    static Choice findFirst(Walkable w, int sx, int sy, int sz, List<int[]> goals, int box,
                            int each, int total, long maxMillis, Known known) {
        long deadline = System.nanoTime() + maxMillis * 1_000_000;
        int left = total;
        List<Try> tried = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) {
            Route r = known == null ? null : known.failed(i);
            boolean cached = r != null;
            if (!cached) {
                long ms = (deadline - System.nanoTime()) / 1_000_000;
                if (left <= 0 || ms <= 0) {
                    break;
                }
                int[] g = goals.get(i);
                r = find(w, sx, sy, sz, g[0], g[1], g[2], box, Math.min(each, left), ms);
                if (r == null) {
                    return null;
                }
                left -= r.expanded();
            }
            tried.add(new Try(i, r, cached));
            if (r.reached()) {
                return new Choice(i, tried);
            }
        }
        return tried.isEmpty() ? null : new Choice(0, tried);
    }

    private static void push(PriorityQueue<Node> open, Long2DoubleOpenHashMap best, Node from,
                             int x, int y, int z, double g, Goal goal) {
        long k = key(x, y, z);
        if (g < best.get(k)) {
            best.put(k, g);
            open.add(new Node(x, y, z, g, g + H_WEIGHT * goal.h(x, y, z), from));
        }
    }

    /**
     * Drops the spots between two that a straight walk joins, so the line does not zigzag on every
     * block; looks at most {@code lookahead} spots ahead.
     */
    static List<int[]> smooth(Walkable w, List<int[]> nodes, int lookahead) {
        if (nodes.size() <= 2) {
            return nodes;
        }
        List<int[]> out = new ArrayList<>();
        int i = 0;
        out.add(nodes.get(0));
        while (i < nodes.size() - 1) {
            int far = i + 1;
            for (int j = Math.min(nodes.size() - 1, i + lookahead); j > i + 1; j--) {
                if (straight(w, nodes.get(i), nodes.get(j))) {
                    far = j;
                    break;
                }
            }
            out.add(nodes.get(far));
            i = far;
        }
        return out;
    }

    /**
     * Points {x, y, z} to draw along the smoothed route, about one a block, each on the ground it
     * crosses ({@code lift} above the surface).
     */
    static List<double[]> points(Walkable w, List<int[]> anchors, double lift) {
        List<double[]> out = new ArrayList<>();
        if (anchors.isEmpty()) {
            return out;
        }
        int[] first = anchors.get(0);
        out.add(new double[] {first[0] + 0.5, first[1] + lift, first[2] + 0.5});
        for (int s = 1; s < anchors.size(); s++) {
            int[] a = anchors.get(s - 1);
            int[] b = anchors.get(s);
            int steps = Math.max(1, (int) Math.ceil(flat(a[0], a[2], b[0], b[2])));
            int y = a[1];
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                double x = a[0] + 0.5 + (b[0] - a[0]) * t;
                double z = a[2] + 0.5 + (b[2] - a[2]) * t;
                int ny = i == steps ? b[1] : w.stand((int) Math.floor(x), (int) Math.floor(z), y);
                if (ny != NONE) {
                    y = ny;
                }
                out.add(new double[] {x, y + lift, z});
            }
        }
        return out;
    }

    /** Whether a straight walk from a to b stays on walkable ground, column by column. */
    static boolean straight(Walkable w, int[] a, int[] b) {
        double dx = b[0] - a[0];
        double dz = b[2] - a[2];
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) * 2);
        int y = a[1];
        int px = a[0];
        int pz = a[2];
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(a[0] + 0.5 + dx * t);
            int z = (int) Math.floor(a[2] + 0.5 + dz * t);
            if (x == px && z == pz) {
                continue;
            }
            // crossing a corner: both columns beside it must be walkable too
            if (x != px && z != pz && (w.stand(x, pz, y) == NONE || w.stand(px, z, y) == NONE)) {
                return false;
            }
            int ny = w.stand(x, z, y);
            if (ny == NONE) {
                return false;
            }
            y = ny;
            px = x;
            pz = z;
        }
        return y == b[1];
    }

    private static List<int[]> trace(Node n) {
        List<int[]> out = new ArrayList<>();
        for (Node c = n; c != null; c = c.parent()) {
            out.add(new int[] {c.x(), c.y(), c.z()});
        }
        Collections.reverse(out);
        return out;
    }

    /**
     * Octile distance (the walking length with diagonal steps and no obstacles) plus {@link #H_Y} a
     * block of height beyond what that walk can climb or descend anyway (a stair or slope covers a
     * block of height a step), so a stair down is not judged worse than jumping off. With
     * {@code steep} (the target far above or below, a cliff city) a block of height counts as much
     * as a block closer: the larger of the two, so the spot right above the target is not taken
     * for near, and a stair (closer and lower at once) is not counted twice against a drop.
     */
    static double h(int x, int y, int z, int tx, int ty, int tz, boolean steep) {
        int dx = Math.abs(tx - x);
        int dz = Math.abs(tz - z);
        double walk = Math.max(dx, dz) + (DIAG - 1) * Math.min(dx, dz);
        int dy = Math.abs(ty - y);
        return steep ? Math.max(walk, dy) : walk + H_Y * Math.max(0, dy - walk);
    }

    private static double flat(int x, int z, int tx, int tz) {
        return Math.hypot(tx - x, tz - z);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
