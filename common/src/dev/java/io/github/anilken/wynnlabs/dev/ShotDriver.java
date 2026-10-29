package io.github.anilken.wynnlabs.dev;

import io.github.anilken.wynnlabs.ui.MenuScale;
import io.github.anilken.wynnlabs.ui.RoadmapScreen;
import io.github.anilken.wynnlabs.ui.ShotHands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;

/**
 * Dev builds only: the screenshot tool's hands on the mod's own menu. Each view starts on a fresh
 * menu on its tab (every view inside it at its start), then takes its steps ({@link ShotRun#views}:
 * a sub-page, a drawer, a menu, a window of the mod) through {@link ShotHands}, which sits in the
 * menu's package and uses the same preview hooks the gametests do. It finds the element a tooltip
 * shot hovers, and puts the menu's shared views (the build window, the Endgame and Crafting pages,
 * the plan pill) back afterwards. It only shows the mod's own screens, never opens a game menu,
 * never starts a road map build or a build search.
 */
final class ShotDriver {

    private final Minecraft mc;
    /** The shared views as the run found them. */
    private final Object saved;
    /** The menu on screen, and the mod's window opened over it for a view (null: none). */
    private RoadmapScreen screen;
    private Screen window;

    ShotDriver(Minecraft mc) {
        this.mc = mc;
        saved = ShotHands.save();
    }

    // ---- the tabs ----

    /** The tabs' numbers in rail order. */
    static int[] railOrder() {
        return ShotHands.railOrder();
    }

    /** A tab's name in commands and file names: "home", "craft", "endgame" … (its language key's end). */
    static String tabName(int tab) {
        String key = ShotHands.tabKeys()[tab];
        int at = key.lastIndexOf("tab_");
        // the Mounts tab's sections are keyed "sec_mounts", "sec_road", "sec_places"
        return at >= 0 ? key.substring(at + 4) : key.substring(key.lastIndexOf("sec_") + 4);
    }

    static String[] tabNames() {
        String[] names = new String[ShotHands.tabKeys().length];
        for (int t = 0; t < names.length; t++) {
            names[t] = tabName(t);
        }
        return names;
    }

    /** The menu opened on Home. */
    void open() {
        show(ShotHands.menu(0));
    }

    /** Whether the menu, or the window the run opened over it, is still the screen. */
    boolean showing() {
        return mc.screen != null && (mc.screen == screen || window != null && mc.screen == window);
    }

    /** {@code t} as a fresh menu on it: every view inside it at its start, the shared views closed. */
    void select(int t) {
        ShotHands.close();
        show(ShotHands.menu(t));
    }

    private void show(RoadmapScreen s) {
        window = null;
        screen = s;
        mc.setScreen(s);
    }

    /** One step of a view ({@link ShotRun#views}); false when there is nothing to show for it. */
    boolean act(String step) {
        if (step.startsWith("window:")) {
            Screen w = ShotHands.window(screen, step.substring("window:".length()));
            if (w == null) {
                return false;
            }
            window = w;
            mc.setScreen(w);
            return true;
        }
        if (window != null) {
            return ShotHands.inWindow(window, step);
        }
        return ShotHands.act(screen, step);
    }

    /** After a view's steps: nothing focused on the menu or the window. */
    void settle() {
        ShotHands.settle(window != null ? window : screen);
    }

    /**
     * Why the frame drawn last is not the view asked for ({@code expected} things open over the
     * page), or null when it is: {@link ShotRun#NOTHING} when less is open (a menu with nothing to
     * list stays shut), else what is wrong (another screen, a field focused, something more open).
     */
    String unclean(int expected) {
        Screen want = window != null ? window : screen;
        if (mc.screen != want) {
            return "another screen is open";
        }
        if (ShotHands.focused(want)) {
            return "a field is focused";
        }
        int n = ShotHands.overlays(want);
        if (n < expected) {
            return ShotRun.NOTHING;
        }
        return n == expected ? null : n + " open over the page, " + expected + " expected";
    }

    // ---- what a tooltip shot hovers ----

    /**
     * The box of {@code part} drawn last frame in GUI pixels {x0, y0, x1, y1}, or null when it was
     * not drawn: the parts the menu reports to the tool, else the boxes the preview already reads.
     */
    float[] target(String part, Map<String, float[]> parts) {
        float[] r = parts.get(part);
        if (r != null) {
            return r;
        }
        int[] logical = ShotHands.part(screen, part);
        if (logical == null) {
            return null;
        }
        return ShotCrop.toGui(logical[0], logical[1], logical[2], logical[3], MenuScale.factor());
    }

    /** The shared views back as the run found them. */
    void restore() {
        ShotHands.restore(saved);
    }
}
