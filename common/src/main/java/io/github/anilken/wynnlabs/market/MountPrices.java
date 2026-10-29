package io.github.anilken.wynnlabs.market;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.Names;
import io.github.anilken.wynnlabs.model.Mount;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Mount prices by potential. A mount's worth follows its potential (a 1k-potential Wyvern Reins
 * sells for many times a fresh one), but Wynnventory uploads a mount as its name alone (tier 1,
 * no potential, no stats), so its name price mixes every potential and is never shown for a
 * mount. Instead the mount listings hovered on the Trade Market screen are kept with their
 * potential and price ({@code config/wynnlabs/mount-listings.json}); a mount is priced from
 * those near its potential, and says "depends on potential" when there are none. No request is
 * ever sent for this.
 */
public final class MountPrices {

    /** One mount listing seen on the Trade Market screen, at {@code at} (epoch ms). */
    public record Seen(String name, int potential, int price, long at) {}

    /** A band of listings: their middle price and potential, and how many there were. */
    public record Estimate(double price, int potential, int count) {}

    /** How far from the mount's potential a listing may be, tried in turn: ±10 %, then wider. */
    static final double[] BANDS = {0.10, 0.20, 0.35};
    /** Listings a band needs before it is used; the widest band takes even one. */
    static final int ENOUGH = 2;
    /** Older listings are left out: mounts sell slowly, but a week-old price is likely gone. */
    static final long FRESH_MS = 7 * 24 * 3_600_000L;
    /** At most this many listings are kept; the oldest goes first. */
    static final int MAX = 500;
    /** The tradable mount items, for a Wynntils that does not read mounts. */
    private static final Set<String> NAMES = Set.of("Horse Saddle", "Wyvern Reins", "Adasaur Harness");

    private static final Gson GSON = new Gson();
    /** Guarded by itself. */
    private static final List<Seen> SEEN = new ArrayList<>();
    private static boolean loaded;

    private MountPrices() {}

    /** Whether {@code name} (English or translated) is a mount item, whose name price means nothing. */
    public static boolean isMountName(String name) {
        if (name == null) {
            return false;
        }
        if (NAMES.contains(name)) {
            return true;
        }
        String en = Names.english(name);
        return en != null && NAMES.contains(en);
    }

    /** A mount listing on the Trade Market screen at {@code price}: kept with its potential. */
    public static void seen(Mount m, int price) {
        if (m == null || m.name() == null || price <= 0) {
            return;
        }
        String json;
        synchronized (SEEN) {
            load();
            if (!add(SEEN, new Seen(m.name(), m.potential(), price, System.currentTimeMillis()))) {
                return;
            }
            json = toJson(SEEN);
        }
        Saver.mark(file(), () -> json);
    }

    /**
     * Keeps {@code s}: the same listing seen again (name, potential, price) only gets the new
     * time, and only once a minute (a hovered listing is seen every frame). True when changed.
     */
    static boolean add(List<Seen> list, Seen s) {
        for (int i = 0; i < list.size(); i++) {
            Seen o = list.get(i);
            if (o.name().equals(s.name()) && o.potential() == s.potential() && o.price() == s.price()) {
                if (s.at() - o.at() < 60_000L) {
                    return false;
                }
                list.remove(i);
                list.add(s);
                return true;
            }
        }
        list.add(s);
        while (list.size() > MAX) {
            list.remove(0);
        }
        return true;
    }

    /** The price of this mount from the listings near its potential; null when none are known. */
    public static Estimate estimate(Mount m) {
        if (m == null) {
            return null;
        }
        List<Seen> copy;
        synchronized (SEEN) {
            load();
            copy = List.copyOf(SEEN);
        }
        return estimate(copy, m.name(), m.potential(), System.currentTimeMillis());
    }

    /**
     * From the fresh listings of {@code name}: the first band around {@code potential} with
     * {@link #ENOUGH} listings, else the widest band with any; null when even that is empty. The
     * price is the lower middle of the band (what a buyer is likely to pay), the potential its
     * middle.
     */
    static Estimate estimate(List<Seen> all, String name, int potential, long now) {
        List<Seen> fresh = new ArrayList<>();
        for (Seen s : all) {
            if (s.name().equals(name) && now - s.at() <= FRESH_MS) {
                fresh.add(s);
            }
        }
        List<Seen> in = List.of();
        for (double band : BANDS) {
            int reach = Math.max(2, (int) Math.round(potential * band));
            in = fresh.stream().filter(s -> Math.abs(s.potential() - potential) <= reach).toList();
            if (in.size() >= ENOUGH) {
                break;
            }
        }
        if (in.isEmpty()) {
            return null;
        }
        int[] prices = in.stream().mapToInt(Seen::price).sorted().toArray();
        int[] pots = in.stream().mapToInt(Seen::potential).sorted().toArray();
        return new Estimate(prices[(prices.length - 1) / 2], pots[(pots.length - 1) / 2], in.size());
    }

    /**
     * The tooltip's price for a mount: "~7.2 LE for ~1,000 potential (3 listings)", or "depends
     * on potential" when nothing near it was seen (or the potential could not be read).
     */
    public static String text(Mount m) {
        Estimate e = estimate(m);
        if (e == null) {
            return L.t("wynnlabs.price.mount_depends");
        }
        return text(e);
    }

    static String text(Estimate e) {
        return L.n("wynnlabs.price.mount_band", e.count(), Prices.fmt(e.price()), thousands(e.potential()), e.count());
    }

    /** "1,000" in English, "1.000" in Turkish. */
    static String thousands(int v) {
        String code = L.code();
        return String.format(code != null && code.startsWith("tr") ? Locale.forLanguageTag("tr") : Locale.ENGLISH, "%,d", v);
    }

    // ---- the file ----

    private static Path file() {
        return Dirs.config().resolve("mount-listings.json");
    }

    /** Once, under the lock: what earlier sessions saw, old listings dropped. */
    private static void load() {
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
            long now = System.currentTimeMillis();
            for (JsonElement el : GSON.fromJson(text, JsonArray.class)) {
                JsonObject o = el.getAsJsonObject();
                Seen s = new Seen(o.get("name").getAsString(), o.get("potential").getAsInt(), o.get("price").getAsInt(),
                        o.get("at").getAsLong());
                if (now - s.at() <= FRESH_MS) {
                    SEEN.add(s);
                }
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read mount-listings.json: " + e);
        }
    }

    private static String toJson(List<Seen> list) {
        JsonArray a = new JsonArray();
        for (Seen s : list) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s.name());
            o.addProperty("potential", s.potential());
            o.addProperty("price", s.price());
            o.addProperty("at", s.at());
            a.add(o);
        }
        return GSON.toJson(a);
    }
}
