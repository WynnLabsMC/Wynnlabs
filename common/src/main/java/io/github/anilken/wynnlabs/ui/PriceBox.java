package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Wynnventory's price box, drawn by us at the screen's left edge. Wynnventory draws the box
 * beside where the game's own tooltip would stand, or (its anchor setting) at the left edge,
 * through a positioner of its own; but Wynntils' tooltip fitting takes every tooltip's place
 * over, so the box lands at the mouse, under the item tooltip. So, the way WynnChaYuan does it:
 * as Wynnventory is about to draw the box its lines are taken ({@link #take}) and drawn at the
 * left edge, level with the middle of the screen, with a positioner the fitting is told to keep
 * ({@link #drawing}, {@link #fixed}). Where the box stands is noted, so a tooltip near the left
 * edge keeps out from under it ({@link #avoid}). Nothing of Wynnventory's is copied: its lines
 * are drawn as the game draws any tooltip.
 */
public final class PriceBox {

    private PriceBox() {}

    /** A note older than this is from an earlier tooltip: ignored. */
    static final long FRESH_NANOS = 120_000_000L;
    /** The frame the game draws around a tooltip. */
    static final int FRAME = 4;

    private static boolean drawing;
    /** Where the box is being drawn: the positioner forced on the tooltip fitting while {@link #drawing()}. */
    private static final Vector2i spotNow = new Vector2i();
    private static final ClientTooltipPositioner FIXED = (sw, sh, mx, my, tw, th) -> spotNow;

    private static volatile int[] box;
    private static volatile long notedAt;

    /**
     * Wynnventory is about to draw its box: drawn here instead, at the left edge; true to stop
     * Wynnventory's own drawing. False with nothing to draw, or while already drawing.
     */
    public static boolean take(GuiGraphics g, int mouseX, int mouseY, List<Component> vanilla, List<Component> custom) {
        if (drawing || g == null || custom == null || custom.isEmpty()) {
            return false;
        }
        drawing = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            List<ClientTooltipComponent> comps = new ArrayList<>(custom.size());
            int w = 0;
            int h = 0;
            for (Component c : custom) {
                ClientTooltipComponent cc = ClientTooltipComponent.create(c.getVisualOrderText());
                comps.add(cc);
                w = Math.max(w, cc.getWidth(font));
                h += cc.getHeight(font);
            }
            int screenH = mc.getWindow().getGuiScaledHeight();
            float[] spot = anchored(w, h, screenH);
            float s = spot[2];
            int fx = Math.round(spot[0] / s);
            int fy = Math.round(spot[1] / s);
            spotNow.set(fx, fy);
            var pose = g.pose();
            pose.pushMatrix();
            pose.scale(s, s);
            try {
                g.renderTooltip(font, comps, fx, fy, FIXED, null);
            } finally {
                pose.popMatrix();
            }
            note(Math.round(spot[0] - FRAME * s), Math.round(spot[1] - FRAME * s),
                    Math.round((w + 2 * FRAME) * s), Math.round((h + 2 * FRAME) * s));
            return true;
        } catch (Throwable t) {
            return false;                       // Wynnventory then draws as usual
        } finally {
            drawing = false;
        }
    }

    /** Whether the box is being drawn now: its place is ours, not the tooltip fitting's. */
    public static boolean drawing() {
        return drawing;
    }

    /** The positioner that keeps the box where we put it. */
    public static ClientTooltipPositioner fixed() {
        return FIXED;
    }

    /**
     * Where the box's text goes at the screen's left edge, level with the screen's middle:
     * {@code {x, y, scale}}, the box {@code w} × {@code h} shrunk to the screen's height like
     * Wynnventory does for its anchored box.
     */
    static float[] anchored(int w, int h, int screenH) {
        float s = h + 10 > screenH ? screenH / (float) (h + 10) : 1f;
        float bh = (h + 2 * FRAME) * s;
        float by = Math.max(2, (screenH - bh) / 2f);
        return new float[] {2 + FRAME * s, by + FRAME * s, s};
    }

    // ---- keeping the tooltip out from under the box --------------------------------------

    /** The price box this frame: its top-left and size in screen GUI units, frame included. */
    public static void note(int x, int y, int w, int h) {
        box = new int[] {x, y, w, h};
        notedAt = System.nanoTime();
    }

    /** Tests: no box. */
    public static void forget() {
        box = null;
    }

    /** Whether a price box was put on the screen just now (this tooltip's frame). */
    public static boolean fresh() {
        return box != null && System.nanoTime() - notedAt <= FRESH_NANOS;
    }

    /**
     * Where our tooltip's left edge should be so it does not meet the price box: {@code x} as
     * placed when there is no box, or none this frame, or they do not meet; else the mouse's other
     * side when that fits the screen, else just past the box, else {@code x} after all. All in
     * screen GUI units; {@code sw}/{@code sh} the tooltip's drawn size, {@code edge} the frame
     * around it, {@code gap} the distance from the mouse.
     */
    static float avoid(float x, float y, float sw, float sh, int mouseX, int gap, float edge, int screenW) {
        return avoid(box, System.nanoTime() - notedAt, x, y, sw, sh, mouseX, gap, edge, screenW);
    }

    static float avoid(int[] b, long age, float x, float y, float sw, float sh, int mouseX, int gap, float edge, int screenW) {
        if (b == null || age > FRESH_NANOS || !hits(b, x - edge, y - edge, sw + 2 * edge, sh + 2 * edge)) {
            return x;
        }
        float right = mouseX + gap;
        float left = mouseX - gap - sw;
        float other = Math.abs(x - right) <= Math.abs(x - left) ? left : right;
        float beyond = b[0] + b[2] + gap;
        float before = b[0] - gap - sw;
        for (float c : new float[] {other, beyond, before}) {
            if (c >= edge && c + sw + edge <= screenW && !hits(b, c - edge, y - edge, sw + 2 * edge, sh + 2 * edge)) {
                return c;
            }
        }
        return x;
    }

    private static boolean hits(int[] b, float x, float y, float w, float h) {
        return x < b[0] + b[2] && b[0] < x + w && y < b[1] + b[3] && b[1] < y + h;
    }
}
