package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Obtain;
import io.github.anilken.wynnlabs.quests.QuestBook;

import java.util.List;

/**
 * Where a piece of gear comes from, for the shopping list and everything that shows it: the raw
 * source ("MERCHANT: Ragni", the Trade Market first when it is listed there now), the same in
 * words for people, its kind for code that decides by it, the second way to it, and whether a
 * rule, a quest or a run of its own stands in the way.
 */
public final class ShoppingSources {

    private ShoppingSources() {}

    /** "MERCHANT: Detlas" becomes "Detlas tüccarı" ("Detlas merchant") and so on, for tooltips. */
    public static String where(String source) {
        String[] parts = source.split(": ", 2);
        String name = parts.length > 1 ? parts[1] : "";
        return switch (parts[0]) {
            case "TRADE_MARKET" -> L.t("wynnlabs.shop.where_market");
            case "MERCHANT" -> L.t("wynnlabs.shop.where_merchant", name);
            case "DUNGEON_MERCHANT" -> L.t("wynnlabs.shop.where_dungeon_merchant", name);
            case "QUEST" -> L.t("wynnlabs.shop.where_quest", name);
            case "DUNGEON" -> L.t("wynnlabs.shop.where_dungeon", name);
            case "CAVE_LOOT_CHEST", "CAVE_COMPLETION" -> L.t("wynnlabs.shop.where_cave_chest");
            case "LOOT_CHEST" -> L.t("wynnlabs.shop.where_loot_chest");
            case "NORMAL_MOB_DROP", "MOB_REGION" -> L.t("wynnlabs.shop.where_mob_drop");
            case "SPECIFIC_MOB_DROP", "MINIBOSS" -> L.t("wynnlabs.shop.where_dropped_by", name);
            case "WORLD_EVENT" -> L.t("wynnlabs.shop.where_event", name);
            case "BOSS_ALTAR" -> L.t("wynnlabs.shop.where_altar", name);
            case "RAID" -> L.t("wynnlabs.shop.where_raid", name);
            case "CHALLENGE" -> L.t("wynnlabs.shop.where_challenge", name);
            default -> plain(parts[0], name);
        };
    }

    /** A kind with no words of its own, for people: "TINKERING: Scavenger's Workshop" → "Scavenger's Workshop (tinkering)". */
    static String plain(String kind, String name) {
        String k = kind.toLowerCase(java.util.Locale.ROOT).replace('_', ' ').trim();
        return name.isBlank() ? k : name.trim() + " (" + k + ")";
    }

    /**
     * What kind of source this is, the same in every language, for code that decides by it
     * (instead of reading the words {@link #where} shows): "merchant" (town or dungeon merchant),
     * "event", "dungeon", "quest", "chest", "drop", "altar", or "other". Takes the raw source
     * ({@link #sourceOf}, "MERCHANT: Detlas" style); null gives "other".
     */
    public static String kind(String source) {
        if (source == null) {
            return "other";
        }
        String type = source.split(": ", 2)[0];
        return switch (type) {
            case "TRADE_MARKET" -> "market";
            case "MERCHANT", "DUNGEON_MERCHANT" -> "merchant";
            case "WORLD_EVENT" -> "event";
            case "DUNGEON" -> "dungeon";
            case "QUEST" -> "quest";
            case "CAVE_LOOT_CHEST", "CAVE_COMPLETION", "LOOT_CHEST" -> "chest";
            case "NORMAL_MOB_DROP", "MOB_REGION", "SPECIFIC_MOB_DROP", "MINIBOSS" -> "drop";
            case "BOSS_ALTAR" -> "altar";
            default -> "other";
        };
    }

    /** {@link #kind(String)} of where this gear comes from in the world ({@link #origin}, never the market). */
    public static String kind(ItemData info) {
        return kind(origin(info));
    }

    public static String sourceOf(ItemData info) {
        return source(info);
    }

