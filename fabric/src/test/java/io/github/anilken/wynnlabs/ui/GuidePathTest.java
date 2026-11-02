package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide line's walking route on small made-up grids. */
class GuidePathTest {

    private static final int N = GuidePath.NONE;

    /** A 20 × 20 grid of feet heights (NONE: a wall or water), stepping rules as in the game. */
    private static final class Grid implements GuidePath.Walkable {
        final int[][] h = new int[20][20];

        Grid(int flat) {
            for (int[] row : h) {
                Arrays.fill(row, flat);
            }
        }

        @Override
        public int stand(int x, int z, int fromY) {
            if (x < 0 || z < 0 || x >= 20 || z >= 20 || h[x][z] == N) {
                return N;
            }
            int y = h[x][z];
            return y - fromY > 1 || fromY - y > 3 ? N : y;
        }
    }

    @Test
    void aWallWithAGapRoutesThroughTheGap() {
        Grid g = new Grid(64);
        for (int z = 0; z < 20; z++) {
            if (z != 15) {
                g.h[10][z] = N;
            }
        }
        GuidePath.Route r = GuidePath.find(g, 2, 64, 5, 18, 5, 64, 20_000);
        assertTrue(r.reached());
        assertTrue(r.nodes().stream().anyMatch(n -> n[0] == 10 && n[2] == 15));
        steps(g, r.nodes());
    }

    @Test
    void aCliffIsSkirted() {
        // high ground (70) west of x = 10, low ground east; the drop is 6 except where z ≥ 17,
        // where a ledge at 67 makes two safe drops of 3
        Grid g = new Grid(70);
        for (int x = 10; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                g.h[x][z] = z >= 17 ? 67 : 64;
            }
        }
        GuidePath.Route r = GuidePath.find(g, 2, 70, 5, 18, 5, 64, 20_000);
        assertTrue(r.reached());
        assertTrue(r.nodes().stream().anyMatch(n -> n[2] >= 17));
        steps(g, r.nodes());
        assertEquals(64, r.nodes().get(r.nodes().size() - 1)[1]);
    }

    @Test
    void noWayInEndsAtTheNearestReachableSpot() {
        Grid g = new Grid(64);
        for (int i = 12; i <= 18; i++) {                // a closed ring around (15, 15)
            g.h[i][12] = N;
            g.h[i][18] = N;
            g.h[12][i] = N;
            g.h[18][i] = N;
        }
        GuidePath.Route r = GuidePath.find(g, 2, 64, 15, 15, 15, 64, 20_000);
        assertFalse(r.reached());
        int[] end = r.nodes().get(r.nodes().size() - 1);
        assertEquals(11, end[0]);
        assertEquals(15, end[2]);
    }

    @Test
    void noGroundUnderTheStartGivesNoRoute() {
        Grid g = new Grid(64);
        g.h[2][2] = N;
        assertNull(GuidePath.find(g, 2, 64, 2, 15, 15, 64, 20_000));
    }

    @Test
    void anOpenFieldSmoothsToOneStraightSegment() {
        Grid g = new Grid(64);
        GuidePath.Route r = GuidePath.find(g, 1, 64, 1, 17, 9, 64, 20_000);
        List<int[]> smooth = GuidePath.smooth(g, r.nodes(), 24);
        assertEquals(2, smooth.size());
        List<double[]> pts = GuidePath.points(g, smooth, 0.3);
        assertTrue(pts.size() > 10);
        assertTrue(pts.stream().allMatch(p -> Math.abs(p[1] - 64.3) < 1e-9));
    }

    @Test
    void smoothingKeepsTheTurnAtAWall() {
        Grid g = new Grid(64);
        for (int z = 0; z < 20; z++) {
            if (z != 15) {
                g.h[10][z] = N;
            }
        }
        GuidePath.Route r = GuidePath.find(g, 2, 64, 5, 18, 5, 64, 20_000);
        List<int[]> smooth = GuidePath.smooth(g, r.nodes(), 24);
        assertTrue(smooth.size() >= 3 && smooth.size() < r.nodes().size());
        for (int i = 1; i < smooth.size(); i++) {
            assertTrue(GuidePath.straight(g, smooth.get(i - 1), smooth.get(i)));
        }
    }

    /** Every step of a route goes to a neighbouring column the grid allows from the last height. */
    private static void steps(Grid g, List<int[]> nodes) {
        for (int i = 1; i < nodes.size(); i++) {
            int[] a = nodes.get(i - 1);
            int[] b = nodes.get(i);
            assertTrue(Math.abs(a[0] - b[0]) <= 1 && Math.abs(a[2] - b[2]) <= 1);
            assertEquals(b[1], g.stand(b[0], b[2], a[1]));
        }
    }
}
