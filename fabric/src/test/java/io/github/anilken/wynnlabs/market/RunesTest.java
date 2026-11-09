package io.github.anilken.wynnlabs.market;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunesTest {

    @Test
    void theFiveRunesAreShipped() {
        assertEquals(5, Runes.runes().size());
        assertEquals("The Wartorn Palace", Runes.of("Ek Rune").raids().get(0).raid());
        assertEquals(2, Runes.of("Tol Rune").raids().size());
        assertTrue(Runes.of("Az Rune").keys().isEmpty());
        assertNull(Runes.of("Luminous Rune"));
    }

    @Test
    void aKeyTradesMarginIsTheKeyLessTheBrokenKeyAndRunes() {
        Runes.Rune tol = Runes.of("Tol Rune");
        Runes.KeyTrade t = tol.keys().get(0);          // a broken key and two runes
        Map<String, Double> prices = Map.of(t.broken(), 100.0, "Tol Rune", 50.0, t.key(), 260.0);
        Runes.Margin m = Runes.margin(tol, t, prices::get);
        assertEquals(200.0, m.cost());
        assertEquals(60.0, m.profit());
        assertNull(Runes.margin(tol, t, n -> null).profit());
    }
}
