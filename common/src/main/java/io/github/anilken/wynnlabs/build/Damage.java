package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.SetData;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.StatRange;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A small damage model of a whole build, after Wynncraft 2.x's formula (see the skill's
 * mekanikler.md): spells scale from the weapon's base DPS times the spell's conversion, % IDs
 * add up per element, raw spell damage is added times the conversion, then Strength and crit
 * multiply. Mana decides how often you can cast. Health, XP bonus and walk speed weigh in as
 * the rules say, so the number means "how fast this build levels", not just "how hard it hits".
 *
 * <p>The spells, their costs, conversions and shares live in rules.model so they can be set per
 * class and archetype.
 */
public final class Damage {

    public static final String[] ELEMENTS = {"neutral", "earth", "thunder", "water", "fire", "air"};

    /** One wearable piece, reduced to what the model needs. */
    /**
     * {@code key} is the item's advice key (name and rolls): Wynncraft swaps the ItemStack objects
     * in your inventory all the time, so pieces are matched by key, not by object.
     */
    public record Piece(ItemStack stack, String key, String name, Slot type, int level, Map<String, Double> stats,
            Map<SkillKind, Integer> reqs, double baseDps, Map<String, Double> weaponShares, double health,
            boolean crafted) {

        Piece(ItemStack stack, String key, String name, Slot type, int level, Map<String, Double> stats,
                Map<SkillKind, Integer> reqs, double baseDps, Map<String, Double> weaponShares, double health) {
            this(stack, key, name, type, level, stats, reqs, baseDps, weaponShares, health, false);
        }

        /** Weapons and crafted gear never help other items' skill requirements (Wynncraft rule). */
        public boolean helpsRequirements() {
            return !crafted && (type == null || !type.isWeapon());
        }
    }

    /**
     * {@code ehp} is effective health as WynnBuilder shows it (Defence and Agility only);
     * {@code ehpElemental} also counts the build's elemental defences against the incoming split
     * (rules.model.incomingElements), and is what the survival factor uses. {@code pieces} and
     * {@code weapon}: the set it was worked out for (null when read back from disk), so the road
     * map can tell which pieces a set keeps from the milestone before it. {@code casts} is the
     * fixed cast rate (rules.model.maxCastsPer5s): no mana is simulated. {@code factors} holds
     * the six parts the objective multiplies ({@link #FACTORS}), then the mana stats the mana
     * part is made of: mana back every 5 s ({@link #manaPer5}) and the share of the spells'
     * cost the build takes off ({@link #costCut}).
     */
    public record Result(double objective, double spellDps, double mainDps, double casts, double ehp, double xp,
            double speed, double dmgPerCast, double[] factors, double ehpElemental, double health,
            List<Piece> pieces, Piece weapon) {

        Result(double objective, double spellDps, double mainDps, double casts, double ehp, double xp,
                double speed, double dmgPerCast) {
            this(objective, spellDps, mainDps, casts, ehp, xp, speed, dmgPerCast, new double[0], ehp, 0);
        }

        /** A result with no pieces to it (read back from disk, made up in tests). */
        public Result(double objective, double spellDps, double mainDps, double casts, double ehp, double xp,
                double speed, double dmgPerCast, double[] factors, double ehpElemental, double health) {
            this(objective, spellDps, mainDps, casts, ehp, xp, speed, dmgPerCast, factors, ehpElemental, health, null, null);
        }

        /** Mana back every 5 s from the stats: the base, the tree, Mana Regen and Mana Steal (per 3 s); 0 when unknown. */
        public double manaPer5() {
            return factors.length > 6 ? factors[6] : 0;
        }

        /** The share of the spells' mana cost the build takes off (Intelligence and the cost IDs), by each spell's share; 0..1. */
        public double costCut() {
            return factors.length > 7 ? factors[7] : 0;
        }

        /** The healing a second the Healer goal scores, yours and the party's (Healing); 0 for the other goals. */
        public double healPerSecond() {
            return factors.length > 8 ? factors[8] : 0;
        }
    }

    /**
     * The parts the objective multiplies, in this order (for the gain breakdown): language keys,
     * shown with {@link #factor}.
     */
    static final String[] FACTORS = {"wynnlabs.damage.factor_damage", "wynnlabs.damage.factor_health",
            "wynnlabs.damage.factor_mana", "wynnlabs.damage.factor_speed", "wynnlabs.damage.factor_xp",
            "wynnlabs.damage.factor_attack_speed"};

    /** The name of factor {@code i} in the game's language. */
    public static String factor(int i) {
        return L.t(FACTORS[i]);
    }

    private Damage() {}

    /** The model's view of a stack, or null for anything it cannot read (unidentified, not gear). */
    /** A piece you do not own yet, from Wynntils' item data, every roll at the middle of its range. */
    /** Pseudo-stat on weapons: how a weapon's attack speed feels to play (1 = no penalty). */
    static final String FEEL = "_attackSpeedFeel";
    /** Pseudo-stat on weapons: attack speed tier, 0 = super slow … 6 = super fast. */
    static final String TIER = "_attackSpeedTier";
    /** Hits per second by attack speed tier (Wynncraft / WynnBuilder). */
    static final double[] HITS = {0.51, 0.83, 1.5, 2.05, 2.5, 3.1, 4.3};

    /** Pseudo-stat: one per piece of a set ("_set:Morph"); evaluate() adds the set bonus. */
    public static final String SET = "_set:";
    static final Map<String, SetData> SETS = new java.util.concurrent.ConcurrentHashMap<>();

