package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.MountType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mounts tab's Places: the three Mount Enclosures on Wynntils' map (the nearest gold, a
 * click on a place or a row points the compass there) beside their list with coordinates and
 * distance, the one compass button, and which mount types this character has unlocked. The Mount
 * Merchant is left out until its places are known.
 */
final class MountsPlaces {

    /** A Mount Enclosure: its place name, the map chip's short name and where it is. */
    record Place(String name, String chip, int[] at) {}

    static final List<Place> ENCLOSURES = List.of(
            new Place("Ternaves", "Ternaves", new int[] {859, 71, -1663}),
            new Place("Bantisu Air Temple", "Bantisu", new int[] {497, 107, -4743}),
            new Place("Aldwell", "Aldwell", new int[] {-1219, 60, -729}));

    /** A type's unlock: the level and the quest. */
    record Unlock(MountType type, int level, String quest) {}

    static final List<Unlock> UNLOCKS = List.of(new Unlock(MountType.WYVERN, 84, "The Canyon Guides"),
            new Unlock(MountType.HORSE, 9, "Stable Story"), new Unlock(MountType.ADASAUR, 115, "Burning Bonds"));

    private static final int MAP_SHARE_NUM = 55;     // the map's share of the width, large windows (percent)

    private final MountsTab tab;
    private final QuestMap map;
    private String rowLit;

    MountsPlaces(MountsTab tab) {
        this.tab = tab;
        map = new QuestMap(tab.menu, false);
        map.questsButtons = false;
    }

