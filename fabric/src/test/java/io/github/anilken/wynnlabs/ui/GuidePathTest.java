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
    private static class Grid implements GuidePath.Walkable {
        final int[][] h = new int[20][20];
        /** Deepest step down: 3 a walk; {@link GuidePath#DROP} off a ledge where nothing overhangs. */
        int drop = 3;

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
            return y - fromY > 1 || fromY - y > drop ? N : y;
        }
    }

    @Test
    void aRoofWithNoStairsIsLeftBySteppingOffTheEdge() {
        // a 10-block platform over x 0..7; the target on the ground beside it
        Grid g = new Grid(64);
        g.drop = GuidePath.DROP;
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z < 20; z++) {
                g.h[x][z] = 74;
            }
        }
        GuidePath.Route r = GuidePath.find(g, 2, 74, 5, 15, 64, 5, 64, 20_000);
        assertTrue(r.reached());
        List<int[]> n = r.nodes();
        boolean stepped = false;
        for (int i = 1; i < n.size(); i++) {
            stepped |= n.get(i - 1)[1] - n.get(i)[1] == 10;
        }
        assertTrue(stepped);
        assertEquals(64, n.get(n.size() - 1)[1]);
    }

    @Test
    void stairsBeatADrop() {
        // the same platform with a stair down along z = 0 to the target: the route takes it
        Grid g = new Grid(64);
        g.drop = GuidePath.DROP;
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z < 20; z++) {
                g.h[x][z] = 74;
            }
        }
        for (int x = 8; x <= 17; x++) {
            g.h[x][0] = 74 - (x - 7);                     // 73 at x = 8 down to 64 at x = 17
        }
        GuidePath.Route r = GuidePath.find(g, 6, 74, 1, 18, 64, 1, 64, 20_000);
        assertTrue(r.reached());
        List<int[]> n = r.nodes();
        for (int i = 1; i < n.size(); i++) {
            assertTrue(n.get(i - 1)[1] - n.get(i)[1] <= 3, "dropped instead of taking the stair");
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
        GuidePath.Route r = GuidePath.find(g, 2, 64, 5, 18, 64, 5, 64, 20_000);
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
        GuidePath.Route r = GuidePath.find(g, 2, 70, 5, 18, 64, 5, 64, 20_000);
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
        GuidePath.Route r = GuidePath.find(g, 2, 64, 15, 15, 64, 15, 64, 20_000);
        assertFalse(r.reached());
        int[] end = r.nodes().get(r.nodes().size() - 1);
        assertEquals(11, end[0]);
        assertEquals(15, end[2]);
    }

    @Test
    void noGroundUnderTheStartGivesNoRoute() {
        Grid g = new Grid(64);
        g.h[2][2] = N;
        assertNull(GuidePath.find(g, 2, 64, 2, 15, 64, 15, 64, 20_000));
    }

    @Test
    void anOpenFieldSmoothsToOneStraightSegment() {
        Grid g = new Grid(64);
        GuidePath.Route r = GuidePath.find(g, 1, 64, 1, 17, 64, 9, 64, 20_000);
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
        GuidePath.Route r = GuidePath.find(g, 2, 64, 5, 18, 64, 5, 64, 20_000);
        List<int[]> smooth = GuidePath.smooth(g, r.nodes(), 24);
        assertTrue(smooth.size() >= 3 && smooth.size() < r.nodes().size());
        for (int i = 1; i < smooth.size(); i++) {
            assertTrue(GuidePath.straight(g, smooth.get(i - 1), smooth.get(i)));
        }
    }

    @Test
    void routesKeepToRoads() {
        // a road along z = 8, a little aside from the straight way along z = 5
        Grid g = new Grid(64) {
            @Override
            public double cost(int x, int y, int z) {
                return z == 8 ? GuidePath.ROAD : 1;
            }
        };
        GuidePath.Route r = GuidePath.find(g, 2, 64, 5, 18, 64, 5, 64, 20_000);
        assertTrue(r.reached());
        long onRoad = r.nodes().stream().filter(n -> n[2] == 8).count();
        assertTrue(onRoad >= 8, "steps on the road: " + onRoad);
    }

    /**
     * A 10 × 10 two-storey building: the ground floor at feet 64 everywhere, an upper floor at 70
     * over x 5..9; spots are listed one by one, so a column can have both floors.
     */
    private static final class House implements GuidePath.Walkable {
        final java.util.Set<String> spots = new java.util.HashSet<>();
        final java.util.Set<String> ladder = new java.util.HashSet<>();

        House() {
            for (int x = 0; x < 10; x++) {
                for (int z = 0; z < 10; z++) {
                    spots.add(x + "," + 64 + "," + z);
                    if (x >= 5) {
                        spots.add(x + "," + 70 + "," + z);
                    }
                }
            }
        }

        /** A stair along the wall z = 9: one block up per column from x = 0 (65) to x = 4 (69). */
        House stair() {
            for (int x = 0; x <= 4; x++) {
                spots.add(x + "," + (65 + x) + "," + 9);
            }
            return this;
        }

        @Override
        public int stand(int x, int z, int fromY) {
            for (int y = fromY + 1; y >= fromY - 3; y--) {
                if (spots.contains(x + "," + y + "," + z) || ladder.contains(x + "," + y + "," + z)) {
                    return y;
                }
            }
            return N;
        }

        @Override
        public int climb(int x, int z, int y, int dy) {
            return ladder.contains(x + "," + y + "," + z) && ladder.contains(x + "," + (y + dy) + "," + z) ? y + dy : N;
        }
    }

    @Test
    void aTargetUpstairsIsReachedByTheStair() {
        House h = new House().stair();
        int ty = GuidePath.feet(h, 8, 2, 70);
        assertEquals(70, ty);
        GuidePath.Route r = GuidePath.find(h, 1, 64, 2, 8, ty, 2, 64, 20_000);
        assertTrue(r.reached());
        int[] end = r.nodes().get(r.nodes().size() - 1);
        assertEquals(70, end[1]);
        assertTrue(r.nodes().stream().anyMatch(n -> n[2] == 9 && n[1] > 64 && n[1] < 70));
    }

    @Test
    void withoutTheStairTheRouteEndsUnderTheTarget() {
        House h = new House();
        GuidePath.Route r = GuidePath.find(h, 1, 64, 2, 8, 70, 2, 64, 20_000);
        assertFalse(r.reached());
        int[] end = r.nodes().get(r.nodes().size() - 1);
        assertEquals(8, end[0]);
        assertEquals(64, end[1]);
        assertEquals(2, end[2]);
    }

    @Test
    void aLadderIsClimbed() {
        House h = new House();
        for (int y = 64; y <= 69; y++) {
            h.ladder.add(4 + "," + y + "," + 5);         // up the wall beside the upper floor
        }
        GuidePath.Route r = GuidePath.find(h, 1, 64, 2, 8, 70, 2, 64, 20_000);
        assertTrue(r.reached());
        assertTrue(r.nodes().stream().anyMatch(n -> n[0] == 4 && n[2] == 5 && n[1] == 67));
    }

    @Test
    void aTargetInAWallNicheAimsAtTheNearestFloor() {
        House h = new House();
        assertEquals(70, GuidePath.feet(h, 7, 7, 73));     // 3 above the floor: still found
        assertEquals(64, GuidePath.feet(h, 2, 2, 66));
        assertEquals(90, GuidePath.feet(h, 2, 2, 90));     // nothing within 4: the pin height
    }

    @Test
    void aRiderLandsOnGroundJustBelowOrNearby() {
        Grid g = new Grid(64);
        assertTrue(Arrays.equals(new int[] {5, 64, 5}, GuidePath.below(g, 5, 5, 68, 6, 3)));
        assertNull(GuidePath.below(g, 5, 5, 80, 6, 3));    // flying high: no ground near
        g.h[5][5] = N;
        g.h[4][5] = N;
        int[] land = GuidePath.below(g, 5, 5, 66, 6, 3);
        assertTrue(land != null && Math.max(Math.abs(land[0] - 5), Math.abs(land[2] - 5)) == 1);
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
