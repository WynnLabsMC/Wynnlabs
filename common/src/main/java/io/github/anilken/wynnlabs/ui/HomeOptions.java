package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.roadmap.PanelOptions;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A gear slot's options as a comparison table: the title row (filter, Back), the column heads
 * (the values any option changes, then Build), "Now" with today's values, then one row per option,
 * best gain first: the item, its name and where it is had, only the difference in each column
 * ("—" when the same), a flag when it has a catch, the build's gain as a seal and a copy button.
 * Everything else (the catches, the points, the old → new values) is in the tooltips. A click on
 * a row or its copy button copies the name. The state lives on {@link HomePage}.
 */
final class HomeOptions extends MenuPaint {

    /** A row: two lines (name, source) beside the 18 px slot; its wash SP_2 round it, SP_2 of plain page to the next. */
    static final int ROW = Type.LABEL.lineHeight + Type.META.lineHeight;
    static final int PITCH = ROW + 3 * Theme.SP_2;
    /** A value column at least this wide when the panel has room (a large window). */
    private static final int COL_MIN = 44;
    private static final int SLOT = 18;
    private static final int NAME_MIN = 96;

    private final HomePage page;
    /** The columns for the shown list, worked out when the list or the width changes. */
    private List<Roadmap.Option> colsFor;
    private int colsW;
    private int[] cols = new int[0];
    private int[] colW = new int[0];
    private int sealW;

