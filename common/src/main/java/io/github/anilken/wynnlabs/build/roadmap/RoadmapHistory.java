package io.github.anilken.wynnlabs.build.roadmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.GameState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The road maps a new one replaced, the last {@link #KEEP} per character, so a map you liked is
 * one click away: each in {@code config/wynnlabs/characters/<id>/roadmap-history/<builtAt>.json}
 * as {@link RoadmapStore} writes it, listed in that folder's {@code index.json}, newest first.
 */
public final class RoadmapHistory {

    private static final int KEEP = 5;
    private static final String FOLDER = "roadmap-history";
    private static final String INDEX = "index.json";

    /** A replaced map in the list: when it was built, for which level, and the weapon it starts on. */
    public record Entry(long builtAt, int level, String weapon) {}

    private static String listedFor;
    private static List<Entry> listed = List.of();

    private RoadmapHistory() {}

    /** The replaced maps of the character you play, newest first. */
    public static synchronized List<Entry> entries() {
        return entries(GameState.current().characterId());
    }

    private static List<Entry> entries(String id) {
        if (id == null || id.isEmpty()) {
            return List.of();
        }
        if (!id.equals(listedFor)) {
            listed = read(id);
            listedFor = id;
        }
        return listed;
    }

    /** Keeps a map that is being replaced; the oldest one past {@link #KEEP} goes. */
    static synchronized void push(String id, List<Roadmap.Step> steps, String key, int level, long builtAt) {
        if (id == null || id.isEmpty() || steps.isEmpty()) {
            return;
        }
        try {
            List<Entry> out = new ArrayList<>(entries(id));
            out.removeIf(e -> e.builtAt() == builtAt);
            out.add(0, new Entry(builtAt, level, weapon(steps)));
            String text = RoadmapStore.text(steps, key, level, builtAt);
            Saver.mark(map(id, builtAt), () -> text);
            while (out.size() > KEEP) {
                delete(id, out.remove(out.size() - 1).builtAt());
            }
            store(id, out);
        } catch (Throwable t) {
            // no config folder (tests without the game): nothing to keep it in
        }
    }

    /** The map built at {@code builtAt}; null when it is gone or cannot be read. */
    static synchronized RoadmapStore.Loaded read(String id, long builtAt) {
        try {
            Path f = map(id, builtAt);
            Saver.settle(f);
            return Files.exists(f) ? RoadmapStore.parse(Files.readString(f, StandardCharsets.UTF_8)) : null;
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not read the saved map " + builtAt + ": " + t);
            return null;
        }
    }

    /** Takes a map off the list: it was brought back. */
    static synchronized void remove(String id, long builtAt) {
        List<Entry> out = new ArrayList<>(entries(id));
        out.removeIf(e -> e.builtAt() == builtAt);
        delete(id, builtAt);
        store(id, out);
    }

    /** Tests: the list is read from the file again. */
    static synchronized void forget() {
        listedFor = null;
        listed = List.of();
    }

    private static String weapon(List<Roadmap.Step> steps) {
        Damage.Piece w = steps.get(0).build().get("WEAPON");
        return w == null ? "" : w.name();
    }

    private static void delete(String id, long builtAt) {
        try {
            Path f = map(id, builtAt);
            Saver.settle(f);
            Files.deleteIfExists(f);
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not remove the saved map " + builtAt + ": " + t);
        }
    }

    private static void store(String id, List<Entry> list) {
        listed = List.copyOf(list);
        listedFor = id;
        JsonArray a = new JsonArray();
        for (Entry e : list) {
            JsonObject o = new JsonObject();
            o.addProperty("builtAt", e.builtAt());
            o.addProperty("level", e.level());
            o.addProperty("weapon", e.weapon());
            a.add(o);
        }
        String text = a.toString();
        Saver.mark(folder(id).resolve(INDEX), () -> text);
    }

    private static List<Entry> read(String id) {
        try {
            Path f = folder(id).resolve(INDEX);
            Saver.settle(f);
            if (!Files.exists(f)) {
                return List.of();
            }
            String text = Files.readString(f, StandardCharsets.UTF_8);
            Saver.known(f, text);
            List<Entry> out = new ArrayList<>();
            for (JsonElement e : JsonParser.parseString(text).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                out.add(new Entry(o.get("builtAt").getAsLong(), o.get("level").getAsInt(), o.get("weapon").getAsString()));
            }
            return List.copyOf(out);
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not read the map history: " + t);
            return List.of();
        }
    }

    private static Path map(String id, long builtAt) {
        return folder(id).resolve(builtAt + ".json");
    }

    private static Path folder(String id) {
        Path root = Locks.rootOverride != null ? Locks.rootOverride : Dirs.config().resolve("characters");
        return root.resolve(id).resolve(FOLDER);
    }
}
