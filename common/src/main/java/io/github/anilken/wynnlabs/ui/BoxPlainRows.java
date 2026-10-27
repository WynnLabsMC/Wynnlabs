package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The listings of an item without rolls (an ingredient, a material) as a one-line table: the
 * exact price of one, the amount and how long ago the listing was seen, under the column names
 * and a hairline. Listings seen long before the newest are grey, as in the rolled rows.
 */
final class BoxPlainRows {

    /** One listing's line and the gap after it. */
    static final int ROW = Type.BODY.lineHeight + Theme.SP_2;
    /** The column names' line, its hairline and the gap under it. */
    static final int HEAD = Type.META.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;

    private BoxPlainRows() {}

    /** The amount column's right edge; the price starts left, the age ends right. */
    private static int amountEdge(int x, int w) {
        return x + w * 70 / 100;
    }

    static void header(BoxScreen s, GuiGraphics g, int x, int y, int w) {
        Type.META.draw(g, s.font(), L.t("wynnlabs.box.col_unit"), x, y, Theme.INK_3);
        Type.META.drawRight(g, s.font(), L.t("wynnlabs.box.col_amount"), amountEdge(x, w), y, Theme.INK_3);
        Type.META.drawRight(g, s.font(), L.t("wynnlabs.box.col_seen"), x + w, y, Theme.INK_3);
        Kit.hair(g, x, y + Type.META.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
    }

    static void row(BoxScreen s, GuiGraphics g, BoxMarket.Listing l, int x, int y, int w) {
        boolean grey = l.possiblySold();
        int ax = amountEdge(x, w);
        String n = "×" + l.amount();
        int nw = Type.BODY.width(s.font(), n);
        Type.BODY.drawRight(g, s.font(), n, ax, y, grey ? Theme.INK_3 : Theme.INK);
        String p = io.github.anilken.wynnlabs.market.Prices.fmt(l.price());
        Type.NUMBER.draw(g, s.font(), Type.NUMBER.fit(s.font(), p, ax - nw - Theme.SP_3 - x), x, y, grey ? Theme.INK_3 : Theme.PRICE);
        if (l.seenAt() > 0) {
            String a = age(Math.min(l.seenAt(), System.currentTimeMillis()));
            Type.META.drawRight(g, s.font(), a, x + w, y + 1, grey ? Theme.WARN : Theme.INK_3);
        }
    }

    /** "8 dk", "2 sa", "3 g": the short age of a listing. */
    static String age(long t) {
        long min = Math.max(0, (System.currentTimeMillis() - t) / 60000);
        if (min < 60) {
            return L.t("wynnlabs.box.age_min", min);
        }
        if (min < 24 * 60) {
            return L.t("wynnlabs.box.age_hours", min / 60);
        }
        return L.t("wynnlabs.box.age_days", min / 1440);
    }

    /** How many items the listings hold together. */
    static int stock(List<BoxMarket.Listing> listings) {
        int n = 0;
        for (BoxMarket.Listing l : listings) {
            n += l.amount();
        }
        return n;
    }

    /** An ingredient's stars (0 for anything else). */
    static int stars(String name) {
        Entry e = ItemIndex.get().byName(name);
        return e == null ? 0 : Math.max(0, e.stars());
    }
}
