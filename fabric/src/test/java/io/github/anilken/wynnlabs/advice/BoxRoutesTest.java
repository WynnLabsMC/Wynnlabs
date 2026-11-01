package io.github.anilken.wynnlabs.advice;

import io.github.anilken.wynnlabs.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which way a gear box goes: the Blacksmith, the Trade Market's reveal, or the Item Identifier,
 * from its outcomes' prices and fees. The rare chestplate box is the one from the player's
 * screenshot (Lv. 56-60).
 */
class BoxRoutesTest {

    private static final double FEE = 5;
    private static final double MIN_PROFIT = 640;

    private static BoxRoutes.Outcome o(String name, int cost, Double price) {
        return new BoxRoutes.Outcome(name, cost, price, true, false);
    }

    /** Rare chestplates, Lv. 56-60, with the prices the tooltip showed. */
    private static List<BoxRoutes.Outcome> rareChest() {
        return List.of(
                o("Dravarden", Tier.RARE.identificationCost(56), 16.0 * 64),
                o("Hailstone", Tier.RARE.identificationCost(57), 16.0 * 64),
                o("Skien's Fatigues", Tier.RARE.identificationCost(59), 2.8 * 4096),
                o("Masochist", Tier.RARE.identificationCost(57), 46.0 * 64),
                o("Tarnkappe", Tier.RARE.identificationCost(56), 9.0 * 64));
    }

    @Test
    void feesAreWhatTheGameShows() {
        // the Identifier's 76/77/79 for Lv. 56/57/59 rares, 33 for a Lv. 60 unique (the Relik)
        assertEquals(76, Tier.RARE.identificationCost(56));
        assertEquals(77, Tier.RARE.identificationCost(57));
        assertEquals(79, Tier.RARE.identificationCost(59));
        assertEquals(33, Tier.UNIQUE.identificationCost(60));
        assertEquals("76-79 E", BoxRoutes.cost(BoxRoutes.value(rareChest(), FEE)));
        assertEquals("33 E", BoxRoutes.cost(BoxRoutes.value(List.of(o("Autumn", 33, 48.0 * 64)), FEE)));
    }

    @Test
    void expectedValueIsTheMeanOverTheOutcomes() {
        BoxRoutes.Value v = BoxRoutes.value(rareChest(), FEE);
        double mean = (1024 + 1024 + 2.8 * 4096 + 2944 + 576) / 5;
        assertEquals(mean, v.expected(), 1e-6);
        assertEquals(5, v.priced());
        assertTrue(v.unpriced().isEmpty());
        assertEquals((76 + 77 + 79 + 77 + 76) / 5.0, v.cost(), 1e-9);
        assertEquals(mean * 0.95 - v.cost(), v.net(), 1e-6);
    }

    @Test
    void aBoxWorthMoreThanItsFeeIsRevealedAtTheTradeMarket() {
        BoxRoutes.Choice c = BoxRoutes.choose(rareChest(), FEE, MIN_PROFIT, false);
        assertEquals(BoxRoutes.Route.REVEAL_MARKET, c.route());
        assertNull(c.wearable());
    }

    @Test
    void aBoxWorthLessThanItsFeeGoesToTheBlacksmith() {
        // unique Lv. 30 boots: 18 E to reveal, outcomes that sell for less than that
        List<BoxRoutes.Outcome> cheap = List.of(o("Boots A", 18, 10.0), o("Boots B", 18, 12.0));
        BoxRoutes.Choice c = BoxRoutes.choose(cheap, FEE, MIN_PROFIT, false);
        assertEquals(BoxRoutes.Route.BLACKSMITH, c.route());
        assertTrue(c.value().net() < 0);
    }

    @Test
    void aSmallProfitUnderTheRuleGoesToTheBlacksmith() {
        // worth ~5 EB after the fee: more than it costs, less than the 10 EB the rule asks
        List<BoxRoutes.Outcome> small = List.of(o("Ring A", 40, 6.0 * 64), o("Ring B", 40, 6.0 * 64));
        BoxRoutes.Choice c = BoxRoutes.choose(small, FEE, MIN_PROFIT, false);
        assertEquals(BoxRoutes.Route.BLACKSMITH, c.route());
        assertTrue(c.value().net() > 0 && c.value().net() < MIN_PROFIT);
        // with the rule at 0 any profit is worth the reveal
        assertEquals(BoxRoutes.Route.REVEAL_MARKET, BoxRoutes.choose(small, FEE, 0, false).route());
    }

    @Test
    void aBoxUnderTheTradeMarketMinimumGoesToTheBlacksmith() {
        // a Lv. 51-55 bracelet worth ~16 EB revealed, with "sell on the Trade Market from" at 2 LE
        List<BoxRoutes.Outcome> outs = List.of(o("Devilish", 71, 11.0 * 64), o("Bovine Cuff", 74, 17.0 * 64),
                o("Czytash's Compass", 74, 19.0 * 64));
        assertEquals(BoxRoutes.Route.REVEAL_MARKET, BoxRoutes.choose(outs, FEE, MIN_PROFIT, false).route());
        assertEquals(BoxRoutes.Route.BLACKSMITH, BoxRoutes.choose(outs, FEE, MIN_PROFIT, 2 * 4096, false).route());
    }

