package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The guide's stage list: one row per stage, its number right-aligned in the mark column (a tick
 * once done), its label cut to its first clause when the sentence does not fit; the stage you are
 * on in green, the one shown on a soft gold wash. No stripes: the rows breathe instead.
 */
final class GuideRail {

    /** A row: a body line with SP_2 above and below. */
    static final int ROW = Type.BODY.lineHeight + 2 * Theme.SP_2;

    private GuideRail() {}

    static void draw(GuideScreen s, GuiGraphics g, int mx, int my, int x, int top, int w, int bottom) {
        Font font = s.getFont();
        int n = s.stages.size();
        int fit = Math.max(1, (bottom - top) / ROW);
        if (s.follow) {
            if (s.sel < s.listScroll + 1 || s.sel > s.listScroll + fit - 2) {
                s.listScroll = s.sel - Math.min(3, fit / 2);
            }
            s.follow = false;
        }
        s.listScroll = Math.max(0, Math.min(s.listScroll, n - fit));
        int tx = x + Theme.SP_2 + GuidePane.MARK_W;           // the labels' column
        for (int i = s.listScroll; i < n && i < s.listScroll + fit; i++) {
            int ry = top + (i - s.listScroll) * ROW;
            boolean shown = i == s.sel;
            boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + ROW;
            if (shown || hover) {
                Kit.rowLit(g, x, ry, w, ROW);
                if (shown) {
                    g.fill(x, ry + Theme.SP_2, x + 2, ry + ROW - Theme.SP_2, Theme.GOLD_DEEP);
                }
            }
            int ty = ry + Theme.SP_2;
            boolean done = i < s.current;
            boolean here = i == s.current;
            if (done) {
                Kit.icon(g, "icon_check", tx - Theme.SP_1 - 7, Type.BODY.onCaps(ty, 6), 7, 6, Theme.GOOD);
            } else {
                Type.NUMBER.drawRight(g, font, (i + 1) + ".", tx - Theme.SP_1, ty, here ? Theme.GOOD : Theme.INK_2);
            }
            Type role = done ? Type.MUTED : Type.BODY;
            int ink = here ? Theme.GOOD : role.colour;
            role.draw(g, font, label(font, role, s.labels.get(i), x + w - Theme.SP_2 - tx), tx, ty, ink);
            int stage = i;
            s.tap(x, ry, w, ROW, () -> s.pick(stage));
        }
        if (n > fit) {
            // where in the list you are: a thin bar along the rail's right edge
            int barTop = top + (bottom - top) * s.listScroll / n;
            int barH = Math.max(6, (bottom - top) * fit / n);
            g.fill(x + w + Theme.SP_1, barTop, x + w + Theme.SP_1 + 2, Math.min(bottom, barTop + barH), Theme.LINE);
        }
    }

    /** The whole label when it fits, else its first clause (before ", ", " (" or "; "), "…" last. */
    static String label(Font font, Type role, String text, int width) {
        if (role.width(font, text) <= width) {
            return text;
        }
        for (String cut : List.of(", ", " (", "; ")) {
            int at = text.indexOf(cut);
            if (at >= 12) {
                String head = text.substring(0, at);
                if (role.width(font, head) <= width) {
                    return head;
                }
            }
        }
        return role.fit(font, text, width);
    }
}
