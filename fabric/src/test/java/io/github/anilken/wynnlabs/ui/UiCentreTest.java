package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A label's capitals centred in its box, from the fonts' real metrics (pure arithmetic). */
class UiCentreTest {

    /** The capitals' middle, relative to the box's middle, for a text drawn at y. */
    private static float offCentre(int y, int boxY, int boxH, float px, int shift, boolean pixel) {
        float mid = y + Ui.capTop(px, shift, pixel) + Ui.capHeight(px, pixel) / 2f;
        return mid - (boxY + boxH / 2f);
    }

    @Test
    void everySizeSitsWithinHalfAPixelOfTheMiddle() {
        float[][] fonts = {{9, 0}, {7.8f, 0}, {7, 0}, {11.5f, 2}, {13.5f, 3}};
        for (float[] f : fonts) {
            for (int h : new int[] {11, 12, 14, 16, 20}) {
                int y = Ui.centreY(100, h, f[0], (int) f[1], false);
                assertTrue(Math.abs(offCentre(y, 100, h, f[0], (int) f[1], false)) <= 0.5f, f[0] + "px in " + h);
            }
        }
    }

    @Test
    void theBaseFontInAChipSitsLowerThanTheOldOnePixelInset() {
        // the "Pusulayı göster ›" bug: text drawn 1px into a 14px pill had its capitals 2px above the middle
        assertTrue(offCentre(101, 100, 14, 9, 0, false) < -1.5f);
        assertEquals(103, Ui.centreY(100, 14, 9, 0, false));
        assertEquals(103, Ui.centreY(100, 16, 11.5f, 2, false));
    }

    @Test
    void thePixelFontHasSevenPixelCapitalsFromTheTop() {
        assertEquals(105, Ui.centreY(100, 16, 9, 0, true));
    }

    @Test
    void anIconSitsOnTheCapitalsOfItsLine() {
        // a 7px pin beside base text at y 50: capitals 50.57..57, middle 53.8 → the pin from 50 to 57
        assertEquals(50, Ui.onCaps(50, 7, 9, 0, false));
        assertEquals(51, Ui.onCaps(50, 5, 9, 0, false));
    }

    /** Every icon an icon button or a search field shows: its drawn shape's middle on the button's middle. */
    @Test
    void buttonIconsSitWithinHalfAPixelOfTheMiddle() throws Exception {
        String[] icons = {"search", "refresh", "target", "plus", "minus", "eraser", "x", "compass", "map-pin", "scale",
            "list-check", "trending-up", "wand", "diamond", "arrows-horizontal"};
        int w = Ui.CHIP_H + 2;
        int h = Ui.CHIP_H;
        assertEquals(0, (w - 8) % 2, "the icon and the button share their parity");
        assertEquals(0, (h - 8) % 2, "the icon and the button share their parity");
        for (String name : icons) {
            var in = UiCentreTest.class.getResourceAsStream("/assets/wynnlabs/textures/gui/sprites/icon/" + name + ".png");
            assertTrue(in != null, name);
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            int x0 = img.getWidth();
            int y0 = img.getHeight();
            int x1 = -1;
            int y1 = -1;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getRGB(x, y) >>> 24) > 40) {
                        x0 = Math.min(x0, x);
                        y0 = Math.min(y0, y);
                        x1 = Math.max(x1, x + 1);
                        y1 = Math.max(y1, y + 1);
                    }
                }
            }
            float k = 8f / img.getWidth();
            float cx = Ui.iconIn(w, 8) + (x0 + x1) / 2f * k;
            float cy = Ui.iconIn(h, 8) + (y0 + y1) / 2f * k;
            assertTrue(Math.abs(cx - w / 2f) <= 0.5f, name + " x " + cx);
            assertTrue(Math.abs(cy - h / 2f) <= 0.5f, name + " y " + cy);
        }
    }

    /** The material tier chip: its star and number one group in the chip's middle, on whole pixels. */
    @Test
    void theTierSealCentresItsStarAndNumber() {
        assertEquals(0, (UiTierSeal.H - UiTierSeal.STAR) % 2, "chip and star share their parity");
        assertEquals(103, UiTierSeal.starY(100), "(14 - 8) / 2 = 3 from the top");
        for (int numW : new int[] {5, 6, 7}) {
            int w = UiTierSeal.width(numW);
            int gx = UiTierSeal.groupX(100, w, numW);
            float left = gx - 100;
            float right = 100 + w - (gx + UiTierSeal.group(numW));
            assertTrue(Math.abs(left - right) <= 1, numW + "px: the group sits in the middle");
        }
        // the number's capitals on the chip's middle, as every label in a chip
        int y = Ui.centreY(100, UiTierSeal.H, 9, 0, false);
        assertEquals(103, y);
    }

    /** The build score in the home gauge: short enough for the arc, the exact value in the tooltip. */
    @Test
    void theGaugeScoreIsCompact() {
        assertEquals("42", HomeHero.compact(42));
        assertEquals("3769", HomeHero.compact(3769));
        assertEquals("12.4k", HomeHero.compact(12_400));
        assertEquals("756k", HomeHero.compact(755_771));
        assertEquals("1.2M", HomeHero.compact(1_234_567));
    }

    @Test
    void theSwitchKnobSitsOnTheTracksMiddleOnAWholePixel() {
        // the circle (its shadow left out) centred on the track: same middle, whole-pixel gap
        assertEquals(0, (UiToggle.H - UiToggle.KNOB_D) % 2, "the gap is the same above and below");
        assertEquals(UiToggle.H / 2f, UiToggle.KNOB_INSET + UiToggle.KNOB_D / 2f, 0.0001f);
        // the slide: from where it was to where it goes, eased, done after SLIDE_MS
        assertEquals(0f, UiToggle.slide(true, 0), 0.0001f);
        assertEquals(1f, UiToggle.slide(true, UiToggle.SLIDE_MS), 0.0001f);
        assertEquals(0f, UiToggle.slide(false, UiToggle.SLIDE_MS + 50), 0.0001f);
        float half = UiToggle.slide(true, UiToggle.SLIDE_MS / 2);
        assertTrue(half > 0.5f && half < 1f, "eased out: past half way at half time");
        assertEquals(1f, UiToggle.position("test-switch", true, 1000), 0.0001f);
        assertEquals(1f, UiToggle.position("test-switch", false, 1000), 0.0001f);
        assertEquals(0f, UiToggle.position("test-switch", false, 1000 + UiToggle.SLIDE_MS), 0.0001f);
    }
}