    @Test
    void oneOutcomeOverTheMinimumStillPaysForTheReveal() {
        // the Lv. 101-105 unique spear from the player's screenshot: six cheap outcomes and
        // Worthless Heirloom at 4.1 LE, with "sell on the Trade Market from" at 2 LE. The mean
        // (~1.1 LE) is under the minimum, but a 55 E reveal buys a 1-in-7 shot at 4.1 LE
        List<BoxRoutes.Outcome> outs = List.of(
                o("Violent Spark", 54, 49.0 * 64), o("Imagination", 55, 42.0 * 64),
                o("Sinistra Lance", 55, 11.0 * 64), o("Buried Glory", 55, 8.0 * 64),
                o("Stalwart Peacekeeper", 55, 33.0 * 64), o("Susurrus", 55, 1.5 * 4096),
                o("Worthless Heirloom", 55, 4.1 * 4096));
        BoxRoutes.Choice c = BoxRoutes.choose(outs, FEE, MIN_PROFIT, 2 * 4096, false);
        assertEquals(BoxRoutes.Route.REVEAL_MARKET, c.route());
        assertEquals(List.of("Worthless Heirloom"), c.above());
        // with the minimum at 0 every outcome sells there: nothing to single out
        assertTrue(BoxRoutes.choose(outs, FEE, MIN_PROFIT, false).above().isEmpty());
        // a minimum nothing reaches: the Blacksmith, as before
        assertEquals(BoxRoutes.Route.BLACKSMITH, BoxRoutes.choose(outs, FEE, MIN_PROFIT, 5 * 4096, false).route());
    }

    @Test
    void aWearableOutcomeGoesToTheItemIdentifier() {
        List<BoxRoutes.Outcome> outs = new java.util.ArrayList<>(rareChest());
        outs.set(3, new BoxRoutes.Outcome("Masochist", 77, 46.0 * 64, true, true));
        BoxRoutes.Choice c = BoxRoutes.choose(outs, FEE, MIN_PROFIT, false);
        assertEquals(BoxRoutes.Route.IDENTIFIER, c.route());
        assertEquals("Masochist", c.wearable());
        // even when the box would not pay for a reveal: you wear what comes out
        List<BoxRoutes.Outcome> cheap = List.of(new BoxRoutes.Outcome("Boots A", 18, 10.0, true, true));
        assertEquals(BoxRoutes.Route.IDENTIFIER, BoxRoutes.choose(cheap, FEE, MIN_PROFIT, false).route());
    }

    @Test
    void anOutcomeWithoutAPriceIsNamedNotCountedAsZero() {
        List<BoxRoutes.Outcome> outs = List.of(
                o("Dravarden", 76, 16.0 * 64),
                o("Masochist", 77, 46.0 * 64),
                o("Tarnkappe", 76, null));
        BoxRoutes.Value v = BoxRoutes.value(outs, FEE);
        assertEquals((16.0 * 64 + 46.0 * 64) / 2, v.expected(), 1e-9);
        assertEquals(List.of("Tarnkappe"), v.unpriced());
        assertEquals(2, v.priced());
        assertEquals(BoxRoutes.Route.REVEAL_MARKET, BoxRoutes.choose(outs, FEE, MIN_PROFIT, false).route());
    }

    @Test
    void noPricesAtAllLeaveTheWorthUnknown() {
        List<BoxRoutes.Outcome> outs = List.of(o("A", 76, null), o("B", 77, null));
        BoxRoutes.Value v = BoxRoutes.value(outs, FEE);
        assertNull(v.expected());
        assertNull(v.net());
        assertEquals(2, v.unpriced().size());
        // a tier that sells well still goes to the market; any other to the Blacksmith
        assertEquals(BoxRoutes.Route.REVEAL_MARKET, BoxRoutes.choose(outs, FEE, MIN_PROFIT, true).route());
        assertEquals(BoxRoutes.Route.BLACKSMITH, BoxRoutes.choose(outs, FEE, MIN_PROFIT, false).route());
    }

    @Test
    void untradableOutcomes() {
        // nothing in it trades: only the Blacksmith takes it
        List<BoxRoutes.Outcome> none = List.of(new BoxRoutes.Outcome("Vest", 20, null, false, false));
        assertEquals(BoxRoutes.Route.UNTRADABLE, BoxRoutes.choose(none, FEE, MIN_PROFIT, true).route());
        // one untradable among priced ones counts as 0, not as missing
        List<BoxRoutes.Outcome> mixed = List.of(o("A", 20, 1000.0), new BoxRoutes.Outcome("Vest", 20, null, false, false));
        BoxRoutes.Value v = BoxRoutes.value(mixed, FEE);
        assertEquals(500, v.expected(), 1e-9);
        assertTrue(v.unpriced().isEmpty());
        // only the untradable one known, the tradable one unpriced: worth unknown
        List<BoxRoutes.Outcome> unknown = List.of(o("A", 20, null), new BoxRoutes.Outcome("Vest", 20, null, false, false));
        assertNull(BoxRoutes.value(unknown, FEE).expected());
    }
}
