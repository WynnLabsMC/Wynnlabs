package io.github.anilken.wynnlabs.advice;

import io.github.anilken.wynnlabs.game.TestGame;
import io.github.anilken.wynnlabs.model.BaseStats;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Requirement;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.StatRange;
import io.github.anilken.wynnlabs.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A spell cost cut is kept as a positive number: a plus, while a cost raise is a caution. */
class CautionsTest {

    private static ItemData boots(StatRange stat) {
        return new ItemData("Test Boots", Slot.BOOTS, Tier.UNIQUE, 2,
                new Requirement(40, Optional.empty(), List.of(), Optional.empty()),
                new BaseStats(300, 0, Optional.empty(), Optional.empty(), List.of(), List.of()), List.of(stat),
                Optional.empty(), "NONE");
    }

    @Test
    void aSpellCostCutIsNoCaution() throws Exception {
        TestGame.boot();
        assertTrue(Cautions.of(boots(new StatRange("1stSpellCost", "1st Spell Cost", "PERCENT", false, 6, 26))).isEmpty());
        assertTrue(Cautions.of(boots(new StatRange("raw3rdSpellCost", "3rd Spell Cost", "RAW", false, 1, 3))).isEmpty());
    }

    @Test
    void aSpellCostRaiseIsACautionWithTheGamesSign() throws Exception {
        TestGame.boot();
        List<String> c = Cautions.of(boots(new StatRange("raw3rdSpellCost", "3rd Spell Cost", "RAW", false, -10, -6)));
        assertEquals(1, c.size(), c.toString());
        assertTrue(c.get(0).endsWith("+8"), c.get(0));
    }

    @Test
    void otherStatsStillWarnWhenNegative() throws Exception {
        TestGame.boot();
        assertEquals(1, Cautions.of(boots(new StatRange("walkSpeed", "Walk Speed", "PERCENT", false, -20, -10))).size());
        assertTrue(Cautions.of(boots(new StatRange("walkSpeed", "Walk Speed", "PERCENT", false, 10, 20))).isEmpty());
    }
}
