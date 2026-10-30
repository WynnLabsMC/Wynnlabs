package io.github.anilken.wynnlabs.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What an untradable piece costs when its seller takes no emeralds: Time Fragments in Tempo Town,
 * a Hive voucher, Legendary Island tokens, a dungeon boss's tokens, altar tributes, materials.
 * The shipped table {@code assets/wynnlabs/build/earn-costs.json} (its {@code _doc} lists the
 * fields, its {@code sources} the wiki pages) says what each takes and how long earning it is;
 * the road map charges that time as {@link #percent} so such a piece only wins when it is worth
 * the runs. Before it, any "merchant" read as an emerald shop (Seal Breaker at Lv 34 took seven
 * Time Fragments from 8-hourly bosses) and only dungeon, raid and quest pieces paid a flat 5 %.
 */
public final class EarnCost {

    private EarnCost() {}

    /** One way to earn a currency, as the table gives it. */
    record Family(String id, String type, String name, String prefix, String currency, String how, double perRun,
            double minutesPerRun, String quest, double fallback, Map<String, Double> items, Map<String, Tier> tiers) {}

    /** A Legendary Island tier: its token, how many, how many clears the chain takes, the tier it consumes. */
    record Tier(String currency, double amount, double runs, String needs) {}

    /**
     * One piece's cost: the family, what it is paid in and how many, the piece it also takes
     * (a Legendary Island tier the one below, or null), the quest that opens the seller (or null)
     * and the minutes it takes to earn.
     */
    public record Cost(String family, String currency, double amount, String needs, String quest, double minutes) {}

    private static volatile List<Family> table;

    /** The per-piece costs worked out once per game state (the item data may load later: asked again then). */
    private record ByName(GameState state, Map<String, Double> minutes, Map<String, String> once) {}

    private static volatile ByName byName;

    /** The table, read once. */
    static List<Family> table() {
        List<Family> t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return t;
    }

    /**
     * What this piece costs to earn, or null: a tradable piece (the Trade Market sells it), or one
     * the table does not cover. {@code origin}: its world source ({@link ShoppingSources#origin}).
     */
    public static Cost of(ItemData info, String origin) {
        if (info == null || Shopping.tradable(info) || origin == null) {
            return null;
        }
        String type = origin.split(": ", 2)[0];
        String place = origin.contains(": ") ? origin.split(": ", 2)[1] : "";
        for (Family f : table()) {
            boolean byItem = f.items().containsKey(info.name());
            boolean bySource = f.type() != null && f.type().equals(type)
                    && (f.name() == null || f.name().equals(place))
                    && (f.prefix() == null || place.startsWith(f.prefix()));
            if (!byItem && !bySource) {
                continue;
            }
            Tier tier = tierOf(f, info.name());
            if (tier != null) {
                // the rings cost half (the wiki's Legendary Island page): a Diamond ring is one clear
                boolean ring = info.name().endsWith(" Ring");
                double amount = ring ? Math.ceil(tier.amount() / 2) : tier.amount();
                double runs = ring ? Math.max(1, Math.ceil(tier.runs() / 2)) : tier.runs();
                String needs = tier.needs() == null ? null : below(info.name(), tier.needs());
                return new Cost(f.id(), tier.currency(), amount, needs, f.quest(), runs * f.minutesPerRun());
            }
            double amount = f.items().getOrDefault(info.name(), f.fallback());
            double runs = Math.ceil(amount / Math.max(1e-9, f.perRun()));
            return new Cost(f.id(), f.currency(), amount, null, f.quest(), runs * f.minutesPerRun());
        }
        return null;
    }

    /** {@link #of(ItemData, String)} with the piece's own world source. */
    public static Cost of(ItemData info) {
        return info == null || Shopping.tradable(info) ? null : of(info, ShoppingSources.origin(info));
    }

    /** The tier a Legendary Island piece's name starts with ("Diamond Static Bracelet": Diamond), or null. */
    private static Tier tierOf(Family f, String name) {
        if (f.tiers().isEmpty()) {
            return null;
        }
        int sp = name.indexOf(' ');
        return sp < 0 ? null : f.tiers().get(name.substring(0, sp));
    }

    /**
     * The piece a tier consumes: the same element and kind one tier down ("Diamond Static
     * Bracelet" takes the "Gold Static Bracelet"); Bronze has only "Basic" ones.
     */
    private static String below(String name, String tier) {
        String rest = name.substring(name.indexOf(' ') + 1);
        if (tier.equals("Bronze")) {
            rest = "Basic" + rest.substring(rest.indexOf(' '));
        }
        return tier + " " + rest;
    }

    /**
     * The share of a piece's gain the road map takes off for earning it, in %: the table's
     * minutes at rules.shopping.effortPercentPerHour (3 % an hour), at most
     * rules.shopping.effortCapPercent (8 %); an untradable dungeon, raid or quest piece the
     * table does not know pays rules.shopping.untradableEffortPercent (5 %) as before, once per
     * source in a set ({@link #once}); 0 for anything else (tradable, a drop, a chest). Seal
     * Breaker (7 Time Fragments, ~105 min) is 5.3 %, a Timelocked ring (one fragment) 0.8 %, a
     * Diamond Legendary Island bracelet (two clears) 4 %, a dungeon merchant piece (four ~10 min
     * runs) 2 %. Modest on purpose: the leveling meta's dungeon picks (Witherhead's Talisman,
     * Criistal, Vanguard) still win where they are clearly better.
     */
    public static double percent(ItemData info) {
        return info == null || Shopping.tradable(info) ? 0 : percentFor(minutes(info));
    }

    /** The table's minutes for an untradable piece, {@link #FLAT} for the flat charge, 0 for none. */
    private static double minutes(ItemData info) {
        String origin = ShoppingSources.origin(info);
        Cost c = of(info, origin);
        if (c != null) {
            return c.minutes();
        }
        return origin.startsWith("DUNGEON") || origin.startsWith("RAID") || origin.startsWith("QUEST") ? FLAT : 0;
    }

    /** Marks a piece that pays rules.shopping.untradableEffortPercent rather than the table's time. */
    private static final double FLAT = -1;

    private static double percentFor(double minutes) {
        return minutes == FLAT ? Rules.sub("shopping", "untradableEffortPercent", 5) : minutes <= 0 ? 0 : percentOf(minutes);
    }

    /** {@code minutes} of earning as the % the road map takes off. */
    static double percentOf(double minutes) {
        return Math.min(Rules.sub("shopping", "effortCapPercent", 8),
                Rules.sub("shopping", "effortPercentPerHour", 3) * minutes / 60.0);
    }

    /**
     * {@link #percent} by the piece's name, for the set judging that only has the pieces' names:
     * worked out once per game state for every untradable piece; 0 for a name it does not know.
     */
    public static double percent(String name) {
        Double t = byName().minutes().get(name);
        return t == null ? 0 : percentFor(t);
    }

    /**
     * For a piece that pays the flat charge (a quest, dungeon or raid reward the table does not
     * know), its world source: a set pays it once per source, as one quest gives every piece of
     * its set (the Cosmic set from one quest is one quest's work, not four). Null for the rest.
     */
    public static String once(String name) {
        return byName().once().get(name);
    }

    private static ByName byName() {
        GameState state = GameState.current();
        ByName b = byName;
        if (b != null && b.state() == state) {
            return b;
        }
        Map<String, Double> m = new HashMap<>();
        Map<String, String> once = new HashMap<>();
        try {
            state.allGear().forEach(info -> {
                double t = Shopping.tradable(info) ? 0 : minutes(info);
                if (t != 0) {
                    m.put(info.name(), t);
                }
                if (t == FLAT) {
                    once.put(info.name(), ShoppingSources.origin(info));
                }
            });
        } catch (RuntimeException | LinkageError e) {
            // no item data (Wynntils missing or not loaded): nothing is charged, and it is asked again
            return new ByName(state, Map.of(), Map.of());
        }
        b = new ByName(state, Map.copyOf(m), Map.copyOf(once));
        if (!m.isEmpty()) {
            byName = b;                // the item data may not be loaded yet: ask again then
        }
        return b;
    }

    // ---- the table ----------------------------------------------------------------------------

    private static List<Family> load() {
        List<Family> out = new ArrayList<>();
        try (InputStream in = EarnCost.class.getResourceAsStream("/assets/wynnlabs/build/earn-costs.json")) {
            if (in != null) {
                JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : doc.getAsJsonArray("families")) {
                    out.add(family(e.getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the earn cost table: " + e);
        }
        return List.copyOf(out);
    }

    static Family family(JsonObject o) {
        JsonObject match = o.has("match") ? o.getAsJsonObject("match") : new JsonObject();
        Map<String, Double> items = new HashMap<>();
        if (o.has("items")) {
            o.getAsJsonObject("items").entrySet().forEach(e -> items.put(e.getKey(), e.getValue().getAsDouble()));
        }
        Map<String, Tier> tiers = new HashMap<>();
        if (o.has("tiers")) {
            for (var e : o.getAsJsonObject("tiers").entrySet()) {
                JsonObject t = e.getValue().getAsJsonObject();
                tiers.put(e.getKey(), new Tier(text(t, "currency"), num(t, "amount", 1), num(t, "runs", 1), text(t, "needs")));
            }
        }
        return new Family(text(o, "id"), text(match, "type"), text(match, "name"), text(match, "prefix"),
                text(o, "currency"), text(o, "how"), num(o, "perRun", 1), num(o, "minutesPerRun", 0), text(o, "quest"),
                num(o, "default", 1), Map.copyOf(items), Map.copyOf(tiers));
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : fallback;
    }
}
