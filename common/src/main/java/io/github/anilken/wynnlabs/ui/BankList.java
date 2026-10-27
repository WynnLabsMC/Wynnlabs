package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bank's two other layouts, both one entry per kind of item (its total, wherever it is), only
 * what passes the filter, in the chosen order. "List": a row each: the item, its name in its tier's
 * colour and its total, where it is, its mark as a seal and its worth, those two in right columns
 * as wide as their widest entry. "All side by side": every item in one grid that wraps to the
 * width, in sections by kind (by tier when sorted by tier).
 */
final class BankList {

    private static final int ICON = 18;

    private final BankPages pages;
    /** The right columns' widths for the list now: {seal, value}; worked out when the list changes. */
    private int[] cols = {0, 0};
    private List<BankIndex.Item> colsFor;

    BankList(BankPages pages) {
        this.pages = pages;
    }

    /** Draws the layout picked from {@code y} (scrolled) across {@code w}; returns the content's height. */
    int draw(MenuTab tab, GuiGraphics g, BankIndex idx, int x, int y, int w, int top, int bottom, int mx, int my) {
        pages.slots.clear();
        return pages.view == BankPages.VIEW_LIST ? list(tab, g, x, y, w, top, bottom, mx, my)
                : grid(tab, g, idx, x, y, w, top, bottom, mx, my);
    }

    // ---- the list ----

    private static String sealText(BankIndex.Item it) {
        String k = BankIndex.markKey(it.mark());
        return k == null ? null : L.t(k);
    }

    private static String valueText(BankIndex.Item it) {
        Double v = it.group() == null ? null : Vault.value(it.group());
        return v == null ? null : "~" + Prices.fmt(v);
    }

    private int list(MenuTab tab, GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        Font font = tab.font();
        List<BankIndex.Item> items = pages.shownStacks;
        if (items != colsFor) {
            colsFor = items;
            int sw = 0;
            int vw = 0;
            for (BankIndex.Item it : items) {
                String s = sealText(it);
                if (s != null) {
                    sw = Math.max(sw, Type.META.width(font, s) + 2 * Theme.SP_2);
                }
                String v = valueText(it);
                if (v != null) {
                    vw = Math.max(vw, Type.NUMBER.width(font, v));
                }
            }
            cols = new int[] {sw, vw};
        }
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        int rowH = Math.max(ICON, textH);
        int valueX = x + w - cols[1];
        int sealX = valueX - (cols[1] > 0 ? Theme.SP_4 : 0) - cols[0];
        int nameR = (cols[0] > 0 ? sealX : valueX) - Theme.SP_4;
        int cy = y;
        for (BankIndex.Item it : items) {
            if (cy + rowH > top && cy < bottom) {
                row(tab, g, font, it, x, cy, w, rowH, sealX, valueX, nameR, top, bottom, mx, my);
            }
            cy += rowH + Theme.SP_2;
        }
        return Math.max(0, cy - Theme.SP_2 - y);
    }

    private void row(MenuTab tab, GuiGraphics g, Font font, BankIndex.Item it, int x, int y, int w, int rowH, int sealX, int valueX,
                     int nameR, int top, int bottom, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH && my >= top && my < bottom;
        int iy = y + (rowH - ICON) / 2;
        BankPages.slot(tab, g, font, x, iy, ICON, it, 1, it == pages.picked, false);      // the total is after the name
        int tx = x + ICON + Theme.SP_3;
        String count = it.group() != null && it.group().total() > 1 ? "×" + it.group().total() : null;
        int cw = count == null ? 0 : Type.META.width(font, count) + Theme.SP_2;
        String name = Type.BODY.fit(font, it.st().name(), nameR - tx - cw);
        Type.BODY.draw(g, font, name, tx, y, hover ? Theme.LINK : HomePart.tierColour(it.tier()));
        if (count != null) {
            Type.META.draw(g, font, count, tx + Type.BODY.width(font, name) + Theme.SP_2, y + 1, Theme.INK_3);
        }
        String where = it.group() != null ? Vault.where(it.group()) : Vault.label(it.st().page());
        Type.META.draw(g, font, Type.META.fit(font, where, nameR - tx), tx, y + Type.BODY.lineHeight, Theme.INK_3);
        String seal = sealText(it);
        if (seal != null) {
            Ui.seal(g, font, seal, sealX, Type.BODY.onCaps(y, Ui.SEAL_H), it.mark());
        }
        String value = valueText(it);
        if (value != null) {
            Type.NUMBER.drawRight(g, font, value, x + w, y, Theme.PRICE);
        }
        int vy = Math.max(y, top);
        int vh = Math.min(y + rowH, bottom) - vy;
        pages.slots.add(new Object[] {new int[] {x, vy, w, vh}, it});
        BankTips.addSlotTip(tab, x, vy, w, vh, it, mx, my);
    }

