package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.RideView;
import io.github.anilken.wynnlabs.mounts.RideWatch;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The mount panel (setting ridePanel, off by default): while riding, the HUD's dark panel with the
 * mount's name and type, its energy pool, and its eight stats as level/limit with a bar on one
 * shared scale (0 to level filled, level to limit lighter, a gold tick at the max when a feeder
 * showed it). A stat at its limit is dimmed; a changed one shows "+N / −N" in green or red for two
 * seconds. Movable ({@link HudPlace}, id {@code ride}); its default place is the left edge, a
 * little above the middle. Texts and widths are worked out once per read.
 */
public final class RidePanel {

    public static final String ID = "ride";

    static final int TRACK = 0x30F4E9C8;
    static final int ROOM = 0x66F4E9C8;
    static final int FILL = 0xFFE6DCC2;
    static final int TEAL = 0xFF6FCFD6;
    static final int GREEN = 0xFF8CC86A;
    static final int RED = 0xFFE0604A;
    static final int SEAL_UP = 0xFF4F7F48;
    static final int SEAL_DOWN = 0xFF8E3A2A;
    static final int SEAL_INK = 0xFFF4F8EA;
    static final int RULE = 0x33F4E9C8;

    private static final int PAD = Theme.SP_3;
    private static final int STRIPE = 2;
    private static final int BAR_W = 42;
    private static final int BAR_W_SMALL = 34;
    /** Below this GUI height the rows sit closer and the bars are shorter. */
    static final int SMALL_SCREEN = 300;

    private RidePanel() {}

    /** One view's texts and widths. */
    private record Layout(RideView v, boolean small, String note, List<String> names, List<String> numbers, int nameW,
                          int deltaW, int numW, int barW, int inner, int w, int h, int scale) {}

    private static Layout cached;

    private static Layout layout(Font font, RideView v, boolean small) {
        Layout c = cached;
        if (c != null && c.v() == v && c.small() == small) {
            return c;
        }
        List<String> names = new ArrayList<>();
        List<String> numbers = new ArrayList<>();
        int nameW = 0;
        int numW = 0;
        int scale = 1;
        for (MountRead.Stat s : v.stats()) {
            names.add(s.name());
            String n = s.level() + "/" + s.limit();
            numbers.add(n);
            nameW = Math.max(nameW, Type.META.width(font, s.name()));
            numW = Math.max(numW, Type.META.width(font, n));
            scale = Math.max(scale, end(v, s));
        }
        int deltaW = Type.META.width(font, "+10") + 2 * Theme.SP_2;
        int barW = small ? BAR_W_SMALL : BAR_W;
        String note = v.note() == null ? null : L.t(v.note());
        int inner;
        int body;
        if (v.stats().isEmpty()) {
            // no stats read: the header, the energy and one dim line, about as wide as the full panel
            inner = Math.max(MIN_INNER, Math.max(Type.META.width(font, note),
                    Type.LABEL.width(font, v.title()) + Theme.SP_3 + Type.META.width(font, v.kind())));
            body = Type.META.lineHeight;
        } else {
            inner = nameW + Theme.SP_3 + barW + Theme.SP_2 + deltaW + Theme.SP_1 + numW;
            int rows = v.stats().size();
            body = rows * Type.META.lineHeight + Math.max(0, rows - 1) * pitchGap(small);
        }
        int w = STRIPE + 2 * PAD + inner;
        int h = PAD + Type.LABEL.lineHeight + Theme.SP_1 + Type.META.lineHeight + Theme.SP_2 + 1 + Theme.SP_2 + body + PAD;
        c = new Layout(v, small, note, names, numbers, nameW, deltaW, numW, barW, inner, w, h, scale);
        cached = c;
        return c;
    }

    /** The narrowest panel without stats (about the full one's width). */
    private static final int MIN_INNER = 130;

    private static int pitchGap(boolean small) {
        return small ? 0 : Theme.SP_1;
    }

