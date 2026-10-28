package io.github.anilken.wynnlabs.crafting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The suggestions page's shape, worked out from the runs' results: the best recipes of every goal
 * and item type at a chosen search tier (owned only, a few bought, the best) as one ranked list,
 * each with one status (ready, ingredients to buy, a profession level missing); for sets, the one
 * set shown for a goal, level band and tier.
 */
public final class SuggestionBoard {

    /** What stands between the player and crafting it: nothing, ingredients to buy, or a profession level. */
    public enum Status { READY, BUY, LEVEL }

    /** The tiers a suggestion can be picked at, in order. */
    public static final List<CraftSearch.Tier> TIERS = List.of(CraftSearch.Tier.OWN, CraftSearch.Tier.OWN_PLUS_TWO,
            CraftSearch.Tier.ANY);

    /** The tier shown until the player picks another: the best. */
    public static final CraftSearch.Tier DEFAULT_TIER = CraftSearch.Tier.ANY;

    private SuggestionBoard() {}

    /** The profession level a recipe and its ingredients need: the recipe's, or a higher ingredient's. */
    public static int needs(CraftData.Recipe r, List<Ingredient> slots) {
        int n = r.minLevel();
        for (Ingredient in : slots) {
            if (!in.empty()) {
                n = Math.max(n, in.level());
            }
        }
        return n;
    }

    /** Its status, {@code level} giving the player's level in a profession ("jeweling"). */
    public static Status status(CraftData.Recipe r, CraftSearch.Layout l, ToIntFunction<String> level) {
        if (needs(r, l.slots()) > level.applyAsInt(r.profession())) {
            return Status.LEVEL;
        }
        return l.bought().isEmpty() ? Status.READY : Status.BUY;
    }

    /**
     * The suggestion shown for a goal and type at a tier: the best scored of those found at that
     * tier or a lower one (a layout from owned ingredients is also one with a few bought; the runs
     * keep only the first of equal layouts); ties go to the one found first. Null when none.
     */
    public static Suggestions.Suggestion pick(List<Suggestions.Suggestion> all, String goal, String type, CraftSearch.Tier tier) {
        Suggestions.Suggestion best = null;
        for (Suggestions.Suggestion s : all) {
            if (!s.goal().key().equals(goal) || !s.recipe().type().equals(type) || s.tier().ordinal() > tier.ordinal()) {
                continue;
            }
            if (best == null || s.layout().score() > best.layout().score()
                    || s.layout().score() == best.layout().score() && s.order() < best.order()) {
                best = s;
            }
        }
        return best;
    }

    /** A suggestion in the ranked list: its status, the level it needs, and its rank (1 = its goal's best). */
    public record Ranked(Suggestions.Suggestion pick, Status status, int needs, double rank) {}

    /**
     * Every goal's best recipe for every item type and level bracket at a tier, as one list, best
     * first ({@code bracket}: only that bracket's first level, null for all). Scores of different
     * goals and brackets do not compare (a Lv 40 piece is not weighed against a Lv 117 one), so each
     * is divided by the best of its goal in its bracket (1 = that best), gear before consumables;
     * equal ranks put the bracket
     * nearest {@code home} (the player's) first, the higher of two as near, then the order found (the
     * class's goals first). The same ingredients for the same recipe under another goal are one
     * recipe: the better ranked stays.
     */
    public static List<Ranked> ranked(List<Suggestions.Suggestion> all, CraftSearch.Tier tier, Integer bracket, int home,
                                      ToIntFunction<String> level) {
        Map<String, Suggestions.Suggestion> best = new LinkedHashMap<>();
        for (Suggestions.Suggestion s : all) {
            if (s.tier().ordinal() > tier.ordinal() || bracket != null && s.recipe().minLevel() != bracket) {
                continue;
            }
            String k = s.goal().key() + "|" + s.recipe().type() + "|" + s.recipe().minLevel();
            Suggestions.Suggestion b = best.get(k);
            if (b == null || s.layout().score() > b.layout().score()
                    || s.layout().score() == b.layout().score() && s.order() < b.order()) {
                best.put(k, s);
            }
        }
        Map<String, Double> top = new LinkedHashMap<>();
        for (Suggestions.Suggestion s : best.values()) {
            top.merge(topKey(s), s.layout().score(), Math::max);
        }
        Map<String, Ranked> byLayout = new LinkedHashMap<>();
        for (Suggestions.Suggestion s : best.values()) {
            double t = top.get(topKey(s));
            double rank = t > 0 ? s.layout().score() / t : 0;
            List<String> names = new ArrayList<>();
            for (Ingredient in : s.layout().slots()) {
                if (!in.empty()) {
                    names.add(in.name());
                }
            }
            names.sort(String::compareTo);
            String k = s.recipe().id() + "|" + names;
            Ranked r = new Ranked(s, status(s.recipe(), s.layout(), level), needs(s.recipe(), s.layout().slots()), rank);
            Ranked old = byLayout.get(k);
            if (old == null || rank > old.rank()) {
                byLayout.put(k, r);
            }
        }
        List<Ranked> out = new ArrayList<>(byLayout.values());
        out.sort((a, b) -> {
            // gear first: a consumable's recipe is easily its goal's best (there is one per bracket), so it would lead
            boolean ca = a.pick().recipe().consumable();
            boolean cb = b.pick().recipe().consumable();
            if (ca != cb) {
                return ca ? 1 : -1;
            }
            if (a.rank() != b.rank()) {
                return Double.compare(b.rank(), a.rank());
            }
            int la = a.pick().recipe().minLevel();
            int lb = b.pick().recipe().minLevel();
            int da = Math.abs(la - home);
            int db = Math.abs(lb - home);
            return da != db ? Integer.compare(da, db) : la != lb ? Integer.compare(lb, la) : Integer.compare(a.pick().order(), b.pick().order());
        });
        return out;
    }

