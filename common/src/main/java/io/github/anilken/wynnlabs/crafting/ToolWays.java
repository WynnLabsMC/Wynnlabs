package io.github.anilken.wynnlabs.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ways to get a gathering tool besides crafting (tools are not crafted): the Trade Market, the
 * Tool Merchant (tiers 1 and 2), upgrading the tier below at an Item Upgrader with dungeon
 * fragments, and a Dungeon Merchant (fragments and emeralds). Fragments drop from finishing a
 * dungeon and cannot be traded, so they are counted, never priced; the rest of a way's cost comes
 * from the prices handed in, and a tool of the tier below that the player has costs nothing. The
 * tiers are shipped in {@code assets/wynnlabs/crafting/tool-ways.json}. Plain data in, plain data out.
 */
public final class ToolWays {

    public enum Kind { MARKET, MERCHANT, UPGRADE, DUNGEON }

    /** One thing a way takes: {@code count} of {@code name}, each {@code each} emeralds (null: no price or a fragment; 0: yours). */
    public record Part(String name, int count, Double each, boolean owned, boolean fragment) {}

    /**
     * A way: its kind, where (a dungeon, null otherwise), what it takes, plain emeralds on top, the
     * emeralds it costs besides its fragments (null when a part has no price) and how many
     * fragments it takes.
     */
    public record Way(Kind kind, String at, List<Part> parts, long emeralds, Double total, int fragments) {}

    record Upgrade(String fragment, int count) {}

    record Dungeon(String at, String fragment, int count, long emeralds) {}

    record Tier(int tier, long merchant, Upgrade upgrade, List<Dungeon> dungeons) {}

    private static final Pattern TOOL = Pattern.compile("^(.+) (Pickaxe|Axe|Scythe|Rod) T([0-9]{1,2})$");
    private static volatile Map<Integer, Tier> tiers;

    private ToolWays() {}

    /** A tool's tier from its name ("Cobalt Pickaxe T8" → 8), or 0 when the name is no tool's. */
    public static int tier(String name) {
        Matcher m = name == null ? null : TOOL.matcher(name);
        return m != null && m.matches() ? Integer.parseInt(m.group(3)) : 0;
    }

    /**
     * The ways to get a tool of {@code tier}, cheapest first (those without a price last).
     * {@code lower} is the name of the same profession's tool one tier below (null when unknown);
     * {@code ownLower}: the player has it, so upgrading takes only the fragments.
     */
    public static List<Way> ways(String name, int tier, String lower, boolean ownLower, Function<String, Double> price) {
        List<Way> out = new ArrayList<>();
        Double market = price.apply(name);
        out.add(new Way(Kind.MARKET, null, List.of(new Part(name, 1, market, false, false)), 0, market, 0));
        Tier t = tiers().get(tier);
        if (t != null) {
            if (t.merchant() > 0) {
                out.add(new Way(Kind.MERCHANT, null, List.of(), t.merchant(), (double) t.merchant(), 0));
            }
            if (t.upgrade() != null && lower != null) {
                List<Part> parts = List.of(new Part(lower, 1, ownLower ? Double.valueOf(0) : price.apply(lower), ownLower, false),
                        new Part(t.upgrade().fragment(), t.upgrade().count(), null, false, true));
                out.add(new Way(Kind.UPGRADE, null, parts, 0, total(parts, 0), t.upgrade().count()));
            }
            for (Dungeon d : t.dungeons()) {
                List<Part> parts = List.of(new Part(d.fragment(), d.count(), null, false, true));
                out.add(new Way(Kind.DUNGEON, d.at(), parts, d.emeralds(), total(parts, d.emeralds()), d.count()));
            }
        }
        // the ways bought with emeralds alone first (cheapest first), then those that need dungeon runs
        out.sort(Comparator.comparing((Way w) -> w.fragments() > 0)
                .thenComparing(w -> w.total() == null ? Double.MAX_VALUE : w.total()));
        return out;
    }

    private static Double total(List<Part> parts, long emeralds) {
        double sum = emeralds;
        for (Part p : parts) {
            if (p.fragment()) {
                continue;
            }
            if (p.each() == null) {
                return null;
            }
            sum += p.each() * p.count();
        }
        return sum;
    }

    static Map<Integer, Tier> tiers() {
        Map<Integer, Tier> t = tiers;
        if (t == null) {
            t = load();
            tiers = t;
        }
        return t;
    }

    private static Map<Integer, Tier> load() {
        Map<Integer, Tier> out = new HashMap<>();
        try (InputStream in = ToolWays.class.getResourceAsStream("/assets/wynnlabs/crafting/tool-ways.json")) {
            if (in == null) {
                return out;
            }
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : doc.getAsJsonArray("tiers")) {
                JsonObject o = e.getAsJsonObject();
                Upgrade up = null;
                if (o.has("upgrade")) {
                    JsonObject u = o.getAsJsonObject("upgrade");
                    up = new Upgrade(u.get("fragment").getAsString(), u.get("count").getAsInt());
                }
                List<Dungeon> ds = new ArrayList<>();
                if (o.has("dungeon")) {
                    for (JsonElement de : o.getAsJsonArray("dungeon")) {
                        JsonObject d = de.getAsJsonObject();
                        ds.add(new Dungeon(d.get("at").getAsString(), d.get("fragment").getAsString(), d.get("count").getAsInt(),
                                d.get("emeralds").getAsLong()));
                    }
                }
                int tier = o.get("tier").getAsInt();
                out.put(tier, new Tier(tier, o.has("merchant") ? o.get("merchant").getAsLong() : 0, up, List.copyOf(ds)));
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the shipped tool ways: " + e);
        }
        return out;
    }
}
