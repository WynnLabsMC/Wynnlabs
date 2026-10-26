package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the quests tab would otherwise work out every frame, kept for a moment: the book's entries
 * by name, the stage a quest is on, a quest's places, the rail's rows, Wynntils' place names and
 * which places have map tiles. Each is worked out again when its inputs change or after a short
 * time (the book and the tracked task change in play), never on every frame. {@link #on} false
 * works everything out every frame again (the preview measures both).
 */
final class QuestsCache {

    /** Off: every value worked out each frame, as before (for the measurement). */
    static boolean on = true;
    /** The measurement: the tab's draw time over the frames since {@link #measure}. */
    static long nanos;
    static int frames;

    private static final long SHORT = 500;
    private static final long LONG = 30_000;
    private static final Map<String, Object[]> KEPT = new HashMap<>();
    private static final Map<Long, Long> TILES = new HashMap<>();
    /** How long "no tiles here" holds before Wynntils is asked again (its map may still be downloading). */
    private static final long NO_TILE_AGAIN = 10_000;
    private static Map<String, QuestBook.Entry> byName = Map.of();
    private static long byNameAt;

    private QuestsCache() {}

    /** The value under {@code key}, worked out again after {@code ttl} ms. */
    @SuppressWarnings("unchecked")
    static <T> T kept(String key, long ttl, Supplier<T> make) {
        if (!on) {
            return make.get();
        }
        long now = System.currentTimeMillis();
        Object[] k = KEPT.get(key);
        if (k != null && now - (long) k[1] < ttl) {
            return (T) k[0];
        }
        T v = make.get();
        if (KEPT.size() > 1000) {
            KEPT.clear();
        }
        KEPT.put(key, new Object[] {v, now});
        return v;
    }

    /** The book's entry for a quest, by name (any case); null when the book does not have it. */
    static QuestBook.Entry entry(String name) {
        if (name == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (!on || now - byNameAt > SHORT) {
            Map<String, QuestBook.Entry> m = new HashMap<>();
            for (QuestBook.Entry e : QuestBook.all()) {
                m.put(e.name().toLowerCase(Locale.ROOT), e);
            }
            byName = m;
            byNameAt = now;
        }
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    /** The stage you are on for this quest, task and wiki info (-1: not started). */
    static int stage(String name, QuestWiki.Info info, String task, Supplier<Integer> make) {
        return kept("stage|" + name + "|" + task + "|" + System.identityHashCode(info), SHORT, make);
    }

    /** A quest's places for the map and list. */
    static List<QuestMap.Place> places(String name, QuestWiki.Info info, boolean all, int stage, Supplier<List<QuestMap.Place>> make) {
        return kept("places|" + name + "|" + all + "|" + stage + "|" + System.identityHashCode(info), 1000, make);
    }

    /** Wynntils' place names, all of them, fetched once in a while (they do not move). */
    static List<Wyn.MapLabel> labels() {
        return kept("labels", LONG, () -> Wyn.mapLabels(-100_000, -100_000, 100_000, 100_000));
    }

    /** Whether Wynntils has a map tile at x, z (kept: the tiles do not change while playing). */
    static boolean tile(int x, int z) {
        if (!on) {
            return Wyn.hasMap(x - 1, z - 1, x + 1, z + 1);
        }
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Long seen = TILES.get(key);                     // Long.MAX_VALUE: tiles there; else when none were found
        long now = System.currentTimeMillis();
        if (seen != null && (seen == Long.MAX_VALUE || now - seen < NO_TILE_AGAIN)) {
            return seen == Long.MAX_VALUE;
        }
        boolean b = Wyn.hasMap(x - 1, z - 1, x + 1, z + 1);
        if (TILES.size() > 4000) {
            TILES.clear();
        }
        TILES.put(key, b ? Long.MAX_VALUE : now);
        return b;
    }

    /** Everything kept is worked out again on the next frame (a change the player just made). */
    static void forget() {
        KEPT.clear();
    }

    /** One frame of the tab drawn in {@code dt} ns. */
    static void frame(long dt) {
        nanos += dt;
        frames++;
    }

    /** Starts a measurement: the caches {@code cached} or not, the counters reset. */
    static void measure(boolean cached) {
        on = cached;
        KEPT.clear();
        TILES.clear();
        byNameAt = 0;
        nanos = 0;
        frames = 0;
    }
}
