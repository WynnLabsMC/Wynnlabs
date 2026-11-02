package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import static io.github.anilken.wynnlabs.ui.GuideTerrain.AIR;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.BARRIER;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.HALF;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.LAVA;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.ROAD;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.SOLID;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.TALL;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.THIN;
import static io.github.anilken.wynnlabs.ui.GuideTerrain.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The route's walking rules over block codes, in a made-up Wynncraft cave. */
class GuideTerrainTest {

    private static final int N = GuidePath.NONE;

    /**
     * A tunnel along z = 5 from x = 2 to 30, floor at y = 9 (feet 10), two blocks high: a pool to
     * swim through (x 10-12), a carpet (15), a hanging chain (18), a slab up (21) to a floor one
     * higher (feet 11 from x = 22), lava along its south side up to x = 20.
     */
    private static GuideDumps.World cave() {
        GuideDumps.World w = new GuideDumps.World(34, 16, 10, SOLID);
        w.fill(2, 10, 5, 20, 11, 5, AIR);
        w.fill(10, 9, 5, 12, 10, 5, WATER);
        w.set(15, 10, 5, THIN);
        w.fill(18, 10, 5, 18, 11, 5, THIN);
        w.set(21, 10, 5, HALF).fill(21, 11, 5, 21, 12, 5, AIR);
        w.fill(22, 11, 5, 30, 12, 5, AIR);
        w.fill(2, 10, 6, 20, 10, 6, LAVA);
        return w;
    }

    @Test
    void aCaveWithAPoolACarpetAChainAndASlabIsWalkedThrough() {
        GuideDump d = cave().dump(2, 10, 5, 30, 11, 5);
        GuideTerrain w = GuideDumps.walkable(d);
        GuidePath.Route r = GuidePath.find(w, 2, 10, 5, 30, 11, 5, 64, 10_000);
        assertNotNull(r);
        assertTrue(r.reached());
        assertFalse(r.enclosed());
        boolean swam = false;
        for (int[] n : r.nodes()) {
            assertEquals(5, n[2], "off the tunnel (into the lava?) at " + n[0]);
            swam |= n[0] >= 10 && n[0] <= 12 && d.at(n[0], n[1], n[2]) == WATER;
        }
        assertTrue(swam);
    }

    @Test
    void aOneBlockCrawlGapClosesTheCaveAndSaysWhy() {
        GuideDump d = cave().set(25, 12, 5, SOLID).dump(2, 10, 5, 30, 11, 5);
        GuidePath.Route r = GuidePath.find(GuideDumps.walkable(d), 2, 10, 5, 30, 11, 5, 64, 10_000);
        assertFalse(r.reached());
        assertTrue(r.enclosed());
        assertEquals(23, r.columns());                    // x 2 to 24
        assertEquals(GuidePath.STUCK, r.stuck().size());
        GuidePath.Stuck first = r.stuck().get(0);
        assertEquals(24, first.x());
        assertEquals(11, first.y());
        assertEquals("head blocked", first.rejected().get("E"));
        assertEquals("wall", first.rejected().get("N"));
        int[] last = r.nodes().get(r.nodes().size() - 1);
        assertEquals(24, last[0]);
    }

    @Test
    void theBudgetRunningOutIsNotEnclosed() {
        GuideDump d = cave().dump(2, 10, 5, 30, 11, 5);
        GuidePath.Route r = GuidePath.find(GuideDumps.walkable(d), 2, 10, 5, 30, 11, 5, 64, 5);
        assertFalse(r.reached());
        assertFalse(r.enclosed());
    }

    @Test
    void lavaIsNeverWalkedIntoAndSaysSo() {
        GuideTerrain w = GuideDumps.walkable(cave().dump(2, 10, 5, 30, 11, 5));
        assertEquals(N, w.stand(5, 6, 10));
        assertEquals("lava", w.why(5, 6, 10));
    }

    @Test
    void thinBlocksAtTheFeetAreStoodInAndWaterIsSwum() {
        GuideTerrain w = GuideDumps.walkable(cave().dump(2, 10, 5, 30, 11, 5));
        assertEquals(10, w.stand(15, 5, 10));            // carpet
        assertEquals(10, w.stand(18, 5, 10));            // chain, feet and head
        assertEquals(10, w.stand(11, 5, 10));            // in the pool
        assertEquals(GuideTerrain.SWIM, w.cost(11, 10, 5));
        assertEquals(10, w.climb(11, 5, 9, 1));          // up from the pool's bottom
    }