    /** What a score is measured against: its goal's best in its bracket, gear and consumables apart (a potion's score would dwarf a ring's). */
    private static String topKey(Suggestions.Suggestion s) {
        return s.goal().key() + "|" + s.recipe().minLevel() + "|" + s.recipe().consumable();
    }

    /** How many of a ranked list have each status (every status present). */
    public static Map<Status, Integer> rankedCounts(List<Ranked> list) {
        Map<Status, Integer> out = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            out.put(s, 0);
        }
        for (Ranked r : list) {
            out.merge(r.status(), 1, Integer::sum);
        }
        return out;
    }

    // ---- sets ----

    /** The level bands (steps) found for a goal's sets, lowest step (the highest band) first. */
    public static List<Integer> steps(List<SetSuggestions.CraftSet> sets, String goal) {
        List<Integer> out = new ArrayList<>();
        for (SetSuggestions.CraftSet s : sets) {
            if (s.goal().key().equals(goal) && !out.contains(s.step())) {
                out.add(s.step());
            }
        }
        out.sort(Integer::compare);
        return out;
    }

    /**
     * The set shown for a goal, band and tier: that one, else the nearest lower tier of the band
     * (an equal set found again is dropped by the run), else the band's lowest tier found. Null when
     * the band has none.
     */
    public static SetSuggestions.CraftSet pickSet(List<SetSuggestions.CraftSet> sets, String goal, int step, CraftSearch.Tier tier) {
        SetSuggestions.CraftSet below = null;
        SetSuggestions.CraftSet above = null;
        for (SetSuggestions.CraftSet s : sets) {
            if (!s.goal().key().equals(goal) || s.step() != step) {
                continue;
            }
            int t = s.tier().ordinal();
            if (t <= tier.ordinal()) {
                if (below == null || t > below.tier().ordinal()) {
                    below = s;
                }
            } else if (above == null || t < above.tier().ordinal()) {
                above = s;
            }
        }
        return below != null ? below : above;
    }

    /** A set's pieces by status (the pieces found; the second ring counts as a piece). */
    public static Map<Status, List<SetSuggestions.Piece>> pieces(SetSuggestions.CraftSet set, ToIntFunction<String> level) {
        Map<Status, List<SetSuggestions.Piece>> out = new EnumMap<>(Status.class);
        for (Status s : Status.values()) {
            out.put(s, new ArrayList<>());
        }
        for (SetSuggestions.Piece p : set.pieces()) {
            if (p.layout() != null) {
                out.get(status(p.recipe(), p.layout(), level)).add(p);
            }
        }
        return out;
    }

    /** The professions and levels a set's locked pieces need, highest first, each once ("jeweling" → 105). */
    public static Map<String, Integer> missingLevels(SetSuggestions.CraftSet set, ToIntFunction<String> level) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (SetSuggestions.Piece p : pieces(set, level).get(Status.LEVEL)) {
            out.merge(p.recipe().profession(), needs(p.recipe(), p.layout().slots()), Math::max);
        }
        List<Map.Entry<String, Integer>> l = new ArrayList<>(out.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        l.forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** The gear piece with the lowest durability (its lower end), or null when the set has none. */
    public static SetSuggestions.Piece weakest(SetSuggestions.CraftSet set) {
        SetSuggestions.Piece out = null;
        for (SetSuggestions.Piece p : set.pieces()) {
            if (p.layout() == null || p.recipe().consumable() || p.layout().result().durability() == null) {
                continue;
            }
            if (out == null || p.layout().result().durability().min() < out.layout().result().durability().min()) {
                out = p;
            }
        }
        return out;
    }
}
