package io.github.anilken.wynnlabs.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A guild's economy an hour, as far as the map can tell: what its territories make (the game
 * sends it) against what their upgrades likely cost. Another guild's upgrade levels are not
 * shown, so the cost is put together from what is:
 * <ul>
 * <li>the tower: each territory's defence rating stands for the setups war guilds use for it
 * (Medium about 3 in every stat with aura and volley 1, High about 6, Very High about 9 with
 * aura 3 and volley 2; an HQ about damage 8, attack 9, health 11, defence 8, aura and volley 3
 * and multi-attack). Damage eats ore, attack crops, health wood, defence fish, aura crops,
 * volley ore, multi-attack fish;</li>
 * <li>storage: a territory's capacities tell its storage bonuses exactly (300 resources and
 * 3000 emeralds, an HQ 1500 and 5000, times the bonus);</li>
 * <li>production: how far its output is above the base (9000 emeralds, 3600 of a resource an
 * hour, raised by its treasury) tells the efficiency and rate bonuses, roughly.</li>
 * </ul>
 * Upgrade costs are paid every hour. Per level they are the game's own (its upgrade tooltips).
 */
final class GuildEco {

    private GuildEco() {}

    /** A tower upgrade level's cost an hour, levels 0 to 11 (the same for every tower stat). */
    static final int[] LEVEL_COST = {0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800};
    static final int[] AURA_COST = {0, 800, 1600, 3200};
    static final int[] VOLLEY_COST = {0, 200, 400, 800};
    static final int MULTI_COST = 4800;
    /** Storage bonus levels: the capacity added in %, and their costs (resources' in emeralds, emeralds' in wood). */
    static final int[] STORAGE_PCT = {0, 100, 300, 700, 1400, 3300, 7900};
    static final int[] RES_STORAGE_COST = {0, 400, 800, 2000, 5000, 16000, 48000};
    static final int[] EM_STORAGE_COST = {0, 200, 400, 1000, 2500, 8000, 24000};
    /** Efficiency bonuses: the output added in %, and the cost (resources' in emeralds, emeralds' in ore). */
    static final int[] EFF_RES_PCT = {0, 50, 100, 150, 200, 250, 300};
    static final int[] EFF_RES_COST = {0, 6000, 12000, 24000, 48000, 96000, 192000};
    static final int[] EFF_EM_PCT = {0, 35, 100, 300};
    static final int[] EFF_EM_COST = {0, 2000, 8000, 32000};
    /** Rate bonuses: seconds between gathers (4 at none), and the cost (resources' in emeralds, emeralds' in crops). */
    static final int[] RATE_SECONDS = {4, 3, 2, 1};
    static final int[] RES_RATE_COST = {0, 6000, 18000, 32000};
    static final int[] EM_RATE_COST = {0, 2000, 8000, 32000};
    /** The treasury's output bonus by its rating, 1 very low … 5 very high (near the HQ). */
    static final double[] TREASURY = {0, 0, 0.10, 0.20, 0.25, 0.30};

    /** The typical tower behind a rating (1 very low … 5 very high): {damage, attack, health, defence, aura, volley, multi}. */
    static int[] tower(int rating, boolean hq) {
        if (hq && rating >= 5) {
            return new int[] {8, 9, 11, 8, 3, 3, 1};
        }
        int r = hq ? Math.max(1, rating - 1) : rating;     // an HQ shows one rating above its upgrades
        return switch (r) {
            case 2 -> new int[] {2, 2, 2, 2, 0, 0, 0};
            case 3 -> new int[] {3, 3, 3, 3, 1, 1, 0};
            case 4 -> new int[] {6, 6, 6, 6, 1, 1, 0};
            case 5 -> new int[] {9, 9, 9, 9, 3, 2, 0};
            default -> new int[] {0, 0, 0, 0, 0, 0, 0};
        };
    }

