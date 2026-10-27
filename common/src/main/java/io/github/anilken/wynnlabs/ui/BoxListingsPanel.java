package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Listings;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The box window's listings of the chosen item, one collapsed line each: the HD roll bar and the
 * overall %, the build's first stats as tiny chips while they fit and "+N", then age and price.
 * A click on a row or its chevron opens it (one at a time) to every stat chip with ↑ / ↓ against
 * your roll; the rows under it move down while the clicked one stays put. ↑ ↓ move the selection,
 * Enter or Space open and close it; hovering only lights a row. Listings rolled above the
 * high-roll mark (or above yours) get a gold dot in the gutter; possibly sold ones are grey. No
 * seller is ever shown. Items without rolls get the one-line table ({@link BoxPlainRows}).
 */
final class BoxListingsPanel {

    /** A collapsed row's line and the gap after it. */
    static final int LINE = Type.BODY.lineHeight;
    private static final int BAR_W = 32;
    /** The chevron's column at the row's right end, kept for every row so price and age never move. */
    private static final int CHEV = Ui.CHIP_H + 2 + Theme.SP_2;

    /** Between two rows: SP_3, SP_4 in a small window so the ▾ buttons are not packed. */
    private int gap() {
        return s.compact ? Theme.SP_4 : Theme.SP_3;
    }

    private final BoxScreen s;
    private final ScrollBar bar = new ScrollBar();
    private int scroll;
    private int max;
    int[] area = new int[4];
    /** The open row and the keys' selection (-1: none). */
    int open = -1;
    int sel = -1;
    /** Item, filter and sort the rows were for: another one closes the open row and goes to the top. */
    private String shownFor = "";
    private int[] rowY = new int[0];
    int rowsTop;
    private int listHeight;
    private int rowX;
    private int rowW;

    BoxListingsPanel(BoxScreen s) {
        this.s = s;
    }

    /** Back to the top, nothing open (another item was chosen). */
    void reset() {
        scroll = 0;
        open = -1;
        sel = -1;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        area = new int[] {x, y, w, h};
        BoxMarket.Row r = s.selectedRow();
        if (r == null) {
            return;
        }
        String key = r.name() + "/" + s.filter + "/" + s.sort;
        if (!key.equals(shownFor)) {
            shownFor = key;
            reset();
        }
        boolean plain = !s.rolled();
        BoxChips.use(r.name());
        int top = y;
        if (!plain) {
            top = head(g, r, x, y, w, mx, my);
        }
        if (r.listings().isEmpty() || !Listings.live()) {
            // live prices off: no listing is shown, even ones held from before
            empty(g, r, x, top, w, y + h - top, mx, my);
            return;
        }
        if (plain) {
            BoxPlainRows.header(s, g, x, top, w - ScrollBar.W - Theme.SP_2);
            top += BoxPlainRows.HEAD;
        }
        boolean more = r.hasMore();
        // "N more" stays under the list, never half scrolled away
        int listH = y + h - top - (more ? Theme.SP_3 + Ui.CHIP_H : 0);
        List<BoxMarket.Listing> ls = r.listings();
        rowX = x + Theme.SP_3;
        rowW = w - ScrollBar.W - Theme.SP_2 - Theme.SP_3 - Theme.SP_2;
        open = Math.min(open, ls.size() - 1);
        layout(ls, plain);
        int total = rowY[ls.size()];
        max = ScrollBar.max(listH, total);
        scroll = Math.max(0, Math.min(max, scroll));
        rowsTop = top;
        listHeight = listH;
        Entry item = ItemIndex.get().byName(r.name());
        List<Component> tip = new ArrayList<>();
        g.enableScissor(x - Theme.SP_1, top - Theme.SP_1, x + w, top + listH);
        for (int i = 0; i < ls.size(); i++) {
            int ry = top - scroll + rowY[i];
            int rh = rowY[i + 1] - rowY[i];
            if (ry + rh < top || ry > top + listH) {
                continue;
            }
            if (plain) {
                BoxPlainRows.row(s, g, ls.get(i), rowX, ry, rowW);
            } else {
                row(g, ls.get(i), i, ry, item, mx, my, top, listH, tip);
            }
        }
        g.disableScissor();
        if (more) {
            moreButton(g, r, top + listH + Theme.SP_3, mx, my);
        }
        bar.place(x + w - ScrollBar.W, top, listH, listH, total, scroll);
        bar.draw(g, mx, my);
        if (!tip.isEmpty()) {
            s.tip(tip);
        }
    }

