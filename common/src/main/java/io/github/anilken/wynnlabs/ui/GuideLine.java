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
import net.minecraft.world.entity.player.Player;
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
 * A line from the player to the compass target (setting guideLine): the nearest pin of ours on
 * the Wynntils map, else the nearest other waypoint there (a map click or /compass puts its
 * target there too). On foot it follows a walkable route ({@link GuidePath}) through the loaded
 * area, worked out every {@link #ROUTE_EVERY} ticks on a thread of its own; past the loaded area,
 * or where no route goes on, it continues straight, laid on the highest block every
 * {@link #STEP} blocks. While riding or gliding it is a gentle arc through the air. Depth tested
 * like {@link ChestBeams}, so terrain hides it, and faded near the player so it never covers the
 * crosshair. Each frame only draws. Drawing only: nothing is sent to the server.
 */
public final class GuideLine {

    /** Ground points this far apart, in blocks. */
    static final double STEP = 2;
    /** The line stops this far from the player, in blocks (measured flat). */
    static final double CAP = 300;
    /** How far above the ground the ground line floats. */
    static final double LIFT = 0.3;
    /** Ticks between two looks at the target and the ground. */
    private static final int EVERY = 10;
    /** Ticks between two route searches. */
    private static final int ROUTE_EVERY = 20;
    /** At most this many spots looked at in one route search. */
    private static final int BUDGET = 20_000;
    /** Pin colours: "go there" aqua, quest points gold, someone else's waypoint white. */
    private static final int GO = 0x55FFFF;
    private static final int QUEST = 0xFFAA00;
    private static final int OTHER = 0xFFFFFF;

    /** Height of the highest block at a column, or {@link #UNKNOWN} where the chunk is not loaded. */
    @FunctionalInterface
    interface Ground {
        int height(int x, int z);
    }

    static final int UNKNOWN = Integer.MIN_VALUE;

    /** The target being led to and its colour. */
    record Target(double x, double y, double z, int colour) {}

    private static int tick;
    private static boolean failed;
    private static volatile Target target;
    /** The ground line's points {x, y, z}, player first; replaced (never changed) every {@link #EVERY} ticks. */
    private static volatile List<double[]> ground = List.of();
    /** The target the shown ground line was routed for (null: only the straight line so far). */
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

    /**
     * A gentle arc from ({@code sx}, {@code sy}, {@code sz}) to the target, cut at {@code cap}
     * blocks (straight-line distance): {@code segments + 1} points, highest in the middle at a
     * twentieth of the length (at most 12 blocks) above the straight line.
     */
    static List<double[]> arc(double sx, double sy, double sz, double tx, double ty, double tz,
                              double cap, int segments) {
        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double k = dist > cap ? cap / dist : 1;
        double rise = Math.min(12, dist * k * 0.05);
        List<double[]> out = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double u = t * k;
            out.add(new double[] {sx + dx * u, sy + dy * u + rise * Math.sin(Math.PI * t), sz + dz * u});
        }
        return out;
    }

    /** Opacity at {@code d} blocks from the player: none up close, full from 8 blocks on. */
    static float nearFade(double d) {
        return (float) Math.max(0, Math.min(1, (d - 2) / 6));
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
        if (best == null) {
            return null;
        }
        int colour = !bestOurs ? OTHER : Waypoints.isQuest(best) ? QUEST : GO;
        return new Target(best.x() + 0.5, best.y(), best.z() + 0.5, colour);
    }

    /** Every {@link #EVERY} ticks: the target, and on foot the ground points to it. */
    public static void onTick(Minecraft mc) {
        if (++tick % EVERY != 0) {
            return;
        }
        Player p = mc.player;
        if (failed || p == null || mc.level == null || !Settings.on("guideLine")) {
            target = null;
            ground = List.of();
            return;
        }
        try {
            var pins = Wyn.waypoints();
            Target t = pins == null ? null : pick(pins.all(), p.getX(), p.getZ());
            boolean changed = !Objects.equals(t, target);
            target = t;
            if (t == null || airborne(p)) {
                ground = List.of();
                routed = null;
                return;
            }
            ClientLevel level = mc.level;
            if (changed || routed == null) {
                // the straight line at once; the route replaces it when found
                ground = List.copyOf(sample(p.getX(), p.getY(), p.getZ(), t.x(), t.z(), STEP, CAP, heights(level)));
            }
            if (changed || tick % ROUTE_EVERY == 0) {
                if (changed) {
                    routed = null;
                }
                int sx = (int) Math.floor(p.getX());
                int sy = (int) Math.floor(p.getY() + 0.01);
                int sz = (int) Math.floor(p.getZ());
                int box = Math.max(32, Math.min(256, mc.options.getEffectiveRenderDistance() * 16));
                ROUTER.execute(() -> route(level, t, sx, sy, sz, box));
            }
        } catch (Throwable e) {
            failed = true;
            target = null;
            ground = List.of();
            System.out.println("[WynnLabs] guide line failed, off until restart: " + e);
        }
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
            GuidePath.Route r = GuidePath.find(w, sx, sy, sz, tx, tz, box, BUDGET);
            if (r == null || r.nodes().size() < 2 || !t.equals(target)) {
                return;                                 // the straight line stays
            }
            List<double[]> pts = new ArrayList<>(GuidePath.points(w, GuidePath.smooth(w, r.nodes(), 24), LIFT));
            double[] end = pts.get(pts.size() - 1);
            double walked = Math.hypot(end[0] - sx, end[2] - sz);
            if (!r.reached() && walked < CAP) {
                List<double[]> rest = sample(end[0], end[1] - LIFT, end[2], t.x(), t.z(), STEP, CAP - walked, heights(level));
                pts.addAll(rest.subList(1, rest.size()));
            }
            if (t.equals(target)) {
                ground = List.copyOf(pts);
                routed = t;
            }
        } catch (Throwable e) {
            // a failed search leaves the straight line; the next one tries again
        }
    }

    /** Columns the player can step to, read from loaded chunks only (an unloaded one has none). */
    private static GuidePath.Walkable walkable(ClientLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, z, fromY) -> {
            ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
            if (chunk == null) {
                return GuidePath.NONE;
            }
            for (int y = fromY + 1; y >= fromY - 3; y--) {
                if (standable(chunk, pos, x, y, z)) {
                    return y;
                }
            }
            return GuidePath.NONE;
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

    /** On a mount or gliding: the line goes through the air. */
    private static boolean airborne(Player p) {
        return p.isPassenger() || p.isFallFlying();
    }

    /** Every frame: the line, in one batch. */
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
            List<double[]> pts;
            if (airborne(p)) {
                // from just ahead of the eyes, so the start is not inside the mount
                Vec3 look = p.getViewVector(pt);
                double sx = me.x + look.x * 3;
                double sy = me.y + p.getEyeHeight() - 0.6;
                double sz = me.z + look.z * 3;
                pts = arc(sx, sy, sz, t.x(), t.y() + 1, t.z(), CAP, 64);
            } else {
                pts = ground;
                if (pts.isEmpty()) {
                    return;
                }
            }
            VertexConsumer quads = buffers.getBuffer(RenderTypes.debugQuads());
            double fx = t.x() - me.x;
            double fz = t.z() - me.z;
            for (int i = 1; i < pts.size(); i++) {
                double[] a = pts.get(i - 1);
                double[] b = pts.get(i);
                // the ground points are a few ticks old: skip the ones already behind the player
                if ((b[0] - me.x) * fx + (b[2] - me.z) * fz < 0) {
                    continue;
                }
                float fa = nearFade(Math.sqrt(sq(a[0] - me.x) + sq(a[1] - me.y) + sq(a[2] - me.z)));
                float fb = nearFade(Math.sqrt(sq(b[0] - me.x) + sq(b[1] - me.y) + sq(b[2] - me.z)));
                if (fa < 0.02f && fb < 0.02f) {
                    continue;
                }
                float ax = (float) (a[0] - c.x), ay = (float) (a[1] - c.y), az = (float) (a[2] - c.z);
                float bx = (float) (b[0] - c.x), by = (float) (b[1] - c.y), bz = (float) (b[2] - c.z);
                ribbon(pose, quads, ax, ay, az, bx, by, bz, 0.06f, t.colour(), 0.75f * fa, 0.75f * fb);
                ribbon(pose, quads, ax, ay, az, bx, by, bz, 0.18f, t.colour(), 0.2f * fa, 0.2f * fb);
            }
        } catch (Throwable e) {
            failed = true;                          // once broken, stay out of the frame loop
            target = null;
            ground = List.of();
            System.out.println("[WynnLabs] guide line failed, off until restart: " + e);
        } finally {
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        }
    }

    /**
     * One segment from a to b (relative to the camera) as a strip {@code half} wide each side,
     * turned to face the camera.
     */
    private static void ribbon(PoseStack pose, VertexConsumer v, float ax, float ay, float az,
                               float bx, float by, float bz, float half, int rgb, float alphaA, float alphaB) {
        // side = (b - a) × (camera - midpoint): across the segment, square to the line of sight
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float cx = -(ax + bx) / 2, cy = -(ay + by) / 2, cz = -(az + bz) / 2;
        float ox = dy * cz - dz * cy;
        float oy = dz * cx - dx * cz;
        float oz = dx * cy - dy * cx;
        float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (len < 1e-5f) {
            return;
        }
        ox *= half / len;
        oy *= half / len;
        oz *= half / len;
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int a1 = Math.round(255 * Math.max(0, Math.min(1, alphaA)));
        int a2 = Math.round(255 * Math.max(0, Math.min(1, alphaB)));
        var p = pose.last();
        v.addVertex(p, ax - ox, ay - oy, az - oz).setColor(r, g, b, a1);
        v.addVertex(p, ax + ox, ay + oy, az + oz).setColor(r, g, b, a1);
        v.addVertex(p, bx + ox, by + oy, bz + oz).setColor(r, g, b, a2);
        v.addVertex(p, bx - ox, by - oy, bz - oz).setColor(r, g, b, a2);
    }

    private static double sq(double v) {
        return v * v;
    }
}
