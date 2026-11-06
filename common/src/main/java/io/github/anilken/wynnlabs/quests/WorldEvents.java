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
    /** A book entry that has started ("Event has started"). */
    static final long ON = 0;
    /** A book entry that is about to start ("Event starting in …"), its time not kept by the read. */
    static final long WAITING = -1;

    /** One world event: its name, centre, radius in blocks, level, and next start (0 when not scheduled). */
    public record Event(String name, int x, int y, int z, int radius, int level, String difficulty, long start) {}

    /** An event to draw: the event, when it starts (0: soon, time unknown) and whether it is on now. */
    public record Shown(Event event, long start, boolean active) {}

    private static volatile List<Event> events = List.of();
    private static volatile long fetchedAt;
    private static volatile boolean fetching;
    /** The content book's world events: name → {@link #ON}, {@link #SOON} or a start time. */
    private static volatile Map<String, Long> book = Map.of();
    private static volatile long bookAt;
    private static volatile long askedBookAt;
    private static volatile boolean loaded;
    private static ExecutorService worker;
    private static HttpClient http;

    /** Once a second: asks what is due while the main map is open and the setting is on. */
    public static void tick(boolean mapOpen, long now) {
        if (!Settings.on(SETTING)) {
            return;
        }
        if (!loaded) {
            loaded = true;
            loadCache();                            // the places, so a start heard in chat finds its event
        }
        readLabels(now);
        if (!mapOpen) {
            return;
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
        Map<String, Long> h = heard;
        // only the events still to start: one that has started leaves the map
        return shown(events, bookAt > 0 && now - bookAt < BOOK_TRUSTED ? book : null, h == null ? Map.of() : h, now)
                .stream().filter(s -> !s.active()).toList();
    }

    /** Every known event (for the bridge to ask Wynntils which ones it heard about in chat). */
    public static List<Event> all() {
        return events;
    }

    /** Start times heard in chat ("… World Event starts in 3m 59s!"): name key → start. */
    private static volatile Map<String, Long> heard;

    /**
     * A start time Wynntils heard for this event. Wynntils rounds it up to the next whole minute,
     * so a time we read ourselves within that minute ({@link #exact}) is kept.
     */
    public static void heard(String name, long start) {
        Map<String, Long> h = heardMap();
        Long had = h.get(key(name));
        if (had == null || Math.abs(had - start) >= 65_000L) {
            h.put(key(name), start);
        }
    }

    /** A start time read from the game's own countdown, to the second. */
    public static void exact(String name, long start) {
        heardMap().put(key(name), start);
    }

    /** The start kept for this event (null: none), for tests. */
    static Long heardFor(String name) {
        return heardMap().get(key(name));
    }

    private static Map<String, Long> heardMap() {
        Map<String, Long> h = heard;
        if (h == null) {
            h = new java.util.concurrent.ConcurrentHashMap<>();
            heard = h;
        }
        return h;
    }

    private static final Pattern CHAT = Pattern.compile(
            "([A-Z][A-Za-z'\\- ]*?) World Event starts in(?: (\\d+)h)?(?: (\\d+)m)?(?: (\\d+)s)?!");
    private static final Pattern LABEL = Pattern.compile("^Starts in(?: (\\d+)h)?(?: (\\d+)m)?(?: (\\d+)s)?$");

    private static final Pattern BOOK_STARTS = Pattern.compile("Event starting in(?: (\\d+)h)?(?: (\\d+)m)?(?: (\\d+)s)?");

    /** True while the content book's world events are being read (half a minute at most). */
    public static boolean reading() {
        long asked = askedBookAt;
        return asked > 0 && bookAt < asked && System.currentTimeMillis() - asked < 30_000L;
    }

    /** One content book entry seen during the read: its "Event starting in 3m 54s", to the second. */
    public static void fromLore(String name, List<String> lore, long now) {
        String n = io.github.anilken.wynnlabs.game.ItemJson.clean(name).strip();
        if (n.isEmpty()) {
            return;
        }
        for (String line : lore) {
            Matcher m = BOOK_STARTS.matcher(io.github.anilken.wynnlabs.game.ItemJson.clean(line));
            if (m.find() && (m.group(1) != null || m.group(2) != null || m.group(3) != null)) {
                exact(n, now + seconds(m.group(1), m.group(2), m.group(3)) * 1000);
                return;
            }
        }
    }

    /** "The Slimy Schism World Event starts in 3m 59s! (345 blocks away)": its start, to the second. */
    public static void onChat(net.minecraft.network.chat.Component message, boolean overlay) {
        if (overlay || !Settings.on(SETTING)) {
            return;
        }
        try {
            Matcher m = CHAT.matcher(io.github.anilken.wynnlabs.game.ItemJson.clean(message.getString()));
            if (m.find()) {
                exact(m.group(1), System.currentTimeMillis() + seconds(m.group(2), m.group(3), m.group(4)) * 1000);
            }
        } catch (Throwable t) {
            System.out.println("[WynnLabs] world event chat unreadable: " + t);
        }
    }

    /** The countdown over a world event near the player ("Starts in 4m 4s"), given to the nearest event. */
    static void readLabels(long now) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null || events.isEmpty()) {
            return;
        }
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.Display.TextDisplay t)) {
                continue;
            }
            String text = io.github.anilken.wynnlabs.game.ItemJson.clean(t.getText().getString());
            for (String line : text.split("\n")) {
                Matcher m = LABEL.matcher(line.strip());
                if (!m.matches()) {
                    continue;
                }
                Event near = nearest(events, e.getX(), e.getZ());
                if (near != null) {
                    exact(near.name(), now + seconds(m.group(1), m.group(2), m.group(3)) * 1000);
                }
            }
        }
    }

    /** The event whose centre is nearest (x, z), within its radius and 48 blocks more. */
    static Event nearest(List<Event> all, double x, double z) {
        Event best = null;
        double gap = Double.MAX_VALUE;
        for (Event ev : all) {
            double d = Math.hypot(ev.x() - x, ev.z() - z);
            if (d <= ev.radius() + 48 && d < gap) {
                gap = d;
                best = ev;
            }
        }
        return best;
    }

    static long seconds(String h, String m, String s) {
        return num(h) * 3600 + num(m) * 60 + num(s);
    }

    static List<Shown> shown(List<Event> all, Map<String, Long> fromBook, long now) {
        return shown(all, fromBook, Map.of(), now);
    }

    /**
     * An event heard in chat: from the message until {@link #RUNS} after its start, at that time.
     * Else, with the content book's list ({@code fromBook} non-null): its events, at the
     * schedule's time. Without it: the scheduled events from {@link #SOON} before their start
     * until {@link #RUNS} after.
     */
    static List<Shown> shown(List<Event> all, Map<String, Long> fromBook, Map<String, Long> fromChat, long now) {
        List<Shown> out = new ArrayList<>();
        for (Event e : all) {
            long start;
            Long c = fromChat.get(key(e.name()));
            if (c != null && c > 0 && now - c <= RUNS && (fromBook == null || fromBook.containsKey(key(e.name())) || c > now)) {
                out.add(new Shown(e, c, c <= now));
                continue;
            }
            if (fromBook != null) {
                Long b = fromBook.get(key(e.name()));
                if (b == null) {
                    continue;
                }
                if (b == ON) {
                    out.add(new Shown(e, e.start() > 0 && e.start() <= now ? e.start() : now, true));
                    continue;
                }
                start = b > 0 ? b : e.start() > now ? e.start() : 0;
            } else {
                if (e.start() <= 0 || e.start() - now > SOON || now - e.start() > RUNS) {
                    continue;
                }
                start = e.start();
            }
            out.add(new Shown(e, start, start > 0 && start <= now));
        }
        return out;
    }

    private static final Pattern STARTS_IN = Pattern.compile("(?i)starting in\\s*(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?");

    /**
     * The book's world events that are on (STARTED: "Event has started") or about to start
     * (AVAILABLE: "Event starting in …"): name → {@link #ON}, or the start time when a "starting
     * in 3m 54s" is in the line's text, else {@link #WAITING}. Wynntils' read keeps the entry's
     * region but not its countdown, so the time usually comes from the schedule.
     */
    static Map<String, Long> fromBook(List<Wyn.ContentLine> lines, long now) {
        Map<String, Long> out = new HashMap<>();
        for (Wyn.ContentLine l : lines) {
            if (!"AVAILABLE".equals(l.status()) && !"STARTED".equals(l.status())) {
                continue;
            }
            if ("STARTED".equals(l.status())) {
                out.put(key(l.name()), ON);
                continue;
            }
            long start = WAITING;
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

    /** A name as a key: lower case, without a leading "The" (chat says "The Slimy Schism"). */
    static String key(String name) {
        String k = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        return k.startsWith("the ") ? k.substring(4) : k;
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
