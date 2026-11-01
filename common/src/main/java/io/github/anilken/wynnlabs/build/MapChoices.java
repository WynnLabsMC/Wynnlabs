package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What you told the road map about its picks, saved per character in
 * {@code config/wynnlabs/characters/<id>/map-choices.json}: pieces it keeps from the level they
 * can be worn on, pieces it never picks, and the lowest item level it wears on the milestones at
 * or above that level. The items of "Choose items" ({@link Locks}) are another thing: they plan
 * one build for the level the highest of them needs. Names stay as the database writes them, in
 * English. Nothing here acts in the game.
 */
public final class MapChoices {

    private static final String FILE = "map-choices.json";

    /** One character's choices. */
    public record Choice(List<String> kept, List<String> banned, int minLevel) {

        public static final Choice NONE = new Choice(List.of(), List.of(), 0);

        /** Whether milestone {@code m} may not offer this piece; a kept one passes the level filter. */
        public boolean refuses(String name, int level, int m) {
            return banned.contains(name) || m >= minLevel && level < minLevel && !kept.contains(name);
        }

        /** How many choices are set, for the button's count. */
        public int count() {
            return kept.size() + banned.size() + (minLevel > 0 ? 1 : 0);
        }

        /** What the road map's keys take from the choices; empty with none, so older maps stay current. */
        public String key() {
            return count() == 0 ? "" : String.join("|", kept) + "/" + String.join("|", banned) + "/" + minLevel;
        }
    }

    private static volatile String loadedFor;
    private static volatile Choice choice = Choice.NONE;
    /** Tests and the preview: these choices instead of the character's file; null for the file. */
    private static volatile Choice override;

    private MapChoices() {}

    /** Tests and the preview: use {@code c} (null: the saved ones again); never written. */
    public static void use(Choice c) {
        override = c;
    }

    /** The choices of the character you play. */
    public static Choice current() {
        Choice o = override;
        if (o != null) {
            return o;
        }
        String id = characterId();
        if (!id.equals(loadedFor)) {
            synchronized (MapChoices.class) {
                if (!id.equals(loadedFor)) {
                    choice = read(id);
                    loadedFor = id;
                }
            }
        }
        return choice;
    }

    /** Keeps the piece, or stops keeping it; a kept piece is no longer banned. */
    public static void toggleKeep(String name) {
        Choice c = current();
        store(new Choice(toggled(c.kept(), name), without(c.banned(), name), c.minLevel()));
    }

    /** Bans the piece, or lifts the ban; a banned piece is no longer kept. */
    public static void toggleBan(String name) {
        Choice c = current();
        store(new Choice(without(c.kept(), name), toggled(c.banned(), name), c.minLevel()));
    }

    /** The lowest item level the map wears; 0 for any. */
    public static void setMinLevel(int level) {
        Choice c = current();
        store(new Choice(c.kept(), c.banned(), level));
    }

    private static List<String> toggled(List<String> list, String name) {
        List<String> out = new ArrayList<>(list);
        if (!out.remove(name)) {
            out.add(name);
        }
        return List.copyOf(out);
    }

    private static List<String> without(List<String> list, String name) {
        List<String> out = new ArrayList<>(list);
        out.remove(name);
        return List.copyOf(out);
    }

    private static void store(Choice c) {
        if (override != null) {
            override = c;
            return;
        }
        choice = c;
        String id = characterId();
        if (id.isEmpty()) {
            return;
        }
        JsonObject o = new JsonObject();
        o.add("kept", array(c.kept()));
        o.add("banned", array(c.banned()));
        o.addProperty("minLevel", c.minLevel());
        String text = o.toString();
        Saver.mark(file(id), () -> text);
    }

    private static JsonArray array(List<String> list) {
        JsonArray a = new JsonArray();
        list.forEach(a::add);
        return a;
    }

    private static Choice read(String id) {
        if (id.isEmpty()) {
            return Choice.NONE;
        }
        try {
            Path f = file(id);
            Saver.settle(f);
            if (!Files.exists(f)) {
                return Choice.NONE;
            }
            String text = Files.readString(f, StandardCharsets.UTF_8);
            Saver.known(f, text);
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            return new Choice(names(o, "kept"), names(o, "banned"), o.has("minLevel") ? o.get("minLevel").getAsInt() : 0);
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not read " + FILE + ": " + t);
            return Choice.NONE;
        }
    }

    private static List<String> names(JsonObject o, String field) {
        List<String> out = new ArrayList<>();
        if (o.has(field) && o.get(field).isJsonArray()) {
            o.getAsJsonArray(field).forEach(e -> out.add(e.getAsString()));
        }
        return List.copyOf(out);
    }

    private static String characterId() {
        try {
            return GameState.current().characterId();
        } catch (Throwable t) {
            return "";
        }
    }

    private static Path file(String id) {
        Path root = Locks.rootOverride != null ? Locks.rootOverride : Dirs.config().resolve("characters");
        return root.resolve(id).resolve(FILE);
    }

    /** Tests: the next read takes the file again. */
    public static void forget() {
        loadedFor = null;
        choice = Choice.NONE;
    }
}
