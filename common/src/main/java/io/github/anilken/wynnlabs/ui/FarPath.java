package io.github.anilken.wynnlabs.ui;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The guide route to a target far past the loaded chunks, over the LOD surface ({@link LodGround}):
 * the same steps as {@link GuidePath#find}, but out in the coarse area (where {@link Coarse} says
 * so) a move goes two columns at once, each one stepped on in turn, and the distance estimate
 * weighs {@link #WEIGHT}, so a search across a couple of thousand blocks finds a good way in time
 * rather than the best one. A move whose second column cannot be stepped on stops at the first.
 * Pure: no Minecraft types.
 */
final class FarPath {

    /** The distance estimate is scaled by this: above 1, so the search heads for the target. */
    static final double WEIGHT = 1.25;
    /** The clock is read once every this many spots looked at. */
    private static final int CLOCK_EVERY = 512;

    /** Whether column (x, z) is out in the coarse (LOD) area. */
    @FunctionalInterface
    interface Coarse {
        boolean coarse(int x, int z);
    }

    private record Node(int x, int y, int z, double g, double f, Node parent) {}

    private static final int[][] MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final double DIAG = Math.sqrt(2);

    private FarPath() {}

    /**
     * The route from feet ({@code sx}, {@code sy}, {@code sz}) to feet ({@code tx}, {@code ty},
     * {@code tz}), at most {@code budget} spots inside {@code box} blocks of the start and
     * {@code maxMillis}; short of the target it ends at the spot nearest it. Null when the start
     * has no ground.
     */
    static GuidePath.Route find(GuidePath.Walkable w, Coarse coarse, int sx, int sy, int sz,
                                int tx, int ty, int tz, int box, int budget, long maxMillis) {
        int y0 = w.stand(sx, sz, sy);
        if (y0 == GuidePath.NONE) {
            return null;
        }
        long deadline = System.nanoTime() + maxMillis * 1_000_000;
        boolean steep = Math.abs(y0 - ty) > GuidePath.STEEP;
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap(Math.min(budget, 1 << 16));
        best.defaultReturnValue(Double.POSITIVE_INFINITY);
        double h0 = GuidePath.h(sx, y0, sz, tx, ty, tz, steep);
        Node start = new Node(sx, y0, sz, 0, WEIGHT * h0, null);
        open.add(start);
        best.put(key(sx, y0, sz), 0.0);
        Node nearest = start;
        double nearestH = h0;
        int expanded = 0;
        while (!open.isEmpty() && expanded < budget) {
            if (expanded % CLOCK_EVERY == 0 && expanded > 0 && System.nanoTime() > deadline) {
                break;
            }
            Node n = open.poll();
            if (best.get(key(n.x(), n.y(), n.z())) < n.g()) {
                continue;
            }
            double hn = GuidePath.h(n.x(), n.y(), n.z(), tx, ty, tz, steep);
            if (hn < nearestH) {
                nearest = n;
                nearestH = hn;
            }
            if (Math.hypot(tx - n.x(), tz - n.z()) <= GuidePath.REACH && Math.abs(n.y() - ty) <= GuidePath.REACH_Y) {
                return new GuidePath.Route(trace(n), true, expanded);
            }
            expanded++;
            int stride = coarse.coarse(n.x(), n.z()) ? 2 : 1;
            for (int[] m : MOVES) {
                boolean diagonal = m[0] != 0 && m[1] != 0;
                int x = n.x();
                int y = n.y();
                int z = n.z();
                double g = n.g();
                int steps = 0;
                while (steps < stride) {
                    int nx = x + m[0];
                    int nz = z + m[1];
                    if (Math.abs(nx - sx) > box || Math.abs(nz - sz) > box) {
                        break;
                    }
                    if (diagonal && (w.stand(nx, z, y) == GuidePath.NONE || w.stand(x, nz, y) == GuidePath.NONE)) {
                        break;
                    }
                    int ny = w.stand(nx, nz, y);
                    if (ny == GuidePath.NONE) {
                        break;
                    }
                    int drop = y - ny;
                    g += (diagonal ? DIAG : 1) * w.cost(nx, ny, nz) + (ny > y ? 0.5 : 0)
                            + (drop > 3 ? GuidePath.DROP_COST * drop : 0);
                    x = nx;
                    y = ny;
                    z = nz;
                    steps++;
                }
                if (steps > 0) {
                    push(open, best, n, x, y, z, g, WEIGHT * GuidePath.h(x, y, z, tx, ty, tz, steep));
                }
            }
            for (int dy = -1; dy <= 1; dy += 2) {
                int ny = w.climb(n.x(), n.z(), n.y(), dy);
                if (ny != GuidePath.NONE) {
                    push(open, best, n, n.x(), ny, n.z(), n.g() + (dy > 0 ? 1.5 : 1),
                            WEIGHT * GuidePath.h(n.x(), ny, n.z(), tx, ty, tz, steep));
                }
            }
        }
        return new GuidePath.Route(trace(nearest), false, expanded);
    }

    private static void push(PriorityQueue<Node> open, Long2DoubleOpenHashMap best, Node from,
                             int x, int y, int z, double g, double h) {
        long k = key(x, y, z);
        if (g < best.get(k)) {
            best.put(k, g);
            open.add(new Node(x, y, z, g, g + h, from));
        }
    }

    private static List<int[]> trace(Node n) {
        List<int[]> out = new ArrayList<>();
        for (Node c = n; c != null; c = c.parent()) {
            out.add(new int[] {c.x(), c.y(), c.z()});
        }
        Collections.reverse(out);
        return out;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
