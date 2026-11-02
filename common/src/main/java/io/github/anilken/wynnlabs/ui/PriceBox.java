package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import org.joml.Vector2i;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Wynnventory's price box beside our item tooltip. Wynnventory draws the box next to where the
 * game's own tooltip would stand (that tooltip's size, at the mouse); our compact tooltip is
 * another size and lands elsewhere, so the box could sit under it. So, the way WynnChaYuan does
 * it: the box's lines are taken as Wynnventory is about to draw them ({@link #take}), and once
 * our tooltip is placed the box is drawn beside it ({@link #drawBeside}): to its right with a
 * gap, to its left when the right has no room. When no compact tooltip is shown for the item
 * the lines are handed back and Wynnventory draws as usual ({@link #release}); with its
 * "anchor tooltips" setting on (the box at the screen's left edge) nothing is taken either,
 * and our tooltip only keeps out from under the anchored box ({@link #avoid}). Nothing of
 * Wynnventory's is copied: its lines are drawn as the game draws any tooltip.
 */
public final class PriceBox {

    private PriceBox() {}

    /** A note older than this is from an earlier tooltip: ignored. */
    static final long FRESH_NANOS = 120_000_000L;
    /** Between our tooltip and the box, in screen GUI units (Wynnventory's own gap). */
    static final int GAP = 18;
    /** The frame the game draws around a tooltip. */
    static final int FRAME = 4;

    /** What Wynnventory was about to draw: the screen, the mouse, the item's lines and the price lines. */
    private record Taken(GuiGraphics g, int mouseX, int mouseY, List<Component> vanilla, List<Component> custom) {}

    private static Taken taken;
    /** Taken for our tooltip: drawn beside it once it is placed. */
    private static Taken ours;
    /** Where our tooltip was placed this frame (screen GUI units), for the box beside it. */
    private static final float[] placed = new float[4];
    private static boolean hasPlace;
    private static boolean passing;
    private static boolean drawing;

    private static volatile int[] box;
    private static volatile long notedAt;

    // ---- taking the box over --------------------------------------------------------------

    /**
     * Wynnventory is about to draw its box: true to stop it and draw the box ourselves later.
     * False while we are handing the lines back, with the box anchored, or with nothing to draw.
     */
    public static boolean take(GuiGraphics g, int mouseX, int mouseY, List<Component> vanilla, List<Component> custom) {
        if (passing || custom == null || custom.isEmpty() || anchored()) {
            return false;
        }
        taken = new Taken(g, mouseX, mouseY, vanilla, custom);
        return true;
    }

    /** Our compact tooltip is being shown for the item: its box is drawn beside ours. */
    public static void keepForOurs() {
        ours = taken;
        taken = null;
        hasPlace = false;
    }

    /** No compact tooltip for the item: Wynnventory draws the box where it meant to. */
    public static void release() {
        Taken t = taken;
        taken = null;
        if (t == null) {
            return;
        }
        passing = true;
        try {
            Method draw = Class.forName("com.wynnventory.util.RenderUtils").getMethod("drawTooltip",
                    GuiGraphics.class, int.class, int.class, List.class, List.class);
            draw.invoke(null, t.g(), t.mouseX(), t.mouseY(), t.vanilla(), t.custom());
        } catch (Throwable ignored) {
            // the box is lost for this frame; the next hover takes it again
        } finally {
            passing = false;
        }
    }

    /** Whether a box waits to be drawn beside our tooltip. */
    public static boolean waiting() {
        return ours != null;
    }

    /** Our tooltip's place and drawn size this frame, in screen GUI units. */
    static void placed(float x, float y, float sw, float sh) {
        placed[0] = x;
        placed[1] = y;
        placed[2] = sw;
        placed[3] = sh;
        hasPlace = true;
    }

    /** After our tooltip is drawn: the box beside it, once. */
    public static void drawBeside(GuiGraphics g) {
        Taken t = ours;
        if (t == null || drawing || !hasPlace) {
            return;
        }
        ours = null;
        drawing = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            List<ClientTooltipComponent> comps = new ArrayList<>(t.custom().size());
            int w = 0;
            int h = 0;
            for (Component c : t.custom()) {
                ClientTooltipComponent cc = ClientTooltipComponent.create(c.getVisualOrderText());
                comps.add(cc);
                w = Math.max(w, cc.getWidth(font));
                h += cc.getHeight(font);
            }
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();
            float[] spot = beside(placed[0], placed[1], placed[2], placed[3], w, h, screenW, screenH);
            if (spot == null) {
                return;
            }
            float s = spot[2];
            int fx = Math.round(spot[0] / s);
            int fy = Math.round(spot[1] / s);
            var pose = g.pose();
            pose.pushMatrix();
            pose.scale(s, s);
            try {
                g.renderTooltip(font, comps, fx, fy, (sw, sh, mx, my, tw, th) -> new Vector2i(fx, fy), null);
            } finally {
                pose.popMatrix();
            }
        } catch (Throwable ignored) {
            // the box must never break the tooltip
        } finally {
            drawing = false;
        }
    }

    /**
     * Where the box's text goes beside a tooltip at ({@code x}, {@code y}) of drawn size
     * {@code sw} × {@code sh}: {@code {x, y, scale}}, the box {@code w} × {@code h} shrunk to the
     * screen's height like Wynnventory does. To the right with the gap when that fits the screen,
     * else to the left; the top level with the tooltip's, kept on the screen. Null with no room.
     */
    static float[] beside(float x, float y, float sw, float sh, int w, int h, int screenW, int screenH) {
        float s = h + 10 > screenH ? screenH / (float) (h + 10) : 1f;
        float bw = (w + 2 * FRAME) * s;
        float bh = (h + 2 * FRAME) * s;
        float right = x + sw + FRAME + GAP;             // the box's frame starts here
        float left = x - FRAME - GAP - bw;
        float bx;
        if (right + bw <= screenW - 2) {
            bx = right;
        } else if (left >= 2) {
            bx = left;
        } else {
            float roomRight = screenW - 2 - right;
            float roomLeft = x - FRAME - GAP - 2;
            float room = Math.max(roomRight, roomLeft);
            if (room < 40) {
                return null;
            }
            s = Math.min(s, room / (w + 2 * FRAME));
            bw = (w + 2 * FRAME) * s;
            bh = (h + 2 * FRAME) * s;
            bx = roomRight >= roomLeft ? right : x - FRAME - GAP - bw;
        }
        float by = Math.max(2, Math.min(y - FRAME, screenH - 2 - bh));
        return new float[] {bx + FRAME * s, by + FRAME * s, s};
    }

    private static Method configGet;
    private static Method settingsGet;
    private static Method anchorGet;
    private static boolean noConfig;

    /** Wynnventory's "anchor tooltips" setting: the box pinned at the screen's left edge. */
    static boolean anchored() {
        if (noConfig) {
            return false;
        }
        try {
            if (anchorGet == null) {
                Class<?> config = Class.forName("com.wynnventory.core.config.ModConfig");
                configGet = config.getMethod("getInstance");
                settingsGet = config.getMethod("getTooltipSettings");
                anchorGet = settingsGet.getReturnType().getMethod("isAnchorTooltips");
            }
            return Boolean.TRUE.equals(anchorGet.invoke(settingsGet.invoke(configGet.invoke(null))));
        } catch (Throwable t) {
            noConfig = true;
            return false;
        }
    }

    // ---- keeping out from under an anchored box ------------------------------------------

    /** Wynnventory's price box this frame: its top-left and size in screen GUI units, frame included. */
    public static void note(int x, int y, int w, int h) {
        box = new int[] {x, y, w, h};
        notedAt = System.nanoTime();
    }

    /** Tests and the screen close: no box. */
    public static void forget() {
        box = null;
        taken = null;
        ours = null;
    }

    /** Whether Wynnventory put a price box on the screen just now (this tooltip's frame). */
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
