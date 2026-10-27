package io.github.anilken.wynnlabs.search;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The map marks the player asked for from the search card ("Mark on the map", "All on the map",
 * "Open on map"), per character in {@code characters/<id>/map-marks.json}. They stay on Wynntils'
 * map and in the "Marked N" list after the menu closes and after a restart, until the player
 * takes them off (a mark's ✕, "Remove all"). One mark per place ({@link MapMark#spot}); written in
 * the background by {@link Saver} on a change, never per frame. The picked row's own mark is not
 * here: that one follows the card and goes with it.
 */
public final class KeptMarks {

    private static final Gson GSON = new Gson();
    private static KeptMarks shared;

    private final Path file;
    private final Map<String, MapMark> bySpot = new LinkedHashMap<>();
    private volatile List<MapMark> all = List.of();
    private int version;

    KeptMarks(Path file) {
        this.file = file;
        try {
            if (file != null && Files.isRegularFile(file)) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement root = GSON.fromJson(text, JsonElement.class);
                if (root != null && root.isJsonArray()) {
                    for (JsonElement e : root.getAsJsonArray()) {
                        if (e.isJsonObject()) {
                            JsonObject o = e.getAsJsonObject();
                            MapMark m = new MapMark(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
                                    o.has("r") ? o.get("r").getAsInt() : 0, o.has("label") ? o.get("label").getAsString() : "",
                                    o.has("badge") && !o.get("badge").isJsonNull() ? o.get("badge").getAsString() : null, false);
                            bySpot.putIfAbsent(m.spot(), m);
                        }
                    }
                }
                Saver.known(file, text);
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] map-marks.json unreadable: " + e);
        }
        all = List.copyOf(bySpot.values());
    }

    /** This character's marks, read again when another character is played. */
    public static synchronized KeptMarks get() {
        Path f = Profile.dir().resolve("map-marks.json");
        if (shared == null || !f.equals(shared.file)) {
            shared = new KeptMarks(f);
        }
        return shared;
    }

    /** The kept marks in the order they were added; cheap to call every frame (the same list until a change). */
    public List<MapMark> all() {
        return all;
    }

    /** Counts the changes: what is drawn from the list is made again when it moves. */
    public synchronized int version() {
        return version;
    }

    public synchronized boolean has(String spot) {
        return bySpot.containsKey(spot);
    }

    /** Whether every one of these places is kept (an empty set is not). */
    public synchronized boolean hasAll(Collection<String> spots) {
        if (spots.isEmpty()) {
            return false;
        }
        for (String s : spots) {
            if (!bySpot.containsKey(s)) {
                return false;
            }
        }
        return true;
    }

    /** Keeps these marks (a place already kept stays as it was); returns how many are new. */
    public synchronized int addAll(Collection<MapMark> marks) {
        int n = 0;
        for (MapMark m : marks) {
            MapMark k = new MapMark(m.x(), m.y(), m.z(), m.radius(), m.label(), m.badge(), false);
            if (bySpot.putIfAbsent(k.spot(), k) == null) {
                n++;
            }
        }
        if (n > 0) {
            changed();
        }
        return n;
    }

    /** Takes these places off; returns how many were kept. */
    public synchronized int removeAll(Collection<String> spots) {
        int n = 0;
        for (String s : spots) {
            if (bySpot.remove(s) != null) {
                n++;
            }
        }
        if (n > 0) {
            changed();
        }
        return n;
    }

    public synchronized void remove(MapMark m) {
        if (bySpot.remove(m.spot()) != null) {
            changed();
        }
    }

    /** Everything off; returns what was kept, for "Undo". */
    public synchronized List<MapMark> clear() {
        List<MapMark> was = List.copyOf(bySpot.values());
        if (!was.isEmpty()) {
            bySpot.clear();
            changed();
        }
        return was;
    }

    private void changed() {
        version++;
        all = List.copyOf(bySpot.values());
        if (file == null) {
            return;
        }
        List<MapMark> now = all;
        Saver.mark(file, () -> {
            JsonArray arr = new JsonArray();
            for (MapMark m : now) {
                JsonObject o = new JsonObject();
                o.addProperty("x", m.x());
                o.addProperty("y", m.y());
                o.addProperty("z", m.z());
                o.addProperty("r", m.radius());
                o.addProperty("label", m.label());
                o.addProperty("badge", m.badge());
                arr.add(o);
            }
            return GSON.toJson(arr);
        });
    }

    /** A store without a file (tests). */
    static KeptMarks inMemory() {
        return new KeptMarks(null);
    }

    /** A store on this file (tests: the same file read again is a restart). */
    static KeptMarks at(Path file) {
        return new KeptMarks(file);
    }
}
