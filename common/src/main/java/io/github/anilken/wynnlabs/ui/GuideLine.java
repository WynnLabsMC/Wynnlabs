package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.Pin;
import io.github.anilken.wynnlabs.quests.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
    /** Dashes (chevrons) this long and wide with gaps this long, in blocks; they flow this many blocks a second. */
    static final double DASH = 1.3;
    static final float DASH_WIDTH = 0.6f;
    static final double GAP = 0.5;
    static final double FLOW = 2.0;
    /** At most this many dashes, the nearest the player. */
    static final int DASHES = 64;
    /** The walkable path is walked in steps this long, laid on the block tops under it, ... */
    static final double SAMPLE = 0.25;
    /** ... with a step's edge eased into a slope this steep (a block up a block along: a stair's run). */
    static final double EASE = 1.0;
    /** The base ribbon lies this far above the ground and the chevrons this far, riding on it. */
    static final float RIBBON_LIFT = 0.04f;
    static final float DASH_LIFT = 0.07f;
    /** The base ribbon: its width and opacity. */
    private static final float RIBBON_WIDTH = 0.5f;
    private static final float RIBBON_ALPHA = 0.28f;
    /** The brightness pulse: this much up and down, over this many seconds. */
    static final double PULSE = 0.2;
    static final double PULSE_SECONDS = 1.2;
    /** The glowing disc under the pillar: its radius in blocks. */
    private static final double DISC = 0.8;
    /** The first blocks from the player fade in over this length. */
    static final double FADE = 1.5;
    /** Height of the pillar at the end of the path. */
    private static final float PILLAR = 6f;
    /** Light cyan for the glow, pillar and disc; the chevrons go from {@link #TAIL} to white at the tip. */
    private static final int BODY = 0x8FDFFF;
    private static final int TAIL = 0x7FD8FF;
    private static final int TIP = 0xFFFFFF;
    private static final int CORE = 0xEAFBFF;
    /** The base ribbon under the chevrons. */
    private static final int RIBBON = 0x5FC8FF;
    /** On Wynntils' minimap and map: a dot at most every this many blocks along the route, and at most this many dots. */
    static final double MAP_STEP = 4;
    static final int MAP_MAX = 400;
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
    /** A block at most this tall (a carpet, a flower pot) is stepped over at the feet. */
    private static final double THIN = 0.375;
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
    /** The arrow toward a target that cannot be walked to: its length, and its height over the last spot. */
    private static final double ARROW = 3;
    private static final double ARROW_UP = 1.2;
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
    /** The straight guess past the route is drawn at this share of the opacity, still. */
    private static final float TAIL_ALPHA = 1 / 3f;

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
    private static volatile Track track;
    /** Riding or gliding: the straight line through the air (faint while a ground route is shown). */
    private static volatile boolean airLine;
    /** The route's colour on Wynntils' minimap and map: a road-sign yellow, which reads on grass, sand and stone. */
    static final int MAP_RGB = 0xFFD23F;
    /** Riding or gliding with no ground under the player: why ({@code /guide}); null otherwise. */
    private static volatile String groundWhy;
    /** Riding or gliding: where the ground route starts, under the player (null: no ground there, or on foot). */
    private static volatile int[] groundStart;
    /** The target the shown ground path was routed for (null: only the straight line so far). */
    private static volatile Target routed;
    /** Past the ground path, for Wynntils' map only: the rest of the way to the target (the world corridor, or straight), faint. */
    private static volatile List<double[]> mapBeyond = List.of();
    /** The ground path last handed to Wynntils' minimap and map (tick thread only; null: none yet). */
    private static Path mapShown;
    /** One thread for route searches; a new request replaces one still waiting. */
    private static final ThreadPoolExecutor ROUTER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), r -> {
                Thread t = new Thread(r, "WynnLabs guide route");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());

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

    /** Opacity at {@code d} blocks along the line from the player: none at the feet, full from {@link #FADE} on. */
    static float nearFade(double d) {
        return (float) Math.max(0, Math.min(1, d / FADE));
    }

    /**
     * Where a dash covers the spot {@code left} blocks before the line's end at {@code seconds}:
     * -1 in a gap, else how far along the dash from its back (0) to its front (1). The pattern is
     * fixed to the end, so a new route (which mostly changes the player end) does not make the
     * dashes jump, and it moves toward the end by {@link #FLOW} blocks a second.
     */
    static double dashAt(double left, double seconds) {
        double period = DASH + GAP;
        double u = ((left + seconds * FLOW) % period + period) % period;
        return u < DASH ? 1 - u / DASH : -1;
    }

    /**
     * Index of the segment of {@code pts} closest to (x, z), seen from above, and how far along it
     * (0..1): where the path is cut so it starts at the player's feet.
     */
    static double[] nearest(List<double[]> pts, double x, double z) {
        double best = Double.MAX_VALUE;
        double[] out = {0, 0};
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            double dx = b[0] - a[0];
            double dz = b[2] - a[2];
            double l2 = dx * dx + dz * dz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[2]) * dz) / l2));
            double d = sq(a[0] + dx * t - x) + sq(a[2] + dz * t - z);
            if (d < best) {
                best = d;
                out[0] = i - 1;
                out[1] = t;
            }
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
                buildTrack(mc);
            } catch (Throwable e) {
                track = null;
                lastError = e.toString();
            }
        }
        if (tick % EVERY != 0) {
            return;
        }
        try {
            syncMap(mc);
        } catch (Throwable e) {
            lastError = e.toString();
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
            boolean air = airborne(p);
            int[] start = null;
            if (t == null) {
                // nothing to lead to
            } else if (!air) {
                start = new int[] {px, py, pz};
            } else {
                // the ground under the mount, however far down: where to land and walk on
                int top = heights(level).height(px, pz);
                start = GuidePath.surface(walkable(level), px, pz, py, top, level.getMinY());
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
                List<double[]> line = sample(sx + 0.5, sy, sz + 0.5, t.x(), t.z(), STEP, CAP, heights(level));
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
        boolean air = t != null && p != null && airLine && airborne(p);
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

    /**
     * Hands the ground path shown to Wynntils' minimap and map when it is a new one (none when the
     * guide line or its map setting is off, or out of the world); the straight guess past the
     * route is faint. Every {@link #EVERY} ticks, and only an identity check when nothing changed.
     */
    private static void syncMap(Minecraft mc) {
        boolean on = !failed && mc.player != null && mc.level != null
                && Settings.on("guideLine") && Settings.on("guideMinimap");
        Path g = on ? ground : NO_PATH;
        Player p = mc.player;
        Target t = target;
        if (on && g.points().isEmpty() && t != null && airLine && airborne(p)) {
            // riding or gliding with no ground route yet: the straight line, all faint, so the map
            // still shows the way; re-sent as the player moves
            mapShown = null;
            List<double[]> line = new ArrayList<>();
            double dx = t.x() - p.getX();
            double dz = t.z() - p.getZ();
            double len = Math.hypot(dx, dz);
            int n = Math.max(1, (int) Math.ceil(len / MAP_STEP));
            for (int i = 0; i <= n; i++) {
                double k = (double) i / n;
                line.add(new double[] {p.getX() + dx * k, p.getY(), p.getZ() + dz * k});
            }
            int[] keepAir = mapPoints(line, MAP_STEP, MAP_MAX);
            List<int[]> air = new ArrayList<>(keepAir.length);
            for (int i : keepAir) {
                double[] q = line.get(i);
                air.add(new int[] {(int) Math.floor(q[0]), (int) Math.floor(q[1]), (int) Math.floor(q[2])});
            }
            Wyn.showGuideRoute(air, 0, MAP_RGB);
            return;
        }
        if (g == mapShown) {
            return;
        }
        mapShown = g;
        List<double[]> pts = g.points();
        int[] keep = mapPoints(pts, MAP_STEP, MAP_MAX);
        List<int[]> out = new ArrayList<>(keep.length);
        int faint = keep.length;
        for (int i = 0; i < keep.length; i++) {
            double[] q = pts.get(keep[i]);
            if (keep[i] >= g.tail() && faint == keep.length) {
                faint = i;
            }
            out.add(new int[] {(int) Math.floor(q[0]), (int) Math.floor(q[1]), (int) Math.floor(q[2])});
        }
        List<double[]> beyond = g.points().isEmpty() ? List.of() : mapBeyond;
        if (beyond.size() >= 2) {
            // the rest of the way past the route, faint, so the map shows the whole way
            for (int i : mapPoints(beyond, MAP_STEP, MAP_MAX)) {
                double[] q = beyond.get(i);
                out.add(new int[] {(int) Math.floor(q[0]), (int) Math.floor(q[1]), (int) Math.floor(q[2])});
            }
        }
        Wyn.showGuideRoute(out, faint, MAP_RGB);
    }

    /**
     * The indices of {@code pts} kept for Wynntils' map: the first, then each next one at least
     * {@code step} blocks (flat, along the way) on from the last kept, and the last; the step grows
     * with a long way so that at most {@code max} (at least 3) are kept.
     */
    static int[] mapPoints(List<double[]> pts, double step, int max) {
        int n = pts.size();
        if (n == 0) {
            return new int[0];
        }
        double length = 0;
        for (int i = 1; i < n; i++) {
            length += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[2] - pts.get(i - 1)[2]);
        }
        double s = Math.max(step, length / (Math.max(3, max) - 2));
        int[] out = new int[n];
        int k = 0;
        out[k++] = 0;
        double since = 0;
        for (int i = 1; i < n; i++) {
            since += Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[2] - pts.get(i - 1)[2]);
            if (since >= s - 1e-9) {
                out[k++] = i;
                since = 0;
            }
        }
        if (out[k - 1] != n - 1) {
            out[k++] = n - 1;
        }
        return java.util.Arrays.copyOf(out, k);
    }

    private static void clear() {
        target = null;
        groundStart = null;
        track = null;
        candidates = List.of();
        ground = NO_PATH;
        mapBeyond = List.of();
        routed = null;
    }

    /** The highest block of a column, {@link #UNKNOWN} where its chunk is not loaded. */
    private static Ground heights(ClientLevel level) {
        return (x, z) -> level.hasChunk(x >> 4, z >> 4)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : UNKNOWN;
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
            GuidePath.Walkable w = walkable(level);
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
            List<double[]> beyond = List.of();
            if (world != null) {
                // on along the corridor from where the route ends, faint, on the map's heights
                WorldRoute g = world.map();
                List<int[]> cells = world.plan().cells();
                List<double[]> rest = g.line(cells.subList(g.nearestCell(cells, end[0], end[2]), cells.size()), LIFT);
                rest.add(new double[] {t.x(), rest.get(rest.size() - 1)[1], t.z()});
                beyond = rest;                                      // the whole way, for the map
                if (walked < CAP) {
                    pts.addAll(upTo(rest, end, CAP - walked));
                }
            } else if (r.enclosed()) {
                // closed in: no line into the rock; the route ends at an exit (where the ground goes on
                // out of sight) or, sealed, at the spot nearest the target, with an arrow toward it
                arrow = new double[] {t.x(), t.y() + 1, t.z()};
            } else if (!r.reached() && walked < CAP) {
                List<double[]> rest = sample(end[0], end[1] - LIFT, end[2], t.x(), t.z(), STEP, CAP - walked, heights(level));
                pts.addAll(rest.subList(1, rest.size()));
            }
            if (!r.reached() && beyond.isEmpty()) {
                // no corridor: the map's line still goes on to the target, straight and faint
                beyond = List.of(end, new double[] {t.x(), end[1], t.z()});
            }
            if (gen == generation) {
                target = t;
                mapBeyond = beyond;
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
                int ph = g.height(px, pz);
                if (ph != WorldRoute.UNKNOWN && sy < ph - UNDER) {
                    why = "the player is under the map's surface";
                } else {
                    // a target under the map's surface (a valley floor under an overhang, a cave
                    // mouth) is still aimed at over the map: the block route does the last part
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

    /** Columns the player can step to, read from loaded chunks only (an unloaded one has none). */
    private static GuidePath.Walkable walkable(ClientLevel level) {
        return new GuideTerrain(blocks(level));
    }

    /** Every block's code ({@link GuideTerrain}), {@link GuideTerrain#UNKNOWN} where its chunk is not loaded. */
    private static GuideTerrain.Blocks blocks(ClientLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return new GuideTerrain.Blocks() {
            private int cx = Integer.MIN_VALUE;
            private int cz;
            private ChunkAccess chunk;

            @Override
            public char at(int x, int y, int z) {
                if (x >> 4 != cx || z >> 4 != cz) {
                    cx = x >> 4;
                    cz = z >> 4;
                    chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                }
                if (chunk == null) {
                    return GuideTerrain.UNKNOWN;
                }
                pos.set(x, y, z);
                return code(chunk, pos, chunk.getBlockState(pos));
            }
        };
    }

    /**
     * A block's code for the route: a barrier, lava, climbable, door, slab or stair first; then whatever the
     * player walks through (no collision, something thin like a carpet or snow layer, a chain, a
     * cobweb) is water or a non-solid; the rest is a fence or wall (taller than a block), a road
     * block or plain solid.
     */
    static char code(BlockGetter level, BlockPos pos, BlockState s) {
        if (s.isAir()) {
            return GuideTerrain.AIR;
        }
        if (s.is(Blocks.BARRIER)) {
            return GuideTerrain.BARRIER;
        }
        FluidState fluid = s.getFluidState();
        if (fluid.is(FluidTags.LAVA)) {
            return GuideTerrain.LAVA;
        }
        if (s.is(BlockTags.CLIMBABLE)) {
            return GuideTerrain.CLIMB;
        }
        if (door(s)) {
            return GuideTerrain.DOOR;
        }
        if (s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS)) {
            return GuideTerrain.HALF;
        }
        VoxelShape shape = s.getCollisionShape(level, pos);
        if (!s.blocksMotion() || shape.isEmpty() || shape.max(Direction.Axis.Y) <= THIN || s.is(BlockTags.CHAINS)) {
            return fluid.is(FluidTags.WATER) ? GuideTerrain.WATER : GuideTerrain.THIN;
        }
        if (shape.max(Direction.Axis.Y) > 1) {
            return GuideTerrain.TALL;
        }
        return road(s) ? GuideTerrain.ROAD : GuideTerrain.SOLID;
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
        GuideTerrain.Blocks b = blocks(level);
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
        set.addProperty("mode", airborne(p) ? "air" : "ground");
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

    /** A door, fence gate or trapdoor: the player opens it. */
    private static boolean door(BlockState s) {
        return s.is(BlockTags.DOORS) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.TRAPDOORS);
    }

    /** A road or stair block, which the route prefers. */
    private static boolean road(BlockState s) {
        return s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE)
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS)
                || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.SMOOTH_STONE)
                || s.is(BlockTags.PLANKS) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS);
    }

    /** On a mount or gliding: the straight line through the air. */
    private static boolean airborne(Player p) {
        return p.isPassenger() || p.isFallFlying();
    }

    /**
     * A stretch of path walked by arc length: {@code s[i]} blocks from its start lies the point
     * ({@code x[i]}, {@code y[i]}, {@code z[i]}). {@code toEnd} is how far its start is from the
     * end of the walkable route, which the dashes are fixed to, so a new route (which mostly
     * changes the player end) does not make them jump.
     */
    record Track(double[] s, double[] x, double[] y, double[] z, double toEnd) {
        double length() {
            return s.length == 0 ? 0 : s[s.length - 1];
        }
    }

    /**
     * {@code pts} walked from its first point, a point every {@code step} blocks of arc length up
     * to {@code max} blocks (the last point where the path ends sooner); {@code toEnd} is the
     * whole path's length.
     */
    static Track resample(List<double[]> pts, double step, double max) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += dist(pts.get(i - 1), pts.get(i));
        }
        double len = Math.min(total, max);
        int n = Math.max(1, (int) Math.ceil(len / step)) + 1;
        double[] s = new double[n];
        double[] x = new double[n];
        double[] y = new double[n];
        double[] z = new double[n];
        int seg = 1;
        double segStart = 0;
        for (int i = 0; i < n; i++) {
            double at = Math.min(len, i * step);
            while (seg < pts.size() - 1 && segStart + dist(pts.get(seg - 1), pts.get(seg)) < at) {
                segStart += dist(pts.get(seg - 1), pts.get(seg));
                seg++;
            }
            double[] a = pts.get(Math.min(seg - 1, pts.size() - 1));
            double[] b = pts.get(Math.min(seg, pts.size() - 1));
            double l = dist(a, b);
            double t = l < 1e-9 ? 0 : Math.max(0, Math.min(1, (at - segStart) / l));
            s[i] = at;
            x[i] = a[0] + (b[0] - a[0]) * t;
            y[i] = a[1] + (b[1] - a[1]) * t;
            z[i] = a[2] + (b[2] - a[2]) * t;
        }
        return new Track(s, x, y, z, total);
    }

    /** The point {x, y, z} {@code at} blocks along the track (clamped to its ends). */
    static double[] at(Track t, double at) {
        double[] s = t.s();
        int n = s.length;
        if (n == 1 || at <= 0) {
            return new double[] {t.x()[0], t.y()[0], t.z()[0]};
        }
        if (at >= s[n - 1]) {
            return new double[] {t.x()[n - 1], t.y()[n - 1], t.z()[n - 1]};
        }
        int i = java.util.Arrays.binarySearch(s, at);
        if (i >= 0) {
            return new double[] {t.x()[i], t.y()[i], t.z()[i]};
        }
        int b = -i - 1;
        int a = b - 1;
        double k = (at - s[a]) / (s[b] - s[a]);
        return new double[] {t.x()[a] + (t.x()[b] - t.x()[a]) * k, t.y()[a] + (t.y()[b] - t.y()[a]) * k,
                t.z()[a] + (t.z()[b] - t.z()[a]) * k};
    }

    /**
     * Where the dashes' centres lie along a track of {@code length} blocks whose start is
     * {@code toEnd} from the route's end, at {@code seconds}: one every {@link #DASH} + {@link #GAP}
     * blocks counted back from the end, moving toward it by {@link #FLOW} blocks a second, at most
     * {@link #DASHES}, nearest the player first; only those that fit whole on the track.
     */
    static double[] centres(double toEnd, double length, double seconds) {
        double period = DASH + GAP;
        double shift = ((seconds * FLOW) % period + period) % period;
        // the first centre past the dash's half length from the start
        double first = toEnd - period * Math.floor((toEnd + shift - DASH / 2) / period) + shift;
        double[] out = new double[DASHES];
        int n = 0;
        for (double c = first; c + DASH / 2 <= length + 1e-9 && n < DASHES; c += period) {
            if (c - DASH / 2 >= -1e-9) {
                out[n++] = c;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** The gentle brightness pulse: 1 ± {@link #PULSE} over {@link #PULSE_SECONDS}. */
    static float pulse(double seconds) {
        return (float) (1 + PULSE * Math.sin(2 * Math.PI * seconds / PULSE_SECONDS));
    }

    /**
     * Heights {@code y} of points {@code step} blocks apart, with every rise and drop eased into a
     * slope of {@link #EASE}: each point is at least as high as any point near it less the slope
     * times the distance. A staircase becomes the line over its step edges, so a chevron lies on
     * the stair's run instead of standing up at a step; nothing goes below the real ground.
     */
    static double[] ease(double[] y, double step) {
        int reach = (int) Math.ceil(1.5 / step);
        double[] out = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            double h = y[i];
            for (int j = Math.max(0, i - reach); j <= Math.min(y.length - 1, i + reach); j++) {
                h = Math.max(h, y[j] - EASE * step * Math.abs(i - j));
            }
            out[i] = h;
        }
        return out;
    }

    /**
     * Every 2 ticks: the walkable part of the ground path from the player's feet, a point every
     * {@link #SAMPLE} blocks, each on the top of the block it is over (a slab, a stair step), the
     * step edges eased ({@link #ease}).
     */
    private static void buildTrack(Minecraft mc) {
        Path g = ground;
        Player p = mc.player;
        List<double[]> pts = g.points();
        if (p == null || mc.level == null || pts.size() < 2) {
            track = null;
            return;
        }
        int tail = Math.max(1, Math.min(pts.size(), g.tail()));
        if (tail < 2) {
            track = null;
            return;
        }
        Track raw = resample(fromFeet(pts.subList(0, tail), p.getX(), p.getZ()), SAMPLE, CAP);
        double[] y = raw.y().clone();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < y.length; i++) {
            y[i] = surface(mc.level, pos, raw.x()[i], raw.z()[i], y[i] - LIFT);
        }
        track = new Track(raw.s(), raw.x(), ease(y, SAMPLE), raw.z(), raw.toEnd());
    }

    /**
     * The walking surface at (x, z) near height {@code near}: the top of the highest block there
     * that is not more than a little above it (a slab or stair step counts at its own height).
     */
    private static double surface(ClientLevel level, BlockPos.MutableBlockPos pos, double x, double z, double near) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(near);
        for (int y = by + 1; y >= by - 2; y--) {
            BlockState s = level.getBlockState(pos.set(bx, y, bz));
            VoxelShape shape = s.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                if (top <= near + 0.75) {
                    return top;
                }
            }
        }
        return near;
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
            float glow = pulse(seconds);
            VertexConsumer quads = buffers.getBuffer(RenderTypes.debugQuads());
            if (airLine && airborne(p) && !(routed != null && ground.points().size() >= 2)) {
                // only until a ground route is found: then the one line, on the ground, leads
                float dim = 1f;
                double dx = t.x() - me.x;
                double dy = t.y() + 1 - me.y;
                double dz = t.z() - me.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double k = dist > CAP ? CAP / dist : 1;
                double len = dist * k;
                Track air = new Track(new double[] {0, len}, new double[] {me.x, me.x + dx * k},
                        new double[] {me.y, me.y + dy * k}, new double[] {me.z, me.z + dz * k}, dist);
                chevrons(pose, quads, c, air, seconds, false, glow * dim, 0);
                if (k == 1) {
                    Beams.draw(pose, quads, (float) (t.x() - c.x), (float) (t.y() + 1 - c.y), (float) (t.z() - c.z),
                            PILLAR, BODY, dim);
                }
            }
            Track tr = track;
            if (tr != null) {
                ribbon(pose, quads, c, tr);
                chevrons(pose, quads, c, tr, seconds, true, glow, DASH_LIFT);
            }
            Path g = ground;
            List<double[]> pts = g.points();
            if (pts.size() >= 2) {
                int tail = Math.max(1, Math.min(pts.size(), g.tail()));
                if (tail < pts.size()) {
                    // past the last reachable spot: a still, faint straight guess, not a path to follow
                    plain(pose, quads, c, pts.subList(tail - 1, pts.size()), TAIL_ALPHA);
                }
                if (g.end() != null) {
                    double[] e = g.end();
                    disc(pose, quads, c, e, glow);
                    Beams.draw(pose, quads, (float) (e[0] - c.x), (float) (e[1] - c.y), (float) (e[2] - c.z),
                            PILLAR, BODY, 1f);
                    if (g.arrow() != null) {
                        arrow(pose, quads, c, e, g.arrow());
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

    /** The path cut where it passes the player, so it starts at their feet (it is up to a second old). */
    private static List<double[]> fromFeet(List<double[]> pts, double x, double z) {
        double[] at = nearest(pts, x, z);
        int i = (int) at[0];
        double[] a = pts.get(i);
        double[] b = pts.get(i + 1);
        double t = at[1];
        List<double[]> out = new ArrayList<>(pts.size() - i);
        out.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
        out.addAll(pts.subList(i + 1, pts.size()));
        return out;
    }

    /**
     * The base ribbon along the whole track, under the chevrons: one strip through every point,
     * each point's edge vertices shared with the next quad so the strip runs on unbroken round the
     * turns, every point at its own ground height.
     */
    private static void ribbon(PoseStack pose, VertexConsumer v, Vec3 c, Track t) {
        double[] s = t.s();
        int n = s.length;
        if (n < 2) {
            return;
        }
        int r = RIBBON >> 16 & 0xFF;
        int g = RIBBON >> 8 & 0xFF;
        int b = RIBBON & 0xFF;
        var e = pose.last();
        float[] prev = null;
        float prevX = 0, prevY = 0, prevZ = 0;
        int prevA = 0;
        for (int i = 0; i < n; i++) {
            double[] pt = {t.x()[i], t.y()[i], t.z()[i]};
            float[] o = side(t, s[i], pt, c, true, RIBBON_WIDTH / 2);
            float x = (float) (pt[0] - c.x), y = (float) (pt[1] - c.y) + RIBBON_LIFT, z = (float) (pt[2] - c.z);
            int a = Math.round(255 * RIBBON_ALPHA * nearFade(s[i]));
            if (prev != null && (a > 0 || prevA > 0)) {
                v.addVertex(e, prevX - prev[0], prevY - prev[1], prevZ - prev[2]).setColor(r, g, b, prevA);
                v.addVertex(e, prevX + prev[0], prevY + prev[1], prevZ + prev[2]).setColor(r, g, b, prevA);
                v.addVertex(e, x + o[0], y + o[1], z + o[2]).setColor(r, g, b, a);
                v.addVertex(e, x - o[0], y - o[1], z - o[2]).setColor(r, g, b, a);
            }
            prev = o;
            prevX = x;
            prevY = y;
            prevZ = z;
            prevA = a;
        }
    }

    /**
     * The flowing chevrons along a track: each a narrow tail widening to its broadest point and
     * closing to a tip toward the target, its three stations placed by arc length and at the
     * track's (eased) height there, so a dash lies flat on the ground, tilts with a stair's run
     * and bends with the path at a turn. Three layers: a wide soft glow, the body (light cyan at
     * the tail to white at the tip) and a bright thin core. Flat on the ground with {@code flat},
     * else turned to face the camera; {@code lift} above the track.
     */
    private static void chevrons(PoseStack pose, VertexConsumer v, Vec3 c, Track t, double seconds,
                                 boolean flat, float glow, float lift) {
        double[] centres = centres(t.toEnd(), t.length(), seconds);
        for (int layer = 0; layer < 3; layer++) {
            float width = layer == 0 ? 1.1f : layer == 1 ? DASH_WIDTH : 0.12f;
            float alpha = (layer == 0 ? 0.3f : layer == 1 ? 0.7f : 1f) * glow;
            int back = layer == 0 ? TAIL : layer == 1 ? TAIL : CORE;
            int front = layer == 0 ? TAIL : TIP;
            float up = lift + layer * 0.003f;
            for (double s : centres) {
                float fade = nearFade(s - DASH / 2) * alpha;
                if (fade < 0.01f) {
                    continue;
                }
                double[] tail = at(t, s - DASH / 2);
                double[] mid = at(t, s + DASH * 0.1);
                double[] tip = at(t, s + DASH / 2);
                float[] ot = side(t, s - DASH / 2, tail, c, flat, width * 0.15f);
                float[] om = side(t, s + DASH * 0.1, mid, c, flat, width * 0.5f);
                int a = Math.round(255 * Math.min(1, fade));
                int cb = back;
                int cm = mix(back, front, 0.6f);
                int cf = front;
                var e = pose.last();
                float tx = (float) (tail[0] - c.x), ty = (float) (tail[1] - c.y) + up, tz = (float) (tail[2] - c.z);
                float mx = (float) (mid[0] - c.x), my = (float) (mid[1] - c.y) + up, mz = (float) (mid[2] - c.z);
                float px = (float) (tip[0] - c.x), py = (float) (tip[1] - c.y) + up, pz = (float) (tip[2] - c.z);
                // tail to broadest point, then broadest point to the tip (the last two vertices meet)
                vertex(v, e, tx - ot[0], ty - ot[1], tz - ot[2], cb, a);
                vertex(v, e, tx + ot[0], ty + ot[1], tz + ot[2], cb, a);
                vertex(v, e, mx + om[0], my + om[1], mz + om[2], cm, a);
                vertex(v, e, mx - om[0], my - om[1], mz - om[2], cm, a);
                vertex(v, e, mx - om[0], my - om[1], mz - om[2], cm, a);
                vertex(v, e, mx + om[0], my + om[1], mz + om[2], cm, a);
                vertex(v, e, px, py, pz, cf, a);
                vertex(v, e, px, py, pz, cf, a);
            }
        }
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose e, float x, float y, float z, int rgb, int a) {
        v.addVertex(e, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, a);
    }

    /** The colour {@code k} of the way from {@code a} to {@code b}. */
    static int mix(int a, int b, float k) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * k);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return r << 16 | g << 8 | bl;
    }

    /**
     * The half-width offset across the track at arc length {@code s} (point {@code p}): level and
     * to the left of the way with {@code flat} (with the vertex order used the face looks up),
     * else square to the line of sight.
     */
    private static float[] side(Track t, double s, double[] p, Vec3 c, boolean flat, float half) {
        double[] a = at(t, s - 0.15);
        double[] b = at(t, s + 0.15);
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double ox, oy, oz;
        if (flat && dx * dx + dz * dz > 1e-6) {
            ox = -dz;
            oy = 0;
            oz = dx;
        } else {
            // (b - a) × (camera - point)
            double cx = c.x - p[0], cy = c.y - p[1], cz = c.z - p[2];
            ox = dy * cz - dz * cy;
            oy = dz * cx - dx * cz;
            oz = dx * cy - dy * cx;
        }
        double len = Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (len < 1e-9) {
            return new float[] {0, 0, 0};
        }
        return new float[] {(float) (ox / len * half), (float) (oy / len * half), (float) (oz / len * half)};
    }

    /** A soft glowing disc on the ground at the end of the path, brightest in the middle. */
    private static void disc(PoseStack pose, VertexConsumer v, Vec3 c, double[] e, float glow) {
        float cx = (float) (e[0] - c.x);
        float cy = (float) (e[1] - LIFT + RIBBON_LIFT - c.y);
        float cz = (float) (e[2] - c.z);
        int r = BODY >> 16 & 0xFF;
        int g = BODY >> 8 & 0xFF;
        int b = BODY & 0xFF;
        int mid = Math.round(255 * Math.min(1, 0.45f * glow));
        var p = pose.last();
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n;
            double a1 = 2 * Math.PI * (i + 1) / n;
            // centre, next rim point, this rim point, centre: the face looks up
            v.addVertex(p, cx, cy, cz).setColor(r, g, b, mid);
            v.addVertex(p, cx + (float) (DISC * Math.cos(a1)), cy, cz + (float) (DISC * Math.sin(a1))).setColor(r, g, b, 0);
            v.addVertex(p, cx + (float) (DISC * Math.cos(a0)), cy, cz + (float) (DISC * Math.sin(a0))).setColor(r, g, b, 0);
            v.addVertex(p, cx, cy, cz).setColor(r, g, b, mid);
        }
    }

    /**
     * A short faint arrow from over the last reachable spot {@code e} toward the target {@code to}
     * (the target cannot be walked to from here): a shaft and two barbs, turned to the camera.
     */
    private static void arrow(PoseStack pose, VertexConsumer v, Vec3 c, double[] e, double[] to) {
        double ax = e[0], ay = e[1] + ARROW_UP, az = e[2];
        double dx = to[0] - ax, dy = to[1] - ay, dz = to[2] - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return;
        }
        double k = Math.min(ARROW, len) / len;
        double bx = ax + dx * k, by = ay + dy * k, bz = az + dz * k;
        // across the arrow, square to the line of sight at its tip
        double vx = c.x - bx, vy = c.y - by, vz = c.z - bz;
        double ox = dy * vz - dz * vy, oy = dz * vx - dx * vz, oz = dx * vy - dy * vx;
        double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
        float a = 0.55f * TAIL_ALPHA * 2;
        strip(pose, v, (float) (ax - c.x), (float) (ay - c.y), (float) (az - c.z),
                (float) (bx - c.x), (float) (by - c.y), (float) (bz - c.z), 0.06f, 0.06f, a, a, false);
        if (ol < 1e-9) {
            return;
        }
        double back = 0.7 / len;
        for (int side = -1; side <= 1; side += 2) {
            double hx = bx - dx * back + ox / ol * 0.45 * side;
            double hy = by - dy * back + oy / ol * 0.45 * side;
            double hz = bz - dz * back + oz / ol * 0.45 * side;
            strip(pose, v, (float) (hx - c.x), (float) (hy - c.y), (float) (hz - c.z),
                    (float) (bx - c.x), (float) (by - c.y), (float) (bz - c.z), 0.06f, 0.06f, a, a, false);
        }
    }

    /** The faint still dashes of the straight guess past the last reachable spot. */
    private static void plain(PoseStack pose, VertexConsumer v, Vec3 c, List<double[]> pts, float alpha) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += dist(pts.get(i - 1), pts.get(i));
        }
        double along = 0;
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            double len = dist(a, b);
            if (len < 1e-6) {
                continue;
            }
            int pieces = Math.max(1, (int) Math.ceil(len / 0.3));
            for (int s = 0; s < pieces; s++) {
                double s0 = (double) s / pieces;
                double s1 = (double) (s + 1) / pieces;
                if (dashAt(total - along - len * (s0 + s1) / 2, 0) < 0) {
                    continue;
                }
                float ax = (float) (a[0] + (b[0] - a[0]) * s0 - c.x);
                float ay = (float) (a[1] + (b[1] - a[1]) * s0 - c.y);
                float az = (float) (a[2] + (b[2] - a[2]) * s0 - c.z);
                float bx = (float) (a[0] + (b[0] - a[0]) * s1 - c.x);
                float by = (float) (a[1] + (b[1] - a[1]) * s1 - c.y);
                float bz = (float) (a[2] + (b[2] - a[2]) * s1 - c.z);
                strip(pose, v, ax, ay, az, bx, by, bz, 0.1f, 0.1f, 0.55f * alpha, 0.55f * alpha, true);
            }
            along += len;
        }
    }

    /**
     * One piece from a to b (relative to the camera), {@code halfA} / {@code halfB} wide each side:
     * lying flat with {@code flat} (facing up), else (or going straight up a ladder) square to the
     * line of sight.
     */
    private static void strip(PoseStack pose, VertexConsumer v, float ax, float ay, float az,
                              float bx, float by, float bz, float halfA, float halfB,
                              float alphaA, float alphaB, boolean flat) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float ox, oy, oz;
        if (flat && dx * dx + dz * dz > 1e-4f) {
            // left of the way, level: (-dz, 0, dx); with the order below the face looks up
            ox = -dz;
            oy = 0;
            oz = dx;
        } else {
            // (b - a) × (camera - midpoint): across the piece, square to the line of sight
            float cx = -(ax + bx) / 2, cy = -(ay + by) / 2, cz = -(az + bz) / 2;
            ox = dy * cz - dz * cy;
            oy = dz * cx - dx * cz;
            oz = dx * cy - dy * cx;
        }
        float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (len < 1e-6f) {
            return;
        }
        ox /= len;
        oy /= len;
        oz /= len;
        int r = BODY >> 16 & 0xFF;
        int g = BODY >> 8 & 0xFF;
        int b = BODY & 0xFF;
        int a1 = Math.round(255 * Math.max(0, Math.min(1, alphaA)));
        int a2 = Math.round(255 * Math.max(0, Math.min(1, alphaB)));
        if (a1 == 0 && a2 == 0) {
            return;
        }
        var p = pose.last();
        v.addVertex(p, ax - ox * halfA, ay - oy * halfA, az - oz * halfA).setColor(r, g, b, a1);
        v.addVertex(p, ax + ox * halfA, ay + oy * halfA, az + oz * halfA).setColor(r, g, b, a1);
        v.addVertex(p, bx + ox * halfB, by + oy * halfB, bz + oz * halfB).setColor(r, g, b, a2);
        v.addVertex(p, bx - ox * halfB, by - oy * halfB, bz - oz * halfB).setColor(r, g, b, a2);
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
    }

    private static double sq(double v) {
        return v * v;
    }
}
