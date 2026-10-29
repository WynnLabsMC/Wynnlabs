package io.github.anilken.wynnlabs.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The short thin beacon-like beam the world markers share (loot chests, tracked drops): two
 * upright strips across the line of sight, a core and a faint halo, fading out toward the top.
 * Drawn into a depth-tested quad buffer, so terrain hides it like a beacon beam.
 */
final class Beams {

    private Beams() {}

    /**
     * One beam whose foot is at ({@code x}, {@code y}, {@code z}) relative to the camera,
     * {@code height} blocks tall, in {@code rgb} at {@code alpha} (0..1).
     */
    static void draw(PoseStack pose, VertexConsumer quads, float x, float y, float z, float height, int rgb, float alpha) {
        double len = Math.max(1e-3, Math.sqrt(x * x + z * z));
        float ux = (float) (-z / len);
        float uz = (float) (x / len);
        strip(pose, quads, x, y, z, ux, uz, 0.05f, height, rgb, 0.6f * alpha);
        strip(pose, quads, x, y, z, ux, uz, 0.15f, height, rgb, 0.18f * alpha);
    }

    /** One upright strip, {@code half} wide each side along (ux, uz), from y up {@code height}. */
    private static void strip(PoseStack pose, VertexConsumer v, float x, float y, float z, float ux, float uz,
                              float half, float height, int rgb, float alpha) {
        int a = Math.round(255 * Math.max(0, Math.min(1, alpha)));
        if (a == 0) {
            return;
        }
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        float ox = ux * half;
        float oz = uz * half;
        var p = pose.last();
        v.addVertex(p, x - ox, y, z - oz).setColor(r, g, b, a);
        v.addVertex(p, x + ox, y, z + oz).setColor(r, g, b, a);
        v.addVertex(p, x + ox, y + height, z + oz).setColor(r, g, b, 0);
        v.addVertex(p, x - ox, y + height, z - oz).setColor(r, g, b, 0);
    }
}
