package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Wyn;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * A quest's places on Wynntils' own map (its tiles, through {@link Wyn}): fitted to the places,
 * under a dark veil in a soft frame; each place a dot with a short leader to its number chip in
 * free space (merged when close, "5.1–5.3"); the player as an arrow when near, else a pill on the
 * border pointing at them; a place name or two. Nothing leaves the frame. Without tiles nothing is
 * drawn and {@link #draw} says so, so the page lays out without the map.
 */
final class QuestMap extends MenuPaint {

    /** A place to mark: its number ("5.2"), where, its task (the tooltip) and its state (QuestMapLayout.NOW …). */
    record Place(String num, int[] at, String task, int state) {}

    private static final int OCEAN = 0xFF1B2A31;         // where Wynntils has no tile: a calm dark sea
    /** The tiles drawn at ~63 % brightness: the dark wash on the map itself, so nothing can draw it away. */
    private static final int TINT = 0xFFA2A098;
    static final int DARK = 0xFF1A130C;
    static final int GOLD = 0xFFE0AE3E;
    static final int PALE = 0xFFFFE071;
    static final int CREAM = 0xFFF3E6C4;
    static final int FADED = 0xFF8C7B58;
    static final int START = 0xFF5FA8E8;
    static final int YOU = 0xFF2F6FB0;
    static final int CHIP_H = 11;
    /** The zoom the + and − buttons and the wheel set: 1 fits the places, larger shows less. */
    double zoom = 1;
    /** Panned or zoomed by hand: the view stays put until the quest or its stage changes, or ⌖. */
    private boolean pinned;
    private double vx0;
    private double vz0;
    private double vspan = 1;
    private String viewKey = "";
    /** The places' box and a margin: how far a pan may go. */
    private double[] bounds;
    /** A press on the map: held until it moves (a pan) or is let go (a click on what is under it). */
    private net.minecraft.client.input.MouseButtonEvent press;
    private double lastX;
    private double lastY;
    private boolean moved;
    /** The + − ⌖ buttons this frame: a press there is a click, not a pan. */
    private int[] buttonsRect;
    /** The place under the mouse this frame (the list's row lights with it); null for none. */
    String hovered;
    /** Where the map was drawn this frame, for the wheel; null when it was not. */
    int[] rect;

    QuestMap(RoadmapScreen menu) {
        super(menu);
        marks = new QuestMapMarks(menu, this);
    }

    private final QuestMapMarks marks;

    /** Whether Wynntils has map tiles around these places (the page leaves the map out without them). */
    static boolean available(List<Place> places) {
        if (places.isEmpty()) {
            return false;
        }
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Place p : places) {
            minX = Math.min(minX, p.at()[0]);
            maxX = Math.max(maxX, p.at()[0]);
            minZ = Math.min(minZ, p.at()[2]);
            maxZ = Math.max(maxZ, p.at()[2]);
        }
        float x0 = (float) minX;
        float z0 = (float) minZ;
        float x1 = (float) maxX + 1;
        float z1 = (float) maxZ + 1;
        return QuestsCache.kept("map|" + x0 + "|" + z0 + "|" + x1 + "|" + z1, 30_000, () -> Wyn.hasMap(x0, z0, x1, z1));
    }

    /**
     * The map in x, y, w, h; {@code lit} is the place the list's hovered row stands for. Returns
     * false (nothing drawn) when Wynntils has no tiles there.
     */
    boolean draw(GuiGraphics g, int x, int y, int w, int h, List<Place> places, String lit, boolean buttons, int mx, int my) {
        return draw(g, x, y, w, h, places, lit, buttons, "", mx, my);
    }

    /**
     * The map as above for {@code key} (the quest, its stage and the view): a new key fits the
     * places again; within one, a pan or a zoom by hand stays.
     */
    boolean draw(GuiGraphics g, int x, int y, int w, int h, List<Place> places, String lit, boolean buttons, String key,
                 int mx, int my) {
        rect = null;
        hovered = null;
        buttonsRect = null;
        places = QuestMapLayout.remaining(places, Place::state);
        if (!key.equals(viewKey)) {
            viewKey = key;
            pinned = false;
            zoom = 1;
        }
        if (places.isEmpty() || w < 60 || h < 40) {
            return false;
        }
        // only places on Wynntils' map shape the view; the stage you are on first
        List<Place> mapped = new ArrayList<>();
        for (Place p : places) {
            if (QuestsCache.tile(p.at()[0], p.at()[2])) {
                mapped.add(p);
            }
        }
        if (mapped.isEmpty()) {
            return false;
        }
        List<Place> focus = new ArrayList<>();
        for (Place p : mapped) {
            if (p.state() == QuestMapLayout.NOW || p.state() == QuestMapLayout.START) {
                focus.add(p);
            }
        }
        if (focus.isEmpty()) {
            focus = mapped;
        }
        List<double[]> xz = new ArrayList<>();
        for (Place p : focus) {
            xz.add(new double[] {p.at()[0], p.at()[2]});
        }
        var pl = Minecraft.getInstance().player;
        double[] me = pl == null ? null : new double[] {pl.getX(), pl.getZ()};
        QuestMapLayout.Fit f = QuestMapLayout.fit(xz, me, w, h, zoom);
        if (pinned) {
            boolean near = me != null && QuestMapLayout.onMap((me[0] - vx0) / vspan, (me[1] - vz0) / vspan, w, h);
            f = new QuestMapLayout.Fit(vx0, vz0, vspan, near);
        } else {
            vx0 = f.x0();
            vz0 = f.z0();
            vspan = f.span();
        }
        double bx0 = Double.MAX_VALUE;
        double bz0 = Double.MAX_VALUE;
        double bx1 = -Double.MAX_VALUE;
        double bz1 = -Double.MAX_VALUE;
        for (Place p : mapped) {
            bx0 = Math.min(bx0, p.at()[0]);
            bz0 = Math.min(bz0, p.at()[2]);
            bx1 = Math.max(bx1, p.at()[0]);
            bz1 = Math.max(bz1, p.at()[2]);
        }
        bounds = new double[] {bx0 - 600, bz0 - 600, bx1 + 600, bz1 + 600};
        float x0 = (float) f.x0();
        float z0 = (float) f.z0();
        float x1 = (float) (f.x0() + f.span() * w);
        float z1 = (float) (f.z0() + f.span() * h);
        Kit.softCard(g, x - 2, y - 2, w + 4, h + 2);
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, OCEAN);              // off the map's tiles: a calm dark sea, not paper
        Wyn.drawMap(g, x0, z0, x1, z1, x, y, (float) (1 / f.span()), TINT);
        // Wynntils' tiles go through their own draw: what follows starts a new layer, so the veil,
        // the route and the markers always lie over them
        g.nextStratum();
        rect = new int[] {x, y, w, h};

        // what must stay clear: the zoom buttons, you on the border, the chips for places out of view
        List<double[]> avoid = new ArrayList<>();
        if (buttons) {
            avoid.add(new double[] {w - Theme.SP_3 - (Ui.CHIP_H + 2), Theme.SP_3, Ui.CHIP_H + 2, 3 * Ui.CHIP_H + 2 * Theme.SP_1});
        }
        double[] youBox = marks.youBox(w, h, f, me, places);
        if (youBox != null) {
            avoid.add(youBox);
        }
        List<QuestMapLayout.Marker> inside = new ArrayList<>();
        List<QuestMapLayout.Marker> outside = new ArrayList<>();
        for (Place p : places) {
            double px = f.px(p.at()[0]);
            double py = f.py(p.at()[2]);
            (QuestMapLayout.onMap(px, py, w, h) ? inside : outside).add(new QuestMapLayout.Marker(p.num(), px, py, p.state()));
        }
        List<QuestMapLayout.Group> groups = QuestMapLayout.merge(inside);
        List<double[]> dots = new ArrayList<>();
        for (QuestMapLayout.Group gr : groups) {
            dots.add(new double[] {gr.x(), gr.y()});
        }
        List<double[]> edges = marks.edgeChips(outside, w, h, avoid);
        avoid.addAll(edges);
        List<double[]> chips = new ArrayList<>();
        for (QuestMapLayout.Group gr : groups) {
            double cw = marks.chipWidth(gr);
            double[] at = QuestMapLayout.chip(gr.x(), gr.y(), cw, CHIP_H, w, h, Theme.SP_2, dots, chips, avoid);
            chips.add(new double[] {at[0], at[1], cw, CHIP_H});
        }
        marks.route(g, x, y, w, h, f, places);
        List<double[]> taken = new ArrayList<>(chips);
        taken.addAll(avoid);
        marks.names(g, x, y, w, h, x0, z0, x1, z1, f, dots, taken);
        for (int i = 0; i < groups.size(); i++) {
            QuestMapLayout.Group gr = groups.get(i);
            double[] c = chips.get(i);
            int dx = x + (int) Math.round(gr.x());
            int dy = y + (int) Math.round(gr.y());
            int bx = x + (int) Math.round(c[0]);
            int by = y + (int) Math.round(c[1]);
            int cw = (int) Math.round(c[2]);
            boolean over = mx >= bx && mx < bx + cw && my >= by && my < by + CHIP_H
                    || Math.abs(mx - dx) <= 4 && Math.abs(my - dy) <= 4;
            if (over) {
                hovered = gr.nums().get(0);
            }
            marks.marker(g, gr, dx, dy, bx, by, cw, over || lit != null && gr.nums().contains(lit));
            marks.hits(places, gr.nums(), bx, by, cw, CHIP_H);
            marks.hits(places, gr.nums(), dx - 4, dy - 4, 9, 9);
        }
        marks.drawEdges(g, x, y, outside, edges, places, lit, mx, my);
        marks.you(g, x, y, f, me, pl == null ? 0 : pl.getYRot(), youBox, places);
        g.disableScissor();
        int edge = 0x998A6A1C;
        Kit.hair(g, x, y, w, 1, edge);
        Kit.hair(g, x, y + h - 1, w, 1, edge);
        Kit.hair(g, x, y, 1, h, edge);
        Kit.hair(g, x + w - 1, y, 1, h, edge);
        if (buttons) {
            // inside the frame by SP_3 (its 1-px edge and SP_2 of map around them)
            int bx = x + w - Theme.SP_3 - (Ui.CHIP_H + 2);
            int by = y + Theme.SP_3;
            int step = Ui.CHIP_H + Theme.SP_1;
            Ui.iconButton(g, "plus", bx, by, false, mx, my);
            menu.hits.add(new MenuHit(bx, by, Ui.CHIP_H + 2, Ui.CHIP_H, null, null, "#qzoom:+"));
            Ui.iconButton(g, "minus", bx, by + step, false, mx, my);
            menu.hits.add(new MenuHit(bx, by + step, Ui.CHIP_H + 2, Ui.CHIP_H, null, null, "#qzoom:-"));
            int n = 2;
            if (pinned) {
                // back to the view that fits the places (a double-click does the same)
                Ui.iconButton(g, "target", bx, by + 2 * step, false, mx, my);
                menu.hits.add(new MenuHit(bx, by + 2 * step, Ui.CHIP_H + 2, Ui.CHIP_H,
                        null, List.of(L.c("wynnlabs.quests.map_fit").withStyle(ChatFormatting.GRAY)), "#qmapfit"));
                n = 3;
            }
            buttonsRect = new int[] {bx, by, Ui.CHIP_H + 2, n * step};
        }
        // the hand that says the map moves: a pointer over a marker, the move cursor elsewhere
        if (mx >= x && mx < x + w && my >= y && my < y + h && (buttonsRect == null || !in(mx, my, buttonsRect))) {
            g.requestCursor(hovered != null ? com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND
                    : com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
        }
        return true;
    }

    // ---- moving the map by hand ----

    /** Whether a press starts on the map (off its buttons): it is held to tell a pan from a click. */
    boolean press(net.minecraft.client.input.MouseButtonEvent e) {
        if (rect == null || !in(e.x(), e.y(), rect) || buttonsRect != null && in(e.x(), e.y(), buttonsRect)) {
            return false;
        }
        press = e;
        lastX = e.x();
        lastY = e.y();
        moved = false;
        return true;
    }

    /** The held press moving: past 3 px the map pans with it (the view then stays, see {@link #draw}). */
    boolean drag(double x, double y) {
        if (press == null) {
            return false;
        }
        if (!moved && Math.hypot(x - press.x(), y - press.y()) < 3) {
            return true;
        }
        moved = true;
        pinned = true;
        vx0 -= (x - lastX) * vspan;
        vz0 -= (y - lastY) * vspan;
        clamp();
        lastX = x;
        lastY = y;
        return true;
    }

    /** The press let go: a click when it never moved (the caller replays it on what is under it). */
    net.minecraft.client.input.MouseButtonEvent release() {
        net.minecraft.client.input.MouseButtonEvent e = press;
        press = null;
        return e != null && !moved ? e : null;
    }

    /** A step closer ({@code dir} above 0) or further, keeping the world point under (mx, my) where it is. */
    void zoomAt(double mx, double my, int dir) {
        if (rect == null) {
            return;
        }
        double ns = Math.max(QuestMapLayout.MIN_SPAN, Math.min(QuestMapLayout.MAX_SPAN, dir > 0 ? vspan / 1.5 : vspan * 1.5));
        double ox = mx - rect[0];
        double oy = my - rect[1];
        vx0 = vx0 + ox * vspan - ox * ns;
        vz0 = vz0 + oy * vspan - oy * ns;
        vspan = ns;
        pinned = true;
        clamp();
    }

    /** Back to the view that fits the places. */
    void refit() {
        pinned = false;
        zoom = 1;
    }

    /** Keeps the view's middle inside the places' box and its margin. */
    private void clamp() {
        if (bounds == null || rect == null) {
            return;
        }
        double cx = Math.max(bounds[0], Math.min(bounds[2], vx0 + vspan * rect[2] / 2));
        double cz = Math.max(bounds[1], Math.min(bounds[3], vz0 + vspan * rect[3] / 2));
        vx0 = cx - vspan * rect[2] / 2;
        vz0 = cz - vspan * rect[3] / 2;
    }

    /** One step of the + / − buttons: closer or further around the map's middle. */
    void zoomBy(int dir) {
        if (rect != null) {
            zoomAt(rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0, dir);
        }
    }

}
