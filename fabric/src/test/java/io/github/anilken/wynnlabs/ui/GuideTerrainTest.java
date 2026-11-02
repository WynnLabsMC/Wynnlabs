package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guide route on patches of real Wynncraft terrain ({@link TerrainGrid}, exported from a
 * Distant Horizons LOD database by tools/dh_lod_export.py): a coast with a bluff, a river, and a
 * moated hill with arches over its paths. Each case checks the route a player would walk; the
 * costs in {@link GuidePath} were checked against these (docs/guide-terrain.md).
 */
class GuideTerrainTest {

    /** The game's search box at a 12-chunk render distance and its node budget. */
    private static final int BOX = 192;
    private static final int BUDGET = 150_000;

    private record Walk(GuidePath.Route route, double length, double road, int maxDrop) {}

    private static Walk walk(TerrainGrid g, int sx, int sy, int sz, int tx, int ty, int tz) {
        GuidePath.Route r = GuidePath.find(g, sx, sy, sz, tx, ty, tz, BOX, BUDGET);
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
    void theRiverIsNotSwumSoTheRouteStopsAtTheBank() {
        // the river runs the patch's whole width with no bridge: without swimming the route ends
        // on the near bank, at the water, as close to the far side as the land reaches
        TerrainGrid g = TerrainGrid.load("river");
        Walk w = walk(g, 30, g.floor(30, 62), 62, 40, g.top(40, 15), 15);
        assertFalse(w.route().reached());
        int[] end = w.route().nodes().get(w.route().nodes().size() - 1);
        boolean bank = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                bank |= g.inside(end[0] + dx, end[2] + dz) && g.wet(end[0] + dx, end[2] + dz);
            }
        }
        assertTrue(bank, "ends at the water: " + end[0] + ", " + end[2]);
        assertTrue(end[2] < 45, "on the bank nearest the target: z " + end[2]);
    }

    @Test
    void theHillRoadUnderTheArches() {
        // the start lies under an arch (its highest floor is the arch top, 58 blocks up): the
        // route starts on the ground below and keeps to the road round the hill
        TerrainGrid g = TerrainGrid.load("mountain");
        assertTrue(g.top(8, 88) > g.floor(8, 88) + 40);
        Walk w = walk(g, 8, g.floor(8, 88), 88, 44, g.floor(44, 90), 90);
        assertTrue(w.route().reached());
        assertEquals(g.floor(8, 88), w.route().nodes().get(0)[1]);
        assertTrue(w.road() >= 0.5, "road share " + w.road());
        assertTrue(w.maxDrop() <= 3, "walked, not jumped");
    }
}
