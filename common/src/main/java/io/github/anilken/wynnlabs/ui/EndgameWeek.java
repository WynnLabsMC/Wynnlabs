package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.endgame.PoolView;
import io.github.anilken.wynnlabs.endgame.WynnventoryPools;
import io.github.anilken.wynnlabs.model.RaidSummary;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Endgame page's week (the approved build/endgame-mockup/index.html): "Bu hafta senin için",
 * the raid that drops the most aspects you lack and the camp with the most mythics, each a click
 * from its pool; then the raids and the lootrun camps of this week, one row each, the best one lit
 * and gold. A row opens its pool in the drawer.
 */
final class EndgameWeek extends EndgamePanel {

    private static final int ROW = Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_1;

    EndgameWeek(EndgameTab tab) {
        super(tab);
    }

    // ---- the page ----

    /**
     * Standard and large: "Bu hafta senin için" across the top, then the raids, the camps and the
     * aspects in three columns (large), or the raids over the camps beside the aspects (standard).
     */
    void wide(GuiGraphics g, int x, int y, int w, int h, boolean large, int mx, int my) {
        int top = y + hero(g, x, y, w, false, mx, my) + Theme.SP_6;
        int bottom = y + h;
        int gap = Theme.SP_7;
        if (large) {
            int col = (w - 2 * gap) / 3;
            int x2 = x + col + gap;
            int x3 = x2 + col + gap;
            int ry = raids(g, x, top, col, bottom - Type.META.lineHeight - Theme.SP_3, mx, my);
            dryLine(g, x, ry + Theme.SP_1, col);
            camps(g, x2, top, col, bottom, mx, my);
            tab.aspectList.aspects(g, x3, top, x + w - x3, bottom - top, true, false, mx, my);
            Kit.hair(g, x2 - gap / 2, top, 1, bottom - top, Theme.LINE);
            Kit.hair(g, x3 - gap / 2, top, 1, bottom - top, Theme.LINE);
            return;
        }
        int col = (w - gap) / 2;
        int x2 = x + col + gap;
        int ry = raids(g, x, top, col, bottom, mx, my);
        ry += dryLine(g, x, ry, col) + Theme.SP_4;
        camps(g, x, ry, col, bottom, mx, my);
        tab.aspectList.aspects(g, x2, top, x + w - x2, bottom - top, false, false, mx, my);
        Kit.hair(g, x2 - gap / 2, top, 1, bottom - top, Theme.LINE);
    }

    /** Compact: a short card, then Raidler / Kamplar / Aspectler as chips and the picked one under them. */
    void compact(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int top = y + hero(g, x, y, w, true, mx, my) + Theme.SP_4;
        String[] names = {L.t("wynnlabs.endgame.raids"), L.t("wynnlabs.endgame.camps"), L.t("wynnlabs.endgame.aspects")};
        String[] icons = {"sword", "map-pin", "crystal-ball"};
        int cx = x;
        for (int i = 0; i < names.length; i++) {
            int cw = Ui.iconChipWidth(tab.font, icons[i], names[i], false);
            Ui.iconChip(g, tab.font, icons[i], names[i], false, cx, top, cw, tab.section == i, mx, my);
            int sec = i;
            tab.acts.add(new Act(cx, top, cw, Ui.CHIP_H, () -> tab.section = sec, "endgame:section" + i));
            cx += cw + Theme.SP_2;
        }
        int sy = top + Ui.CHIP_H + Theme.SP_4;
        int bottom = y + h;
        switch (tab.section) {
            case EndgameTab.SEC_LOOTRUNS -> camps(g, x, sy, w, bottom, mx, my);
            case EndgameTab.SEC_ASPECTS -> tab.aspectList.aspects(g, x, sy, w, bottom - sy, false, true, mx, my);
            default -> {
                int ry = raids(g, x, sy, w, bottom - Type.META.lineHeight, mx, my);
                dryLine(g, x, ry, w);
            }
        }
    }

    // ---- the card ----

