package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.SkillKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A build's numbers for the Home's stats page: the model's {@link Damage.Result} with the stat
 * totals it was worked out from (gear, set bonuses, the tree's passives), the mana pool and the
 * effective health without Agility's dodge. The same sums and formulas the score uses; nothing
 * new is modelled here.
 */
public final class BuildStats {

    /** A stat total below this is flagged: the build takes far more of that element. */
    public static final double LOW_DEFENCE = -100;

    /**
     * One build's numbers: its result, every stat total by its id key ("manaRegen"), the mana
     * pool and the effective health with Agility left out; the level it was worked out at (0: yours)
     * and why you cannot wear it yet (null: you can); {@code parts} where its DPS comes from, spell
     * by spell (null when no level was given); {@code tome} the guild tome its points assume
     * ({@link Tomes}, null: none).
     */
    public record Side(Damage.Result result, Map<String, Double> stats, double mana, double ehpNoAgility, int level,
            String notWearable, Damage.Breakdown parts, Tomes.Tome tome) {

        Side(Damage.Result result, Map<String, Double> stats, double mana, double ehpNoAgility) {
            this(result, stats, mana, ehpNoAgility, 0, null, null, null);
        }

        /** This side worked out at {@code lv}, with why it cannot be worn yet (null: it can). */
        public Side at(int lv, String why) {
            return new Side(result, stats, mana, ehpNoAgility, lv, why, parts, tome);
        }

        /** This side's points spread with {@code t} assumed (a guild tome not had yet). */
        Side assuming(Tomes.Tome t) {
            return new Side(result, stats, mana, ehpNoAgility, level, notWearable, parts, t);
        }

        /** The main attack's speed tier with the Attack Speed IDs: 0 super slow … 6 super fast. */
        public int attackTier() {
            return Math.max(0, Math.min(6, (int) Math.round(stats.getOrDefault(Damage.TIER, 3.0)) + (int) Math.round(stat("rawAttackSpeed"))));
        }

        /** One main attack's damage: the main attack DPS over hits per second. */
        public double perAttack() {
            return result.mainDps() / Damage.HITS[attackTier()];
        }

        public double stat(String key) {
            return stats.getOrDefault(key, 0.0);
        }

        /** Health regen as the game applies it (Damage.healthRegen). */
        public double regen() {
            return Damage.healthRegen(stat("healthRegenRaw"), stat("healthRegen"));
        }

        /** Health regen as effective health: what it heals counted the way the damage you take is. */
        public double effectiveRegen() {
            return result.health() <= 0 ? 0 : regen() * result.ehp() / result.health();
        }

        /** An element's defence %: its own and the all-element one. */
        public double defence(String element) {
            return stat(element + "Defence") + stat("elementalDefence");
        }

        /**
         * An element's defence as WynnBuilder shows it: the pieces' own defences raised by the
         * element's % and the all-element % (a negative one shrinks with them: -250 at +20 % is -200).
         */
        public double defenceTotal(String element) {
            double raw = stat(Damage.defKey(element));
            double pct = defence(element) / 100.0;
            return raw < 0 ? raw - raw * pct : raw + raw * pct;
        }

        /** What could get you killed: negative regen or life steal, an element's defence far below zero. */
        public List<Warn> warnings() {
            List<Warn> out = new ArrayList<>();
            if (regen() < 0) {
                out.add(new Warn("regen", null, regen()));
            }
            if (stat("lifeSteal") < 0) {
                out.add(new Warn("steal", null, stat("lifeSteal")));
            }
            for (int i = 1; i < Damage.ELEMENTS.length; i++) {
                double d = defence(Damage.ELEMENTS[i]);
                if (d < LOW_DEFENCE) {
                    out.add(new Warn("defence", Damage.ELEMENTS[i], d));
                }
            }
            return out;
        }
    }

    /** One warning: "regen", "steal" or "defence" (with its element), and the value. */
    public record Warn(String kind, String element, double value) {}

    /** The applied plan's numbers and what they were worked out for ({key, Side}). */
    private static volatile Object[] planned;

    private BuildStats() {}

    /** What you wear: the pieces the plan scored as worn (requirements met) with your points; null before a plan. */
    public static Side worn(Build.Plan p) {
        if (p == null || p.current() == null || p.working() == null) {
            return null;
        }
        return of(p.working(), p.wornAllocation(), p.current(), p.tree(), p.level());
    }

    /**
     * The mod's suggested build (the plan's best set from worn and carried gear) with its own
     * skill point spread; null when it is what you wear with the points you placed.
     */
    public static Side suggested(Build.Plan p) {
        if (p == null || p.result() == null || p.pieces() == null) {
            return null;
        }
        if (java.util.Arrays.equals(p.allocation(), p.wornAllocation()) && sameNames(p.pieces(), p.working())) {
            return null;
        }
        return of(p.pieces(), p.allocation(), p.result(), p.tree(), p.level());
    }

    /** Whether both sets hold the same items by slot (by key: Wynncraft swaps the stacks). */
    static boolean sameNames(Map<String, Damage.Piece> a, Map<String, Damage.Piece> b) {
        for (String slot : Build.SLOTS) {
            Damage.Piece x = a.get(slot);
            Damage.Piece y = b == null ? null : b.get(slot);
            if (!(x == null ? y == null : y != null && x.key().equals(y.key()))) {
                return false;
            }
        }
        return true;
    }

    /** A build the window found, with its points, at the level it was found for. */
    public static Side candidate(Map<String, Damage.Piece> set, Build.Eval eval, int level) {
        TreeModel.Run run = TreeModel.run();
        Side s = of(set, eval.withTome(), eval.result(), run == null ? null : run.at(level), level);
        return eval.tome() != null && eval.tome().assumed() ? s.assuming(eval.tome()) : s;
    }

    /**
     * The applied plan: its pieces by name (what you have, else the item data's middle rolls) with
     * the tuning it was found with. Wearable now and without points of its own: at your level,
     * the points spread as the road map would. Otherwise as the build would be at its own level
     * ({@link #asBuilt}), with why it cannot be worn yet. Null without a piece to show; kept until
     * the plan or the build plan changes.
     */
    public static Side planned(Plans.Build b, Build.Plan now) {
        if (b == null || now == null) {
            return null;
        }
        String key = b.id() + "|" + b.updated() + "|" + System.identityHashCode(now);
        Object[] c = planned;
        if (c != null && key.equals(c[0])) {
            return (Side) c[1];
        }
        Side side;
        try {
            Map<String, Damage.Piece> set = piecesOf(b);
            side = set.isEmpty() ? null : Tuning.with(b.tuning(), () -> TreeModel.with(now.tree(), () -> {
                String why = whyNot(set, now.level(), now.earned());
                if (why == null && b.points() == null) {
                    Build.Eval e = Build.allocate(set, now.level(), now.earned());
                    if (e != null) {
                        return of(set, e.allocation(), e.result(), now.tree(), now.level());
                    }
                }
                Side built = asBuilt(set, b.points(), Math.max(now.level(), b.level()), now.tree());
                return built.at(built.level(), why);
            }));
        } catch (RuntimeException | Error t) {
            // kept as none too: the Home asks every frame, and a plan that fails fails again
            planned = new Object[] {key, null};
            throw t;
        }
        planned = new Object[] {key, side};
        return side;
    }

    /**
     * Why you cannot wear {@code set} at {@code level} with {@code earned} points: a piece above your
     * level, or the skill points it needs; null when you can.
     */
    static String whyNot(Map<String, Damage.Piece> set, int level, int earned) {
        int need = 0;
        for (Damage.Piece p : set.values()) {
            need = p == null ? need : Math.max(need, p.level());
        }
        if (need > level) {
            return io.github.anilken.wynnlabs.core.L.t("wynnlabs.stats.why_level", need);
        }
        return Build.whyNot(set, earned);
    }

    /**
     * {@code set} as it would be at {@code level} (at least every piece's level): with
     * {@code points} when it has its own (a WynnBuilder link's, assigned per skill), else the
     * road map's spread for that level, else the least its requirements take, past the cap if need be.
     */
    static Side asBuilt(Map<String, Damage.Piece> set, List<Integer> points, int level, TreeModel.Overlay tree) {
        int lv = level;
        for (Damage.Piece p : set.values()) {
            lv = p == null ? lv : Math.max(lv, p.level());
        }
        int[] pts = new int[SkillKind.values().length];
        if (points != null) {
            for (int i = 0; i < pts.length && i < points.size(); i++) {
                pts[i] = points.get(i) == null ? 0 : points.get(i);
            }
        } else {
            Build.Eval e = Build.allocate(set, lv, Math.max(0, (Math.min(lv, 101) - 1) * 2));
            pts = e != null ? e.allocation() : Build.minPoints(set.values());
        }
        List<Damage.Piece> armour = new ArrayList<>();
        set.forEach((slot, p) -> {
            if (!"WEAPON".equals(slot) && p != null) {
                armour.add(p);
            }
        });
        int[] fin = pts;
        int at = lv;
        Damage.Result r = TreeModel.with(tree, () -> Damage.evaluate(armour, set.get("WEAPON"), fin, at));
        return of(set, fin, r, tree, at).at(at, null);
    }

    /** A plan's pieces by build slot, found by name in what you have or the item data (middle rolls). */
    static Map<String, Damage.Piece> piecesOf(Plans.Build plan) {
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        GameState state = GameState.current();
        plan.items().forEach((slot, name) -> state.allGear().filter(i -> i.name().equals(name)).findFirst()
                .ifPresent(i -> set.put(slot, Damage.piece(i))));
        return set;
    }

    /**
     * The numbers for {@code set} worn with {@code points}, whose result is {@code r}, under the tree
     * {@code tree}; with its DPS spell by spell at {@code level} (0: none).
     */
    static Side of(Map<String, Damage.Piece> set, int[] points, Damage.Result r, TreeModel.Overlay tree, int level) {
        int[] pts = points == null ? new int[SkillKind.values().length] : points;
        return TreeModel.with(tree, () -> {
            List<Damage.Piece> armour = new ArrayList<>();
            set.forEach((slot, p) -> {
                if (!"WEAPON".equals(slot) && p != null) {
                    armour.add(p);
                }
            });
            Map<String, Double> s = Damage.totals(armour, set.get("WEAPON"));
            double intel = Damage.curve(Damage.skillPoints(s, pts, SkillKind.INTELLIGENCE));
            double def = Damage.defence(Damage.skillPoints(s, pts, SkillKind.DEFENCE)) / 100.0;
            // Result.ehp with Agility's dodge at 0: health over what Defence lets through, the class's share
            double ehpNoAgility = r.health() / (1 - def) / Damage.damageTaken();
            Damage.Breakdown parts = level > 0 ? Damage.detail(armour, set.get("WEAPON"), pts, level).breakdown() : null;
            return new Side(r, Map.copyOf(s), Damage.manaPool(s, intel), ehpNoAgility, 0, null, parts, null);
        });
    }
}
