package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ToolWaysTest {

    @Test
    void aToolsTierComesFromItsName() {
        assertEquals(8, ToolWays.tier("Cobalt Pickaxe T8"));
        assertEquals(12, ToolWays.tier("Dernic Rod T12"));
        assertEquals(0, ToolWays.tier("Silver Ingot"));
    }

    @Test
    void everyTierIsShipped() {
        for (int t = 1; t <= 13; t++) {
            assertEquals(t, ToolWays.tiers().get(t).tier());
        }
    }

    @Test
    void fragmentsAreCountedNotPriced() {
        Map<String, Double> prices = Map.of("Bronze Pickaxe T4", 300.0, "Copper Pickaxe T3", 100.0);
        var ways = ToolWays.ways("Bronze Pickaxe T4", 4, "Copper Pickaxe T3", true, prices::get);
        // the market (emeralds only) first, then the ways that need dungeon runs
        assertEquals(ToolWays.Kind.MARKET, ways.get(0).kind());
        var up = ways.get(1);
        assertEquals(ToolWays.Kind.UPGRADE, up.kind());
        assertEquals(0.0, up.total());          // the owned T3 costs nothing
        assertEquals(1, up.fragments());
        assertEquals(ToolWays.Kind.DUNGEON, ways.get(2).kind());
        assertEquals(2, ways.get(2).fragments());
    }

    @Test
    void withoutTheLowerToolItsPriceCounts() {
        Map<String, Double> prices = Map.of("Copper Pickaxe T3", 100.0);
        var up = ToolWays.ways("Bronze Pickaxe T4", 4, "Copper Pickaxe T3", false, prices::get).stream()
                .filter(w -> w.kind() == ToolWays.Kind.UPGRADE).findFirst().orElseThrow();
        assertEquals(100.0, up.total());
        assertNull(ToolWays.ways("Bronze Pickaxe T4", 4, "Copper Pickaxe T3", false, n -> null).get(0).total());
    }
}
