package io.github.anilken.wynnlabs.ui;

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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A navigation path from the player to the compass target (setting guideLine), in the manner of
 * Black Desert's: a light cyan strip of dashes flowing toward the target, with a thin pillar of
 * light where it ends. The target is the nearest pin of ours on the Wynntils map, else the nearest
 * other waypoint there (a map click or /compass puts its target there too).
 *
 * <p>On foot the strip lies flat on the ground along a walkable route ({@link GuidePath}) through
 * the loaded area, worked out every {@link #ROUTE_EVERY} ticks on a thread of its own; past the
 * loaded area, or where no route goes on, it continues straight, laid on the highest block every
 * {@link #STEP} blocks. The route aims at the target's floor, not only its column, and climbs
 * ladders and vines. While riding or gliding it is a straight line to the target; when the target
 * is well above or below the mount (a cliff city a wyvern cannot reach) and there is ground just
 * under the player, the ground route from there is drawn instead, so the player sees where to
 * land and walk. Depth tested like
 * {@link ChestBeams}, so terrain hides it. Each frame only draws. Drawing only: nothing is sent to
 * the server.
 */
public final class GuideLine {

    /** Ground points this far apart on the straight stretch, in blocks. */
    static final double STEP = 2;
    /** The line stops this far from the player, in blocks (measured flat). */
    static final double CAP = 300;
    /** How far above the ground the strip lies. */
    static final double LIFT = 0.08;
    /** Dashes this long with gaps this long, in blocks; they flow this many blocks a second. */
    static final double DASH = 1.2;
    static final double GAP = 0.6;
    static final double FLOW = 1.0;
    /** The first blocks from the player fade in over this length. */
    static final double FADE = 1.5;
    /** Mounted with the target this much higher or lower: the ground route from below is drawn instead. */
    private static final double HIGH = 8;
    /** Where a rider can land: ground at most this far below, ... */
    private static final int LAND_DEPTH = 6;
    /** ... in the column under them or up to this many columns away. */
    private static final int LAND_REACH = 3;
    /** Height of the pillar at the end of the path. */
    private static final float PILLAR = 6f;
    /** Light cyan. */
    private static final int COLOUR = 0x9FE8FF;
    /** Ticks between two looks at the target. */
    private static final int EVERY = 10;
    /** Ticks between two route searches. */
    private static final int ROUTE_EVERY = 20;
    /** At most this many spots looked at in one route search. */
    private static final int BUDGET = 20_000;

    /** Height of the highest block at a column, or {@link #UNKNOWN} where the chunk is not loaded. */
    @FunctionalInterface
    interface Ground {
        int height(int x, int z);
    }

    static final int UNKNOWN = Integer.MIN_VALUE;

    /** The target being led to (the pin's block centre). */
    record Target(double x, double y, double z) {}

    /**
     * A ground path: its points {x, y, z}, player end first, where its pillar stands (null: none),
     * and the index from which the points are only the straight guess past the last reachable spot
     * (the size when the route reaches the target).
     */
    record Path(List<double[]> points, double[] end, int tail) {}

    private static final Path NO_PATH = new Path(List.of(), null, 0);
    /** The straight guess past the route is drawn at this share of the opacity, still. */
    private static final float TAIL_ALPHA = 1 / 3f;

    private static int tick;
    private static boolean failed;
    private static volatile Target target;
    /** The ground path drawn; replaced (never changed) by the tick or the route thread. */
    private static volatile Path ground = NO_PATH;
    /** Riding or gliding with no ground route to show: the straight line through the air. */
    private static volatile boolean airLine;
    /** The target the shown ground path was routed for (null: only the straight line so far). */
    private static volatile Target routed;
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
        return best == null ? null : new Target(best.x() + 0.5, best.y(), best.z() + 0.5);
    }

    /** Every {@link #EVERY} ticks: the target, and when a ground path is wanted, its search. */
    public static void onTick(Minecraft mc) {
        if (++tick % EVERY != 0) {
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
            Target t = pins == null ? null : pick(pins.all(), p.getX(), p.getZ());
            boolean changed = !Objects.equals(t, target);
            target = t;
            ClientLevel level = mc.level;
            int px = (int) Math.floor(p.getX());
            int py = (int) Math.floor(p.getY() + 0.01);
            int pz = (int) Math.floor(p.getZ());
            int[] start = null;
            if (t == null) {
                // nothing to lead to
            } else if (!airborne(p)) {
                start = new int[] {px, py, pz};
            } else if (Math.abs(t.y() - p.getY()) > HIGH) {
                // the ground just under the mount: where to land and walk on
                start = GuidePath.below(walkable(level), px, pz, py, LAND_DEPTH, LAND_REACH);
            }
            airLine = t != null && start == null && airborne(p);
            if (start == null) {
                ground = NO_PATH;
                routed = null;
                return;
            }
            int sx = start[0];
            int sy = start[1];
            int sz = start[2];
            if (changed || routed == null) {
                // the straight line at once; the route replaces it when found
                List<double[]> line = sample(sx + 0.5, sy, sz + 0.5, t.x(), t.z(), STEP, CAP, heights(level));
                ground = new Path(List.copyOf(line), line.get(line.size() - 1), line.size());
            }
            if (changed || tick % ROUTE_EVERY == 0) {
                if (changed) {
                    routed = null;
                }
                int box = Math.max(32, Math.min(256, mc.options.getEffectiveRenderDistance() * 16));
                ROUTER.execute(() -> route(level, t, sx, sy, sz, box));
            }
        } catch (Throwable e) {
            failed = true;
            clear();
            System.out.println("[WynnLabs] guide line failed, off until restart: " + e);
        }
    }

    private static void clear() {
        target = null;
        ground = NO_PATH;
        routed = null;
    }

    /** The highest block of a column, {@link #UNKNOWN} where its chunk is not loaded. */
    private static Ground heights(ClientLevel level) {
        return (x, z) -> level.hasChunk(x >> 4, z >> 4)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : UNKNOWN;
    }

    /**
     * On the route thread: the walking route to the target, smoothed, then straight on to the
     * target from where it stops. Kept only while the target is still the same.
     */
    private static void route(ClientLevel level, Target t, int sx, int sy, int sz, int box) {
        try {
            GuidePath.Walkable w = walkable(level);
            int tx = (int) Math.floor(t.x());
            int tz = (int) Math.floor(t.z());
            int ty = GuidePath.feet(w, tx, tz, (int) Math.floor(t.y()));
            GuidePath.Route r = GuidePath.find(w, sx, sy, sz, tx, ty, tz, box, BUDGET);
            if (r == null || r.nodes().size() < 2 || !t.equals(target)) {
                return;                                 // the straight line stays
            }
            List<double[]> pts = new ArrayList<>(GuidePath.points(w, GuidePath.smooth(w, r.nodes(), 24), LIFT));
            double[] end = pts.get(pts.size() - 1);
            double walked = Math.hypot(end[0] - sx, end[2] - sz);
            int tail = pts.size();
            if (!r.reached() && walked < CAP) {
                List<double[]> rest = sample(end[0], end[1] - LIFT, end[2], t.x(), t.z(), STEP, CAP - walked, heights(level));
                pts.addAll(rest.subList(1, rest.size()));
            }
            if (t.equals(target)) {
                ground = new Path(List.copyOf(pts), end, tail);
                routed = t;
            }
        } catch (Throwable e) {
            // a failed search leaves the straight line; the next one tries again
        }
    }

    /** Columns the player can step to, read from loaded chunks only (an unloaded one has none). */
    private static GuidePath.Walkable walkable(ClientLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return new GuidePath.Walkable() {
            @Override
            public int stand(int x, int z, int fromY) {
                ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                if (chunk == null) {
                    return GuidePath.NONE;
                }
                for (int y = fromY + 1; y >= fromY - 3; y--) {
                    if (standable(chunk, pos, x, y, z) || onLadder(chunk, pos, x, y, z)) {
                        return y;
                    }
                }
                // no fall damage in Wynncraft: off a ledge, down an open column to the ground
                if (chunk.getBlockState(pos.set(x, fromY + 1, z)).blocksMotion()) {
                    return GuidePath.NONE;
                }
                for (int y = fromY; y >= fromY - GuidePath.DROP; y--) {
                    BlockState feet = chunk.getBlockState(pos.set(x, y, z));
                    if (feet.blocksMotion() || !feet.getFluidState().isEmpty()) {
                        return GuidePath.NONE;
                    }
                    if (y < fromY - 3 && standable(chunk, pos, x, y, z)) {
                        return y;
                    }
                }
                return GuidePath.NONE;
            }

            @Override
            public int climb(int x, int z, int y, int dy) {
                ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                return chunk != null && onLadder(chunk, pos, x, y, z) && onLadder(chunk, pos, x, y + dy, z)
                        ? y + dy : GuidePath.NONE;
            }

            @Override
            public double cost(int x, int y, int z) {
                ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
                return chunk != null && road(chunk.getBlockState(pos.set(x, y - 1, z))) ? GuidePath.ROAD : 1;
            }
        };
    }

    /** Solid ground under feet height {@code y}, two free blocks from there, no water or lava at the feet. */
    private static boolean standable(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        BlockState feet = chunk.getBlockState(pos.set(x, y, z));
        if (feet.blocksMotion() || !feet.getFluidState().isEmpty()) {
            return false;
        }
        if (chunk.getBlockState(pos.set(x, y + 1, z)).blocksMotion()) {
            return false;
        }
        BlockState below = chunk.getBlockState(pos.set(x, y - 1, z));
        return below.blocksMotion() && below.getFluidState().isEmpty();
    }

    /** Feet height {@code y} in a ladder, vines or scaffolding, with room for the head. */
    private static boolean onLadder(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        if (!chunk.getBlockState(pos.set(x, y, z)).is(BlockTags.CLIMBABLE)) {
            return false;
        }
        BlockState head = chunk.getBlockState(pos.set(x, y + 1, z));
        return !head.blocksMotion() || head.is(BlockTags.CLIMBABLE);
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
            VertexConsumer quads = buffers.getBuffer(RenderTypes.debugQuads());
            if (airLine && airborne(p)) {
                double dx = t.x() - me.x;
                double dy = t.y() + 1 - me.y;
                double dz = t.z() - me.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double k = dist > CAP ? CAP / dist : 1;
                List<double[]> air = List.of(new double[] {me.x, me.y, me.z},
                        new double[] {me.x + dx * k, me.y + dy * k, me.z + dz * k});
                dashes(pose, quads, c, air, seconds, false, 1f, true);
                if (k == 1) {
                    Beams.draw(pose, quads, (float) (t.x() - c.x), (float) (t.y() + 1 - c.y), (float) (t.z() - c.z),
                            PILLAR, COLOUR, 1f);
                }
            }
            Path g = ground;
            List<double[]> pts = g.points();
            if (pts.size() >= 2) {
                int tail = Math.max(1, Math.min(pts.size(), g.tail()));
                List<double[]> walk = pts.subList(0, tail);
                if (walk.size() >= 2) {
                    dashes(pose, quads, c, fromFeet(walk, me), seconds, true, 1f, true);
                }
                if (tail < pts.size()) {
                    // past the last reachable spot: a still, faint straight guess, not a path to follow
                    dashes(pose, quads, c, pts.subList(tail - 1, pts.size()), 0, true, TAIL_ALPHA, false);
                }
                if (g.end() != null) {
                    double[] e = g.end();
                    Beams.draw(pose, quads, (float) (e[0] - c.x), (float) (e[1] - c.y), (float) (e[2] - c.z),
                            PILLAR, COLOUR, 1f);
                }
            }
        } catch (Throwable e) {
            failed = true;                          // once broken, stay out of the frame loop
            clear();
            System.out.println("[WynnLabs] guide line failed, off until restart: " + e);
        } finally {
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        }
    }

    /** The path cut where it passes the player, so it starts at their feet (it is up to a second old). */
    private static List<double[]> fromFeet(List<double[]> pts, Vec3 me) {
        double[] at = nearest(pts, me.x, me.z);
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
     * The dashes along {@code pts} (absolute positions), each narrowing at its back; flat on the
     * ground with {@code flat}, else turned to face the camera; at {@code alpha} of the full
     * opacity, fading in from the first point with {@code fadeIn}.
     */
    private static void dashes(PoseStack pose, VertexConsumer v, Vec3 c, List<double[]> pts, double seconds,
                               boolean flat, float alpha, boolean fadeIn) {
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
            // cut the segment where dashes start and end: pieces of at most 0.3 blocks
            int pieces = Math.max(1, (int) Math.ceil(len / 0.3));
            for (int s = 0; s < pieces; s++) {
                double s0 = (double) s / pieces;
                double s1 = (double) (s + 1) / pieces;
                double d0 = along + len * s0;
                double d1 = along + len * s1;
                double mid = dashAt(total - (d0 + d1) / 2, seconds);
                if (mid < 0) {
                    continue;
                }
                double u0 = Math.max(0, dashAt(total - d0, seconds));
                double u1 = Math.max(0, dashAt(total - d1, seconds));
                float f0 = alpha * (fadeIn ? nearFade(d0) : 1);
                float f1 = alpha * (fadeIn ? nearFade(d1) : 1);
                if (f0 < 0.02f && f1 < 0.02f) {
                    continue;
                }
                float ax = (float) (a[0] + (b[0] - a[0]) * s0 - c.x);
                float ay = (float) (a[1] + (b[1] - a[1]) * s0 - c.y);
                float az = (float) (a[2] + (b[2] - a[2]) * s0 - c.z);
                float bx = (float) (a[0] + (b[0] - a[0]) * s1 - c.x);
                float by = (float) (a[1] + (b[1] - a[1]) * s1 - c.y);
                float bz = (float) (a[2] + (b[2] - a[2]) * s1 - c.z);
                // the back of a dash is narrow, its front full width: a soft arrow toward the target
                float w0 = (float) (0.35 + 0.65 * u0);
                float w1 = (float) (0.35 + 0.65 * u1);
                strip(pose, v, ax, ay, az, bx, by, bz, 0.06f * w0, 0.06f * w1, 0.8f * f0, 0.8f * f1, flat);
                strip(pose, v, ax, ay, az, bx, by, bz, 0.175f * w0, 0.175f * w1, 0.25f * f0, 0.25f * f1, flat);
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
        int r = COLOUR >> 16 & 0xFF;
        int g = COLOUR >> 8 & 0xFF;
        int b = COLOUR & 0xFF;
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
