package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combined goals: how they are made, what is offered per class, and that no part is given up. */
class CombinedGoalsTest {

    @Test
    void aCombinedGoalWeighsEveryPartAndTakesAnyCore() {
        Suggestions.GoalSet g = Suggestions.combined(List.of("rawspell", "mana", "spellcost"), 100, null);
        assertNotNull(g);
        assertEquals("combo:rawspell+mana+spellcost", g.key());
        assertEquals(List.of("rawspell", "mana", "spellcost"), Suggestions.parts(g.key()));
        Map<String, Double> w = g.goal().weights();
        assertTrue(w.get("rawSpellDamage") > 0);
        assertTrue(w.get("manaRegen") > 0);
        assertTrue(w.get("2ndSpellCost") < 0, "a cost cut is the gain");
        assertTrue(g.core().containsAll(List.of("rawSpellDamage", "manaRegen", "2ndSpellCost")));
        // the ids the damage model reads ("raw" + "1st" + "SpellCost", and the percent "1stSpellCost")
        for (String n : List.of("1st", "2nd", "3rd", "4th")) {
            assertTrue(w.get("raw" + n + "SpellCost") < 0 && w.get(n + "SpellCost") < 0, n);
        }
        // scaled: each part's strongest core stat counts 1, so the parts weigh alike
        assertEquals(-1.0, w.get("raw1stSpellCost"), 1e-9);
        assertTrue(w.get("manaRegen") >= 1.0 - 1e-9, "mana counts at least as its own part: " + w.get("manaRegen"));
        assertNull(Suggestions.combined(List.of("rawspell", "nothing"), 100, null));
        assertEquals(List.of("mana"), Suggestions.parts("mana"));
    }

    @Test
    void presetsFollowTheClassAndItsElement() {
        List<List<String>> caster = Suggestions.presets(true, new int[] {0, 0, 80, 0, 30});
        assertEquals(List.of("rawspell", "mana", "spellcost"), caster.get(0));
        assertTrue(caster.contains(List.of("water", "rawspell")), "intelligence: water");
        List<List<String>> melee = Suggestions.presets(false, null);
        assertEquals(List.of("melee", "attackspeed", "lifesteal"), melee.get(0));
        assertTrue(melee.stream().noneMatch(p -> Suggestions.ELEMENTS.contains(p.get(0))), "no element while skills are unknown");
        for (List<String> p : caster) {
            assertNotNull(Suggestions.combined(p, 100, null), "every preset is a goal: " + p);
        }
        for (List<String> p : melee) {
            assertNotNull(Suggestions.combined(p, 100, null), "every preset is a goal: " + p);
        }
    }

    /** A combined set brings every part: the raw spell, mana and cost goal leaves none of them at nothing. */
    @Test
    void aCombinedSetGivesUpNoPart() {
        Suggestions.GoalSet g = Suggestions.combined(List.of("rawspell", "mana"), 105, null);
        WhatCanICraft.Stock stock = new WhatCanICraft.Stock(Map.of(), Map.of(), Map.of());
        List<SetSuggestions.CraftSet> sets = new java.util.ArrayList<>();
        SetSuggestions.runBand("relik", List.of(g), 103, stock, CraftGoldenTest.ingredients(), n -> null,
                CraftGoldenTest.NO_DEADLINE_MS, new CraftSearch.Budget(CraftGoldenTest.NO_DEADLINE_MS), sets::add);
        assertTrue(!sets.isEmpty(), "a combined set is found");
        for (SetSuggestions.CraftSet s : sets) {
            Craft.Range spell = s.totals().getOrDefault("rawSpellDamage", Craft.Range.ZERO);
            Craft.Range pct = s.totals().getOrDefault("spellDamage", Craft.Range.ZERO);
            Craft.Range mana = s.totals().getOrDefault("manaRegen", Craft.Range.ZERO);
            Craft.Range steal = s.totals().getOrDefault("manaSteal", Craft.Range.ZERO);
            assertTrue(spell.mean() > 0 || pct.mean() > 0, s.tier() + " spell given up: " + s.totals());
            assertTrue(mana.mean() > 0 || steal.mean() > 0, s.tier() + " mana given up: " + s.totals());
        }
    }
}
