package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drawer's body split between its scrolling list and the map pinned beside it (pure arithmetic). */
class UiDrawerTest {

    @Test
    void wideTheMapSitsBesideTheListTheBodysWholeHeight() {
        int[][] p = UiDrawer.split(100, 50, 600, 400, false);
        int[] list = p[0];
        int[] map = p[1];
        assertEquals(50, map[1]);
        assertEquals(400, map[3]);                              // the body's whole height: it never scrolls away
        assertEquals(list[1], map[1]);
        assertEquals(Theme.SP_6, map[0] - (list[0] + list[2])); // SP_6 apart, no overlap
        assertEquals(100 + 600, map[0] + map[2]);               // ends at the body's right edge
        assertTrue(map[2] > 200 && list[2] > map[2]);
    }

    @Test
    void narrowTheMapSitsAboveTheList() {
        int[][] p = UiDrawer.split(10, 20, 300, 400, true);
        int[] list = p[0];
        int[] map = p[1];
        assertEquals(20, map[1]);
        assertEquals(300, map[2]);
        assertEquals(map[1] + map[3] + Theme.SP_4, list[1]);
        assertEquals(20 + 400, list[1] + list[3]);              // the list ends at the body's bottom
    }
}
