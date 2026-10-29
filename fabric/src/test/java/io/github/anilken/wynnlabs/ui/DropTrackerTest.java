package io.github.anilken.wynnlabs.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which tracked drops get a beam and a name, in what order, colour and text. */
class DropTrackerTest {

    @Test
    void nearestWithinRangeUpToTheCap() {
        double[] d = {30 * 30, 5 * 5, 70 * 70, 12 * 12, 0};
        assertArrayEquals(new int[] {4, 1, 3, 0}, DropTracker.nearest(d, d.length, DropTracker.RANGE, 30),
                "nearest first, the one past the range left out");
        assertArrayEquals(new int[] {4, 1}, DropTracker.nearest(d, d.length, DropTracker.RANGE, 2));
        assertArrayEquals(new int[] {1}, DropTracker.nearest(d, 2, 10, 5), "only the first n count");
    }

    @Test
    void aStackShowsItsCount() {
        assertEquals("Bob's Tear", DropTracker.label("Bob's Tear", 1));
        assertEquals("Lucky Rabbit's Foot [✫✫✫] ×3", DropTracker.label("Lucky Rabbit's Foot [✫✫✫]", 3));
    }

    @Test
    void theNameKeepsTheGamesColour() {
        assertEquals(0x55FFFF, DropTracker.nameColour(Component.literal("Gale's Force").withStyle(ChatFormatting.AQUA), 0xFFFFFF));
        Component boxed = Component.literal("").append(Component.literal("Unidentified Helmet").withStyle(ChatFormatting.LIGHT_PURPLE));
        assertEquals(0xFF55FF, DropTracker.nameColour(boxed, 0xFFFFFF), "a part deeper in the name");
        assertEquals(0xABCDEF, DropTracker.nameColour(Component.literal("Plain"), 0xABCDEF), "no colour: the fallback");
    }

    @Test
    void beamsFadeOnlyNearTheEdge() {
        assertEquals(1f, DropTracker.fade(10), 1e-6);
        assertEquals(0f, DropTracker.fade(DropTracker.RANGE), 1e-6);
        assertEquals(0.5f, DropTracker.fade(DropTracker.RANGE * 0.925), 1e-4);
    }
}
