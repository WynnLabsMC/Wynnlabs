package io.github.anilken.wynnlabs.crafting;

import io.github.anilken.wynnlabs.crafting.ToolLadder.Kind;
import io.github.anilken.wynnlabs.crafting.ToolLadder.Rung;
import io.github.anilken.wynnlabs.crafting.ToolLadder.State;
import io.github.anilken.wynnlabs.crafting.ToolLadder.Where;
import io.github.anilken.wynnlabs.market.Prices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolLadderTest {

    /** Pickaxes T1..T13 at the game's levels (1, 10, 20 ... 120). */
    private static List<ToolLadder.Tool> pickaxes() {
        List<ToolLadder.Tool> out = new ArrayList<>();
        for (int t = 13; t >= 1; t--) {                 // out of order: the ladder sorts
            out.add(new ToolLadder.Tool("Stone Pickaxe T" + t, t, t == 1 ? 1 : (t - 1) * 10));
        }
        return out;
    }

    private static State state(List<Rung> ladder, int tier) {
        return ladder.get(tier - 1).state();
    }

    @Test
    void yoursNextLockedAndPast() {
        List<Rung> ladder = ToolLadder.ladder(pickaxes(), 45, Map.of(2, Where.BANK, 3, Where.HAND));
        assertEquals(State.PAST, state(ladder, 2));
        assertEquals(Where.BANK, ladder.get(1).where());
        assertEquals(State.YOURS, state(ladder, 3));
        assertEquals(State.OPEN, state(ladder, 4));          // allowed, but T5 is better
        assertEquals(State.NEXT, state(ladder, 5));          // Lv 40: the best Lv 45 allows
        assertEquals(State.LOCKED, state(ladder, 6));
        assertEquals(3, ToolLadder.yours(ladder).tool().tier());
        assertEquals(5, ToolLadder.next(ladder).tool().tier());
    }

    @Test
    void withNothingAllowedAboveTheNextIsTheFirstLockedOne() {
        List<Rung> ladder = ToolLadder.ladder(pickaxes(), 45, Map.of(5, Where.BAG));
        assertEquals(State.LOCKED, state(ladder, 6));
        Rung next = ToolLadder.next(ladder);
        assertEquals(6, next.tool().tier());
        assertEquals(State.LOCKED, next.state());
    }

    @Test
    void noToolMeansEveryAllowedTierIsAStepAndTheLastHasNoNext() {
        List<Rung> none = ToolLadder.ladder(pickaxes(), 1, Map.of());
        assertNull(ToolLadder.yours(none));
        assertEquals(1, ToolLadder.next(none).tool().tier());
        assertEquals(State.NEXT, state(none, 1));
        List<Rung> top = ToolLadder.ladder(pickaxes(), 132, Map.of(13, Where.HAND));
        assertNull(ToolLadder.next(top));
    }

    @Test
    void anUnknownLevelLocksNothing() {
        List<Rung> ladder = ToolLadder.ladder(pickaxes(), 0, Map.of(3, Where.BAG));
        for (int t = 4; t <= 13; t++) {
            assertEquals(State.OPEN, state(ladder, t));
        }
        assertEquals(4, ToolLadder.next(ladder).tool().tier());
    }

    @Test
    void theNearerPlaceWins() {
        assertEquals(Where.HAND, ToolLadder.nearer(Where.BANK, Where.HAND));
        assertEquals(Where.BAG, ToolLadder.nearer(null, Where.BAG));
        assertEquals(Where.BAG, ToolLadder.nearer(Where.BAG, null));
    }

    @Test
    void toolsAndFragmentsByName() {
        assertTrue(ToolLadder.isTool("Cobalt Pickaxe T8", Kind.PICKAXE, 8));
        assertFalse(ToolLadder.isTool("Cobalt Pickaxe T8", Kind.AXE, 8));
        assertFalse(ToolLadder.isTool("Cobalt Pickaxe T8", Kind.PICKAXE, 9));
        assertEquals(Kind.ROD, Kind.of("FISHING"));
        assertNull(Kind.of("cooking"));
        assertEquals("Corrupted Infested Pit", ToolLadder.dungeonOf("Corrupted Infested Pit Fragment"));
    }

    @Test
    void cheapestWaysWrittenShort() {
        // T2: the Tool Merchant's 12E beats a market price of 200E
        var t2 = ToolWays.ways("Stone Pickaxe T2", 2, "Stone Pickaxe T1", false, Map.of("Stone Pickaxe T2", 200.0)::get);
        var best = ToolLadder.cheapest(t2);
        assertEquals(ToolWays.Kind.MERCHANT, best.kind());
        assertEquals(Prices.fmt(12) + " merchant", ToolLadder.summary(best));
        // T5 without a market price: the upgrade from an owned T4 is two fragments
        var t5 = ToolWays.ways("Stone Pickaxe T5", 5, "Stone Pickaxe T4", true, n -> null);
        var up = ToolLadder.cheapest(t5);
        assertEquals(ToolWays.Kind.UPGRADE, up.kind());
        assertEquals("2 frags", ToolLadder.summary(up));
        // a market price: "~" and the coins
        var t8 = ToolWays.ways("Stone Pickaxe T8", 8, null, false, Map.of("Stone Pickaxe T8", 5000.0)::get);
        assertEquals("~" + Prices.fmt(5000), ToolLadder.summary(ToolLadder.cheapest(t8)));
        // a Dungeon Merchant: its fixed emeralds and the fragments
        var dungeon = t8.stream().filter(w -> w.kind() == ToolWays.Kind.DUNGEON).findFirst().orElseThrow();
        assertEquals(Prices.fmt(512) + " + 6 frags", ToolLadder.summary(dungeon));
        assertEquals("", ToolLadder.summary(null));
    }
}