    @Test
    void theMiddleOfARoadIsCheaperThanItsEdge() {
        // a road three wide along x at z 4..6, on solid ground at y 9, air above
        GuideDumps.World w = new GuideDumps.World(12, 14, 10, SOLID);
        w.fill(0, 10, 0, 11, 13, 9, AIR);
        w.fill(0, 9, 4, 11, 9, 6, ROAD);
        GuideTerrain g = GuideDumps.walkable(w.dump(5, 10, 5, 6, 10, 5));
        assertEquals(GuideTerrain.ROAD_COST, g.cost(5, 10, 5));     // the middle
        assertEquals(GuideTerrain.ROAD_EDGE, g.cost(5, 10, 4));     // an edge
        assertEquals(GuideTerrain.ROAD_EDGE, g.cost(5, 10, 6));
        assertEquals(1, g.cost(5, 10, 7));                          // off the road
    }

    @Test
    void aNarrowRoadHasNoEdge() {
        // roads one and two wide along x (z 2, and z 5..6): every block of them is the road
        GuideDumps.World w = new GuideDumps.World(12, 14, 10, SOLID);
        w.fill(0, 10, 0, 11, 13, 9, AIR);
        w.fill(0, 9, 2, 11, 9, 2, ROAD);
        w.fill(0, 9, 5, 11, 9, 6, ROAD);
        GuideTerrain g = GuideDumps.walkable(w.dump(5, 10, 5, 6, 10, 5));
        assertEquals(GuideTerrain.ROAD_COST, g.cost(5, 10, 2));
        assertEquals(GuideTerrain.ROAD_COST, g.cost(5, 10, 5));
        assertEquals(GuideTerrain.ROAD_COST, g.cost(5, 10, 6));
    }

    @Test
    void aSlabIsAHalfStepUpAndAFenceIsNotJumpedOnto() {
        GuideTerrain w = GuideDumps.walkable(cave().dump(2, 10, 5, 30, 11, 5));
        assertEquals(11, w.stand(21, 5, 10));
        assertEquals(GuideTerrain.HALF_RISE, w.rise(21, 11, 5));
        assertEquals(GuidePath.RISE, w.rise(22, 11, 5));
        assertEquals(10, w.stand(20, 5, 11));            // and down off it

        GuideDumps.World f = new GuideDumps.World(6, 16, 6, AIR).fill(0, 0, 0, 5, 9, 5, SOLID).set(3, 10, 3, TALL);
        GuideTerrain fw = GuideDumps.walkable(f.dump(2, 10, 3, 4, 10, 3));
        assertEquals(N, fw.stand(3, 3, 10));
        assertEquals("fence", fw.why(3, 3, 10));
    }

    @Test
    void aBarrierIsWalkedOnAndBlocksLikeAnySolid() {
        // a floor of barriers, a barrier wall at x = 4 two high
        GuideDumps.World w = new GuideDumps.World(8, 16, 4, AIR).fill(0, 0, 0, 7, 9, 3, BARRIER)
                .fill(4, 10, 0, 4, 11, 3, BARRIER);
        GuideTerrain g = GuideDumps.walkable(w.dump(1, 10, 1, 6, 10, 1));
        assertEquals(10, g.stand(2, 1, 10));
        assertEquals(N, g.stand(4, 1, 10));
        assertEquals("wall", g.why(4, 1, 10));
        assertFalse(GuidePath.find(g, 1, 10, 1, 6, 10, 1, 16, 1000).reached());
    }

    @Test
    void aDeepShaftDropsIntoWaterButNotPastTheLimit() {
        GuideDumps.World w = new GuideDumps.World(6, 60, 6, SOLID);
        w.fill(1, 50, 1, 1, 51, 1, AIR);                  // the ledge
        w.fill(2, 1, 1, 2, 51, 1, AIR);                   // an open shaft beside it, down to y = 1
        w.fill(3, 30, 1, 3, 51, 1, AIR).fill(3, 30, 1, 3, 32, 1, WATER);
        GuideTerrain g = GuideDumps.walkable(w.dump(1, 50, 1, 3, 30, 1));
        assertEquals(N, g.stand(2, 1, 50));
        assertEquals("drop too far", g.why(2, 1, 50));
        assertEquals(32, g.stand(3, 1, 50));             // the water's top
    }
}
