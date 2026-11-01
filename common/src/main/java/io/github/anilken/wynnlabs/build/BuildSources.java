package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.endgame.PoolView;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.market.ShoppingSources;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a build's pieces come from, for the build window and the panel: the item database's
 * piece by name, what you wear and have (re-read each second), this week's raid pools, the
 * Trade Market price, a quest, merchant, chest or drop.
 */
public final class BuildSources {

    private BuildSources() {}

    private static Map<String, String> wornCache = Map.of();
    private static long wornAt;

    /** What you wear now, by build slot (names); read again each second. */
    public static Map<String, String> worn() {
        long now = System.currentTimeMillis();
        if (now - wornAt < 1000) {
            return wornCache;
        }
        Map<String, String> out = new LinkedHashMap<>();
        Build.Plan p = Build.plan();
        if (p != null && p.worn() != null) {
            p.worn().forEach((s, piece) -> {
                if (piece != null) {
                    out.put(s, piece.name());
                }
            });
        }
        wornCache = out;
        wornAt = now;
        return out;
    }

    /** Forgets what was worn and had, so the next ask reads them again (a test's new game). */
    static void forgetReads() {
        wornAt = 0;
        haveAt = 0;
    }

    /** The item database's piece of this name (looked up once). */
    private static final Map<String, java.util.Optional<ItemData>> INFO = new java.util.concurrent.ConcurrentHashMap<>();

    public static ItemData itemOf(String name) {
        return INFO.computeIfAbsent(name, n -> GameState.current().allGear().filter(i -> i.name().equals(n)).findFirst()).orElse(null);
    }

    private static Map<String, Integer> haveCache = Map.of();
    private static long haveAt;

    /** Names of what you have in the bag and bank. */
    public static java.util.Set<String> haveNames() {
        return haveCounts().keySet();
    }

    /** What you have in the bag and bank, by name and how many; read again each second. */
    private static Map<String, Integer> haveCounts() {
        long now = System.currentTimeMillis();
        if (now - haveAt < 1000) {
            return haveCache;
        }
        haveAt = now;
        Map<String, Integer> out = new java.util.HashMap<>();
        haveCache = out;
        try {
            for (ItemStack s : GameState.current().inventory()) {
                if (!s.isEmpty()) {
                    out.merge(ItemJson.clean(s.getHoverName().getString()).trim(), s.getCount(), Integer::sum);
                }
            }
            for (Vault.Group gr : Vault.groups()) {
                out.merge(gr.name(), gr.total(), Integer::sum);
            }
        } catch (Throwable ignored) {
            // what was read
        }
        return out;
    }

    /** The slots of a build you already have a piece for: see {@link #ownedSlots(Map, Map, Map)}. */
    public static java.util.Set<String> ownedSlots(Map<String, Damage.Piece> set, Map<String, String> worn) {
        return ownedSlots(set, worn, haveCounts());
    }

