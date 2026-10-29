package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class FeederOverlayTest {

    /** A feeder's slots: six rows of nine, 18 px apart, from (100, 50). */
    private static List<int[]> slots() {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                out.add(new int[] {100 + 18 * c - 1, 50 + 18 * r - 1, 18, 18});
            }
        }
        return out;
    }

    @Test
    void aClickInsideAnyRealSlotIsNeverOurs() {
        List<int[]> slots = slots();
        // our drawings laid right over the slots: the slot still wins, every pixel of it
        int[] chip = {90, 40, 200, 140};
        int[] card = {90, 40, 200, 140};
        int[] pin = {90, 40, 200, 140};
        List<int[]> tags = List.of(new int[] {90, 40, 200, 140, 2});
        for (int[] s : slots) {
            for (int dx = 0; dx < s[2]; dx++) {
                for (int dy = 0; dy < s[3]; dy++) {
                    assertEquals(FeederOverlay.Hit.GAME, FeederOverlay.hit(s[0] + dx + 0.5, s[1] + dy + 0.5, slots, chip, card, pin, null, tags));
                }
            }
        }
    }

    @Test
    void ourOwnDrawingsOutsideTheSlotsTakeTheirClicks() {
        List<int[]> slots = slots();
        int[] chip = {120, 20, 80, 14};
        int[] card = {300, 60, 176, 160};
        int[] pin = {450, 63, 10, 10};
        int[] button = {400, 200, 60, 12};
        List<int[]> tags = List.of(new int[] {70, 54, 26, 9, 1});
        assertEquals(FeederOverlay.Hit.CHIP, FeederOverlay.hit(130, 25, slots, chip, card, pin, button, tags));
        assertEquals(FeederOverlay.Hit.PIN, FeederOverlay.hit(455, 68, slots, chip, card, pin, button, tags));
        assertEquals(FeederOverlay.Hit.BUTTON, FeederOverlay.hit(410, 205, slots, chip, card, pin, button, tags));
        assertEquals(FeederOverlay.Hit.CARD, FeederOverlay.hit(320, 100, slots, chip, card, pin, button, tags));
        assertEquals(FeederOverlay.Hit.TAG, FeederOverlay.hit(80, 58, slots, chip, card, pin, button, tags));
        assertEquals(FeederOverlay.Hit.GAME, FeederOverlay.hit(5, 5, slots, chip, card, pin, button, tags), "nothing of ours: the game's");
    }
}
