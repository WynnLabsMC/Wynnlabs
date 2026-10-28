package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The suggestions page's shaping: the best per type and goal, the status, the tier switch and the set shown. */
class SuggestionBoardTest {

    private static final Suggestions.GoalSet RAW = new Suggestions.GoalSet("rawspell", null, Set.of());
    private static final Suggestions.GoalSet MANA = new Suggestions.GoalSet("mana", null, Set.of());

    private static CraftData.Recipe recipe(String type, String prof, int min) {
        return new CraftData.Recipe(type + min, type, prof, min, min + 2, List.of(), null, null, null, null, 0);
    }

    private static Ingredient ing(String name, int level) {
        return new Ingredient(name, 1, level, List.of(), Map.of(), new int[6], 0, 0, 0, new int[5]);
    }

    private static CraftSearch.Layout layout(double score, List<String> bought, Ingredient... slots) {
        List<Ingredient> l = new ArrayList<>(List.of(slots));
        while (l.size() < 6) {
            l.add(Ingredient.NONE);
        }
        return new CraftSearch.Layout(l, null, score, bought, 0, List.of(), List.of());
    }

    private static Suggestions.Suggestion sugg(Suggestions.GoalSet g, CraftData.Recipe r, CraftSearch.Tier t, double score,
                                               List<String> bought, int order, Ingredient... slots) {
        return new Suggestions.Suggestion(g, r, 1, 1, layout(score, bought, slots), t, order);
    }

    @Test
    void statusIsLevelThenBuyThenReady() {
        CraftData.Recipe legs = recipe("leggings", "tailoring", 105);
        Map<String, Integer> lv = Map.of("tailoring", 105, "jeweling", 98);
        assertEquals(SuggestionBoard.Status.READY,
                SuggestionBoard.status(legs, layout(1, List.of(), ing("A", 100)), p -> lv.getOrDefault(p, 0)));
        assertEquals(SuggestionBoard.Status.BUY,
                SuggestionBoard.status(legs, layout(1, List.of("A"), ing("A", 100)), p -> lv.getOrDefault(p, 0)));
        // an ingredient above the level locks it even when the recipe does not
        CraftData.Recipe low = recipe("leggings", "tailoring", 90);
        assertEquals(SuggestionBoard.Status.LEVEL,
                SuggestionBoard.status(low, layout(1, List.of("A"), ing("A", 106)), p -> lv.getOrDefault(p, 0)));
        assertEquals(106, SuggestionBoard.needs(low, List.of(ing("A", 106), Ingredient.NONE)));
        assertEquals(SuggestionBoard.Status.LEVEL,
                SuggestionBoard.status(recipe("ring", "jeweling", 105), layout(1, List.of()), p -> lv.getOrDefault(p, 0)));
    }

    @Test
    void bestPerTypeAndGoalAtTheTier() {
        CraftData.Recipe legs = recipe("leggings", "tailoring", 105);
        CraftData.Recipe legsLow = recipe("leggings", "tailoring", 103);
        List<Suggestions.Suggestion> all = List.of(
                sugg(RAW, legs, CraftSearch.Tier.OWN, 10, List.of(), 0),
                sugg(RAW, legsLow, CraftSearch.Tier.OWN, 12, List.of(), 1),
                sugg(RAW, legs, CraftSearch.Tier.OWN_PLUS_TWO, 20, List.of("A"), 2),
                sugg(RAW, legs, CraftSearch.Tier.ANY, 30, List.of("A", "B", "C"), 3),
                sugg(MANA, legs, CraftSearch.Tier.OWN, 50, List.of(), 4));
        assertEquals(1, SuggestionBoard.pick(all, "rawspell", "leggings", CraftSearch.Tier.OWN).order());
        assertEquals(2, SuggestionBoard.pick(all, "rawspell", "leggings", CraftSearch.Tier.OWN_PLUS_TWO).order());
        assertEquals(3, SuggestionBoard.pick(all, "rawspell", "leggings", CraftSearch.Tier.ANY).order());
        assertNull(SuggestionBoard.pick(all, "rawspell", "boots", CraftSearch.Tier.ANY));
    }

