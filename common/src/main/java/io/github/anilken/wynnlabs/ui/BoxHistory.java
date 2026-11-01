package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.History;
import io.github.anilken.wynnlabs.market.Prices;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The box window's price history of one item ({@link History}): the chart of daily medians with
 * the listings seen as faint bars under it, the four numbers under the chart (last sold, lowest,
 * typical, listings seen), and the one-line strip over the listings with a tiny sparkline that
 * opens the chart. Drawn by {@link BoxListingsPanel}; nothing here asks the network.
 */
final class BoxHistory {

    /** The strip's height over the listings. */
    static final int STRIP_H = 12 + 2 * Theme.SP_1;
    private static final DateTimeFormatter EN = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter TR = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr"));
    /** The listings-seen bars: the hint ink, faint. */
    private static final int BAR = (Theme.INK_3 & 0x00FFFFFF) | 0x38000000;
    private static final int GRID = (Theme.LINE & 0x00FFFFFF) | 0x40000000;
    private static final int CELL = 0x26A08A5A;

    private BoxHistory() {}

    /** "Oct 18" (Turkish: "18 Eki"). */
    static String date(LocalDate d) {
        return d.format("tr_tr".equals(L.code()) ? TR : EN);
    }

    private static String price(Integer v) {
        return v == null ? "–" : Prices.fmt(v);
    }

    // ---- the chart ----

    /** The listings-seen bars' greatest height. */
    static final int BAR_H = 8;

