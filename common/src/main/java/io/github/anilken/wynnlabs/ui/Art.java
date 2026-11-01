package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The mod's own GUI art (tools/art renders it from SVG): rounded boxes that stretch to any size
 * (nine-sliced, white, tinted here) and Tabler icons. Radii: 2, 3, 4, 5, 6, 8.
 */
public final class Art {

    private Art() {}

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath("wynnlabs", path);
    }

    private static int radius(int r, int w, int h) {
        int[] have = {2, 3, 4, 5, 6, 8};
        int max = Math.max(2, Math.min(w, h) / 2);
        int best = 2;
        for (int v : have) {
            if (v <= Math.min(r, max)) {
                best = v;
            }
        }
        return best;
    }

    /** A rounded box: {@code fill} inside, a 1px {@code line} border (0 = none). Colours are ARGB. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int r, int fill, int line) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int rr = radius(r, w, h);
        if (fill != 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("shape/r" + rr + "_fill"), x, y, w, h, fill);
        }
        if (line != 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("shape/r" + rr + "_line"), x, y, w, h, line);
        }
    }

    /** A fully rounded pill. */
    static void pill(GuiGraphics g, int x, int y, int w, int h, int fill, int line) {
        box(g, x, y, w, h, h / 2, fill, line);
    }

    /**
     * A Tabler icon (tools/art's list), tinted. Drawn from the 64 px set when it lands larger than
     * 45 screen pixels, else from the 32 px one, so it is neither blocky when big nor loses strokes
     * when small.
     */
    public static void icon(GuiGraphics g, String name, int x, int y, int size, int colour) {
        double screen = size * g.pose().m00() * net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        String set = screen > 45 ? "icon/" : "icon_lo/";
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(set + name), x, y, size, size, colour);
    }

    /**
     * A straight line {@code t} units thick from (x0, y0) to (x1, y1), drawn left to right as one
     * fill per column that spans the column's rise, so steep parts stay joined.
     */
    static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int t, int colour) {
        if (x1 < x0) {
            int tx = x0;
            x0 = x1;
            x1 = tx;
            int ty = y0;
            y0 = y1;
            y1 = ty;
        }
        if (x1 == x0) {
            g.fill(x0, Math.min(y0, y1), x0 + t, Math.max(y0, y1) + t, colour);
            return;
        }
        int prev = y0;
        for (int x = x0; x <= x1; x++) {
            int y = y0 + Math.round((y1 - y0) * (float) (x - x0) / (x1 - x0));
            g.fill(x, Math.min(prev, y), x + t, Math.max(prev, y) + t, colour);
            prev = y;
        }
    }

    /** An icon on a rounded square of its own colour family, like the web mock-up. */
    static void iconTile(GuiGraphics g, String name, int x, int y, int size, int bg, int fg) {
        box(g, x, y, size, size, Math.max(3, size / 4), bg, 0);
        int pad = Math.max(2, size / 5);
        icon(g, name, x + pad, y + pad, size - 2 * pad, fg);
    }
}
