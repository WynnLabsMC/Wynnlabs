package io.github.anilken.wynnlabs.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * "Our suggestions": ready recipes for the player, many of them. Every item type that fits the
 * class, at the highest bracket the player can use at their combat level and the one below
 * (whatever their profession levels: a locked recipe is marked, not hidden), for each goal
 * set (in the class's order), made from own stock and as the strongest with up to two bought.
 * The same layout found twice is kept once, and a layout that adds nothing to its goal is
 * dropped. Each search gets a short budget; suggestions are handed over as they come.
 *
 * <p>The goal sets follow the recipes players share: one or two stats pushed hard, gear that
 * keeps about 100 durability, requirements a build can meet (a couple of skills up to
 * about 130), and whatever the layout gives up (health, steal, walk speed) counted against it.
 */
public final class Suggestions {

    /** The durability gear keeps (shared recipes keep 100 to 160); when nothing fits, {@link #FALLBACK_FLOOR}. */
    public static final int FLOOR = 90;
    public static final int FALLBACK_FLOOR = 50;
    /** Poison stacks on fragile pieces: its goal settles for this. */
    public static final int POISON_FLOOR = 40;
    /** The highest requirement per skill a suggestion asks, before the level's own limit. */
    public static final int CAP = 130;
    /** The five requirements added up: about two skills high, as shared recipes ask. */
    public static final int TOTAL_CAP = 240;

    /** The goal sets of melee and of spell builds. */
    static final Set<String> MELEE = Set.of("rawmelee", "melee", "attackspeed");
    static final Set<String> SPELL = Set.of("rawspell", "spell", "mana");

    /** The elements in skill order (strength, dexterity, intelligence, defence, agility). */
    static final List<String> ELEMENTS = List.of("earth", "thunder", "water", "fire", "air");

    /** What a requirement point on a gear piece costs, in percent of damage. */
    static final double REQUIREMENT_COST = 0.15;
    /** A layout made to bring its goal's core keeps at least this share of the goal's best score. */
    static final double CORE_KEEP = 0.75;
    /** What a layout gives up counts this many times its plain value: players weigh a loss above a gain. */
    static final double LOSS = 2.0;

    /**
     * A named goal ("rawspell", "thunder" …) the suggestions are made for, and its core: the
     * identifications a suggestion must bring at least one of (poison for poison).
     */
    public record GoalSet(String key, CraftSearch.Goal goal, Set<String> core) {}

    /**
     * One suggestion: the goal set it is for, the recipe and tiers, the layout found, whether it
     * needs nothing bought, and the search tier it came from.
     */
    public record Suggestion(GoalSet goal, CraftData.Recipe recipe, int tier1, int tier2, CraftSearch.Layout layout,
                             CraftSearch.Tier tier, int order) {

        public boolean fromStock() {
            return layout.bought().isEmpty();
        }
    }

    private static final CraftSearch.Tier[] TIERS = {CraftSearch.Tier.OWN, CraftSearch.Tier.OWN_PLUS_TWO, CraftSearch.Tier.ANY};

    private Suggestions() {}

    /**
     * Runs every search one after another within {@code budget}; {@code each} gets every kept
     * suggestion as soon as it is found.
     */
    public static List<Suggestion> run(List<String> types, List<GoalSet> goals, int combatLevel, WhatCanICraft.Stock stock,
                                       List<Ingredient> all, Function<String, CraftSearch.Price> prices,
                                       long millisEach, CraftSearch.Budget budget, Consumer<Suggestion> each) {
        List<Suggestion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int order = 0;
        for (GoalSet g : goals) {
            for (String type : types) {
                for (CraftData.Recipe r : brackets(type, combatLevel)) {
                    int t1 = tier(stock, r.materials().get(0));
                    int t2 = tier(stock, r.materials().get(1));
                    for (CraftSearch.Tier tier : TIERS) {
                        if (budget.cancelled()) {
                            return out;
                        }
                        CraftSearch.Request q = new CraftSearch.Request(r, t1, t2, Craft.Speed.NORMAL, g.goal(), all,
                                stock.ingredients(), stock.level(r.profession()), true, prices);
                        CraftSearch.Layout l = best(q, tier, millisEach, budget, g.core());
                        if (l == null) {
                            continue;
                        }
                        // the same ingredients one bracket lower are the same suggestion: keep the first (higher)
                        String key = r.type() + "|" + l.slots().stream().map(Ingredient::name).sorted().toList();
                        if (!seen.add(key)) {
                            continue;
                        }
                        Suggestion s = new Suggestion(g, r, t1, t2, l, tier, order++);
                        out.add(s);
                        if (each != null) {
                            each.accept(s);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * One level bracket's suggestions (every type that has a recipe from {@code minLevel}, every
     * goal, every search tier), numbered from {@code firstOrder}; returns the next number. The page
     * runs the brackets one after another, the player's first, so nothing is limited to a level.
     */
    public static int runBracket(List<String> types, List<GoalSet> goals, int minLevel, WhatCanICraft.Stock stock,
                                 List<Ingredient> all, Function<String, CraftSearch.Price> prices, long millisEach,
                                 CraftSearch.Budget budget, int firstOrder, Consumer<Suggestion> each) {
        int order = firstOrder;
        Set<String> seen = new HashSet<>();
        for (GoalSet g : goals) {
            for (String type : types) {
                CraftData.Recipe r = CraftData.find(type, minLevel);
                if (r == null) {
                    continue;
                }
                int t1 = tier(stock, r.materials().get(0));
                int t2 = tier(stock, r.materials().get(1));
                for (CraftSearch.Tier tier : TIERS) {
                    if (budget.cancelled()) {
                        return order;
                    }
                    CraftSearch.Request q = new CraftSearch.Request(r, t1, t2, Craft.Speed.NORMAL, g.goal(), all,
                            stock.ingredients(), stock.level(r.profession()), true, prices);
                    CraftSearch.Layout l = best(q, tier, millisEach, budget, g.core());
                    if (l == null) {
                        continue;
                    }
                    String key = g.key() + "|" + type + "|" + l.slots().stream().map(Ingredient::name).sorted().toList();
                    if (!seen.add(key)) {
                        continue;
                    }
                    Suggestion s = new Suggestion(g, r, t1, t2, l, tier, order++);
                    if (each != null) {
                        each.accept(s);
                    }
                }
            }
        }
        return order;
    }

    /** Every level bracket's first level, lowest first (the same for every type). */
    public static List<Integer> bracketLevels() {
        java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
        for (CraftData.Recipe r : CraftData.all()) {
            out.add(r.minLevel());
        }
        return new ArrayList<>(out);
    }

    /**
     * The brackets in the order worth searching: the highest a combat level can wear first, then
     * outward (one below, one above, …), so the useful ones fill in first.
     */
    public static List<Integer> bracketOrder(int combatLevel) {
        List<Integer> all = bracketLevels();
        int home = 0;
        for (int i = 0; i < all.size(); i++) {
            CraftData.Recipe r = CraftData.find("ring", all.get(i));
            if (r != null && r.maxLevel() <= Math.max(3, combatLevel)) {
                home = i;
            }
        }
        List<Integer> out = new ArrayList<>();
        for (int d = 0; out.size() < all.size(); d++) {
            if (home - d >= 0) {
                out.add(all.get(home - d));
            }
            if (d > 0 && home + d < all.size()) {
                out.add(all.get(home + d));
            }
        }
        return out;
    }

    /**
     * The best layout worth suggesting for a request, or null: one that adds to its goal and
     * brings something of its core. When nothing keeps the goal's durability floor, the fallback floor.
     */
    static CraftSearch.Layout best(CraftSearch.Request q, CraftSearch.Tier tier, long millis, CraftSearch.Budget budget,
                                   Set<String> core) {
        CraftSearch.Layout l = cored(q, tier, millis, budget, core);
        if (l == null && q.goal().durabilityFloor() > FALLBACK_FLOOR && !q.recipe().consumable() && !budget.cancelled()) {
            l = cored(with(q, q.goal().withFloor(FALLBACK_FLOOR)), tier, millis, budget, core);
        }
        return l;
    }

    /**
     * The best layout bringing some of the core: when the goal's best has none (mana regeneration
     * won the raw spell goal), the search again with the core stats counting twice. That one is
     * kept only while it scores {@link #CORE_KEEP} of the goal's best: raw melee boots bought
     * with health, regeneration and strength are not shown when a melee percent piece does far better.
     */
    private static CraftSearch.Layout cored(CraftSearch.Request q, CraftSearch.Tier tier, long millis, CraftSearch.Budget budget,
                                            Set<String> core) {
        CraftSearch.Layout top = CraftSearch.search(q, tier, new CraftSearch.Budget(millis, budget)).best();
        CraftSearch.Layout l = worth(top, core);
        if (l == null && !budget.cancelled()) {
            CraftSearch.Goal g = q.goal();
            Map<String, Double> w = new LinkedHashMap<>(g.weights());
            w.replaceAll((id, x) -> core.contains(id) ? 2 * x : x);
            CraftSearch.Goal focused = new CraftSearch.Goal(java.util.Collections.unmodifiableMap(w), g.pick(), g.noNegative(),
                    g.caps(), g.durabilityFloor(), g.penalties(), g.totalCap(), g.requirementCost());
            CraftSearch.Layout f = worth(CraftSearch.search(with(q, focused), tier, new CraftSearch.Budget(millis, budget)).best(), core);
            // shown with the goal's own score
            l = f == null ? null : new CraftSearch.Layout(f.slots(), f.result(), g.score(f.slots(), !q.recipe().consumable()),
                    f.bought(), f.cost(), f.unpriced(), f.oldPrices());
            l = worth(l, core);
            if (l != null && top != null && l.score() < CORE_KEEP * top.score()) {
                l = null;
            }
        }
        return l;
    }

    private static CraftSearch.Request with(CraftSearch.Request q, CraftSearch.Goal g) {
        return new CraftSearch.Request(q.recipe(), q.tier1(), q.tier2(), q.speed(), g, q.ingredients(), q.owned(),
                q.professionLevel(), q.allowLocked(), q.prices());
    }

    /** A layout worth showing: it adds to its goal and brings some of its core. */
    private static CraftSearch.Layout worth(CraftSearch.Layout l, Set<String> core) {
        if (l == null || l.score() <= 0 || l.slots().stream().allMatch(Ingredient::empty)) {
            return null;
        }
        for (String id : core) {
            Craft.Range v = l.result().ids().get(id);
            if (v != null && (cost(id) ? v.mean() < 0 : v.mean() > 0)) {
                return l;
            }
        }
        return null;
    }

    /** A spell's cost: less is the gain. */
    static boolean cost(String id) {
        return id.endsWith("SpellCost");
    }

    /** The spell cost identifications: each spell's percent and raw cut. */
    static final List<String> COSTS = List.of("1stSpellCost", "2ndSpellCost", "3rdSpellCost", "4thSpellCost",
            "raw1stSpellCost", "raw2ndSpellCost", "raw3rdSpellCost", "raw4thSpellCost");

    /** The material tier to craft with: the best the player has enough of, else three stars (bought). */
    static int tier(WhatCanICraft.Stock stock, CraftData.Material m) {
        int t = WhatCanICraft.bestTier(stock, m);
        return t == 0 ? 3 : t;
    }

    /** The highest bracket of a type usable at a combat level (its top level at most that), and the one below it. */
    static List<CraftData.Recipe> brackets(String type, int combatLevel) {
        List<CraftData.Recipe> rs = CraftData.ofType(type);
        if (rs.isEmpty()) {
            return List.of();
        }
        int top = -1;
        for (int i = 0; i < rs.size(); i++) {
            if (rs.get(i).maxLevel() <= Math.max(3, combatLevel)) {
                top = i;
            }
        }
        if (top < 0) {
            return List.of();
        }
        return top == 0 ? List.of(rs.get(0)) : List.of(rs.get(top), rs.get(top - 1));
    }

    /**
     * The goal sets in the order shown: casters lead with spell goals, the others with melee;
     * then the elements of the build's two strongest skills, then the rest. {@code points} are
     * the player's skill points (strength … agility), or null when unknown.
     */
    public static List<String> order(boolean caster, int[] points) {
        List<String> out = new ArrayList<>(caster
                ? List.of("rawspell", "spell", "mana", "spellcost")
                : List.of("melee", "attackspeed", "rawmelee"));
        if (points != null) {
            Integer[] idx = {0, 1, 2, 3, 4};
            Arrays.sort(idx, (a, b) -> Integer.compare(points[b], points[a]));
            for (int i = 0; i < 2; i++) {
                if (points[idx[i]] > 0) {
                    out.add(ELEMENTS.get(idx[i]));
                }
            }
        }
        out.addAll(caster
                ? List.of("health", "poison", "speed", "rawmelee", "lifesteal", "loot", "xp")
                : List.of("health", "poison", "speed", "rawspell", "mana", "lifesteal", "loot", "xp"));
        for (String e : ELEMENTS) {
            if (!out.contains(e)) {
                out.add(e);
            }
        }
        out.addAll(caster ? List.of("melee", "attackspeed") : List.of("spell", "spellcost"));
        return out;
    }

    /** A combined goal's key prefix: "combo:rawspell+mana+spellcost". */
    public static final String COMBO = "combo:";

    /** The goals a combined key is made of ("combo:a+b" → [a, b]); a plain goal is itself. */
    public static List<String> parts(String key) {
        return key.startsWith(COMBO) ? List.of(key.substring(COMBO.length()).split("\\+")) : List.of(key);
    }

    public static String comboKey(List<String> parts) {
        return COMBO + String.join("+", parts);
    }

    /**
     * Several goals worked for together: each goal's weights scaled so its strongest core stat
     * counts 1 (one goal does not drown the others) and added up; a piece must bring some of any
     * goal's core; the strictest durability floor and total cap; a stat is charged as little as any
     * of the goals charges it. Null when a part is not a goal.
     */
    public static GoalSet combined(List<String> parts, int level, int[] points) {
        List<GoalSet> sets = goalSets(parts, level, points);
        if (sets.size() != parts.size() || sets.isEmpty()) {
            return null;
        }
        Map<String, Double> w = new LinkedHashMap<>();
        Map<String, Double> pen = new LinkedHashMap<>();
        Set<String> core = new HashSet<>();
        int floor = 0;
        int total = Integer.MAX_VALUE;
        for (GoalSet g : sets) {
            double top = 0;
            for (Map.Entry<String, Double> e : g.goal().weights().entrySet()) {
                if (g.core().contains(e.getKey())) {
                    top = Math.max(top, Math.abs(e.getValue()));
                }
            }
            double scale = top > 0 ? 1 / top : 1;
            g.goal().weights().forEach((id, v) -> w.merge(id, v * scale, Double::sum));
            g.goal().penalties().forEach((id, v) -> pen.merge(id, v, Math::min));
            core.addAll(g.core());
            floor = Math.max(floor, g.goal().durabilityFloor());
            total = Math.min(total, g.goal().totalCap());
        }
        // a stat one goal wants is not charged as given up by another
        w.forEach((id, v) -> pen.computeIfPresent(id, (k, p) -> Math.min(p, (LOSS - 1) * Math.abs(v))));
        CraftSearch.Goal first = sets.get(0).goal();
        return new GoalSet(comboKey(parts), new CraftSearch.Goal(java.util.Collections.unmodifiableMap(w), CraftSearch.Pick.MEAN,
                Set.of(), first.caps().clone(), floor, Map.copyOf(pen), total, first.requirementCost()), Set.copyOf(core));
    }

    /**
     * The combinations offered for a class, from what players build: casters a raw spell build that
     * keeps its mana up, spell percent with mana, their element with raw spell, a tank with life
     * steal; melee classes melee with attack speed and life steal, raw melee with attack speed, their
     * element with melee, and a tank. The element is the build's strongest skill's (none while unknown).
     */
    public static List<List<String>> presets(boolean caster, int[] points) {
        String element = null;
        if (points != null) {
            int best = -1;
            for (int i = 0; i < 5; i++) {
                if (points[i] > 0 && (best < 0 || points[i] > points[best])) {
                    best = i;
                }
            }
            element = best < 0 ? null : ELEMENTS.get(best);
        }
        List<List<String>> out = new ArrayList<>();
        if (caster) {
            out.add(List.of("rawspell", "mana", "spellcost"));
            out.add(List.of("spell", "mana"));
            if (element != null) {
                out.add(List.of(element, "rawspell"));
            }
            out.add(List.of("spell", "speed"));
        } else {
            out.add(List.of("melee", "attackspeed", "lifesteal"));
            out.add(List.of("rawmelee", "attackspeed"));
            if (element != null) {
                out.add(List.of(element, "melee"));
            }
            out.add(List.of("poison", "attackspeed"));
        }
        out.add(List.of("health", "lifesteal"));
        return out;
    }

    /** The goal sets at level 100 with no skill points known (tests and previews). */
    public static List<GoalSet> goalSets(List<String> order) {
        return goalSets(order, 100, null);
    }

    /**
     * The goal sets for a combat level. Every goal counts in percent of damage: raw damage is
     * worth one percent per {@link #rawPerPercent} points, and everything else a piece can give
     * up is priced the same way ({@link #values}) and charged when an ingredient brings it below
     * zero. Requirements are capped by {@link #caps} and each point costs {@link #REQUIREMENT_COST};
     * gear keeps {@link #FLOOR} durability ({@link #POISON_FLOOR} for poison).
     */
    public static List<GoalSet> goalSets(List<String> order, int level, int[] points) {
        double r = rawPerPercent(level);
        int[] caps = caps(level, points);
        Map<String, Double> values = values(level);
        List<GoalSet> out = new ArrayList<>();
        for (String k : order) {
            Map<String, Double> w = weights(k, r, level, values);
            if (w == null) {
                continue;
            }
            Map<String, Double> pen = new LinkedHashMap<>();
            values.forEach((id, val) -> pen.put(id, LOSS * val));
            // a goal stat lost costs its goal weight plus as much again
            w.forEach((id, val) -> pen.put(id, (LOSS - 1) * val));
            if (ELEMENTS.contains(k)) {
                pen.put(k + "Defence", LOSS * 0.3);    // the build's own element: its defence given up hurts more
            }
            // the other kind of damage is what shared recipes give up first: melee pieces drop spell
            // damage and the other way round, so it costs a fifth of its plain value
            List<String> other = MELEE.contains(k) ? List.of("spellDamage", "rawSpellDamage", "elementalSpellDamage")
                    : SPELL.contains(k) ? List.of("mainAttackDamage", "rawMainAttackDamage") : List.of();
            for (String id : other) {
                pen.computeIfPresent(id, (x, val) -> val / 5);
            }
            if (k.equals("health")) {
                // a tank piece gives up damage of every kind, as shared tank recipes do
                for (String id : values.keySet()) {
                    if (id.contains("Damage") || id.equals("poison")) {
                        pen.put(id, pen.get(id) / 5);
                    }
                }
            }
            if (MELEE.contains(k) || SPELL.contains(k)) {
                for (String e : ELEMENTS) {
                    String id = MELEE.contains(k) ? e + "SpellDamage" : e + "MainAttackDamage";
                    pen.computeIfPresent(id, (x, val) -> val / 5);
                    String raw = MELEE.contains(k) ? "raw" + cap(e) + "SpellDamage" : "raw" + cap(e) + "MainAttackDamage";
                    pen.computeIfPresent(raw, (x, val) -> val / 5);
                }
            }
            pen.keySet().removeIf(Suggestions::cost);
            int floor = k.equals("poison") ? POISON_FLOOR : FLOOR;
            out.add(new GoalSet(k, new CraftSearch.Goal(java.util.Collections.unmodifiableMap(w), CraftSearch.Pick.MEAN,
                    Set.of(), caps.clone(), floor, Map.copyOf(pen), Math.min(TOTAL_CAP, 2 * caps[0]), REQUIREMENT_COST),
                    core(k)));
        }
        return out;
    }

    /** What a goal set's suggestion must bring at least one of. */
    static Set<String> core(String key) {
        Set<String> c = new HashSet<>();
        switch (key) {
            // raw goals lean on raw damage but take a percent piece that does better: either kind is the core
            case "rawspell", "spell" -> {
                c.add("rawSpellDamage");
                ELEMENTS.forEach(e -> c.add("raw" + cap(e) + "SpellDamage"));
                c.add("spellDamage");
                c.add("elementalSpellDamage");
                ELEMENTS.forEach(e -> c.add(e + "SpellDamage"));
            }
            case "rawmelee", "melee" -> {
                c.add("rawMainAttackDamage");
                ELEMENTS.forEach(e -> c.add("raw" + cap(e) + "MainAttackDamage"));
                c.add("mainAttackDamage");
                ELEMENTS.forEach(e -> c.add(e + "MainAttackDamage"));
            }
            case "attackspeed" -> c.add("rawAttackSpeed");
            case "poison" -> c.add("poison");
            case "health" -> c.add("rawHealth");
            case "mana" -> c.addAll(List.of("manaRegen", "manaSteal"));
            case "spellcost" -> c.addAll(COSTS);
            case "speed" -> c.add("walkSpeed");
            case "lifesteal" -> c.add("lifeSteal");
            case "loot" -> c.addAll(List.of("lootBonus", "lootQuality"));
            case "xp" -> c.addAll(List.of("xpBonus", "combatExperience"));
            default -> {
                String u = cap(key);
                c.addAll(List.of(key + "Damage", "raw" + u + "Damage", "raw" + u + "SpellDamage",
                        "raw" + u + "MainAttackDamage", key + "SpellDamage", key + "MainAttackDamage", "elementalDamage"));
            }
        }
        return Set.copyOf(c);
    }

    /** Raw points worth one percent of the same kind: 1 below level 15, rising to 8 at the top. */
    static double rawPerPercent(int level) {
        return Math.max(1, Math.min(8, level / 15.0));
    }

    /** The requirement cap per skill: {@link #CAP}, lower while the level gives fewer points, higher where the player has more. */
    static int[] caps(int level, int[] points) {
        int c = Math.min(CAP, Math.max(0, 2 * (level - 1)));
        int[] out = new int[5];
        for (int s = 0; s < 5; s++) {
            out[s] = points == null ? c : Math.max(c, points[s]);
        }
        return out;
    }

    /**
     * What one point of each identification is worth to any build, in percent of damage: what a
     * layout pays when it gives the stat up. Health is priced against the level's health (a
     * thousand at the top weighs about seven percent), skill points at a quarter percent each,
     * an attack speed tier at twenty (melee crafts are built around tiers).
     */
    static Map<String, Double> values(int level) {
        double r = rawPerPercent(level);
        double hp = 0.0075 * 120.0 / Math.max(20, level);
        Map<String, Double> v = new LinkedHashMap<>();
        v.put("rawHealth", hp);
        v.put("healthRegenRaw", hp * 1.5);
        v.put("healthRegen", 0.5);
        v.put("healingEfficiency", 0.3);
        v.put("lifeSteal", hp * 2);
        v.put("manaSteal", 2.0);
        v.put("manaRegen", 2.0);
        v.put("rawMaxMana", 0.3);
        v.put("walkSpeed", 0.3);
        v.put("sprint", 0.1);
        v.put("sprintRegen", 0.1);
        v.put("rawAttackSpeed", 20.0);
        v.put("spellDamage", 1.0);
        v.put("mainAttackDamage", 1.0);
        v.put("damage", 1.0);
        v.put("elementalDamage", 0.5);
        v.put("elementalSpellDamage", 0.5);
        v.put("neutralDamage", 0.5);
        v.put("elementalDefence", 0.3);
        v.put("rawSpellDamage", 1 / r);
        v.put("rawMainAttackDamage", 1 / r);
        v.put("rawNeutralDamage", 1 / r);
        v.put("rawNeutralMainAttackDamage", 1 / r);
        v.put("poison", 2.0 / Math.max(10, level));
        v.put("lootBonus", 0.2);
        v.put("combatExperience", 0.2);
        for (String s : List.of("Strength", "Dexterity", "Intelligence", "Defence", "Agility")) {
            v.put("raw" + s, 0.25);
        }
        for (String e : ELEMENTS) {
            String u = cap(e);
            v.put(e + "Damage", 0.5);
            v.put(e + "SpellDamage", 0.5);
            v.put(e + "MainAttackDamage", 0.5);
            v.put(e + "Defence", 0.1);
            v.put("raw" + u + "Damage", 0.5 / r);
            v.put("raw" + u + "SpellDamage", 0.5 / r);
            v.put("raw" + u + "MainAttackDamage", 0.5 / r);
        }
        return v;
    }

    /**
     * A goal set's weights in percent of damage, or null for an unknown key: its own stats count
     * more than their plain value (raw at {@code 1.5 / r} a point on the raw goals), with the
     * damage that helps every build alongside.
     */
    private static Map<String, Double> weights(String key, double r, int level, Map<String, Double> v) {
        Map<String, Double> w = new LinkedHashMap<>();
        switch (key) {
            case "rawspell" -> {
                w.put("rawSpellDamage", 1.5 / r);
                for (String e : ELEMENTS) {
                    w.put("raw" + cap(e) + "SpellDamage", 1.5 / r);
                }
                w.put("spellDamage", 1.5);
                w.put("elementalSpellDamage", 1.5);
                for (String e : ELEMENTS) {
                    w.put(e + "SpellDamage", 1.5);
                }
                w.put("manaRegen", 2.0);
                shared(w, v);
            }
            case "spell" -> {
                w.put("spellDamage", 2.0);
                w.put("elementalSpellDamage", 2.0);
                for (String e : ELEMENTS) {
                    w.put(e + "SpellDamage", 2.0);
                }
                w.put("rawSpellDamage", 1 / r);
                w.put("manaRegen", 2.0);
                shared(w, v);
            }
            case "rawmelee" -> {
                w.put("rawMainAttackDamage", 1.5 / r);
                for (String e : ELEMENTS) {
                    w.put("raw" + cap(e) + "MainAttackDamage", 1.5 / r);
                }
                w.put("mainAttackDamage", 1.5);
                for (String e : ELEMENTS) {
                    w.put(e + "MainAttackDamage", 1.5);
                }
                w.put("rawAttackSpeed", 30.0);
                shared(w, v);
            }
            case "melee" -> {
                w.put("mainAttackDamage", 2.0);
                for (String e : ELEMENTS) {
                    w.put(e + "MainAttackDamage", 2.0);
                }
                w.put("rawMainAttackDamage", 1 / r);
                w.put("rawAttackSpeed", 30.0);
                shared(w, v);
            }
            case "attackspeed" -> {
                w.put("rawAttackSpeed", 40.0);
                w.put("mainAttackDamage", 1.0);
                w.put("rawMainAttackDamage", 1 / r);
            }
            case "poison" -> {
                w.put("poison", 4.0 / Math.max(10, level));
                w.put("rawAttackSpeed", 20.0);
                w.put("mainAttackDamage", 0.5);
                w.put("spellDamage", 0.5);
            }
            case "health" -> {
                double hp = v.get("rawHealth");
                w.put("rawHealth", 3 * hp);     // tank recipes trade speed and intelligence for health
                w.put("healthRegenRaw", 2 * v.get("healthRegenRaw"));
                w.put("healthRegen", 0.4);
                w.put("elementalDefence", 0.6);
                for (String e : ELEMENTS) {
                    w.put(e + "Defence", 0.2);
                }
            }
            case "spellcost" -> {
                // a percent off a spell's cost is worth about as much as mana regeneration; a raw point more
                for (String id : COSTS) {
                    w.put(id, id.startsWith("raw") ? -4.0 : -1.5);
                }
            }
            case "mana" -> {
                w.put("manaRegen", 4.0);
                w.put("manaSteal", 4.0);
                w.put("rawMaxMana", 0.6);
                w.put("spellDamage", 0.4);
            }
            case "speed" -> {
                w.put("walkSpeed", 2.0);
                w.put("rawAgility", 1.0);
                w.put("sprint", 0.4);
                w.put("sprintRegen", 0.4);
            }
            case "lifesteal" -> {
                w.put("lifeSteal", 2 * v.get("lifeSteal"));
                w.put("healthRegenRaw", 2 * v.get("healthRegenRaw"));
            }
            case "loot" -> {
                w.put("lootBonus", 2.0);
                w.put("lootQuality", 2.0);
            }
            case "xp" -> {
                w.put("xpBonus", 2.0);
                w.put("combatExperience", 2.0);
                w.put("lootBonus", 0.4);
            }
            default -> {
                if (!ELEMENTS.contains(key)) {
                    return null;
                }
                // an element: its damage of every kind, a second element riding along at its value
                String c = cap(key);
                w.put(key + "Damage", 2.0);
                w.put("elementalDamage", 2.0);
                w.put("damage", 2.0);
                w.put(key + "SpellDamage", 2.0);
                w.put(key + "MainAttackDamage", 2.0);
                w.put("raw" + c + "Damage", 2 / r);
                w.put("raw" + c + "SpellDamage", 2 / r);
                w.put("raw" + c + "MainAttackDamage", 2 / r);
                w.put("rawAttackSpeed", 20.0);
                for (String o : ELEMENTS) {
                    if (!o.equals(key)) {
                        w.put(o + "Damage", v.get(o + "Damage"));
                    }
                }
            }
        }
        return w;
    }

    /**
     * Damage that helps any build of the kind, at its plain value: all damage and elemental
     * damage. A single element is the element goals' business (and a loss here when it drops).
     */
    private static void shared(Map<String, Double> w, Map<String, Double> v) {
        w.putIfAbsent("damage", v.get("damage"));
        w.putIfAbsent("elementalDamage", v.get("elementalDamage"));
    }

    private static String cap(String e) {
        return Character.toUpperCase(e.charAt(0)) + e.substring(1);
    }
}
