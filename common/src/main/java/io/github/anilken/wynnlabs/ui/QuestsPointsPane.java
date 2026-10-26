package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The picked quest's places ("Noktalar"), what was the old tab's Points tab: every place numbered
 * by stage with its coordinates, pin and distance, the passed ones struck through with a tick, the
 * stage you are on lit, "Yol ›" to each; Wynntils' map beside the list (above it in a compact
 * window); "Hepsini işaretle", "Sadece bu aşama" and "İşaretleri sil" at the foot. A row and its
 * marker light together.
 */
final class QuestsPointsPane extends MenuPaint {

    private static final int ROW = Theme.SP_2 + Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_2;
    private static final int PITCH = ROW + Theme.SP_2;

    private final QuestsTab tab;

    QuestsPointsPane(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        String name = tab.questSelected;
        QuestWiki.Info info = QuestWiki.get(name);
        List<QuestMap.Place> all = QuestsData.mapPlaces(name, info, true);
        int cy = y + tab.guide.titleRow(g, x, y, w, name, info, all.size(), mx, my);
        int foot = y + h - Ui.BUTTON_H;
        boolean compact = menu.layout.compact();
        if (all.isEmpty()) {
            String none = info == null ? L.t("wynnlabs.quests.wiki_loading") : L.t("wynnlabs.quests.points_none");
            Type.MUTED.drawWrapped(g, font(), none, x, cy, w, 4, Theme.INK_2);
            buttons(g, x, foot, w, name, mx, my);
            return;
        }
        String nearest = nearest(all);
        String head = nearest == null ? L.n("wynnlabs.quests.points_count", all.size())
                : L.n("wynnlabs.quests.points_head", all.size(), all.size(), nearest);
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), head, w), x, cy, Theme.INK_2);
        cy += Type.BODY.lineHeight + Theme.SP_3;
        Kit.hair(g, x, cy, w, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        // the map: what is left to do (and you), beside the list, or a strip over it when compact
        List<QuestMap.Place> left = QuestsData.mapPlaces(name, info, false);
        int listX = x;
        int listW = w;
        int listTop = cy;
        int listBottom = foot - Theme.SP_4;
        if (!left.isEmpty()) {
            if (compact) {
                int sh = 84;
                if (menu.layout.h() >= 300 && listBottom - cy > sh + 2 * PITCH
                        && tab.map.draw(g, x, cy, w, sh, left, tab.litPlace, true, name + "|" + QuestsData.stage(name, info) + "|p", mx, my)) {
                    listTop = cy + sh + Theme.SP_4;
                }
            } else {
                int mw = Math.max(180, Math.min(300, w * 42 / 100));
                if (tab.map.draw(g, x + w - mw, cy, mw, listBottom - cy, left, tab.litPlace, true, name + "|" + QuestsData.stage(name, info) + "|p", mx, my)) {
                    listW = w - mw - Theme.SP_6;
                }
            }
        }
        list(g, info, all, listX, listTop, listW, listBottom, compact, mx, my);
        buttons(g, x, foot, w, name, mx, my);
    }

    /** The places, scrolled; the first open on the stage you are on (the passed ones a wheel turn above). */
    private void list(GuiGraphics g, QuestWiki.Info info, List<QuestMap.Place> all, int x, int top, int w, int bottom, boolean compact,
                      int mx, int my) {
        tab.pointsRect = new int[] {x, top, w, bottom - top};
        int rows = Math.max(1, (bottom - top + Theme.SP_2) / PITCH);
        int cur = QuestsData.stage(tab.questSelected, info);
        String key = tab.questSelected + "|" + cur;
        if (!key.equals(tab.pointScrollKey)) {
            tab.pointScrollKey = key;
            tab.pointScroll = 0;
            while (tab.pointScroll < all.size() && all.get(tab.pointScroll).state() == QuestMapLayout.DONE) {
                tab.pointScroll++;
            }
            if (!compact) {
                tab.pointScroll = Math.max(0, tab.pointScroll - Math.max(0, rows - (all.size() - tab.pointScroll)));
            }
        }
        tab.pointScroll = Math.max(0, Math.min(tab.pointScroll, Math.max(0, all.size() - rows)));
        String lit = null;
        int goW = GuidePane.goW(font());
        int numW = Type.NUMBER.width(font(), "00.0") + Theme.SP_3;
        int ry = top;
        for (int i = tab.pointScroll; i < all.size() && ry + ROW <= bottom; i++) {
            QuestMap.Place p = all.get(i);
            boolean done = p.state() == QuestMapLayout.DONE;
            boolean now = p.state() == QuestMapLayout.NOW || p.state() == QuestMapLayout.START;
            boolean hover = mx >= x - Theme.SP_3 && mx < x + w && my >= ry && my < ry + ROW;
            boolean fromMap = p.num().equals(tab.map.hovered);
            if (now) {
                Kit.rowLit(g, x - Theme.SP_3, ry, w + Theme.SP_3, ROW);
            } else if (hover || fromMap) {
                g.fill(x - Theme.SP_3, ry, x + w, ry + ROW, Theme.CARD_HOVER);
            }
            if (hover) {
                lit = p.num();
            }
            int ty = ry + Theme.SP_2;
            // a passed place stays in the list, faded to half, struck through and ticked
            Type.NUMBER.draw(g, font(), p.num(), x, ty, done ? half(Theme.INK_3) : Theme.PRICE);
            int tx = x + numW;
            int right = x + w - (done ? 12 : goW + Theme.SP_3);
            String what = Type.BODY.fit(font(), p.task(), right - tx);
            if (done) {
                Kit.text(g, font(), Type.BODY.of(what).withStyle(ChatFormatting.STRIKETHROUGH), tx, ty, half(Theme.INK_2));
                Art.icon(g, "check", x + w - 9, Type.BODY.onCaps(ty, 8), 8, half(Theme.GOOD));
            } else {
                Type.BODY.draw(g, font(), what, tx, ty, Theme.INK);
                int gy = Type.BODY.onCaps(ty, Ui.SMALL_H);
                Ui.small(g, font(), L.t("wynnlabs.guide.go"), x + w - goW, gy, goW, mx, my);
            }
            String at = p.at()[0] + ", " + p.at()[1] + ", " + p.at()[2];
            int my2 = ty + Type.BODY.lineHeight;
            Type.META.draw(g, font(), at, tx, my2, done ? half(Theme.INK_3) : Theme.LINK);
            int aw = Type.META.width(font(), at);
            Art.icon(g, "map-pin", tx + aw + Theme.SP_1, Type.META.onCaps(my2, 8), 8, done ? half(Theme.INK_3) : Theme.GOLD_EDGE);
            String d = QuestsData.distance(p.at());
            if (d != null) {
                Type.META.draw(g, font(), d, tx + aw + Theme.SP_1 + 8 + Theme.SP_3, my2, done ? half(Theme.INK_3) : Theme.INK_3);
            }
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(p.num() + " · " + p.task()).withStyle(ChatFormatting.LIGHT_PURPLE));
            tip.add(Component.literal(at).withStyle(ChatFormatting.GRAY));
            tip.add(L.c("wynnlabs.quests.point_click").withStyle(ChatFormatting.DARK_GRAY));
            menu.hits.add(new MenuHit(x - Theme.SP_3, ry, w + Theme.SP_3, ROW, null, tip,
                    "/compass at " + p.at()[0] + " " + p.at()[1] + " " + p.at()[2]));
            ry += PITCH;
        }
        tab.litPlace = lit;
        // where in the list you are: a thin bar at the right edge, dragged or clicked
        tab.pointsBar.place(x + w + Theme.SP_1, top, bottom - top, rows, all.size(), tab.pointScroll);
        tab.pointsBar.drawThin(g, mx, my);
    }

    /** A colour at half its strength (a passed place's row). */
    private static int half(int argb) {
        return (argb & 0x00FFFFFF) | 0x80000000;
    }

    private static String nearest(List<QuestMap.Place> places) {
        String best = null;
        double d = Double.MAX_VALUE;
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) {
            return null;
        }
        for (QuestMap.Place pl : places) {
            double dd = Math.hypot(p.getX() - pl.at()[0], p.getZ() - pl.at()[2]);
            if (pl.state() != QuestMapLayout.DONE && dd < d) {
                d = dd;
                best = QuestsData.distance(pl.at());
            }
        }
        return best;
    }

    /** The marks: every place queued from the stage you are on, this stage's only, ours cleared. */
    private void buttons(GuiGraphics g, int x, int y, int w, String name, int mx, int my) {
        boolean compact = menu.layout.compact();
        String all = L.t("wynnlabs.quests.btn_mark_every");
        int bw = Ui.iconButtonLabelWidth(font(), all);
        Ui.iconButtonLabel(g, font(), "map-pin", all, x, y, bw, mx, my);
        menu.hits.add(new MenuHit(x, y, bw, Ui.BUTTON_H, null, List.of(L.c("wynnlabs.quests.pins_tip").withStyle(ChatFormatting.GRAY)),
                "#qpins:" + name));
        int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        int bx = x + bw + Theme.SP_2;
        String stage = L.t(compact ? "wynnlabs.quests.btn_mark_stage_short" : "wynnlabs.quests.btn_mark_stage");
        int sw = Ui.iconChipWidth(font(), "route", stage, false);
        Ui.iconChip(g, font(), "route", stage, false, bx, cy, sw, false, mx, my);
        menu.hits.add(new MenuHit(bx, cy, sw, Ui.CHIP_H, null, null, "#qpinstage:" + name));
        bx += sw + Theme.SP_2;
        // clearing our marks: a thin pill in the error colour (an icon alone when compact)
        String clear = L.t("wynnlabs.quests.btn_clear_pins");
        List<Component> tip = List.of(Component.literal(clear).withStyle(ChatFormatting.WHITE),
                L.c("wynnlabs.quests.clear_tip").withStyle(ChatFormatting.GRAY));
        if (compact) {
            int sq = Ui.CHIP_H + 2;
            Ui.iconButton(g, "eraser", x + w - sq, cy, false, mx, my);
            menu.hits.add(new MenuHit(x + w - sq, cy, sq, Ui.CHIP_H, null, tip, "#qclear"));
            return;
        }
        int cw = Ui.iconChipWidth(font(), "eraser", clear, false);
        if (bx + cw > x + w) {
            return;
        }
        boolean hover = mx >= bx && mx < bx + cw && my >= cy && my < cy + Ui.CHIP_H;
        Kit.pill(g, bx, cy, cw, Ui.CHIP_H, false, hover);
        int ty = Type.BODY.yIn(cy, Ui.CHIP_H);
        int inner = 8 + Theme.SP_1 + Type.BODY.width(font(), clear);
        int ix = bx + (cw - inner) / 2;
        Art.icon(g, "eraser", ix, Type.BODY.onCaps(ty, 8), 8, Theme.BAD);
        Type.BODY.draw(g, font(), clear, ix + 8 + Theme.SP_1, ty, Theme.BAD);
        menu.hits.add(new MenuHit(bx, cy, cw, Ui.CHIP_H, null, tip, "#qclear"));
    }
}
