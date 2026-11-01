package io.github.anilken.wynnlabs.market;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Saver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The daily Trade Market price history of the items the box window shows, as Wynnventory's
 * /api/trademarket/history answers it (no key needed): one day each, its median, lowest and
 * highest price and how many listings were seen. Wynnventory deletes old listings but keeps these.
 * At most {@link #MAX} items are held; a series is fresh for a day and saved to
 * config/wynnlabs/history.json, so a restart does not ask again. Requests go through
 * {@link Prices}' listings worker and pace ({@link Prices#queueHistory}), one item at a time and
 * only for the item the window shows.
 */
public final class History {

    /** How many items are held; the one looked at longest ago goes first. */
    static final int MAX = 200;
    /** How many days are asked for. */
    public static final int DAYS = 14;
    /** A series is not asked for again within this time. */
    static final long FRESH_MS = 24 * 60 * 60 * 1000L;
    /** After a failed request (a rate limit, a server error, a timeout) the item is asked again after this. */
    static final long RETRY_MS = 10 * 60 * 1000L;

    /** Where requests go: {@link Prices}' worker in the game, a recorder in tests. */
    interface Sender {
        /** Whether history can be asked for at all (Wynnventory installed, the priceApi switch on). */
        boolean live();

        /** Asks for one item's history; true when it will be fetched (queued now or already waiting). */
        boolean queue(String name);
    }

    /** One day: its median, lowest and highest price (null when the answer had none) and listings seen. */
    public record Day(LocalDate date, Integer p50, Integer lowest, Integer highest, int count) {}

    /** An item's days, oldest first, and when they were read (epoch ms). */
    public record Series(List<Day> days, long fetchedAt) {

        /** The newest day with listings seen and a median; null when none. */
        public Day lastSold() {
            for (int i = days.size() - 1; i >= 0; i--) {
                Day d = days.get(i);
                if (d.count() > 0 && d.p50() != null) {
                    return d;
                }
            }
            return null;
        }

        /** The lowest price of the days with listings; null when none. */
        public Integer lowest() {
            Integer low = null;
            for (Day d : days) {
                if (d.count() > 0 && d.lowest() != null && (low == null || d.lowest() < low)) {
                    low = d.lowest();
                }
            }
            return low;
        }

        /** The median of the daily medians; null when no day has one. */
        public Integer typical() {
            List<Integer> p = new ArrayList<>();
            for (Day d : days) {
                if (d.p50() != null) {
                    p.add(d.p50());
                }
            }
            Double m = median(p);
            return m == null ? null : (int) Math.round(m);
        }

        /** Listings seen over all the days. */
        public int sold() {
            int n = 0;
            for (Day d : days) {
                n += d.count();
            }
            return n;
        }

        /**
         * The last 7 days' median of the daily medians against the 7 before, in percent (+8.0: up
         * 8 %), counted back from the day the series was read; null when either week has no day.
         */
        public Double weekChange() {
            LocalDate end = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(fetchedAt), ZoneOffset.UTC);
            List<Integer> recent = new ArrayList<>();
            List<Integer> before = new ArrayList<>();
            for (Day d : days) {
                if (d.p50() == null || d.count() <= 0) {
                    continue;
                }
                if (d.date().isAfter(end.minusDays(7)) && !d.date().isAfter(end)) {
                    recent.add(d.p50());
                } else if (d.date().isAfter(end.minusDays(14)) && !d.date().isAfter(end.minusDays(7))) {
                    before.add(d.p50());
                }
            }
            Double a = median(recent);
            Double b = median(before);
            if (a == null || b == null || b <= 0) {
                return null;
            }
            return (a - b) / b * 100;
        }

        /** Whether any day has a median to draw. */
        public boolean drawable() {
            for (Day d : days) {
                if (d.p50() != null) {
                    return true;
                }
            }
            return false;
        }
    }

    /** What is held for one item. Guarded by {@link #ITEMS}. */
    private static final class Entry {
        Series series;
        long failedAt;
    }

    private static final Map<String, Entry> ITEMS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX;
        }
    };

    private static final Gson GSON = new Gson();

    static Sender sender = new Sender() {
        @Override
        public boolean live() {
            return Prices.historyReady();
        }

        @Override
        public boolean queue(String name) {
            return Prices.queueHistory(name);
        }
    };

    private static volatile boolean loaded;

    /** Goes up when a held series changes. */
    public static volatile int version;

    private History() {}

    // ---- reading ----

    /** The held series of {@code name} (possibly older than a day), null while unknown. */
    public static Series of(String name) {
        ensureLoaded();
        synchronized (ITEMS) {
            Entry e = ITEMS.get(name);
            return e == null ? null : e.series;
        }
    }

    /** Whether the last request for {@code name} got no answer and nothing is held. */
    public static boolean failed(String name) {
        ensureLoaded();
        synchronized (ITEMS) {
            Entry e = ITEMS.get(name);
            return e != null && e.series == null && e.failedAt > 0;
        }
    }

    /** The days of {@code name} in an answer: other names and shinies left out, oldest first, one per date. */
    static Series parse(String name, JsonArray body) {
        TreeMap<LocalDate, Day> byDate = new TreeMap<>();
        for (JsonElement el : body) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String n = str(o, "name");
            if (n != null && !name.equalsIgnoreCase(n) || bool(o, "shiny")) {
                continue;
            }
            LocalDate date = date(str(o, "timestamp"));
            if (date == null) {
                continue;
            }
            Integer count = intOf(o, "total_count");
            byDate.put(date, new Day(date, positive(intOf(o, "p50_price")), positive(intOf(o, "lowest_price")),
                    positive(intOf(o, "highest_price")), count == null ? 0 : Math.max(0, count)));
        }
        return new Series(List.copyOf(byDate.values()), System.currentTimeMillis());
    }

    private static LocalDate date(String s) {
        if (s == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            try {
                return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static Integer positive(Integer v) {
        return v == null || v < 1 ? null : v;
    }

    // ---- the store ----

    /** An answer for {@code name} (an empty one: no history), kept and saved. */
    static void accept(String name, JsonArray body) {
        put(name, parse(name, body));
        save();
    }

    /** Keeps {@code s} for {@code name} (tests use this directly; nothing is saved). */
    static void put(String name, Series s) {
        synchronized (ITEMS) {
            Entry e = ITEMS.computeIfAbsent(name, k -> new Entry());
            e.series = s;
            e.failedAt = 0;
        }
        version++;
    }

    /** A request for {@code name} got no answer: asked again after {@link #RETRY_MS}; a held series stays. */
    static void failed(String name, long at) {
        synchronized (ITEMS) {
            ITEMS.computeIfAbsent(name, k -> new Entry()).failedAt = at;
        }
        version++;
    }

    // ---- asking ----

    /** The history of {@code name} unless one read within a day is held or it just failed. Never per frame. */
    public static void request(String name) {
        if (name == null || name.isEmpty() || !sender.live()) {
            return;
        }
        ensureLoaded();
        long now = System.currentTimeMillis();
        synchronized (ITEMS) {
            Entry e = ITEMS.get(name);
            if (e != null && (e.series != null && now - e.series.fetchedAt() < FRESH_MS
                    || e.failedAt > 0 && now - e.failedAt < RETRY_MS)) {
                return;
            }
        }
        sender.queue(name);
    }

    // ---- the local API ----

    /** /prices/history?item=: the days and their summary, or {"status":"pending"} with a request sent. */
    public static JsonObject json(String name) {
        JsonObject o = new JsonObject();
        Series s = of(name);
        if (s == null) {
            boolean failed = failed(name);
            if (!sender.live()) {
                o.addProperty("status", "off");
            } else {
                request(name);
                o.addProperty("status", failed ? "failed" : "pending");
            }
            o.addProperty("item", name);
            return o;
        }
        o.addProperty("item", name);
        JsonArray days = new JsonArray();
        for (Day d : s.days()) {
            days.add(day(d));
        }
        o.add("days", days);
        JsonObject sum = new JsonObject();
        Day last = s.lastSold();
        if (last != null) {
            JsonObject l = new JsonObject();
            l.addProperty("date", last.date().toString());
            l.addProperty("p50", last.p50());
            sum.add("lastSold", l);
        }
        addNum(sum, "lowest", s.lowest());
        addNum(sum, "typical", s.typical());
        sum.addProperty("sold", s.sold());
        Double week = s.weekChange();
        if (week != null) {
            sum.addProperty("weekChange", Math.round(week * 10) / 10.0);
        }
        o.add("summary", sum);
        o.addProperty("fetchedAt", s.fetchedAt());
        return o;
    }

    private static JsonObject day(Day d) {
        JsonObject j = new JsonObject();
        j.addProperty("date", d.date().toString());
        addNum(j, "p50", d.p50());
        addNum(j, "lowest", d.lowest());
        addNum(j, "highest", d.highest());
        j.addProperty("count", d.count());
        return j;
    }

    private static void addNum(JsonObject o, String k, Integer v) {
        if (v != null) {
            o.addProperty(k, v);
        }
    }

    // ---- history.json ----

    private static Path file() {
        return Dirs.config().resolve("history.json");
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (ITEMS) {
            if (loaded) {
                return;
            }
            loaded = true;
            try {
                Path f = file();
                Saver.settle(f);
                if (!Files.exists(f)) {
                    return;
                }
                String text = Files.readString(f, StandardCharsets.UTF_8);
                Saver.known(f, text);
                JsonObject all = GSON.fromJson(text, JsonObject.class);
                if (all == null) {
                    return;
                }
                for (Map.Entry<String, JsonElement> it : all.entrySet()) {
                    if (!it.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject o = it.getValue().getAsJsonObject();
                    Long at = o.has("fetchedAt") && o.get("fetchedAt").isJsonPrimitive() ? o.get("fetchedAt").getAsLong() : null;
                    if (at == null || !o.has("days") || !o.get("days").isJsonArray()) {
                        continue;
                    }
                    List<Day> days = new ArrayList<>();
                    for (JsonElement el : o.getAsJsonArray("days")) {
                        if (!el.isJsonObject()) {
                            continue;
                        }
                        JsonObject d = el.getAsJsonObject();
                        LocalDate date = date(str(d, "date"));
                        if (date != null) {
                            Integer count = intOf(d, "count");
                            days.add(new Day(date, intOf(d, "p50"), intOf(d, "lowest"), intOf(d, "highest"),
                                    count == null ? 0 : count));
                        }
                    }
                    Entry e = new Entry();
                    e.series = new Series(List.copyOf(days), at);
                    ITEMS.put(it.getKey(), e);
                }
            } catch (Exception e) {
                System.out.println("[WynnLabs] could not read history.json: " + e);
            }
        }
    }

    /** history.json, written in the background a few seconds after the last change. */
    private static void save() {
        Saver.mark(file(), () -> {
            JsonObject all = new JsonObject();
            synchronized (ITEMS) {
                ITEMS.forEach((name, e) -> {
                    if (e.series == null) {
                        return;
                    }
                    JsonObject o = new JsonObject();
                    o.addProperty("fetchedAt", e.series.fetchedAt());
                    JsonArray days = new JsonArray();
                    e.series.days().forEach(d -> days.add(day(d)));
                    o.add("days", days);
                    all.add(name, o);
                });
            }
            return GSON.toJson(all);
        });
    }

    /** Tests: nothing held, nothing read from disk. */
    static void clear() {
        synchronized (ITEMS) {
            ITEMS.clear();
            loaded = true;
        }
    }

    // ---- json helpers ----

    private static Double median(List<Integer> v) {
        if (v.isEmpty()) {
            return null;
        }
        List<Integer> s = new ArrayList<>(v);
        s.sort(null);
        int n = s.size();
        return n % 2 == 1 ? (double) s.get(n / 2) : (s.get(n / 2 - 1) + (double) s.get(n / 2)) / 2;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    private static Integer intOf(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonPrimitive()) {
            return null;
        }
        try {
            return (int) Math.round(o.get(k).getAsDouble());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
    }
}
