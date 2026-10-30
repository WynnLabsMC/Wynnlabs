package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.quests.QuestBook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Untradable gear paid in fragments, tokens or materials costs what earning them takes: the
 * shipped earn cost table's amounts, the minutes they take, the % the road map charges, the
 * source line that names them, and the quest that opens the seller.
 */
class EarnCostTest {

    @BeforeEach
    void install() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
    }

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static ItemData gear(String name) {
        return GameState.current().allGear().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void tempoTownTakesTimeFragments() {
        EarnCost.Cost seal = EarnCost.of(gear("Seal Breaker"));
        assertNotNull(seal);
        assertEquals("Time Fragment", seal.currency());
        assertEquals(7, seal.amount());
        assertEquals("Tempo Town Trouble", seal.quest());
        assertEquals(EarnCost.of(gear("Timelocked Stone")).amount(), 1);
        // seven fragments cost more than one, both less than the cap
        double big = EarnCost.percent(gear("Seal Breaker"));
        double small = EarnCost.percent(gear("Timelocked Stone"));
        assertTrue(small > 0 && small < big && big <= 12, small + " " + big);
        assertEquals(big, EarnCost.percent("Seal Breaker"), 1e-9);
        String where = ShoppingSources.where(ShoppingSources.sourceOf(gear("Seal Breaker")));
        // the fixture's book has Tempo Town Trouble done: no "after"
        assertEquals("Tempo Town merchant · 7× Time Fragment", where);
        assertEquals("merchant", ShoppingSources.kind(ShoppingSources.sourceOf(gear("Seal Breaker"))));
        assertNull(ShoppingSources.questPending(GameState.current(), gear("Seal Breaker")));
    }

    @Test
    void legendaryIslandTiersTakeTheTierBelow() {
        EarnCost.Cost d = EarnCost.of(gear("Diamond Static Bracelet"));
        assertEquals("Diamond Token", d.currency());
        assertEquals(2, d.amount());
        assertEquals("Gold Static Bracelet", d.needs());
        assertEquals("Bronze Basic Bracelet", EarnCost.of(gear("Silver Static Bracelet")).needs());
        // a Diamond bracelet is two clears, its ring one (rings cost half)
        assertTrue(EarnCost.percent(gear("Diamond Static Bracelet")) > EarnCost.percent(gear("Diamond Static Ring")));
        assertTrue(ShoppingSources.where(ShoppingSources.sourceOf(gear("Diamond Static Bracelet")))
                .endsWith("2× Diamond Token + Gold Static Bracelet"));
    }

    @Test
    void qiraHiveWaitsForItsQuest() {
        ItemData visor = gear("Sparkling Visor");
        assertEquals("Hive voucher", EarnCost.of(visor).currency());
        QuestBook.Entry q = ShoppingSources.questPending(GameState.current(), visor);
        assertNotNull(q);
        assertEquals("The Qira Hive", q.name());
        assertTrue(ShoppingSources.where(ShoppingSources.sourceOf(visor)).endsWith("(after The Qira Hive)"));
        // a Master piece takes the whole quest
        assertTrue(EarnCost.percent(gear("Boreal-Patterned Crown")) > EarnCost.percent(visor));
    }

    @Test
    void dungeonsAltarsAndMaterials() {
        double normal = EarnCost.percent(gear("Admiral"));
        double corrupted = EarnCost.percent(gear("Peaceful Rest"));
        assertTrue(normal > 0 && corrupted > normal, normal + " " + corrupted);
        assertTrue(EarnCost.percent(gear("Summa")) > 0);
        assertEquals("material set", EarnCost.of(gear("Sting-Glass Necklace")).currency());
        // the Almuj merchant's emerald gear stays free of it
        assertEquals(0, EarnCost.percent(gear("Drywind")));
        assertNull(EarnCost.of(gear("Drywind")));
    }
}
