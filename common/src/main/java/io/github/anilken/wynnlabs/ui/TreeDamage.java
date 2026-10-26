package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.DamageReport;
import io.github.anilken.wynnlabs.coach.DpsMeter;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The tree page's damage: a block (the model's DPS, per spell and measured on one line, the best
 * three of "what would add most") and the drawer's full view (where the DPS comes from, every
 * what-if, the live meter). Reads {@link DamageReport}, which keeps its own result.
 */
final class TreeDamage extends MenuPaint {

    /** Spells, main attack, summons; then the elements in Damage.ELEMENTS order. */
    private static final int[] SOURCE_COLOURS = {0xFF7F77DD, 0xFFEF9F27, 0xFF1D9E75};
    private static final int[] ELEMENT_COLOURS = {0xFFC9B48F, 0xFF5DA548, 0xFFD9C23A, 0xFF3E8FC9, 0xFFE8584A, 0xFF9AA6A8};

    private final TreeTab tab;

    TreeDamage(RoadmapScreen menu, TreeTab tab) {
        super(menu);
        this.tab = tab;
    }

    /** A gain or share in % with one decimal below 10 ("%4,5", "%12"). */
    private static String pctOf(double v) {
        return RoadmapScreen.pct(Math.abs(v) < 0.05 ? "0" : Math.abs(v) < 10 ? dec(v, 1) : String.valueOf(Math.round(v)));
    }

