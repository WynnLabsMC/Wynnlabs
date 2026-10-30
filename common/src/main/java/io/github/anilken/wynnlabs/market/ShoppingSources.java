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
        int cut = source.indexOf(COST);
        if (cut >= 0) {
            return costText(where(source.substring(0, cut)), source.substring(cut + COST.length()));
        }
        String[] parts = source.split(": ", 2);
        String name = parts.length > 1 ? parts[1] : "";
        return switch (parts[0]) {
            case "TRADE_MARKET" -> L.t("wynnlabs.shop.where_market");
            case "MERCHANT" -> L.t("wynnlabs.shop.where_merchant", name);
            case "DUNGEON_MERCHANT" -> L.t("wynnlabs.shop.where_dungeon_merchant", name);
            case "QUEST" -> L.t("wynnlabs.shop.where_quest", name);
            case "QUEST_TOKEN" -> {
                Token tk = tokenOfQuest(name);
                yield tk == null ? L.t("wynnlabs.shop.where_quest", name)
                        : L.t("wynnlabs.shop.where_quest_token", tk.merchant(), tk.token(), tk.quest());
            }
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
            case "QUEST", "QUEST_TOKEN" -> "quest";
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
        return withCost(info, source(info));
    }

    /** Between a source and what the piece costs to earn ({@link #withCost}). */
    private static final String COST = " #";

    /**
     * {@code src} (a raw source of this piece) with what it costs to earn when its seller takes
     * no emeralds ({@link EarnCost}), for {@link #where} to show: "MERCHANT: Tempo Town #7|Time
     * Fragment||Tempo Town Trouble" reads "Tempo Town merchant · 7× Time Fragment (after Tempo Town
     * Trouble)". The Trade Market and an unknown source stay as they are.
     */
    public static String withCost(ItemData info, String src) {
        if (src == null || src.startsWith("TRADE_MARKET") || src.startsWith("UNKNOWN") || src.contains(COST)) {
            return src;
        }
        EarnCost.Cost c = EarnCost.of(info, src);
        if (c == null) {
            return src;
        }
        long n = Math.round(c.amount());
        return src + COST + n + "|" + c.currency() + "|" + (c.needs() == null ? "" : c.needs()) + "|"
                + (c.quest() == null ? "" : c.quest());
    }

    /** {@code place} with the cost {@link #withCost} wrote: the amount and currency, the piece it also takes, the quest still to do. */
    private static String costText(String place, String cost) {
        String[] f = cost.split("\\|", -1);
        if (f.length < 4) {
            return place;
        }
        String paid = f[2].isEmpty() ? L.t("wynnlabs.shop.earn_cost", f[0], f[1])
                : L.t("wynnlabs.shop.earn_cost_with", f[0], f[1], f[2]);
        String out = L.t("wynnlabs.shop.earn_where", place, paid);
        return f[3].isEmpty() || questDone(GameState.current(), f[3]) ? out : L.t("wynnlabs.shop.earn_after", out, f[3]);
    }

    /** Whether the quest book lists {@code quest} as done; an empty book or an unknown quest counts as done (gates nothing). */
    private static boolean questDone(GameState state, String quest) {
        String want = questKey(quest);
        for (QuestBook.Entry q : state.quests()) {
            if (questKey(q.name()).equals(want)) {
                return "COMPLETED".equals(q.status());
            }
        }
        return true;
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
     * Whether getting this untradable piece takes runs of its own ({@link EarnCost#percent}): a
     * dungeon or its merchant (Admiral wants four Redbeard's Hair), a raid, a quest, a challenge,
     * an altar or a seller paid in fragments, tokens or materials (Tempo Town's Time Fragments).
     */
    public static boolean effort(ItemData info) {
        return EarnCost.percent(info) > 0;
    }

    /**
     * How much more this piece must gain to be worth earning, in % ({@link EarnCost#percent}):
     * the time its currency takes, 0 for a tradable piece or one you just pick up.
     */
    public static double effortPercent(ItemData info) {
        return EarnCost.percent(info);
    }

    /**
     * The quest this piece asks for, when the quest book lists it as not done yet; null when it
     * asks for none, it is done, or the book does not know it (an empty book gates nothing).
     */
    public static QuestBook.Entry questPending(GameState state, ItemData info) {
        Token tk = token(info);
        var need = tk != null ? java.util.Optional.of(tk.quest()) : info.requirements().quest();
        if (need.isEmpty()) {
            // a seller a quest opens (Tempo Town's merchant after Tempo Town Trouble, the Hive
            // shop after The Qira Hive): the earn cost table says which
            EarnCost.Cost c = EarnCost.of(info);
            need = java.util.Optional.ofNullable(c == null ? null : c.quest());
        }
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

    /**
     * A currency only a quest gives, the merchant that takes it and the quest. The Ornate Shadow
     * pieces are sold by the Mysterious Merchant in Lutho for one Corrupted Soul Token each, and
     * the token's one source is A Hunter's Calling (one token): a character gets one piece, and it
     * is untradable (the wiki's Corrupted Soul Token and Ornate Shadow pages).
     */
    record Token(String quest, String token, String merchant) {}

    /** The quest-only currency this piece is bought with, or null. */
    static Token token(ItemData info) {
        return info == null ? null : tokenOfGroup(io.github.anilken.wynnlabs.build.Exclusive.group(info.name()));
    }

    private static Token tokenOfGroup(String group) {
        return "Ornate Shadow".equals(group) ? new Token("A Hunter's Calling", "Corrupted Soul Token", "Lutho") : null;
    }

    private static Token tokenOfQuest(String quest) {
        Token tk = tokenOfGroup("Ornate Shadow");
        return questKey(tk.quest()).equals(questKey(quest)) ? tk : null;
    }

    /**
     * Whether this piece is bought with a quest's one-off currency (the Ornate Shadow pieces):
     * until the quest is done it is an alternative for after it, never a pick, whatever the level.
     */
    public static boolean questToken(ItemData info) {
        return token(info) != null;
    }

    /** A quest name to compare by: case, curly apostrophes and edge spaces aside. */
    private static String questKey(String name) {
        return name.replace('’', '\'').trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Where it comes from in the world, "MERCHANT: Ragni" style; "UNKNOWN" when nothing says. A
     * seasonal event's piece reads EVENT however else it is sold (Prologue is also at the Isles of
     * Fiction merchant, which only opens for the events), and so does a piece sold only by such an
     * event merchant (the Ornaments); a merchant named after a world event is that world event
     * (Golem's Arm).
     */
    public static String origin(ItemData info) {
        if (token(info) != null) {
            return "QUEST_TOKEN: " + token(info).quest();
        }
        List<Obtain> obtain = GameState.current().obtain(info);
        if (obtain == null || obtain.isEmpty()) {
            return "UNKNOWN";
        }
        Places places = places();
        boolean onlyEventShops = true;
        for (Obtain o : obtain) {
            if (o.type().equals("EVENT")) {
                return label(o);
            }
            onlyEventShops &= merchant(o.type()) && places.eventShops.contains(o.name().orElse(""));
        }
        if (onlyEventShops) {
            return "EVENT" + obtain.get(0).name().map(n -> ": " + n).orElse("");
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
        if (merchant(best.type()) && places.worldEvents.contains(best.name().orElse(""))) {
            return "WORLD_EVENT" + best.name().map(n -> ": " + n).orElse("");
        }
        return label(best);
    }

    private static String label(Obtain o) {
        return o.type() + o.name().map(n -> ": " + n).orElse("");
    }

    private static boolean merchant(String type) {
        return type.equals("MERCHANT") || type.equals("DUNGEON_MERCHANT");
    }

    /**
     * The item data's event merchants (a merchant that also sells a seasonal event's pieces) and
     * world event names, worked out once per game state.
     */
    private record Places(GameState state, java.util.Set<String> eventShops, java.util.Set<String> worldEvents) {}

    private static volatile Places places;

    private static Places places() {
        GameState state = GameState.current();
        Places p = places;
        if (p != null && p.state == state) {
            return p;
        }
        java.util.Set<String> shops = new java.util.HashSet<>();
        java.util.Set<String> events = new java.util.HashSet<>();
        state.allGear().forEach(info -> {
            List<Obtain> obtain = state.obtain(info);
            if (obtain == null) {
                return;
            }
            boolean seasonal = obtain.stream().anyMatch(o -> o.type().equals("EVENT"));
            for (Obtain o : obtain) {
                if (seasonal && merchant(o.type())) {
                    o.name().ifPresent(shops::add);
                } else if (o.type().equals("WORLD_EVENT")) {
                    o.name().ifPresent(events::add);
                }
            }
        });
        p = new Places(state, java.util.Set.copyOf(shops), java.util.Set.copyOf(events));
        if (!shops.isEmpty() || !events.isEmpty()) {
            places = p;               // the game's item data may not be loaded yet: ask again then
        }
        return p;
    }
}