    HomeOptions(RoadmapScreen menu, HomePage page) {
        super(menu);
        this.page = page;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<Roadmap.Option> all = page.detail.options() == null ? List.of() : page.detail.options();
        Roadmap.Option pick = all.isEmpty() ? null : all.get(0);
        List<Roadmap.Option> opts = PanelOptions.shown(all, page.detailMarket, Wyn.level());
        int ty = title(g, x, y, w, mx, my);
        // the list's top, known before anything is drawn: the rows (and Now with them) leave room
        // for the scroll bar only when it is there, so every right edge lines up
        int nowH = ROW + 2 * Theme.SP_1;
        int hy = ty + Type.META.lineHeight + Theme.SP_1;
        int ny = hy + 1 + Theme.SP_3;
        int top = ny + nowH + Theme.SP_4 + Theme.SP_2;
        int bottom = y + h - 2;
        boolean scrolls = opts.size() * PITCH > bottom - top;
        int cw = scrolls ? w - ScrollBar.W - Theme.SP_2 : w;
        layout(opts, pick, cw);

        // the value columns' heads (the names need none) over a hairline
        int hx = x + Theme.SP_2 + SLOT + Theme.SP_3;
        int rx = right(x, cw);
        for (int c = cols.length - 1; c >= 0; c--) {
            Type.META.drawRight(g, font(), Damage.factor(cols[c]), rx, ty, Theme.INK_3);
            rx -= colW[c] + Theme.SP_4;
        }
        Type.META.drawRight(g, font(), L.t("wynnlabs.home.card_build"), x + cw - Theme.SP_2 - Ui.CHIP_H - 2 - Theme.SP_3, ty,
                Theme.INK_3);
        Kit.hair(g, x, hy, cw, 1, Theme.LINE);

        // what you wear now, on a soft card
        Kit.softCard(g, x, ny, cw, nowH);
        int sy = ny + (nowH - SLOT) / 2;
        Kit.slot(g, x + Theme.SP_2, sy, SLOT, 0);
        ItemStack worn = page.detailWorn;
        if (!worn.isEmpty()) {
            g.renderItem(worn, x + Theme.SP_2 + 1, sy + 1);
            menu.hits.add(new MenuHit(x + Theme.SP_2 + 1, sy + 1, 16, 16, worn, null, null));
        }
        String nowLabel = L.t("wynnlabs.home.card_now");
        int line = Type.BODY.yIn(ny, nowH);
        Type.META.draw(g, font(), nowLabel, hx, line + 1, Theme.INK_3);
        int wx = hx + Type.META.width(font(), nowLabel) + Theme.SP_2;
        String wn = worn.isEmpty() ? L.cap(L.t("wynnlabs.screen.empty")) : ItemJson.clean(worn.getHoverName().getString());
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), wn, nameRight(x, cw) - wx), wx, line, Theme.INK);
        if (pick != null) {
            rx = right(x, cw);
            for (int c = cols.length - 1; c >= 0; c--) {
                Type.BODY.drawRight(g, font(), value(pick.from(), cols[c]), rx, line, Theme.INK_2);
                rx -= colW[c] + Theme.SP_4;
            }
        }

        // the options, scrolled by the wheel and the bar
        int listH = opts.size() * PITCH;
        page.detailScrollMax = Math.max(0, listH - (bottom - top));
        page.detailScroll = Math.min(page.detailScroll, page.detailScrollMax);
        page.listRect = new int[] {x, top, w, bottom - top};
        int hitsFrom = menu.hits.size();
        g.enableScissor(x - Theme.SP_2, top - Theme.SP_2, x + w, bottom);
        int ry = top - page.detailScroll;
        for (int i = 0; i < opts.size(); i++) {
            if (ry + ROW > top && ry < bottom) {
                row(g, opts.get(i), opts.get(i) == pick, x, ry, cw, mx, my, top, bottom);
            }
            ry += PITCH;
        }
        g.disableScissor();
        // a tooltip only where its row is in view
        menu.hits.subList(hitsFrom, menu.hits.size()).removeIf(hb -> hb.y() < top || hb.y() + hb.h() > bottom);
        page.detailBar.place(x + w - ScrollBar.W, top, bottom - top, bottom - top, listH, page.detailScroll);
        page.detailBar.draw(g, mx, my);
        if (opts.size() <= (page.detailMarket ? 0 : 1)) {
            Type.MUTED.draw(g, font(), L.t(page.detailMarket ? "wynnlabs.home.detail_none_market" : "wynnlabs.home.detail_none"),
                    x, ry + Theme.SP_1, Theme.INK_3);
        }
    }

    /** The slot's name, "options, by gain" where it fits, the filter and Back; returns the y under it. */
    private int title(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String back = L.t("wynnlabs.home.back");
        int bw = Ui.iconChipWidth(font(), "chevron-left", back, false);
        String[] fl = {L.t("wynnlabs.home.filter_all"), L.t("wynnlabs.home.filter_market")};
        int fx = x + w - bw - Theme.SP_3 - Ui.chipWidth(font(), fl[0]) - Theme.SP_2 - Ui.chipWidth(font(), fl[1]);
        String label = Type.LABEL.fit(font(), page.detailLabel, fx - x - Theme.SP_3);
        Type.LABEL.draw(g, font(), label, x, y, BuildControls.CHOSEN);
        String sub = L.t("wynnlabs.home.detail_sub");
        int subX = x + Type.LABEL.width(font(), label) + Theme.SP_3;
        if (subX + Type.META.width(font(), sub) + Theme.SP_4 <= fx) {
            Type.META.draw(g, font(), sub, subX, y + 1, Theme.INK_3);
        }
        int cy = Type.LABEL.onCaps(y, Ui.CHIP_H);
        Ui.iconChip(g, font(), "chevron-left", back, false, x + w - bw, cy, bw, false, mx, my);
        page.backRect = new int[] {x + w - bw, cy, bw, Ui.CHIP_H};
        page.filterRects = new int[2][];
        for (int i = 0; i < 2; i++) {
            int fw = Ui.chipWidth(font(), fl[i]);
            Ui.chip(g, font(), fl[i], fx, cy, fw, page.detailMarket == (i == 1), false, false, mx, my);
            page.filterRects[i] = new int[] {fx, cy, fw, Ui.CHIP_H};
            fx += fw + Theme.SP_2;
        }
        return cy + Ui.CHIP_H + Theme.SP_3;
    }

    /** One option's row: lit when it is the road map's pick, lighter under the mouse. */
    private void row(GuiGraphics g, Roadmap.Option o, boolean pick, int x, int y, int w, int mx, int my, int top, int bottom) {
        boolean inList = my >= top && my < bottom;
        boolean hover = inList && mx >= x && mx < x + w && my >= y - Theme.SP_2 && my < y + ROW + Theme.SP_2;
        if (pick) {
            Kit.rowLit(g, x, y - Theme.SP_2, w, ROW + 2 * Theme.SP_2);
        } else if (hover) {
            Art.box(g, x, y - Theme.SP_2, w, ROW + 2 * Theme.SP_2, 4, Theme.CARD_HOVER, 0);
        }
        ItemData info = o.info();
        ItemStack icon = menu.icon(info);
        int sy = y + (ROW - SLOT) / 2;
        Kit.slot(g, x + Theme.SP_2, sy, SLOT, 0);
        g.renderItem(icon, x + Theme.SP_2 + 1, sy + 1);
        menu.hits.add(new MenuHit(x + Theme.SP_2 + 1, sy + 1, 16, 16, icon, null, null));

        // the name in its tier's colour and the level, where it is had under them
        int tx = x + Theme.SP_2 + SLOT + Theme.SP_3;
        int nr = nameRight(x, w);
        String lv = "Lv " + info.requirements().level();
        int lvW = Type.META.width(font(), lv);
        String nm = Type.LABEL.fit(font(), info.name(), nr - tx - Theme.SP_2 - lvW);
        Integer rgb = info.tier().colour().getColor();
        Type.LABEL.draw(g, font(), nm, tx, y, rgb == null ? Theme.INK : 0xFF000000 | Theme.paperRgb(rgb));
        Type.META.draw(g, font(), lv, tx + Type.LABEL.width(font(), nm) + Theme.SP_2, y + 1, Theme.INK_3);
        String where = o.price() != null ? o.price()
                : Shopping.tradable(info) ? L.cap(o.source()) : L.t("wynnlabs.home.card_untradable", L.cap(o.source()));
        Type.META.draw(g, font(), Type.META.fit(font(), where, nr - tx), tx, y + Type.LABEL.lineHeight, Theme.INK_2);
        menu.hits.add(new MenuHit(tx, y, nr - tx, ROW, null, rowTip(o), null));

        // the differences, right-aligned in their columns
        int line = Type.LABEL.yIn(y, ROW);
        int rx = right(x, w);
        double[] p = o.factorPct();
        for (int c = cols.length - 1; c >= 0; c--) {
            int i = cols[c];
            double v = i < p.length ? p[i] : 0;
            if (Math.abs(v) < 0.5) {
                Type.BODY.drawRight(g, font(), "—", rx, line, Theme.INK_3);
            } else {
                String t = pct(v);
                Type.LABEL.drawRight(g, font(), t, rx, line, v > 0 ? Theme.GOOD : Theme.BAD);
                int vw = Type.LABEL.width(font(), t);
                menu.hits.add(new MenuHit(rx - vw, y, vw, ROW, null, List.of(
                        Component.literal(Damage.factor(i) + " · " + change(o, i)).withStyle(ChatFormatting.WHITE)), null));
            }
            rx -= colW[c] + Theme.SP_4;
        }

        // the flag, the gain, the copy button
        int bx = x + w - Theme.SP_2 - (Ui.CHIP_H + 2);
        int by = y + (ROW - Ui.CHIP_H) / 2;
        int gx = bx - Theme.SP_3 - sealW;
        String flag = flag(o);
        if (flag != null) {
            Art.icon(g, flag, gx - Theme.SP_3 - 8, y + (ROW - 8) / 2, 8, flag.equals("alert-triangle") ? Theme.WARN : Theme.INK_3);
        }
        gain(g, o.gain(), gx, y + (ROW - Ui.CHIP_H) / 2);
        Ui.iconButton(g, "copy", bx, by, false, inList ? mx : -1, my);
        menu.hits.add(new MenuHit(bx, by, Ui.CHIP_H + 2, Ui.CHIP_H, null,
                List.of(L.c("wynnlabs.home.card_copy").withStyle(ChatFormatting.WHITE)), null));
        page.cardHits.add(new Object[] {new int[] {x, y, w, ROW}, info});
    }

    /** The build's gain as a light seal in its colour, {@link #sealW} wide for every row. */
    private void gain(GuiGraphics g, double v, int x, int y) {
        String t = pct(v);
        int ink = v >= 0 ? Theme.GOOD : Theme.BAD;
        Art.box(g, x, y, sealW, Ui.CHIP_H, Ui.CHIP_H / 2, v >= 0 ? 0xFFE3EED8 : 0xFFF3DCD6, ink);
        Type.LABEL.draw(g, font(), t, x + (sealW - Type.LABEL.width(font(), t)) / 2, Type.LABEL.yIn(y, Ui.CHIP_H), ink);
    }

    /**
     * "hierarchy" for a set's pick (judged with the rest of its set), "alert-triangle" for a
     * downside, "arrows-horizontal" for skill points to move, else null.
     */
    private static String flag(Roadmap.Option o) {
        if (o.set()) {
            return "hierarchy";
        }
        if (!o.cautions().isEmpty()) {
            return "alert-triangle";
        }
        return o.skills().isEmpty() ? null : "arrows-horizontal";
    }

    /** The row's tooltip: the name, its catches and points, what stays the same, what a click does. */
    private List<Component> rowTip(Roadmap.Option o) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(o.info().name()).withStyle(o.info().tier().colour()));
        if (o.set()) {
            tip.add(L.c("wynnlabs.home.option_set").withStyle(ChatFormatting.AQUA));
        }
        int lv = o.info().requirements().level();
        if (lv > Wyn.level()) {
            tip.add(L.c("wynnlabs.home.chip_wear_at", lv).withStyle(ChatFormatting.GOLD));
        }
        Integer seen = Rules.communityLevel(o.info().name());
        if (seen != null) {
            tip.add(L.c("wynnlabs.home.chip_community", seen).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (o.cautions().isEmpty()) {
            tip.add(L.c("wynnlabs.home.chip_no_cons").withStyle(ChatFormatting.GREEN));
        }
        for (String c : o.cautions()) {
            tip.add(Component.literal(c).withStyle(ChatFormatting.RED));
        }
        tip.add((o.skills().isEmpty() ? L.c("wynnlabs.home.chip_no_points")
                : L.c("wynnlabs.home.chip_points", o.skills() + (o.respec() ? L.t("wynnlabs.home.chip_respec") : "")))
                .withStyle(ChatFormatting.GOLD));
        List<String> same = new ArrayList<>();
        double[] p = o.factorPct();
        for (int i = 0; i < 6 && i < p.length; i++) {
            if (Math.abs(p[i]) < 0.5) {
                same.add(Damage.factor(i));
            }
        }
        if (!same.isEmpty()) {
            tip.add(L.c("wynnlabs.home.card_same", String.join(", ", same)).withStyle(ChatFormatting.GRAY));
        }
        tip.add(L.c("wynnlabs.home.row_copy_tip").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    // ---- the columns ----

    /**
     * The columns: every value an option in the list changes, in the model's order, as wide as
     * their widest entry; in a narrow panel the ones with the smallest changes go first, so the
     * name keeps NAME_MIN. Worked out again only when the list or the width changes.
     */
    private void layout(List<Roadmap.Option> opts, Roadmap.Option pick, int w) {
        if (opts == colsFor && w == colsW) {
            return;
        }
        colsFor = opts;
        colsW = w;
        double[] most = new double[6];
        sealW = Type.LABEL.width(font(), "+00%") + 2 * Theme.SP_3;
        for (Roadmap.Option o : opts) {
            double[] p = o.factorPct();
            for (int i = 0; i < 6 && i < p.length; i++) {
                most[i] = Math.max(most[i], Math.abs(p[i]) >= 0.5 ? Math.abs(p[i]) : 0);
            }
            sealW = Math.max(sealW, Type.LABEL.width(font(), pct(o.gain())) + 2 * Theme.SP_3);
        }
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (most[i] > 0) {
                keep.add(i);
            }
        }
        int[] widths = new int[6];
        for (int i : keep) {
            int cw = Type.META.width(font(), Damage.factor(i));
            if (pick != null) {
                cw = Math.max(cw, Type.BODY.width(font(), value(pick.from(), i)));
            }
            for (Roadmap.Option o : opts) {
                double[] p = o.factorPct();
                if (i < p.length) {
                    cw = Math.max(cw, Type.LABEL.width(font(), pct(p[i])));
                }
            }
            widths[i] = cw;
        }
        // drop the smallest changes until the name has its room
        while (!keep.isEmpty() && fixedRight(keep, widths) + NAME_MIN > w) {
            int least = keep.get(0);
            for (int i : keep) {
                if (most[i] < most[least]) {
                    least = i;
                }
            }
            keep.remove(Integer.valueOf(least));
        }
        cols = keep.stream().mapToInt(Integer::intValue).toArray();
        // with room to spare (a large window) the columns open up to COL_MIN, the name keeps twice its least
        int[] wide = widths.clone();
        for (int i : keep) {
            wide[i] = Math.max(wide[i], COL_MIN);
        }
        boolean roomy = fixedRight(keep, wide) + 2 * NAME_MIN <= w;
        colW = new int[cols.length];
        for (int c = 0; c < cols.length; c++) {
            colW[c] = roomy ? wide[cols[c]] : widths[cols[c]];
        }
    }

    /** Everything right of the name: the slot, the columns, the flag, the seal, the copy button. */
    private int fixedRight(List<Integer> keep, int[] widths) {
        int r = Theme.SP_2 + SLOT + Theme.SP_3 + Theme.SP_4;
        for (int i : keep) {
            r += widths[i] + Theme.SP_4;
        }
        return r + 8 + Theme.SP_3 + sealW + Theme.SP_3 + Ui.CHIP_H + 2 + Theme.SP_2;
    }

    /** The right edge of the last value column. */
    private int right(int x, int w) {
        return x + w - Theme.SP_2 - (Ui.CHIP_H + 2) - Theme.SP_3 - sealW - Theme.SP_3 - 8 - Theme.SP_4;
    }

    /** Where the name's room ends: SP_4 before the first column. */
    private int nameRight(int x, int w) {
        int r = right(x, w);
        for (int c = 0; c < cols.length; c++) {
            r -= colW[c] + Theme.SP_4;
        }
        return r - (cols.length == 0 ? Theme.SP_4 : 0);
    }

    // ---- the values ----

    /** Value {@code i} of a build as the column shows it. */
    private static String value(Damage.Result r, int i) {
        return switch (i) {
            case 0 -> String.valueOf(Math.round(r.spellDps()));
            case 1 -> String.valueOf(Math.round(r.ehp()));
            // the mana stats behind the mana part: mana back every 5 s, the spell cost cut
            case 2 -> Math.round(r.manaPer5()) + "/5s" + (Math.abs(r.costCut()) >= 0.005
                    ? " −" + Math.round(r.costCut() * 100) + "%" : "");
            case 3 -> signed(r.speed()) + "%";
            case 4 -> Math.round(r.xp()) + "%";
            default -> r.factors().length > 5 ? "×" + String.format(Locale.ROOT, "%.2f", r.factors()[5]) : "";
        };
    }

    private static String change(Roadmap.Option o, int i) {
        return value(o.from(), i) + " → " + value(o.to(), i);
    }

    private static String pct(double v) {
        return (v >= 0 ? "+" : "−") + RoadmapScreen.pct(Math.round(Math.abs(v)));
    }

    private static String signed(double v) {
        long n = Math.round(v);
        return n > 0 ? "+" + n : String.valueOf(n);
    }
}
