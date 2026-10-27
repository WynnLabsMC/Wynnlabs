package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.BoxVerdict;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Listings;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.search.BoxInsight;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.ItemIndex;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * The box window's summary card: the numbers that answer "what is it worth" (label over value,
 * hairlines between), on the right the verdict seal (a box) or your roll (one item) with a
 * magnifier into Search beside it, and under a hairline the answer line: is identifying worth it
 * (a box), or where your roll sits and a price that would put yours near the top (one rolled
 * item). Its (i) explains how; too little data says so instead of guessing.
 */
final class BoxSummary {

    private BoxSummary() {}

    private record Cell(String label, String value, int ink) {}

    private static final int VERDICT_OK = 0xFF3F7A2E;

    /** What the card shows, made once per rebuild of the rows (and when the window's size class changes). */
    private record Model(List<Cell> cells, String seal, int sealInk, String sub, String[] line, int lineInk,
                         List<Component> lineTip, List<Component> sealTip, String link) {}

    private static Model model;
    private static int modelFor = -1;
    private static boolean modelCompact;
    private static BoxScreen modelScreen;

    private static Model build(BoxScreen s) {
        boolean live = Listings.live();
        List<Cell> cells = new ArrayList<>();
        String seal = null;
        int sealInk = VERDICT_OK;
        String sub = null;
        String[] line = null;           // icon, head, numbers; ink in lineInk
        int lineInk = Theme.INK_3;
        List<Component> lineTip = null;
        List<Component> sealTip = null;
        String link = null;
        if (s.box != null) {
            BoxMarket.Summary sm = s.summary;
            cells.add(new Cell(L.t("wynnlabs.box.cell_average"), live ? BoxScreen.money(sm == null ? null : sm.average()) : "—", Theme.PRICE));
            cells.add(new Cell(L.t("wynnlabs.box.cell_best"), live ? BoxScreen.money(sm == null ? null : sm.best()) : "—", Theme.PRICE));
            Double unid = sm == null ? null : sm.unidentified();
            cells.add(new Cell(L.t("wynnlabs.box.cell_unid"), live ? BoxScreen.money(unid) : "—", unid == null ? Theme.INK_3 : Theme.PRICE));
            String typeName = s.boxName();
            link = typeName;                // Search opens on it as a query when it has no card
            BoxInsight.Identify id = s.identify;
            if (live && id != null) {
                boolean sure = id.answer() == BoxInsight.Answer.WORTH || id.answer() == BoxInsight.Answer.SELL;
                if (sure && s.verdict != null) {
                    seal = s.verdict.text();
                    sealInk = switch (s.verdict.label()) {
                        case IDENTIFY -> VERDICT_OK;
                        case SELL -> Theme.MARK_SELL;
                        case KEEP -> Theme.NEAR;
                    };
                    sealTip = new ArrayList<>();
                    for (String t : s.verdict.tipLines()) {
                        sealTip.add(Component.literal(t).withStyle(ChatFormatting.GRAY));
                    }
                }
                line = identifyLine(id, s.compact);
                lineInk = id.answer() == BoxInsight.Answer.WORTH ? Theme.GOOD : sure ? Theme.INK : Theme.INK_3;
                lineTip = identifyTip(id);
            }
        } else {
            BoxMarket.Row r = s.rows.isEmpty() ? null : s.rows.get(0);
            int count = r == null ? 0 : r.count();
            String lowest = BoxScreen.money(r == null || r.lowest() == null ? null : (double) r.lowest());
            cells.add(new Cell(L.t("wynnlabs.box.cell_listings"), String.valueOf(count), Theme.INK));
            cells.add(new Cell(L.t("wynnlabs.box.cell_lowest"), lowest, Theme.PRICE));
            if (!s.rolled()) {
                cells.add(new Cell(L.t("wynnlabs.box.cell_stock"), r == null ? "?" : String.valueOf(BoxPlainRows.stock(r.listings())), Theme.INK));
            } else {
                cells.add(new Cell(L.t("wynnlabs.box.cell_median"),
                        BoxScreen.money(r == null || r.median() == null ? null : (double) r.median()), Theme.PRICE));
                if (r != null && r.bestRoll() != null) {
                    cells.add(new Cell(L.t("wynnlabs.box.cell_best_roll"), BoxScreen.pct(r.bestRoll()), Theme.GOOD));
                }
                Float own = s.single.ownRoll();
                if (own != null && r != null) {
                    seal = L.t("wynnlabs.box.own_seal", BoxScreen.pct(Math.round(own)));
                    int better = BoxMarket.better(r.listings(), own);
                    sub = L.n("wynnlabs.box.own_better", better, better);
                    Integer top = BoxInsight.topPercent(r.listings(), own);
                    BoxInsight.Suggestion sg = BoxInsight.suggest(r.listings(), own);
                    int n = BoxInsight.current(r.listings());
                    if (top == null || sg == null) {
                        line = new String[] {"trending-up", L.t("wynnlabs.box.rank_few"),
                                L.t("wynnlabs.box.rank_few_numbers", n, BoxInsight.MIN_LISTINGS)};
                    } else {
                        String price = "~" + Prices.fmt(sg.price());
                        line = new String[] {"trending-up", L.t("wynnlabs.box.rank_top", top),
                                s.compact ? L.t("wynnlabs.box.rank_suggest_short", price, sg.rank())
                                        : L.t("wynnlabs.box.rank_suggest", price, sg.rank())};
                        lineInk = Theme.GOOD;
                    }
                    lineTip = List.of(L.c("wynnlabs.box.rank_tip1", BoxScreen.pct(Math.round(own)), n).withStyle(ChatFormatting.GRAY),
                            L.c("wynnlabs.box.rank_tip2", BoxInsight.TARGET_RANK, (int) BoxInsight.BAND).withStyle(ChatFormatting.GRAY),
                            L.c("wynnlabs.box.rank_tip3").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        return new Model(cells, seal, sealInk, sub, line, lineInk, lineTip, sealTip, link);
    }

    /** Draws it from x, y in w; returns its height (the gap after it is the caller's). */
    static int draw(BoxScreen s, GuiGraphics g, int x, int y, int w, int mx, int my) {
        if (model == null || modelScreen != s || modelFor != s.generation || modelCompact != s.compact) {
            model = build(s);
            modelScreen = s;
            modelFor = s.generation;
            modelCompact = s.compact;
        }
        Model m = model;
        List<Cell> cells = m.cells();
        String seal = m.seal();
        int sealInk = m.sealInk();
        String sub = m.sub();
        String[] line = m.line();
        int lineInk = m.lineInk();
        List<Component> lineTip = m.lineTip();
        List<Component> sealTip = m.sealTip();
        String link = m.link();
        int top = Type.META.lineHeight + Type.NUMBER.lineHeight + 2 * Theme.SP_3;
        int h = top + (line == null ? 0 : 1 + Theme.SP_3 + Type.BODY.lineHeight + Theme.SP_3);
        Art.box(g, x, y, w, h, 5, 0xFFF4EAD0, 0x40A08A5A);
        // the right end first: the seal (and its META under it), the magnifier beside it
        int right = x + w - Theme.SP_4;
        int sealY = y + Theme.SP_3;
        if (link != null) {
            right -= Ui.CHIP_H + 2;
            s.bar.searchButton(g, right, sealY + (Ui.SEAL_H - Ui.CHIP_H) / 2, link, mx, my);
            right -= Theme.SP_2;
        }
        int cellsEnd = right;
        if (seal != null) {
            int sw = Type.META.width(s.font(), seal) + 2 * Theme.SP_2 + 8 + Theme.SP_1;
            int sx = right - sw;
            Art.box(g, sx, sealY, sw, Ui.SEAL_H, Ui.SEAL_H / 2, sealInk, 0);
            Art.icon(g, "check", sx + Theme.SP_2, sealY + Ui.iconIn(Ui.SEAL_H, 7), 7, Theme.CREAM);
            Type.META.draw(g, s.font(), seal, sx + Theme.SP_2 + 8 + Theme.SP_1, Type.META.yIn(sealY, Ui.SEAL_H), Theme.CREAM);
            if (sealTip != null && BoxChips.in(mx, my, sx, sealY, sw, Ui.SEAL_H)) {
                s.tip(sealTip);
            }
            cellsEnd = sx;
            if (sub != null && !s.compact) {
                Type.META.drawRight(g, s.font(), sub, x + w - Theme.SP_4, sealY + Ui.SEAL_H + Theme.SP_1, Theme.INK_3);
                cellsEnd = Math.min(cellsEnd, x + w - Theme.SP_4 - Type.META.width(s.font(), sub));
            }
        }
        int cx = x + Theme.SP_4;
        int gap = s.compact ? Theme.SP_5 : Theme.SP_6;
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            int cw = Math.max(Type.META.width(s.font(), c.label()), Type.NUMBER.width(s.font(), c.value()));
            if (cx + cw > cellsEnd - Theme.SP_3) {
                break;
            }
            Type.META.draw(g, s.font(), c.label(), cx, y + Theme.SP_3, Theme.INK_3);
            Type.NUMBER.draw(g, s.font(), c.value(), cx, y + Theme.SP_3 + Type.META.lineHeight, c.ink());
            cx += cw + gap;
            if (i < cells.size() - 1) {
                Kit.hair(g, cx - gap / 2, y + Theme.SP_3 + 1, 1, top - 2 * Theme.SP_3 - 2, 0x40A08A5A);
            }
        }
        if (line != null) {
            int ly = y + top;
            Kit.hair(g, x + Theme.SP_4, ly, w - 2 * Theme.SP_4, 1, 0x40A08A5A);
            ly += 1 + Theme.SP_3;
            int lx = x + Theme.SP_4;
            Art.icon(g, line[0], lx, Type.BODY.onCaps(ly, 8), 8, lineInk);
            lx += 8 + Theme.SP_2;
            Type.LABEL.draw(g, s.font(), line[1], lx, ly, lineInk);
            lx += Type.LABEL.width(s.font(), line[1]) + Theme.SP_3;
            int info = x + w - Theme.SP_4 - 8;
            Type.META.draw(g, s.font(), Type.META.fit(s.font(), line[2], info - Theme.SP_3 - lx), lx, ly + 1, Theme.INK_2);
            Art.icon(g, "info-circle", info, Type.BODY.onCaps(ly, 8), 8, Theme.INK_3);
            if (lineTip != null && BoxChips.in(mx, my, x, ly - Theme.SP_1, w, Type.BODY.lineHeight + Theme.SP_2)) {
                s.tip(lineTip);
            }
        }
        return h;
    }

    /** "Worth identifying · expected ~3.2 LE · fee 57 E · 6/8 items priced", or why there is no answer. */
    private static String[] identifyLine(BoxInsight.Identify id, boolean compact) {
        String exp = id.expected() == null ? "?" : "~" + Prices.fmt(id.expected());
        String fee = id.costLow() == id.costHigh() ? id.costLow() + " E" : id.costLow() + "-" + id.costHigh() + " E";
        return switch (id.answer()) {
            case WORTH, SELL -> new String[] {"coin", L.t(id.answer() == BoxInsight.Answer.WORTH ? "wynnlabs.box.answer_worth" : "wynnlabs.box.answer_sell"),
                compact ? L.t("wynnlabs.box.answer_short", exp, fee) : L.t("wynnlabs.box.answer_numbers", exp, fee, id.priced(), id.outcomes())};
            case FEW -> new String[] {"coin", L.t("wynnlabs.box.answer_few"), L.t("wynnlabs.box.answer_few_numbers", id.priced(), id.outcomes())};
            case NO_COST -> new String[] {"coin", L.t("wynnlabs.box.answer_no_cost"), L.t("wynnlabs.box.answer_no_cost_numbers", exp)};
        };
    }

    private static List<Component> identifyTip(BoxInsight.Identify id) {
        return List.of(L.c("wynnlabs.box.answer_tip1", id.priced(), id.outcomes()).withStyle(ChatFormatting.GRAY),
                L.c("wynnlabs.box.answer_tip2", Math.round(BoxVerdict.marketFee())).withStyle(ChatFormatting.GRAY));
    }
}
