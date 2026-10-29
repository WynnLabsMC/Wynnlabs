package io.github.anilken.wynnlabs.mounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Saver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finished (or cancelled best-so-far) exact plans, one per mount + goal + options, kept on disk once
 * {@link #attach} names the file. A plan is returned with the stats it was made for, when it was saved, and whether
 * the mount has changed since ({@code stale}): showing a stale plan is fine, recomputing is the player's choice.
 */
public final class FeedCache {
    /** A cached plan; {@code stale} = the mount's stats now differ from {@code state}. */
    public record Cached(FeedPlan plan, MountStats state, long savedAtMillis, boolean stale) {
    }

    private record Entry(MountStats state, int[] acts, boolean proven, boolean goalReached, long savedAt) {
    }

    private static final int MAX = 200;
    private static final Map<String, Entry> MAP = new LinkedHashMap<>(16, 0.75f, true);
    private static volatile Path file;

    private FeedCache() {
    }

    /** Keeps the cache in this file (read now, written in the background on change). */
    public static synchronized void attach(Path f) {
        file = f;
        MAP.clear();
        try {
            if (f != null && Files.exists(f)) {
                JsonObject doc = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : doc.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    JsonArray st = o.getAsJsonArray("stats");
                    int[] lv = new int[8], li = new int[8], mx = new int[8];
                    for (int i = 0; i < 8; i++) {
                        JsonArray s = st.get(i).getAsJsonArray();
                        lv[i] = s.get(0).getAsInt();
                        li[i] = s.get(1).getAsInt();
                        mx[i] = s.get(2).getAsInt();
                    }
                    JsonArray a = o.getAsJsonArray("acts");
                    int[] acts = new int[a.size()];
                    for (int i = 0; i < acts.length; i++) {
                        acts[i] = a.get(i).getAsInt();
                    }
                    MAP.put(e.getKey(), new Entry(MountStats.of(lv, li, mx), acts, o.get("proven").getAsBoolean(),
                            o.get("goal").getAsBoolean(), o.get("saved").getAsLong()));
                }
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the mount plan cache: " + e);
        }
    }

    static String key(String mountId, FeedGoal g, FeedOptions o) {
        StringBuilder b = new StringBuilder(mountId == null ? "" : mountId).append('|');
        b.append(g.fullLimits() ? "full" : "custom").append(g.minAvgLimit()).append(g.targets()).append('|');
        b.append(o.trainSecPerLevel()).append(',').append(o.trainFocus()).append(',').append(o.trainCap()).append(',')
                .append(o.allowQueueSwaps()).append(',').append(o.maxTimeSec()).append(',').append(o.objective());
        return b.toString();
    }

    static synchronized void store(String key, MountStats state, int[] acts, boolean proven, boolean goalReached) {
        MAP.put(key, new Entry(state, acts.clone(), proven, goalReached, System.currentTimeMillis()));
        while (MAP.size() > MAX) {
            MAP.remove(MAP.keySet().iterator().next());
        }
        Path f = file;
        if (f != null) {
            Saver.mark(f, FeedCache::json);
        }
    }

    static synchronized Cached get(String key, MountStats now, FeedGoal goal, FeedOptions options) {
        Entry e = MAP.get(key);
        if (e == null) {
            return null;
        }
        FeedPlan plan = FeedPlanner.rebuild(e.state(), goal, options, e.acts(), e.proven(), "cached");
        return new Cached(plan, e.state(), e.savedAt(), now != null && !now.equals(e.state()));
    }

    private static synchronized String json() {
        JsonObject doc = new JsonObject();
        for (Map.Entry<String, Entry> e : MAP.entrySet()) {
            Entry v = e.getValue();
            JsonObject o = new JsonObject();
            JsonArray st = new JsonArray();
            for (StatLine l : v.state().stats()) {
                JsonArray s = new JsonArray();
                s.add(l.level());
                s.add(l.limit());
                s.add(l.max());
                st.add(s);
            }
            o.add("stats", st);
            JsonArray a = new JsonArray();
            for (int x : v.acts()) {
                a.add(x);
            }
            o.add("acts", a);
            o.addProperty("proven", v.proven());
            o.addProperty("goal", v.goalReached());
            o.addProperty("saved", v.savedAt());
            doc.add(e.getKey(), o);
        }
        return doc.toString();
    }
}