    /**
     * The chart in {@code w} from {@code y}, top to bottom: the line area ({@code chartH} tall),
     * SP_1, the listings-seen bars ({@link #BAR_H}) on the axis, SP_2, the dates. Nothing overlaps.
     * Hovering a day's column lights it and tells its date, median, range and listings seen.
     * Returns the bottom.
     */
    static int chart(BoxScreen s, GuiGraphics g, History.Series h, int x, int y, int w, int chartH, int mx, int my) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int maxCount = 0;
        for (History.Day d : h.days()) {
            if (d.p50() != null) {
                min = Math.min(min, d.p50());
                max = Math.max(max, d.p50());
            }
            maxCount = Math.max(maxCount, d.count());
        }
        if (min > max) {
            return y;
        }
        String top = Prices.fmt(max);
        String low = Prices.fmt(min);
        int labelW = Math.max(Type.META.width(s.font(), top), Type.META.width(s.font(), low)) + Theme.SP_2;
        int px = x + labelW;
        int pw = Math.max(8, w - labelW);
        LocalDate first = h.days().get(0).date();
        LocalDate last = h.days().get(h.days().size() - 1).date();
        long span = ChronoUnit.DAYS.between(first, last);
        int lineTop = y + Type.META.lineHeight / 2;
        int lineBot = y + chartH - Type.META.lineHeight / 2;
        int axis = y + chartH + Theme.SP_1 + BAR_H;
        int dateY = axis + 1 + Theme.SP_2;
        int bottom = dateY + Type.META.lineHeight;
        // the day under the mouse: the nearest column while the mouse is over the chart
        History.Day hover = null;
        if (BoxChips.in(mx, my, px, y, pw, bottom - y)) {
            int best = Integer.MAX_VALUE;
            for (History.Day d : h.days()) {
                int dist = Math.abs(xOf(d.date(), first, span, px, pw) - mx);
                if (dist < best) {
                    best = dist;
                    hover = d;
                }
            }
        }
        // the listings seen, faint, in their own band under the line area
        int bw = span == 0 ? 4 : Math.max(1, (int) (pw / (span + 1)) - 1);
        for (History.Day d : h.days()) {
            if (d.count() <= 0 || maxCount == 0) {
                continue;
            }
            int bh = Math.max(1, Math.round(BAR_H * (float) d.count() / maxCount));
            int bx = Math.max(px, Math.min(px + pw - bw, xOf(d.date(), first, span, px, pw) - bw / 2));
            g.fill(bx, axis - bh, bx + bw, axis, d == hover ? Theme.INK_3 : BAR);
        }
        if (hover != null) {
            int hx = xOf(hover.date(), first, span, px, pw);
            Kit.hair(g, hx, lineTop, 1, axis - lineTop, GRID);
        }
        Kit.hair(g, px, axis, pw, 1, Theme.LINE);
        // the max and min lines with their prices at the left
        int yMax = yOf(max, min, max, lineTop, lineBot);
        int yMin = yOf(min, min, max, lineTop, lineBot);
        Kit.hair(g, px, yMax, pw, 1, GRID);
        Type.META.draw(g, s.font(), top, x, yMax - Type.META.lineHeight / 2, Theme.INK_3);
        if (yMin != yMax) {
            Kit.hair(g, px, yMin, pw, 1, GRID);
            Type.META.draw(g, s.font(), low, x, yMin - Type.META.lineHeight / 2, Theme.INK_3);
        }
        // the daily medians, joined
        int prevX = Integer.MIN_VALUE;
        int prevY = 0;
        for (History.Day d : h.days()) {
            if (d.p50() == null) {
                continue;
            }
            int dx = xOf(d.date(), first, span, px, pw);
            int dy = yOf(d.p50(), min, max, lineTop, lineBot);
            if (prevX != Integer.MIN_VALUE) {
                Art.line(g, prevX, prevY, dx, dy, 1, Theme.PRICE);
            }
            int r = d == hover ? 2 : 1;
            g.fill(dx - r, dy - r, dx + r + 1, dy + r + 1, Theme.PRICE);
            prevX = dx;
            prevY = dy;
        }
        Type.META.draw(g, s.font(), date(first), px, dateY, Theme.INK_3);
        if (span > 0) {
            Type.META.drawRight(g, s.font(), date(last), px + pw, dateY, Theme.INK_3);
        }
        if (hover != null) {
            s.tip(dayTip(hover));
        }
        return bottom;
    }

    /** A day's tooltip: its date, median, lowest to highest and the listings seen. */
    static List<Component> dayTip(History.Day d) {
        List<Component> t = new ArrayList<>();
        t.add(Component.literal(date(d.date())));
        if (d.p50() != null) {
            t.add(Component.literal(L.t("wynnlabs.box.history_day_median", price(d.p50()))).withStyle(ChatFormatting.GRAY));
        }
        if (d.lowest() != null && d.highest() != null && !d.lowest().equals(d.highest())) {
            t.add(Component.literal(L.t("wynnlabs.box.history_day_range", price(d.lowest()), price(d.highest()))).withStyle(ChatFormatting.GRAY));
        }
        t.add(Component.literal(L.n("wynnlabs.box.history_day_seen", d.count(), d.count())).withStyle(ChatFormatting.GRAY));
        return t;
    }

    /** The chart's height besides its line area: the bars with SP_1 above, the axis, SP_2 and the dates. */
    static int chartExtra() {
        return Theme.SP_1 + BAR_H + 1 + Theme.SP_2 + Type.META.lineHeight;
    }

    private static int xOf(LocalDate d, LocalDate first, long span, int px, int pw) {
        if (span == 0) {
            return px + pw / 2;
        }
        return px + 1 + (int) ((pw - 3) * ChronoUnit.DAYS.between(first, d) / span);
    }

    private static int yOf(int v, int min, int max, int top, int bot) {
        if (max == min) {
            return (top + bot) / 2;
        }
        return bot - Math.round((bot - top) * (float) (v - min) / (max - min));
    }

    // ---- the numbers ----

    private static int columns(int w) {
        return (w - 3 * Theme.SP_2) / 4 >= 56 ? 4 : 2;
    }

    private static int cellH() {
        return Theme.SP_2 + Type.META.lineHeight + Type.NUMBER.lineHeight + Type.META.lineHeight + Theme.SP_1;
    }

    /** The cells' height in {@code w}: one row of four, or two of two when narrow. */
    static int cellsHeight(int w) {
        return columns(w) == 4 ? cellH() : 2 * cellH() + Theme.SP_2;
    }

    /** Last sold, lowest, typical and listings seen in a grid from {@code y}; returns the bottom. */
    static int cells(BoxScreen s, GuiGraphics g, History.Series h, int x, int y, int w) {
        int cols = columns(w);
        int cw = (w - (cols - 1) * Theme.SP_2) / cols;
        History.Day last = h.lastSold();
        String[][] c = {
                {L.t("wynnlabs.box.history_last_sold"), last == null ? "–" : price(last.p50()), last == null ? "" : date(last.date())},
                {L.t("wynnlabs.box.history_lowest"), price(h.lowest()), ""},
                {L.t("wynnlabs.box.history_typical"), price(h.typical()), L.t("wynnlabs.box.history_typical_sub")},
                {L.t("wynnlabs.box.history_sold"), String.valueOf(h.sold()), L.t("wynnlabs.box.history_sold_sub")}};
        int ch = cellH();
        for (int i = 0; i < c.length; i++) {
            int cx = x + (i % cols) * (cw + Theme.SP_2);
            int cy = y + (i / cols) * (ch + Theme.SP_2);
            Art.box(g, cx, cy, cw, ch, 4, CELL, 0);
            int tx = cx + Theme.SP_2;
            int tw = cw - 2 * Theme.SP_2;
            int ty = cy + Theme.SP_2;
            ty = Type.META.draw(g, s.font(), Type.META.fit(s.font(), c[i][0], tw), tx, ty, Theme.INK_3);
            ty = Type.NUMBER.draw(g, s.font(), Type.NUMBER.fit(s.font(), c[i][1], tw), tx, ty, i == 3 ? Theme.INK : Theme.PRICE);
            if (!c[i][2].isEmpty()) {
                Type.META.draw(g, s.font(), Type.META.fit(s.font(), c[i][2], tw), tx, ty, Theme.INK_3);
            }
        }
        return y + cellsHeight(w);
    }

    // ---- the strip over the listings ----

    /**
     * One line over the listings: the sparkline, "14d median X", "low Y" and the week's change,
     * a chevron at the end; a click calls {@code toggle}. Returns the strip's height.
     */
    static int strip(BoxScreen s, GuiGraphics g, History.Series h, boolean open, int x, int y, int w, int mx, int my,
                     Runnable toggle) {
        boolean hover = BoxChips.in(mx, my, x - Theme.SP_2, y, w + 2 * Theme.SP_2, STRIP_H);
        if (hover) {
            Art.box(g, x - Theme.SP_2, y, w + 2 * Theme.SP_2, STRIP_H, 4, Theme.CARD_HOVER, 0);
        }
        int sparkW = s.compact ? 40 : 60;
        sparkline(g, h, x, y + Theme.SP_1, sparkW, 12);
        int chev = 8;
        Art.icon(g, open ? "chevron-up" : "chevron-down", x + w - chev, y + (STRIP_H - chev) / 2, chev, Theme.INK_3);
        int ty = y + (STRIP_H - Type.META.lineHeight) / 2 + 1;
        int right = x + w - chev - Theme.SP_3;
        int cx = x + sparkW + Theme.SP_3;
        Double week = h.weekChange();
        String weekText = null;
        String weekFull = null;
        int weekInk = Theme.INK_3;
        if (week != null) {
            long pct = Math.round(Math.abs(week));
            weekFull = L.t(week >= 0 ? "wynnlabs.box.history_week_up" : "wynnlabs.box.history_week_down", pct);
            weekText = weekFull;
            weekInk = pct == 0 ? Theme.INK_3 : week > 0 ? Theme.GOOD : Theme.BAD;
        }
        String median = L.t("wynnlabs.box.history_median", price(h.typical()));
        String low = h.lowest() == null ? null : L.t("wynnlabs.box.history_low", price(h.lowest()));
        int room = right - cx;
        int mw = Type.META.width(s.font(), median);
        int lw = low == null ? 0 : Type.META.width(s.font(), low) + Theme.SP_3;
        int ww = weekText == null ? 0 : Type.META.width(s.font(), weekText) + Theme.SP_3;
        if (weekText != null && mw + lw + ww > room) {
            // "▲ 15%", the whole line in its tooltip
            weekText = L.t(week >= 0 ? "wynnlabs.box.history_week_up_short" : "wynnlabs.box.history_week_down_short",
                    Math.round(Math.abs(week)));
            ww = Type.META.width(s.font(), weekText) + Theme.SP_3;
        }
        if (weekText != null && mw + lw + ww > room) {
            low = null;                         // the week's change goes before the low
            lw = 0;
        }
        if (mw + lw + ww > room) {
            weekText = null;
            ww = 0;
        }
        if (mw + lw > room) {
            low = null;
        }
        Type.META.draw(g, s.font(), Type.META.fit(s.font(), median, room), cx, ty, Theme.PRICE);
        cx += mw + Theme.SP_3;
        if (low != null) {
            Type.META.draw(g, s.font(), low, cx, ty, Theme.INK_3);
        }
        if (weekText != null) {
            Type.META.drawRight(g, s.font(), weekText, right, ty, weekInk);
            int tw = Type.META.width(s.font(), weekText);
            if (!weekText.equals(weekFull) && BoxChips.in(mx, my, right - tw, y, tw, STRIP_H)) {
                s.tip(List.of(Component.literal(weekFull)));
            }
        }
        s.hit(x - Theme.SP_2, y, w + 2 * Theme.SP_2, STRIP_H, toggle);
        return STRIP_H;
    }

    /** The daily medians as a tiny line in {@code w} x {@code h}, no labels. */
    static void sparkline(GuiGraphics g, History.Series hs, int x, int y, int w, int h) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (History.Day d : hs.days()) {
            if (d.p50() != null) {
                min = Math.min(min, d.p50());
                max = Math.max(max, d.p50());
            }
        }
        if (min > max) {
            return;
        }
        LocalDate first = hs.days().get(0).date();
        long span = ChronoUnit.DAYS.between(first, hs.days().get(hs.days().size() - 1).date());
        int prevX = Integer.MIN_VALUE;
        int prevY = 0;
        for (History.Day d : hs.days()) {
            if (d.p50() == null) {
                continue;
            }
            int dx = span == 0 ? x + w / 2 : x + (int) ((w - 1) * ChronoUnit.DAYS.between(first, d.date()) / span);
            int dy = yOf(d.p50(), min, max, y + 1, y + h - 2);
            if (prevX != Integer.MIN_VALUE) {
                Art.line(g, prevX, prevY, dx, dy, 1, Theme.PRICE);
            } else if (span == 0) {
                g.fill(dx - 1, dy - 1, dx + 2, dy + 2, Theme.PRICE);
            }
            prevX = dx;
            prevY = dy;
        }
    }
}
