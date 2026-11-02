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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A line from the player to the compass target (setting guideLine): the nearest pin of ours on
 * the Wynntils map, else the nearest other waypoint there (a map click or /compass puts its
 * target there too). On foot it follows the ground: a point every {@link #STEP} blocks laid on
 * the highest block at that spot, straight on where the chunk is not loaded. While riding or
 * gliding it is a gentle arc through the air. Depth tested like {@link ChestBeams}, so terrain
 * hides it, and faded near the player so it never covers the crosshair. The target and the ground
 * points are worked out every {@link #EVERY} ticks; each frame only draws them. Drawing only:
 * nothing is sent to the server.
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
            target = t;
            if (t == null || airborne(p)) {
                ground = List.of();
                return;
            }
            ClientLevel level = mc.level;
            ground = List.copyOf(sample(p.getX(), p.getY(), p.getZ(), t.x(), t.z(), STEP, CAP,
                    (x, z) -> level.hasChunk(x >> 4, z >> 4)
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : UNKNOWN));
        } catch (Throwable e) {
            failed = true;
            target = null;
            ground = List.of();
            System.out.println("[WynnLabs] guide line failed, off until restart: " + e);
        }
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
