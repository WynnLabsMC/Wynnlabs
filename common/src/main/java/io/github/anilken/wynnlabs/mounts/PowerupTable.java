package io.github.anilken.wynnlabs.mounts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The shipped powerup table ({@code assets/wynnlabs/mounts/powerups.json}): fingerprint → what the
 * powerup raises and its label colour. An unknown fingerprint is not a powerup we label.
 */
public final class PowerupTable {

    /**
     * One powerup: its name ("Speed", "Energy Boost"), the mount stat it raises (null for the two
     * boosts), its colour (RGB) and whether it was checked in game.
     */
    public record Kind(String name, MountStat stat, int colour, boolean verified) {

        public boolean energyBoost() {
            return name.equals("Energy Boost");
        }
    }

    private static Map<String, Kind> table;

    private PowerupTable() {}

    /** The powerup with this fingerprint, null when unknown. */
    public static Kind of(String print) {
        if (table == null) {
            table = load();
        }
        return print == null ? null : table.get(print.toLowerCase(Locale.ROOT));
    }

    /** Every known powerup, by fingerprint. */
    public static Map<String, Kind> all() {
        if (table == null) {
            table = load();
        }
        return table;
    }

    private static Map<String, Kind> load() {
        try (InputStream in = PowerupTable.class.getResourceAsStream("/assets/wynnlabs/mounts/powerups.json")) {
            if (in == null) {
                return Map.of();
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Map.of();
        }
    }

    static Map<String, Kind> parse(Reader json) {
        JsonObject doc = JsonParser.parseReader(json).getAsJsonObject();
        Map<String, Kind> out = new HashMap<>();
        for (JsonElement el : doc.getAsJsonArray("powerups")) {
            JsonObject o = el.getAsJsonObject();
            String name = o.get("stat").getAsString();
            int colour = Integer.parseInt(o.get("colour").getAsString().replace("#", ""), 16);
            boolean verified = o.has("verified") && o.get("verified").getAsBoolean();
            out.put(o.get("print").getAsString().toLowerCase(Locale.ROOT),
                    new Kind(name, MountStat.parse(name), colour, verified));
        }
        return Map.copyOf(out);
    }
}
