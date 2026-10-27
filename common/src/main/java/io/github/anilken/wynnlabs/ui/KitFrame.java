package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.DevHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/** The menu's frame: the wooden window, the rail's backdrop and rules, the parchment page, the close seal. */
final class KitFrame {

    private KitFrame() {}

    /** The window: dark outline, wooden border, brass nails in the corners. */
    static void window(GuiGraphics g, int x, int y, int w, int h) {
        LayoutCheck.frame(g.pose(), x, y, w, h);                 // the preview's layout check: the screen's own box
        if (DevHooks.ON) {
            DevHooks.frame(g.pose(), x, y, w, h + 3);            // the dev screenshot tool's crop: the frame and its shadow
        }
        // a soft shadow straight under the frame, fading out in three steps
        g.fill(x + 3, y + h, x + w - 3, y + h + 1, 0x66000000);
        g.fill(x + 5, y + h + 1, x + w - 5, y + h + 2, 0x40000000);
        g.fill(x + 8, y + h + 2, x + w - 8, y + h + 3, 0x1C000000);
        Kit.nine(g, "window", x, y, w, h);
    }

    /**
     * The rail's backdrop: {@link Theme#PLANK_B} with a faint ship's deck in it (kit/rail_wood,
     * tone on tone), a little darker toward the page along its right side.
     */
    static void railPlain(GuiGraphics g, int x, int y, int w, int h, int railW) {
        LayoutCheck.textArea(g.pose(), x, y, railW, h);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Kit.sprite("rail_wood"), x, y, w, h);
        for (int i = 0; i < 4; i++) {
            g.fill(x + railW - 6 + i, y, x + railW - 5 + i, y + h, (0x10 + i * 0x0C) << 24 | (Theme.WOOD_EDGE & 0xFFFFFF));
        }
    }

    /** An engraved rule on the rail (kit/rail_rule), 2px tall. */
    static void railRule(GuiGraphics g, int x, int y, int w) {
        if (w > 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Kit.sprite("rail_rule"), x, y, w, 2);
        }
    }

    /** The parchment page: shadow, torn rim with its inner rule, grain and corner ornaments. */
    static void paper(GuiGraphics g, int x, int y, int w, int h) {
        LayoutCheck.textArea(g.pose(), x, y, w, h);                // the preview's layout check: text may sit here
        g.fill(x + 2, y + h, x + w + 2, y + h + 2, 0x55000000);
        Kit.nine(g, "paper", x, y, w, h);
        // the grain over the flat middle, in as few 256px pieces as the page needs
        int gx0 = x + 6;
        int gy0 = y + 6;
        int gw = w - 12;
        int gh = h - 12;
        for (int oy = 0; oy < gh; oy += Kit.GRAIN_SIZE) {
            for (int ox = 0; ox < gw; ox += Kit.GRAIN_SIZE) {
                int pw = Math.min(Kit.GRAIN_SIZE, gw - ox);
                int ph = Math.min(Kit.GRAIN_SIZE, gh - oy);
                g.blit(RenderPipelines.GUI_TEXTURED, Kit.GRAIN, gx0 + ox, gy0 + oy, 0, 0, pw, ph, Kit.GRAIN_SIZE, Kit.GRAIN_SIZE);
            }
        }
        Kit.nine(g, "corner_tl", x + 2, y + 2, 13, 13);
        Kit.nine(g, "corner_tr", x + w - 15, y + 2, 13, 13);
        Kit.nine(g, "corner_bl", x + 2, y + h - 15, 13, 13);
        Kit.nine(g, "corner_br", x + w - 15, y + h - 15, 13, 13);
    }

    /**
     * The red close seal on the window's top-right corner, over the brass nail there (the window
     * drawn by {@link #window} 5px around the panel). Returns its click rectangle.
     */
    static int[] closeOutside(GuiGraphics g, int screenW, int px, int py, int pw, boolean hover) {
        int size = 14;
        int x = px + pw + 2 - size / 2;         // the nail sits 3px in from the frame's outer corner
        int y = py - 2 - size / 2;
        // a window with no room past the corner: pushed in until it is whole, over the frame's corner
        x = Math.min(x, screenW - size);
        y = Math.max(y, 0);
        close(g, x, y, size, hover);
        LayoutCheck.frame(g.pose(), x, y, size, size);
        if (DevHooks.ON) {
            DevHooks.frame(g.pose(), x, y, size, size);           // the dev screenshot tool's crop keeps the seal
        }
        return new int[] {x, y, size, size};
    }

    /** The red close seal, {@code size} square. */
    static void close(GuiGraphics g, int x, int y, int size, boolean hover) {
        Kit.nine(g, hover ? "close_hover" : "close", x, y, size, size);
    }
}
