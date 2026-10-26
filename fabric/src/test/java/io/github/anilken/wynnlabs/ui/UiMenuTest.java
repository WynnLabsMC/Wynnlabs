package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UiMenuTest {

    @Test
    void opensUnderItsChipWithTheLeftEdgesTogether() {
        int[] b = UiMenu.place(new int[] {100, 50, 60, 14}, 120, 80, new int[] {0, 0, 800, 450});
        assertArrayEquals(new int[] {100, 50 + 14 + Theme.SP_1, 120, 80}, b);
    }

    @Test
    void opensAboveWhenThereIsNoRoomBelow() {
        int[] b = UiMenu.place(new int[] {100, 400, 60, 14}, 120, 80, new int[] {0, 0, 800, 450});
        assertEquals(400 - Theme.SP_1 - 80, b[1]);
    }

    @Test
    void flipsLeftOntoTheChipsRightEdgeAtTheScreensSide() {
        int[] b = UiMenu.place(new int[] {740, 50, 50, 14}, 120, 80, new int[] {0, 0, 800, 450});
        assertEquals(740 + 50 - 120, b[0]);
    }

    @Test
    void neverLeavesTheScreenWhenNeitherSideHasRoom() {
        int[] b = UiMenu.place(new int[] {10, 40, 50, 14}, 120, 430, new int[] {0, 0, 800, 450});
        assertEquals(450 - Theme.SP_2 - 430, b[1], "as low as it fits, never past the screen's foot");
    }
}
