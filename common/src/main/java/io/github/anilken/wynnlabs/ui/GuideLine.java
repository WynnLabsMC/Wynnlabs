package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Workers;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.Pin;
import io.github.anilken.wynnlabs.quests.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A navigation path from the player to the compass target (setting guideLine), in the manner of
 * Black Desert's: glowing light cyan chevrons flowing toward the target, with a thin pillar of
 * light over a soft disc where it ends. The target is the nearest pin of ours on the Wynntils map, else the nearest
 * other waypoint there (a map click or /compass puts its target there too).
 *
 * <p>On foot the strip lies flat on the ground along a walkable route ({@link GuidePath}) through
 * the loaded area, worked out every {@link #ROUTE_EVERY} ticks on a thread of its own; past the
 * loaded area, or where no route goes on, it continues straight, laid on the highest block every
 * {@link #STEP} blocks. Where the walkable ground around the player is closed in (the search runs
 * out of spots before its budget) nothing is drawn into the rock: the route leads to an exit, where
 * the ground goes on past the search box or into chunks not loaded yet (it grows from there as the
 * player walks), or, with none, to the spot nearest the target; the pillar stands there with a short
 * faint arrow toward the target. The route aims at the target's floor,
 * not only its column, swims, and climbs ladders and vines ({@link GuideTerrain}). A target beyond
 * the loaded area is first planned over the coarse map of the walkable world ({@link WorldRoute}),
 * so the way round a mountain or out of a sealed canyon is known before its chunks load: the
 * block-level route aims at the farthest waypoint of that corridor inside the loaded area, and past
 * it the corridor is drawn faint on the map's heights instead of a straight line. While riding or
 * gliding a straight line through the air leads until a ground route is found, which is
 * searched the same way from the ground under the player (the highest spot to stand on in their
 * column, however far down: {@link GuidePath#surface}); where there is one it is the only line,
 * so the player sees where to land and walk (a cliff city a wyvern cannot reach). Depth tested like {@link ChestBeams}, so terrain hides it. The dashes' stretch of
 * path is laid on the ground every 2 ticks; each frame only moves the dashes along it and draws.
 * Drawing only: nothing is sent to the server.
 */
public final class GuideLine {

    /** Ground points this far apart on the straight stretch, in blocks. */
    static final double STEP = 2;
    /** The line stops this far from the player, in blocks (measured flat). */
    static final double CAP = 300;
    /** How far above the ground the strip lies. */
    static final double LIFT = 0.08;
    /** Ticks between two looks at the target. */
    private static final int EVERY = 10;
    /** Ticks between two route searches. */
    private static final int ROUTE_EVERY = 20;
    /** At most this many spots looked at in one round of route searches, ... */
    private static final int BUDGET = 150_000;
    /** ... at most this many for one candidate pin, so the nearest cannot use them all, ... */
    private static final int EACH = 60_000;
    /** ... and at most this long for the round, in milliseconds. */
    private static final long SEARCH_MILLIS = 600;
    /** On foot, up to this many of our pins (nearest first, within the search box) are tried. */
    private static final int CANDIDATES = 4;
    /** A candidate that could not be reached is not searched again while the player stays this close. */
    private static final double RETRY = 8;
    /** The world plan looks at most at this many cells of the coarse map, for at most this long in milliseconds, ... */
    private static final int WORLD_BUDGET = 300_000;
    private static final long WORLD_MILLIS = 400;
    /** ... is planned again once the player has moved this far from where it was planned, ... */
    private static final double REPLAN = 32;
    /** ... hands the block-level route a waypoint about every this many blocks along its corridor, ... */
    private static final double WAYPOINT = 24;
    /** ... aims at waypoints this far inside the loaded area, ... */
    private static final int MARGIN = 8;
    /** ... and is not used when the player is this far under the map's surface (a cave). */
    private static final int UNDER = 12;
    /** {@code /guide?dump=1}: the box written, this many blocks each way flat and up and down by default. */
    public static final int DUMP_FLAT = 64;
    public static final int DUMP_UP = 24;

    /** Height of the highest block at a column, or {@link #UNKNOWN} where the chunk is not loaded. */
    @FunctionalInterface
    interface Ground {
        int height(int x, int z);
    }

    static final int UNKNOWN = Integer.MIN_VALUE;

    /** The target being led to (the pin's block centre), its label and whether the pin is ours. */
    record Target(double x, double y, double z, String label, boolean ours) {}

    /**
     * The last route search, for /guide: whether it reached the target, its spots, the spots
     * looked at, the last reachable spot {x, feetY, z}, the box and budget, how long it took, and
     * whether the walkable ground was closed in, with the columns reached and the stuck spots.
     */
    record Search(boolean reached, int nodes, int expanded, int[] spot, int box, int budget, long millis,
                  boolean enclosed, int columns, List<GuidePath.Stuck> stuck, int[] exit, String exitKind) {}

    /**
     * A ground path: its points {x, y, z}, player end first, where its pillar stands (null: none),
     * the index from which the points are only the straight guess past the last reachable spot
     * (the size when the route reaches the target), and where a short arrow from the pillar points
     * when the target cannot be walked to (null: no arrow).
     */
    record Path(List<double[]> points, double[] end, int tail, double[] arrow) {
        Path(List<double[]> points, double[] end, int tail) {
            this(points, end, tail, null);
        }
    }

    /**
     * The world plan of the last route search, for /guide: whether it was used, why not when it
     * was not, its length in cells, how long it took, and the corridor waypoint the block-level
     * route aims at {x, feetY, z} (null: none).
     */
    record World(boolean used, String why, int cells, long millis, double[] next) {}

    /**
     * A world plan kept (route thread only): where the player stood when it was made, the target
     * it leads to, the plan and its corridor waypoints (a null plan: no way was found, {@code why}
     * says why, and none is looked for again until the player has moved {@link #REPLAN}).
     */
    private record Coarse(int sx, int sz, Target target, WorldRoute map, WorldRoute.Plan plan, List<double[]> waypoints, String why) {}

    private static final Path NO_PATH = new Path(List.of(), null, 0);

    private static int tick;
    /** The last route search (null: none yet) and the last error's message (null: none), for /guide. */
    private static volatile Search lastSearch;
    private static volatile String lastError;
    private static boolean failed;
    private static volatile Target target;
    /** The pins the line may lead to, nearest first; a new list starts a new {@link #generation}. */
    private static volatile List<Target> candidates = List.of();
    private static volatile int generation;
    /** The last round's candidates as searched, and the one led to, for /guide. */
    private static volatile GuidePath.Choice lastChoice;
    private static volatile List<Target> lastCandidates = List.of();
    /** A failed search kept: where the player stood and the route found then. */
    private record Failed(int x, int y, int z, GuidePath.Route route) {}
    /** Failed searches by target spot (route thread only), dropped when the pins on the map change. */
    private static final Map<Long, Failed> FAILED = new HashMap<>();
    private static int failedFor;
    /** The world plan kept (route thread only; null: none) and the last round's, for /guide. */
    private static Coarse coarse;
    private static volatile World lastWorld;
    /** The ground path drawn; replaced (never changed) by the tick or the route thread. */
    private static volatile Path ground = NO_PATH;
    /** The dashes' stretch of the ground path, from the player's feet; rebuilt every 2 ticks (null: none). */
    private static volatile GuideTrack.Track track;
    /** Riding or gliding: the straight line through the air (faint while a ground route is shown). */
    private static volatile boolean airLine;
    /** Riding or gliding with no ground under the player: why ({@code /guide}); null otherwise. */
    private static volatile String groundWhy;
    /** Riding or gliding: where the ground route starts, under the player (null: no ground there, or on foot). */
    private static volatile int[] groundStart;
    /** The target the shown ground path was routed for (null: only the straight line so far). */
    private static volatile Target routed;
    /** One thread for route searches; a new request replaces one still waiting. */
    private static final ThreadPoolExecutor ROUTER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), Workers.factory("WynnLabs guide route", false),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private GuideLine() {}

    /**
     * Points along the ground from ({@code sx}, {@code sy}, {@code sz}) toward the target, one every
     * {@code step} blocks, at most {@code cap} blocks (flat distance) out; the last one is the
     * target's column when it is within reach. Each point sits {@link #LIFT} above the ground; where
     * the ground is unknown, or rises more than a walkable climb at once (a cave roof, a building
     * over the way), the line keeps the last height.
     */
    static List<double[]> sample(double sx, double sy, double sz, double tx, double tz,
                                 double step, double cap, Ground ground) {
        double dx = tx - sx;
        double dz = tz - sz;
        double flat = Math.sqrt(dx * dx + dz * dz);
        double len = Math.min(flat, cap);
        int n = Math.max(1, (int) Math.ceil(len / step));
        List<double[]> out = new ArrayList<>(n + 1);
        double y = sy + LIFT;
        out.add(new double[] {sx, y, sz});
        if (flat < 1e-6) {
            return out;
        }
        double climb = step * 1.5 + 1;
        for (int i = 1; i <= n; i++) {
            double d = Math.min(len, i * step);
            double x = sx + dx / flat * d;
            double z = sz + dz / flat * d;
            int h = ground.height((int) Math.floor(x), (int) Math.floor(z));
            if (h != UNKNOWN && h + LIFT <= y + climb) {
                y = h + LIFT;
            }
            out.add(new double[] {x, y, z});
        }
        return out;
    }


    /** The pin to lead to: the nearest of ours, else the nearest of any; null with none on the map. */
    static Target pick(List<Pin> pins, double px, double pz) {
        Pin best = null;
        boolean bestOurs = false;
        double bestD = Double.MAX_VALUE;
        for (Pin p : pins) {
            boolean ours = Waypoints.ours(p);
            double d = sq(p.x() + 0.5 - px) + sq(p.z() + 0.5 - pz);
            if (best == null || ours && !bestOurs || ours == bestOurs && d < bestD) {
                best = p;
                bestOurs = ours;
                bestD = d;
            }
        }
        return best == null ? null : new Target(best.x() + 0.5, best.y(), best.z() + 0.5, best.name(), bestOurs);
    }

    /**
     * The pins to try on foot: ours within {@code box} blocks (flat), nearest first, at most
     * {@link #CANDIDATES}; with none of ours near, the one {@link #pick} would lead to.
     */
    static List<Target> candidates(List<Pin> pins, double px, double pz, int box) {
        List<Pin> ours = new ArrayList<>();
        for (Pin p : pins) {
            if (Waypoints.ours(p) && Math.abs(p.x() + 0.5 - px) <= box && Math.abs(p.z() + 0.5 - pz) <= box) {
                ours.add(p);
            }
        }
        if (ours.isEmpty()) {
            Target t = pick(pins, px, pz);
            return t == null ? List.of() : List.of(t);
        }
        ours.sort(java.util.Comparator.comparingDouble(p -> sq(p.x() + 0.5 - px) + sq(p.z() + 0.5 - pz)));
        List<Target> out = new ArrayList<>();
        for (int i = 0; i < Math.min(CANDIDATES, ours.size()); i++) {
            Pin p = ours.get(i);
            out.add(new Target(p.x() + 0.5, p.y(), p.z() + 0.5, p.name(), true));
        }
        return List.copyOf(out);
    }

    /** Every {@link #EVERY} ticks: the target, and when a ground path is wanted, its search. */
    public static void onTick(Minecraft mc) {
        tick++;
        if (tick % 2 == 0) {
            try {
                track = GuideTrack.buildTrack(mc, ground);
            } catch (Throwable e) {
                track = null;
                lastError = e.toString();
            }
        }
        if (tick % EVERY != 0) {
            return;
        }
        Player p = mc.player;
        if (failed || p == null || mc.level == null || !Settings.on("guideLine")) {
            airLine = false;
            clear();
            return;
        }
        try {
            var pins = Wyn.waypoints();
            List<Pin> all = pins == null ? List.of() : pins.all();
            int box = Math.max(32, Math.min(256, mc.options.getEffectiveRenderDistance() * 16));
            List<Target> cands = candidates(all, p.getX(), p.getZ(), box);
            boolean changed = !cands.equals(candidates);
            if (changed) {
                // a new set of pins: lead to the nearest until a route says otherwise
                candidates = cands;
                generation++;
                target = cands.isEmpty() ? null : cands.get(0);
            }
            Target t = target;
            ClientLevel level = mc.level;
            int px = (int) Math.floor(p.getX());
            int py = (int) Math.floor(p.getY() + 0.01);
            int pz = (int) Math.floor(p.getZ());
            boolean air = GroundProbe.airborne(p);
            int[] start = null;
            if (t == null) {
                // nothing to lead to
            } else if (!air) {
                start = new int[] {px, py, pz};
            } else {
                // the ground under the mount, however far down: where to land and walk on
                int top = GroundProbe.heights(level).height(px, pz);
                start = GuidePath.surface(GroundProbe.walkable(level), px, pz, py, top, level.getMinY());
                groundWhy = start != null ? null : top == UNKNOWN ? "the player's column is not loaded"
                        : "no spot to stand on below the player (feet " + py + ", column top " + top + ")";
            }
            airLine = t != null && air;
            groundStart = air ? start : null;
            if (start == null) {
                ground = NO_PATH;
                routed = null;
                return;
            }
            int sx = start[0];
            int sy = start[1];
            int sz = start[2];
            if (air && changed) {
                // in the air the air line leads until a ground route is found
                ground = NO_PATH;
                routed = null;
            } else if (!air && (changed || routed == null)) {
                // the straight line at once; the route replaces it when found
                List<double[]> line = sample(sx + 0.5, sy, sz + 0.5, t.x(), t.z(), STEP, CAP, GroundProbe.heights(level));
                ground = new Path(List.copyOf(line), line.get(line.size() - 1), line.size());
            }
            if (changed || tick % ROUTE_EVERY == 0) {
                if (changed) {
                    routed = null;
                }
                int gen = generation;
                int pinsNow = all.hashCode();
                ROUTER.execute(() -> route(level, cands, gen, pinsNow, sx, sy, sz, box));
            }
        } catch (Throwable e) {
            failed = true;
            clear();
            lastError = e.toString();
            System.out.println("[WynnLabs] guide line failed, off until restart: " + e);
        }
    }

    /** /guide: the setting, the target, the mode and the last route search, to see why a route fails. */
    public static JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("setting", Settings.on("guideLine"));
        o.addProperty("broken", failed);
        Target t = target;
        if (t == null) {
            o.add("target", null);
        } else {
            JsonObject j = new JsonObject();
            j.addProperty("x", t.x());
            j.addProperty("y", t.y());
            j.addProperty("z", t.z());
            j.addProperty("label", t.label());
            j.addProperty("ours", t.ours());
            o.add("target", j);
        }
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            o.add("player", null);
        } else {
            JsonArray me = new JsonArray();
            me.add(p.getX());
            me.add(p.getY());
            me.add(p.getZ());
            o.add("player", me);
        }
        boolean air = t != null && p != null && airLine && GroundProbe.airborne(p);
        o.addProperty("mode", t == null || p == null ? "none" : air && routed != null ? "air+ground" : air ? "air" : "ground");
        // riding or gliding: where the ground route starts, under the player (null: no ground there)
        int[] gs = groundStart;
        if (gs == null || !air) {
            o.add("groundStart", null);
            o.addProperty("groundWhy", air ? groundWhy : null);
        } else {
            JsonArray a = new JsonArray();
            for (int v : gs) {
                a.add(v);
            }
            o.add("groundStart", a);
        }
        o.addProperty("routed", routed != null && routed.equals(t));
        o.addProperty("points", ground.points().size());
        JsonArray route = new JsonArray();
        for (double[] q : ground.points()) {
            JsonArray a = new JsonArray();
            a.add(Math.round(q[0] * 10) / 10.0);
            a.add(Math.round(q[1] * 10) / 10.0);
            a.add(Math.round(q[2] * 10) / 10.0);
            route.add(a);
        }
        o.add("route", route);
        Search s = lastSearch;
        if (s == null) {
            o.add("search", null);
        } else {
            JsonObject j = new JsonObject();
            j.addProperty("reached", s.reached());
            j.addProperty("nodes", s.nodes());
            j.addProperty("expanded", s.expanded());
            if (s.spot() == null) {
                j.add("lastSpot", null);
            } else {
                JsonArray a = new JsonArray();
                for (int v : s.spot()) {
                    a.add(v);
                }
                j.add("lastSpot", a);
            }
            j.addProperty("box", s.box());
            j.addProperty("budget", s.budget());
            j.addProperty("millis", s.millis());
            j.addProperty("expandedPerMs", s.millis() == 0 ? s.expanded() : (double) s.expanded() / s.millis());
            // closed in: the spots reached nearest the target and why each neighbour was refused
            j.addProperty("enclosed", s.enclosed());
            if (s.enclosed()) {
                j.addProperty("columns", s.columns());
                // where the ground goes on out of sight, which the line leads to (null: sealed)
                if (s.exit() == null) {
                    j.add("exit", null);
                } else {
                    JsonArray e = new JsonArray();
                    for (int v : s.exit()) {
                        e.add(v);
                    }
                    j.add("exit", e);
                }
                j.addProperty("exitKind", s.exitKind());
                JsonArray stuck = new JsonArray();
                for (GuidePath.Stuck st : s.stuck()) {
                    JsonObject k = new JsonObject();
                    k.addProperty("x", st.x());
                    k.addProperty("y", st.y());
                    k.addProperty("z", st.z());
                    JsonObject why = new JsonObject();
                    st.rejected().forEach(why::addProperty);
                    k.add("rejected", why);
                    stuck.add(k);
                }
                j.add("stuck", stuck);
            }
            o.add("search", j);
        }
        // the candidate pins of the last round: which were searched, how far each got, which one leads
        GuidePath.Choice c = lastChoice;
        List<Target> cands = lastCandidates;
        JsonArray list = new JsonArray();
        for (int i = 0; i < cands.size(); i++) {
            Target ct = cands.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("label", ct.label());
            j.addProperty("x", ct.x());
            j.addProperty("y", ct.y());
            j.addProperty("z", ct.z());
            GuidePath.Try tr = c != null && i < c.tried().size() ? c.tried().get(i) : null;
            j.addProperty("tried", tr != null);
            if (tr != null) {
                j.addProperty("reached", tr.route().reached());
                j.addProperty("expanded", tr.route().expanded());
                j.addProperty("cached", tr.cached());
            }
            list.add(j);
        }
        o.add("candidates", list);
        if (c == null) {
            o.add("leadsTo", null);
        } else {
            o.addProperty("leadsTo", c.chosen());
        }
        // the world map's data on disk: missing, downloading, ready (hash and age) or failed
        WorldRouteData.Status ds = WorldRouteData.status();
        JsonObject data = new JsonObject();
        data.addProperty("state", ds.state().name().toLowerCase(java.util.Locale.ROOT));
        data.addProperty("hash", ds.hash());
        data.addProperty("ageMinutes", ds.hash() == null ? null : ds.age() / 60_000);
        data.addProperty("why", ds.why());
        WorldRoute wm = WorldRouteData.current();
        data.addProperty("impassable", wm == null ? null : wm.impassable());
        data.addProperty("wallLines", wm == null ? null : wm.gates().lines());
        data.addProperty("wallSegments", wm == null ? null : wm.gates().segments());
        data.addProperty("gates", wm == null ? null : wm.gates().gates());
        data.addProperty("corridors", wm == null ? null : wm.tunnels());
        WorldRoads roads = WorldRouteData.roads();
        data.addProperty("roads", WorldRouteData.roadsHash());
        data.addProperty("roadTiles", roads == null ? null : roads.tiles());
        o.add("data", data);
        // the world plan: whether the coarse map led the way, how long it is and the next waypoint
        World w = lastWorld;
        if (w == null) {
            o.add("world", null);
        } else {
            JsonObject j = new JsonObject();
            j.addProperty("used", w.used());
            j.addProperty("why", w.why());
            j.addProperty("cells", w.cells());
            j.addProperty("millis", w.millis());
            if (w.next() == null) {
                j.add("next", null);
            } else {
                JsonArray a = new JsonArray();
                for (double v : w.next()) {
                    a.add(v);
                }
                j.add("next", a);
            }
            o.add("world", j);
        }
        o.addProperty("error", lastError);
        return o;
    }

    private static void clear() {
        target = null;
        groundStart = null;
        track = null;
        candidates = List.of();
        ground = NO_PATH;
        routed = null;
    }


    /**
     * On the route thread: the walking route to the first candidate that can be reached (the
     * nearest when none can), smoothed, then straight on to that target from where it stops. Kept
     * only while the candidates are still the same.
     */
    private static void route(ClientLevel level, List<Target> cands, int gen, int pinsNow,
                              int sx, int sy, int sz, int box) {
        try {
            if (pinsNow != failedFor) {
                FAILED.clear();
                failedFor = pinsNow;
            }
            WorldRouteData.map();                   // the map and the roads: in memory, from disk, or fetched (throttled)
            GuidePath.Walkable w = GroundProbe.walkable(level);
            // one target beyond the loaded area: the way there over the coarse map first, and the
            // block-level route aims at the farthest waypoint of its corridor that is loaded
            Target far = cands.size() == 1 ? cands.get(0) : null;
            Coarse world = far != null && Math.max(Math.abs(far.x() - sx), Math.abs(far.z() - sz)) > box
                    ? world(far, sx, sy, sz) : null;
            int hand = world == null ? -1
                    : WorldRoute.handoff(world.waypoints(), sx, sz, box - MARGIN, (x, z) -> level.hasChunk(x >> 4, z >> 4));
            List<int[]> goals = new ArrayList<>();
            if (hand >= 0) {
                double[] wp = world.waypoints().get(hand);
                int wx = (int) Math.floor(wp[0]);
                int wz = (int) Math.floor(wp[2]);
                goals.add(new int[] {wx, GuidePath.feet(w, wx, wz, (int) wp[1]), wz});
            } else {
                for (Target t : cands) {
                    int tx = (int) Math.floor(t.x());
                    int tz = (int) Math.floor(t.z());
                    goals.add(new int[] {tx, GuidePath.feet(w, tx, tz, (int) Math.floor(t.y())), tz});
                }
            }
            if (world != null) {
                lastWorld = new World(true, hand >= 0 ? null : "no corridor waypoint in the loaded area",
                        world.plan().cells().size(), world.plan().millis(), hand >= 0 ? world.waypoints().get(hand) : null);
            }
            long t0 = System.nanoTime();
            GuidePath.Choice c = GuidePath.findFirst(w, sx, sy, sz, goals, box, EACH, BUDGET, SEARCH_MILLIS, i -> {
                Failed f = FAILED.get(spot(goals.get(i)));
                boolean near = f != null && sq(f.x() - sx) + sq(f.y() - sy) + sq(f.z() - sz) <= RETRY * RETRY;
                return near ? f.route() : null;
            });
            long millis = (System.nanoTime() - t0) / 1_000_000;
            lastChoice = c;
            lastCandidates = cands;
            if (c == null) {
                lastSearch = new Search(false, 0, 0, null, box, BUDGET, millis, false, 0, List.of(), null, null);
                return;                                 // the straight line stays
            }
            for (GuidePath.Try tr : c.tried()) {
                if (!tr.cached() && !tr.route().reached()) {
                    FAILED.put(spot(goals.get(tr.index())), new Failed(sx, sy, sz, tr.route()));
                }
            }
            GuidePath.Route r = c.route();
            int expanded = c.tried().stream().filter(tr -> !tr.cached()).mapToInt(tr -> tr.route().expanded()).sum();
            lastSearch = new Search(r.reached(), r.nodes().size(), expanded, r.nodes().get(r.nodes().size() - 1),
                    box, BUDGET, millis, r.enclosed(), r.columns(), r.stuck(), r.exit(), r.exitKind());
            if (r.nodes().size() < 2 || gen != generation) {
                return;
            }
            Target t = cands.get(c.chosen());
            List<double[]> pts = new ArrayList<>(GuidePath.points(w, GuidePath.smooth(w, r.nodes(), 24), LIFT));
            double[] end = pts.get(pts.size() - 1);
            double walked = Math.hypot(end[0] - sx, end[2] - sz);
            int tail = pts.size();
            double[] arrow = null;
            if (world != null && walked < CAP) {
                // on along the corridor from where the route ends, faint, on the map's heights
                WorldRoute g = world.map();
                List<int[]> cells = world.plan().cells();
                List<double[]> rest = g.line(cells.subList(g.nearestCell(cells, end[0], end[2]), cells.size()), LIFT);
                rest.add(new double[] {t.x(), rest.get(rest.size() - 1)[1], t.z()});
                pts.addAll(upTo(rest, end, CAP - walked));
            } else if (r.enclosed()) {
                // closed in: no line into the rock; the route ends at an exit (where the ground goes on
                // out of sight) or, sealed, at the spot nearest the target, with an arrow toward it
                arrow = new double[] {t.x(), t.y() + 1, t.z()};
            } else if (!r.reached() && walked < CAP) {
                List<double[]> rest = sample(end[0], end[1] - LIFT, end[2], t.x(), t.z(), STEP, CAP - walked, GroundProbe.heights(level));
                pts.addAll(rest.subList(1, rest.size()));
            }
            if (gen == generation) {
                target = t;
                ground = new Path(List.copyOf(pts), end, tail, arrow);
                routed = t;
            }
        } catch (Throwable e) {
            // a failed search leaves the straight line; the next one tries again
            lastError = e.toString();
        }
    }

    /**
     * On the route thread: the world plan from where the player stands to {@code t} over the
     * coarse map, kept while the player stays within {@link #REPLAN} of where it was made; null
     * when there is none (no map, a way not found, the player or the target under the surface),
     * which {@link #lastWorld} then says.
     */
    private static Coarse world(Target t, int sx, int sy, int sz) {
        Coarse k = coarse;
        if (k == null || !k.target().equals(t) || Math.hypot(sx - k.sx(), sz - k.sz()) > REPLAN) {
            // the map: in memory, on disk, or fetched now (on this thread, the route thread)
            WorldRoute g = WorldRouteData.map();
            WorldRoute.Plan plan = null;
            List<double[]> waypoints = List.of();
            String why = null;
            if (g == null) {
                why = "no map: " + WorldRouteData.status().state().name().toLowerCase(java.util.Locale.ROOT);
            } else {
                int px = g.cellOf(sx);
                int pz = g.cellOf(sz);
                int tx = g.cellOf(t.x());
                int tz = g.cellOf(t.z());
                {
                    // the player or the target under the map's surface (a canyon town, a valley
                    // floor under an overhang, a cave mouth) still gets a way over the map: the
                    // block route does the first and last parts, and a cell with no way out of it
                    // just gives no plan
                    plan = g.plan(px, pz, tx, tz, WORLD_BUDGET, WORLD_MILLIS);
                    if (plan == null) {
                        why = "no cell to start from or to aim at";
                    } else if (!plan.reached()) {
                        why = "no way found (" + plan.expanded() + " cells, " + plan.millis() + " ms)";
                        plan = null;
                    } else {
                        waypoints = g.corridor(plan.cells(), WAYPOINT);
                    }
                }
            }
            k = new Coarse(sx, sz, t, g, plan, waypoints, why);
            coarse = k;
        }
        if (k.plan() == null) {
            lastWorld = new World(false, k.why(), 0, 0, null);
            return null;
        }
        return k;
    }

    /** The points of {@code pts} up to {@code max} blocks (flat, along them) from {@code from}. */
    static List<double[]> upTo(List<double[]> pts, double[] from, double max) {
        List<double[]> out = new ArrayList<>();
        double along = 0;
        double[] prev = from;
        for (double[] p : pts) {
            along += Math.hypot(p[0] - prev[0], p[2] - prev[2]);
            if (along > max) {
                break;
            }
            out.add(p);
            prev = p;
        }
        return out;
    }

    private static long spot(int[] g) {
        return ((long) (g[0] & 0x3FFFFFF) << 38) | ((long) (g[2] & 0x3FFFFFF) << 12) | (g[1] & 0xFFF);
    }

    /**
     * {@code /guide?dump=1}, on the game thread: the block codes in a box {@code flat} blocks each
     * way and {@code up} blocks up and down around the player, with the player, the target and the
     * route settings ({@link GuideDump}), and per code the blocks it stands for here, the most common
     * first; null without a player.
     */
    public static JsonObject dump(int flat, int up) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) {
            return null;
        }
        int px = (int) Math.floor(p.getX());
        int py = (int) Math.floor(p.getY() + 0.01);
        int pz = (int) Math.floor(p.getZ());
        int sx = 2 * flat + 1;
        int sy = 2 * up + 1;
        GuideTerrain.Blocks b = GroundProbe.blocks(level);
        char[][] cols = new char[sx * sx][];
        // which blocks each code stands for here, so an odd one (a shaft of "non-solid") can be named
        Map<Character, Map<String, Integer>> names = new java.util.TreeMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k < sx; k++) {
            for (int i = 0; i < sx; i++) {
                char[] col = new char[sy];
                for (int j = 0; j < sy; j++) {
                    int x = px - flat + i;
                    int y = py - up + j;
                    int z = pz - flat + k;
                    char code = b.at(x, y, z);
                    col[j] = code;
                    if (code != GuideTerrain.UNKNOWN && code != GuideTerrain.AIR) {
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).getBlock()).toString();
                        names.computeIfAbsent(code, c -> new HashMap<>()).merge(id, 1, Integer::sum);
                    }
                }
                cols[k * sx + i] = col;
            }
        }
        Target t = target;
        JsonObject tj = null;
        if (t != null) {
            tj = new JsonObject();
            tj.addProperty("x", t.x());
            tj.addProperty("y", t.y());
            tj.addProperty("z", t.z());
            tj.addProperty("label", t.label());
            tj.addProperty("ours", t.ours());
        }
        JsonObject set = new JsonObject();
        set.addProperty("guideLine", Settings.on("guideLine"));
        set.addProperty("mode", GroundProbe.airborne(p) ? "air" : "ground");
        set.addProperty("box", Math.max(32, Math.min(256, mc.options.getEffectiveRenderDistance() * 16)));
        set.addProperty("budget", BUDGET);
        set.addProperty("each", EACH);
        set.addProperty("millis", SEARCH_MILLIS);
        set.addProperty("candidates", CANDIDATES);
        set.addProperty("drop", GuidePath.DROP);
        JsonObject out = new GuideDump(px - flat, py - up, pz - flat, sx, sy, sx, cols,
                new double[] {p.getX(), p.getY(), p.getZ()}, tj, set).toJson();
        JsonObject named = new JsonObject();
        names.forEach((code, ids) -> {
            JsonObject j = new JsonObject();
            ids.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(16)
                    .forEach(e -> j.addProperty(e.getKey(), e.getValue()));
            named.add(String.valueOf(code), j);
        });
        out.add("names", named);
        return out;
    }

    /** Every frame: the path, in one batch. */
    public static void renderWorld(PoseStack pose, MultiBufferSource buffers) {
        Target t = target;
        if (t == null || failed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        try {
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 me = p.getPosition(pt);
            Vec3 c = mc.gameRenderer.getMainCamera().position();
            double seconds = (mc.level.getGameTime() % 100_000 + pt) / 20.0;
            float glow = GuideTrack.pulse(seconds);
            VertexConsumer quads = buffers.getBuffer(RenderTypes.debugQuads());
            if (airLine && GroundProbe.airborne(p) && !(routed != null && ground.points().size() >= 2)) {
                // only until a ground route is found: then the one line, on the ground, leads
                float dim = 1f;
                double dx = t.x() - me.x;
                double dy = t.y() + 1 - me.y;
                double dz = t.z() - me.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double k = dist > CAP ? CAP / dist : 1;
                double len = dist * k;
                GuideTrack.Track air = new GuideTrack.Track(new double[] {0, len}, new double[] {me.x, me.x + dx * k},
                        new double[] {me.y, me.y + dy * k}, new double[] {me.z, me.z + dz * k}, dist);
                GuideTrack.chevrons(pose, quads, c, air, seconds, false, glow * dim, 0);
                if (k == 1) {
                    Beams.draw(pose, quads, (float) (t.x() - c.x), (float) (t.y() + 1 - c.y), (float) (t.z() - c.z),
                            GuideTrack.PILLAR, GuideTrack.BODY, dim);
                }
            }
            GuideTrack.Track tr = track;
            if (tr != null) {
                GuideTrack.ribbon(pose, quads, c, tr);
                GuideTrack.chevrons(pose, quads, c, tr, seconds, true, glow, GuideTrack.DASH_LIFT);
            }
            Path g = ground;
            List<double[]> pts = g.points();
            if (pts.size() >= 2) {
                int tail = Math.max(1, Math.min(pts.size(), g.tail()));
                if (tail < pts.size()) {
                    // past the last reachable spot: a still, faint straight guess, not a path to follow
                    GuideTrack.plain(pose, quads, c, pts.subList(tail - 1, pts.size()), GuideTrack.TAIL_ALPHA);
                }
                if (g.end() != null) {
                    double[] e = g.end();
                    GuideTrack.disc(pose, quads, c, e, glow);
                    Beams.draw(pose, quads, (float) (e[0] - c.x), (float) (e[1] - c.y), (float) (e[2] - c.z),
                            GuideTrack.PILLAR, GuideTrack.BODY, 1f);
                    if (g.arrow() != null) {
                        GuideTrack.arrow(pose, quads, c, e, g.arrow());
                    }
                }
            }
        } catch (Throwable e) {
            failed = true;                          // once broken, stay out of the frame loop
            clear();
            lastError = e.toString();
            System.out.println("[WynnLabs] guide line failed, off until restart: " + e);
        } finally {
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
