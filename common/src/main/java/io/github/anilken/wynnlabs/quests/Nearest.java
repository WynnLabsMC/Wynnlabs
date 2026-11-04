package io.github.anilken.wynnlabs.quests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The not-yet-done content nearest to the player, ranked by how far they really walk: quest and
 * mini-quest start points, caves, dungeons, boss altars, raids, lootrun camps, time trials and
 * secret discoveries with a known place. One flood over the world route map ({@link Router})
 * gives the walking length to many entries at once; entries it did not reach follow by straight
 * line, marked as estimates; without the map every entry is ranked by straight line. Content more
 * than {@link #OVER} levels above the player is left out unless asked for. Pure: the candidates,
 * the position, the level and the router come in, so it is tested on hand-made grids.
 */
public final class Nearest {

    /** Content more than this many levels above the player is hidden unless {@code over}. */
    public static final int OVER = 5;
    /** The flood stops after this many entries are reached (and its cost tail). */
    public static final int WANT = 25;
    /** A result serves while the player stays within this many blocks of where it was worked out. */
    public static final double MOVE = 48;

    /** The kinds with a place on the map the list ranks, besides quests and secret discoveries. */
    public static final List<ContentType> PLACED = List.of(ContentType.CAVE, ContentType.DUNGEON, ContentType.BOSS_ALTAR,
            ContentType.RAID, ContentType.LOOTRUN_CAMP, ContentType.TIME_TRIAL);

    /** One piece of content with a place: its kind, its English name, the name its pane knows it by (a discovery's id), level, block. */
    public record Candidate(ContentType kind, String name, String key, int level, int x, int y, int z) {}

    /** A ranked entry: the walked length in blocks (-1: not reached), the straight line, and whether the distance is a straight-line estimate. */
    public record Entry(Candidate c, double walk, double straight, boolean estimate) {

        /** The distance the row shows: the walk, or the straight line for an estimate. */
        public double shown() {
            return estimate ? straight : walk;
        }
    }

    /** What is asked: where, the level, whether over-level content is wanted, every candidate, the route map's hash (null: none) and when. */
    public record Request(double x, double z, int level, boolean over, List<Candidate> all, String map, long now) {}

    /**
     * A ranking: where it was worked out, whether by route ({@code route}) or straight line, the
     * route map's state then, when, the entries, and what it was worked out for (the refresh rule).
     */
    public record Result(double x, double z, boolean route, String routeState, long computedAt, List<Entry> entries,
                         int level, boolean over, int candidates, String map) {}

    /** Walking lengths over the route map: from block (px, pz) to each block {x, z}, in blocks, -1 when not reached. */
    @FunctionalInterface
    public interface Router {
        double[] walk(double px, double pz, int[][] at, int want);
    }

    private Nearest() {}

    // ---- the candidates ----

    /**
     * Every not-yet-done piece of content with a known place: quests not completed with the book's
     * coordinates (a mini-quest without them: its post, {@code miniPost}), the {@code places} not
     * done and with a place, and the secret discoveries {@code done} does not count.
     */
    public static List<Candidate> gather(List<QuestBook.Entry> quests, Function<String, int[]> miniPost, List<Content.Line> places,
                                         List<Discoveries.Entry> discoveries, Predicate<Discoveries.Entry> done) {
        List<Candidate> out = new ArrayList<>();
        for (QuestBook.Entry q : quests) {
            if ("COMPLETED".equals(q.status())) {
                continue;
            }
            boolean mini = "MINI_QUEST".equals(q.type());
            int[] at = q.x() != null && q.z() != null ? new int[] {q.x(), q.y() == null ? 0 : q.y(), q.z()} : mini ? miniPost.apply(q.name()) : null;
            if (at != null) {
                out.add(new Candidate(mini ? ContentType.MINI_QUEST : ContentType.QUEST, q.name(), q.name(), q.level(), at[0], at[1], at[2]));
            }
        }
        for (Content.Line l : places) {
            if (!l.done() && l.place() != null && PLACED.contains(l.type())) {
                Content.Place p = l.place();
                out.add(new Candidate(l.type(), l.name(), l.name(), l.level(), p.x(), p.y(), p.z()));
            }
        }
        for (Discoveries.Entry e : discoveries) {
            if (!done.test(e)) {
                out.add(new Candidate(ContentType.SECRET_DISCOVERY, e.name(), e.id(), e.minLevel(), e.x(), e.y(), e.z()));
            }
        }
        return out;
    }

    /** The candidates the list shows at {@code level}: none more than {@link #OVER} above unless {@code over}; no level (0) always. */
    public static List<Candidate> fit(List<Candidate> all, int level, boolean over) {
        return over ? all : all.stream().filter(c -> c.level() <= level + OVER).toList();
    }

    // ---- the ranking ----

    /**
     * The candidates ranked from (px, pz): with a router, the reached ones by walked length (ties
     * by straight line, then name), then the rest by straight line as estimates; without one,
     * every one by straight line as an estimate.
     */
    public static List<Entry> rank(List<Candidate> cands, double px, double pz, Router router) {
        double[] walk = null;
        if (router != null && !cands.isEmpty()) {
            int[][] at = new int[cands.size()][];
            for (int i = 0; i < at.length; i++) {
                at[i] = new int[] {cands.get(i).x(), cands.get(i).z()};
            }
            walk = router.walk(px, pz, at, WANT);
        }
        List<Entry> reached = new ArrayList<>();
        List<Entry> rest = new ArrayList<>();
        for (int i = 0; i < cands.size(); i++) {
            Candidate c = cands.get(i);
            double straight = Math.hypot(c.x() - px, c.z() - pz);
            if (walk != null && walk[i] >= 0) {
                reached.add(new Entry(c, walk[i], straight, false));
            } else {
                rest.add(new Entry(c, -1, straight, true));
            }
        }
        reached.sort(Comparator.comparingDouble(Entry::walk).thenComparingDouble(Entry::straight).thenComparing(e -> e.c().name()));
        rest.sort(Comparator.comparingDouble(Entry::straight).thenComparing(e -> e.c().name()));
        reached.addAll(rest);
        return reached;
    }

    /** Works out a request: the fitting candidates ranked, by route when {@code router} is given. */
    public static Result compute(Request q, Router router, String routeState) {
        List<Entry> entries = rank(fit(q.all(), q.level(), q.over()), q.x(), q.z(), router);
        return new Result(q.x(), q.z(), router != null, routeState, q.now(), entries, q.level(), q.over(), q.all().hashCode(), q.map());
    }

    /**
     * Whether {@code r} still answers {@code q}: the player within {@link #MOVE} blocks of where it
     * was worked out, and the same candidates (nothing newly done), level, over-level choice and
     * route map.
     */
    public static boolean fresh(Result r, Request q) {
        return r != null && Math.hypot(q.x() - r.x(), q.z() - r.z()) <= MOVE && r.level() == q.level() && r.over() == q.over()
                && r.candidates() == q.all().hashCode() && Objects.equals(r.map(), q.map());
    }

    // ---- the local API ----

    /** The result as /nearest serves it: at most {@code limit} entries; {@code computing} while a newer one is being worked out. */
    public static JsonObject json(Result r, int limit, boolean computing, String routeState) {
        JsonObject o = new JsonObject();
        o.addProperty("computing", computing);
        o.addProperty("routeState", r == null ? routeState : r.routeState());
        if (r == null) {
            o.addProperty("mode", "straight");
            o.add("entries", new JsonArray());
            return o;
        }
        JsonObject origin = new JsonObject();
        origin.addProperty("x", Math.round(r.x()));
        origin.addProperty("z", Math.round(r.z()));
        o.add("origin", origin);
        o.addProperty("mode", r.route() ? "route" : "straight");
        o.addProperty("computedAt", r.computedAt());
        o.addProperty("over", r.over());
        JsonArray arr = new JsonArray();
        for (Entry e : r.entries().subList(0, Math.min(limit, r.entries().size()))) {
            JsonObject j = new JsonObject();
            j.addProperty("kind", e.c().kind().name());
            j.addProperty("name", e.c().name());
            j.addProperty("level", e.c().level());
            j.addProperty("x", e.c().x());
            j.addProperty("y", e.c().y());
            j.addProperty("z", e.c().z());
            if (!e.estimate()) {
                j.addProperty("walk", Math.round(e.walk()));
            }
            j.addProperty("straight", Math.round(e.straight()));
            j.addProperty("estimate", e.estimate());
            arr.add(j);
        }
        o.add("entries", arr);
        return o;
    }
}
