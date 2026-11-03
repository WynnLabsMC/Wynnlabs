package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide line's walking route on small made-up grids. */
class GuidePathTest {

    private static final int N = GuidePath.NONE;

    /** A square grid of feet heights (NONE: a wall or water), 20 × 20 unless sized, stepping rules as in the game. */
    private static class Grid implements GuidePath.Walkable {
        final int[][] h;
        final int size;
        /** Deepest step down: 3 a walk; {@link GuidePath#DROP} off a ledge where nothing overhangs. */
        int drop = 3;

        Grid(int flat) {
            this(flat, 20);
        }

        Grid(int flat, int size) {
            this.size = size;
            h = new int[size][size];
            for (int[] row : h) {
                Arrays.fill(row, flat);
            }
        }

        @Override
        public int stand(int x, int z, int fromY) {
            if (x < 0 || z < 0 || x >= size || z >= size || h[x][z] == N) {
                return N;
            }
            int y = h[x][z];
            return y - fromY > 1 || fromY - y > drop ? N : y;
        }
    }

    @Test
    void aChestOnCratesIsReachedByTheStairAside() {
        // a town square: crates 4 high over x 20..23, z 20..23 with the chest on top at (21, 21);
        // the player 14 blocks west of it. The only way up is a stair along z = 10 (x 10..13,
        // one block a step) and a walkway at crate height back along x = 21 to the crates.
        Grid g = new Grid(64, 40);
        for (int x = 20; x <= 23; x++) {
            for (int z = 20; z <= 23; z++) {
                g.h[x][z] = 68;
            }
        }
        for (int x = 10; x <= 13; x++) {
            g.h[x][10] = 65 + (x - 10);
        }
        for (int x = 14; x <= 21; x++) {
            g.h[x][10] = 68;
        }
        for (int z = 11; z <= 19; z++) {
            g.h[21][z] = 68;
        }
        int ty = GuidePath.feet(g, 21, 21, 68);
        assertEquals(68, ty);
        // the box the game uses at its smallest (32 blocks each way) and its node budget
        GuidePath.Route r = GuidePath.find(g, 7, 64, 21, 21, ty, 21, 32, 20_000);
        assertTrue(r.reached());
        int[] end = r.nodes().get(r.nodes().size() - 1);
        assertEquals(68, end[1]);
        assertTrue(Math.hypot(end[0] - 21, end[2] - 21) <= GuidePath.REACH);
        assertTrue(r.nodes().stream().anyMatch(n -> n[2] == 10 && n[0] >= 10 && n[0] <= 13));
        assertTrue(r.nodes().size() > 25, "a detour, not straight at the crates: " + r.nodes().size());
        steps(g, r.nodes());
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
        assertEquals(64, GuidePath.feet(h, 2, 2, 90));     // nothing within 4: the ground of the column, further down
    }

    /**
     * A plateau (x 0-9, feet 70), a canyon (x 10-14, floor feet 30) with a stone arch over its
     * north end (y 90-91, z 6-7) and its east wall (x 15-23, feet 70); column (20, 3) not loaded.
     */
    private static GuideDump canyon() {
        GuideDumps.World w = new GuideDumps.World(24, 128, 8, GuideTerrain.AIR)
                .fill(0, 0, 0, 9, 69, 7, GuideTerrain.SOLID)
                .fill(10, 0, 0, 14, 29, 7, GuideTerrain.SOLID)
                .fill(15, 0, 0, 23, 69, 7, GuideTerrain.SOLID)
                .fill(10, 90, 6, 14, 91, 7, GuideTerrain.SOLID)
                .fill(20, 0, 3, 20, 127, 3, GuideTerrain.UNKNOWN);
        return w.dump(0, 70, 0, 12, 29, 0);
    }

    /** The ground route's start for a rider at feet (x, y, z) over {@link #canyon}. */
    private static int[] surface(GuideDump d, int x, int y, int z) {
        return GuidePath.surface(GuideDumps.walkable(d), x, z, y, d.ground().height(x, z), 0);
    }

    @Test
    void aRiderHighAboveAPlateauStartsOnThePlateau() {
        assertArrayEquals(new int[] {5, 70, 3}, surface(canyon(), 5, 110, 3));
    }

    @Test
    void aGoalWithAWrongHeightIsPutOnTheGroundOfItsColumn() {
        // the world map's cell height says 100 where the plateau stands at 70: the goal goes there,
        // not into the rock (where a search burns its whole budget and never arrives)
        GuideDump d = canyon();
        assertEquals(70, GuidePath.feet(GuideDumps.walkable(d), 5, 3, 100));
        assertEquals(70, GuidePath.feet(GuideDumps.walkable(d), 5, 3, 50));
    }