    /**
     * One copy fills one slot: a build with the same ring twice needs two of them. What is worn
     * where the build keeps it takes its copy first, as a worn ring also sits in the bag.
     */
    static java.util.Set<String> ownedSlots(Map<String, Damage.Piece> set, Map<String, String> worn, Map<String, Integer> have) {
        Map<String, Integer> left = new java.util.HashMap<>(have);
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String slot : Plans.SLOTS) {
            Damage.Piece p = set.get(slot);
            if (p != null && p.name().equals(worn.get(slot))) {
                out.add(slot);
                left.merge(p.name(), -1, Integer::sum);
            }
        }
        for (String slot : Plans.SLOTS) {
            Damage.Piece p = set.get(slot);
            if (p == null || out.contains(slot)) {
                continue;
            }
            if (p.stack() != null || left.getOrDefault(p.name(), 0) > 0) {
                out.add(slot);
                left.merge(p.name(), -1, Integer::sum);
            }
        }
        return out;
    }

    /**
     * The icon of where a piece comes from, by the same order as {@link #source}: this week's
     * pool (the raid trophy), the Trade Market (a coin), a merchant (the cart), a quest or a quest
     * item (the scroll), a cave or loot chest (the box), mobs (the sword), a dungeon (the
     * skull); a question mark when nothing is known.
     */
    public static String sourceIcon(String name) {
        if (poolOf(name) != null) {
            return "trophy";
        }
        ItemData info = itemOf(name);
        if (info == null) {
            return "question-mark";
        }
        boolean tradable = Shopping.tradable(info);
        if (tradable) {
            Prices.Price p = GameState.current().price(name);
            if (p != null && p.typical() != null) {
                return "coin";
            }
        }
        String src = ShoppingSources.origin(info);
        boolean known = src != null && !src.isBlank() && !src.toUpperCase(Locale.ROOT).startsWith("UNKNOWN");
        if (known) {
            return kindIcon(ShoppingSources.kind(src));
        }
        if ("QUEST_ITEM".equals(info.restriction())) {
            return "script";
        }
        return tradable ? "coin" : "question-mark";
    }

    /** The badge of a {@link Shopping#kind} ("merchant" → the cart); the search tab's sources use it too. */
    public static String kindIcon(String kind) {
        return switch (kind) {
            case "merchant" -> "shopping-cart";
            case "quest" -> "script";
            case "chest" -> "box";
            case "drop" -> "sword";
            case "dungeon" -> "skull";
            default -> "question-mark";
        };
    }

    /**
     * One more line about where a piece comes from, under {@link #sourceText}, or null: the mob
     * area of a drop or the region of a chest (the line itself names neither), or, when the
     * line is the Trade Market's price, the place it also comes from.
     */
    public static String sourceDetail(String name) {
        if (poolOf(name) != null) {
            return null;
        }
        ItemData info = itemOf(name);
        if (info == null) {
            return null;
        }
        String src = ShoppingSources.origin(info);
        if (src == null || src.isBlank() || src.toUpperCase(Locale.ROOT).startsWith("UNKNOWN")) {
            return null;
        }
        if (source(name)[1] != null) {
            return L.t("wynnlabs.homev.source_also", ShoppingSources.where(src));
        }
        String[] parts = src.split(": ", 2);
        String place = parts.length > 1 ? parts[1].trim() : "";
        if (place.isEmpty()) {
            return null;
        }
        return switch (parts[0]) {
            case "NORMAL_MOB_DROP", "MOB_REGION" -> L.t("wynnlabs.homev.source_area", place);
            case "CAVE_LOOT_CHEST", "CAVE_COMPLETION", "LOOT_CHEST" -> L.t("wynnlabs.homev.source_region", place);
            default -> null;
        };
    }

    /** Where a piece comes from, as one line (a Trade Market price written out). */
    public static String sourceText(String name) {
        Object[] s = source(name);
        return s[1] != null ? L.t("wynnlabs.results.market", Prices.fmt((double) s[1])) : (String) s[0];
    }

    /**
     * Where a piece comes from: {text, Trade Market price (null when none)}: this week's raid
     * pool, else its Trade Market price, a quest, its merchant, chest or drop; with no known
     * source, a quest item as a quest reward, one you have as such, an untradable piece as such, a tradable one as
     * the Trade Market with no price; "price unknown" only when nothing is known.
     */
    public static Object[] source(String name) {
        String pool = poolOf(name);
        if (pool != null) {
            return new Object[] {L.t("wynnlabs.results.in_pool", pool), null};
        }
        ItemData info = itemOf(name);
        if (info == null) {
            return new Object[] {L.t("wynnlabs.results.price_unknown"), null};
        }
        // with what earning it takes when the seller wants no emeralds (Tempo Town: Time Fragments)
        String src = ShoppingSources.withCost(info, ShoppingSources.origin(info));
        boolean tradable = Shopping.tradable(info);
        if (tradable) {
            Prices.Price p = GameState.current().price(name);
            if (p != null && p.typical() != null) {
                return new Object[] {"", p.typical()};
            }
        }
        boolean known = src != null && !src.isBlank() && !src.toUpperCase(Locale.ROOT).startsWith("UNKNOWN");
        if (known && src.startsWith("QUEST")) {
            return new Object[] {L.t("wynnlabs.results.quest", ShoppingSources.where(src)), null};
        }
        if (known) {
            return new Object[] {L.cap(ShoppingSources.where(src)), null};
        }
        if ("QUEST_ITEM".equals(info.restriction())) {
            return new Object[] {L.t("wynnlabs.results.quest_reward"), null};
        }
        if (haveNames().contains(name) || worn().containsValue(name)) {
            // kept because you have it: nothing says where another one comes from
            return new Object[] {L.t("wynnlabs.results.unknown_have"), null};
        }
        if (!tradable) {
            return new Object[] {L.t("wynnlabs.results.untradable"), null};
        }
        return new Object[] {L.t("wynnlabs.results.market_unknown"), null};
    }

    private static Map<String, String> pools;
    private static long poolsAt;

    /** The raid whose pool this week holds the item, or null. */
    private static String poolOf(String name) {
        long now = System.currentTimeMillis();
        if (pools == null || now - poolsAt > 30_000) {
            Map<String, String> m = new java.util.HashMap<>();
            try {
                List<PoolView.Region> regions = PoolView.raidRegions();
                for (PoolView.Region reg : regions == null ? List.<PoolView.Region>of() : regions) {
                    for (PoolView.Group gr : PoolView.raid(reg.name(), false)) {
                        for (PoolView.Row row : gr.rows()) {
                            m.putIfAbsent(row.item().name(), reg.name());
                        }
                    }
                }
            } catch (Throwable ignored) {
                // no pool data
            }
            pools = m;
            poolsAt = now;
        }
        return pools.get(name);
    }
}