    // ---- pieces (Pieces) ------------------------------------------------------------------------

    public static int powderTier(int itemLevel) {
        return Pieces.powderTier(itemLevel);
    }

    static double tierOf(String speed) {
        return Pieces.tierOf(speed);
    }

    static double feel(String speed) {
        return Pieces.feel(speed);
    }

    /** A piece you do not own yet, from Wynntils' item data, every roll at the middle of its range. */
    public static Piece piece(ItemData info) {
        return Pieces.piece(info);
    }

    /** {@link #piece(ItemData)} with the powders a WynnBuilder link gives it ("T6T6E7") instead of assumed ones. */
    public static Piece piece(ItemData info, String powders) {
        return Pieces.piece(info, powders);
    }

    static List<String> assumedPowders(ItemData info, Map<String, Double> shares) {
        return Pieces.assumedPowders(info, shares);
    }

    public static Piece withAssumedPowders(Piece p) {
        return Pieces.withAssumedPowders(p);
    }

    public static Object[] addedPowders(Piece p) {
        return Pieces.addedPowders(p);
    }

    public static int[] assumedCount(Piece p, ItemData info) {
        return Pieces.assumedCount(p, info);
    }

    public static Object[] powderHint(ItemData info) {
        return Pieces.powderHint(info);
    }

    /** The model's view of a stack, or null for anything it cannot read (unidentified, not gear). */
    public static Piece piece(ItemStack stack) {
        return Pieces.piece(stack);
    }

    public static Piece piece(ItemView item, String key, ItemStack stack) {
        return Pieces.piece(item, key, stack);
    }

    /** The stat key of a piece's own (base) defence in element {@code el}, before the % IDs. */
    static String defKey(String el) {
        return "_def:" + el;
    }


    // ---- skill point curve -----------------------------------------------------------------

    /**
     * Percent effect of this many points in a skill (Str/Dex/Int scale), 0 to 80.8: Wynncraft's
     * f(p) = 0.9908 / (1 − 0.9908) × (1 − 0.9908^p), capped at 150 points (WynnBuilder build_utils).
     */
    static double curve(double points) {
        double p = Math.max(0, Math.min(150, points));
        return 0.9908 / (1 - 0.9908) * (1 - Math.pow(0.9908, p));
    }

    /** % more damage the class takes (base resistances): Shaman 1.4, Archer 1.3, Mage 1.2. */
    /** The class, taken on the game thread, while a build plan is worked out in the background. */
    static final ThreadLocal<io.github.anilken.wynnlabs.model.Klass> CLASS_FOR_THREAD = new ThreadLocal<>();

    /**
     * How much of a hit you take (WynnBuilder's defMult): the class's share, times what the
     * tree's base resistance nodes leave ("+5% Base Resistance").
     */
    static double damageTaken() {
        return classDamageTaken() * TreeModel.taken();
    }

