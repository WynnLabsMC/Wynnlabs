package io.github.anilken.wynnlabs.crafting;

import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The crafting tab's profession planner: how much XP a target level is away, which recipe tier
 * gets a crafting profession there in the fewest crafts and what its materials cost, and the
 * nearest gathering spots a gathering profession can use. Plain data in, plain data out.
 *
 * <p>The game only tells the XP of the level the player is in. Later levels are estimated: all
 * professions share one level curve, and the recipes' XP grows with level in step with it, so a
 * later level is taken to need this level's XP times how much more the recipes of that level give
 * (the shipped recipes, every type averaged). Every count built on that is shown with "~".
 */
public final class ProfessionPlan {

    /** The game's highest profession level. */
    public static final int MAX_LEVEL = 132;

    /**
     * One way to level a crafting profession: an item type, the recipe of the player's level, how
     * many crafts reach the target (-1: not known), the materials of all of them (shown name at
     * tier 1 → count) and what they cost at the cached prices (null when one has no price;
     * {@code unpriced} names those).
     */
    public record CraftOption(String type, CraftData.Recipe recipe, long crafts, Map<String, Long> materials, Long cost,
                              List<String> unpriced) {}

    /** A gathering resource: its name ("Copper"), profession ("mining"), level, nodes and the materials it gives. */
    public record Resource(String name, String profession, int level, List<Area> nodes, List<String> materials) {}

    /** How far from a spot's node (blocks) other nodes still count as the same spot. */
    private static final int SPOT_RADIUS = 64;
    /** How many blocks of walking one more node at a spot is worth: a big field a little farther beats a small patch. */
    static final int NODE_WALK = 25;
    /** A resource's node list → how many of its nodes lie around each one (by identity: the lists are kept). */
    private static final java.util.Map<List<Area>, int[]> AROUND =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /**
     * A resource's nearest node to the player, how far it is (NaN when the position is unknown)
     * and how many of its nodes are at that spot, the node itself included.
     */
    public record Spot(Resource resource, Area node, double distance, int nodes) {}

    private ProfessionPlan() {}

    /** The target level kept in 2..{@link #MAX_LEVEL} and above the current level. */
    public static int clampTarget(int level, int target) {
        return Math.max(Math.min(MAX_LEVEL, Math.max(1, level) + 1), Math.min(MAX_LEVEL, target));
    }

