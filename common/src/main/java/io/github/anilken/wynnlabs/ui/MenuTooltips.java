package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * The "Tooltip size in menus" setting: while a menu is open (inventory, bank, a shop, any
 * container screen) tooltips are drawn at the chosen size, so the advice lines under them still
 * fit on screen. Elsewhere, and at 100%, nothing changes.
 *
 * <p>Hooked through Wynntils' tooltip positioning ({@code WynntilsEvents.Tooltips}): Wynntils
 * asks for a positioner before it places a tooltip, and its own fitting (universal scale, fit to
 * screen) stands aside when one is set. So ours replaces that fitting in menus rather than
 * shrinking on top of it: the smaller of the two scales is drawn, and smaller still only when the
 * tooltip would not fit the window's height. The positioner places the scaled box beside the
 * mouse and inside the window, scales the pose and hands back that place in the pose's units, as
 * Wynntils' fitting does; {@link #end} undoes the pose once the tooltip is drawn.
 */
public final class MenuTooltips {

    /** The setting's key in {@link Settings#STEPS}. */
    static final String KEY = "menuTooltipScale";
    /** The gap between the mouse and the tooltip, as the game draws it. */
    static final int OFFSET = 12;
    /** The frame around the text (3) and a pixel of air: kept inside the window too. */
    static final int EDGE = 4;
    /** Never smaller than this, whatever the tooltip's length. */
    static final float MIN = 0.25f;

    private static final ScaledTooltipPositioner POSITIONER = new ScaledTooltipPositioner();

    private MenuTooltips() {}

    /** The size chosen for menus (0.7 … 1), or 1 when no menu is open. */
    static float wanted() {
        int pct = Settings.stepValue(KEY);
        if (pct >= 100 || pct <= 0 || !(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)) {
            return 1f;
        }
        return pct / 100f;
    }

    /**
     * The positioner for the tooltip about to be placed, or null to leave it to Wynntils and the
     * game. {@code wynntils}: Wynntils' universal tooltip scale (1 when its fitting is off).
     */
    public static ClientTooltipPositioner positioner(GuiGraphics g, float wynntils) {
        // a Wynn Labs screen draws its tooltip at full size on its pixel grid, in place of
        // Wynntils' fitting (which shrinks tall tooltips by any fraction and blurs them)
        int grid = menuGrid();
        if (grid > 0) {
            POSITIONER.begin(g, 1f, 1f, grid);
            return POSITIONER;
        }
        float s = wanted();
        if (s >= 1f) {
            return null;
        }
        POSITIONER.begin(g, s, wynntils > 0 ? wynntils : 1f, 0);
        return POSITIONER;
    }

