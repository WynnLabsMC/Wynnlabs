package io.github.anilken.wynnlabs.quests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.anilken.wynnlabs.core.Platform;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.game.Wyn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The content book's kinds besides quests: where each place is and its level (Wynntils' map data in
 * {@code wynntils/cache}, the same files {@link Grind} reads), and whether you did it. "Done" comes
 * from what Wynntils keeps (caves, discoveries) or from our own copy of its content book read
 * ({@code content-book.json} in the character's folder), which only the player's refresh press
 * starts ({@link #scan}). Files are read and written off the render thread.
 */
public final class Content {

    /** A place from the map data: its kind, name, level, block, and for caves the book's facts. */
    public record Place(ContentType type, String name, int level, int x, int y, int z, String description, String length,
                        String difficulty, List<String> rewards) {}

    /** A row of a kind: the name, the level, the status ("UNKNOWN" when never read), the place (null: none). */
    public record Line(ContentType type, String name, int level, String status, String description, Place place) {
        public boolean done() {
            return "COMPLETED".equals(status);
        }
    }

    /** How long a press waits for Wynntils' read before the "reading" state ends. */
    private static final long SCAN_TIMEOUT = 30_000;

    private static volatile List<Place> places;
    private static volatile boolean placesAsked;
    private static final Map<ContentType, Map<String, Wyn.ContentLine>> BOOK = new EnumMap<>(ContentType.class);
    private static final Map<ContentType, Long> READ_AT = new EnumMap<>(ContentType.class);
    private static final Map<ContentType, Long> ASKED_AT = new EnumMap<>(ContentType.class);
    private static volatile String loadedFor;

    private Content() {}

    // ---- the places ----

    /** Every place of the map data; empty until read (in the background, once). */
    public static List<Place> places() {
        List<Place> p = places;
        if (p == null) {
            if (!placesAsked) {
                placesAsked = true;
                CompletableFuture.runAsync(() -> places = readPlaces());
            }
            return List.of();
        }
        return p;
    }

    /** The J menu preview: places from its own sample instead of Wynntils' cache. */
    public static void preview(String caveJson, String featureJson) {
        placesAsked = true;
        places = parse(caveJson, featureJson);
    }

    private static List<Place> readPlaces() {
        Path dir = Platform.get().gameDir().resolve("wynntils/cache");
        String caves = "[]";
        String features = "[]";
        try {
            caves = Files.readString(dir.resolve("dataStaticCaveInfo"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[WynnLabs] content: no cave data: " + e);
        }
        try {
            features = Files.readString(dir.resolve("dataStaticCombatMapFeatures"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[WynnLabs] content: no map features: " + e);
        }
        return parse(caves, features);
    }

    /** The places in Wynntils' cave list and map features (caves from the cave list, with its facts). */
    static List<Place> parse(String caveJson, String featureJson) {
        List<Place> out = new ArrayList<>();
        try {
            for (JsonElement e : JsonParser.parseString(caveJson).getAsJsonArray()) {
                JsonObject c = e.getAsJsonObject();
                JsonObject loc = c.getAsJsonObject("location");
                List<String> rewards = new ArrayList<>();
                if (c.has("rewards")) {
                    c.getAsJsonArray("rewards").forEach(r -> rewards.add(ItemJson.clean(r.getAsString())));
                }
                int lv = c.has("requirements") ? parseInt(c.get("requirements").getAsString()) : 0;
                out.add(new Place(ContentType.CAVE, c.get("name").getAsString(), lv, loc.get("x").getAsInt(), loc.get("y").getAsInt(),
                        loc.get("z").getAsInt(), ItemJson.clean(str(c, "description")), str(c, "length"), str(c, "difficulty"),
                        List.copyOf(rewards)));
            }
        } catch (Exception ignored) {
            // no caves: the rest still come from the features
        }
        try {
            JsonArray all = JsonParser.parseString(featureJson).getAsJsonArray();
            for (JsonElement e : all) {
                JsonObject f = e.getAsJsonObject();
                String cat = str(f, "categoryId");
                ContentType t = null;
                for (ContentType k : ContentType.values()) {
                    if (k != ContentType.CAVE && k.feature != null && (cat.equals(k.feature) || cat.startsWith(k.feature + ":"))) {
                        t = k;
                    }
                }
                if (t == null) {
                    continue;
                }
                JsonObject a = f.getAsJsonObject("attributes");
                JsonObject loc = f.getAsJsonObject("location");
                out.add(new Place(t, str(a, "label"), a.has("level") ? a.get("level").getAsInt() : 0, loc.get("x").getAsInt(),
                        loc.get("y").getAsInt(), loc.get("z").getAsInt(), "", "", "", List.of()));
            }
        } catch (Exception ignored) {
            // no features: caves only
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- the rows of a kind ----

    /** A kind's rows: its places (or, for discoveries, the book's lines), each with its status. */
    public static List<Line> lines(ContentType type) {
        load();
        Map<String, Wyn.ContentLine> book = new LinkedHashMap<>();
        for (Wyn.ContentLine l : Wyn.knownContent(type.name())) {
            book.put(l.name(), l);
        }
        synchronized (BOOK) {
            book.putAll(BOOK.getOrDefault(type, Map.of()));
        }
        return merge(type, places(), book);
    }

    /** The places of {@code type} with the book's status by name; book lines with no place after them. */
    static List<Line> merge(ContentType type, List<Place> places, Map<String, Wyn.ContentLine> book) {
        List<Line> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Place p : places) {
            if (p.type() != type) {
                continue;
            }
            Wyn.ContentLine l = book.get(p.name());
            seen.add(p.name());
            out.add(new Line(type, p.name(), p.level() > 0 ? p.level() : l == null ? 0 : l.level(), l == null ? "UNKNOWN" : l.status(),
                    !p.description().isEmpty() || l == null ? p.description() : l.description(), p));
        }
        for (Wyn.ContentLine l : book.values()) {
            if (!seen.contains(l.name()) && !type.placed()) {
                out.add(new Line(type, l.name(), l.level(), l.status(), l.description(), null));
            }
        }
        return out;
    }

    /**
     * Recommended places for {@code level} at px, pz: not done, from 4 levels under to 2 over, of
     * the kinds you can do alone (caves, dungeons, boss altars), nearest first.
     */
    public static List<Line> near(List<Line> all, int level, double px, double pz, int max) {
        return all.stream()
                .filter(l -> l.place() != null && !l.done() && l.level() >= level - 4 && l.level() <= level + 2)
                .filter(l -> l.type() == ContentType.CAVE || l.type() == ContentType.DUNGEON || l.type() == ContentType.BOSS_ALTAR)
                .sorted(Comparator.comparingDouble(l -> Math.hypot(l.place().x() - px, l.place().z() - pz)))
                .limit(max)
                .toList();
    }

    // ---- reading the book (the player's press) ----

    /** The player pressed refresh on a kind: Wynntils reads that part of the content book. */
    public static boolean scan(ContentType type) {
        if (!type.book() || scanning(type) || !Wyn.canRescanQuests() || !Wyn.knowsKind(type.name())) {
            return false;
        }
        load();
        if (!Wyn.scanContent(type.name(), lines -> took(type, lines))) {
            return false;
        }
        synchronized (BOOK) {
            ASKED_AT.put(type, System.currentTimeMillis());
        }
        return true;
    }

    /** True from the press until the lines come back or the read times out. */
    public static boolean scanning(ContentType type) {
        synchronized (BOOK) {
            long asked = ASKED_AT.getOrDefault(type, 0L);
            return asked > 0 && System.currentTimeMillis() - asked < SCAN_TIMEOUT && READ_AT.getOrDefault(type, 0L) < asked;
        }
    }

    /** When this kind was last read for this character (0: never). */
    public static long readAt(ContentType type) {
        load();
        synchronized (BOOK) {
            return READ_AT.getOrDefault(type, 0L);
        }
    }

    private static void took(ContentType type, List<Wyn.ContentLine> lines) {
        Map<String, Wyn.ContentLine> m = new LinkedHashMap<>();
        for (Wyn.ContentLine l : lines) {
            m.put(l.name(), l);
        }
        synchronized (BOOK) {
            BOOK.put(type, m);
            READ_AT.put(type, System.currentTimeMillis());
        }
        System.out.println("[WynnLabs] took " + lines.size() + " " + type.shown + " lines from Wynntils' content book read");
        if (!Profile.id().isEmpty()) {
            Saver.mark(file(), Content::json);
        }
        if (type == ContentType.CAVE) {
            Wyn.caveMapSync();                  // the finished caves fade on the map right away
        }
    }

    /**
     * The book's lines of a kind as this character last read them (the Quests tab's refresh),
     * from memory or the character's file; empty before the first read.
     */
    public static List<Wyn.ContentLine> bookLines(ContentType type) {
        load();
        synchronized (BOOK) {
            return new ArrayList<>(BOOK.getOrDefault(type, Map.of()).values());
        }
    }

    // ---- the character's file ----

    private static Path file() {
        return Profile.dir().resolve("content-book.json");
    }

    /** The character's copy, read in the background when the character changes. */
    private static void load() {
        String id = Profile.id();
        if (id.equals(loadedFor)) {
            return;
        }
        loadedFor = id;
        synchronized (BOOK) {
            BOOK.clear();
            READ_AT.clear();
            ASKED_AT.clear();
        }
        if (id.isEmpty()) {
            return;
        }
        Path f = file();
        CompletableFuture.runAsync(() -> {
            try {
                if (!Files.exists(f)) {
                    return;
                }
                String text = Files.readString(f, StandardCharsets.UTF_8);
                Saver.known(f, text);
                read(text);
            } catch (Exception e) {
                System.out.println("[WynnLabs] could not read content-book.json: " + e);
            }
        });
    }

    static void read(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        synchronized (BOOK) {
            for (String k : o.keySet()) {
                ContentType t = ContentType.of(k);
                if (!t.book() || !t.name().equals(k)) {
                    continue;
                }
                JsonObject v = o.getAsJsonObject(k);
                Map<String, Wyn.ContentLine> m = new LinkedHashMap<>();
                for (JsonElement e : v.getAsJsonArray("lines")) {
                    JsonObject l = e.getAsJsonObject();
                    m.put(str(l, "name"), new Wyn.ContentLine(str(l, "name"), str(l, "status"), l.has("level") ? l.get("level").getAsInt() : 0,
                            str(l, "description")));
                }
                BOOK.put(t, m);
                READ_AT.put(t, v.has("readAt") ? v.get("readAt").getAsLong() : 0L);
            }
        }
    }

    static String json() {
        JsonObject o = new JsonObject();
        synchronized (BOOK) {
            for (var e : BOOK.entrySet()) {
                JsonObject v = new JsonObject();
                v.addProperty("readAt", READ_AT.getOrDefault(e.getKey(), 0L));
                JsonArray arr = new JsonArray();
                for (Wyn.ContentLine l : e.getValue().values()) {
                    JsonObject j = new JsonObject();
                    j.addProperty("name", l.name());
                    j.addProperty("status", l.status());
                    j.addProperty("level", l.level());
                    if (!l.description().isEmpty()) {
                        j.addProperty("description", l.description());
                    }
                    arr.add(j);
                }
                v.add("lines", arr);
                o.add(e.getKey().name(), v);
            }
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o);
    }
}
