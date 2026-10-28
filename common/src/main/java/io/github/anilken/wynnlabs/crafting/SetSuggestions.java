package io.github.anilken.wynnlabs.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * "Our suggestions" as whole sets: for a goal, one craft per slot from head to toe (the class's
 * weapon, the four armour pieces, two rings, a bracelet, a necklace) plus a potion, at the same
 * bracket step from the combat level and from the same search tier. Inside a set the bag and
 * bank are shared: an ingredient copy used by one piece is not counted for the next.
 */
public final class SetSuggestions {

    /** One piece of a set: the slot type, the recipe and tiers, and the layout found (null: nothing worth it). */
    public record Piece(String type, CraftData.Recipe recipe, int tier1, int tier2, CraftSearch.Layout layout) {}

    /**
     * A set: its goal, the bracket step (0 = the highest wearable), the search tier, the pieces in
     * slot order, the gear pieces' identifications added up, the highest requirement per skill,
     * and every copy that must be bought.
     */
    public record CraftSet(Suggestions.GoalSet goal, int step, CraftSearch.Tier tier, List<Piece> pieces,
                       Map<String, Craft.Range> totals, int[] maxRequirements, List<String> bought) {

        public boolean fromStock() {
            return bought.isEmpty();
        }

        /** The pieces' goal scores added up. */
        public double score() {
            double sc = 0;
            for (Piece p : pieces) {
                if (p.layout() != null) {
                    sc += p.layout().score();
                }
            }
            return sc;
        }

        public int found() {
            int n = 0;
            for (Piece p : pieces) {
                if (p.layout() != null) {
                    n++;
                }
            }
            return n;
        }
    }

    private static final CraftSearch.Tier[] TIERS = {CraftSearch.Tier.OWN, CraftSearch.Tier.OWN_PLUS_TWO, CraftSearch.Tier.ANY};

    private SetSuggestions() {}

    /**
     * One set per goal and group (from stock, or with buying): the strongest of the search tiers
     * and the two bracket steps, the higher bracket on a tie. The own-plus-two set of a goal is
     * weaker than its any-ingredient one by design (its leggings may be what the bag holds), and
     * the bracket below mostly repeats the top one, so showing them all only doubles the list.
     */
    public static List<CraftSet> strongest(List<CraftSet> sets) {
        Map<String, CraftSet> best = new LinkedHashMap<>();
        for (CraftSet s : sets) {
            best.merge(s.goal().key() + "|" + s.fromStock(), s, (a, b) -> {
                if (Math.abs(a.score() - b.score()) < 1e-9) {
                    return a.step() <= b.step() ? a : b;
                }
                return a.score() > b.score() ? a : b;
            });
        }
        return new ArrayList<>(best.values());
    }

    /** The slots of a set, the class's weapon first; the potion last. */
    public static List<String> slots(String weapon) {
        return List.of(weapon, "helmet", "chestplate", "leggings", "boots", "ring", "ring", "bracelet", "necklace", "potion");
    }

