package io.github.anilken.wynnlabs.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A guild's economy an hour, as far as the map can tell: what its territories make (the game
 * sends it) against what their tower upgrades likely cost. The upgrades of another guild's
 * territories are not shown, only each one's defence rating, so the cost is a guess from the
 * rating: the typical levels behind it, each level's hourly cost per stat (damage eats ore,
 * attack crops, health wood, defence fish; aura crops, volley ore). An HQ shows one rating
 * higher than its upgrades. Emeralds' costs (storage and rate bonuses) cannot be guessed.
 */
final class GuildEco {

    private GuildEco() {}

    /** An upgrade level's cost an hour, levels 0 to 11 (the same for every tower stat). */
    static final int[] LEVEL_COST = {0, 100, 300, 600, 1200, 2400, 4800, 8400, 12000, 15600, 19200, 22800};
    /** Aura's and volley's costs an hour, levels 0 to 3. */
    static final int[] AURA_COST = {0, 800, 1600, 3200};
    static final int[] VOLLEY_COST = {0, 200, 400, 800};

    /** The typical tower level, aura and volley behind a rating (1 very low … 5 very high). */
    static int[] typical(int rating) {
        return switch (rating) {
            case 2 -> new int[] {3, 0, 0};
            case 3 -> new int[] {6, 0, 0};
            case 4 -> new int[] {8, 1, 1};
            case 5 -> new int[] {11, 3, 3};
            default -> new int[] {0, 0, 0};
        };
    }

    /**
     * Per resource (emeralds, ore, wood, fish, crops as the game lists them): {made, cost,
     * whether a cost is guessed} an hour, over the guild's territories.
     */
    static Map<String, long[]> of(String guild, List<GuildMapLook.Territory> all) {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (GuildMapLook.Territory t : all) {
            if (!guild.equals(t.guild())) {
                continue;
            }
            for (GuildMapLook.Res r : t.res()) {
                long[] v = out.computeIfAbsent(r.key(), k -> new long[3]);
                v[0] += r.perHour();
                if (!r.key().equals("EMERALDS")) {
                    v[2] = 1;
                }
            }
            if (!t.known() || t.defenceLevel() <= 0) {
                continue;
            }
            int rating = t.hq() ? Math.max(1, t.defenceLevel() - 1) : t.defenceLevel();
            int[] ty = typical(rating);
            int each = LEVEL_COST[ty[0]];
            add(out, "ORE", each + VOLLEY_COST[ty[2]]);
            add(out, "CROPS", each + AURA_COST[ty[1]]);
            add(out, "WOOD", each);
            add(out, "FISH", each);
        }
        out.values().removeIf(v -> v[0] <= 0 && v[1] <= 0);
        return out;
    }

    private static void add(Map<String, long[]> out, String key, long cost) {
        long[] v = out.computeIfAbsent(key, k -> new long[3]);
        v[1] += cost;
        v[2] = 1;
    }
}
