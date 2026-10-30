package io.github.anilken.wynnlabs.build.roadmap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.game.GameState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The road map's beam results per character, on disk (roadmap-cache.json in the character's
 * folder): input key → the set it chose, slot → piece key and name, or none, and from the endgame
 * on the other whole sets it ended on ({@link Alternatives}, "~n/slot"). The beam is the
 * slow part of a map (about 20 s); with its inputs unchanged a restart needs none of it.
 * At most {@link #MAX} entries, the oldest go first. In tests (no character folder) memory only.
 */
public final class BeamCache {

    private static final int MAX = 600;
    private static final Gson GSON = new Gson();
    private static final String NONE = "-";
    /** The prefix of an alternative set's slots in an entry: "~0/WEAPON". */
    private static final String ALT = "~";

    private BeamCache() {}

    /**
     * The scoring model the kept results were made with, part of every key: raised whenever the
     * way sets are scored or searched changes (survival floor, skill point search, set bonuses,
     * prices, the ability tree per milestone, the search's starts), so a set chosen by an older
     * model is searched again instead of coming back from disk. A method, not a field: a field
     * added by a hot swap keeps no value.
     */
    static int modelVersion() {
        return 33;
    }

    /** The loaded entries and whose they are; access-ordered, so the oldest drop first. */
    private static Map<String, Map<String, String>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private static String loadedFor;
    private static boolean dirty;

    /**
     * Tests only: a store beyond this JVM, so the test JVMs and later runs search a set once
     * (null in the game, and after {@link #clear}).
     */
    public interface Shared {
        /** The entry kept for the key, or null; may wait while another JVM searches it. */
        Map<String, String> load(String key);

        /** Keeps a searched entry. */
        void keep(String key, Map<String, String> entry);
    }

    private static Shared shared;

    /** Tests only: shares the results through {@code s} (null: this JVM's memory only). */
    public static synchronized void share(Shared s) {
        shared = s;
    }

    private static synchronized Map<String, Map<String, String>> entries() {
        String id = Profile.id();
        if (!id.equals(loadedFor)) {
            loadedFor = id;
            entries = new LinkedHashMap<>(16, 0.75f, true);
            dirty = false;
            Path f = file();
            if (f != null && Files.exists(f)) {
                try {
                    Map<String, Map<String, String>> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                            new TypeToken<Map<String, Map<String, String>>>() {}.getType());
                    if (read != null) {
                        entries.putAll(read);
                    }
                } catch (Exception e) {
                    // a broken cache is an empty one
                }
            }
        }
        return entries;
    }

    private static Path file() {
        String id = Profile.id();
        return id.isEmpty() ? null : Profile.dir().resolve("roadmap-cache.json");
    }

    static boolean has(String key) {
        Shared s;
        synchronized (BeamCache.class) {
            if (entries().containsKey(key)) {
                return true;
            }
            s = shared;
        }
        Map<String, String> e = s == null ? null : s.load(key);    // outside the lock: it may wait
        if (e == null) {
            return false;
        }
        synchronized (BeamCache.class) {
            entries().put(key, e);
        }
        return true;
    }

    /** True when the beam found nothing better for these inputs. */
    static synchronized boolean isNone(String key) {
        Map<String, String> e = entries().get(key);
        return e != null && e.containsKey(NONE);
    }

    /**
     * The set kept for the key, its pieces taken from the two starting sets or the item data;
     * null when there is none, or a piece is gone or rolls otherwise now (search again then).
     */
    static synchronized Map<String, Damage.Piece> get(String key, Map<String, Damage.Piece> start,
            Map<String, Damage.Piece> build) {
        return get(key, java.util.List.of(start, build));
    }

    /** {@link #get(String, Map, Map)} with the pieces taken from any of {@code sets} (what you wear too). */
    static synchronized Map<String, Damage.Piece> get(String key, java.util.List<Map<String, Damage.Piece>> sets) {
        Map<String, String> e = entries().get(key);
        if (e == null || e.containsKey(NONE)) {
            return null;
        }
        Map<String, Damage.Piece> out = new LinkedHashMap<>();
        for (var se : e.entrySet()) {
            if (se.getKey().startsWith(ALT)) {
                continue;
            }
            Damage.Piece p = resolve(se.getValue(), sets);
            if (p == null) {
                return null;
            }
            out.put(se.getKey(), p);
        }
        return out;
    }

    /** The piece kept as "key\nname": from {@code sets}, else the item data; null when gone or rolled otherwise. */
    private static Damage.Piece resolve(String value, java.util.List<Map<String, Damage.Piece>> sets) {
        String[] kn = value.split("\n", 2);
        Damage.Piece p = null;
        for (Map<String, Damage.Piece> s : sets) {
            for (Damage.Piece q : s.values()) {         // any slot: a ring may change hands
                if (p == null && q != null && q.key().equals(kn[0])) {
                    p = q;
                }
            }
        }
        if (p == null && kn.length == 2) {
            var info = GameState.current().allGear().filter(i -> i.name().equals(kn[1])).findFirst().orElse(null);
            p = info == null ? null : Roadmap.pieceOf(info);     // a copy you have, with its rolls
        }
        return p == null || !p.key().equals(kn[0]) ? null : p;
    }

    /**
     * The other whole sets kept with the key ({@link Alternatives#pool}), in their order; a set
     * with a piece that is gone or rolls otherwise now is left out. Empty when none were kept.
     */
    static synchronized java.util.List<Map<String, Damage.Piece>> alternatives(String key,
            java.util.List<Map<String, Damage.Piece>> sets) {
        Map<String, String> e = entries().get(key);
        if (e == null) {
            return java.util.List.of();
        }
        Map<Integer, Map<String, Damage.Piece>> byIndex = new java.util.TreeMap<>();
        java.util.Set<Integer> broken = new java.util.HashSet<>();
        for (var se : e.entrySet()) {
            String k = se.getKey();
            int slash = k.indexOf('/');
            if (!k.startsWith(ALT) || slash < 0) {
                continue;
            }
            int i;
            try {
                i = Integer.parseInt(k.substring(ALT.length(), slash));
            } catch (NumberFormatException x) {
                continue;
            }
            Damage.Piece p = broken.contains(i) ? null : resolve(se.getValue(), sets);
            if (p == null) {
                broken.add(i);
                continue;
            }
            byIndex.computeIfAbsent(i, n -> new LinkedHashMap<>()).put(k.substring(slash + 1), p);
        }
        java.util.List<Map<String, Damage.Piece>> out = new java.util.ArrayList<>();
        byIndex.forEach((i, set) -> {
            if (!broken.contains(i)) {
                out.add(set);
            }
        });
        return out;
    }

    static synchronized void put(String key, Map<String, Damage.Piece> set) {
        put(key, set, java.util.List.of());
    }

    /** {@link #put(String, Map)} with the other whole sets the search ended on ({@link Alternatives#pool}). */
    static synchronized void put(String key, Map<String, Damage.Piece> set, java.util.List<Map<String, Damage.Piece>> alts) {
        Map<String, String> e = new LinkedHashMap<>();
        if (set == null) {
            e.put(NONE, "");
        } else {
            set.forEach((slot, p) -> {
                if (p != null) {
                    e.put(slot, p.key() + "\n" + p.name());
                }
            });
        }
        for (int i = 0; alts != null && i < alts.size(); i++) {
            String at = ALT + i + "/";
            alts.get(i).forEach((slot, p) -> {
                if (p != null) {
                    e.put(at + slot, p.key() + "\n" + p.name());
                }
            });
        }
        Map<String, Map<String, String>> all = entries();
        all.put(key, e);
        while (all.size() > MAX) {
            all.remove(all.keySet().iterator().next());
        }
        dirty = true;
        if (shared != null) {
            shared.keep(key, e);
        }
    }

    /** Writes the entries when they changed (the road map builder calls this after a map). */
    static synchronized void save() {
        Path f = file();
        if (!dirty || f == null) {
            return;
        }
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(entries), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            // kept in memory; written next time
        }
    }

    /** Tests only: forget everything, and share nothing until the next {@link #share}. */
    public static synchronized void clear() {
        shared = null;
        entries = new LinkedHashMap<>(16, 0.75f, true);
        loadedFor = null;
        dirty = false;
    }
}