    /** The player's {x, z}, or null out of the world. */
    static double[] me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : new double[] {p.getX(), p.getZ()};
    }

    /** Blocks from the player to {@code at}, or -1. */
    static int distance(int[] at) {
        double[] me = me();
        return me == null ? -1 : (int) Math.round(Math.hypot(me[0] - at[0], me[1] - at[2]));
    }

    /** The enclosure nearest the player (the first when the player is nowhere). */
    static Place nearest() {
        Place best = ENCLOSURES.get(0);
        int bd = Integer.MAX_VALUE;
        for (Place p : ENCLOSURES) {
            int d = distance(p.at());
            if (d >= 0 && d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private List<QuestMap.Place> places() {
        Place near = nearest();
        List<QuestMap.Place> out = new ArrayList<>();
        for (Place p : ENCLOSURES) {
            out.add(new QuestMap.Place(p.chip(), p.at(), p.name(), p == near ? QuestMapLayout.NOW : QuestMapLayout.NEXT));
        }
        return out;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean compact = tab.compact();
        List<QuestMap.Place> pts = places();
        boolean hasMap = QuestMap.available(pts);
        int lx = x;
        int lw = w;
        int ly = y;
        String lit = rowLit;
        rowLit = null;
        if (hasMap) {
            if (compact) {
                int mh = Math.max(80, h * 2 / 5);
                map.draw(g, x, y, w, mh, pts, lit, true, "mounts", mx, my);
                ly = y + mh + Theme.SP_5;
            } else {
                int mw = w * MAP_SHARE_NUM / 100;
                map.draw(g, x, y, mw, h, pts, lit, true, "mounts", mx, my);
                lx = x + mw + Theme.SP_6;
                lw = w - mw - Theme.SP_6;
            }
        }
        g.enableScissor(lx - Theme.SP_2, ly, lx + lw + Theme.SP_2, y + h);
        if (!hasMap && !compact) {
            // no tiles here: the enclosures and the unlocks side by side, not one long row each
            int half = (lw - Theme.SP_6) / 2;
            list(g, lx, ly, half, mx, my);
            unlocks(g, lx + half + Theme.SP_6, ly, lw - half - Theme.SP_6);
        } else {
            int cy = list(g, lx, ly, lw, mx, my);
            if (!compact || !hasMap) {
                unlocks(g, lx, cy + Theme.SP_6, lw);
            }
        }
        g.disableScissor();
    }

    /** The enclosures, the nearest washed, then the compass button; returns the y after it. */
    private int list(GuiGraphics g, int x, int y, int w, int mx, int my) {
        var font = tab.font();
        y = head(g, x, y, w, "Mount Enclosure", String.valueOf(ENCLOSURES.size()));
        Place near = nearest();
        int rowH = Theme.SP_2 + Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_2;
        for (Place p : ENCLOSURES) {
            boolean on = p == near;
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
            if (on) {
                Kit.rowLit(g, x - Theme.SP_2, y, w + 2 * Theme.SP_2, rowH);
            } else if (hover) {
                g.fill(x - Theme.SP_2, y, x + w + Theme.SP_2, y + rowH, Theme.CARD_HOVER);
            }
            if (hover) {
                rowLit = p.chip();
            }
            int ty = y + Theme.SP_2;
            Art.icon(g, "map-pin", x, Type.BODY.onCaps(ty, 8), 8, on ? Theme.GOLD_EDGE : Theme.INK_3);
            int d = distance(p.at());
            String dist = d < 0 ? "" : RoadmapScreen.dist(d);
            int dw = Type.META.width(font, dist);
            Type.META.drawRight(g, font, dist, x + w, ty, Theme.INK_2);
            int nx = x + 8 + Theme.SP_3;
            (on ? Type.LABEL : Type.BODY).draw(g, font, (on ? Type.LABEL : Type.BODY).fit(font, p.name(), x + w - dw - Theme.SP_3 - nx),
                    nx, ty, Theme.INK);
            Type.META.draw(g, font, p.at()[0] + ", " + p.at()[1] + ", " + p.at()[2], nx, ty + Type.BODY.lineHeight, Theme.INK_2);
            tab.compass(x - Theme.SP_2, y, w + 2 * Theme.SP_2, rowH, p.name(), p.at());
            y += rowH + Theme.SP_2;
        }
        y += Theme.SP_2;
        String label = L.t("wynnlabs.mounts.compass_to", near.chip());
        int bw = Math.min(w, Ui.iconButtonLabelWidth(font, label));
        Ui.iconButtonLabel(g, font, "compass", label, x, y, bw, mx, my);
        tab.compass(x, y, bw, Ui.BUTTON_H, near.name(), near.at());
        return y + Ui.BUTTON_H;
    }

    /** Which types are open to this character: a check, or a lock with the level and the quest. */
    private void unlocks(GuiGraphics g, int x, int y, int w) {
        var font = tab.font();
        y = head(g, x, y, w, L.t("wynnlabs.mounts.unlocks"), null);
        int nameW = 0;
        for (Unlock u : UNLOCKS) {
            nameW = Math.max(nameW, Type.BODY.width(font, u.type().display()));
        }
        for (Unlock u : UNLOCKS) {
            boolean ok = tab.model.unlocked(u.type(), u.quest(), u.level());
            Art.icon(g, ok ? "check" : "lock", x, Type.BODY.onCaps(y, 8), 8, ok ? Theme.GOOD : Theme.INK_3);
            int nx = x + 8 + Theme.SP_3;
            Type.BODY.draw(g, font, u.type().display(), nx, y, ok ? Theme.INK : Theme.INK_3);
            int qx = nx + nameW + Theme.SP_5;
            String q = L.t("wynnlabs.mounts.unlock_line", u.level(), u.quest());
            Type.META.draw(g, font, Type.META.fit(font, q, x + w - qx), qx, y, Theme.INK_2);
            y += Type.BODY.lineHeight + Theme.SP_2;
        }
    }

    /** A section title: LABEL, a count on the right, the hairline under it; returns the y for the rows. */
    int head(GuiGraphics g, int x, int y, int w, String title, String count) {
        var font = tab.font();
        Type.LABEL.draw(g, font, title, x, y, Theme.INK);
        if (count != null) {
            Type.META.drawRight(g, font, count, x + w, y, Theme.INK_3);
        }
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    // ---- the map's mouse ----

    boolean clicked(MouseButtonEvent e, boolean doubleClick) {
        if (map.rect == null || !MenuPaint.in(e.x(), e.y(), map.rect)) {
            return false;
        }
        if (map.button(e.x(), e.y())) {
            return true;
        }
        if (doubleClick && map.press(e)) {
            map.release();
            map.refit();
            return true;
        }
        return map.press(e);
    }

    boolean dragged(double x, double y) {
        return map.drag(x, y);
    }

    void released() {
        MouseButtonEvent click = map.release();
        if (click != null) {
            tab.menu.replayClick(click);
        }
    }

    void scrolled(double x, double y, double v) {
        if (map.rect != null && MenuPaint.in(x, y, map.rect)) {
            map.zoomAt(x, y, (int) Math.signum(v));
        }
    }
}
