package io.github.anilken.wynnlabs.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The raid runes (Az, Nii, Uth, Tol, Ek), shipped in {@code assets/wynnlabs/crafting/runes.json}:
 * the raids each opens, the corrupted keys each makes at the Key Forge Merchant, the world event
 * levels that give it, and the powder tier it turns into a Blank Powder. Plain data in, plain data out.
 */
public final class Runes {

    public record Raid(String raid, int level) {}

    /** A Key Forge trade: a broken key and {@code runes} of this rune make a corrupted key. */
    public record KeyTrade(String broken, int runes, String key) {}

    public record Rune(String name, String element, int eventLow, int eventHigh, boolean lootrun, List<Raid> raids,
                       List<KeyTrade> keys, int powderTier) {}

    /** What a key trade costs and what the key sells for; nulls when a price is unknown. */
    public record Margin(Double cost, Double sells) {
        public Double profit() {
            return cost == null || sells == null ? null : sells - cost;
        }
    }

    private static volatile Map<String, Rune> all;

    private Runes() {}

    /** A rune by its name ("Tol Rune"), or null when the name is no raid rune's. */
    public static Rune of(String name) {
        return name == null ? null : runes().get(name);
    }

    /** A key trade's cost (the broken key and the runes) and the key's price, by {@code price} (null: unknown). */
    public static Margin margin(Rune rune, KeyTrade t, Function<String, Double> price) {
        Double broken = price.apply(t.broken());
        Double each = price.apply(rune.name());
        Double cost = broken == null || each == null ? null : broken + each * t.runes();
        return new Margin(cost, price.apply(t.key()));
    }

    static Map<String, Rune> runes() {
        Map<String, Rune> r = all;
        if (r == null) {
            r = load();
            all = r;
        }
        return r;
    }

    private static Map<String, Rune> load() {
        Map<String, Rune> out = new LinkedHashMap<>();
        try (InputStream in = Runes.class.getResourceAsStream("/assets/wynnlabs/crafting/runes.json")) {
            if (in == null) {
                return out;
            }
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : doc.getAsJsonArray("runes")) {
                JsonObject o = e.getAsJsonObject();
                List<Raid> raids = new ArrayList<>();
                for (JsonElement re : o.getAsJsonArray("raids")) {
                    JsonObject ro = re.getAsJsonObject();
                    raids.add(new Raid(ro.get("raid").getAsString(), ro.get("level").getAsInt()));
                }
                List<KeyTrade> keys = new ArrayList<>();
                for (JsonElement ke : o.getAsJsonArray("keys")) {
                    JsonObject ko = ke.getAsJsonObject();
                    keys.add(new KeyTrade(ko.get("broken").getAsString(), ko.get("runes").getAsInt(), ko.get("key").getAsString()));
                }
                var ev = o.getAsJsonArray("events");
                String name = o.get("name").getAsString();
                out.put(name, new Rune(name, o.get("element").getAsString(), ev.get(0).getAsInt(), ev.get(1).getAsInt(),
                        o.get("lootrun").getAsBoolean(), List.copyOf(raids), List.copyOf(keys), o.get("powderTier").getAsInt()));
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the shipped runes: " + e);
        }
        return out;
    }
}
