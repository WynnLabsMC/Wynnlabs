package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The suggestions' disk cache: what it keys on, when it is dropped, and that results read back as they were. */
class SuggestionCacheTest {

    private static final Suggestions.GoalSet RAW = new Suggestions.GoalSet("rawspell", null, Set.of());
    private static final Ingredient DUST = new Ingredient("Test Dust", 2, 40, List.of("jeweling"),
            Map.of("rawSpellDamage", new Ingredient.Stat(10, 14, "Raw Spell Damage", "")), new int[6], -20, 0, 0, new int[5]);

    private static final int LV_INDEX = 15;

    private static int lv() {
        return CraftData.ofType("ring").get(LV_INDEX).minLevel();
    }

    private static Suggestions.Suggestion ring(CraftSearch.Tier tier, List<String> bought) {
        CraftData.Recipe r = CraftData.ofType("ring").get(LV_INDEX);
        List<Ingredient> slots = new ArrayList<>(List.of(DUST, Ingredient.NONE, Ingredient.NONE, Ingredient.NONE, Ingredient.NONE,
                Ingredient.NONE));
        Craft.Result res = Craft.craft(r, 2, 3, Craft.Speed.NORMAL, slots);
        return new Suggestions.Suggestion(RAW, r, 2, 3, new CraftSearch.Layout(slots, res, 12.5, bought, 900, List.of(), List.of()), tier, 7);
    }

    @Test
    void aSuggestionReadsBackWithItsItemCraftedAgain() {
        SuggestionCache c = new SuggestionCache("d1");
        Suggestions.Suggestion s = ring(CraftSearch.Tier.OWN_PLUS_TWO, List.of("Test Dust"));
        c.putSingles(lv(), "st", 1000L, List.of(s));
        SuggestionCache back = SuggestionCache.fromJson(c.toJson(), "d1");
        assertTrue(back.hasSingles(lv()));
        assertEquals(1000L, back.singlesEntry(lv()).at());
        List<Suggestions.Suggestion> l = back.singles(lv(), k -> k.equals("rawspell") ? RAW : null, n -> n.equals("Test Dust") ? DUST : null);
        assertEquals(1, l.size());
        Suggestions.Suggestion r = l.get(0);
        assertEquals(s.recipe().id(), r.recipe().id());
        assertEquals(CraftSearch.Tier.OWN_PLUS_TWO, r.tier());
        assertEquals(7, r.order());
        assertEquals(12.5, r.layout().score());
        assertEquals(List.of("Test Dust"), r.layout().bought());
        assertEquals(s.layout().result().ids(), r.layout().result().ids());
        assertEquals(s.layout().result().durability(), r.layout().result().durability());
    }

    @Test
    void anUnknownGoalOrIngredientIsLeftOut() {
        SuggestionCache c = new SuggestionCache("d1");
        c.putSingles(lv(), "st", 1L, List.of(ring(CraftSearch.Tier.OWN, List.of())));
        assertTrue(c.singles(lv(), k -> null, n -> DUST).isEmpty());
        assertTrue(c.singles(lv(), k -> RAW, n -> null).isEmpty());
    }

    @Test
    void otherDataOrAnotherVersionDropsIt() {
        SuggestionCache c = new SuggestionCache("d1");
        c.putSingles(lv(), "st", 1L, List.of(ring(CraftSearch.Tier.OWN, List.of())));
        assertFalse(SuggestionCache.fromJson(c.toJson(), "d2").hasSingles(lv()));
        String older = c.toJson().replace("\"version\":" + SuggestionCache.VERSION, "\"version\":" + (SuggestionCache.VERSION - 1));
        assertFalse(SuggestionCache.fromJson(older, "d1").hasSingles(lv()));
        assertFalse(SuggestionCache.fromJson("not json", "d1").hasSingles(lv()));
        assertFalse(SuggestionCache.fromJson(null, "d1").hasSingles(lv()));
    }

    @Test
    void theKeysFollowWhatResultsDependOn() {
        String a = SuggestionCache.dataVersion(List.of(DUST));
        Ingredient other = new Ingredient("Test Dust", 3, 40, List.of("jeweling"), DUST.ids(), new int[6], -20, 0, 0, new int[5]);
        assertNotEquals(a, SuggestionCache.dataVersion(List.of(other)));
        WhatCanICraft.Stock st = new WhatCanICraft.Stock(Map.of("Test Dust", 3), Map.of(), Map.of());
        String p = SuggestionCache.stockPrint(st, new int[] {0, 0, 50, 0, 0}, 60);
        assertEquals(p, SuggestionCache.stockPrint(new WhatCanICraft.Stock(Map.of("Test Dust", 3), Map.of(), Map.of("jeweling", 9)),
                new int[] {0, 0, 50, 0, 0}, 60));      // a profession level is a status, not an input
        assertNotEquals(p, SuggestionCache.stockPrint(new WhatCanICraft.Stock(Map.of("Test Dust", 4), Map.of(), Map.of()),
                new int[] {0, 0, 50, 0, 0}, 60));
        assertNotEquals(p, SuggestionCache.stockPrint(st, new int[] {0, 0, 51, 0, 0}, 60));
    }

    @Test
    void setsRoundTripWithTheirTotalsAddedAgain() {
        Suggestions.Suggestion s = ring(CraftSearch.Tier.OWN, List.of("Test Dust"));
        SetSuggestions.CraftSet set = new SetSuggestions.CraftSet(RAW, lv(), CraftSearch.Tier.OWN,
                List.of(new SetSuggestions.Piece("ring", s.recipe(), 2, 3, s.layout()), new SetSuggestions.Piece("helmet", null, 1, 1, null)),
                s.layout().result().ids(), s.layout().result().requirements(), List.of("Test Dust"));
        SuggestionCache c = new SuggestionCache("d1");
        c.putSets("relik", lv(), "st", 5L, List.of(set));
        SuggestionCache back = SuggestionCache.fromJson(c.toJson(), "d1");
        assertTrue(back.hasSets("relik", lv()));
        assertFalse(back.hasSets("wand", lv()));
        SetSuggestions.CraftSet r = back.sets("relik", lv(), k -> RAW, n -> DUST).get(0);
        assertEquals(lv(), r.step());
        assertEquals(set.totals(), r.totals());
        assertEquals(List.of("Test Dust"), r.bought());
        assertNull(r.pieces().get(1).recipe());
        back.clearSets("relik", lv());
        assertFalse(back.hasSets("relik", lv()));
    }
}
