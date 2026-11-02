package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide route past the loaded chunks: exact blocks near the player, a made-up LOD surface beyond. */
class FarPathTest {

    private static final int N = GuidePath.NONE;
    /** The loaded area: this many blocks each way around the origin. */
    private static final int LOADED = 32;

    /** Flat exact ground at 64 inside the loaded area, one block up and three down a step. */
    private static final GuidePath.Walkable EXACT = (x, z, fromY) -> {
        int y = 64;
        return y - fromY > 1 || fromY - y > 3 ? N : y;
    };

    private static final LodGround.Loaded INSIDE = (x, z) -> Math.abs(x) <= LOADED && Math.abs(z) <= LOADED;

    /** Flat LOD ground at 64 with a cliff (a plateau at 80) and a river of water across the way. */
    private static int surface(int x, int z) {
        if (x >= 150 && x <= 160 && Math.abs(z) <= 60) {
            return LodGround.pack(80, LodGround.GROUND, 0);
        }
        if (x >= 80 && x <= 85) {
            return LodGround.pack(63, LodGround.WET, 0);
        }
        if (x >= 300 && x <= 302 && Math.abs(z) <= 10) {
            return LodGround.pack(64, LodGround.LAVA, 0);
        }
        return LodGround.pack(64, LodGround.GROUND, 0);
    }

    private static LodGround ground(LodGround.Columns lod) {
        return new LodGround(EXACT, (x, z) -> 64, INSIDE, lod);
    }

    @Test
    void theRouteGoesPastTheLoadedEdgeAroundACliffAndOverARiver() {
        LodGround w = ground(FarPathTest::surface);
        GuidePath.Route r = FarPath.find(w, w, 0, 64, 0, 220, 64, 0, 260, 600_000, 5_000);
        assertNotNull(r);
        assertTrue(r.reached(), "the far target is reached over the LOD surface");
        boolean wet = false;
        boolean around = false;
        for (int[] n : r.nodes()) {
            assertFalse(n[0] >= 150 && n[0] <= 160 && Math.abs(n[2]) <= 60, "never on the plateau");
            wet |= n[0] >= 80 && n[0] <= 85;
            around |= n[0] >= 150 && n[0] <= 160 && Math.abs(n[2]) > 60;
            assertEquals(n[0] >= 80 && n[0] <= 85 && !INSIDE.loaded(n[0], n[2]) ? 63 : 64, n[1]);
        }
        assertTrue(wet, "the river is swum across");
        assertTrue(around, "the cliff is skirted");
        // out in the LOD area the steps go two columns at a time; in the loaded area one
        int[] a = r.nodes().get(r.nodes().size() - 2);
        int[] b = r.nodes().get(r.nodes().size() - 1);
        assertEquals(2, Math.max(Math.abs(a[0] - b[0]), Math.abs(a[2] - b[2])));
        int[] first = r.nodes().get(1);
        assertEquals(1, Math.max(Math.abs(first[0]), Math.abs(first[2])));
    }

    @Test
    void lavaIsNeverSteppedOnAndWaterCostsMore() {
        LodGround w = ground(FarPathTest::surface);
        assertEquals(N, w.stand(301, 0, 64));
        assertEquals(64, w.stand(301, 11, 64));
        assertEquals(LodGround.WATER, w.cost(82, 63, 0));
        assertEquals(1, w.cost(90, 64, 0));
        // more than a block up onto the plateau is barred, off it is a drop
        assertEquals(N, w.stand(150, 0, 64));
        assertEquals(64, w.stand(161, 0, 80));
        // loaded columns keep the exact rules
        assertEquals(64, w.stand(10, 10, 64));
        assertFalse(w.coarse(10, 10));
        assertTrue(w.coarse(40, 10));
    }

    @Test
    void withoutLodDataTheRouteEndsAtTheLoadedEdge() {
        LodGround w = ground((x, z) -> LodGround.NO_DATA);
        GuidePath.Route r = FarPath.find(w, w, 0, 64, 0, 220, 64, 0, 260, 600_000, 5_000);
        assertNotNull(r);
        assertFalse(r.reached());
        int[] last = r.nodes().get(r.nodes().size() - 1);
        assertEquals(LOADED, last[0]);
        assertEquals(GuideLine.UNKNOWN, w.height(100, 0));
    }

    @Test
    void theCacheKeepsColumnsAndCountsReads() {
        int[] calls = {0};
        LodGround.Cache c = new LodGround.Cache((x, z) -> {
            calls[0]++;
            return LodGround.pack(70, LodGround.WET, 1);
        }, 60_000);
        c.round();
        assertEquals(70, LodGround.feet(c.surface(5, -5)));
        c.surface(5, -5);
        assertEquals(1, calls[0]);
        assertEquals(1, c.reads());
        assertEquals(1, c.finest());
        c.round();
        c.surface(5, -5);
        assertEquals(0, c.reads(), "kept from the last round");
        assertEquals(1, c.finest());
        assertEquals(LodGround.WET, LodGround.kind(LodGround.pack(-12, LodGround.WET, 3)));
        assertEquals(-12, LodGround.feet(LodGround.pack(-12, LodGround.WET, 3)));
        assertEquals(3, LodGround.detail(LodGround.pack(-12, LodGround.WET, 3)));
    }

    @Test
    void theDrawnPointsLieOnTheLodSurface() {
        LodGround w = ground(FarPathTest::surface);
        GuidePath.Route r = FarPath.find(w, w, 0, 64, 0, 120, 64, 0, 160, 600_000, 5_000);
        assertTrue(r.reached());
        List<double[]> pts = GuidePath.points(w, GuidePath.smooth(w, r.nodes(), 24), 0);
        boolean low = false;
        for (double[] p : pts) {
            if (p[0] >= 81 && p[0] <= 85) {
                low |= p[1] == 63;
            }
        }
        assertTrue(low, "the line dips onto the river's surface");
    }
}
