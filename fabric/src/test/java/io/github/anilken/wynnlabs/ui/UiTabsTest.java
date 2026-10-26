package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tab bar's and the filter row's heights, centring and overflow (pure arithmetic). */
class UiTabsTest {

    @Test
    void theTabBarsDividerIsItsLastRowSp2UnderThePills() {
        assertEquals(Ui.CHIP_H + Theme.SP_2 + 1, Ui.TAB_BAR_H);
        int y = 40;
        int divider = y + Ui.TAB_BAR_H - 1;
        assertEquals(y + Ui.CHIP_H + Theme.SP_2, divider, "SP_2 of padding between the pills and the hairline");
        assertEquals(Ui.SMALL_H, Ui.FILTER_H);
    }

    @Test
    void labelsSitCentredInTabsAndFilterChips() {
        // the base font (9px Noto Sans, no shift) in a 14px tab and a 12px filter chip
        for (int h : new int[] {Ui.TAB_BAR_H - Theme.SP_2 - 1, Ui.FILTER_H}) {
            int ty = Ui.centreY(100, h, 9, 0, false);
            float mid = ty + Ui.capTop(9, 0, false) + Ui.capHeight(9, false) / 2f;
            assertTrue(Math.abs(mid - (100 + h / 2f)) <= 0.5f, "capitals centred in " + h);
        }
    }

    @Test
    void chipsThatFitAllShowWithAGroupGapAndNoArrows() {
        int[] w = {30, 40, 20, 20};
        int[] group = {0, 0, 1, 1};
        int[] at = Ui.filterLayout(w, group, 200, 0);
        assertEquals(0, at[0]);
        assertEquals(30 + Ui.TAB_GAP, at[1]);
        assertEquals(at[1] + 40 + Ui.GROUP_GAP, at[2], "groups apart by SP_4");
        assertEquals(at[2] + 20 + Ui.TAB_GAP, at[3]);
        assertEquals(-1, at[4]);
        assertEquals(-1, at[5]);
    }

    @Test
    void chipsThatDoNotFitEndInAnArrowAndAreNeverCut() {
        int[] w = {50, 50, 50, 50, 50};
        int[] group = {0, 0, 1, 1, 1};
        int room = 140;
        int[] at = Ui.filterLayout(w, group, room, 0);
        assertEquals(room - Ui.ARROW_W, at[6], "a › at the row's end");
        for (int i = 0; i < 5; i++) {
            if (at[i] >= 0) {
                assertTrue(at[i] + w[i] <= room - Ui.ARROW_W - Ui.TAB_GAP, "chip " + i + " clear of the ›");
            }
        }
        assertEquals(-1, at[4], "the last chip waits behind the ›");
        // scrolled on: a ‹ in front, the last chip shown, no ›
        int[] on = Ui.filterLayout(w, group, room, 3);
        assertEquals(0, on[5]);
        assertEquals(-1, on[0]);
        assertTrue(on[4] >= 0 && on[4] + w[4] <= room);
        assertEquals(-1, on[6]);
    }

    @Test
    void aRowThatFitsIgnoresAStaleScroll() {
        int[] at = Ui.filterLayout(new int[] {20, 20}, new int[] {0, 0}, 100, 1);
        assertEquals(0, at[0]);
        assertEquals(-1, at[2]);
    }
}
