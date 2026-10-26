package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * One tab of the menu ({@link RoadmapScreen}): it draws its page and takes the clicks, wheel and
 * keys meant for it. The screen keeps the window, the rail and what the tabs share (the hover
 * areas, the flash line, the road map); a tab keeps its own view state. Made with each screen, so
 * the view starts afresh every time the menu opens.
 */
abstract class MenuTab extends MenuPaint {

    MenuTab(RoadmapScreen menu) {
        super(menu);
    }

    /** The text boxes this tab types into, made again on each {@code init} (a new font, a resize). */
    List<EditBox> init() {
        return List.of();
    }

    /** The start of every frame, whichever tab shows: last frame's click areas forgotten. */
    void beginFrame() {
    }

    /** The page, in the area under its title. */
    abstract void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt);

    /**
     * Something on the right of the page title (left of {@code right}, at most {@code room} wide);
     * returns the width it took, 0 for nothing.
     */
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        return 0;
    }

    /** A drag with the button held (a scroll bar's thumb follows it); true when the tab took it. */
    boolean barDragged(double x, double y) {
        return false;
    }

    /** The mouse button let go: a scroll bar's drag ends. */
    void barReleased() {
    }

    /** A click on the page (logical pixels); true when the tab took it. */
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    /**
     * Whether a click the tab did not take goes on to the menu's shared click areas (copy, track,
     * the compass, the {@code #q} actions); false sends it straight to the screen's widgets.
     */
    boolean sharedClicks() {
        return true;
    }

    /** The mouse wheel over the page (logical pixels, {@code v} up positive). */
    void mouseScrolled(double x, double y, double v) {
    }

    /**
     * What this tab has waiting, for its rail entry: a count, the tracked quest's pin or a dot;
     * null for nothing. Asked every two seconds, not each frame (the rail caches it).
     */
    MenuRail.Badge badge() {
        return null;
    }

    // ---- popups: an open one owns the mouse ----

    /**
     * A tab's popups (menus under a chip, a list over the page) as one object. While one is open
     * the screen draws the page with the mouse away (no hover, no tooltips, no click areas), then
     * {@link #draw} with the real mouse; a click outside only closes it ({@link #close}), the wheel
     * and the keys go to it, never to the page under it.
     */
    interface Popup {
        boolean open();

        /** Draws the open popups; adds each one's box to {@code boxes}. */
        void draw(GuiGraphics g, int mx, int my, List<int[]> boxes);

        /** A click outside every popup (or Esc): they close, nothing else happens. */
        void close();

        /** A click inside a popup before its click areas get it (a scroll bar); true when taken. */
        default boolean clicked(MouseButtonEvent event) {
            return false;
        }

        /** The wheel over an open popup. */
        default void scrolled(double x, double y, double v) {
        }

        /** Up, down, Enter while open; true when taken. */
        default boolean key(int key) {
            return false;
        }
    }

    /** This tab's popups, or null when it has none. */
    Popup popup() {
        return null;
    }

    final boolean popupOpen() {
        return popup() != null && popup().open();
    }

    final void drawPopup(GuiGraphics g, int mx, int my, List<int[]> boxes) {
        popup().draw(g, mx, my, boxes);
    }

    final void closePopup() {
        popup().close();
    }

    final boolean popupClicked(MouseButtonEvent event) {
        return popup().clicked(event);
    }

    final void popupScrolled(double x, double y, double v) {
        popup().scrolled(x, y, v);
    }

    final boolean popupKey(int key) {
        return popup().key(key);
    }

    /** A key press the menu did not take first; true when the tab took it. */
    boolean keyPressed(int key) {
        return false;
    }
}
