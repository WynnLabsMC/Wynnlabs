package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide line's ground points, air arc and fade near the player. */
class GuideLineTest {

    private static final double E = 1e-6;

    @Test
    void groundPointsFollowTheHeightAndEndAtTheTarget() {
        // ground rises one block every 4 blocks east
        List<double[]> pts = GuideLine.sample(0.5, 64, 0.5, 20.5, 0.5, 2, 300, (x, z) -> 64 + x / 4);
        assertEquals(11, pts.size());
        assertEquals(64 + GuideLine.LIFT, pts.get(0)[1], E);
        double[] last = pts.get(pts.size() - 1);
        assertEquals(20.5, last[0], E);
        assertEquals(0.5, last[2], E);
        assertEquals(64 + 5 + GuideLine.LIFT, last[1], E);
        for (int i = 1; i < pts.size(); i++) {
            assertEquals(2, Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[2] - pts.get(i - 1)[2]), E);
        }
    }

    @Test
    void unloadedGroundKeepsTheLastHeight() {
        List<double[]> pts = GuideLine.sample(0, 70, 0, 0, 10, 2, 300,
                (x, z) -> z >= 6 ? GuideLine.UNKNOWN : 60);
        assertEquals(60 + GuideLine.LIFT, pts.get(2)[1], E);
        assertEquals(60 + GuideLine.LIFT, pts.get(pts.size() - 1)[1], E);
    }

    @Test
    void aRoofOverheadDoesNotLiftTheLine() {
        // a cave: the highest block is 30 above the player from z = 4 on
        List<double[]> pts = GuideLine.sample(0, 40, 0, 0, 12, 2, 300, (x, z) -> z >= 4 ? 70 : 40);
        assertTrue(pts.stream().allMatch(p -> p[1] < 41));
    }

    @Test
    void theLineStopsAtTheCap() {
        List<double[]> pts = GuideLine.sample(0, 64, 0, 1000, 0, 2, 300, (x, z) -> 64);
        assertEquals(151, pts.size());
        assertEquals(300, pts.get(pts.size() - 1)[0], E);
        assertEquals(1, GuideLine.sample(5, 64, 5, 5, 5, 2, 300, (x, z) -> 64).size());
    }

    @Test
    void theArcRisesInTheMiddleAndReachesTheTarget() {
        List<double[]> pts = GuideLine.arc(0, 100, 0, 100, 100, 0, 300, 10);
        assertEquals(11, pts.size());
        assertEquals(100, pts.get(10)[0], E);
        assertEquals(100, pts.get(10)[1], E);
        assertEquals(105, pts.get(5)[1], E);
        double[] cut = GuideLine.arc(0, 100, 0, 1000, 100, 0, 300, 10).get(10);
        assertEquals(300, cut[0], E);
    }

    @Test
    void fadesOutNearThePlayer() {
        assertEquals(0f, GuideLine.nearFade(1), 1e-6);
        assertEquals(1f, GuideLine.nearFade(8), 1e-6);
        assertTrue(GuideLine.nearFade(5) > 0 && GuideLine.nearFade(5) < 1);
    }
}