    /** The week's best for you on a soft card (an info notice while there is no pool); returns its height. */
    int hero(GuiGraphics g, int x, int y, int w, boolean compact, int mx, int my) {
        PoolView.Region raid = tab.pools.best(true, null);
        PoolView.Region camp = tab.pools.best(false, null);
        if (raid == null && camp == null) {
            boolean loading = tab.d.p().status() == WynnventoryPools.Status.LOADING;
            return Ui.notice(g, tab.font, Ui.Level.INFO, List.of(L.t(loading ? "wynnlabs.endgame.week_loading" : "wynnlabs.endgame.week_none")),
                    null, x, y, w, 80, mx, my, new int[1][]);
        }
        int pad = Theme.SP_5;
        int h = pad + Type.META.lineHeight + Type.LEAD.lineHeight + Type.META.lineHeight + pad;
        Kit.softCard(g, x, y, w, h);
        String go = L.t(compact ? "wynnlabs.endgame.pool_open_short" : "wynnlabs.endgame.pool_open");
        int bw = Ui.buttonWidth(tab.font, go);
        int bx = x + w - pad - bw;
        int by = y + (h - Ui.BUTTON_H) / 2;
        PoolView.Region first = raid != null && raid.marked() > 0 || camp == null ? raid : camp;
        Ui.button(g, tab.font, go, bx, by, bw, mx, my);
        tab.acts.add(new Act(bx, by, bw, Ui.BUTTON_H, () -> tab.openPool(first.raid(), first.name()), "endgame:hero"));
        int half = compact || camp == null || raid == null ? bx - Theme.SP_5 - x - pad : (bx - Theme.SP_6 - x - pad) / 2;
        int cx = x + pad;
        if (raid != null) {
            pick(g, cx, y + pad, half, raid, "wynnlabs.endgame.week_for_you", raidLine(raid));
            cx += half + Theme.SP_6;
        }
        if (camp != null && !compact) {
            if (raid != null) {
                Kit.hair(g, cx - Theme.SP_3, y + Theme.SP_4, 1, h - 2 * Theme.SP_4, Theme.LINE);
            }
            pick(g, cx, y + pad, half, camp, "wynnlabs.endgame.week_mythic", campLine(camp));
        }
        return h;
    }