    @Test
    void aRiderStartsOnTheGroundBelowWhateverTheColumnHeightSays() {
        // Wynncraft's chunks carry no heightmap: the column's height reads 0
        GuideDump d = canyon();
        assertArrayEquals(new int[] {5, 70, 3}, GuidePath.surface(GuideDumps.walkable(d), 5, 3, 110, 0, 0));
        assertArrayEquals(new int[] {12, 30, 3}, GuidePath.surface(GuideDumps.walkable(d), 12, 3, 110, 0, 0));
    }

    @Test
    void aRiderOverACanyonStartsOnItsFloorHoweverFarDown() {
        GuideDump d = canyon();
        int[] s = surface(d, 12, 110, 3);
        assertArrayEquals(new int[] {12, 30, 3}, s);       // 80 down, past any drop limit
        // and the ground route runs from there as on foot
        GuidePath.Route r = GuidePath.find(GuideDumps.walkable(d), s[0], s[1], s[2], 12, 30, 0, 64, 10_000);
        assertTrue(r.reached());
    }

    @Test
    void aRiderUnderAnArchStartsBelowThemNotOnTheArch() {
        GuideDump d = canyon();
        assertArrayEquals(new int[] {12, 92, 6}, surface(d, 12, 110, 6));   // over it: on its top
        assertArrayEquals(new int[] {12, 30, 6}, surface(d, 12, 85, 6));    // under it: the floor
    }

    @Test
    void aRiderOverAColumnNotLoadedHasNoGroundStart() {
        assertNull(surface(canyon(), 20, 110, 3));
    }

    @Test
    void aClosedDoorInAWallIsWalkedThrough() {
        // a wall along x = 10 with one door at z = 12: the game's door counts as a way through,
        // a step that costs 1.5 (it has to be opened)
        Grid g = new Grid(64) {
            @Override
            public double cost(int x, int y, int z) {
                return x == 10 && z == 12 ? 1.5 : 1;
            }
        };
        for (int z = 0; z < 20; z++) {
            if (z != 12) {
                g.h[10][z] = N;
            }
        }
        GuidePath.Route r = GuidePath.find(g, 3, 64, 12, 17, 64, 12, 64, 20_000);
        assertTrue(r.reached());
        assertTrue(r.nodes().stream().anyMatch(n -> n[0] == 10 && n[2] == 12));
        assertTrue(r.expanded() > 0);
        steps(g, r.nodes());
    }

