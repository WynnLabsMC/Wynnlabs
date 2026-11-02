package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide route's dots on Wynntils' minimap and map: one every few blocks, never too many. */
class GuideMinimapTest {

    /** {@code n} points {@code gap} blocks apart going east. */
    private static List<double[]> east(int n, double gap) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new double[] {i * gap, 64, 0});
        }
        return pts;
    }

    @Test
    void aDotEveryFourBlocksWithBothEnds() {
        int[] keep = GuideLine.mapPoints(east(101, 1), 4, 400);
        assertEquals(26, keep.length);
        for (int i = 0; i < keep.length; i++) {
            assertEquals(i * 4, keep[i]);
        }
    }

    @Test
    void theLastPointIsKeptOffTheStep() {
        int[] keep = GuideLine.mapPoints(east(12, 1), 4, 400);
        assertArrayEquals(new int[] {0, 4, 8, 11}, keep);
    }

    @Test
    void pointsFurtherApartThanTheStepAreAllKept() {
        assertEquals(10, GuideLine.mapPoints(east(10, 6), 4, 400).length);
    }

    @Test
    void theStepIsMeasuredAlongTheWayNotStraight() {
        // a zigzag north and south across one column: 2 blocks a point, never far from the start
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            pts.add(new double[] {0, 64, i % 2 == 0 ? 0 : 2});
        }
        assertArrayEquals(new int[] {0, 2, 4, 6, 8}, GuideLine.mapPoints(pts, 4, 400));
    }

    @Test
    void aLongWayKeepsAtMostTheCap() {
        int[] keep = GuideLine.mapPoints(east(2000, 1), 4, 50);
        assertTrue(keep.length <= 50, "kept " + keep.length);
        assertTrue(keep.length >= 40, "kept " + keep.length);
        assertEquals(0, keep[0]);
        assertEquals(1999, keep[keep.length - 1]);
        for (int i = 1; i < keep.length; i++) {
            assertTrue(keep[i] > keep[i - 1]);
        }
    }

    @Test
    void noPointsNoDots() {
        assertEquals(0, GuideLine.mapPoints(List.of(), 4, 400).length);
        assertArrayEquals(new int[] {0}, GuideLine.mapPoints(east(1, 1), 4, 400));
    }
}