    static double classDamageTaken() {
        try {
            var forThread = CLASS_FOR_THREAD.get();
            var cls = forThread != null ? forThread : GameState.current().classType();
            return switch (cls.name()) {
                case "SHAMAN" -> 1.4;
                case "ARCHER" -> 1.3;
                case "MAGE" -> 1.2;
                default -> 1.0;
            };
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /**
     * One hit of a damage source with these conversions (fractions per element, neutral first,
     * already split by the weapon's elements): % IDs add up per element, generic raw is spread
     * over the hit times the total conversion, element raw counts times the total conversion when
     * that element is present (WynnBuilder damage_calc). Before Strength and crit.
     */
    static double hit(Map<String, Double> s, double[] c, double baseDps, double[] skillBonus,
            boolean spell, double[] byElement) {
        return hit(s, c, baseDps, skillBonus, spell, byElement, 1);
    }

    /** The stat key of the tree's flat damage on every hit in element {@code el} (the masteries' "+2-4"). */
    static String flatKey(String el) {
        // constants, not built on every hit: the model asks for these on every evaluation
        return switch (el) {
            case "neutral" -> "_flat:neutral";
            case "earth" -> "_flat:earth";
            case "thunder" -> "_flat:thunder";
            case "water" -> "_flat:water";
            case "fire" -> "_flat:fire";
            case "air" -> "_flat:air";
            default -> "_flat:" + el;
        };
    }

    /**
     * {@link #hit} for a source of {@code hits} hits (a Totem cast is all its ticks): the tree's
     * flat damage per element (the masteries' "+2-4") lands on each hit, raised by the hit's %.
     */
    static double hit(Map<String, Double> s, double[] c, double baseDps, double[] skillBonus,
            boolean spell, double[] byElement, double hits) {
        double convTotal = 0;
        for (double v : c) {
            convTotal += Math.max(0, v);
        }
        double dmg = 0;
        String kind = spell ? "Spell" : "MainAttack";
        double raw = stat(s, "raw" + kind + "Damage") + stat(s, "rawDamage");
        double rawElemental = stat(s, "rawElemental" + kind + "Damage") + stat(s, "rawElementalDamage");
        for (int i = 0; i < ELEMENTS.length; i++) {
            if (c[i] <= 0) {
                continue;
            }
            String el = ELEMENTS[i];
            double pct = stat(s, spell ? "spellDamage" : "mainAttackDamage") + stat(s, "damage")
                    + stat(s, el + kind + "Damage") + stat(s, el + "Damage") + skillBonus[i];
            if (i > 0) {
                pct += stat(s, "elemental" + kind + "Damage") + stat(s, "elementalDamage");
            }
            double flat = i > 0 ? stat(s, flatKey(el)) * hits : 0;
            dmg += (baseDps * c[i] + flat) * (1 + pct / 100.0);
            String cap = Character.toUpperCase(el.charAt(0)) + el.substring(1);
            double elementRaw = 0;
            if (i > 0) {
                elementRaw = (stat(s, "raw" + cap + kind + "Damage") + stat(s, "raw" + cap + "Damage")) * convTotal;
                dmg += elementRaw;
            }
            if (byElement != null) {
                // the generic raw goes to each element by its share of the conversion
                byElement[i] += (baseDps * c[i] + flat) * (1 + pct / 100.0) + elementRaw + raw * c[i]
                        + (i > 0 ? rawElemental * c[i] : 0);
            }
        }
        dmg += raw * convTotal;
        dmg += rawElemental * (convTotal - Math.max(0, c[0]));
        return dmg;
    }

    /** Spreads a neutral conversion over the weapon's own elements (2.x). */
    static double[] split(double[] conv, Map<String, Double> shares) {
        double[] c = conv.clone();
        double neutral = c[0];
        c[0] = neutral * shares.getOrDefault("neutral", 0.0);
        for (int i = 1; i < ELEMENTS.length; i++) {
            c[i] += neutral * shares.getOrDefault(ELEMENTS[i], 0.0);
        }
        return c;
    }

    // ---- the model ---------------------------------------------------------------------------

    private static double stat(Map<String, Double> s, String key) {
        return s.getOrDefault(key, 0.0);
    }

    static double model(String key, double fallback) {
        // the road map's tuning strip (Tuning) leans a few weights; 1 everywhere else. The ability
        // tree you have at the level worked out for (TreeModel) sets summons, mana and the main attack
        // nothing measured or learned in game (the damage meter, deaths) changes these: the model
        // is the math of gear, skill points, tree and powders, and the goal's weights
        return TreeModel.adjust(key, Rules.sub("model", key, fallback)) * Tuning.weight(key);
    }

    /**
     * The elemental effective health a build should keep at this level before damage counts in
     * full: the table below (about what dies in the open world at that level, a Lv 69-72 Shaman
     * died again and again at 5.5-7k), times the goal's rules.model.ehpFloorScale (Damage 1,
     * Tank 1.6) and scaled by rules.roadmap.ehpFloorScale (0 turns it off).
     */
    static double ehpFloor(int level) {
        double scale = Rules.sub("roadmap", "ehpFloorScale", 1.0);
        if (scale <= 0) {
            return 0;
        }
        return scale * ehpTable(level) * model("ehpFloorScale", 1.0);
    }

    /**
     * What a build below the health floor keeps of its life factor: 1 at the floor and above,
     * then m + (1 - m)·exp(-a·(1 - r)²) for r = health / floor, so it leaves the floor with no
     * kink (a 1 % shortfall costs next to nothing), falls fastest around 65 % and never goes under
     * m (rules.model.ehpFloorMin, 0.4; sharpness rules.model.ehpFloorSharpness, 4). The cube it
     * replaced fell to nothing, and the search poured every point into Defence to climb out.
     */
    static double floorFactor(double ehp, double floor) {
        if (floor <= 0 || ehp >= floor) {
            return 1;
        }
        double r = Math.max(0, ehp / floor);
        double m = Math.max(0.05, Math.min(1, model("ehpFloorMin", 0.4)));
        double a = Math.max(0.1, model("ehpFloorSharpness", 4));
        return m + (1 - m) * Math.exp(-a * (1 - r) * (1 - r));
    }

    /**
     * The health the score counts once it is past {@code knee}: the same below it, then
     * knee + h·(1 − exp(−(health − knee) / h)) with h = knee / 2, so it leaves the knee with no
     * kink and levels off at 1.5 × knee. The Damage goal puts the knee at 1.5 × the floor, so
     * health stops paying at about 2.25 × the floor (Balanced 2.5 → 3.75 ×; Safe, Tank and Healer
     * have none). Community damage builds sit at 1-2 × the floor; with health counted to 6 × a
     * pick with less damage outscored them by 2-8 ×, and a log past the knee still left 1.5-2.5 ×.
     * 0 or less turns it off.
     */
    static double softCap(double ehp, double knee) {
        if (knee <= 0 || ehp <= knee) {
            return ehp;
        }
        double h = knee / 2;
        return knee + h * (1 - Math.exp(-(ehp - knee) / h));
    }

    /**
     * What a build whose health drains keeps of its life factor, from the share of its own max
     * health lost every regen tick (4 s) after life steal and the tree's own heals: m + (1 - m) /
     * (1 + w·share / (1 - m)), slope -w at no drain and never under m (rules.model.drainWeight 4,
     * drainFloor 0.5). A −250/4 s drain on 20k health costs about 5 %, Nether's Scar's −2500 on
     * 13k about 30 %; scaled by the level's health it was 0.3 for both.
     */
    static double drainFactor(double share) {
        if (share <= 0) {
            return 1;
        }
        double m = Math.max(0, Math.min(0.95, model("drainFloor", 0.5)));
        double w = Math.max(0, model("drainWeight", 4.0));
        return m + (1 - m) / (1 + w * share / (1 - m));
    }

    /**
     * Health regen a tick as the game applies it: a positive raw value is raised by its %, a
     * negative one shrinks with a positive % and never turns into a heal (WynnBuilder: min(0,
     * raw − raw·%)), and a negative % deepens it.
     */
    public static double healthRegen(double raw, double pct) {
        if (raw >= 0) {
            return raw * (1 + pct / 100.0);
        }
        return Math.min(0, raw - raw * pct / 100.0);
    }

    /** The floor's table for a Damage build: level, elemental effective health, straight lines between. */
    public static double ehpTable(int level) {
        int[][] t = {{1, 0}, {50, 2000}, {60, 4000}, {70, 6500}, {80, 9000}, {90, 12000}, {100, 16000},
            {110, 20000}, {120, 25000}};
        for (int i = 1; i < t.length; i++) {
            if (level <= t[i][0]) {
                return t[i - 1][1] + (t[i][1] - t[i - 1][1]) * (double) (Math.max(1, level) - t[i - 1][0])
                        / (t[i][0] - t[i - 1][0]);
            }
        }
        return t[t.length - 1][1];
    }

    /**
     * Evaluates a build: the armour and accessories, the weapon (may be null) and the skill
     * points you assigned yourself (indexed by SkillKind.ordinal()).
     */
    static Result evaluate(List<Piece> pieces, Piece weapon, int[] assigned, int level) {
        return evaluate(pieces, weapon, assigned, level, null);
    }

    /**
     * Where a build's DPS comes from, per element (in {@link #ELEMENTS} order): spells, the main
     * attack and summons. Filled by the same pass as the Result, so {@code spell} adds up to its
     * spellDps and {@code main} to its mainDps (before mainAttackWeight and the attack speed feel).
     * {@code spells}: each spell of rules.model.spells as the model sees one cast of it, for the
     * damage meter to compare a cast with; {@code sources} the same DPS source by source, with
     * the heals and ultimates ({@link Sources}).
     * The summons here are whole: the score alone weighs them by {@link Sources#fightShare}.
     */
    public record Breakdown(double[] spell, double[] main, double[] summon, List<SpellPart> spells,
            List<Sources.Source> sources) {

        Breakdown() {
            this(new double[ELEMENTS.length], new double[ELEMENTS.length], new double[ELEMENTS.length],
                    new java.util.ArrayList<>(), new java.util.ArrayList<>());
        }

        public double spellTotal() {
            return sum(spell);
        }

        public double mainTotal() {
            return sum(main);
        }

        public double summonTotal() {
            return sum(summon);
        }

        /** Spells, main attack and summons together: the DPS the what-ifs compare. */
        public double total() {
            return spellTotal() + mainTotal() + summonTotal();
        }

        /** Element {@code i} over all three sources. */
        public double element(int i) {
            return spell[i] + main[i] + summon[i];
        }

        private static double sum(double[] a) {
            double t = 0;
            for (double v : a) {
                t += v;
            }
            return t;
        }
    }

    /**
     * One spell as the model sees it: {@code perCast} the whole cast (all its hits and ticks, with
     * Strength and crit). A lasting spell (Totem) counts with the summons; {@code duration} is how long its ticks run (0: one go); {@code cost} its mana per
     * cast after Intelligence and the cost IDs.
     */
    public record SpellPart(String name, int index, double share, double perCast, boolean lasting,
            double duration, double cost) {}

    public record Detail(Result result, Breakdown breakdown) {}

    /** {@link #evaluate} with the breakdown of its DPS. */
    static Detail detail(List<Piece> pieces, Piece weapon, int[] assigned, int level) {
        Breakdown b = new Breakdown();
        return new Detail(evaluate(pieces, weapon, assigned, level, b), b);
    }

    /**
     * Every stat the pieces and the weapon add up to, with the set bonuses and the ability tree's
     * passive bonuses in force (TreeModel): what {@link #evaluate} works from.
     */
    static Map<String, Double> totals(List<Piece> pieces, Piece weapon) {
        Map<String, Double> s = new HashMap<>();
        for (Piece p : pieces) {
            if (p != null) {
                p.stats().forEach((k, v) -> s.merge(k, v, Double::sum));
            }
        }
        if (weapon != null) {
            weapon.stats().forEach((k, v) -> s.merge(k, v, Double::sum));
        }
        // Set bonuses: n pieces of one set add that set's bonus for n (Morph, Adventurer's, …);
        // a ring in both hands is two, and some sets pay from one piece (Goblin, Black Catalyst)
        for (var e : new java.util.ArrayList<>(s.entrySet())) {
            if (!e.getKey().startsWith(SET)) {
                continue;
            }
            var set = SETS.get(e.getKey().substring(SET.length()));
            int n = (int) Math.round(e.getValue());
            var bonus = set == null || n < 1 ? null : set.bonusFor(n);
            if (bonus != null) {
                bonus.minor().forEach(sv -> s.merge(sv.key(), (double) sv.value(), Double::sum));
            }
        }
        // the tree's passive bonuses at this level (TreeModel): Main Attack Damage, the masteries' element %
        TreeModel.addStats(s);
        return s;
    }

    /** A skill's points: what you assigned plus what the gear in {@code s} gives. */
    static double skillPoints(Map<String, Double> s, int[] assigned, SkillKind sk) {
        String api = "raw" + sk.name().charAt(0) + sk.name().substring(1).toLowerCase(Locale.ROOT);
        return assigned[sk.ordinal()] + stat(s, api);
    }

    /** Defence's damage cut in % for this many points (70 % cap). */
    static double defence(double points) {
        return curve(points) * 0.867;
    }

    /**
     * The mana pool as WynnBuilder has it: 100, the gear's max mana and Intelligence's
     * {@code intel} % rounded down. A fixed base: nothing measured in game changes it.
     */
    static double manaPool(Map<String, Double> s, double intel) {
        return 100 + stat(s, "rawMaxMana") + stat(s, "maxMana") + Math.floor(intel);
    }

    /** Mana back every 5 s before the gear: the game's 25, and what the tree's nodes add (TreeModel). */
    static double baseManaPer5() {
        return TreeModel.adjust("baseManaPer5s", 25);
    }

    /**
     * The mana part of the score from the build's own stats: {@code manaPer5} (mana back every
     * 5 s) over the game's base, divided by the share of the spells' cost left after
     * {@code costCut}, on a log scale with the weight rules.model.manaWeight; at least 0.3.
     */
    static double manaFactor(double manaPer5, double costCut) {
        // its two parts apart, so the Priority menu can lean on one (Tuning: costCutLean,
        // manaRegenLean; both 1 without it, the same score as one log of their ratio)
        double regen = Math.log(Math.max(0.05, manaPer5 / BASE_MANA_PER_5));
        double cut = -Math.log(Math.max(0.05, 1 - costCut));
        return Math.max(0.3, 1 + model("manaWeight", 0.3)
                * (Tuning.weight("manaRegenLean") * regen + Tuning.weight("costCutLean") * cut));
    }

    /** A spell slot's name in the cost IDs ("1st" … "4th"). */
    static String nth(int idx) {
        return idx == 1 ? "1st" : idx == 2 ? "2nd" : idx == 3 ? "3rd" : "4th";
    }

    /**
     * A spell's mana per cast: Intelligence cuts the base cost, then the raw cost IDs of its slot,
     * then the % ones, at least 1 (WynnBuilder getSpellCost). The item data keeps a cut as a
     * positive number (Wynntils has "-15% 3rd Spell Cost" as 15), so a cost ID takes off what it says.
     */
    static double spellCost(Map<String, Double> s, double baseCost, int idx, double intReduction) {
        String nth = nth(idx);
        double cost = (baseCost * (1 - intReduction) - stat(s, "raw" + nth + "SpellCost"))
                * (1 - stat(s, nth + "SpellCost") / 100.0);
        return Math.max(1, cost);
    }

    /** The game's own mana back every 5 s, the mana part's yardstick. */
    static final double BASE_MANA_PER_5 = 25;

    private static Result evaluate(List<Piece> pieces, Piece weapon, int[] assigned, int level, Breakdown parts) {
        double[] spellEl = parts == null ? null : new double[ELEMENTS.length];
        double[] mainEl = parts == null ? null : new double[ELEMENTS.length];
        double[] summonEl = parts == null ? null : new double[ELEMENTS.length];
        Map<String, Double> s = totals(pieces, weapon);
        double health = 0;
        for (Piece p : pieces) {
            if (p != null) {
                health += p.health();
            }
        }
        if (weapon != null) {
            health += weapon.health();
        }
        double[] skill = new double[SkillKind.values().length];
        for (SkillKind sk : SkillKind.values()) {
            skill[sk.ordinal()] = skillPoints(s, assigned, sk);
        }
        double str = curve(skill[SkillKind.STRENGTH.ordinal()]);
        double dex = curve(skill[SkillKind.DEXTERITY.ordinal()]);
        double intel = curve(skill[SkillKind.INTELLIGENCE.ordinal()]);
        double def = defence(skill[SkillKind.DEFENCE.ordinal()]);
        double agi = curve(skill[SkillKind.AGILITY.ordinal()]) * 0.951;     // 76.8 % cap
        // element bonuses: Def and Agi scaled the same way (WynnBuilder skillpoint_damage_mult)
        double[] skillBonus = {0, str, dex, intel, def, agi};

        double baseDps = weapon == null ? 0 : weapon.baseDps();
        Map<String, Double> shares = weapon == null ? Map.of("neutral", 1.0) : weapon.weaponShares();
        // Strength adds, a crit adds +100 % plus Crit Damage % (additive with Strength, not ×)
        double multiplier = 1 + str / 100.0 + dex / 100.0 * (1 + stat(s, "criticalDamageBonus") / 100.0);
        double intReduction = 0.5 * intel / curve(150);      // half off at 150 points

        // Spells: the cast's conversion through the damage pipeline (the tree's cuts, waves and
        // one cast per Totem are in the conversion, TreeModel); nothing measured in game scales it
        double dmgSum = 0;
        double shareSum = 0;
        // lasting spells (Totem: a hit every 0.4 s for 30 s) deal alongside the casts, as summons do
        double lastingDps = 0;
        // the mana part: how much of each spell's cost the stats take off, by the spell's share
        double cutSum = 0;
        double cutShare = 0;
        double[] lastingEl = parts == null ? null : new double[ELEMENTS.length];
        JsonElement spellsEl = Rules.get().has("model") ? Rules.get().getAsJsonObject("model").get("spells") : null;
        // with the tree's cost changes and the spells not bought yet at this level (TreeModel)
        JsonArray spells = TreeModel.spells(spellsEl != null && spellsEl.isJsonArray() ? spellsEl.getAsJsonArray() : new JsonArray());
        for (JsonElement e : spells) {
            JsonObject sp = e.getAsJsonObject();
            double share = sp.has("share") ? sp.get("share").getAsDouble() : 1;
            if (share <= 0) {
                continue;
            }
            JsonObject conv = sp.getAsJsonObject("conv");
            double[] c = new double[ELEMENTS.length];
            for (int i = 0; i < ELEMENTS.length; i++) {
                c[i] = conv.has(ELEMENTS[i]) ? conv.get(ELEMENTS[i]).getAsDouble() : 0;
            }
            double[] el = spellEl == null ? null : new double[ELEMENTS.length];
            double dmg = hit(s, split(c, shares), baseDps, skillBonus, true, el,
                    sp.has("hits") ? sp.get("hits").getAsDouble() : 1);
            int idx = sp.has("index") ? sp.get("index").getAsInt() : 0;
            String nth = nth(idx);
            double baseCost = sp.has("cost") ? sp.get("cost").getAsDouble() : 30;
            double cost = spellCost(s, baseCost, idx, intReduction);
            // what the build takes off this spell's cost (below 0 when an ID raises it), counted
            // by its share: a cut on a spell you do not cast counts nothing
            cutSum += share * (1 - cost / Math.max(1, baseCost));
            cutShare += share;
            double duration = sp.has("duration") ? sp.get("duration").getAsDouble() : 0;
            boolean lasting = ClassProfiles.lasting(sp) && duration > 0;
            if (parts != null) {
                // shown as the game takes it, whole mana rounded down: a Totem at 15 with 39
                // Intelligence comes to 11.97 and costs 11. The mana math keeps the decimals, as
                // WynnBuilder's does (a score in steps would stall the road map's search)
                parts.spells().add(new SpellPart(sp.has("name") ? sp.get("name").getAsString() : nth, idx, share,
                        dmg * multiplier, lasting, duration, Math.max(1, Math.floor(cost + 1e-9))));
            }
            if (lasting) {
                lastingDps += dmg * multiplier / duration;
                if (el != null) {
                    for (int i = 0; i < el.length; i++) {
                        lastingEl[i] += el[i] * multiplier / duration;
                    }
                }
                continue;
            }
            if (el != null) {
                for (int i = 0; i < el.length; i++) {
                    spellEl[i] += share * el[i] * multiplier;
                }
            }
            dmgSum += share * dmg * multiplier;
            shareSum += share;
        }
        double dmgPerCast = shareSum > 0 ? dmgSum / shareSum : 0;
        // Spells are cast at a fixed rate (rules.model.maxCastsPer5s): no mana is simulated, so
        // the spell damage does not hang on a guess of how a fight goes. Mana is its own part of
        // the score below, from the build's stats alone
        double casts = Math.max(0, model("maxCastsPer5s", 4));
        // mana back every 5 s: the base and the tree's nodes, Mana Regen (per 5 s) and Mana Steal
        // counted whole, as WynnBuilder does (its value comes back every 3 s)
        double manaPer5 = baseManaPer5() + stat(s, "manaRegen") + stat(s, "manaSteal") * 5.0 / 3.0;
        double costCut = cutShare > 0 ? cutSum / cutShare : 0;
        double spellDps = dmgPerCast * casts / 5.0;

        // Main attack: per hit times hits per second from the weapon's tier plus Attack Speed IDs
        int tier = weapon == null ? 3 : (int) Math.round(weapon.stats().getOrDefault(TIER, 3.0));
        int tierNow = Math.max(0, Math.min(6, tier + (int) Math.round(stat(s, "rawAttackSpeed"))));
        double perHitBase = baseDps / HITS[tier];
        double[] mc = split(new double[] {1, 0, 0, 0, 0, 0}, shares);
        double mainHit = hit(s, mc, perHitBase, skillBonus, false, mainEl) * model("mainAttackClassMult", 1.0);
        double mainDps = mainHit * HITS[tierNow] * multiplier;

        // Summons and areas (Puppet Master, Crimson Effigy, a trap): spell hits on their own clock,
        // no mana, each from its own conversion times its hits a second and how many (Sources);
        // the score weighs them by the share of a fight they are out (Sources.fightShare), the
        // numbers shown and the totals have them whole
        List<Sources.Source> summonList = parts == null ? null : new java.util.ArrayList<>();
        Sources.Summons summons = Sources.summons(TreeModel.active(), s, shares, baseDps, skillBonus, multiplier,
                summonEl, summonList);
        double passive = summons.scored() + lastingDps;
        if (parts != null) {
            double perSpell = shareSum > 0 ? 1 / shareSum * casts / 5.0 : 0;
            double perMain = model("mainAttackClassMult", 1.0) * HITS[tierNow] * multiplier;
            for (int i = 0; i < ELEMENTS.length; i++) {
                parts.spell()[i] = spellEl[i] * perSpell;
                parts.main()[i] = mainEl[i] * perMain;
                parts.summon()[i] = summonEl[i] + lastingEl[i];
            }
        }
        // Slow weapons feel sluggish on the main attack only: spells and summons do not care
        double feel = weapon == null ? 1.0 : weapon.stats().getOrDefault(FEEL, 1.0);
        double mainPart = model("mainAttackWeight", 0.5) * mainDps;
        double total = spellDps + passive + mainPart * feel;
        double totalNoFeel = spellDps + passive + mainPart;
        double feelFactor = totalNoFeel > 0 ? total / totalNoFeel : 1;

        // Staying alive: real HP (5 × level + 5), Defence and Agility as Wynncraft applies them,
        // and how much more damage the class takes
        health += model("hpBase", 5) + model("hpPerLevel", 5) * level + stat(s, "rawHealth");
        double d = def / 100.0;
        double a = agi / 100.0;
        double ehp = Math.max(1, health) / (0.1 * a + (1 - a) * (1 - d)) / damageTaken();
        // Elemental defences: the damage you take splits by element (rules.model.incomingElements,
        // half neutral and a tenth each by default); each element's share shrinks by the build's
        // defence % for it, a negative one grows it. WynnBuilder's EHP leaves this out, so ehp
        // above stays comparable and the elemental view is its own number
        double taken = 0;
        double[] incoming = incomingElements();
        for (int i = 0; i < ELEMENTS.length; i++) {
            double pct = i == 0 ? 0 : stat(s, ELEMENTS[i] + "Defence") + stat(s, "elementalDefence");
            taken += incoming[i] * Math.max(0.1, 1 - pct / 100.0);
        }
        double ehpElemental = ehp / Math.max(0.1, taken);
        double xp = stat(s, "combatExperience") + stat(s, "xpBonus");
        double speed = stat(s, "walkSpeed");
        // XP is worth nothing once you are at the level cap
        double xpWeight = level >= model("maxLevel", 120) ? 0 : model("xpWeight", 1.0) * model("goalXp", 1.0);
        // the fighting goals (rules.model.xpLog 1: Damage, Safe, Boss) count XP with diminishing
        // returns, so +300 % XP does not outweigh staying alive; the levelling goals keep it linear
        double fXp = model("xpLog", 0) > 0
                ? 1 + xpWeight * Math.log1p(Math.max(-0.9, xp / 100.0))
                : 1 + xpWeight * xp / 100.0;
        // speed penalties count more than bonuses (rules.model.speedPenaltyWeight)
        double fSpeed = speedFactor(speed);
        // the goals that fight for damage stop paying in full for health far above what the level
        // needs (softCap, rules.model.ehpSoftCap times the floor): the log and the power below see
        // the capped value, the floor itself the real one
        double floor = ehpFloor(level);
        double ehpScored = softCap(ehpElemental, model("ehpSoftCap", 0) * floor);
        double fLife = Math.max(0.2, 1 + model("survivalWeight", 0.2)
                * Math.log(ehpScored / (model("hpBase", 5) + model("hpPerLevel", 5) * level)));
        // Sustain: health regen (healthRegen) and half the life steal, which only works while
        // hitting. Below zero the health drains even out of a fight, so it weighs more than a bonus
        // does (Winter's Essence, −65 regen, was picked as an upgrade); see drainFactor
        double regen = healthRegen(stat(s, "healthRegenRaw"), stat(s, "healthRegen")) + 0.5 * stat(s, "lifeSteal");
        double regenScale = model("hpBase", 5) + model("hpPerLevel", 5) * level;
        // the Healer goal's healing (Healing): the tree's heals as often as the mana pays for, for
        // you and the party. A healer's own heals cover a drain (one tick of regen every 4 s, the
        // wiki's Identifications page), so a negative regen or life steal does not sink it
        double healWeight = model("healWeight", 0);
        Healing.Result healing = healWeight > 0 || parts != null
                ? Healing.of(s, Math.max(1, health), manaPer5, manaPool(s, intel), intReduction, spells,
                        TreeModel.active() == null ? null : TreeModel.active().heals)
                : null;
        if (healWeight > 0) {
            regen += 4 * healing.self();
        } else if (regen < 0 && TreeModel.active() != null && TreeModel.active().heals != null
                && !TreeModel.active().heals.isEmpty()) {
            // any build's own heals from the tree cover a drain (up to none left, never a bonus)
            Healing.Result own = Healing.of(s, Math.max(1, health), manaPer5, manaPool(s, intel), intReduction, spells,
                    TreeModel.active().heals);
            regen = Math.min(0, regen + 4 * own.self());
        }
        double fRegen = regen < 0
                ? drainFactor(-regen / Math.max(1, health))
                : 1 + model("regenWeight", 0.2) * Math.min(1, regen / regenScale);
        fLife *= fRegen;
        // health also counts as a power of itself (rules.model.survivalPower, set by the goal: 0.25
        // for Damage up to 1 for a Tank), so more of it keeps paying where the log above flattens
        double power = model("survivalPower", 0.25);
        if (power > 0) {
            fLife *= Math.pow(ehpScored / regenScale, power);
        }
        // below the floor for this level a build pays a smooth, bounded share (floorFactor), so
        // damage is not bought with paper armour: the skill points, the road map and the build
        // plan all see it, as it is part of the score
        fLife *= floorFactor(ehpElemental, floor);
        // Mana, from the stats alone: the mana back every 5 s over the game's base 25, divided by
        // what is left of the spells' cost; no time and no fight are simulated. A build with no
        // mana stats and no Intelligence is 1; +25 mana every 5 s (or half the cost off) is 2,
        // worth manaWeight × ln 2 (+21 % at 0.3, the Mana chip triples the weight)
        double fMana = manaFactor(manaPer5, costCut);
        // A healer (the Healer goal, rules.model.healWeight above 0) is scored by the healing it
        // keeps up (Healing, worked out above): a power of the health a second every 5 s over the
        // level's health, so twice the healing is worth 2^healWeight times the score. Damage then
        // counts less (damageWeight below 1). Healing Efficiency far below -100 % (Immolation's
        // -650 %) heals nothing, never less: a NaN score threw the weapon out
        double fHeal = healWeight > 0
                ? Math.pow(Math.max(1e-6, healing.value() * 5 / regenScale), healWeight)
                : 1;
        // Boss fights reward a big hit per cast (rules.model.burstWeight), loot runs Loot Bonus and
        // Loot Quality (rules.model.lootWeight); both 0 unless the goal asks for them
        double fBurst = Math.pow(Math.max(1, dmgPerCast) / regenScale, model("burstWeight", 0));
        double fLoot = 1 + model("lootWeight", 0) * Math.max(0, stat(s, "lootBonus") + stat(s, "lootQuality")) / 100.0;
        double objective = Math.pow(Math.max(0, total), model("damageWeight", 1.0)) * fXp * fSpeed * fLife * fMana * fHeal
                * fBurst * fLoot;
        if (parts != null) {
            // the same numbers source by source: each spell, summon, area and heal (Sources)
            Sources.fill(parts, new Sources.Inputs(s, shares, baseDps, skillBonus, multiplier, casts, HITS[tierNow],
                    Math.max(1, health), summonList, healing));
        }
        // the Healer goal's healing a second (yours and the party's) after the mana stats
        double[] factors = healWeight > 0
                ? new double[] {totalNoFeel, fLife, fMana, fSpeed, fXp, feelFactor, manaPer5, costCut, healing.value()}
                : new double[] {totalNoFeel, fLife, fMana, fSpeed, fXp, feelFactor, manaPer5, costCut};
        return new Result(objective, spellDps, mainDps, casts, ehp, xp, speed, dmgPerCast,
                factors, ehpElemental, Math.max(1, health), pieces, weapon);
    }

    /**
     * Walk speed's share of the score. Penalties count linearly (rules.model.speedPenaltyWeight);
     * bonuses rise with the slope speedWeight at first and level off below speedCeiling, so a
     * build at +146% is not worth 2.45 times one without speed (it was, and speed drowned out
     * damage: Lv 100+ rings scored below a Lv 61 ring that gave 8% speed).
     */
    /**
     * How the damage you take splits by element, neutral first in {@link #ELEMENTS} order and
     * normalised to 1: rules.model.incomingElements ({"neutral": 0.5, "earth": 0.1, ...}), or
     * half neutral and a tenth each. Tunable per area later.
     */
    static double[] incomingElements() {
        double[] out = {0.5, 0.1, 0.1, 0.1, 0.1, 0.1};
        try {
            var m = Rules.get().getAsJsonObject("model");
            var inc = m == null || !m.has("incomingElements") || !m.get("incomingElements").isJsonObject()
                    ? null : m.getAsJsonObject("incomingElements");
            if (inc != null) {
                for (int i = 0; i < ELEMENTS.length; i++) {
                    if (inc.has(ELEMENTS[i])) {
                        out[i] = Math.max(0, inc.get(ELEMENTS[i]).getAsDouble());
                    }
                }
            }
        } catch (Exception e) {
            return new double[] {0.5, 0.1, 0.1, 0.1, 0.1, 0.1};
        }
        double sum = 0;
        for (double v : out) {
            sum += v;
        }
        if (sum <= 0) {
            return new double[] {0.5, 0.1, 0.1, 0.1, 0.1, 0.1};
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= sum;
        }
        return out;
    }

    static double speedFactor(double speed) {
        if (speed < 0) {
            return Math.max(0.2, 1 + model("speedPenaltyWeight", model("speedWeight", 0.3)) * speed / 100.0);
        }
        double w = model("speedWeight", 0.3) * model("goalSpeed", 1.0);
        double c = Math.max(0.01, (model("speedCeiling", 1.35) - 1) * model("goalSpeedRoom", 1.0));
        return 1 + c * (1 - Math.exp(-w * speed / 100.0 / c));
    }

    static JsonObject json(Result r) {
        JsonObject o = new JsonObject();
        o.addProperty("objective", Math.round(r.objective() * 10) / 10.0);
        o.addProperty("spellDps", Math.round(r.spellDps() * 10) / 10.0);
        o.addProperty("mainAttackDps", Math.round(r.mainDps() * 10) / 10.0);
        o.addProperty("damagePerCast", Math.round(r.dmgPerCast()));
        // the mana stats the mana part is made of (no cast rate: no mana is simulated)
        o.addProperty("manaPer5s", Math.round(r.manaPer5() * 10) / 10.0);
        o.addProperty("spellCostCutPercent", Math.round(r.costCut() * 1000) / 10.0);
        o.addProperty("health", Math.round(r.health()));
        o.addProperty("effectiveHealth", Math.round(r.ehp()));
        o.addProperty("effectiveHealthElemental", Math.round(r.ehpElemental()));
        // the factors the objective multiplies: damage is the weighted DPS sum, the rest are factors
        JsonObject parts = new JsonObject();
        String[] names = {"damage", "life", "mana", "speed", "xp", "attackSpeedFeel"};
        for (int i = 0; i < r.factors().length && i < names.length; i++) {
            parts.addProperty(names[i], Math.round(r.factors()[i] * 1000) / 1000.0);
        }
        if (!parts.isEmpty()) {
            o.add("parts", parts);
        }
        if (r.healPerSecond() > 0) {
            o.addProperty("healPerSecond", Math.round(r.healPerSecond()));
        }
        o.addProperty("xpBonus", r.xp());
        o.addProperty("walkSpeed", r.speed());
        return o;
    }
}
