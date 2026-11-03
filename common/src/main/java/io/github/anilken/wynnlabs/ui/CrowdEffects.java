package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The shipped table of spell effects ({@code assets/wynnlabs/crowd/effects.json}): the fingerprint
 * of the texture behind an effect's model ({@link io.github.anilken.wynnlabs.mounts.PowerupPrint})
 * → what it is. Crowd mode hides a display whose model carries one of these textures whatever
 * stack it stands in, so a totem's rings, leaves and sparkles go even where the stack's shape
 * says nothing. Model numbers change with every pack; textures do not. Pure.
 */
public final class CrowdEffects {

    private static volatile Map<String, String> table;

    private CrowdEffects() {}

    /** What the fingerprint is an effect of, or null when it is no known effect. */
    public static String effect(String print) {
        Map<String, String> t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return print == null ? null : t.get(print);
    }

    private static Map<String, String> load() {
        try (InputStream in = CrowdEffects.class.getResourceAsStream("/assets/wynnlabs/crowd/effects.json")) {
            if (in == null) {
                return Map.of();
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.out.println("[WynnLabs] crowd effects table not read: " + e);
            return Map.of();
        }
    }

    /** The table from its JSON: {@code {"effects": {"<sha256>": "what"}}}; odd entries are skipped. */
    public static Map<String, String> parse(Reader json) {
        Map<String, String> out = new HashMap<>();
        JsonObject doc = JsonParser.parseReader(json).getAsJsonObject();
        JsonObject effects = doc.has("effects") && doc.get("effects").isJsonObject() ? doc.getAsJsonObject("effects") : doc;
        for (Map.Entry<String, JsonElement> e : effects.entrySet()) {
            if (e.getKey().matches("[0-9a-f]{64}") && e.getValue().isJsonPrimitive()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return out;
    }

    /** Tests: the table read again from the resource next time. */
    static void reset() {
        table = null;
    }
}