    /** The block: three numbers, then the best {@code rows} what-ifs; returns the y under it. */
    int block(GuiGraphics g, int x, int y, int w, int rows, int mx, int my) {
        y += tab.side.head(g, L.t("wynnlabs.tree.damage_head"), null, L.t("wynnlabs.tree.all"), "#treedrawer:damage", x, y, w);
        DamageReport.Report report = DamageReport.get();
        if (report == null) {
            Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), L.t("wynnlabs.damage.no_plan"), w), x, y);
            return y + Type.BODY.lineHeight;
        }
        numbers(g, x, y, w, report);
        y += Type.LABEL.lineHeight + Theme.SP_3;
        List<DamageReport.WhatIf> ifs = report.whatIf();
        double max = 0;
        for (DamageReport.WhatIf wi : ifs) {
            max = Math.max(max, wi.gain());
        }
        for (int i = 0; i < ifs.size() && i < rows; i++) {
            y = whatIf(g, x, y, w, ifs.get(i), max, i == 0, mx, my);
        }
        return y;
    }

    /** "2.1k DPS · 829 büyü başı · ~760 ölçülen"; the measured one's ratio and note in its tooltip. */
    private void numbers(GuiGraphics g, int x, int y, int w, DamageReport.Report report) {
        double model = DamageReport.modelPerCast();
        int casts = DamageReport.measuredCasts();
        double measured = DamageReport.measuredPerCast();
        String[][] items = {{QuestAdvisor.fmt(Math.round(report.detail().breakdown().total())), "DPS"},
            {model > 0 ? QuestAdvisor.fmt(Math.round(model)) : "—", L.t("wynnlabs.tree.per_cast")},
            {casts > 0 ? "~" + QuestAdvisor.fmt(Math.round(measured)) : "—", L.t("wynnlabs.tree.measured")}};
        int cx = x;
        for (int i = 0; i < items.length; i++) {
            int vw = Type.LABEL.width(font(), items[i][0]);
            int lw = Type.META.width(font(), items[i][1]);
            if (cx + vw + Theme.SP_1 + lw > x + w) {
                break;
            }
            Type.LABEL.draw(g, font(), items[i][0], cx, y, i == 2 && casts == 0 ? Theme.INK_3 : Theme.INK);
            Type.META.draw(g, font(), items[i][1], cx + vw + Theme.SP_1, y + 1, Theme.INK_3);
            if (i == 2) {
                String note = casts == 0 ? L.t("wynnlabs.damage.none_measured")
                        : casts < DamageReport.ENOUGH_CASTS ? L.n("wynnlabs.damage.few_casts", casts, casts, DamageReport.ENOUGH_CASTS)
                        : L.t("wynnlabs.damage.enough_casts");
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(L.t("wynnlabs.damage.vs_title")).withStyle(ChatFormatting.WHITE));
                if (casts > 0 && model > 0) {
                    tip.add(Component.literal(L.t("wynnlabs.damage.ratio") + " " + RoadmapScreen.pct(Math.round(measured / model * 100)))
                            .withStyle(ChatFormatting.GRAY));
                }
                for (String l : Advice.wrap(note + " " + L.t("wynnlabs.damage.vs_explain"), 48)) {
                    tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
                }
                menu.hits.add(new MenuHit(cx, y, vw + Theme.SP_1 + lw, Type.LABEL.lineHeight, null, tip, null));
            }
            cx += vw + Theme.SP_1 + lw + Theme.SP_6;
        }
    }

    /** One what-if: the change, a green bar of its gain against the best, "+112"; lit when the best. */
    private int whatIf(GuiGraphics g, int x, int y, int w, DamageReport.WhatIf wi, double max, boolean best, int mx, int my) {
        int rh = Theme.SP_2 + Type.BODY.lineHeight + Theme.SP_2;
        if (best) {
            Kit.rowLit(g, x - Theme.SP_2, y, w + 2 * Theme.SP_2, rh);
        }
        int ty = y + Theme.SP_2;
        long gain = Math.round(wi.gain());
        boolean useful = wi.gain() >= 0.5;
        String num = (gain > 0 ? "+" : "") + QuestAdvisor.fmt(gain);
        String share = pctOf(wi.percent());
        int pw = Type.META.width(font(), "%00,0");
        int nw = Type.NUMBER.width(font(), "+00.0k");
        Type.META.drawRight(g, font(), share, x + w, ty + 1, Theme.INK_3);
        Type.NUMBER.drawRight(g, font(), num, x + w - pw - Theme.SP_2, ty, useful ? Theme.GOOD : Theme.INK_3);
        int labelW = Math.min(w * 42 / 100, 150);
        (useful ? Type.BODY : Type.MUTED).draw(g, font(), Type.BODY.fit(font(), L.t(wi.key()), labelW - Theme.SP_2), x, ty);
        int bx = x + labelW;
        int bw = x + w - pw - nw - 2 * Theme.SP_3 - bx;
        if (bw > 16) {
            Art.pill(g, bx, Type.BODY.onCaps(ty, 4), bw, 4, Theme.WELL, 0);
            int fill = (int) Math.round(bw * (max > 0 ? Math.max(0, wi.gain()) / max : 0));
            if (fill >= 2) {
                Art.pill(g, bx, Type.BODY.onCaps(ty, 4), fill, 4, Theme.GREEN, 0);
            }
        }
        menu.hits.add(new MenuHit(x, y, w, rh, null, List.of(Component.literal(L.t(wi.key())).withStyle(ChatFormatting.WHITE),
                L.c("wynnlabs.damage.what_if_tip", QuestAdvisor.fmt(Math.round(wi.dps())), dec(wi.casts(), 2)).withStyle(ChatFormatting.GRAY)), null));
        return y + rh + Theme.SP_2;
    }

    /** The drawer: where the DPS comes from, the live meter, every what-if and the free points; returns its height. */
    int drawer(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int y0 = y;
        DamageReport.Report report = DamageReport.get();
        if (report == null) {
            Type.MUTED.draw(g, font(), L.t("wynnlabs.damage.no_plan"), x, y);
            return Type.BODY.lineHeight;
        }
        boolean two = !menu.layout.compact();
        int cw = two ? (w - Theme.SP_6) / 2 : w;
        numbers(g, x, y, w, report);
        y += Type.LABEL.lineHeight + Theme.SP_5;
        // where it comes from
        Damage.Breakdown b = report.detail().breakdown();
        double total = b.total();
        int ly = y + tab.side.head(g, L.t("wynnlabs.tree.from_head"), null, null, null, x, y, cw);
        String[] sources = {L.t("wynnlabs.damage.src_spell"), L.t("wynnlabs.damage.src_main"), L.t("wynnlabs.damage.src_summon")};
        double[] values = {b.spellTotal(), b.mainTotal(), b.summonTotal()};
        for (int i = 0; i < sources.length; i++) {
            ly = share(g, x, ly, cw, sources[i], values[i], total, SOURCE_COLOURS[i], Type.BODY);
        }
        double weight = DamageReport.mainAttackWeight();
        Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.damage.main_weight", dec(weight, 2)), cw), x, ly, Theme.INK_3);
        menu.hits.add(new MenuHit(x, ly, cw, Type.META.lineHeight, null,
                List.of(L.c("wynnlabs.damage.main_weight_tip", dec(weight, 2)).withStyle(ChatFormatting.GRAY)), null));
        ly += Type.META.lineHeight + Theme.SP_3;
        for (int i = 0; i < Damage.ELEMENTS.length; i++) {
            ly = share(g, x, ly, cw, L.t(DamageReport.ELEMENT_KEYS[i]), b.element(i), total, ELEMENT_COLOURS[i], Type.META);
        }
        ly += Theme.SP_4;
        ly = live(g, x, ly, cw);
        // every what-if
        int rx = two ? x + cw + Theme.SP_6 : x;
        int ry = two ? y : ly + Theme.SP_5;
        String free = report.freePoints() > 0 ? L.n("wynnlabs.damage.free_points", report.freePoints()) : null;
        ry += tab.side.head(g, L.t("wynnlabs.damage.what_if"), free, null, null, rx, ry, cw);
        double max = 0;
        for (DamageReport.WhatIf wi : report.whatIf()) {
            max = Math.max(max, wi.gain());
        }
        boolean first = true;
        for (DamageReport.WhatIf wi : report.whatIf()) {
            ry = whatIf(g, rx, ry, cw, wi, max, first, mx, my);
            first = false;
        }
        if (report.freePoints() <= 0) {
            Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.damage.no_free_points"), cw), rx, ry, Theme.WARN);
            ry += Type.META.lineHeight;
        }
        return Math.max(ly, ry) - y0;
    }

    private int share(GuiGraphics g, int x, int y, int w, String label, double value, double total, int colour, Type role) {
        double s = total > 0 ? value / total : 0;
        String v = QuestAdvisor.fmt(Math.round(value)) + " · " + pctOf(s * 100);
        int vw = Type.META.width(font(), v);
        role.draw(g, font(), Type.BODY.fit(font(), label, 80), x, y, role == Type.META ? Theme.INK_2 : Theme.INK);
        int bx = x + 84;
        int bw = x + w - vw - Theme.SP_3 - bx;
        if (bw > 10) {
            Art.pill(g, bx, role.onCaps(y, 4), bw, 4, Theme.WELL, 0);
            if (s > 0) {
                Art.pill(g, bx, role.onCaps(y, 4), Math.max(2, (int) Math.round(bw * s)), 4, colour, 0);
            }
        }
        Type.META.drawRight(g, font(), v, x + w, y + (role == Type.META ? 0 : 1), value > 0 ? Theme.INK_2 : Theme.INK_3);
        return y + role.lineHeight + Theme.SP_2;
    }

    /** The live meter: the last 10 seconds' DPS, the session's best, this fight. */
    private int live(GuiGraphics g, int x, int y, int w) {
        DpsMeter.State s = DpsMeter.LIVE.state();
        y += tab.side.head(g, L.t("wynnlabs.damage.live_title"), null, null, null, x, y, w);
        if (s.sessionDamage() <= 0) {
            Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), L.t("wynnlabs.damage.live_empty"), w), x, y);
            return y + Type.BODY.lineHeight;
        }
        String dps = QuestAdvisor.fmt(Math.round(s.dps())) + " DPS";
        Type.LABEL.draw(g, font(), dps, x, y, s.fighting() ? Theme.WARN : Theme.INK);
        String best = L.t("wynnlabs.damage.live_best", QuestAdvisor.fmt(Math.round(s.best())));
        Type.META.draw(g, font(), Type.META.fit(font(), best, w - Type.LABEL.width(font(), dps) - Theme.SP_4),
                x + Type.LABEL.width(font(), dps) + Theme.SP_4, y + 1, Theme.INK_3);
        y += Type.LABEL.lineHeight + Theme.SP_1;
        String fight = L.t(s.fighting() ? "wynnlabs.damage.live_fight" : "wynnlabs.damage.live_last_fight",
                QuestAdvisor.fmt(Math.round(s.fightDamage())), Math.round(s.fightSeconds()));
        Type.META.draw(g, font(), Type.META.fit(font(), fight, w), x, y, Theme.INK_3);
        return y + Type.META.lineHeight;
    }
}