    @Test
    void tierFallsBackToALowerOneTheRunDropped() {
        // the run keeps only the first of equal layouts: "a few bought" shows the owned one
        CraftData.Recipe boots = recipe("boots", "tailoring", 105);
        List<Suggestions.Suggestion> all = List.of(sugg(RAW, boots, CraftSearch.Tier.OWN, 5, List.of(), 0));
        assertEquals(0, SuggestionBoard.pick(all, "rawspell", "boots", CraftSearch.Tier.OWN_PLUS_TWO).order());
        assertEquals(0, SuggestionBoard.pick(all, "rawspell", "boots", CraftSearch.Tier.ANY).order());
        List<Suggestions.Suggestion> bought = List.of(sugg(RAW, boots, CraftSearch.Tier.ANY, 5, List.of("A"), 0));
        assertNull(SuggestionBoard.pick(bought, "rawspell", "boots", CraftSearch.Tier.OWN));
    }

    @Test
    void oneSetPerGoalBandAndTier() {
        SetSuggestions.CraftSet own0 = set(RAW, 0, CraftSearch.Tier.OWN);
        SetSuggestions.CraftSet any0 = set(RAW, 0, CraftSearch.Tier.ANY);
        SetSuggestions.CraftSet plus1 = set(RAW, 1, CraftSearch.Tier.OWN_PLUS_TWO);
        SetSuggestions.CraftSet mana = set(MANA, 0, CraftSearch.Tier.OWN);
        List<SetSuggestions.CraftSet> sets = List.of(plus1, own0, any0, mana);
        assertEquals(List.of(0, 1), SuggestionBoard.steps(sets, "rawspell"));
        assertSame(own0, SuggestionBoard.pickSet(sets, "rawspell", 0, CraftSearch.Tier.OWN_PLUS_TWO));
        assertSame(any0, SuggestionBoard.pickSet(sets, "rawspell", 0, CraftSearch.Tier.ANY));
        assertSame(plus1, SuggestionBoard.pickSet(sets, "rawspell", 1, CraftSearch.Tier.OWN));    // only a higher tier: that
        assertNull(SuggestionBoard.pickSet(sets, "mana", 1, CraftSearch.Tier.ANY));
    }

    @Test
    void setPiecesByStatusAndTheLevelsMissing() {
        List<SetSuggestions.Piece> pieces = List.of(
                new SetSuggestions.Piece("ring", recipe("ring", "jeweling", 105), 1, 1, layout(1, List.of())),
                new SetSuggestions.Piece("relik", recipe("relik", "woodworking", 103), 1, 1, layout(1, List.of())),
                new SetSuggestions.Piece("boots", recipe("boots", "tailoring", 105), 1, 1, layout(1, List.of("A"))),
                new SetSuggestions.Piece("helmet", recipe("helmet", "armouring", 105), 1, 1, null));
        SetSuggestions.CraftSet s = new SetSuggestions.CraftSet(RAW, 0, CraftSearch.Tier.OWN, pieces, Map.of(), new int[5], List.of("A"));
        Map<String, Integer> lv = Map.of("tailoring", 105, "jeweling", 98, "woodworking", 1);
        Map<SuggestionBoard.Status, List<SetSuggestions.Piece>> by = SuggestionBoard.pieces(s, p -> lv.getOrDefault(p, 0));
        assertEquals(2, by.get(SuggestionBoard.Status.LEVEL).size());
        assertEquals(1, by.get(SuggestionBoard.Status.BUY).size());
        assertEquals(0, by.get(SuggestionBoard.Status.READY).size());
        assertEquals(List.of("jeweling", "woodworking"),
                List.copyOf(SuggestionBoard.missingLevels(s, p -> lv.getOrDefault(p, 0)).keySet()));
    }