    public static List<CraftSet> run(String weapon, List<Suggestions.GoalSet> goals, int combatLevel, WhatCanICraft.Stock stock,
                                 List<Ingredient> all, Function<String, CraftSearch.Price> prices, long millisEach,
                                 CraftSearch.Budget budget, Consumer<CraftSet> each) {
        List<CraftSet> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Suggestions.GoalSet g : goals) {
            for (int step = 0; step < 2; step++) {
                for (CraftSearch.Tier tier : TIERS) {
                    if (budget.cancelled()) {
                        return out;
                    }
                    CraftSet s = one(weapon, g, step, tier, combatLevel, stock, all, prices, millisEach, budget);
                    if (s == null || s.found() < 3) {
                        continue;
                    }
                    StringBuilder key = new StringBuilder();
                    for (Piece p : s.pieces()) {
                        key.append(p.recipe() == null ? "-" : p.recipe().id()).append(':')
                                .append(p.layout() == null ? "-" : p.layout().slots().stream().map(Ingredient::name).sorted().toList())
                                .append(';');
                    }
                    if (!seen.add(key.toString())) {
                        continue;
                    }
                    out.add(s);
                    if (each != null) {
                        each.accept(s);
                    }
                }
            }
        }
        return out;
    }

    /**
     * One band's sets: for every goal and tier, each slot crafted at the band that starts at
     * {@code minLevel} (its {@code step} is that level). Any band, not only the player's.
     */
    public static void runBand(String weapon, List<Suggestions.GoalSet> goals, int minLevel, WhatCanICraft.Stock stock,
                               List<Ingredient> all, Function<String, CraftSearch.Price> prices, long millisEach,
                               CraftSearch.Budget budget, Consumer<CraftSet> each) {
        Set<String> seen = new HashSet<>();
        for (Suggestions.GoalSet g : goals) {
            for (CraftSearch.Tier tier : TIERS) {
                if (budget.cancelled()) {
                    return;
                }
                CraftSet s = build(weapon, g, minLevel, tier, type -> CraftData.find(type, minLevel), stock, all, prices, millisEach, budget);
                if (s == null || s.found() < 3) {
                    continue;
                }
                StringBuilder key = new StringBuilder(g.key());
                for (Piece p : s.pieces()) {
                    key.append(p.layout() == null ? "-" : p.layout().slots().stream().map(Ingredient::name).sorted().toList()).append(';');
                }
                if (seen.add(key.toString()) && each != null) {
                    each.accept(s);
                }
            }
        }
    }

    static CraftSet one(String weapon, Suggestions.GoalSet g, int step, CraftSearch.Tier tier, int combatLevel,
                    WhatCanICraft.Stock stock, List<Ingredient> all, Function<String, CraftSearch.Price> prices,
                    long millisEach, CraftSearch.Budget budget) {
        return build(weapon, g, step, tier, type -> {
            List<CraftData.Recipe> brackets = Suggestions.brackets(type, combatLevel);
            return brackets.isEmpty() ? null : brackets.get(Math.min(step, brackets.size() - 1));
        }, stock, all, prices, millisEach, budget);
    }

    private static CraftSet build(String weapon, Suggestions.GoalSet g, int step, CraftSearch.Tier tier,
                                  Function<String, CraftData.Recipe> recipeOf, WhatCanICraft.Stock stock, List<Ingredient> all,
                                  Function<String, CraftSearch.Price> prices, long millisEach, CraftSearch.Budget budget) {
        Map<String, Integer> left = new HashMap<>(stock.ingredients());
        List<Piece> pieces = new ArrayList<>();
        Map<String, Craft.Range> totals = new LinkedHashMap<>();
        int[] maxReq = new int[5];
        List<String> bought = new ArrayList<>();
        for (String type : slots(weapon)) {
            if (budget.cancelled()) {
                return null;
            }
            CraftData.Recipe r = recipeOf.apply(type);
            if (r == null) {
                pieces.add(new Piece(type, null, 1, 1, null));
                continue;
            }
            int t1 = Suggestions.tier(stock, r.materials().get(0));
            int t2 = Suggestions.tier(stock, r.materials().get(1));
            CraftSearch.Request q = new CraftSearch.Request(r, t1, t2, Craft.Speed.NORMAL, g.goal(), all, Map.copyOf(left),
                    stock.level(r.profession()), true, prices);
            CraftSearch.Layout l = Suggestions.best(q, tier, millisEach, budget, g.core());
            if (l == null) {
                pieces.add(new Piece(type, r, t1, t2, null));
                continue;
            }
            pieces.add(new Piece(type, r, t1, t2, l));
            bought.addAll(l.bought());
            // the copies this piece uses are no longer there for the next one
            for (Ingredient in : l.slots()) {
                if (!in.empty()) {
                    left.computeIfPresent(in.name(), (k, v) -> v > 1 ? v - 1 : null);
                }
            }
            if (!r.consumable()) {
                l.result().ids().forEach((id, v) -> totals.merge(id, v, (a, b) -> new Craft.Range(a.min() + b.min(), a.max() + b.max())));
                for (int s = 0; s < 5; s++) {
                    maxReq[s] = Math.max(maxReq[s], l.result().requirements()[s]);
                }
            }
        }
        return new CraftSet(g, step, tier, List.copyOf(pieces), totals, maxReq, List.copyOf(bought));
    }
}
