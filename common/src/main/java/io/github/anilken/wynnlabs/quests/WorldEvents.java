package io.github.anilken.wynnlabs.quests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Workers;
import io.github.anilken.wynnlabs.game.Wyn;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * World events on Wynntils' main map: where each one is (its centre and radius) and when it
 * starts. The places and the shared schedule come from a public world event list, asked at most
 * once an hour and kept in {@code config/wynnlabs/world-events.json}. While the main map is open
 * the content book's world events are read every few minutes (Wynntils' own hidden read, the
 * one approved exception for this feature), which says which events are on in this world and
 * corrects their start times. Nothing is asked while the map is closed or the setting is off.
 */
public final class WorldEvents {

    private WorldEvents() {}

    public static final String SETTING = "worldEvents";
    static final String URL = "https://athena.wynntils.com/cache/get/worldEvents";
    static final long FETCH_EVERY = 60 * 60_000L;
    static final long BOOK_EVERY = 3 * 60_000L;
    /** The content book's word counts for this long; after it the shared schedule alone decides. */
    static final long BOOK_TRUSTED = 10 * 60_000L;
    /** With only the schedule: shown from this long before the start until this long after. */
    static final long SOON = 30 * 60_000L;
    static final long RUNS = 15 * 60_000L;

    /** One world event: its name, centre, radius in blocks, level, and next start (0 when not scheduled). */
    public record Event(String name, int x, int y, int z, int radius, int level, String difficulty, long start) {}

    /** An event to draw: the event, when it starts (or started) and whether it is on now. */
    public record Shown(Event event, long start, boolean active) {}

    private static volatile List<Event> events = List.of();
    private static volatile long fetchedAt;
    private static volatile boolean fetching;
    /** The content book's world events: name → start time (0: on, no time given). */
    private static volatile Map<String, Long> book = Map.of();
    private static volatile long bookAt;
    private static volatile long askedBookAt;
    private static volatile boolean loaded;
    private static ExecutorService worker;
    private static HttpClient http;

    /** Once a second: asks what is due while the main map is open and the setting is on. */
    public static void tick(boolean mapOpen, long now) {
        if (!mapOpen || !Settings.on(SETTING)) {
            return;
        }
        if (!loaded) {
            loaded = true;
            loadCache();
        }
        if (!fetching && now - fetchedAt > FETCH_EVERY) {
            fetching = true;
            if (worker == null) {
                worker = Workers.singleLow("world-events");
                http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            }
            worker.execute(WorldEvents::fetch);
        }
        if (now - askedBookAt > BOOK_EVERY) {
            askedBookAt = now;
            Wyn.scanWorldEvents(lines -> {
                long at = System.currentTimeMillis();
                book = fromBook(lines, at);
                bookAt = at;
            });
        }
    }

    /** The events to draw now. */
    public static List<Shown> shown(long now) {
        if (!Settings.on(SETTING)) {
            return List.of();
        }
        return shown(events, bookAt > 0 && now - bookAt < BOOK_TRUSTED ? book : null, now);
    }

    /**
     * With the content book's list ({@code fromBook} non-null): its events, at the book's start
     * time or else the schedule's. Without it: the scheduled events from {@link #SOON} before
     * their start until {@link #RUNS} after.
     */
    static List<Shown> shown(List<Event> all, Map<String, Long> fromBook, long now) {
        List<Shown> out = new ArrayList<>();
        for (Event e : all) {
            long start;
            if (fromBook != null) {
                Long b = fromBook.get(key(e.name()));
                if (b == null) {
                    continue;
                }
                start = b > 0 ? b : e.start() > 0 ? e.start() : now;
            } else {
                if (e.start() <= 0 || e.start() - now > SOON || now - e.start() > RUNS) {
                    continue;
                }
                start = e.start();
            }
            out.add(new Shown(e, start, start <= now));
        }
        return out;
    }

    private static final Pattern STARTS_IN = Pattern.compile("(?i)starting in\\s*(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?");

    /**
     * The book's world events that are on or about to start (not finished, not out of reach):
     * name → start time from its "starting in 3m 54s" line, 0 when it has none (on now).
     */
    static Map<String, Long> fromBook(List<Wyn.ContentLine> lines, long now) {
        Map<String, Long> out = new HashMap<>();
        for (Wyn.ContentLine l : lines) {
            if (!"AVAILABLE".equals(l.status()) && !"STARTED".equals(l.status())) {
                continue;
            }
            long start = 0;
            Matcher m = STARTS_IN.matcher(l.description());
            if (m.find() && (m.group(1) != null || m.group(2) != null || m.group(3) != null)) {
                long s = num(m.group(1)) * 3600 + num(m.group(2)) * 60 + num(m.group(3));
                start = now + s * 1000;
            }
            out.put(key(l.name()), start);
        }
        return out;
    }

    private static long num(String s) {
        return s == null ? 0 : Long.parseLong(s);
    }

    static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    /** The world event list: each event's first place; events with no place are left out. */
    static List<Event> parse(String json) {
        List<Event> out = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            return out;
        }
        for (JsonElement el : root.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String name = str(o, "name");
            JsonArray locs = o.has("location") && o.get("location").isJsonArray() ? o.getAsJsonArray("location") : null;
            if (name == null || locs == null || locs.isEmpty() || !locs.get(0).isJsonObject()) {
                continue;
            }
            JsonObject loc = locs.get(0).getAsJsonObject();
            JsonObject at = loc.has("event") && loc.get("event").isJsonObject() ? loc.getAsJsonObject("event") : null;
            if (at == null) {
                continue;
            }
            long start = 0;
            String when = str(o, "schedule");
            if (when != null) {
                try {
                    start = OffsetDateTime.parse(when).toInstant().toEpochMilli();
                } catch (RuntimeException ignored) {
                    // an unreadable time: the event shows only when the book lists it
                }
            }
            out.add(new Event(name, num(at, "x"), num(at, "y"), num(at, "z"), num(loc, "radius"), num(o, "level"),
                    str(o, "difficulty"), start));
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    private static int num(JsonObject o, String k) {
        try {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Path cache() {
        return Dirs.config().resolve("world-events.json");
    }

    private static void loadCache() {
        try {
            Path p = cache();
            if (Files.exists(p)) {
                events = List.copyOf(parse(Files.readString(p, StandardCharsets.UTF_8)));
                fetchedAt = Files.getLastModifiedTime(p).toMillis();
            }
        } catch (Throwable t) {
            System.out.println("[WynnLabs] world event cache unreadable: " + t);
        }
    }

    private static void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "WynnLabs").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 200) {
                List<Event> got = parse(res.body());
                if (!got.isEmpty()) {
                    events = List.copyOf(got);
                    Files.createDirectories(cache().getParent());
                    Files.writeString(cache(), res.body(), StandardCharsets.UTF_8);
                }
            } else {
                System.out.println("[WynnLabs] world events: HTTP " + res.statusCode());
            }
        } catch (Throwable t) {
            System.out.println("[WynnLabs] world events unreachable: " + t);
        } finally {
            fetchedAt = System.currentTimeMillis();     // a failure also waits an hour
            fetching = false;
        }
    }
}