    @Test
    void rankedAcrossGoalsBestFirstWithoutDuplicates() {
        CraftData.Recipe legs = recipe("leggings", "tailoring", 105);
        CraftData.Recipe ring = recipe("ring", "jeweling", 105);
        Ingredient a = ing("A", 100);
        Ingredient b = ing("B", 100);
        List<Suggestions.Suggestion> all = List.of(
                sugg(RAW, legs, CraftSearch.Tier.OWN, 100, List.of(), 0, a, b),      // raw spell's best: 1.0
                sugg(RAW, ring, CraftSearch.Tier.OWN, 50, List.of(), 1, b),           // 0.5
                sugg(MANA, ring, CraftSearch.Tier.OWN, 4, List.of(), 2, a),           // mana's best: 1.0
                sugg(MANA, legs, CraftSearch.Tier.OWN, 3, List.of(), 3, b, a),        // the same as raw spell's leggings
                sugg(MANA, legs, CraftSearch.Tier.ANY, 9, List.of("A"), 4, a));       // a higher tier: not at OWN
        List<SuggestionBoard.Ranked> r = SuggestionBoard.ranked(all, CraftSearch.Tier.OWN, null, 105, p -> 105);
        // scores are compared within their goal: both bests first, in the order found; the duplicate is gone
        assertEquals(List.of(0, 2, 1), r.stream().map(x -> x.pick().order()).toList());
        assertEquals(1.0, r.get(0).rank());
        assertEquals(0.5, r.get(2).rank());
        // two rings under two goals stay: the same type may show more than once
        assertEquals(2, r.stream().filter(x -> x.pick().recipe().type().equals("ring")).count());
        // at ANY the mana leggings bought ones beat the owned: mana's top is now 9, the ring falls behind
        List<SuggestionBoard.Ranked> any = SuggestionBoard.ranked(all, CraftSearch.Tier.ANY, null, 105, p -> 105);
        assertEquals(4, any.get(1).pick().order());
        assertEquals(SuggestionBoard.Status.BUY, any.get(1).status());
        assertEquals(1, SuggestionBoard.rankedCounts(any).get(SuggestionBoard.Status.BUY));
    }

    @Test
    void everyBracketRanksAgainstItsOwnBestAndCanBePicked() {
        CraftData.Recipe high = recipe("ring", "jeweling", 105);
        CraftData.Recipe low = recipe("ring", "jeweling", 40);
        List<Suggestions.Suggestion> all = List.of(
                sugg(RAW, low, CraftSearch.Tier.OWN, 10, List.of(), 0, ing("A", 40)),     // the best at Lv 40: 1.0
                sugg(RAW, high, CraftSearch.Tier.OWN, 400, List.of(), 1, ing("B", 100)),  // the best at Lv 105: 1.0
                sugg(RAW, recipe("boots", "tailoring", 105), CraftSearch.Tier.OWN, 200, List.of(), 2));   // 0.5
        // a locked level never removes a row: it is a status
        List<SuggestionBoard.Ranked> r = SuggestionBoard.ranked(all, CraftSearch.Tier.OWN, null, 105, p -> 1);
        // equal ranks: the bracket nearest the player's first; a Lv 40 best is not pushed down by Lv 105 scores
        assertEquals(List.of(1, 0, 2), r.stream().map(x -> x.pick().order()).toList());
        assertEquals(SuggestionBoard.Status.LEVEL, r.get(0).status());
        assertEquals(List.of(0, 1, 2), SuggestionBoard.ranked(all, CraftSearch.Tier.OWN, null, 40, p -> 1).stream()
                .map(x -> x.pick().order()).toList());
        assertEquals(List.of(0), SuggestionBoard.ranked(all, CraftSearch.Tier.OWN, 40, 105, p -> 1).stream()
                .map(x -> x.pick().order()).toList());
    }

    @Test
    void consumablesFollowTheGear() {
        List<Suggestions.Suggestion> all = List.of(
                sugg(RAW, recipe("potion", "alchemism", 105), CraftSearch.Tier.OWN, 5, List.of(), 0),
                sugg(RAW, recipe("ring", "jeweling", 105), CraftSearch.Tier.OWN, 1, List.of(), 1));
        // the potion's score does not set the ring's measure: each is its kind's best (1.0), and the ring comes first
        List<SuggestionBoard.Ranked> r = SuggestionBoard.ranked(all, CraftSearch.Tier.OWN, null, 105, p -> 105);
        assertEquals(List.of(1, 0), r.stream().map(x -> x.pick().order()).toList());
        assertEquals(1.0, r.get(0).rank());
    }

    private static SetSuggestions.CraftSet set(Suggestions.GoalSet g, int step, CraftSearch.Tier t) {
        return new SetSuggestions.CraftSet(g, step, t, List.of(), Map.of(), new int[5], List.of());
    }
}
