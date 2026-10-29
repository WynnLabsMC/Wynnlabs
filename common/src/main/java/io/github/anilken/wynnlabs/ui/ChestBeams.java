package io.github.anilken.wynnlabs.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.LootChests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A short thin beam in the tier colour above each known loot chest (setting chestBeams, the fewest
 * stars in chestBeamTier). Nothing else: no chest, outline or label. The beams are depth tested
 * like a beacon beam, so terrain hides them. The chest list (shipped plus the player's own
 * chests.json) is read once; the nearest {@link #CAP} are picked once a second, and each frame only
 * draws them.
 */
public final class ChestBeams {

    /** At most this many beams, the nearest first. */
    static final int CAP = 60;
    /** Chests farther than this get no beam; the last 15% fade out. */
    static final double RANGE = 200;
    /** Beam height above the chest top, in blocks. */
    private static final float HEIGHT = 10f;
    /** Unknown tier grey, ★ white, ★★ yellow, ★★★ purple, ★★★★ aqua. */
    private static final int[] COLOURS = {0xAAAAAA, 0xFFFFFF, 0xFFFF55, 0xFF55FF, 0x55FFFF};

    /** One beam: the chest's block and its colour. */
    record Beam(int x, int y, int z, int colour) {}

    private static List<Area> chests;
    private static int tick;
    private static boolean failed;
    /** What the frames draw, nearest first; replaced (never changed) once a second. */
    private static volatile List<Beam> shown = List.of();

    private ChestBeams() {}

    static int colourFor(int stars) {
        return COLOURS[Math.max(0, Math.min(4, stars))];
    }

    /**
     * The chests of at least {@code minStars} stars (0: every chest, tier unknown too) within
     * {@link #RANGE} of the player, nearest first, at most {@code cap}.
     */
    static List<Beam> pick(List<Area> all, double px, double py, double pz, int minStars, int cap) {
        double maxSq = RANGE * RANGE;
        List<double[]> found = new ArrayList<>();           // {index, distance²}
        for (int i = 0; i < all.size(); i++) {
            Area a = all.get(i);
            if (minStars > 0 && a.stars() < minStars) {
                continue;
            }
            double d2 = sq(a.x() + 0.5 - px) + sq(a.y() + 0.5 - py) + sq(a.z() + 0.5 - pz);
            if (d2 <= maxSq) {
                found.add(new double[] {i, d2});
            }
        }
        found.sort(Comparator.comparingDouble(f -> f[1]));
        List<Beam> out = new ArrayList<>(Math.min(cap, found.size()));
        for (int i = 0; i < found.size() && out.size() < cap; i++) {
            Area a = all.get((int) found.get(i)[0]);
            out.add(new Beam(a.x(), a.y(), a.z(), colourFor(a.stars())));
        }
        return List.copyOf(out);
    }

    /** Once a second: the beams to draw, or none while the setting is off. */
    public static void onTick(Minecraft mc) {
        if (++tick % 20 != 0) {
            return;
        }
        if (failed || mc.player == null || mc.level == null || !Settings.on("chestBeams")) {
            shown = List.of();
            return;
        }
        if (chests == null) {
            chests = LootChests.load(Dirs.config().resolve("chests.json"));
        }
        shown = pick(chests, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                Settings.stepValue("chestBeamTier"), CAP);
    }

    /** Every frame: the beams, farthest first, in one batch. */
    public static void renderWorld(PoseStack pose, MultiBufferSource buffers) {
        List<Beam> list = shown;
        if (list.isEmpty() || failed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) {
            return;
        }
        try {
            Vec3 c = mc.gameRenderer.getMainCamera().position();
            // past the far clipping plane a depth-tested beam is cut away anyway
            double clip = mc.gameRenderer.getDepthFar() * 0.9;
            VertexConsumer quads = buffers.getBuffer(RenderTypes.debugQuads());
            for (int i = list.size() - 1; i >= 0; i--) {
                Beam b = list.get(i);
                float dx = (float) (b.x() + 0.5 - c.x);
                float dy = (float) (b.y() + 14 / 16.0 - c.y);
                float dz = (float) (b.z() + 0.5 - c.z);
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float a = fade(dist);
                if (a < 0.02f || dist > clip) {
                    continue;
                }
                Beams.draw(pose, quads, dx, dy, dz, HEIGHT, b.colour(), a);
            }
        } catch (Throwable t) {
            failed = true;                          // once broken, stay out of the frame loop
            shown = List.of();
            System.out.println("[WynnLabs] chest beams failed, off until restart: " + t);
        } finally {
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        }
    }

    /** Fades out over the last 15% of the range, so beams do not pop. */
    static float fade(double dist) {
        double edge = RANGE * 0.15;
        return (float) Math.max(0, Math.min(1, (RANGE - dist) / edge));
    }

    private static double sq(double v) {
        return v * v;
    }
}
