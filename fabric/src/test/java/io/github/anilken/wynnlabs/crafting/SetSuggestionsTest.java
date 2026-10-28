package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetSuggestionsTest {

    /** A synthetic list where every ingredient may be used by every profession. */
    private static List<Ingredient> everyProfession() {
        List<Ingredient> out = new ArrayList<>();
        List<String> all = List.of("armouring", "tailoring", "weaponsmithing", "woodworking", "jeweling", "alchemism",
                "scribing", "cooking");
        for (Ingredient i : CraftSearchTest.synthetic(300, 11)) {
            out.add(new Ingredient(i.name(), i.tier(), i.level(), all, i.ids(), i.positions(), i.durability(), i.charges(),
                    i.duration(), i.requirements()));
        }
        return out;
    }

    @Test
    void wholeSetsWithSharedStock() {
        List<Ingredient> all = everyProfession();
        // one copy of a strong spell ingredient: only one piece of an own-stock set may use it
        String one = all.get(5).name();
        WhatCanICraft.Stock stock = new WhatCanICraft.Stock(Map.of(one, 1), Map.of(), Map.of());
        List<SetSuggestions.CraftSet> sets = SetSuggestions.run("relik", Suggestions.goalSets(List.of("spell", "health")), 60,
                stock, all, n -> null, 60, new CraftSearch.Budget(60_000), null);
        assertFalse(sets.isEmpty());
        for (SetSuggestions.CraftSet s : sets) {
            assertEquals(10, s.pieces().size());
            assertEquals(List.of("relik", "helmet", "chestplate", "leggings", "boots", "ring", "ring", "bracelet", "necklace",
                    "potion"), s.pieces().stream().map(SetSuggestions.Piece::type).toList());
            assertTrue(s.found() >= 3);
            int uses = 0;
            for (SetSuggestions.Piece p : s.pieces()) {
                if (p.layout() != null) {
                    assertTrue(p.recipe().maxLevel() <= 60);
                    uses += (int) p.layout().slots().stream().filter(i -> i.name().equals(one)).count();
                }
            }
            if (s.tier() == CraftSearch.Tier.OWN) {
                assertTrue(uses <= 1, "the single copy is used once in the whole set: " + uses);
                assertTrue(s.fromStock());
            }
            // the totals are the gear pieces' ranges added up
            Map<String, Craft.Range> sum = new HashMap<>();
            for (SetSuggestions.Piece p : s.pieces()) {
                if (p.layout() != null && !p.recipe().consumable()) {
                    p.layout().result().ids().forEach((k, v) -> sum.merge(k, v,
                            (a, b) -> new Craft.Range(a.min() + b.min(), a.max() + b.max())));
                }
            }
            assertEquals(sum, s.totals());
        }
    }

    /**
     * An any-ingredient set piece is the single suggestion for its slot under the same goal, caps
     * and floor: the set never settles for weak leggings when nothing forces it.
     */
    @Test
    void anySetPiecesMatchTheSingleSuggestion() {
        List<Ingredient> all = CraftGoldenTest.ingredients();
        WhatCanICraft.Stock stock = new WhatCanICraft.Stock(Map.of(), Map.of(), Map.of());
        Suggestions.GoalSet g = Suggestions.goalSets(List.of("rawspell"), 107, new int[] {0, 0, 40, 0, 0}).get(0);
        SetSuggestions.CraftSet set = SetSuggestions.one("relik", g, 0, CraftSearch.Tier.ANY, 107, stock, all, n -> null, 3_000,
                new CraftSearch.Budget(120_000));
        for (SetSuggestions.Piece p : set.pieces()) {
            if (p.recipe() == null) {
                continue;
            }
            CraftSearch.Request q = new CraftSearch.Request(p.recipe(), p.tier1(), p.tier2(), Craft.Speed.NORMAL, g.goal(), all,
                    Map.of(), 200, true, n -> null);
            CraftSearch.Layout single = Suggestions.best(q, CraftSearch.Tier.ANY, 3_000, new CraftSearch.Budget(10_000), g.core());
            double mine = p.layout() == null ? 0 : p.layout().score();
            double alone = single == null ? 0 : single.score();
            assertEquals(alone, mine, 1e-6, p.type() + " in the set vs alone");
        }
    }

    @Test
    void oneSetPerGoalAndGroupTheStrongest() {
        Suggestions.GoalSet g = Suggestions.goalSets(List.of("rawspell")).get(0);
        Suggestions.GoalSet h = Suggestions.goalSets(List.of("health")).get(0);
        CraftData.Recipe r = CraftData.byId("Helmet105-107");
        SetSuggestions.CraftSet weak = set(g, 0, CraftSearch.Tier.OWN_PLUS_TWO, r, 10, List.of("x"));
        SetSuggestions.CraftSet strong = set(g, 0, CraftSearch.Tier.ANY, r, 30, List.of("x", "y"));
        SetSuggestions.CraftSet lower = set(g, 1, CraftSearch.Tier.ANY, r, 30, List.of("x", "y"));
        SetSuggestions.CraftSet own = set(g, 0, CraftSearch.Tier.OWN, r, 5, List.of());
        SetSuggestions.CraftSet tank = set(h, 1, CraftSearch.Tier.ANY, r, 8, List.of("z"));
        assertEquals(List.of(strong, own, tank), SetSuggestions.strongest(List.of(weak, strong, lower, own, tank)),
                "the strongest buying set (the higher bracket on a tie), the stock set, the other goal");
    }

    private static SetSuggestions.CraftSet set(Suggestions.GoalSet g, int step, CraftSearch.Tier tier, CraftData.Recipe r,
                                               double score, List<String> bought) {
        CraftSearch.Layout l = new CraftSearch.Layout(Craft.slots(), null, score, bought, 0, List.of(), List.of());
        return new SetSuggestions.CraftSet(g, step, tier, List.of(new SetSuggestions.Piece("helmet", r, 3, 3, l)), Map.of(),
                new int[5], bought);
    }
}