    // ---- every item in one grid ----

    private int grid(MenuTab tab, GuiGraphics g, BankIndex idx, int x, int y, int w, int top, int bottom, int mx, int my) {
        Font font = tab.font();
        int sz = BankPages.slotFor(w, idx.cols);
        int per = Math.max(1, (w + BankPages.GAP) / (sz + BankPages.GAP));
        // sections: by tier when the order is the tier, else by kind; each keeps the order
        boolean byTier = pages.sort == BankPages.SORT_TIER;
        Map<Object, List<BankIndex.Item>> sections = new LinkedHashMap<>();
        if (!byTier) {
            for (BankIndex.Kind k : BankIndex.Kind.values()) {
                sections.put(k, new ArrayList<>());
            }
        }
        for (BankIndex.Item it : pages.shownStacks) {
            Object key = byTier ? (it.tier() == null ? "" : it.tier()) : it.kind();
            sections.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
        }
        int cy = y;
        boolean first = true;
        for (Map.Entry<Object, List<BankIndex.Item>> e : sections.entrySet()) {
            List<BankIndex.Item> list = e.getValue();
            if (list.isEmpty()) {
                continue;
            }
            if (!first) {
                cy += Theme.SP_6;
            }
            first = false;
            String title = e.getKey() instanceof Tier t ? MenuPaint.nice(t.name())
                    : e.getKey() instanceof BankIndex.Kind k ? (k == BankIndex.Kind.OTHER ? L.t("wynnlabs.bank.kind_other") : k.label())
                    : L.t("wynnlabs.bank.kind_other");
            Type.LABEL.draw(g, font, title, x, cy, e.getKey() instanceof Tier t ? HomePart.tierColour(t) : Theme.INK);
            String n = String.valueOf(list.size());
            Type.META.draw(g, font, n, x + Type.LABEL.width(font, title) + Theme.SP_2, cy + 1, Theme.INK_3);
            Kit.hair(g, x, cy + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
            cy += Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
            for (int i = 0; i < list.size(); i++) {
                BankIndex.Item it = list.get(i);
                int sx = x + (i % per) * (sz + BankPages.GAP);
                int sy = cy + (i / per) * (sz + BankPages.GAP);
                if (sy + sz <= top || sy >= bottom) {
                    continue;
                }
                boolean hover = mx >= sx && mx < sx + sz && my >= sy && my < sy + sz && my >= top && my < bottom;
                BankPages.slot(tab, g, font, sx, sy, sz, it, it.group() == null ? it.st().count() : it.group().total(),
                        it == pages.picked, hover);
                int vy = Math.max(sy, top);
                int vh = Math.min(sy + sz, bottom) - vy;
                pages.slots.add(new Object[] {new int[] {sx, vy, sz, vh}, it});
                BankTips.addSlotTip(tab, sx, vy, sz, vh, it, mx, my);
            }
            cy += ((list.size() + per - 1) / per) * (sz + BankPages.GAP) - BankPages.GAP;
        }
        return cy - y;
    }
}