    /** One pick of the card: a META title, the region in LEAD (a click opens its pool), its line under it. */
    private void pick(GuiGraphics g, int x, int y, int w, PoolView.Region r, String head, String line) {
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, L.t(head), w), x, y, Theme.INK_3);
        String name = EndgamePoolsView.regionName(r);
        Type.LEAD.draw(g, tab.font, Type.LEAD.fit(tab.font, name, w), x, y + Type.META.lineHeight);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, line, w), x, y + Type.META.lineHeight + Type.LEAD.lineHeight, Theme.INK_2);
        tab.acts.add(new Act(x, y, w, Type.META.lineHeight + Type.LEAD.lineHeight, () -> tab.openPool(r.raid(), r.name()),
                "endgame:pick:" + r.name()));
    }

    private String raidLine(PoolView.Region r) {
        String n = r.marked() > 0 ? L.t("wynnlabs.endgame.week_missing", r.marked()) : L.t("wynnlabs.endgame.week_nothing_new");
        return r.seenAt() >= 0 ? n + " · " + L.t("wynnlabs.endgame.pool_age", agoLong(r.seenAt())) : n;
    }

    private String campLine(PoolView.Region r) {
        String n = L.n("wynnlabs.endgame.mythic_n", r.mythics());
        return r.seenAt() >= 0 ? n + " · " + L.t("wynnlabs.endgame.pool_age", agoLong(r.seenAt())) : n;
    }

    // ---- the raids and the camps ----

    /** A column's title, its hairline and an optional icon chip on the right; returns the y under it. */
    int head(GuiGraphics g, String title, String icon, String label, String what, Runnable action, int x, int y, int w, int mx, int my) {
        Type.LABEL.draw(g, tab.font, title, x, y);
        if (label != null) {
            int cw = Ui.iconChipWidth(tab.font, icon, label, false);
            int cy = Type.LABEL.onCaps(y, Ui.CHIP_H);
            Ui.iconChip(g, tab.font, icon, label, false, x + w - cw, cy, cw, false, mx, my);
            tab.acts.add(new Act(x + w - cw, cy, cw, Ui.CHIP_H, action, what));
        }
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    /**
     * The raids: each this week's pool (or, without one, the raids you ran) with how many aspects
     * it holds that you lack; your wins, best time and when the pool was seen under it. The best
     * one is lit with a gold seal; a row opens its pool. Returns the y under the last row.
     */
    int raids(GuiGraphics g, int x, int y, int w, int bottom, int mx, int my) {
        y = head(g, L.t("wynnlabs.endgame.raids"), "script", L.t("wynnlabs.endgame.log_open"), "endgame:log", tab::openLog, x, y, w, mx, my);
        List<PoolView.Region> regs = tab.d.v().regions(true);
        PoolView.Region best = tab.pools.best(true, null);
        List<String> names = new ArrayList<>();
        regs.forEach(r -> names.add(r.name()));
        for (RaidSummary s : tab.d.summaries()) {
            if (!names.contains(s.raid())) {
                names.add(s.raid());
            }
        }
        if (names.isEmpty()) {
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, L.t("wynnlabs.endgame.raids_none"), w), x, y);
            return y + Type.BODY.lineHeight;
        }
        for (String name : names) {
            if (y + ROW > bottom) {
                break;
            }
            PoolView.Region r = regs.stream().filter(q -> q.name().equals(name)).findFirst().orElse(null);
            RaidSummary s = tab.d.summaries().stream().filter(q -> q.raid().equals(name)).findFirst().orElse(null);
            boolean lit = r != null && best != null && r == best && r.marked() > 0;
            List<String> f = new ArrayList<>();
            if (s != null && s.done() + s.failed() > 0) {
                f.add(L.t("wynnlabs.endgame.row_wins", s.done()));
                if (s.bestMs() > 0) {
                    f.add(L.t("wynnlabs.endgame.row_best", time(s.bestMs())));
                }
            } else {
                f.add(L.t("wynnlabs.endgame.row_no_runs"));
            }
            if (r != null && r.seenAt() >= 0) {
                f.add(L.t("wynnlabs.endgame.pool_age", agoLong(r.seenAt())));
            }
            String seal = r != null && r.marked() > 0 ? L.t("wynnlabs.endgame.row_new", r.marked()) : null;
            row(g, x, y, w, TEX_RAID, "sword", name, String.join(" · ", f), seal, lit, r != null, mx, my,
                    r == null ? null : () -> tab.openPool(true, name), r == null ? null : EndgamePoolsView.regionCount(r));
            y += ROW + Theme.SP_2;
        }
        return y;
    }

    /** The camps of this week: mythics, a shiny, when seen; the one with the most mythics lit. Returns the y under. */
    int camps(GuiGraphics g, int x, int y, int w, int bottom, int mx, int my) {
        y = head(g, L.t("wynnlabs.endgame.camps"), null, null, null, null, x, y, w, mx, my);
        List<PoolView.Region> regs = tab.d.v().regions(false);
        if (regs.isEmpty()) {
            Type.MUTED.drawWrapped(g, tab.font, L.t(tab.d.lootruns().isEmpty() ? "wynnlabs.endgame.camps_none_runs" : "wynnlabs.endgame.camps_none"),
                    x, y, w, 3, Theme.INK_3);
            return y + Type.BODY.lineHeight;
        }
        PoolView.Region best = tab.pools.best(false, null);
        for (PoolView.Region r : regs) {
            if (y + ROW > bottom) {
                break;
            }
            List<String> f = new ArrayList<>();
            f.add(L.n("wynnlabs.endgame.mythic_n", r.mythics()));
            if (r.shinies() > 0) {
                f.add(L.t("wynnlabs.endgame.shiny_n", r.shinies()));
            }
            if (r.seenAt() >= 0) {
                f.add(L.t("wynnlabs.endgame.pool_age", agoLong(r.seenAt())));
            }
            row(g, x, y, w, TEX_CAMP, "map-pin", EndgamePoolsView.regionName(r), String.join(" · ", f), null, r == best && r.mythics() > 0,
                    true, mx, my, () -> tab.openPool(false, r.name()), null);
            y += ROW + Theme.SP_2;
        }
        return y;
    }

    /** One row: the kind's picture, the name, its line; a seal on the right; lit with a wash, hovered lighter. */
    private void row(GuiGraphics g, int x, int y, int w, String tex, String icon, String name, String line, String seal, boolean lit,
            boolean live, int mx, int my, Runnable open, String count) {
        boolean hover = open != null && mx >= x - Theme.SP_2 && mx < x + w + Theme.SP_2 && my >= y - Theme.SP_1 && my < y + ROW;
        if (lit) {
            Kit.rowLit(g, x - Theme.SP_2, y - Theme.SP_1, w + 2 * Theme.SP_2, ROW + Theme.SP_1);
        } else if (hover) {
            g.fill(x - Theme.SP_2, y - Theme.SP_1, x + w + Theme.SP_2, y + ROW, Theme.CARD_HOVER);
        }
        glyph(g, tex, icon, x, Type.BODY.onCaps(y, ROW_ICON), ROW_ICON, live ? Theme.INK_2 : Theme.INK_3);
        int tx = x + ROW_ICON + Theme.SP_2;
        int sw = 0;
        if (seal != null) {
            sw = Type.META.width(tab.font, seal) + 2 * Theme.SP_2;
            Ui.seal(g, tab.font, seal, x + w - sw, Type.BODY.onCaps(y, Ui.SEAL_H), lit ? Theme.GOLD_EDGE : Theme.INK_3);
            sw += Theme.SP_2;
        }
        Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, name, x + w - sw - tx), tx, y, live ? Theme.INK : Theme.INK_3);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, line, x + w - tx), tx, y + Type.BODY.lineHeight, Theme.INK_3);
        if (open != null) {
            tab.acts.add(new Act(x - Theme.SP_2, y - Theme.SP_1, w + 2 * Theme.SP_2, ROW + Theme.SP_1, open, "endgame:row:" + name));
            List<Component> tip = new ArrayList<>();
            tip.add(title(name, ChatFormatting.GOLD));
            if (count != null && !count.isEmpty()) {
                tip.add(grey(count));
            }
            tip.add(dark(L.t("wynnlabs.endgame.row_click")));
            tab.tips.add(new Tip(x - Theme.SP_2, y - Theme.SP_1, w + 2 * Theme.SP_2, ROW + Theme.SP_1, tip));
        }
    }

    /** "Mythic'ten beri: raid 31 · lootrun 40 · tome 39 çekim" under the raids; returns its height. */
    int dryLine(GuiGraphics g, int x, int y, int w) {
        List<String> f = new ArrayList<>();
        if (tab.d.raidsUp() && tab.d.dry() != null && tab.d.dry().aspectPulls() >= 0) {
            f.add(L.t("wynnlabs.endgame.since_raid", tab.d.dry().aspectPulls()));
        }
        if (tab.d.lootUp() && tab.d.dryPulls() >= 0 && !tab.d.lootruns().isEmpty()) {
            f.add(L.t("wynnlabs.endgame.since_loot", tab.d.dryPulls()));
        }
        if (tab.d.raidsUp() && tab.d.dry() != null && tab.d.dry().tomePulls() >= 0) {
            f.add(L.t("wynnlabs.endgame.since_tome", tab.d.dry().tomePulls()));
        }
        if (f.isEmpty()) {
            return 0;
        }
        String head = L.t("wynnlabs.endgame.since_head");
        Type.META.draw(g, tab.font, head, x, y, Theme.INK_3);
        int hx = x + Type.META.width(tab.font, head) + Theme.SP_2;
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, String.join(" · ", f), x + w - hx), hx, y, Theme.INK_2);
        return Type.META.lineHeight;
    }
}