    /**
     * Window pixels per tooltip pixel while one of the mod's own screens places its tooltip: the
     * menu scale while a scaled menu draws it, the game's GUI scale otherwise; 0 on other screens.
     */
    static int menuGrid() {
        if (MenuScale.tooltipWidth() > 0) {
            return MenuScale.effective();
        }
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof RoadmapScreen || mc.screen instanceof BoxScreen
                || mc.screen instanceof GuideScreen || mc.screen instanceof UpdateScreen) {
            return Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
        }
        return 0;
    }

    /**
     * {@code t} rounded down so one tooltip pixel is a whole number of window pixels when a GUI
     * pixel is {@code grid} window pixels (0: left as it is): text stays crisp, never blurred by
     * a scale between pixels.
     */
    static float onGrid(float t, int grid) {
        if (grid <= 0) {
            return t;
        }
        return Math.max(1, (int) Math.floor(t * grid + 1e-4f)) / (float) grid;
    }

    /** The tooltip is drawn: its pose back as it was. */
    public static void end(GuiGraphics g) {
        POSITIONER.end(g);
    }

    /**
     * The scale drawn: the smaller of ours and Wynntils' (never both multiplied), and smaller
     * still when the tooltip, frame included, would be taller than the window.
     */
    static float scale(float ours, float wynntils, int tooltipH, int screenH) {
        float t = Math.min(ours, wynntils);
        int box = tooltipH + 2 * EDGE;
        if (box * t > screenH) {
            t = screenH / (float) box;
        }
        return Math.max(MIN, t);
    }

    /**
     * Where the scaled tooltip's text starts, in GUI pixels, into {@code out}: beside the mouse as
     * the game places it, flipped to the left at the window's right edge, and moved up or down so
     * the scaled box sits inside the window.
     */
    static void place(int screenW, int screenH, int mouseX, int mouseY, int w, int h, float t, float[] out) {
        place(screenW, screenH, mouseX, mouseY, w, h, t, OFFSET, out);
    }

    /** As above with the gap from the mouse given ({@code gap}, in the caller's units). */
    static void place(int screenW, int screenH, int mouseX, int mouseY, int w, int h, float t, int gap, float[] out) {
        float sw = w * t;
        float sh = h * t;
        float edge = EDGE * t;
        float x = mouseX + gap;
        if (x + sw + edge > screenW) {
            x = Math.max(mouseX - gap - sw, edge);
        }
        float y = mouseY - gap;
        if (y + sh + edge > screenH) {
            y = screenH - sh - edge;
        }
        out[0] = x;
        out[1] = Math.max(y, edge);
    }

    /** {@code v} GUI pixels moved onto the nearest window pixel, so the text starts on the pixel grid. */
    static float snap(float v, double guiScale) {
        return guiScale > 0 ? (float) (Math.round(v * guiScale) / guiScale) : v;
    }

    /**
     * One positioner for every tooltip (render thread only, nothing allocated per frame). It works
     * the way Wynntils' own fitting does, so mods that wrap a positioner to make room beside the
     * tooltip (WynnChaYuan, for Wynnventory's price box) move it correctly: the pose is scaled,
     * and the place handed back is in the scaled pose's units. Those mods read the scale from a
     * {@code ScaledTooltipPositioner}'s {@code scaleFactor}, hence the class and field names.
     */
    private static final class ScaledTooltipPositioner implements ClientTooltipPositioner {

        private final float[] at = new float[2];
        private final Vector2i pos = new Vector2i();
        private GuiGraphics g;
        private float ours;
        private float wynntils;
        private int grid;
        private boolean pushed;
        /** The last scale drawn; read by other mods before the next tooltip is placed. */
        @SuppressWarnings("unused")
        private float scaleFactor = 1f;

        void begin(GuiGraphics g, float ours, float wynntils, int grid) {
            end(this.g);                            // a tooltip whose end never came: its pose first
            this.g = g;
            this.ours = ours;
            this.wynntils = wynntils;
            this.grid = grid;
        }

        @Override
        public Vector2ic positionTooltip(int screenW, int screenH, int mouseX, int mouseY, int w, int h) {
            float t = onGrid(scale(ours, wynntils, h, screenH), grid);
            scaleFactor = t;
            // in a menu the gap stays the game's 12 GUI px on screen, in whole menu pixels
            place(screenW, screenH, mouseX, mouseY, w, h, t, Math.round(OFFSET * MenuScale.tooltipGap()), at);
            pos.set(Math.round(at[0] / t), Math.round(at[1] / t));
            double gs = grid > 0 ? grid : Minecraft.getInstance().getWindow().getGuiScale();
            var pose = g.pose();
            pose.pushMatrix();
            // what rounding to whole scaled units lost, so the text still starts on a window pixel
            pose.translate(snap(at[0], gs) - pos.x * t, snap(at[1], gs) - pos.y * t);
            pose.scale(t, t);
            pushed = true;
            return pos;
        }

        void end(GuiGraphics g) {
            if (pushed && g != null && g == this.g) {
                g.pose().popMatrix();
            }
            pushed = false;
            this.g = null;
        }
    }
}
