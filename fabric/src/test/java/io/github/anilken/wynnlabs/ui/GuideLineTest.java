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
    void fadesInFromThePlayersFeet() {
        assertEquals(0f, GuideLine.nearFade(0), 1e-6);
        assertEquals(1f, GuideLine.nearFade(GuideLine.FADE), 1e-6);
        assertTrue(GuideLine.nearFade(0.7) > 0 && GuideLine.nearFade(0.7) < 1);
    }

    @Test
    void dashesAndGapsFlowTowardTheEnd() {
        double period = GuideLine.DASH + GuideLine.GAP;
        assertEquals(1, GuideLine.dashAt(0, 0), E);                       // a dash's front at the end
        assertTrue(GuideLine.dashAt(GuideLine.DASH + 0.1, 0) < 0);        // then a gap
        assertTrue(GuideLine.dashAt(period + 0.1, 0) > 0);                // then the next dash
        // a second later the pattern moved FLOW blocks toward the end
        assertEquals(GuideLine.dashAt(4.5, 0), GuideLine.dashAt(4.5 - GuideLine.FLOW, 1), E);
    }

    @Test
    void thePathIsCutWhereThePlayerStands() {
        List<double[]> pts = List.of(new double[] {0, 64, 0}, new double[] {10, 64, 0}, new double[] {10, 64, 10});
        double[] at = GuideLine.nearest(pts, 4, 1);
        assertEquals(0, at[0], E);
        assertEquals(0.4, at[1], E);
        at = GuideLine.nearest(pts, 11, 6);
        assertEquals(1, at[0], E);
        assertEquals(0.6, at[1], E);
    }

    @Test
    void thePathIsWalkedByArcLengthRoundACorner() {
        // 4 blocks east, then 4 north: a point every 0.25 blocks, the corner kept
        List<double[]> pts = List.of(new double[] {0, 64, 0}, new double[] {4, 64, 0}, new double[] {4, 64, 4});
        GuideLine.Track t = GuideLine.resample(pts, 0.25, 100);
        assertEquals(33, t.s().length);
        assertEquals(8, t.length(), E);
        assertEquals(8, t.toEnd(), E);
        double[] corner = GuideLine.at(t, 4);
        assertEquals(4, corner[0], E);
        assertEquals(0, corner[2], E);
        double[] past = GuideLine.at(t, 5.1);
        assertEquals(4, past[0], E);
        assertEquals(1.1, past[2], E);
        // capped: only the first stretch, but still measured from the whole path's end
        GuideLine.Track cut = GuideLine.resample(pts, 0.25, 3);
        assertEquals(3, cut.length(), E);
        assertEquals(8, cut.toEnd(), E);
    }

    @Test
    void dashCentresAreEvenlySpacedFixedToTheEndAndFlow() {
        double period = GuideLine.DASH + GuideLine.GAP;
        double[] c = GuideLine.centres(20, 20, 0);
        assertTrue(c.length > 5);
        for (int i = 1; i < c.length; i++) {
            assertEquals(period, c[i] - c[i - 1], E);
        }
        assertTrue(c[0] >= GuideLine.DASH / 2 - E);
        assertTrue(c[c.length - 1] + GuideLine.DASH / 2 <= 20 + E);
        // fixed to the end: the last centre sits a whole number of periods before it
        double fromEnd = 20 - c[c.length - 1];
        assertEquals(0, Math.abs(fromEnd / period - Math.round(fromEnd / period)), 1e-6);
        // a moment later every dash moved toward the end by FLOW blocks a second
        double[] later = GuideLine.centres(20, 20, 0.2);
        assertEquals(c[1] + 0.2 * GuideLine.FLOW, later[1], E);
        // never more than the nearest DASHES
        assertEquals(GuideLine.DASHES, GuideLine.centres(1000, 1000, 0).length);
    }

    @Test
    void thePulseStaysWithinItsRange() {
        for (double t = 0; t < 3; t += 0.05) {
            float p = GuideLine.pulse(t);
            assertTrue(p >= 1 - GuideLine.PULSE - 1e-6 && p <= 1 + GuideLine.PULSE + 1e-6);
        }
        assertEquals(1 + GuideLine.PULSE, GuideLine.pulse(GuideLine.PULSE_SECONDS / 4), 1e-6);
    }

    @Test
    void aStaircaseIsEasedIntoARampAndNothingSinks() {
        // three steps a block high and a block deep, a point every 0.25 blocks
        double[] y = new double[16];
        for (int i = 0; i < y.length; i++) {
            y[i] = 64 + Math.min(3, i / 4);
        }
        double[] eased = GuideLine.ease(y, 0.25);
        for (int i = 0; i < y.length; i++) {
            assertTrue(eased[i] >= y[i] - E, "below the ground at " + i);
            if (i > 0) {
                // never steeper than the stair's run: a chevron lies on it, it does not stand up
                assertTrue(Math.abs(eased[i] - eased[i - 1]) <= 0.25 * GuideLine.EASE + E, "too steep at " + i);
            }
        }
        // flat ground stays as it is
        double[] flat = GuideLine.ease(new double[] {64, 64, 64, 64}, 0.25);
        for (double v : flat) {
            assertEquals(64, v, E);
        }
    }

    @Test
    void theChevronsFadeFromCyanToWhite() {
        assertEquals(0x7FD8FF, GuideLine.mix(0x7FD8FF, 0xFFFFFF, 0));
        assertEquals(0xFFFFFF, GuideLine.mix(0x7FD8FF, 0xFFFFFF, 1));
        int half = GuideLine.mix(0x000000, 0xFFFFFF, 0.5f);
        assertEquals(0x80, half >> 16 & 0xFF);
    }
    @Test
    void theCorridorTailStopsAtTheCap() {
        List<double[]> pts = List.of(new double[] {4, 64, 0}, new double[] {8, 64, 0}, new double[] {12, 64, 0},
                new double[] {16, 64, 0});
        List<double[]> out = GuideLine.upTo(pts, new double[] {0, 64, 0}, 10);
        assertEquals(2, out.size());
        assertEquals(8, out.get(1)[0], E);
        assertEquals(4, GuideLine.upTo(pts, new double[] {0, 64, 0}, 100).size());
        assertTrue(GuideLine.upTo(pts, new double[] {0, 64, 0}, 1).isEmpty());
    }

}