    /**
     * How much the recipes of a level give compared with the others: the XP of each type's recipe
     * for that level (the highest bracket starting at or below it), averaged over the types.
     */
    public static double scale(List<CraftData.Recipe> recipes, int level) {
        Map<String, Integer> best = new LinkedHashMap<>();
        Map<String, Integer> bestMin = new LinkedHashMap<>();
        for (CraftData.Recipe r : recipes) {
            if (r.minLevel() <= level && r.xp() > 0 && r.minLevel() >= bestMin.getOrDefault(r.type(), Integer.MIN_VALUE)) {
                bestMin.put(r.type(), r.minLevel());
                best.put(r.type(), r.xp());
            }
        }
        return best.values().stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /**
     * The XP each level from {@code level} to {@code target - 1} still needs: the rest of this
     * one ({@code xpNeed - xpNow}), then the estimate for the later ones. Empty when the level's
     * XP is not known ({@code xpNeed} 0) or the target is not above the level.
     */
    public static long[] xpPerLevel(List<CraftData.Recipe> recipes, int level, int target, int xpNow, int xpNeed) {
        if (xpNeed <= 0 || target <= level || level < 1) {
            return new long[0];
        }
        long[] out = new long[target - level];
        out[0] = Math.max(0, xpNeed - xpNow);
        double here = scale(recipes, level);
        for (int l = level + 1; l < target; l++) {
            double s = scale(recipes, l);
            out[l - level] = here > 0 && s > 0 ? Math.round(xpNeed * s / here) : xpNeed;
        }
        return out;
    }

    /** The XP from now to the target, or -1 when it cannot be told. */
    public static long xpToTarget(List<CraftData.Recipe> recipes, int level, int target, int xpNow, int xpNeed) {
        long[] per = xpPerLevel(recipes, level, target, xpNow, xpNeed);
        if (per.length == 0) {
            return -1;
        }
        long sum = 0;
        for (long x : per) {
            sum += x;
        }
        return sum;
    }

    /** Actions of {@code each} XP that give {@code xp}: rounded up; -1 when either is not known. */
    public static long actions(long xp, double each) {
        if (xp < 0 || each <= 0) {
            return -1;
        }
        return (long) Math.ceil(xp / each);
    }

    /**
     * The ways to level a crafting profession ("jeweling") from {@code level} to {@code target}:
     * one per item type, each level crafted with the highest recipe it allows. Fewest crafts first
     * (the fastest); without the level's XP, most XP per craft first. {@code materialPrice} gives
     * a tier 1 material's cached price by shown name ("Copper Gem"), null when there is none.
     */
    public static List<CraftOption> crafting(List<CraftData.Recipe> recipes, String profession, int level, int target,
                                             int xpNow, int xpNeed, Function<String, Long> materialPrice) {
        String prof = profession.toLowerCase(Locale.ROOT);
        long[] per = xpPerLevel(recipes, level, target, xpNow, xpNeed);
        Map<String, List<CraftData.Recipe>> byType = new LinkedHashMap<>();
        for (CraftData.Recipe r : recipes) {
            if (r.profession().equals(prof) && r.xp() > 0) {
                byType.computeIfAbsent(r.type(), k -> new ArrayList<>()).add(r);
            }
        }
        List<CraftOption> out = new ArrayList<>();
        for (Map.Entry<String, List<CraftData.Recipe>> e : byType.entrySet()) {
            List<CraftData.Recipe> rs = e.getValue();
            CraftData.Recipe now = forLevel(rs, level);
            Map<String, Long> mats = new LinkedHashMap<>();
            long crafts = -1;
            if (per.length > 0) {
                // a run of levels on one recipe is crafted together: its XP summed, then divided
                crafts = 0;
                int i = 0;
                while (i < per.length) {
                    CraftData.Recipe r = forLevel(rs, level + i);
                    long xp = 0;
                    while (i < per.length && forLevel(rs, level + i) == r) {
                        xp += per[i++];
                    }
                    long n = actions(xp, r.xp());
                    crafts += n;
                    for (CraftData.Material m : r.materials()) {
                        mats.merge(m.shown(), n * m.amount(), Long::sum);
                    }
                }
            } else {
                for (CraftData.Material m : now.materials()) {
                    mats.merge(m.shown(), (long) m.amount(), Long::sum);
                }
            }
            Long cost = 0L;
            List<String> unpriced = new ArrayList<>();
            for (Map.Entry<String, Long> m : mats.entrySet()) {
                Long p = materialPrice == null ? null : materialPrice.apply(m.getKey());
                if (p == null) {
                    unpriced.add(m.getKey());
                } else if (cost != null) {
                    cost += p * m.getValue();
                }
            }
            out.add(new CraftOption(e.getKey(), now, crafts, mats, unpriced.isEmpty() ? cost : null, List.copyOf(unpriced)));
        }
        Comparator<CraftOption> order = per.length > 0
                ? Comparator.comparingLong(CraftOption::crafts)
                : Comparator.comparingInt((CraftOption o) -> -o.recipe().xp());
        out.sort(order.thenComparing(CraftOption::type));
        return out;
    }

    /** The option whose materials cost least, among those with every price known; null when none. */
    public static CraftOption cheapest(List<CraftOption> options) {
        return options.stream().filter(o -> o.cost() != null).min(Comparator.comparingLong(CraftOption::cost)).orElse(null);
    }

    /** The highest recipe whose bracket starts at or below the level (the lowest when none does). */
    static CraftData.Recipe forLevel(List<CraftData.Recipe> rs, int level) {
        CraftData.Recipe best = rs.get(0);
        for (CraftData.Recipe r : rs) {
            if (r.minLevel() <= level && r.minLevel() >= best.minLevel()) {
                best = r;
            }
        }
        return best;
    }

    /**
     * The gathering spots for a profession ("mining") at a level: each resource of that
     * profession the level allows, at its nearest dense spot (4 nodes or more within reach; the
     * densest it has when none is that dense), highest level first (it gives the most XP), then
     * the nearest; at most {@code limit}. Without a position the nodes keep the data's order and
     * the distance is NaN.
     */
    public static List<Spot> spots(List<Resource> resources, String profession, int level, double[] pos, int limit) {
        String prof = profession.toLowerCase(Locale.ROOT);
        List<Spot> out = new ArrayList<>();
        for (Resource r : resources) {
            if (!prof.equals(r.profession()) || r.level() > level || r.nodes().isEmpty()) {
                continue;
            }
            // a lone node is no place to gather: the nodes around each one, then the nearest
            // node with enough of them
            // (the counts never change: worked out once per node list, not on every move)
            if (AROUND.size() > 500) {
                AROUND.clear();                 // the item index was read again: old lists go
            }
            int[] around = AROUND.computeIfAbsent(r.nodes(), nodes -> {
                int[] counts = new int[nodes.size()];
                for (int i = 0; i < counts.length; i++) {
                    Area c = nodes.get(i);
                    double[] at = {c.x(), c.z()};
                    counts[i] = (int) nodes.stream().filter(a -> Compass.distance(a, at) <= SPOT_RADIUS).count();
                }
                return counts;
            });
            int most = 0;
            for (int n : around) {
                most = Math.max(most, n);
            }
            int enough = Math.min(most, 4);
            // the spot worth the walk: its distance less what its nodes are worth
            Area best = null;
            double bestD = Double.NaN;
            double bestScore = Double.MAX_VALUE;
            int here = 0;
            for (int i = 0; i < around.length; i++) {
                if (around[i] < enough) {
                    continue;
                }
                Area a = r.nodes().get(i);
                double d = Compass.distance(a, pos);
                double score = (Double.isNaN(d) ? 0 : d) - (double) NODE_WALK * around[i];
                if (best == null || score < bestScore) {
                    best = a;
                    bestD = d;
                    bestScore = score;
                    here = around[i];
                }
            }
            out.add(new Spot(r, best, bestD, here));
        }
        out.sort(Comparator.comparingInt((Spot s) -> -s.resource().level())
                .thenComparingDouble(s -> Double.isNaN(s.distance()) ? Double.MAX_VALUE : s.distance())
                .thenComparing(s -> s.resource().name()));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }
}
