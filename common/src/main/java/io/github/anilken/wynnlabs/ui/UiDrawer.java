package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * A drawer: a full view slid in from the page's right over a dimmed page (the tree's "Tümü ›" for
 * the damage and the deaths). It is a popup ({@link MenuTab.Popup}): it owns the mouse, Esc, its ✕
 * or a click outside close it. Its content (a {@link Content} that draws from a y and returns its
 * height) scrolls inside with a draggable bar. An optional {@link Side} (the deaths' map) is pinned
 * beside it, the full height of the drawer's body, and never scrolls: on a compact window it sits
 * above the list instead. The side takes its own presses, drags and wheel.
 */
final class UiDrawer implements MenuTab.Popup {

    /** What a drawer shows: drawn from x, y across w (scrolled by the drawer); returns its height. */
    interface Content {
        int draw(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my);
    }

    /** A fixed panel beside the content (a map): drawn in its box, with its own mouse. */
    interface Side {
        /** Whether it has anything to show (a map needs Wynntils' tiles); when not, the content takes the body. */
        default boolean shown() {
            return true;
        }

        void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my);

        /** A press inside it; true when it took the press (held for a pan, or a button). */
        default boolean press(MouseButtonEvent e, boolean doubleClick) {
            return false;
        }

        /** The held press moving; true while it is the side's. */
        default boolean drag(double x, double y) {
            return false;
        }

        /** The press let go: a click that never moved, for the drawer's click areas; null for none. */
        default MouseButtonEvent release() {
            return null;
        }

        /** The wheel over it (a map zooms at the pointer). */
        default void wheel(double x, double y, double v) {
        }

        /** A click on the content (a row): the side may follow it (the map pans to that death). */
        default void contentClicked(double x, double y) {
        }
    }

    /** The parchment's own edge inside the page panel (its torn border): the drawer keeps clear of it. */
    private static final int PAPER_EDGE = 4;

    /** A wider drawer (a pool's columns): 74% of the page instead of 62%. */
    private boolean wide;

    /** The share of the body the side takes beside the content, in percent. */
    static final int SIDE_SHARE = 46;

    /**
     * The menu it draws in: the screen open now. A tab that lives longer than one screen (Endgame,
     * Crafting) keeps its drawer, and the menu opened again (or at another size, another scale) is
     * a new screen: its page and its scale, not the old one's, or the dim and the panel land off
     * the page.
     */
    private RoadmapScreen menu;
    private String title;
    private Content content;
    private Side side;
    private boolean open;
    private int scroll;
    private int contentH;
    private final ScrollBar bar = new ScrollBar();
    /** The page it covers, the panel, its ✕, this frame. */
    private int[] page;
    private int[] panel;
    private int[] close;
    /** The content's box (for the wheel and the content's own areas), and the side's. */
    int[] body;
    int[] sideBox;

    UiDrawer(RoadmapScreen menu) {
        this.menu = menu;
    }

    /** Opens on {@code content} under {@code title}, from the top. */
    void show(String title, Content content) {
        show(title, content, null);
    }

    /** Opens on {@code content} with {@code side} pinned beside it. */
    void show(String title, Content content, Side side) {
        this.wide = false;
        this.title = title;
        this.content = content;
        this.side = side;
        open = true;
        scroll = 0;
    }

    /** Opens wide (74% of the page): for content in columns. */
    void showWide(String title, Content content) {
        show(title, content, null);
        wide = true;
    }

    boolean showing() {
        return open;
    }

    /** The page it slides over (set each frame by its tab). */
    void page(int x, int y, int w, int h) {
        page = new int[] {x, y, w, h};
    }

    @Override
    public boolean open() {
        return open && page != null;
    }

    @Override
    public void close() {
        open = false;
    }

    /**
     * The body split between the scrolling content and the fixed side: {content, side} boxes
     * {x, y, w, h}. Wide: the side on the right, SP_6 apart, the body's whole height; narrow: the
     * side on top ({@code SIDE_SHARE} of the height), the content under it after SP_4.
     */
    static int[][] split(int x, int y, int w, int h, boolean narrow) {
        if (narrow) {
            int sh = h * SIDE_SHARE / 100;
            return new int[][] {{x, y + sh + Theme.SP_4, w, h - sh - Theme.SP_4}, {x, y, w, sh}};
        }
        int sw = (w - Theme.SP_6) * SIDE_SHARE / 100;
        int cw = w - Theme.SP_6 - sw;
        return new int[][] {{x, y, cw, h}, {x + cw + Theme.SP_6, y, sw, h}};
    }

    @Override
    public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof RoadmapScreen rs) {
            menu = rs;
        }
        Font font = menu.font();
        int px = page[0];
        int py = page[1];
        int pw = page[2];
        int ph = page[3];
        // the dim: the whole parchment of the page, title row included, one rectangle (the rail and
        // the frame stay as they are); the drawer sits inside it SP_4 from its inner edge
        int[] pp = menu.pagePanel != null ? menu.pagePanel : new int[] {px - Theme.SP_6, py - Theme.SP_6, pw + 2 * Theme.SP_6, ph + Theme.SP_6 + Theme.SP_4};
        g.fill(pp[0], pp[1], pp[0] + pp[2], pp[1] + pp[3], 0x66201810);
        boolean narrow = menu.layout.compact();
        int edge = PAPER_EDGE + Theme.SP_4;
        int dw = narrow ? pp[2] - 2 * edge : Math.min(pp[2] - 2 * edge, pw * (wide || side != null && side.shown() ? 74 : 62) / 100);
        int dx = pp[0] + pp[2] - edge - dw;
        int dy = pp[1] + edge;
        int dh = pp[3] - 2 * edge;
        // a soft shadow on the dimmed page, then an opaque body under the card (its sprite is a touch
        // translucent: a lit row of the page must not show through), then the card
        Kit.windowShadow(g, dx, dy, dw, dh);
        g.fill(dx + 4, dy + 4, dx + dw - 4, dy + dh - 3, 0xFFEADCB4);
        Kit.softCard(g, dx, dy, dw, dh);
        panel = new int[] {dx, dy, dw, dh};
        boxes.add(panel);
        int pad = Theme.SP_5;
        Type.HEADING.draw(g, font, Type.HEADING.fit(font, title, dw - 2 * pad - 20), dx + pad, dy + pad);
        int sq = Ui.CHIP_H + 2;
        int cx = dx + dw - pad - sq;
        Ui.iconButton(g, "x", cx, dy + pad - 1, false, mx, my);
        close = new int[] {cx, dy + pad - 1, sq, Ui.CHIP_H};
        int top = dy + pad + Type.HEADING.lineHeight + Theme.SP_4;
        int bottom = dy + dh - pad;
        int[] list = {dx + pad, top, dw - 2 * pad, bottom - top};
        sideBox = null;
        if (side != null && side.shown()) {
            int[][] parts = split(dx + pad, top, dw - 2 * pad, bottom - top, narrow);
            list = parts[0];
            sideBox = parts[1];
        }
        body = list;
        int lTop = list[1];
        int lBottom = list[1] + list[3];
        int max = Math.max(0, contentH - list[3]);
        scroll = Math.max(0, Math.min(scroll, max));
        int cw = max > 0 ? list[2] - ScrollBar.W - Theme.SP_2 : list[2];
        int hitsFrom = menu.hits.size();
        g.enableScissor(list[0] - Theme.SP_2, lTop, list[0] + list[2], lBottom);
        contentH = content.draw(g, list[0], lTop - scroll, cw, lTop, lBottom, mx, my);
        g.disableScissor();
        // what scrolled out of the list takes no click and shows no tooltip
        menu.hits.subList(hitsFrom, menu.hits.size()).removeIf(h -> h.y() + h.h() <= lTop || h.y() >= lBottom);
        bar.place(list[0] + list[2] - ScrollBar.W, lTop, list[3], list[3], contentH, scroll);     // in pixels
        bar.draw(g, mx, my);
        if (sideBox != null) {
            side.draw(g, sideBox[0], sideBox[1], sideBox[2], sideBox[3], mx, my);
        }
    }

    @Override
    public boolean clicked(MouseButtonEvent event) {
        return clicked(event, false);
    }

    @Override
    public boolean clicked(MouseButtonEvent event, boolean doubleClick) {
        if (close != null && MenuPaint.in(event.x(), event.y(), close)) {
            close();
            return true;
        }
        if (bar.mouseClicked(event.x(), event.y())) {
            scroll = bar.offset();
            return true;
        }
        if (sideBox != null && MenuPaint.in(event.x(), event.y(), sideBox)) {
            return side.press(event, doubleClick);
        }
        if (side != null && body != null && MenuPaint.in(event.x(), event.y(), body)) {
            side.contentClicked(event.x(), event.y());
        }
        return false;
    }

    @Override
    public void scrolled(double x, double y, double v) {
        if (sideBox != null && MenuPaint.in(x, y, sideBox)) {
            side.wheel(x, y, v);                            // over the map: it zooms, the list stays
            return;
        }
        scroll = Math.max(0, scroll - (int) Math.signum(v) * Theme.SP_7 * 2);
    }

    /** The bar's thumb or the side's held press, dragged. */
    boolean drag(double x, double y) {
        if (bar.mouseDragged(y)) {
            scroll = bar.offset();
            return true;
        }
        return side != null && open && side.drag(x, y);
    }

    /** The button let go: a click the side held and never moved, for the drawer's click areas; null for none. */
    MouseButtonEvent release() {
        bar.mouseReleased();
        return side == null ? null : side.release();
    }
}