    /**
     * Where to get it, "TRADE_MARKET" or "MERCHANT: Ragni" style: the Trade Market first for a
     * tradable piece listed there now that otherwise takes a quest, dungeon, raid or drop (Cloak
     * of Luminosity has listings, so a quest is not the way to it); a town merchant's fixed price
     * stays first. The other one is the second line ({@link #alsoFrom}).
     */
    static String source(ItemData info) {
        String o = origin(info);
        if (listed(info) && !o.startsWith("MERCHANT")) {
            return "TRADE_MARKET";
        }
        return o;
    }

    /** No database or player report says where this piece comes from. */
    public static boolean unknownSource(ItemData info) {
        return origin(info).startsWith("UNKNOWN");
    }

    /** A tradable piece the Trade Market has a price for, listed now or seen before. */
    static boolean priced(ItemData info) {
        Prices.Price p = Shopping.tradable(info) ? GameState.current().price(info.name()) : null;
        return p != null && p.buyPrice() != null;
    }

    /** A tradable piece with listings on the Trade Market now (its price is not only history). */
    static boolean listed(ItemData info) {
        if (!Shopping.tradable(info)) {
            return false;
        }
        Prices.Price p = GameState.current().price(info.name());
        if (p == null) {
            return false;
        }
        Prices.Confidence c = p.confidence();
        return c != Prices.Confidence.NONE && c != Prices.Confidence.HISTORY;
    }

    /**
     * The second way to a piece listed on the Trade Market now, in words: the Trade Market under
     * a town merchant, the world source under the Trade Market ("Also: Lights Out quest"); else null.
     */
    public static String alsoFrom(ItemData info) {
        if (!listed(info)) {
            return null;
        }
        String o = origin(info);
        if (o.startsWith("MERCHANT")) {
            return where("TRADE_MARKET");
        }
        return o.startsWith("UNKNOWN") ? null : where(o);
    }

    /**
     * Whether rules.shopping.excludeSources keeps this piece off the list: its world source starts
     * with an entry. "EVENT" is the seasonal events only; world events (WORLD_EVENT) can be done
     * any time and are never hidden by it. A piece with no known source at all is left out too,
     * unless the Trade Market has a price for it (then it can be bought); callers keep what you have.
     */
    public static boolean excluded(ItemData info, List<String> bad) {
        String o = origin(info);
        if (o.startsWith("UNKNOWN") && !priced(info)) {
            return true;
        }
        for (String b : bad) {
            if (o.startsWith(b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether getting this untradable piece takes a run of its own: a dungeon, its merchant
     * (Admiral wants Redbeard's Hair from the dungeon), a raid or a quest. The road map asks such
     * a piece to beat a market one by rules.shopping.untradableEffortPercent more.
     */
    public static boolean effort(ItemData info) {
        if (Shopping.tradable(info)) {
            return false;
        }
        String o = origin(info);
        return o != null && (o.startsWith("DUNGEON") || o.startsWith("RAID") || o.startsWith("QUEST"));
    }

    /**
     * The quest this piece asks for, when the quest book lists it as not done yet; null when it
     * asks for none, it is done, or the book does not know it (an empty book gates nothing).
     */
    public static QuestBook.Entry questPending(GameState state, ItemData info) {
        var need = info.requirements().quest();
        if (need.isEmpty()) {
            return null;
        }
        String want = questKey(need.get());
        for (QuestBook.Entry q : state.quests()) {
            if (questKey(q.name()).equals(want)) {
                return "COMPLETED".equals(q.status()) ? null : q;
            }
        }
        return null;
    }

    /** A quest name to compare by: case, curly apostrophes and edge spaces aside. */
    private static String questKey(String name) {
        return name.replace('’', '\'').trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Where it comes from in the world, "MERCHANT: Ragni" style; "UNKNOWN" when nothing says. */
    public static String origin(ItemData info) {
        List<Obtain> obtain = GameState.current().obtain(info);
        if (obtain == null || obtain.isEmpty()) {
            return "UNKNOWN";
        }
        // prefer a source you can walk to over random loot
        Obtain best = obtain.get(0);
        for (Obtain o : obtain) {
            String n = o.type();
            if (n.equals("MERCHANT") || n.equals("QUEST") || n.equals("DUNGEON_MERCHANT")) {
                best = o;
                break;
            }
        }
        return best.type() + best.name().map(n -> ": " + n).orElse("");
    }
}
