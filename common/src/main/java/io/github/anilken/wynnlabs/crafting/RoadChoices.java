package io.github.anilken.wynnlabs.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The level road's choices of one character ({@code craft-road.json} in its folder): the material
 * preference, the route picked per material, the steps ticked by hand, and per profession the target
 * level and item type. Read once per character, written (off the render thread) when one changes.
 */
public final class RoadChoices {

    public LevelRoad.Pref pref = LevelRoad.Pref.MIXED;
    public final Map<String, LevelRoad.Route> routes = new HashMap<>();
    /** Ticked step keys, per profession ("WEAPONSMITHING" → {"23:craft"}). */
    public final Map<String, Set<String>> ticked = new HashMap<>();
    public final Map<String, Integer> targets = new HashMap<>();
    public final Map<String, String> types = new HashMap<>();

    private static RoadChoices last;
    private static String lastFor;

    private RoadChoices() {}

    /** This character's choices (read from its file the first time). */
    public static RoadChoices get() {
        String id = Profile.id();
        if (!id.equals(lastFor)) {
            lastFor = id;
            last = new RoadChoices();
            try {
                Path f = file();
                if (Files.exists(f)) {
                    String text = Files.readString(f, StandardCharsets.UTF_8);
                    Saver.known(f, text);
                    last = parse(text);
                }
            } catch (Exception e) {
                last = new RoadChoices();           // a broken file: start again
            }
        }
        return last;
    }

    public Set<String> ticked(String profession) {
        return ticked.computeIfAbsent(profession, k -> new HashSet<>());
    }

    /** Something changed: written soon. */
    public void save() {
        String json = json();
        Saver.mark(file(), () -> json);
    }

    private static Path file() {
        return Profile.dir().resolve("craft-road.json");
    }

    static RoadChoices parse(String text) {
        RoadChoices c = new RoadChoices();
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        if (o.has("pref")) {
            try {
                c.pref = LevelRoad.Pref.valueOf(o.get("pref").getAsString());
            } catch (IllegalArgumentException ignored) {
                // a value of another version: the default
            }
        }
        if (o.has("routes")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("routes").entrySet()) {
                try {
                    c.routes.put(e.getKey(), LevelRoad.Route.valueOf(e.getValue().getAsString()));
                } catch (IllegalArgumentException ignored) {
                    // skipped
                }
            }
        }
        if (o.has("ticked")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("ticked").entrySet()) {
                Set<String> s = new HashSet<>();
                e.getValue().getAsJsonArray().forEach(v -> s.add(v.getAsString()));
                c.ticked.put(e.getKey(), s);
            }
        }
        if (o.has("targets")) {
            o.getAsJsonObject("targets").entrySet().forEach(e -> c.targets.put(e.getKey(), e.getValue().getAsInt()));
        }
        if (o.has("types")) {
            o.getAsJsonObject("types").entrySet().forEach(e -> c.types.put(e.getKey(), e.getValue().getAsString()));
        }
        return c;
    }

    String json() {
        JsonObject o = new JsonObject();
        o.addProperty("pref", pref.name());
        JsonObject r = new JsonObject();
        routes.forEach((k, v) -> r.addProperty(k, v.name()));
        o.add("routes", r);
        JsonObject t = new JsonObject();
        ticked.forEach((k, v) -> {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            v.forEach(a::add);
            t.add(k, a);
        });
        o.add("ticked", t);
        JsonObject tg = new JsonObject();
        targets.forEach(tg::addProperty);
        o.add("targets", tg);
        JsonObject ty = new JsonObject();
        types.forEach(ty::addProperty);
        o.add("types", ty);
        return o.toString();
    }
}
