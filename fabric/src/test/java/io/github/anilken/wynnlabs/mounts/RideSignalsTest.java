package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.mounts.RideSignals.Run;
import io.github.anilken.wynnlabs.mounts.RideSignals.Signal;
import org.junit.jupiter.api.Test;

import java.util.List;

class RideSignalsTest {

    private static final String PILL = "wynncraft:hud/gameplay/default/pill";
    private static final String ENERGY = "wynncraft:hud/gameplay/default/center_left";

    @Test
    void readsThePillAndTheEnergyFrameFromTheirFonts() {
        Signal s = RideSignals.read(List.of(new Run("minecraft:default", "❤ 2450/2450 "),
                new Run(PILL, " "), new Run(ENERGY, "")));
        assertEquals(" ", s.pillRaw());
        assertEquals("SEED 12", s.pill());
        assertEquals(0x17, s.energyFrame());
    }

    @Test
    void decodesDigitsDotsLettersAndKeepsUnknownGlyphsAsQuestionMarks() {
        assertEquals("0123456789", RideSignals.decodePill(""));
        assertEquals("1.5", RideSignals.decodePill(""));
        assertEquals("AZ", RideSignals.decodePill(""));
        assertEquals("+?", RideSignals.decodePill("+"));
    }

    @Test
    void energyGlyphsOutsideTheirFontOrRangeAreNotRead() {
        assertEquals(-1, RideSignals.read(List.of(new Run("minecraft:default", ""))).energyFrame());
        assertEquals(-1, RideSignals.read(List.of(new Run(ENERGY, " a"))).energyFrame());
        assertEquals(47, RideSignals.read(List.of(new Run(ENERGY, ""))).energyFrame());
        assertTrue(RideSignals.read(List.of(new Run(null, "x"), new Run(PILL, null))).empty());
    }

    @Test
    void aPillThatShowsUpOrChangesIsAChange() {
        Signal none = Signal.NONE;
        Signal a = new Signal("", "1", -1);
        Signal b = new Signal("", "2", -1);
        assertTrue(RideSignals.changed(none, a));
        assertTrue(RideSignals.changed(a, b));
        assertFalse(RideSignals.changed(a, a));
        assertFalse(RideSignals.changed(a, none), "a pill going away is not a change");
    }

    @Test
    void anEnergyFrameThatMovesIsAChangeOnlyWhenBothAreRead() {
        assertTrue(RideSignals.changed(new Signal("", "", 10), new Signal("", "", 11)));
        assertFalse(RideSignals.changed(new Signal("", "", 10), new Signal("", "", 10)));
        assertFalse(RideSignals.changed(new Signal("", "", -1), new Signal("", "", 11)));
        assertFalse(RideSignals.changed(new Signal("", "", 10), new Signal("", "", -1)));
    }

    @Test
    void theDevLogEscapesPrivateGlyphs() {
        assertEquals("a\\uE021", RideSignals.escaped("a"));
    }

    private static final String BOTTOM = "minecraft:hud/gameplay/default/bottom_middle";

    @Test
    void theMainLinesMountPartAfterItsLastMarkIsReadAndItsCountingIsAChange() {
        Signal a = RideSignals.read(List.of(new Run("minecraft:hud/overlay/control", "Hold Crouch to dismount"),
                new Run(BOTTOM, "쿺7쿺7쿿4쿿3")));
        assertEquals("7쿿4쿿3", a.tail());
        Signal b = RideSignals.read(List.of(new Run(BOTTOM, "쿺7쿺7쿿4쿿3")));
        assertTrue(RideSignals.changed(a, b));
        assertFalse(RideSignals.changed(b, b));
        assertEquals("", RideSignals.read(List.of(new Run(BOTTOM, ""))).tail(), "no mark: no mount part");
        assertFalse(RideSignals.changed(b, Signal.NONE), "going away is not a change");
    }
}
