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
    /**
     * Whether its + − ⌖ buttons are the quests tab's ("#qzoom", "#qmapfit"); a map elsewhere (the
     * deaths drawer) takes their clicks itself through {@link #button}.
     */
    boolean questsButtons = true;
    /**
     * The stage a staged map (a quest's places, a secret's steps) shows: its places with chips and
     * route, the next stage's as outlined dots, nothing of the others. Set to the stage you are on
     * whenever the view's key changes (a new quest, the stage moved in game); the stepper moves it.
     */
    int focus = -1;
    private String focusKey = "";
    /** The stages that have places on this map (the stepper walks them), this frame. */
    private List<Integer> stages = List.of();
    /** The stepper's box this frame {x, y, w, h} and its two arrows' x. */
    private int[] stepRect;

    /** Whether the places are an ordered path (a quest's stages, a secret's steps): the dotted route joins them. */
    final boolean route;

    QuestMap(RoadmapScreen menu) {
        this(menu, true);
    }

    /** A map whose places are a path ({@code route}) or separate events (deaths: no route). */
    QuestMap(RoadmapScreen menu, boolean route) {
        super(menu);
        this.route = route;
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
        return QuestsCache.kept("map|" + x0 + "|" + z0 + "|" + x1 + "|" + z1, 3_000, () -> Wyn.hasMap(x0, z0, x1, z1));
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
        stepRect = null;
        places = QuestMapLayout.remaining(places, Place::state);
        if (!key.equals(viewKey)) {
            viewKey = key;
            pinned = false;
            zoom = 1;
        }
        List<Place> ghosts = List.of();
        if (route && !places.isEmpty()) {
            // a staged map: the focused stage only (the next one as outlined dots)
            java.util.TreeSet<Integer> have = new java.util.TreeSet<>();
            int cur = Integer.MAX_VALUE;
            for (Place p : places) {
                int s = QuestMapLayout.stageOf(p.num());
                have.add(s);
                if (p.state() == QuestMapLayout.NOW || p.state() == QuestMapLayout.START) {
                    cur = Math.min(cur, s);
                }
            }
            stages = new ArrayList<>(have);
            if (!key.equals(focusKey)) {
                focusKey = key;
                focus = cur == Integer.MAX_VALUE ? have.first() : cur;
            }
            if (!have.contains(focus)) {
                Integer up = have.ceiling(focus);
                focus = up != null ? up : have.last();       // a stage with no place on the map: the next that has one
            }
            QuestMapLayout.Focus<Place> fo = QuestMapLayout.focus(places, Place::num, focus, lit);
            places = fo.shown();
            ghosts = fo.ghosts();
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
        float[] cov = coverage();
        // a pan may go anywhere Wynntils has map (the places' box and a margin without it)
        bounds = cov != null ? new double[] {cov[0], cov[1], cov[2], cov[3]} : new double[] {bx0 - 600, bz0 - 600, bx1 + 600, bz1 + 600};
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
        boolean stepper = buttons && route && stages.size() > 1;
        String stepText = stepper ? L.t("wynnlabs.quests.stage_of", focus, stages.get(stages.size() - 1)) : "";
        int stepW = stepper ? 2 * (Ui.CHIP_H + 2) + Type.META.width(font(), stepText) + 2 * Theme.SP_3 : 0;
        if (stepper) {
            avoid.add(new double[] {Theme.SP_3, Theme.SP_3, stepW, Ui.CHIP_H});
        }
        double[] youBox = marks.youBox(w, h, f, me, places);
        if (youBox != null) {
            avoid.add(youBox);
        } else if (me != null && QuestMapLayout.onMap(f.px(me[0]), f.py(me[1]), w, h)) {
            avoid.add(new double[] {f.px(me[0]) - 7, f.py(me[1]) - 7, 15, 15});     // your arrow: no place name under it
        }
        List<QuestMapLayout.Marker> inside = new ArrayList<>();
        List<QuestMapLayout.Marker> outside = new ArrayList<>();
        for (Place p : places) {
            double px = f.px(p.at()[0]);
            double py = f.py(p.at()[2]);
            (QuestMapLayout.onMap(px, py, w, h) ? inside : outside).add(new QuestMapLayout.Marker(p.num(), px, py, p.state()));
        }
        List<QuestMapLayout.Group> groups = QuestMapLayout.merge(inside, route);
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
        for (Place p : ghosts) {
            // the next stage: an outlined dot, no chip
            double gx = f.px(p.at()[0]);
            double gy = f.py(p.at()[2]);
            if (QuestMapLayout.onMap(gx, gy, w, h)) {
                Art.box(g, x + (int) Math.round(gx) - 3, y + (int) Math.round(gy) - 3, 7, 7, 3, 0, CREAM);
            }
        }
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
            menu.hits.add(new MenuHit(bx, by, Ui.CHIP_H + 2, Ui.CHIP_H, null, null, questsButtons ? "#qzoom:+" : null));
            Ui.iconButton(g, "minus", bx, by + step, false, mx, my);
            menu.hits.add(new MenuHit(bx, by + step, Ui.CHIP_H + 2, Ui.CHIP_H, null, null, questsButtons ? "#qzoom:-" : null));
            int n = 2;
            if (pinned) {
                // back to the view that fits the places (a double-click does the same)
                Ui.iconButton(g, "target", bx, by + 2 * step, false, mx, my);
                menu.hits.add(new MenuHit(bx, by + 2 * step, Ui.CHIP_H + 2, Ui.CHIP_H,
                        null, List.of(L.c("wynnlabs.quests.map_fit").withStyle(ChatFormatting.GRAY)), questsButtons ? "#qmapfit" : null));
                n = 3;
            }
            buttonsRect = new int[] {bx, by, Ui.CHIP_H + 2, n * step};
        }
        if (stepper) {
            // "◀ Aşama 5 / 14 ▶" in the top left, inside the frame like the zoom buttons
            int sx = x + Theme.SP_3;
            int sy = y + Theme.SP_3;
            int aw = Ui.CHIP_H + 2;
            Ui.iconButton(g, "chevron-left", sx, sy, false, mx, my);
            menu.hits.add(new MenuHit(sx, sy, aw, Ui.CHIP_H, null, null, questsButtons ? "#qstage:-" : null));
            int lx = sx + aw;
            int lw = stepW - 2 * aw;
            Kit.pill(g, lx, sy, lw, Ui.CHIP_H, false, false);
            Type.META.draw(g, font(), stepText, lx + Theme.SP_3, Type.META.yIn(sy, Ui.CHIP_H), Theme.INK);
            Ui.iconButton(g, "chevron-right", lx + lw, sy, false, mx, my);
            menu.hits.add(new MenuHit(lx + lw, sy, aw, Ui.CHIP_H, null, null, questsButtons ? "#qstage:+" : null));
            stepRect = new int[] {sx, sy, stepW, Ui.CHIP_H};
        }
        // the hand that says the map moves: a pointer over a marker, the move cursor elsewhere
        if (mx >= x && mx < x + w && my >= y && my < y + h && (buttonsRect == null || !in(mx, my, buttonsRect))
                && (stepRect == null || !in(mx, my, stepRect))) {
            g.requestCursor(hovered != null ? com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND
                    : com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
        }
        return true;
    }

    // ---- moving the map by hand ----

    /** Whether a press starts on the map (off its buttons): it is held to tell a pan from a click. */
    boolean press(net.minecraft.client.input.MouseButtonEvent e) {
        if (rect == null || !in(e.x(), e.y(), rect) || buttonsRect != null && in(e.x(), e.y(), buttonsRect)
                || stepRect != null && in(e.x(), e.y(), stepRect)) {
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
        double most = QuestMapLayout.handMaxSpan(coverage(), rect[2], rect[3]);
        double ns = Math.max(QuestMapLayout.HAND_MIN_SPAN, Math.min(most, dir > 0 ? vspan / 1.5 : vspan * 1.5));
        double ox = mx - rect[0];
        double oy = my - rect[1];
        vx0 = vx0 + ox * vspan - ox * ns;
        vz0 = vz0 + oy * vspan - oy * ns;
        vspan = ns;
        pinned = true;
        clamp();
    }

    /** The world box Wynntils' map tiles cover (asked once a minute), or null. */
    private static float[] coverage() {
        return QuestsCache.kept("map|coverage", 60_000, Wyn::mapCoverage);
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

    /** The stepper: the focus to the stage before ({@code dir} below 0) or after that has places; the view fits it. */
    void step(int dir) {
        int i = stages.indexOf(focus);
        if (i < 0) {
            return;
        }
        int j = Math.max(0, Math.min(stages.size() - 1, i + (dir < 0 ? -1 : 1)));
        focus = stages.get(j);
        pinned = false;
        zoom = 1;
    }

    /** A click on the + − ⌖ buttons (a map that owns them): zooms or refits; true when it was on one. */
    boolean button(double x, double y) {
        if (buttonsRect == null || !in(x, y, buttonsRect)) {
            return false;
        }
        int row = (int) ((y - buttonsRect[1]) / (Ui.CHIP_H + Theme.SP_1));
        if (row == 0) {
            zoomBy(1);
        } else if (row == 1) {
            zoomBy(-1);
        } else {
            refit();
        }
        return true;
    }

    /** Brings {@code at} (x, y, z) into view when it is outside it: the view centres on it and stays. */
    void reveal(int[] at) {
        if (rect == null) {
            return;
        }
        double px = (at[0] - vx0) / vspan;
        double py = (at[2] - vz0) / vspan;
        if (QuestMapLayout.onMap(px, py, rect[2], rect[3])) {
            return;
        }
        vx0 = at[0] - vspan * rect[2] / 2;
        vz0 = at[2] - vspan * rect[3] / 2;
        pinned = true;
        clamp();
    }

    /** One step of the + / − buttons: closer or further around the map's middle. */
    void zoomBy(int dir) {
        if (rect != null) {
            zoomAt(rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0, dir);
        }
    }

}
