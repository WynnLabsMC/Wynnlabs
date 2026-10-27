package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The on/off switch, one for every place (settings rows, the AI page, the tree's "DPS on screen"):
 * an HD track (a rounded pill with an inner shade: parchment when off, green when on, lighter on
 * hover, faded when disabled) and a soft round knob that slides across in {@link #SLIDE_MS} when the
 * value changes and widens a little while pressed. 19 x 11, as before. The slide keeps one
 * "changed at" a switch, by its id (a setting's key: a constant, so nothing is made per frame).
 */
final class UiToggle {

    static final int W = 19;
    static final int H = 11;
    static final long SLIDE_MS = 120;
    /** The knob's circle and its gap to the track's edge: 1 + 9 + 1 = 11, so it sits on a whole pixel. */
    static final int KNOB_D = 9;
    static final int KNOB_INSET = (H - KNOB_D) / 2;
    private static final Identifier ON = Identifier.fromNamespaceAndPath("wynnlabs", "kit/toggle_on");
    private static final Identifier OFF = Identifier.fromNamespaceAndPath("wynnlabs", "kit/toggle_off");
    private static final Identifier HOVER = Identifier.fromNamespaceAndPath("wynnlabs", "kit/toggle_hover");
    private static final Identifier KNOB = Identifier.fromNamespaceAndPath("wynnlabs", "kit/toggle_knob");

    /** A switch's last drawn value and when it changed. */
    private static final class Seen {
        boolean on;
        long at;
    }

    private static final Map<String, Seen> SEEN = new HashMap<>();

    private UiToggle() {}

    /** Draws a switch at x, y; returns whether the mouse is on it. */
    static boolean draw(GuiGraphics g, int x, int y, boolean on, String id, int mx, int my) {
        return draw(g, x, y, on, true, id, mx, my);
    }

    static boolean draw(GuiGraphics g, int x, int y, boolean on, boolean enabled, String id, int mx, int my) {
        boolean hover = enabled && mx >= x && mx < x + W && my >= y && my < y + H;
        int fade = enabled ? 0xFFFFFFFF : 0x80FFFFFF;
        NineSlice.draw(g, on ? ON : OFF, x, y, W, H, fade);
        if (hover) {
            NineSlice.draw(g, HOVER, x, y, W, H, 0xFFFFFFFF);
        }
        boolean pressed = hover && Minecraft.getInstance().mouseHandler.isLeftPressed();
        int kw = pressed ? H + 2 : H;
        float t = position(id, on, System.currentTimeMillis());
        float kx = x + t * (W - kw);
        g.pose().pushMatrix();
        g.pose().translate(kx, y);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KNOB, 0, 0, kw, H, enabled ? 0xFFFFFFFF : 0x99FFFFFF);
        g.pose().popMatrix();
        return hover;
    }

    /** Where the knob is, 0 (left, off) to 1 (right, on): sliding for {@link #SLIDE_MS} after a change, eased out. */
    static float position(String id, boolean on, long now) {
        if (id == null) {
            return on ? 1f : 0f;
        }
        Seen s = SEEN.get(id);
        if (s == null) {
            s = new Seen();
            s.on = on;
            s.at = 0;                               // first sight: where it is, no slide
            SEEN.put(id, s);
        } else if (s.on != on) {
            s.on = on;
            s.at = now;
        }
        return slide(on, now - s.at);
    }

    /** The eased position {@code elapsed} ms after a change to {@code on}. */
    static float slide(boolean on, long elapsed) {
        float p = Math.min(1f, Math.max(0f, elapsed / (float) SLIDE_MS));
        float e = 1f - (1f - p) * (1f - p);
        return on ? e : 1f - e;
    }
}
