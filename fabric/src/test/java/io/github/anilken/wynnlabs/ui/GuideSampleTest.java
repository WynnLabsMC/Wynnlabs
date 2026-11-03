package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guide route on three terrain samples from Wynncraft ({@link TerrainGrid}): a coast with a
 * bluff, a river, and a mountain road with arches over it. Each case checks the route a player
 * would walk.
 */
class GuideSampleTest {

    /** The game's search box at a 12-chunk render distance and its node budget. */
    private static final int BOX = 192;
    private static final int BUDGET = 150_000;

    private record Walk(GuidePath.Route route, double length, double road, int maxDrop) {}

    private static Walk walk(TerrainGrid g, int sx, int sy, int sz, int tx, int ty, int tz) {
        GuidePath.Route r = GuidePath.find(g.walkable(), sx, sy, sz, tx, ty, tz, BOX, BUDGET);
        assertNotNull(r, "ground at the start");
        List<int[]> n = r.nodes();
        double length = 0;
        int roads = 0;
        int maxDrop = 0;
        for (int i = 0; i < n.size(); i++) {
            int[] b = n.get(i);
            if (g.road(b[0], b[1], b[2])) {
                roads++;
            }
            if (i > 0) {
                int[] a = n.get(i - 1);
                length += Math.hypot(b[0] - a[0], b[2] - a[2]);
                maxDrop = Math.max(maxDrop, a[1] - b[1]);
            }
        }
        return new Walk(r, length, (double) roads / n.size(), maxDrop);
    }

    @Test
    void theCoastPathKeepsToTheShoreRoad() {
        TerrainGrid g = TerrainGrid.load("coast");
        Walk w = walk(g, 20, g.top(20, 60), 60, 68, g.top(68, 92), 92);
        assertTrue(w.route().reached());
        // hardly longer than the straight way, and much of it on the paved shore path
        assertTrue(w.length() < 1.2 * Math.hypot(68 - 20, 92 - 60), "length " + w.length());
        assertTrue(w.road() >= 0.4, "road share " + w.road());
        assertTrue(w.maxDrop() <= 3, "walked, not jumped");
    }

    @Test
    void offTheBluffToTheShoreBelow() {
        // no fall damage in Wynncraft: from the clifftop town the way to the lower shore is the jump
        TerrainGrid g = TerrainGrid.load("coast");
        Walk w = walk(g, 6, g.top(6, 66), 66, 88, g.top(88, 40), 40);
        assertTrue(w.route().reached());
        assertTrue(w.maxDrop() >= 15, "drop " + w.maxDrop());
        assertTrue(w.length() < 1.2 * Math.hypot(88 - 6, 40 - 66), "length " + w.length());
    }

    @Test
    void alongTheRiverBank() {
        TerrainGrid g = TerrainGrid.load("river");
        Walk w = walk(g, 2, g.floor(2, 62), 62, 90, g.floor(90, 62), 62);
        assertTrue(w.route().reached());
        assertTrue(w.length() < 1.15 * 88, "length " + w.length());
        for (int[] n : w.route().nodes()) {
            assertFalse(g.wet(n[0], n[2]), "never in the river");
        }
    }

    @Test
    void theRiverWithNoBridgeIsSwumStraightAcross() {
        // the river runs the patch's whole width with no bridge: the route swims it, the short way
        TerrainGrid g = TerrainGrid.load("river");
        Walk w = walk(g, 30, g.floor(30, 62), 62, 46, g.floor(46, 30), 30);
        assertTrue(w.route().reached());
        int wet = 0;
        for (int[] n : w.route().nodes()) {
            wet += g.at(n[0], n[1], n[2]) == GuideTerrain.WATER ? 1 : 0;
        }
        assertTrue(wet > 0, "swam");
        assertTrue(w.length() < 1.3 * Math.hypot(46 - 30, 30 - 62), "length " + w.length());
    }

    @Test
    void theHillRoadUnderTheArches() {
        // the start lies under an arch (its highest floor is the arch top, 58 blocks up): the
        // route starts on the ground below and keeps to the road round the hill for a good part
        // (a short swim across the stream beats a bend of the road, and the road's side along
        // the cliff is left for a block or two: the route keeps off walls)
        TerrainGrid g = TerrainGrid.load("mountain");
        assertTrue(g.top(8, 88) > g.floor(8, 88) + 40);
        Walk w = walk(g, 8, g.floor(8, 88), 88, 44, g.floor(44, 90), 90);
        assertTrue(w.route().reached());
        assertEquals(g.floor(8, 88), w.route().nodes().get(0)[1]);
        assertTrue(w.road() >= 0.35, "road share " + w.road());
        assertTrue(w.maxDrop() <= 3, "walked, not jumped");
    }
}
