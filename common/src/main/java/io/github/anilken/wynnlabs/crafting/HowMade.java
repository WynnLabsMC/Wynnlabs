package io.github.anilken.wynnlabs.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * How a crafted item was most likely made. The game keeps no ingredients on a crafted item, so
 * this searches the calculator ({@link Craft}) backwards: the layouts (recipe, material tiers and
 * six slots) whose craft holds every value the item shows, cheapest first, or the closest ones
 * when none does.
 *
 * <p>The recipe is the item type's bracket that ends at the item's level. Identifications,
 * charges, requirements and the ingredients' duration and durability changes all add up per slot,
 * so the search is a depth-first walk over the free slots with a lower bound per value: what the
 * remaining slots can still add is known per slot, and a branch that can no longer fit (or, when
 * nothing fits, misses more than the kept answers) is cut. Ingredients with position effects are
 * placed first (none, then one, up to {@link #MAX_METAS}, every placement once); the slots left
 * over then hold the rest, and slots with the same effectiveness take ingredients in list order,
 * so one multiset is tried once. Ingredients that add the same at a slot are tried once, the
 * cheapest. Material tiers only move the duration, durability and health ranges: they are picked
 * per layout at the end. The first pass lets an ingredient bring an identification the item lacks
 * only where its slot rounds it to 0; the second lets ingredients cancel each other; when neither
 * fits, the closest layouts are kept instead. Everything runs under a time budget.
 */
public final class HowMade {

    /** Layouts kept: the best one and the others shown under it. */
    public static final int KEEP = 6;

    /** The most ingredients with position effects placed in one layout. */
    static final int MAX_METAS = 3;

    /** An identification the item shows: its key ("spellDamage"), shown name, unit ("%", "/5s", "") and value. */
    public record Stat(String key, String shown, String unit, int value) {}

    /**
     * What a crafted item shows. {@code type} is the craft type ("scroll", "helmet"); values that
     * do not apply or are unknown are -1 (charges and duration for consumables, durability and
     * health for gear); {@code requirements} are the skill points in strength, dexterity,
     * intelligence, defence, agility order (0: none), null for consumables.
     */
    public record Target(String name, String type, int level, List<Stat> ids, int charges, int duration, int durability,
                         int health, int[] requirements) {

        public boolean consumable() {
            return Types.consumable(type);
        }
    }

    /** A line of "does it match". */
    public enum Kind { LEVEL, ID, EXTRA, CHARGES, DURATION, DURABILITY, HEALTH, REQUIREMENT }

    /**
     * One value against a layout: what it is ({@code key}: the identification, or the skill index
     * for a requirement), the item's value, the layout's range (null: the layout gives none) and
     * whether the range holds the value.
     */
    public record Row(Kind kind, String key, String shown, String unit, int value, Craft.Range range, boolean ok) {}

    /** A layout found, its craft, its rows, how many rows miss and by how much. */
    public record Found(CraftData.Recipe recipe, int tier1, int tier2, List<Ingredient> slots, Craft.Result result,
                       List<Row> rows, int misses, double distance) {

        public boolean exact() {
            return misses == 0;
        }

        /** The placed ingredients' names in slot order, null for an empty slot. */
        public List<String> names() {
            List<String> out = new ArrayList<>(Craft.SLOTS);
            for (Ingredient i : slots) {
                out.add(i == null || i.empty() ? null : i.name());
            }
            return out;
        }
    }

    /** What the window says about the answer. */
    public enum Verdict { EXACT, POSSIBLE, CLOSEST, NONE }

    /**
     * The answer: the recipe (null when no recipe of the type holds the level), the layouts
     * ranked (exact ones first, cheapest first), how many exact layouts were met, and whether the
     * search ran to its end.
     */
    public record Answer(CraftData.Recipe recipe, List<Found> layouts, int exactCount, boolean complete) {

        public static final Answer EMPTY = new Answer(null, List.of(), 0, true);

        public Verdict verdict() {
            if (layouts.isEmpty()) {
                return Verdict.NONE;
            }
            if (!layouts.get(0).exact()) {
                return Verdict.CLOSEST;
            }
            return exactCount > 1 ? Verdict.POSSIBLE : Verdict.EXACT;
        }

        public Found best() {
            return layouts.isEmpty() ? null : layouts.get(0);
        }
    }

    /** A cost: the emeralds of what has a price and how many things have none. */
    public record Cost(long emeralds, int unknown, int priced) {

        public Cost plus(Cost o) {
            return new Cost(emeralds + o.emeralds, unknown + o.unknown, priced + o.priced);
        }
    }

    private HowMade() {}

    // ---- the recipe and the rows ---------------------------------------------------------------

    /** The recipe of a type for a crafted item's level: the bracket ending there, else one holding it. */
    public static CraftData.Recipe recipe(String type, int level) {
        CraftData.Recipe holding = null;
        for (CraftData.Recipe r : CraftData.ofType(type)) {
            if (r.maxLevel() == level) {
                return r;
            }
            if (holding == null && r.minLevel() <= level && level <= r.maxLevel()) {
                holding = r;
            }
        }
        return holding;
    }

    /** Every value of the item against one craft: level, identifications, then the type's own values. */
    public static List<Row> rows(Target t, CraftData.Recipe recipe, Craft.Result r) {
        List<Row> out = new ArrayList<>();
        out.add(new Row(Kind.LEVEL, "level", "", "", t.level(), new Craft.Range(recipe.minLevel(), recipe.maxLevel()),
                recipe.minLevel() <= t.level() && t.level() <= recipe.maxLevel()));
        Set<String> shown = new HashSet<>();
        for (Stat s : t.ids()) {
            shown.add(s.key());
            Craft.Range range = r.ids().get(s.key());
            boolean ok = range == null ? s.value() == 0 : range.min() <= s.value() && s.value() <= range.max();
            out.add(new Row(Kind.ID, s.key(), s.shown(), s.unit(), s.value(), range, ok));
        }
        for (Map.Entry<String, Craft.Range> e : r.ids().entrySet()) {
            Craft.Range range = e.getValue();
            if (!shown.contains(e.getKey()) && (range.min() > 0 || range.max() < 0)) {
                out.add(new Row(Kind.EXTRA, e.getKey(), e.getKey(), "", 0, range, false));
            }
        }
        if (t.consumable()) {
            if (t.charges() >= 0) {
                out.add(new Row(Kind.CHARGES, "charges", "", "", t.charges(), new Craft.Range(r.charges(), r.charges()),
                        r.charges() == t.charges()));
            }
            if (t.duration() >= 0 && r.duration() != null) {
                out.add(new Row(Kind.DURATION, "duration", "", "s", t.duration(), r.duration(), holds(r.duration(), t.duration())));
            }
        } else {
            if (t.durability() >= 0 && r.durability() != null) {
                out.add(new Row(Kind.DURABILITY, "durability", "", "", t.durability(), r.durability(),
                        holds(r.durability(), t.durability())));
            }
            if (t.health() > 0 && r.health() != null) {
                out.add(new Row(Kind.HEALTH, "health", "", "", t.health(), r.health(), holds(r.health(), t.health())));
            }
            if (t.requirements() != null) {
                for (int s = 0; s < 5; s++) {
                    int want = t.requirements()[s];
                    int got = Math.max(0, r.requirements()[s]);
                    if (want > 0 || got > 0) {
                        out.add(new Row(Kind.REQUIREMENT, String.valueOf(s), "", "", want, new Craft.Range(got, got), want == got));
                    }
                }
            }
        }
        return out;
    }

    private static boolean holds(Craft.Range r, int v) {
        return r.min() <= v && v <= r.max();
    }

    /** How far a value is outside a range, scaled to the value: 0 inside, else 1 plus up to 1 more. */
    static double cost(long gap, int value) {
        return gap <= 0 ? 0 : 1 + Math.min(1.0, gap / (Math.abs(value) + 5.0));
    }

    private static Found found(Target t, CraftData.Recipe recipe, int t1, int t2, List<Ingredient> slots) {
        Craft.Result res = Craft.craft(recipe, t1, t2, Craft.Speed.NORMAL, slots);
        List<Row> rows = rows(t, recipe, res);
        int misses = 0;
        double dist = 0;
        for (Row row : rows) {
            if (!row.ok()) {
                misses++;
                Craft.Range r = row.range() == null ? Craft.Range.ZERO : row.range();
                long gap = row.kind() == Kind.REQUIREMENT || row.kind() == Kind.CHARGES ? Math.abs(r.min() - row.value())
                        : Math.max(0, Math.max(r.min() - (long) row.value(), row.value() - (long) r.max()));
                dist += cost(Math.max(1, gap), row.value());
            }
        }
        return new Found(recipe, t1, t2, List.copyOf(slots), res, rows, misses, dist);
    }

    // ---- costs -----------------------------------------------------------------------------

    /** The placed ingredients at market prices. */
    public static Cost ingredientsCost(List<Ingredient> slots, Function<String, Long> price) {
        long e = 0;
        int unknown = 0;
        int priced = 0;
        for (Ingredient i : slots) {
            if (i == null || i.empty()) {
                continue;
            }
            Long p = price.apply(i.name());
            if (p == null) {
                unknown++;
            } else {
                e += p;
                priced++;
            }
        }
        return new Cost(e, unknown, priced);
    }

    /** The recipe's two materials at their tiers, priced by {@link Shopping#materialKey}. */
    public static Cost materialsCost(CraftData.Recipe recipe, int tier1, int tier2, Function<String, Long> price) {
        long e = 0;
        int unknown = 0;
        int priced = 0;
        for (int m = 0; m < 2 && m < recipe.materials().size(); m++) {
            CraftData.Material mat = recipe.materials().get(m);
            Long p = price.apply(Shopping.materialKey(mat.shown(), m == 0 ? tier1 : tier2));
            if (p == null) {
                unknown++;
            } else {
                e += p * mat.amount();
                priced++;
            }
        }
        return new Cost(e, unknown, priced);
    }

    // ---- the search ------------------------------------------------------------------------

    /**
     * Finds the layouts within {@code millis}. {@code price} gives a market price by name or
     * material key (null: unknown), read on the caller's side before; {@code progress} (may be
     * null) gets the answer so far now and then while it runs.
     */
    public static Answer solve(Target t, List<Ingredient> all, Function<String, Long> price, long millis,
                               CraftSearch.Budget budget, Consumer<Answer> progress) {
        CraftData.Recipe recipe = recipe(t.type(), t.level());
        if (recipe == null) {
            return Answer.EMPTY;
        }
        Search s = new Search(t, recipe, price == null ? n -> null : price, progress);
        boolean complete = s.run(all, millis, budget);
        return s.answer(complete);
    }

    /** The same without a parent budget or progress (tests). */
    public static Answer solve(Target t, List<Ingredient> all, Function<String, Long> price, long millis) {
        return solve(t, all, price, millis, new CraftSearch.Budget(millis + 1000), null);
    }

    /** A kept leaf: the slots, tiers, score and ingredient price. */
    private record Leaf(Ingredient[] slots, int t1, int t2, double score, long price, String key) {}

    private static final class Search {
        final Target t;
        final CraftData.Recipe recipe;
        final boolean consumable;
        final int band;
        final Function<String, Long> price;
        final Consumer<Answer> progress;

        /** Tier pairs {t1, t2}, the base duration or durability range per pair (no ingredient changes), and health per pair. */
        final int[][] pairs = new int[9][];
        final Craft.Range[] base = new Craft.Range[9];
        final Craft.Range[] health = new Craft.Range[9];
        final long[] pairPrice = new long[9];
        final boolean[] pairPriced = new boolean[9];
        /** The value the tier-dependent row aims at (duration or durability), -1 when none. */
        final int tierTarget;

        // rows: identification rows first (lo/hi ranges), then plain sums
        String[] idKeys;
        int[] idTarget;
        int nIds;
        /** Sum rows: charges (consumable), the five requirements (gear), the duration or durability changes. */
        int nSums;
        long[] sumLow;
        long[] sumHigh;
        int[] sumValue;
        int rowCharges = -1;
        int rowReq = -1;
        int rowMods = -1;

        final List<Leaf> kept = new ArrayList<>();
        final Set<String> keys = new HashSet<>();
        int exactCount;
        long lastProgress;
        boolean changed;
        CraftSearch.Budget budget;
        long nodes;

        Search(Target t, CraftData.Recipe recipe, Function<String, Long> price, Consumer<Answer> progress) {
            this.t = t;
            this.recipe = recipe;
            this.consumable = recipe.consumable();
            this.band = Craft.band(recipe);
            this.price = price;
            this.progress = progress;
            this.tierTarget = consumable ? t.duration() : t.durability();
            Ingredient blank = new Ingredient("-", 0, 0, List.of(), Map.of(), new int[6], 0, 0, 0, new int[5]);
            int i = 0;
            for (int a = 1; a <= 3; a++) {
                for (int b = 1; b <= 3; b++) {
                    pairs[i] = new int[] {a, b};
                    Craft.Result r = Craft.craft(recipe, a, b, Craft.Speed.NORMAL, Craft.slots(blank));
                    base[i] = consumable ? r.duration() : r.durability();
                    health[i] = consumable ? null : r.health();
                    Cost c = materialsCost(recipe, a, b, price);
                    pairPrice[i] = c.emeralds();
                    pairPriced[i] = c.unknown() == 0;
                    i++;
                }
            }
        }

        // ---- set-up per pass ----

        /** One candidate as the search reads it: its rows' contributions per effectiveness, cached. */
        final class Cand {
            final Ingredient ing;
            final long price;
            final boolean priced;
            final boolean meta;
            /** Identification row indexes and their stats. */
            final int[] idRows;
            final Ingredient.Stat[] stats;
            final Map<Integer, int[]> byEff = new HashMap<>();

            Cand(Ingredient ing, Long price, Map<String, Integer> rowOf) {
                this.ing = ing;
                this.price = price == null ? 0 : price;
                this.priced = price != null;
                this.meta = ing.meta();
                List<Integer> rows = new ArrayList<>();
                List<Ingredient.Stat> st = new ArrayList<>();
                for (Map.Entry<String, Ingredient.Stat> e : ing.ids().entrySet()) {
                    Integer r = rowOf.get(e.getKey());
                    if (r != null) {
                        rows.add(r);
                        st.add(e.getValue());
                    }
                }
                idRows = rows.stream().mapToInt(Integer::intValue).toArray();
                stats = st.toArray(new Ingredient.Stat[0]);
            }

            /** Whether each identification it brings that the item lacks can come out as 0 at an effectiveness. */
            boolean vanishes(int eff) {
                for (Map.Entry<String, Ingredient.Stat> e : ing.ids().entrySet()) {
                    if (!own.contains(e.getKey())) {
                        Craft.Range r = Craft.scaled(e.getValue(), eff);
                        if (r.min() > 0 || r.max() < 0) {
                            return false;
                        }
                    }
                }
                return true;
            }

            /** {low per id row (sparse, in idRows order), high per id row, then each sum row} at an effectiveness. */
            int[] at(int eff) {
                int[] v = byEff.get(eff);
                if (v == null) {
                    int n = idRows.length;
                    v = new int[2 * n + nSums];
                    for (int k = 0; k < n; k++) {
                        Craft.Range r = Craft.scaled(stats[k], eff);
                        v[k] = r.min();
                        v[n + k] = r.max();
                    }
                    if (rowCharges >= 0) {
                        v[2 * n + rowCharges] = ing.charges();
                    }
                    if (rowReq >= 0) {
                        for (int s = 0; s < 5; s++) {
                            v[2 * n + rowReq + s] = Craft.scaledRequirement(ing.requirements()[s], eff);
                        }
                    }
                    if (rowMods >= 0) {
                        v[2 * n + rowMods] = consumable ? ing.duration() : ing.durability();
                    }
                    byEff.put(eff, v);
                }
                return v;
            }
        }

        List<Cand> metas;
        List<Cand> plain;
        /** The item's own identification keys. */
        final Set<String> own = new HashSet<>();
        /**
         * First pass: an ingredient may only sit where every identification it brings that the
         * item lacks can round to 0 by itself; the second lets them cancel each other.
         */
        boolean selfOnly;
        /** The plain candidates a slot of an effectiveness may hold (first pass), cached per pass. */
        final Map<Integer, List<Cand>> byEff = new HashMap<>();
        final Map<Integer, long[][]> boundsByEff = new HashMap<>();

        /**
         * Rows for a pass: the item's identifications, plus (second pass) any a candidate brings,
         * aiming at 0, then the sums. In the first pass those never miss: each slot can round its
         * own to 0.
         */
        void rows(List<Ingredient> cands, boolean selfOnly) {
            this.selfOnly = selfOnly;
            byEff.clear();
            boundsByEff.clear();
            Map<String, Integer> target = new LinkedHashMap<>();
            for (Stat s : t.ids()) {
                target.put(s.key(), s.value());
            }
            if (!selfOnly) {
                for (Ingredient i : cands) {
                    for (String k : i.ids().keySet()) {
                        target.putIfAbsent(k, 0);
                    }
                }
            }
            nIds = target.size();
            idKeys = target.keySet().toArray(new String[0]);
            idTarget = target.values().stream().mapToInt(Integer::intValue).toArray();
            List<long[]> sums = new ArrayList<>();
            List<Integer> values = new ArrayList<>();
            rowCharges = rowReq = rowMods = -1;
            if (consumable && t.charges() >= 0) {
                rowCharges = sums.size();
                // charges = max(1, band + changes)
                long want = t.charges() - (long) band;
                sums.add(t.charges() <= 1 ? new long[] {Long.MIN_VALUE / 4, 1 - band} : new long[] {want, want});
                values.add(t.charges());
            }
            if (!consumable && t.requirements() != null) {
                rowReq = sums.size();
                for (int s = 0; s < 5; s++) {
                    int want = t.requirements()[s];
                    sums.add(want <= 0 ? new long[] {Long.MIN_VALUE / 4, 0} : new long[] {want, want});
                    values.add(want);
                }
            }
            if (tierTarget >= 0 && base[0] != null) {
                rowMods = sums.size();
                long lo = Long.MAX_VALUE;
                long hi = Long.MIN_VALUE;
                for (int p = 0; p < 9; p++) {
                    if (base[p] == null) {
                        continue;
                    }
                    lo = Math.min(lo, tierTarget - (long) base[p].max());
                    hi = Math.max(hi, tierTarget - (long) base[p].min());
                }
                sums.add(new long[] {lo, hi});
                values.add(tierTarget);
            }
            nSums = sums.size();
            sumLow = new long[nSums];
            sumHigh = new long[nSums];
            sumValue = new int[nSums];
            for (int k = 0; k < nSums; k++) {
                sumLow[k] = sums.get(k)[0];
                sumHigh[k] = sums.get(k)[1];
                sumValue[k] = values.get(k);
            }
            Map<String, Integer> rowOf = new HashMap<>();
            for (int k = 0; k < nIds; k++) {
                rowOf.put(idKeys[k], k);
            }
            metas = new ArrayList<>();
            plain = new ArrayList<>();
            for (Ingredient i : cands) {
                Long p = price.apply(i.name());
                Cand c = new Cand(i, p, rowOf);
                (c.meta ? metas : plain).add(c);
            }
            // the ones that bring the item's own identifications first: good answers early, so more is cut
            Comparator<Cand> useful = Comparator.comparingInt((Cand c) -> -(int) c.ing.ids().keySet().stream().filter(own::contains).count())
                    .thenComparingInt(c -> c.ing.ids().size())
                    .thenComparingLong(c -> c.price);
            plain.sort(useful);
            metas.sort(useful);
        }

        // ---- running ----

        /**
         * Exact layouts first: one ingredient of each slot at a time, its foreign identifications
         * rounded away, then cancelling each other; only when neither finds one, the closest
         * layouts in the time left. True when the exact search ran to its end.
         */
        boolean run(List<Ingredient> all, long millis, CraftSearch.Budget parent) {
            long t0 = System.nanoTime();
            this.budget = parent;
            exactOnly = true;
            if (consumable) {
                leafEmpty();
            }
            List<Ingredient> usable = new ArrayList<>();
            for (Ingredient i : all) {
                if (!i.empty() && i.usableBy(recipe.profession()) && i.level() <= recipe.maxLevel()) {
                    usable.add(i);
                }
            }
            usable = distinct(usable);
            t.ids().forEach(s -> own.add(s.key()));
            boolean foreign = false;
            for (Ingredient i : usable) {
                foreign |= !own.containsAll(i.ids().keySet());
            }
            boolean done = pass(usable, true, new CraftSearch.Budget(millis * 45 / 100, parent));
            if (exactCount == 0 && foreign && !parent.over()) {
                done &= pass(usable, false, new CraftSearch.Budget(millis * 30 / 100, parent));
            }
            if (exactCount > 0 || parent.over()) {
                return done && !parent.cancelled();
            }
            exactOnly = false;
            if (consumable) {
                leafEmpty();
            }
            long left = millis - (System.nanoTime() - t0) / 1_000_000;
            pass(usable, true, new CraftSearch.Budget(Math.max(millis / 5, left), parent));
            return done && !parent.cancelled();
        }

        /** Ingredients that act alike for this recipe kept once: the cheapest priced, else the lowest. */
        List<Ingredient> distinct(List<Ingredient> in) {
            Map<String, Ingredient> best = new LinkedHashMap<>();
            for (Ingredient i : in) {
                String sig = signature(i);
                Ingredient have = best.get(sig);
                if (have == null || cheaper(i, have)) {
                    best.put(sig, i);
                }
            }
            return new ArrayList<>(best.values());
        }

        private boolean cheaper(Ingredient a, Ingredient b) {
            Long pa = price.apply(a.name());
            Long pb = price.apply(b.name());
            if (pa != null && pb != null) {
                return pa < pb;
            }
            if (pa != null || pb != null) {
                return pa != null;
            }
            return a.level() < b.level() || a.level() == b.level() && a.name().compareTo(b.name()) < 0;
        }

        private String signature(Ingredient i) {
            StringBuilder sb = new StringBuilder();
            new TreeMap<>(i.ids()).forEach((k, v) -> sb.append(k).append(':').append(v.min()).append(':').append(v.max()).append(','));
            sb.append('|').append(Arrays.toString(i.positions()));
            if (consumable) {
                sb.append('|').append(i.charges()).append('|').append(i.duration());
            } else {
                sb.append('|').append(i.durability()).append('|').append(Arrays.toString(i.requirements()));
            }
            return sb.toString();
        }

        /** One pass over a candidate list: no position effects, then one, two, up to {@link #MAX_METAS}. True when it ran to its end. */
        boolean pass(List<Ingredient> cands, boolean selfOnly, CraftSearch.Budget b) {
            this.budget = b;
            rows(cands, selfOnly);
            Set<String> seen = new HashSet<>();
            for (int count = 0; count <= MAX_METAS; count++) {
                if (!metas(count, 0, 0, -1, new int[count], new Cand[count], seen)) {
                    return false;
                }
            }
            return true;
        }

        /** Every placement of {@code count} metas: candidates in list order, a repeated one in slot order. */
        private boolean metas(int count, int k, int fromCand, int prevSlot, int[] slots, Cand[] cands, Set<String> seen) {
            if (k == count) {
                return config(slots, cands, seen);
            }
            for (int c = fromCand; c < metas.size(); c++) {
                for (int s = 0; s < Craft.SLOTS; s++) {
                    if (c == fromCand && k > 0 && cands[k - 1] == metas.get(c) && s <= prevSlot || taken(slots, k, s)) {
                        continue;
                    }
                    slots[k] = s;
                    cands[k] = metas.get(c);
                    if (!metas(count, k + 1, c, s, slots, cands, seen)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static boolean taken(int[] slots, int k, int s) {
            for (int i = 0; i < k; i++) {
                if (slots[i] == s) {
                    return true;
                }
            }
            return false;
        }

        // per configuration
        int[] free;
        int[] freeEff;
        long[][] sufLo;      // [k][id row] the least the slots from k on can add to an id's low end
        long[][] sufHi;      // [k][id row] the most they can add to its high end
        long[][] sufMin;     // [k][sum row]
        long[][] sufMax;
        long[] curLo;
        long[] curHi;
        long[] curSum;
        final Ingredient[] at = new Ingredient[Craft.SLOTS];
        int[] prevIdx;
        /** The candidates each free slot may hold. */
        List<?>[] lists;

        /** The plain candidates for a slot of an effectiveness. */
        List<Cand> listAt(int eff) {
            return byEff.computeIfAbsent(eff, e -> {
                // ingredients that add the same at this effectiveness are tried once: the cheapest
                Map<String, Cand> best = new LinkedHashMap<>();
                for (Cand c : plain) {
                    if (selfOnly && !c.vanishes(e)) {
                        continue;
                    }
                    int[] v = c.at(e);
                    int m = c.idRows.length;
                    StringBuilder k = new StringBuilder();
                    for (int j = 0; j < m; j++) {
                        if (v[j] != 0 || v[m + j] != 0) {
                            k.append(c.idRows[j]).append(':').append(v[j]).append(':').append(v[m + j]).append(',');
                        }
                    }
                    k.append('|');
                    for (int j = 2 * m; j < v.length; j++) {
                        k.append(v[j]).append(',');
                    }
                    String key = k.toString();
                    Cand have = best.get(key);
                    if (have == null || c.priced && (!have.priced || c.price < have.price)) {
                        best.put(key, c);
                    }
                }
                return List.copyOf(best.values());
            });
        }

        /** {lo, hi, min, max}: the least and most one slot of an effectiveness adds to each row (0 for empty). */
        long[][] slotBounds(int eff) {
            return boundsByEff.computeIfAbsent(eff, e -> {
                long[] lo = new long[nIds];
                long[] hi = new long[nIds];
                long[] mn = new long[nSums];
                long[] mx = new long[nSums];
                for (Cand c : listAt(e)) {
                    int[] v = c.at(e);
                    int m = c.idRows.length;
                    for (int j = 0; j < m; j++) {
                        lo[c.idRows[j]] = Math.min(lo[c.idRows[j]], v[j]);
                        hi[c.idRows[j]] = Math.max(hi[c.idRows[j]], v[m + j]);
                    }
                    for (int j = 0; j < nSums; j++) {
                        mn[j] = Math.min(mn[j], v[2 * m + j]);
                        mx[j] = Math.max(mx[j], v[2 * m + j]);
                    }
                }
                return new long[][] {lo, hi, mn, mx};
            });
        }

        /** Places the metas, works out the free slots' effectiveness, and walks them. False when the budget ran out. */
        boolean config(int[] metaSlots, Cand[] metaCands, Set<String> seen) {
            Ingredient[] placed = new Ingredient[Craft.SLOTS];
            Arrays.fill(placed, Ingredient.NONE);
            for (int k = 0; k < metaSlots.length; k++) {
                placed[metaSlots[k]] = metaCands[k].ing;
            }
            int[] eff = Craft.effectiveness(Arrays.asList(placed));
            List<Integer> freeList = new ArrayList<>();
            List<String> metaKey = new ArrayList<>();
            for (int s = 0; s < Craft.SLOTS; s++) {
                if (placed[s].empty()) {
                    freeList.add(s);
                } else {
                    metaKey.add(placed[s].name() + "@" + eff[s]);
                }
            }
            freeList.sort(Comparator.comparingInt((Integer s) -> eff[s]).thenComparingInt(s -> s));
            metaKey.sort(null);
            StringBuilder key = new StringBuilder(String.join(",", metaKey)).append('|');
            for (int s : freeList) {
                key.append(eff[s]).append(',');
            }
            if (!seen.add(key.toString())) {
                return !budget.over();
            }
            if (selfOnly) {
                for (int k = 0; k < metaSlots.length; k++) {
                    if (!metaCands[k].vanishes(eff[metaSlots[k]])) {
                        return !budget.over();
                    }
                }
            }
            free = freeList.stream().mapToInt(Integer::intValue).toArray();
            freeEff = new int[free.length];
            for (int k = 0; k < free.length; k++) {
                freeEff[k] = eff[free[k]];
            }
            curLo = new long[nIds];
            curHi = new long[nIds];
            curSum = new long[nSums];
            long metaPrice = 0;
            for (int k = 0; k < metaSlots.length; k++) {
                apply(metaCands[k], eff[metaSlots[k]], 1);
                metaPrice += metaCands[k].price;
            }
            // what each free slot can add at most and least, summed from the end
            int n = free.length;
            sufLo = new long[n + 1][nIds];
            sufHi = new long[n + 1][nIds];
            sufMin = new long[n + 1][nSums];
            sufMax = new long[n + 1][nSums];
            lists = new List<?>[n];
            for (int k = n - 1; k >= 0; k--) {
                lists[k] = listAt(freeEff[k]);
                long[][] sb = slotBounds(freeEff[k]);
                long[] lo = sb[0];
                long[] hi = sb[1];
                long[] mn = sb[2];
                long[] mx = sb[3];
                for (int j = 0; j < nIds; j++) {
                    sufLo[k][j] = sufLo[k + 1][j] + lo[j];
                    sufHi[k][j] = sufHi[k + 1][j] + hi[j];
                }
                for (int j = 0; j < nSums; j++) {
                    sufMin[k][j] = sufMin[k + 1][j] + mn[j];
                    sufMax[k][j] = sufMax[k + 1][j] + mx[j];
                }
            }
            Arrays.fill(at, Ingredient.NONE);
            for (int k = 0; k < metaSlots.length; k++) {
                at[metaSlots[k]] = metaCands[k].ing;
            }
            prevIdx = new int[n];
            double b = bound(0, cap());
            if (b > cap() || b == cap() && metaPrice >= capPrice()) {
                return !budget.over();
            }
            return walk(0, metaSlots.length, metaPrice);
        }

        void apply(Cand c, int eff, int sign) {
            int[] v = c.at(eff);
            int m = c.idRows.length;
            for (int j = 0; j < m; j++) {
                curLo[c.idRows[j]] += sign * v[j];
                curHi[c.idRows[j]] += sign * v[m + j];
            }
            for (int j = 0; j < nSums; j++) {
                curSum[j] += sign * v[2 * m + j];
            }
        }

        /** The least score any layout below this node can have (the duration or durability row by its widest reach). */
        double bound(int k, double cap) {
            double s = 0;
            long[] lo = sufLo[k];
            long[] hi = sufHi[k];
            for (int j = 0; j < nIds; j++) {
                long v = idTarget[j];
                long gap = Math.max(curLo[j] + lo[j] - v, v - (curHi[j] + hi[j]));
                if (gap > 0) {
                    s += cost(gap, idTarget[j]);
                    if (s > cap) {
                        return s;
                    }
                }
            }
            long[] mn = sufMin[k];
            long[] mx = sufMax[k];
            for (int j = 0; j < nSums; j++) {
                long a = curSum[j] + mn[j];
                long b = curSum[j] + mx[j];
                long gap = Math.max(sumLow[j] - b, a - sumHigh[j]);
                if (gap > 0) {
                    s += cost(gap, sumValue[j]);
                    if (s > cap) {
                        return s;
                    }
                }
            }
            return s;
        }

        /** Whether only exact layouts are looked for (any miss cuts the branch). */
        boolean exactOnly;

        double cap() {
            if (exactOnly) {
                return 0;
            }
            return kept.size() < KEEP ? Double.MAX_VALUE : kept.get(kept.size() - 1).score;
        }

        long capPrice() {
            return kept.size() < KEEP ? Long.MAX_VALUE : kept.get(kept.size() - 1).price;
        }

        /** Fills free slot {@code k} on; false when the budget ran out. */
        boolean walk(int k, int count, long partPrice) {
            if ((++nodes & 1023) == 0) {
                if (budget.over()) {
                    return false;
                }
                maybeProgress();
            }
            if (k == free.length) {
                if (count > 0 || !consumable) {
                    leaf(count, partPrice);
                }
                return true;
            }
            @SuppressWarnings("unchecked")
            List<Cand> list = (List<Cand>) lists[k];
            int n = list.size();
            boolean sameAsPrev = k > 0 && freeEff[k] == freeEff[k - 1];
            int start = sameAsPrev ? prevIdx[k - 1] : 0;
            for (int i = start; i <= n; i++) {
                prevIdx[k] = i;
                if (i == n) {
                    // empty: once the rest are empty too (empties last in a same-effectiveness run)
                    at[free[k]] = Ingredient.NONE;
                    double b = bound(k + 1, cap());
                    if (b < cap() || b == cap() && partPrice < capPrice()) {
                        if (!walk(k + 1, count, partPrice)) {
                            return false;
                        }
                    }
                    continue;
                }
                Cand c = list.get(i);
                apply(c, freeEff[k], 1);
                at[free[k]] = c.ing;
                long p = partPrice + c.price;
                double b = bound(k + 1, cap());
                boolean go = b < cap() || b == cap() && p < capPrice();
                if (go && !walk(k + 1, count + 1, p)) {
                    apply(c, freeEff[k], -1);
                    at[free[k]] = Ingredient.NONE;
                    return false;
                }
                apply(c, freeEff[k], -1);
                at[free[k]] = Ingredient.NONE;
            }
            return true;
        }

        /** A full layout: its rows but the tier one are exact now; the tiers are picked to fit that one best. */
        void leaf(int count, long partPrice) {
            double s = 0;
            int skip = rowMods;
            for (int j = 0; j < nIds; j++) {
                long v = idTarget[j];
                long gap = Math.max(curLo[j] - v, v - curHi[j]);
                s += cost(gap, idTarget[j]);
            }
            for (int j = 0; j < nSums; j++) {
                if (j == skip) {
                    continue;
                }
                long gap = Math.max(sumLow[j] - curSum[j], curSum[j] - sumHigh[j]);
                s += cost(gap, sumValue[j]);
            }
            int bestPair = -1;
            double bestTier = Double.MAX_VALUE;
            for (int p = 0; p < 9; p++) {
                double ts = 0;
                if (rowMods >= 0 && base[p] != null) {
                    long lo = base[p].min() + curSum[rowMods];
                    long hi = base[p].max() + curSum[rowMods];
                    ts += cost(Math.max(lo - tierTarget, tierTarget - hi), tierTarget);
                }
                if (!consumable && t.health() > 0 && health[p] != null) {
                    ts += cost(Math.max((long) health[p].min() - t.health(), t.health() - (long) health[p].max()), t.health());
                }
                if (bestPair < 0 || ts < bestTier || ts == bestTier && betterPair(p, bestPair)) {
                    bestPair = p;
                    bestTier = ts;
                }
            }
            s += bestTier;
            if (s == 0) {
                exactCount++;
            }
            long total = partPrice + pairPrice[bestPair];
            keep(at.clone(), pairs[bestPair][0], pairs[bestPair][1], s, total);
        }

        private boolean betterPair(int a, int b) {
            // among tier pairs that fit as well: one the market prices over one it has no listing for
            if (pairPriced[a] != pairPriced[b]) {
                return pairPriced[a];
            }
            if (pairPriced[a] && pairPriced[b] && pairPrice[a] != pairPrice[b]) {
                return pairPrice[a] < pairPrice[b];
            }
            return pairs[a][0] + pairs[a][1] < pairs[b][0] + pairs[b][1];
        }

        /** A consumable with no ingredient (it heals, three charges): its best tier pair. */
        void leafEmpty() {
            int best = -1;
            double bestScore = 0;
            for (int p = 0; p < 9; p++) {
                double d = found(t, recipe, pairs[p][0], pairs[p][1], Craft.slots()).distance();
                if (best < 0 || d < bestScore || d == bestScore && betterPair(p, best)) {
                    best = p;
                    bestScore = d;
                }
            }
            if (bestScore == 0) {
                exactCount++;
            }
            keep(new Ingredient[] {Ingredient.NONE, Ingredient.NONE, Ingredient.NONE, Ingredient.NONE, Ingredient.NONE,
                    Ingredient.NONE}, pairs[best][0], pairs[best][1], bestScore, pairPrice[best]);
        }

        void keep(Ingredient[] slots, int t1, int t2, double score, long total) {
            if (exactOnly && score > 0) {
                return;
            }
            StringBuilder key = new StringBuilder();
            for (Ingredient i : slots) {
                key.append(i.name()).append(';');
            }
            String k = key.toString();
            if (keys.contains(k)) {
                return;
            }
            if (kept.size() >= KEEP) {
                Leaf last = kept.get(kept.size() - 1);
                if (score > last.score || score == last.score && total >= last.price) {
                    return;
                }
            }
            Leaf leaf = new Leaf(slots, t1, t2, score, total, k);
            int at = 0;
            while (at < kept.size() && (kept.get(at).score < score || kept.get(at).score == score && kept.get(at).price <= total)) {
                at++;
            }
            kept.add(at, leaf);
            keys.add(k);
            if (kept.size() > KEEP) {
                keys.remove(kept.remove(kept.size() - 1).key);
            }
            changed = true;
        }

        void maybeProgress() {
            if (progress == null || !changed) {
                return;
            }
            long now = System.nanoTime();
            if (now - lastProgress < 400_000_000L) {
                return;
            }
            lastProgress = now;
            changed = false;
            progress.accept(answer(false));
        }

        Answer answer(boolean complete) {
            List<Found> out = new ArrayList<>();
            for (Leaf l : kept) {
                out.add(found(t, recipe, l.t1, l.t2, Arrays.asList(l.slots)));
            }
            Function<Found, Long> cost = f -> ingredientsCost(f.slots(), price).emeralds()
                    + materialsCost(recipe, f.tier1(), f.tier2(), price).emeralds();
            out.sort(Comparator.comparingInt(Found::misses).thenComparingDouble(Found::distance).thenComparing(cost));
            return new Answer(recipe, List.copyOf(out), exactCount, complete);
        }
    }
}