    /** Where a stat's bar ends: its max when a feeder showed it, else its limit. */
    private static int end(RideView v, MountRead.Stat s) {
        return v.maxKnown() ? Math.max(s.limit(), s.max()) : s.limit();
    }

    /** The panel's size for this view on a screen {@code screenH} tall: {w, h}. */
    static int[] size(Font font, RideView v, int screenH) {
        Layout l = layout(font, v, screenH < SMALL_SCREEN);
        return new int[] {l.w(), l.h()};
    }

    /** Its default place: the left edge, a little above the middle. */
    static int[] defaultAt(int screenW, int screenH, int w, int h) {
        return HudPlace.clamp(Theme.SP_2, (screenH - h) / 2 - 30, screenW, screenH, w, h);
    }

    /** Its place on this screen: the saved spot, else the default. */
    static int[] at(int screenW, int screenH, int w, int h) {
        HudPlace.Spot s = HudPlace.load(ID);
        return s == null ? defaultAt(screenW, screenH, w, h) : HudPlace.resolve(s, screenW, screenH, w, h);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || !RideWatch.showing(mc)) {
            return;
        }
        RideView v = RideWatch.view();
        if (v == null) {
            return;
        }
        Font font = Hud.font();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int[] wh = size(font, v, sh);
        int[] xy = at(sw, sh, wh[0], wh[1]);
        draw(g, font, v, xy[0], xy[1], sh < SMALL_SCREEN, System.currentTimeMillis());
        RideWatch.drawn(xy[0], xy[1], wh[0], wh[1], sw, sh);
    }

    /** The panel at x, y (also the edit screen's sample). */
    static void draw(GuiGraphics g, Font font, RideView v, int x, int y, boolean small, long now) {
        Layout l = layout(font, v, small);
        Art.box(g, x, y, l.w(), l.h(), 4, Hud.PANEL, Hud.RING);
        Art.box(g, x, y + PAD / 2, STRIPE, l.h() - PAD, 1, Hud.GOLD, 0);
        int tx = x + STRIPE + PAD;
        int right = tx + l.inner();
        int ty = y + PAD;
        // the name, and the type on the right
        int kindW = Type.META.width(font, v.kind());
        Type.LABEL.draw(g, font, Type.LABEL.fit(font, v.title(), l.inner() - kindW - Theme.SP_3), tx, ty, Hud.CREAM);
        Type.META.draw(g, font, v.kind(), right - kindW, ty + (Type.LABEL.lineHeight - Type.META.lineHeight), Hud.DIM);
        ty += Type.LABEL.lineHeight + Theme.SP_1;
        // the energy pool: the item's points (an estimate with "~" between reads), else Wynntils' bar in percent
        Art.icon(g, "bolt", tx, Type.META.onCaps(ty, 8), 8, TEAL);
        int ew = Type.META.width(font, v.energy());
        int bx = tx + 8 + Theme.SP_2;
        int bw = right - ew - Theme.SP_3 - bx;
        int by = Type.META.onCaps(ty, 4);
        g.fill(bx, by, bx + bw, by + 4, TRACK);
        int fill = Math.round(bw * Math.max(0f, Math.min(1f, v.fill())));
        if (fill > 0) {
            g.fill(bx, by, bx + fill, by + 4, TEAL);
        }
        Type.META.draw(g, font, v.energy(), right - ew, ty, Hud.CREAM);
        ty += Type.META.lineHeight + Theme.SP_2;
        Kit.hair(g, tx, ty, l.inner(), 1, RULE);
        ty += 1 + Theme.SP_2;
        if (l.note() != null) {
            Type.META.draw(g, font, Type.META.fit(font, l.note(), l.inner()), tx, ty, Hud.MUTED);
            return;
        }
        // the stats
        int barX = tx + l.nameW() + Theme.SP_3;
        int sealX = barX + l.barW() + Theme.SP_2;
        for (int i = 0; i < v.stats().size(); i++) {
            MountRead.Stat s = v.stats().get(i);
            boolean full = s.level() >= s.limit();
            int d = RideWatch.delta(s.id(), now);
            Type.META.draw(g, font, l.names().get(i), tx, ty, full ? Hud.MUTED : Hud.DIM);
            bar(g, barX, Type.META.onCaps(ty, 3), l.barW(), l.scale(), s, end(v, s), v.maxKnown() && s.max() > 0, full, d);
            int ink = full ? Hud.MUTED : Hud.CREAM;
            if (d != 0) {
                String t = (d > 0 ? "+" : "−") + Math.abs(d);
                int w = Type.META.width(font, t) + 2 * Theme.SP_2;
                int sy = Type.META.onCaps(ty, Ui.SEAL_H);
                Kit.seal(g, sealX, sy, w, Ui.SEAL_H, d > 0 ? SEAL_UP : SEAL_DOWN);
                Type.META.draw(g, font, t, sealX + Theme.SP_2, Type.META.yIn(sy, Ui.SEAL_H), SEAL_INK);
                ink = d > 0 ? GREEN : RED;
            }
            String n = l.numbers().get(i);
            Type.META.draw(g, font, n, right - Type.META.width(font, n), ty, ink);
            ty += Type.META.lineHeight + pitchGap(small);
        }
    }

    /**
     * One stat's bar, every layer a flat rectangle on whole GUI px and the same height: the track to
     * its end, the room to its limit, the level (at least 1 px when above 0), the change, the max tick.
     */
    private static void bar(GuiGraphics g, int x, int y, int w, int scale, MountRead.Stat s, int end, boolean maxKnown,
                            boolean full, int d) {
        int h = 3;
        int pEnd = Math.max(1, px(end, w, scale));
        int pLim = px(s.limit(), w, scale);
        int pLv = s.level() > 0 ? Math.max(1, px(s.level(), w, scale)) : 0;
        g.fill(x, y, x + pEnd, y + h, TRACK);
        if (pLim > pLv) {
            g.fill(x + pLv, y, x + pLim, y + h, ROOM);
        }
        if (pLv > 0) {
            g.fill(x, y, x + pLv, y + h, full ? Hud.MUTED : FILL);
        }
        if (d != 0) {
            int a = px(Math.min(s.level(), s.level() - d), w, scale);
            int b = Math.max(a + 1, px(Math.max(s.level(), s.level() - d), w, scale));
            g.fill(x + a, y, x + b, y + h, d > 0 ? GREEN : RED);
        }
        if (maxKnown) {
            Kit.hair(g, x + pEnd - 1, y - 1, 1, h + 2, Hud.GOLD);
        }
    }

    private static int px(int v, int w, int scale) {
        return Math.max(0, Math.min(w, Math.round((float) w * v / scale)));
    }

    /** A sample for the edit screen when no mount is known yet. */
    static MountRead sample() {
        String[] ids = {"SPEED", "ACCELERATION", "ALTITUDE", "ENERGY", "HANDLING", "TOUGHNESS", "BOOST", "TRAINING"};
        String[] names = {"Speed", "Acceleration", "Altitude", "Energy", "Handling", "Toughness", "Boost", "Training"};
        int[][] v = {{12, 21, 58}, {21, 21, 44}, {9, 18, 52}, {15, 19, 40}, {6, 14, 35}, {18, 18, 47}, {4, 12, 38}, {30, 30, 30}};
        List<MountRead.Stat> stats = new ArrayList<>();
        int pot = 0;
        for (int i = 0; i < ids.length; i++) {
            stats.add(new MountRead.Stat(ids[i], names[i], v[i][0], v[i][1], v[i][2]));
            pot += v[i][2];
        }
        return new MountRead("WYVERN", "Wyvern", pot, 18, 30, List.copyOf(stats), false, "Azure", "", "Azure-Horn", null);
    }
}
