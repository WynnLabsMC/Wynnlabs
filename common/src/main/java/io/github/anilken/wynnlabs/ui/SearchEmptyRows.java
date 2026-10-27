package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.search.RecentSearches;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The search list's empty page pieces: "More ↓" / "Less ↑" under its first group, "Clear" on the
 * recent searches' header, and a hovered recent row's ✕ that forgets it.
 */
final class SearchEmptyRows {

    private SearchEmptyRows() {}

    /** "More ↓" (or "Less ↑"), centred in its row: the rest of the empty page, or back to its top. */
    static void more(SearchResultsPanel p, GuiGraphics g, SearchResultsPanel.Row r, int x, int y, int w, int top, int h, int mx, int my) {
        boolean more = SearchResultsPanel.MORE.equals(r.stat());
        String t = L.t(more ? "wynnlabs.search.more" : "wynnlabs.search.less");
        String icon = more ? "chevron-down" : "chevron-up";
        int bw = Ui.iconChipWidth(p.font(), icon, t, false);
        int bx = x + (w - bw) / 2;
        int by = y + (SearchResultsPanel.ROW - Ui.CHIP_H) / 2;
        if (by < top || by + Ui.CHIP_H > top + h) {
            return;
        }
        Ui.iconChip(g, p.font(), icon, t, false, bx, by, bw, false, mx, my);
        p.tab.click(bx, by, bw, Ui.CHIP_H, () -> {
            SearchTab.expanded = more;
            if (!more) {
                p.scroll = 0;
            }
        });
    }

    /** "Clear" at the recent group's header's right end. */
    static int clear(SearchResultsPanel p, GuiGraphics g, int x, int y, int w, int mx, int my) {
        int ty = y + Theme.SP_3;
        String t = L.t("wynnlabs.search.recent_clear");
        int tw = Type.LINK.width(p.font(), t);
        boolean hover = mx >= x + w - tw && mx < x + w && my >= ty && my < ty + Type.LINK.lineHeight;
        Type.LINK.drawRight(g, p.font(), t, x + w, ty, hover ? Theme.GOLD_DEEP : Theme.LINK);
        p.tab.click(x + w - tw, ty, tw, Type.LINK.lineHeight, () -> RecentSearches.get().clear());
        return tw;
    }

    /** On a hovered recent row: a small ✕ at its end that forgets it. */
    static void forget(SearchResultsPanel p, GuiGraphics g, SearchResultsPanel.Row r, int x, int y, int w, int top, int h, int mx, int my) {
        int bw = Ui.CHIP_H + 2;
        int by = y + Theme.SP_2 + (Type.BODY.lineHeight + Type.META.lineHeight - Ui.CHIP_H) / 2;
        boolean rowHover = mx >= x && mx < x + w && my >= Math.max(y, top) && my < Math.min(y + SearchResultsPanel.ROW, top + h);
        if (!rowHover || by < top || by + Ui.CHIP_H > top + h) {
            return;
        }
        if (Ui.iconButton(g, "x", x + w - bw, by, false, mx, my)) {
            p.menu.hits.add(new MenuHit(x + w - bw, by, bw, Ui.CHIP_H, null, List.of(L.c("wynnlabs.search.recent_forget")), null));
        }
        String key = r.e().key();
        p.tab.click(x + w - bw, by, bw, Ui.CHIP_H, () -> RecentSearches.get().remove(key));
    }
}