    /**
     * Per resource (emeralds, ore, wood, fish, crops as the game lists them): {made, cost,
     * whether a cost is known} an hour, over the guild's territories.
     */
    static Map<String, long[]> of(String guild, List<GuildMapLook.Territory> all) {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (GuildMapLook.Territory t : all) {
            if (!guild.equals(t.guild())) {
                continue;
            }
            for (GuildMapLook.Res r : t.res()) {
                out.computeIfAbsent(r.key(), k -> new long[3])[0] += r.perHour();
            }
            if (!t.known()) {
                continue;
            }
            // the tower
            if (t.defenceLevel() > 0) {
                int[] tw = tower(t.defenceLevel(), t.hq());
                add(out, "ORE", LEVEL_COST[tw[0]] + VOLLEY_COST[tw[5]]);
                add(out, "CROPS", LEVEL_COST[tw[1]] + AURA_COST[tw[4]]);
                add(out, "WOOD", LEVEL_COST[tw[2]]);
                add(out, "FISH", LEVEL_COST[tw[3]] + (tw[6] > 0 ? MULTI_COST : 0));
            }
            // storage, from the capacities
            int resCap = 0;
            int emCap = 0;
            for (GuildMapLook.Res r : t.res()) {
                if (r.key().equals("EMERALDS")) {
                    emCap = r.cap();
                } else {
                    resCap = Math.max(resCap, r.cap());
                }
            }
            add(out, "EMERALDS", RES_STORAGE_COST[storageLevel(resCap, t.hq() ? 1500 : 300)]);
            add(out, "WOOD", EM_STORAGE_COST[storageLevel(emCap, t.hq() ? 5000 : 3000)]);
            // efficiency and rate, from how far the output is above the base
            double treasury = TREASURY[Math.max(0, Math.min(5, t.treasuryLevel()))];
            double emRatio = 0;
            double resRatio = 0;
            for (GuildMapLook.Res r : t.res()) {
                if (r.perHour() <= 0) {
                    continue;
                }
                if (r.key().equals("EMERALDS")) {
                    emRatio = r.perHour() / (9000.0 * (1 + treasury));
                } else {
                    resRatio = Math.max(resRatio, r.perHour() / (3600.0 * (1 + treasury)));
                }
            }
            int[] em = bonuses(emRatio, EFF_EM_PCT, EFF_EM_COST, EM_RATE_COST, true);
            add(out, "ORE", em[0]);
            add(out, "CROPS", em[1]);
            int[] res = bonuses(resRatio, EFF_RES_PCT, EFF_RES_COST, RES_RATE_COST, false);
            add(out, "EMERALDS", res[0] + res[1]);
        }
        out.values().removeIf(v -> v[0] <= 0 && v[1] <= 0);
        return out;
    }

    /** The storage bonus level whose capacity is nearest {@code cap} over {@code base}. */
    static int storageLevel(int cap, int base) {
        int best = 0;
        double gap = Double.MAX_VALUE;
        for (int i = 0; i < STORAGE_PCT.length; i++) {
            double d = Math.abs(cap - base * (1 + STORAGE_PCT[i] / 100.0));
            if (d < gap) {
                gap = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * The cheapest efficiency and rate levels whose output reaches {@code ratio} times the base
     * (within 8%; a city's doubled emeralds count for free): {efficiency cost, rate cost}.
     */
    static int[] bonuses(double ratio, int[] pct, int[] effCost, int[] rateCost, boolean emeralds) {
        if (ratio < 1.15) {
            return new int[] {0, 0};
        }
        int[] best = {0, 0};
        long bestCost = Long.MAX_VALUE;
        double bestGap = Double.MAX_VALUE;
        for (int city = 1; city <= (emeralds ? 2 : 1); city++) {
            for (int e = 0; e < pct.length; e++) {
                for (int r = 0; r < RATE_SECONDS.length; r++) {
                    double m = city * (1 + pct[e] / 100.0) * (4.0 / RATE_SECONDS[r]);
                    double gap = Math.abs(m - ratio) / ratio;
                    long cost = (long) effCost[e] + rateCost[r];
                    boolean close = gap <= 0.08;
                    if (close && (bestGap > 0.08 || cost < bestCost) || !close && bestGap > 0.08 && gap < bestGap) {
                        best = new int[] {effCost[e], rateCost[r]};
                        bestCost = cost;
                        bestGap = gap;
                    }
                }
            }
        }
        return best;
    }

    private static void add(Map<String, long[]> out, String key, long cost) {
        if (cost <= 0) {
            out.computeIfAbsent(key, k -> new long[3])[2] = 1;
            return;
        }
        long[] v = out.computeIfAbsent(key, k -> new long[3]);
        v[1] += cost;
        v[2] = 1;
    }
}
