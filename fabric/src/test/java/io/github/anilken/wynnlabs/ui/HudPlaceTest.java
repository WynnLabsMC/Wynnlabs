package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.anilken.wynnlabs.ui.HudPlace.Corner;
import io.github.anilken.wynnlabs.ui.HudPlace.Spot;
import org.junit.jupiter.api.Test;

class HudPlaceTest {

    private static final int W = 853;
    private static final int H = 480;

    @Test
    void anElementIsSavedByTheCornerOfItsQuarter() {
        assertEquals(new Spot(Corner.TOP_LEFT, 4, 100), HudPlace.of(4, 100, 150, 150, W, H));
        assertEquals(new Spot(Corner.TOP_RIGHT, 10, 20), HudPlace.of(W - 150 - 10, 20, 150, 100, W, H));
        assertEquals(new Spot(Corner.BOTTOM_LEFT, 0, 30), HudPlace.of(0, H - 100 - 30, 150, 100, W, H));
        assertEquals(new Spot(Corner.BOTTOM_RIGHT, 8, 8), HudPlace.of(W - 158, H - 108, 150, 100, W, H));
    }

    @Test
    void aSavedSpotComesBackWhereItWasAndKeepsToItsCornerInAnotherWindow() {
        for (int[] at : new int[][] {{4, 100}, {600, 12}, {20, 300}, {690, 370}}) {
            Spot s = HudPlace.of(at[0], at[1], 150, 100, W, H);
            int[] n = HudPlace.numbers(s);
            Spot back = HudPlace.fromNumbers(n[0], n[1], n[2]);
            assertEquals(s, back);
            assertArrayEquals(at, HudPlace.resolve(back, W, H, 150, 100));
        }
        Spot right = HudPlace.of(W - 160, 12, 150, 100, W, H);
        assertArrayEquals(new int[] {460 - 160, 12}, HudPlace.resolve(right, 460, 320, 150, 100),
                "the same distance from the right edge in a smaller window");
    }

    @Test
    void noOrAnUnknownCornerMeansTheDefaultPlace() {
        assertNull(HudPlace.fromNumbers(-1, 0, 0));
        assertNull(HudPlace.fromNumbers(9, 3, 3));
        assertEquals(new Spot(Corner.TOP_LEFT, 0, 0), HudPlace.fromNumbers(0, -5, -1), "negative distances read as 0");
    }

    @Test
    void anElementNeverLeavesTheScreen() {
        assertArrayEquals(new int[] {0, 0}, HudPlace.clamp(-20, -5, W, H, 150, 100));
        assertArrayEquals(new int[] {W - 150, H - 100}, HudPlace.clamp(900, 999, W, H, 150, 100));
        // a spot saved in a large window, far from its corner, in a small one
        assertArrayEquals(new int[] {0, 320 - 152 - 10}, HudPlace.resolve(new Spot(Corner.BOTTOM_RIGHT, 700, 10), 460, 320, 150, 152));
        // larger than the screen: the top-left stays on it
        assertArrayEquals(new int[] {0, 0}, HudPlace.clamp(10, 10, 100, 80, 150, 100));
    }

    @Test
    void edgesAndTheMiddlePullWithinASnap() {
        assertEquals(HudPlace.MARGIN, HudPlace.snap(HudPlace.MARGIN + 3, 150, W));
        assertEquals(W - 150 - HudPlace.MARGIN, HudPlace.snap(W - 150 - HudPlace.MARGIN - 4, 150, W));
        assertEquals((W - 150) / 2, HudPlace.snap((W - 150) / 2 + 2, 150, W));
        assertEquals(200, HudPlace.snap(200, 150, W), "away from every line it stays put");
        assertEquals(true, HudPlace.centred((W - 150) / 2, 150, W));
    }
}
