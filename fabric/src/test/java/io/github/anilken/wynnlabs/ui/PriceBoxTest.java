package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Our tooltip keeps out from under Wynnventory's price box: the mouse's other side, else past the box. */
class PriceBoxTest {

    private static final int[] BOX = {300, 100, 120, 80};      // x, y, w, h on a 640 x 360 screen

    @Test
    void noBoxOrNoMeetingLeavesThePlace() {
        assertEquals(212, PriceBox.avoid(null, 0, 212, 90, 100, 60, 200, 12, 4, 640));
        assertEquals(212, PriceBox.avoid(BOX, PriceBox.FRESH_NANOS + 1, 212, 90, 100, 60, 200, 12, 4, 640), "an old note");
        assertEquals(212, PriceBox.avoid(BOX, 0, 212, 250, 100, 60, 200, 12, 4, 640), "below the box");
    }

    @Test
    void aTooltipOnTheBoxGoesToTheMousesOtherSide() {
        // right of the mouse at 290: 302..402 meets the box at 300..420; the left side 178..278 is free
        assertEquals(178, PriceBox.avoid(BOX, 0, 302, 90, 100, 60, 290, 12, 4, 640));
    }

    @Test
    void whenTheOtherSideIsOffScreenItGoesPastTheBox() {
        // the mouse at 40 over a box at 40..240: the left side would start off screen, so just past the box
        int[] box = {40, 80, 200, 100};
        assertEquals(252, PriceBox.avoid(box, 0, 52, 90, 100, 60, 40, 12, 4, 640));
    }

    @Test
    void theBoxStandsAtTheLeftEdgeLevelWithTheScreensMiddle() {
        float[] a = PriceBox.anchored(100, 60, 360);
        assertEquals(6, a[0]);                                // 2 from the edge, then the frame
        assertEquals((360 - 68) / 2f + 4, a[1]);
        assertEquals(1f, a[2]);
        float[] tall = PriceBox.anchored(100, 600, 360);
        assertEquals(360 / 610f, tall[2], 1e-4);
    }

    @Test
    void withNowhereToGoItStays() {
        int[] wide = {0, 0, 640, 360};
        assertEquals(52, PriceBox.avoid(wide, 0, 52, 90, 100, 60, 40, 12, 4, 640));
    }
}
