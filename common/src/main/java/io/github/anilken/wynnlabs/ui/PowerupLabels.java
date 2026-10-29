package io.github.anilken.wynnlabs.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.mounts.Powerups;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The floating labels over mount powerups while riding (setting powerupLabels): the stat's name in
 * its colour on a name tag's translucent backing, facing the camera, {@link #LIFT} blocks above
 * the powerup. The scale grows with distance so a label keeps about the same size on screen
 * (setting powerupLabelSize scales it); a maxed stat's text is faded. Which powerups get one is
 * {@link Powerups}' work; this only draws.
 */
public final class PowerupLabels {

    /** Blocks above the powerup's displays: its icon floats well above them, the label goes just over it. */
    static final double LIFT = 3.5;
    /** The scale at the camera... */
    private static final double SCALE = 0.025;
    /** ...plus this much per block of distance. */
    private static final double GROW = 0.01;
    /** The size at 100 %. */
    private static final double BASE = 0.5;
    /** Labels closer than this are not drawn (the powerup is right there). */
    private static final double NEAR = 1.5;
    /** Behind the text, like a name tag's backing. */
    private static final int BACKING = 0x80000000;
    /** A maxed stat's text alpha (30 %). */
    static final int DIM_ALPHA = 77;

    private PowerupLabels() {}

    /** A label's scale at {@code distance} blocks from the camera, at {@code size} percent, times its own {@code multiplier}. */
    static float scale(double distance, int size, float multiplier) {
        return (float) ((distance * GROW + SCALE) * BASE * size / 100.0 * multiplier);
    }

    /** The text colour: the stat's RGB, faded when maxed. */
    static int colour(int rgb, boolean dim) {
        return (dim ? DIM_ALPHA : 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static void renderWorld(PoseStack pose, MultiBufferSource buffers) {
        List<Powerups.Shown> labels = Powerups.shown();
        if (labels.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        Vec3 me = mc.player.position();
        Font font = mc.font;
        int size = Settings.stepValue("powerupLabelSize");
        // far to near, so each backing blends over the ones behind it
        List<Powerups.Shown> order = new ArrayList<>(labels);
        order.sort(Comparator.comparingDouble((Powerups.Shown s) -> -me.distanceToSqr(s.x(), s.y(), s.z())));
        for (Powerups.Shown s : order) {
            double x = s.x() - cam.x;
            double y = s.y() + LIFT - cam.y;
            double z = s.z() - cam.z;
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d < NEAR) {
                continue;
            }
            float k = scale(d, size, s.size());
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(camera.rotation());
            pose.scale(k, -k, k);
            font.drawInBatch(s.text(), -font.width(s.text()) / 2f, 0, colour(s.colour(), s.dim()), false,
                    pose.last().pose(), buffers, Font.DisplayMode.NORMAL, BACKING, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }
}
