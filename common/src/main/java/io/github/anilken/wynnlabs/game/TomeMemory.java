package io.github.anilken.wynnlabs.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.model.SkillKind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the tomes the Mastery Tomes menu showed last time the player opened it.
 *
 * <p>Wynntils fills its tome set and tome skill points only through its own menu query; a menu
 * the player opens by hand is not read into them. While that menu is open this reads the tomes
 * from its slots and keeps them per character (tomes.json), so they survive a restart. Only
 * looks at the open menu; never clicks.
 */
public final class TomeMemory {

    /** One tome in the menu: its name and the skill points it gives. */
    public record Seen(String name, Map<SkillKind, Integer> skills) {}

    /** What was remembered: tome names and their skill points summed per skill. */
    record Known(List<String> names, Map<SkillKind, Integer> points) {}

    // no initializers: hot swap safe (null = not loaded yet)
    private static volatile Known known;
    private static volatile boolean loaded;

    private TomeMemory() {}

    /** Reads the Mastery Tomes menu when it is open and remembers its tomes. */
    static void capture() {
        List<Seen> seen = Wyn.masteryTomes();
        Known k = seen == null ? null : of(seen);
        if (k == null) {
            return;                             // not that menu, or its items have not loaded yet
        }
        loaded = true;
        known = k;
        String text = json(k);
        Saver.mark(Profile.dir().resolve("tomes.json"), () -> text);
    }

    /** The tomes and summed points of a menu's tomes; null when it shows none. */
    static Known of(List<Seen> seen) {
        List<String> names = new ArrayList<>();
        Map<SkillKind, Integer> points = new EnumMap<>(SkillKind.class);
        for (Seen t : seen) {
            if (t == null || t.name() == null || t.name().isBlank()) {
                continue;
            }
            names.add(t.name());
            t.skills().forEach((s, v) -> points.merge(s, v, Integer::sum));
        }
        return names.isEmpty() ? null : new Known(List.copyOf(names), Map.copyOf(points));
    }

    static String json(Known k) {
        JsonObject o = new JsonObject();
        JsonArray names = new JsonArray();
        k.names().forEach(names::add);
        o.add("names", names);
        JsonObject points = new JsonObject();
        new java.util.TreeMap<>(k.points()).forEach((s, v) -> points.addProperty(s.name(), v));
        o.add("points", points);
        return o.toString();
    }

    static Known parse(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        List<String> names = new ArrayList<>();
        o.getAsJsonArray("names").forEach(e -> names.add(e.getAsString()));
        Map<SkillKind, Integer> points = new EnumMap<>(SkillKind.class);
        JsonObject p = o.getAsJsonObject("points");
        for (SkillKind s : SkillKind.values()) {
            if (p != null && p.has(s.name())) {
                points.put(s, p.get(s.name()).getAsInt());
            }
        }
        return names.isEmpty() ? null : new Known(List.copyOf(names), Map.copyOf(points));
    }

    /** The remembered tomes of this character (now or a saved read), or null. */
    private static Known known() {
        if (!loaded) {
            loaded = true;
            try {
                Path f = Profile.dir().resolve("tomes.json");
                Saver.settle(f);
                if (Files.exists(f)) {
                    String text = Files.readString(f);
                    Saver.known(f, text);       // the same tomes are not written back
                    known = parse(text);
                }
            } catch (Exception ignored) {
            }
        }
        return known;
    }

    /** Wynntils' tome names when it has any, else the remembered ones. */
    static List<String> names(List<String> wynntils) {
        return pickNames(wynntils, known());
    }

    /** Wynntils' tome points when it has any, else the remembered tomes' points. */
    static int points(SkillKind s, int wynntils, boolean wynntilsAny) {
        return pickPoints(s, wynntils, wynntilsAny, known());
    }

    static List<String> pickNames(List<String> wynntils, Known k) {
        if (!wynntils.isEmpty() || k == null) {
            return wynntils;
        }
        return k.names();
    }

    /**
     * Wynntils' number when it read any tome points (it may rightly be 0 for one skill), else
     * the remembered tomes' points for that skill.
     */
    static int pickPoints(SkillKind s, int wynntils, boolean wynntilsAny, Known k) {
        if (wynntilsAny || k == null) {
            return wynntils;
        }
        return k.points().getOrDefault(s, 0);
    }

    /** Forget the tomes: another character is playing now. */
    public static void reset() {
        known = null;
        loaded = false;
    }
}
