package io.github.anilken.wynnlabs.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A crafting profession's road to a target level as ordered steps, one action each: get each
 * material (gather it, or buy it on the Trade Market), then craft. The levels are split into bands
 * where the recipes of the chosen item type keep the same materials ("Gold Ingot" and "Willow
 * Planks" from 23 to 29); each band gets its material steps, then its craft step. Plain data in,
 * plain data out (tested without the game).
 *
 * <p>The counts rest on {@link ProfessionPlan}: the XP of this level as the game shows it, later
 * levels estimated, and each recipe's base XP (crafting without ingredients). Ingredients, higher
 * material tiers and bombs give more, so the craft counts are an upper bound; an active Profession
 * XP bomb is taken in through {@code xpMultiplier}.
 */
public final class LevelRoad {

    private LevelRoad() {}

    /** How a material is got: gathered with its gathering profession, or bought. */
    public enum Route { GATHER, BUY }

    /** The player's default for every material step: gather, buy, or mixed (buy what is cheap, gather the rest). */
    public enum Pref { GATHER, BUY, MIXED }

    /** What a step asks: gather or buy a material, or craft. */
    public enum Kind { GATHER, BUY, CRAFT }

    /** Mixed buys a material when what is still missing costs at most this (one emerald block). */
    public static final long MIXED_BUY_LIMIT = 64;

    /** Where a material is gathered: the gathering profession ("mining"), the resource's level and name. */
    public record Source(String profession, int level, String resource) {}

    /**
     * What the road is built from. {@code owned} counts a material in the bag and the bank (every
     * tier); {@code source} the resource that gives it (null: not gathered); {@code gatherLevel} a
     * gathering profession's level (0: unknown, which locks nothing); {@code unitPrice} one piece's
     * price (null: none); {@code routes} the player's choice per material; {@code ticked} the keys of
     * steps marked done by hand.
     */
    public record Inputs(List<CraftData.Recipe> recipes, String profession, String type, int level, int target, int xpNow,
                         int xpNeed, Function<String, Integer> owned, Function<String, Source> source,
                         Function<String, Integer> gatherLevel, Function<String, Long> unitPrice, Pref pref,
                         Map<String, Route> routes, Set<String> ticked, double xpMultiplier) {}

    /**
     * One step. A material step: its material, how many in all ({@code need}, -1 when not known),
     * how many there are, where it is gathered and whether the level allows it, the price of one
     * and of what is still missing (null: unknown), the route. A craft step: the recipe, how many
     * crafts (-1: unknown) and the levels it takes the profession through.
     */
    public record Step(String key, Kind kind, String material, long need, long have, Source source, boolean gatherable,
                       Long unitPrice, Long cost, CraftData.Recipe recipe, long crafts, int fromLevel, int toLevel, Route route,
                       boolean done) {}

    /** A band of levels on one pair of materials: from, to, its first recipe and its materials. */
    public record Band(int from, int to, CraftData.Recipe recipe, List<CraftData.Material> materials, long crafts) {}

    /**
     * The road: its bands and steps in order, the step to do now (-1 when all are done), the XP
     * and crafts to the target (-1: unknown), what the bought materials cost (null when one has no
     * price) and how many pieces are still to gather.
     */
    public record Road(List<Band> bands, List<Step> steps, int current, long xp, long crafts, Long cost, long toGather) {}

    public static Road build(Inputs in) {
        String prof = in.profession().toLowerCase(java.util.Locale.ROOT);
        List<CraftData.Recipe> rs = new ArrayList<>();
        for (CraftData.Recipe r : in.recipes()) {
            if (r.profession().equals(prof) && r.type().equals(in.type()) && r.xp() > 0) {
                rs.add(r);
            }
        }
        if (rs.isEmpty() || in.target() <= in.level()) {
            return new Road(List.of(), List.of(), -1, 0, 0, 0L, 0);
        }
        long[] per = ProfessionPlan.xpPerLevel(in.recipes(), in.level(), in.target(), in.xpNow(), in.xpNeed());
        boolean known = per.length > 0;
        double mult = Math.max(1, in.xpMultiplier());
        // the bands: runs of levels whose recipe has the same materials
        List<Band> bands = new ArrayList<>();
        int from = in.level();
        long xpAll = 0;
        long craftsAll = 0;
        while (from < in.target()) {
            CraftData.Recipe first = ProfessionPlan.forLevel(rs, from);
            String mats = matKey(first);
            int to = from;
            long crafts = 0;
            while (to < in.target() && matKey(ProfessionPlan.forLevel(rs, to)).equals(mats)) {
                // a run of levels on one recipe is crafted together: its XP summed, then divided
                CraftData.Recipe r = ProfessionPlan.forLevel(rs, to);
                long xp = 0;
                while (to < in.target() && ProfessionPlan.forLevel(rs, to) == r) {
                    xp += known ? per[to - in.level()] : 0;
                    to++;
                }
                xpAll += xp;
                crafts += known ? ProfessionPlan.actions(xp, r.xp() * mult) : 0;
            }
            bands.add(new Band(from, to, first, first.materials(), known ? crafts : -1));
            craftsAll += known ? crafts : 0;
            from = to;
        }
        // the steps: each band's materials, then its craft; what is owned counts once, for the first band needing it
        Map<String, Long> left = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();
        Long cost = 0L;
        long toGather = 0;
        for (Band b : bands) {
            for (CraftData.Material m : b.materials()) {
                String name = m.shown();
                long need = b.crafts() < 0 ? -1 : b.crafts() * m.amount();
                long owned = left.computeIfAbsent(name, k -> (long) Math.max(0, in.owned().apply(k)));
                long have = need < 0 ? owned : Math.min(owned, need);
                left.put(name, owned - Math.max(0, have));
                Source src = in.source().apply(name);
                int gl = src == null ? 0 : in.gatherLevel().apply(src.profession());
                boolean gatherable = src != null && (gl <= 0 || gl >= src.level());
                Long unit = in.unitPrice().apply(name);
                long missing = need < 0 ? -1 : Math.max(0, need - have);
                Route route = in.routes().get(name);
                if (route == null) {
                    route = defaultRoute(in.pref(), gatherable, unit, missing);
                }
                Long stepCost = Long.valueOf(0);
                if (route == Route.BUY) {
                    stepCost = missing >= 0 && unit != null ? Long.valueOf(unit * missing) : null;
                }
                if (missing > 0 && route == Route.BUY) {
                    cost = cost == null || stepCost == null ? null : Long.valueOf(cost + stepCost);
                }
                if (missing > 0 && route == Route.GATHER) {
                    toGather += missing;
                }
                String key = b.from() + ":" + name;
                boolean done = need >= 0 && have >= need || in.ticked().contains(key);
                steps.add(new Step(key, route == Route.GATHER ? Kind.GATHER : Kind.BUY, name, need, have, src, gatherable, unit,
                        stepCost, b.recipe(), b.crafts(), b.from(), b.to(), route, done));
            }
            String key = b.from() + ":craft";
            boolean done = in.level() >= b.to() || in.ticked().contains(key);
            steps.add(new Step(key, Kind.CRAFT, null, -1, 0, null, true, null, null, b.recipe(), b.crafts(), b.from(), b.to(), null,
                    done));
        }
        int current = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).done()) {
                current = i;
                break;
            }
        }
        return new Road(List.copyOf(bands), List.copyOf(steps), current, known ? xpAll : -1, known ? craftsAll : -1, cost, toGather);
    }

    /**
     * The route a material gets without the player's choice: buy when it cannot be gathered at
     * this level; else gather, or buy what costs little ({@link #MIXED_BUY_LIMIT}) when mixed.
     */
    public static Route defaultRoute(Pref pref, boolean gatherable, Long unitPrice, long missing) {
        if (!gatherable || pref == Pref.BUY) {
            return Route.BUY;
        }
        if (pref == Pref.MIXED && unitPrice != null && missing >= 0 && unitPrice * missing <= MIXED_BUY_LIMIT) {
            return Route.BUY;
        }
        return Route.GATHER;
    }

    private static String matKey(CraftData.Recipe r) {
        StringBuilder b = new StringBuilder();
        for (CraftData.Material m : r.materials()) {
            b.append(m.shown()).append('|');
        }
        return b.toString();
    }
}
