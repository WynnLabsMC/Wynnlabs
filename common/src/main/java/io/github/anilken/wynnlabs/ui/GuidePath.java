package io.github.anilken.wynnlabs.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The guide line's walking route: an A* over block columns. A step goes to one of the eight
 * neighbouring columns where there is ground to stand on at most one block up or three down
 * ({@link Walkable}); a diagonal step needs both columns beside it walkable, so it never cuts a
 * corner. The search is bounded (a box around the start, a node budget); when it cannot reach the
 * target it returns the route to the reachable spot nearest the target. Pure: no Minecraft types.
 */
final class GuidePath {

    /** No ground to stand on from here. */
    static final int NONE = Integer.MIN_VALUE;
    /** The target counts as reached within this flat distance, in blocks. */
    static final double REACH = 1.5;

    /** The ground of a column, seen from a step at feet height {@code fromY}. */
    @FunctionalInterface
    interface Walkable {
        /**
         * The feet height to stand at in column ({@code x}, {@code z}) when stepping there from
         * feet height {@code fromY}: solid ground below, two free blocks, no water or lava, at most
         * one block up and three down; {@link #NONE} when there is none (or the column is unknown).
         */
        int stand(int x, int z, int fromY);
    }

    /** A route's spots {x, feetY, z}, start first, and whether it ends at the target. */
    record Route(List<int[]> nodes, boolean reached) {}

    private record Node(int x, int y, int z, double g, double f, Node parent) {}

    private static final int[][] MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double DIAG = Math.sqrt(2);

    private GuidePath() {}

    /**
     * The route from feet ({@code sx}, {@code sy}, {@code sz}) to column ({@code tx}, {@code tz}),
     * expanding at most {@code budget} spots inside {@code box} blocks of the start; null when the
     * start itself has no ground.
     */
    static Route find(Walkable w, int sx, int sy, int sz, int tx, int tz, int box, int budget) {
        int y0 = w.stand(sx, sz, sy);
        if (y0 == NONE) {
            return null;
        }
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Long, Double> best = new HashMap<>();
        Node start = new Node(sx, y0, sz, 0, h(sx, sz, tx, tz), null);
        open.add(start);
        best.put(key(sx, y0, sz), 0.0);
        Node nearest = start;
        double nearestH = start.f();
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            Node n = open.poll();
            Double known = best.get(key(n.x(), n.y(), n.z()));
            if (known != null && known < n.g()) {
                continue;                               // a shorter way here was already taken
            }
            double hn = h(n.x(), n.z(), tx, tz);
            if (hn < nearestH) {
                nearest = n;
                nearestH = hn;
            }
            if (flat(n.x(), n.z(), tx, tz) <= REACH) {
                return new Route(trace(n), true);
            }
            expanded++;
            for (int[] m : MOVES) {
                int nx = n.x() + m[0];
                int nz = n.z() + m[1];
                if (Math.abs(nx - sx) > box || Math.abs(nz - sz) > box) {
                    continue;
                }
                boolean diagonal = m[0] != 0 && m[1] != 0;
                if (diagonal && (w.stand(n.x() + m[0], n.z(), n.y()) == NONE
                        || w.stand(n.x(), n.z() + m[1], n.y()) == NONE)) {
                    continue;
                }
                int ny = w.stand(nx, nz, n.y());
                if (ny == NONE) {
                    continue;
                }
                double g = n.g() + (diagonal ? DIAG : 1) + (ny > n.y() ? 0.5 : 0);
                long k = key(nx, ny, nz);
                Double old = best.get(k);
                if (old == null || g < old) {
                    best.put(k, g);
                    open.add(new Node(nx, ny, nz, g, g + h(nx, nz, tx, tz), n));
                }
            }
        }
        return new Route(trace(nearest), false);
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

    /** Octile distance: the walking length with diagonal steps and no obstacles. */
    private static double h(int x, int z, int tx, int tz) {
        int dx = Math.abs(tx - x);
        int dz = Math.abs(tz - z);
        return Math.max(dx, dz) + (DIAG - 1) * Math.min(dx, dz);
    }

    private static double flat(int x, int z, int tx, int tz) {
        return Math.hypot(tx - x, tz - z);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
