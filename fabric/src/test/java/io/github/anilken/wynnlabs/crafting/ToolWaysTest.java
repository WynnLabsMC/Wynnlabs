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
    void anOwnedLowerToolMakesTheUpgradeCostOnlyTheFragment() {
        Map<String, Double> prices = Map.of("Bronze Pickaxe T4", 300.0, "Copper Pickaxe T3", 100.0,
                "Underworld Crypt Fragment", 40.0);
        var ways = ToolWays.ways("Bronze Pickaxe T4", 4, "Copper Pickaxe T3", true, prices::get);
        assertEquals(ToolWays.Kind.UPGRADE, ways.get(0).kind());
        assertEquals(40.0, ways.get(0).total());
        // the dungeon merchant's two fragments next, the market after; one without a price last
        assertEquals(ToolWays.Kind.DUNGEON, ways.get(1).kind());
        assertEquals(80.0, ways.get(1).total());
        assertEquals(ToolWays.Kind.MARKET, ways.get(2).kind());
        assertNull(ways.get(ways.size() - 1).total());
    }

    @Test
    void withoutTheLowerToolItsPriceCounts() {
        Map<String, Double> prices = Map.of("Copper Pickaxe T3", 100.0, "Underworld Crypt Fragment", 40.0);
        var up = ToolWays.ways("Bronze Pickaxe T4", 4, "Copper Pickaxe T3", false, prices::get).stream()
                .filter(w -> w.kind() == ToolWays.Kind.UPGRADE).findFirst().orElseThrow();
        assertEquals(140.0, up.total());
    }
}
