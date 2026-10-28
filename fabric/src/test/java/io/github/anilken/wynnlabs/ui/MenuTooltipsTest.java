package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The menu tooltip size: which scale is drawn, and where the scaled box goes. */
class MenuTooltipsTest {

    private static final float EPS = 1e-4f;

    @Test
    void theSmallerScaleWinsAndTheyNeverMultiply() {
        assertEquals(0.85f, MenuTooltips.scale(0.85f, 1f, 100, 480), EPS, "Wynntils at 100%: ours");
        assertEquals(0.8f, MenuTooltips.scale(0.85f, 0.8f, 100, 480), EPS, "Wynntils smaller: Wynntils'");
        assertEquals(0.85f, MenuTooltips.scale(0.85f, 1.2f, 100, 480), EPS, "Wynntils larger: ours");
    }

    @Test
    void aTooltipTallerThanTheWindowShrinksUntilItFits() {
        int screenH = 270;                                  // 1080p at GUI scale 4
        int h = 400;
        float t = MenuTooltips.scale(0.85f, 1f, h, screenH);
        assertTrue(t < 0.85f, "shrunk further");
        assertEquals(screenH, (h + 2 * MenuTooltips.EDGE) * t, 0.01f, "frame included, exactly the window's height");
        assertEquals(MenuTooltips.MIN, MenuTooltips.scale(0.85f, 1f, 100000, screenH), EPS, "never below the floor");
    }

    @Test
    void besideTheMouseLikeTheGame() {
        float[] at = new float[2];
        MenuTooltips.place(640, 360, 100, 100, 120, 60, 0.85f, at);
        assertEquals(100 + MenuTooltips.OFFSET, at[0], EPS);
        assertEquals(100 - MenuTooltips.OFFSET, at[1], EPS);
    }

    @Test
    void flipsLeftAtTheRightEdge() {
        float[] at = new float[2];
        float t = 0.8f;
        MenuTooltips.place(640, 360, 600, 100, 200, 60, t, at);
        assertEquals(600 - MenuTooltips.OFFSET - 200 * t, at[0], EPS, "the box ends left of the mouse");
    }

    @Test
    void theScaledBoxStaysInsideTheWindow() {
        int screenW = 480;
        int screenH = 270;
        float[] at = new float[2];
        for (int h : new int[] {40, 200, 300, 600}) {
            for (int my : new int[] {0, 5, 135, 265, 270}) {
                float t = MenuTooltips.scale(0.85f, 1f, h, screenH);
                MenuTooltips.place(screenW, screenH, 200, my, 150, h, t, at);
                float edge = MenuTooltips.EDGE * t;
                assertTrue(at[1] >= edge - EPS, "top inside at h=" + h + " my=" + my);
                assertTrue(at[1] + h * t + edge <= screenH + 0.01f, "bottom inside at h=" + h + " my=" + my);
            }
        }
    }

    @Test
    void snapsToWholeWindowPixels() {
        assertEquals(10f + 2f / 3, MenuTooltips.snap(10.7f, 3), EPS);
        assertEquals(10.5f, MenuTooltips.snap(10.4f, 2), EPS);
        assertEquals(10.25f, MenuTooltips.snap(10.3f, 4), EPS);
    }

    @Test
    void menuScaleSnapsToWholeWindowPixels() {
        assertEquals(1f, MenuTooltips.onGrid(1f, 2), EPS, "fits: the menu's own size");
        assertEquals(0.5f, MenuTooltips.onGrid(0.7f, 2), EPS, "menu 2: halves only");
        assertEquals(2f / 3f, MenuTooltips.onGrid(0.7f, 3), EPS, "menu 3: thirds");
        assertEquals(1f / 3f, MenuTooltips.onGrid(0.1f, 3), EPS, "never under one window pixel");
        assertEquals(0.7f, MenuTooltips.onGrid(0.7f, 0), EPS, "no grid: unchanged");
    }

    @Test
    void tallTooltipInOwnScreenShrinksInWholePixels() {
        // GUI 3, 360 GUI px high, a 400 px tooltip: Wynntils would draw it at 0.88; ours at 2/3
        float t = MenuTooltips.onGrid(MenuTooltips.scale(1f, 1f, 400, 360), 3);
        assertEquals(2f / 3f, t, EPS);
        assertEquals(1f, MenuTooltips.onGrid(MenuTooltips.scale(1f, 1f, 200, 360), 3), EPS, "fits: full size");
    }
}