    @Test
    void aStairIsClimbedStepByStep() {
        // a stair up 6 blocks along z = 5, one block a step, to a terrace at 70
        Grid g = new Grid(64);
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                if (x >= 12) {
                    g.h[x][z] = 70;
                } else if (z != 5 && x >= 6) {
                    g.h[x][z] = N;                          // the terrace's side wall
                }
            }
        }
        for (int x = 6; x <= 11; x++) {
            g.h[x][5] = 64 + (x - 5);                       // 65 at x = 6 up to 70 at x = 11
        }
        GuidePath.Route r = GuidePath.find(g, 2, 64, 5, 16, 70, 5, 64, 20_000);
        assertTrue(r.reached());
        assertEquals(70, r.nodes().get(r.nodes().size() - 1)[1]);
        steps(g, r.nodes());
    }

    @Test
    void aLadderDownATenDeepShaftIsFound() {
        // a floor at 74 over the whole 10 x 10 square, a tunnel at 64 along z 0..2 under it, and a
        // shaft at (5, 5) down to the tunnel with a ladder from 73 to 64
        House h = new House();
        h.spots.clear();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                if (x != 5 || z != 5) {
                    h.spots.add(x + "," + 74 + "," + z);
                }
                if (z <= 2) {
                    h.spots.add(x + "," + 64 + "," + z);
                }
            }
        }
        h.spots.add("5,64,3");
        h.spots.add("5,64,4");
        for (int y = 64; y <= 73; y++) {
            h.ladder.add("5," + y + ",5");
        }
        GuidePath.Route r = GuidePath.find(h, 1, 74, 8, 8, 64, 1, 64, 150_000);
        assertTrue(r.reached());
        assertEquals(64, r.nodes().get(r.nodes().size() - 1)[1]);
        assertTrue(r.nodes().stream().anyMatch(n -> n[0] == 5 && n[2] == 5 && n[1] == 68));
    }

    @Test
    void theSearchStopsWhenItsTimeIsUp() {
        // a big open square and a walled-in target: with no time at all it stops at the first clock check
        Grid g = new Grid(64, 400);
        for (int i = 390; i < 400; i++) {
            g.h[i][390] = N;
            g.h[390][i] = N;
        }
        GuidePath.Route r = GuidePath.find(g, 10, 64, 10, 395, 64, 395, 192, 150_000, 0);
        assertFalse(r.reached());
        assertEquals(512, r.expanded());
    }

    /** A grid with a target at (8, 10) sealed in rock and an open one at (18, 10). */
    private static Grid sealedAndOpen() {
        Grid g = new Grid(64);
        for (int x = 7; x <= 9; x++) {
            for (int z = 9; z <= 11; z++) {
                if (x != 8 || z != 10) {
                    g.h[x][z] = N;
                }
            }
        }
        return g;
    }

    @Test
    void aSealedNearTargetGivesWayToAnOpenOne() {
        Grid g = sealedAndOpen();
        List<int[]> goals = List.of(new int[] {8, 64, 10}, new int[] {18, 64, 10});
        GuidePath.Choice c = GuidePath.findFirst(g, 2, 64, 10, goals, 64, 60_000, 150_000, 600, null);
        assertEquals(1, c.chosen());
        assertTrue(c.route().reached());
        assertFalse(c.tried().get(0).route().reached());
        int[] end = c.route().nodes().get(c.route().nodes().size() - 1);
        assertTrue(Math.hypot(end[0] - 18, end[2] - 10) <= GuidePath.REACH);
    }

    @Test
    void withNoneReachedTheNearestsPartialRouteIsKept() {
        Grid g = sealedAndOpen();
        for (int x = 17; x <= 19; x++) {                  // the open one walled in too
            for (int z = 9; z <= 11; z++) {
                g.h[x][z] = N;
            }
        }
        List<int[]> goals = List.of(new int[] {8, 64, 10}, new int[] {18, 64, 10});
        GuidePath.Choice c = GuidePath.findFirst(g, 2, 64, 10, goals, 64, 60_000, 150_000, 600, null);
        assertEquals(0, c.chosen());
        assertEquals(2, c.tried().size());
        assertFalse(c.route().reached());
    }

    @Test
    void eachCandidateKeepsToItsShareAndAKnownFailureIsNotSearchedAgain() {
        Grid g = new Grid(64, 400);
        for (int i = 390; i < 400; i++) {                  // a walled-in far corner
            g.h[i][390] = N;
            g.h[390][i] = N;
        }
        List<int[]> goals = List.of(new int[] {395, 64, 395}, new int[] {30, 64, 10});
        GuidePath.Choice c = GuidePath.findFirst(g, 10, 64, 10, goals, 192, 5_000, 150_000, 10_000, null);
        assertEquals(5_000, c.tried().get(0).route().expanded());
        assertEquals(1, c.chosen());
        GuidePath.Route old = c.tried().get(0).route();
        GuidePath.Choice again = GuidePath.findFirst(g, 10, 64, 10, goals, 192, 5_000, 150_000, 10_000,
                i -> i == 0 ? old : null);
        assertTrue(again.tried().get(0).cached());
        assertEquals(1, again.chosen());
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

    @Test
    void aSealedRoomHasNoExit() {
        // a 5 × 5 room in walls, the target outside it
        Grid g = new Grid(64);
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                if (i < 2 || i > 6 || j < 2 || j > 6) {
                    g.h[i][j] = N;
                }
            }
        }
        GuidePath.Route r = GuidePath.find(g, 4, 64, 4, 15, 64, 15, 64, 10_000);
        assertTrue(r.enclosed());
        assertNull(r.exit());
        int[] last = r.nodes().get(r.nodes().size() - 1);
        assertEquals(6, last[0]);
        assertEquals(6, last[2]);
    }

    @Test
    void closedInByTheSearchBoxTheRouteLeadsToItsEdge() {
        // open ground, the target well past a box of 5: the ground goes on at the box's east edge
        Grid g = new Grid(64);
        GuidePath.Route r = GuidePath.find(g, 2, 64, 10, 18, 64, 10, 5, 10_000);
        assertFalse(r.reached());
        assertTrue(r.enclosed());
        assertEquals(GuidePath.EXIT_BOX, r.exitKind());
        assertEquals(7, r.exit()[0]);
        assertEquals(10, r.exit()[2]);
        assertArrayEquals(r.exit(), r.nodes().get(r.nodes().size() - 1));
    }

    @Test
    void anExitIntoUnloadedChunksIsNearerThanAFarOneCloserToTheTarget() {
        // a corridor along z = 10 east to x 12, then the chunks there are not loaded; a long way
        // round (north, x 2..17 along z = 2) ends at unloaded columns too, a little nearer the target
        Grid g = new Grid(64) {
            @Override
            public boolean unknown(int x, int z, int fromY) {
                return x >= 13 && z == 10 || x >= 18 && z == 2;
            }
        };
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                boolean corridor = j == 10 && i >= 2 && i <= 12;
                boolean round = j == 2 && i >= 2 && i <= 17 || i == 2 && j >= 2 && j <= 10;
                if (!corridor && !round) {
                    g.h[i][j] = N;
                }
            }
        }
        GuidePath.Route r = GuidePath.find(g, 6, 64, 10, 40, 64, 6, 64, 10_000);
        assertTrue(r.enclosed());
        assertEquals(GuidePath.EXIT_UNLOADED, r.exitKind());
        assertEquals(12, r.exit()[0]);
        assertEquals(10, r.exit()[2]);
    }
}