    /** The item's name and "x / total listings" over a hairline; returns where the rows start. */
    private int head(GuiGraphics g, BoxMarket.Row r, int x, int y, int w, int mx, int my) {
        String count = L.t("wynnlabs.box.held_of", r.held(), Math.max(r.held(), r.count()));
        int right = x + w - ScrollBar.W - Theme.SP_2;
        Type.META.drawRight(g, s.font(), count, right, y + 1, Theme.INK_3);
        int nameRoom = right - x - Type.META.width(s.font(), count) - Theme.SP_4;
        boolean link = s.box != null && s.compact;
        if (link) {
            nameRoom -= Ui.CHIP_H + 2 + Theme.SP_3;
        }
        String name = Type.LABEL.fit(s.font(), r.name(), nameRoom);
        Type.LABEL.draw(g, s.font(), name, x, y, HomePart.tierColour(r.item().tier()));
        if (BoxChips.in(mx, my, x, y, Type.LABEL.width(s.font(), name), Type.LABEL.lineHeight)) {
            s.tip(List.of(Component.literal(r.name()).withStyle(ChatFormatting.WHITE),
                    L.c("wynnlabs.box.list_tip").withStyle(ChatFormatting.GRAY)));
        }
        if (link) {
            // a small window: the picked item's own way into Search, beside its name
            int bx = x + Type.LABEL.width(s.font(), name) + Theme.SP_3;
            int by = Type.LABEL.onCaps(y, Ui.CHIP_H);
            s.bar.searchButton(g, bx, by, r.name(), mx, my);
        }
        int hy = y + Type.LABEL.lineHeight + Theme.SP_1;
        Kit.hair(g, x, hy, right - x, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    /** Every row's top from the list's top; the open row is taller and has SP_2 more around it. */
    private void layout(List<BoxMarket.Listing> ls, boolean plain) {
        if (rowY.length != ls.size() + 1) {
            rowY = new int[ls.size() + 1];
        }
        int y = 0;
        for (int i = 0; i < ls.size(); i++) {
            if (i == open && !plain) {
                y += i > 0 ? Theme.SP_2 : 0;
                rowY[i] = y;
                y += openHeight(ls.get(i)) + Theme.SP_2 + gap();
            } else {
                rowY[i] = y;
                y += plain ? BoxPlainRows.ROW : LINE + gap();
            }
        }
        rowY[ls.size()] = y;
    }

    /** The open row's height: its chips laid out once when it opens (or the width changes). */
    private int openHeight(BoxMarket.Listing l) {
        if (l != openFor || rowW != openW) {
            List<BoxMarket.StatRoll> st = BoxChips.ordered(l);
            openH = LINE + (st.isEmpty() ? 0 : Theme.SP_2 + BoxChips.height(s.font(), st, own() != null, rowW - 2 * Theme.SP_2));
            openFor = l;
            openW = rowW;
        }
        return openH;
    }

    private BoxMarket.Listing openFor;
    private int openW;
    private int openH;

    private Map<String, Double> own() {
        return s.own.isEmpty() ? null : s.own;
    }

    /** The row index under the mouse, -1 for none. */
    private int rowAt(double mx, double my, int top, int listH) {
        if (mx < rowX - Theme.SP_2 || mx >= rowX + rowW + Theme.SP_2 || my < top || my >= top + listH) {
            return -1;
        }
        double ly = my - top + scroll;
        for (int i = 0; i + 1 < rowY.length; i++) {
            if (ly >= rowY[i] - Theme.SP_1 && ly < rowY[i + 1] - Theme.SP_1) {
                return i;
            }
        }
        return -1;
    }

    /** Opens {@code to} (-1: none), keeping row {@code anchor} where it was on the screen. */
    private void setOpen(int to, int anchor, List<BoxMarket.Listing> ls) {
        int before = anchor >= 0 && anchor < rowY.length ? rowY[anchor] : 0;
        open = to;
        layout(ls, false);
        if (anchor >= 0 && anchor < rowY.length) {
            scroll += rowY[anchor] - before;
        }
        max = ScrollBar.max(listHeight, rowY[ls.size()]);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private void row(GuiGraphics g, BoxMarket.Listing l, int i, int y, Entry item, int mx, int my, int top, int listH,
                     List<Component> tip) {
        boolean opened = i == open;
        boolean grey = l.possiblySold();
        int x = rowX;
        int w = rowW;
        int washH = rowY[i + 1] - rowY[i] - gap() + (opened ? Theme.SP_2 : 0);
        if (opened) {
            Kit.rowLit(g, x - Theme.SP_2, y - Theme.SP_2, w + 2 * Theme.SP_2, washH);
        } else if (i == sel || BoxChips.in(mx, my, x - Theme.SP_2, y - Theme.SP_1, w + 2 * Theme.SP_2, LINE + Theme.SP_2) && my < top + listH) {
            Art.box(g, x - Theme.SP_2, y - Theme.SP_1, w + 2 * Theme.SP_2, LINE + Theme.SP_2, 4, Theme.CARD_HOVER, 0);
        }
        // the chevron at the right end: ▾ to open, ▴ to close
        int cx0 = x + w - (Ui.CHIP_H + 2);
        int cy0 = Type.BODY.onCaps(y, Ui.CHIP_H);
        if (Ui.iconButton(g, opened ? "chevron-up" : "chevron-down", cx0, cy0, false, mx, my) && tip.isEmpty()) {
            tip.add(L.c(opened ? "wynnlabs.box.stats_hide" : "wynnlabs.box.stats_show"));
        }
        w -= CHEV;
        Double roll = l.unidentified() ? null : l.overallRoll();
        Float ownRoll = s.single == null ? null : s.single.ownRoll();
        boolean good = roll != null && !grey && (ownRoll != null ? roll > ownRoll : BoxMarket.highRoll(l, BoxScreen.highRoll()));
        if (good) {
            Art.box(g, x - Theme.SP_3, Type.BODY.onCaps(y, 3), 3, 3, 1, Theme.GOLD_DEEP, 0);
        }
        // the right end: age (or "may be sold"), then the price
        // the compact price ("56.4 LE"), the exact one in its tooltip
        String price = BoxChips.price(l);
        int pw = Type.NUMBER.width(s.font(), price);
        Type.NUMBER.drawRight(g, s.font(), price, x + w, y, grey ? Theme.INK_3 : Theme.PRICE);
        if (tip.isEmpty() && BoxChips.in(mx, my, x + w - pw, y, pw, LINE)) {
            tip.add(Component.literal(exact(l.price())).withStyle(ChatFormatting.GOLD));
        }
        String age = l.seenAt() > 0 ? BoxPlainRows.age(Math.min(l.seenAt(), System.currentTimeMillis())) : "";
        String tail = grey ? L.t("wynnlabs.box.possibly_sold") + (age.isEmpty() ? "" : " · " + age) : age;
        int tw = Type.META.width(s.font(), tail);
        Type.META.drawRight(g, s.font(), tail, x + w - pw - Theme.SP_3, y + 1, grey ? Theme.WARN : Theme.INK_3);
        int end = x + w - pw - Theme.SP_3 - tw - Theme.SP_3;
        int cx;
        if (roll == null) {
            String t = L.t(l.unidentified() ? "wynnlabs.box.unidentified" : "wynnlabs.box.no_roll");
            Type.BODY.draw(g, s.font(), t, x, y, Theme.INK_2);
            cx = x + Type.BODY.width(s.font(), t) + Theme.SP_3;
        } else {
            BoxChips.bar(g, x, Type.BODY.onCaps(y, 6), BAR_W, 6, roll, grey);
            String p = BoxScreen.pct(Math.round(roll));
            Type.NUMBER.draw(g, s.font(), p, x + BAR_W + Theme.SP_2, y,
                    grey ? Theme.INK_3 : roll > 70 ? Theme.GOOD : Theme.INK);
            cx = x + BAR_W + Theme.SP_2 + Type.NUMBER.width(s.font(), BoxScreen.pct(100)) + Theme.SP_2;
        }
        List<BoxMarket.StatRoll> stats = BoxChips.ordered(l);
        if (opened) {
            if (l.rerolls() > 0 && cx + 30 < end) {
                Type.META.draw(g, s.font(), L.n("wynnlabs.box.rerolls", l.rerolls()), cx, y + 1, Theme.INK_3);
            }
            BoxChips.wrap(g, s.font(), stats, own(), grey, item, x + Theme.SP_2, y + LINE + Theme.SP_2, rowW - 2 * Theme.SP_2, mx, my, tip);
            return;
        }
        // collapsed: the build's first stats while they fit, the rest as "+N"
        int shown = 0;
        for (BoxMarket.StatRoll st : stats) {
            int cw = BoxChips.miniWidth(s.font(), st);
            boolean rest = shown + 1 < stats.size();
            if (cx + cw + (rest ? Theme.SP_1 + 16 : 0) > end) {
                break;
            }
            BoxChips.mini(g, s.font(), st, grey, cx, Type.BODY.onCaps(y, BoxChips.H));
            cx += cw + Theme.SP_1;
            shown++;
        }
        if (shown < stats.size() && cx + 12 <= end) {
            Type.META.draw(g, s.font(), "+" + (stats.size() - shown), cx + Theme.SP_1, y + 1, Theme.INK_3);
        }
        if (s.compact && tip.isEmpty() && BoxChips.in(mx, my, x, Math.max(y, top), w, LINE) && my < top + listH) {
            tip.add(L.c("wynnlabs.box.row_tip_small").withStyle(ChatFormatting.GRAY));
        }
    }

    private void moreButton(GuiGraphics g, BoxMarket.Row r, int y, int mx, int my) {
        int left = Math.max(0, r.count() - r.held());
        if (Listings.loadingMore(r.name())) {
            String t = L.t("wynnlabs.box.loading");
            Type.META.draw(g, s.font(), t, rowX + (rowW - Type.META.width(s.font(), t)) / 2, y, Theme.INK_3);
            return;
        }
        String t = left > 0 ? L.n("wynnlabs.box.more_n", left, left) : L.t("wynnlabs.box.more");
        int bw = Ui.iconChipWidth(s.font(), "chevron-down", t, false);
        int bx = rowX + (rowW - bw) / 2;
        Ui.iconChip(g, s.font(), "chevron-down", t, false, bx, y, bw, false, mx, my);
        String name = r.name();
        s.hit(bx, y, bw, Ui.CHIP_H, () -> Listings.requestMore(name));
    }

    /** No listing to show: live prices off, none now (Refresh), loading, or why it cannot be asked. */
    private void empty(GuiGraphics g, BoxMarket.Row r, int x, int y, int w, int h, int mx, int my) {
        if (r.state() == BoxMarket.State.LOADING && Listings.live()) {
            // a few faint rows where the listings will be
            for (int i = 0; i < 4 && y + (i + 1) * (LINE + gap()) <= y + h; i++) {
                int ry = y + i * (LINE + gap());
                Art.box(g, x + Theme.SP_3, Type.BODY.onCaps(ry, 6), BAR_W, 6, 3, 0x30A08A5A, 0);
                Art.box(g, x + Theme.SP_3 + BAR_W + Theme.SP_3, Type.BODY.onCaps(ry, BoxChips.H), 60, BoxChips.H, 5, 0x22A08A5A, 0);
                Art.box(g, x + w - ScrollBar.W - 48, Type.BODY.onCaps(ry, 8), 40, 8, 4, 0x30A08A5A, 0);
            }
            return;
        }
        String icon;
        String title;
        List<String> steps = new ArrayList<>();
        Boolean action = null;
        if (!Listings.live()) {
            icon = "coin";
            title = L.t("wynnlabs.box.off_title");
            steps.add(L.t("wynnlabs.box.off_step1"));
            steps.add(L.t("wynnlabs.box.off_step2"));
        } else if (r.state() == BoxMarket.State.UNKNOWN) {
            // the request got no answer (a rate limit, a timeout): not "no listings"
            icon = "alert-triangle";
            title = L.t("wynnlabs.box.failed_title");
            if (s.canRefresh()) {
                action = Boolean.TRUE;
            }
        } else if (r.state() == BoxMarket.State.UNTRADABLE) {
            icon = "tag";
            title = L.t("wynnlabs.box.untradable");
        } else {
            icon = "tag";
            title = L.t("wynnlabs.box.none_title");
            steps.add(L.t("wynnlabs.box.none_step1"));
            if (s.box != null) {
                steps.add(L.t("wynnlabs.box.none_step2"));
            }
            if (s.canRefresh()) {
                action = Boolean.TRUE;
            }
        }
        String note = r.pastPrice() != null && Listings.live() ? L.t("wynnlabs.box.last_price", "~" + Prices.fmt(r.pastPrice())) : null;
        paneEmpty(g, icon, title, note, steps, action != null, x, y, w, h, mx, my);
    }

    /**
     * A pane's own empty state, smaller than a page's: an icon, the reason in LABEL, the last known
     * price as META, the steps as META lines and the one action as a small button; centred, and
     * steps left out from the end when the pane is short (nothing is drawn past its bottom).
     */
    private void paneEmpty(GuiGraphics g, String icon, String title, String note, List<String> steps, boolean refresh,
                           int x, int y, int w, int h, int mx, int my) {
        int size = 16;
        int need = Theme.SP_6 + size + Theme.SP_3 + Type.LABEL.lineHeight + (note == null ? 0 : Type.META.lineHeight)
                + (refresh ? Theme.SP_4 + Ui.SMALL_H : 0);
        while (!steps.isEmpty() && need + Theme.SP_3 + steps.size() * (Type.META.lineHeight + Theme.SP_1) > h) {
            steps.remove(steps.size() - 1);
        }
        int cx = x + w / 2;
        int cy = y + Theme.SP_6;
        Art.icon(g, icon, cx - size / 2, cy, size, 0xFF8C7B58);
        cy += size + Theme.SP_3;
        String t = Type.LABEL.fit(s.font(), title, w - 2 * Theme.SP_4);
        Type.LABEL.draw(g, s.font(), t, cx - Type.LABEL.width(s.font(), t) / 2, cy, Theme.INK);
        cy += Type.LABEL.lineHeight;
        if (note != null) {
            Type.META.draw(g, s.font(), note, cx - Type.META.width(s.font(), note) / 2, cy, Theme.PRICE);
            cy += Type.META.lineHeight;
        }
        if (!steps.isEmpty()) {
            cy += Theme.SP_3;
            for (int i = 0; i < steps.size(); i++) {
                String st = Type.META.fit(s.font(), (steps.size() > 1 ? (i + 1) + "  " : "") + steps.get(i), w - 2 * Theme.SP_4);
                Type.META.draw(g, s.font(), st, cx - Type.META.width(s.font(), st) / 2, cy, Theme.INK_2);
                cy += Type.META.lineHeight + Theme.SP_1;
            }
        }
        if (refresh) {
            cy += Theme.SP_4;
            String label = L.t("wynnlabs.box.refresh");
            int bw = Type.BODY.width(s.font(), label) + 2 * Theme.SP_4;
            Ui.small(g, s.font(), label, cx - bw / 2, cy, bw, mx, my);
            s.hit(cx - bw / 2, cy, bw, Ui.SMALL_H, s::refresh);
        }
    }

    // ---- input ----

    /** A click on a row or its chevron: opens it, or closes it when it is open. */
    boolean mouseClicked(double mx, double my) {
        if (bar.mouseClicked(mx, my)) {
            scroll = bar.offset();
            atEnd();
            return true;
        }
        BoxMarket.Row r = s.selectedRow();
        if (r == null || !s.rolled() || r.listings().isEmpty() || !Listings.live()) {
            return false;
        }
        int at = rowAt(mx, my, rowsTop, listHeight);
        if (at < 0) {
            return false;
        }
        sel = at;
        setOpen(at == open ? -1 : at, at, r.listings());
        return true;
    }

    /** ↑ ↓: the selection moves (into view); true when used. */
    boolean key(int delta) {
        BoxMarket.Row r = s.selectedRow();
        if (r == null || !s.rolled() || r.listings().isEmpty()) {
            return false;
        }
        sel = Math.max(0, Math.min(r.listings().size() - 1, sel < 0 ? 0 : sel + delta));
        if (rowY[sel] < scroll) {
            scroll = rowY[sel];
        } else if (rowY[sel + 1] > scroll + listHeight) {
            scroll = rowY[sel + 1] - listHeight;
        }
        return true;
    }

    /** Enter or Space: the selected row opens, or closes when open; true when used. */
    boolean toggle() {
        BoxMarket.Row r = s.selectedRow();
        if (r == null || !s.rolled() || sel < 0 || sel >= r.listings().size()) {
            return false;
        }
        setOpen(sel == open ? -1 : sel, sel, r.listings());
        return true;
    }

    boolean mouseDragged(double my) {
        if (bar.mouseDragged(my)) {
            scroll = bar.offset();
            atEnd();
            return true;
        }
        return false;
    }

    void mouseReleased() {
        bar.mouseReleased();
    }

    boolean mouseScrolled(double mx, double my, double v) {
        if (!BoxChips.in(mx, my, area[0], area[1], area[2], area[3])) {
            return false;
        }
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(v) * (LINE + gap()) * 2));
        atEnd();
        return true;
    }

    /** Scrolled to the end while the market has more: the next page (asked once; Listings keeps the rest out). */
    private void atEnd() {
        BoxMarket.Row r = s.selectedRow();
        if (r != null && r.hasMore() && max > 0 && scroll >= max) {
            Listings.requestMore(r.name());
        }
    }

    /**
     * A listing's price to the emerald, the way the Trade Market writes it: "2 EB 49 E",
     * "1 LE 12 EB 3 E" (1 LE = 64 EB = 4096 E); parts that are 0 are left out.
     */
    static String exact(long emeralds) {
        long le = emeralds / 4096;
        long eb = emeralds % 4096 / 64;
        long e = emeralds % 64;
        StringBuilder b = new StringBuilder();
        if (le > 0) {
            b.append(le).append(" LE");
        }
        if (eb > 0) {
            b.append(b.length() > 0 ? " " : "").append(eb).append(" EB");
        }
        if (e > 0 || b.length() == 0) {
            b.append(b.length() > 0 ? " " : "").append(e).append(" E");
        }
        return b.toString();
    }
}
