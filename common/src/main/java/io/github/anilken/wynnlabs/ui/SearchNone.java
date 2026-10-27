package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.search.SearchCategory;
import io.github.anilken.wynnlabs.search.SearchQuery;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The search list with nothing in it, drawn as {@link UiEmpty} draws an empty page: a large icon
 * and "Nothing for “…”", then "Did you mean?" with the closest entry as a real row (its META says
 * when the filters hid it; a click shows it), and "Clear filters" when any filter or category is
 * on. Before anything is typed (filters so narrow nothing is left) the same, without the name.
 */
final class SearchNone {

    private SearchNone() {}

    static void draw(SearchResultsPanel list, SearchTab tab, GuiGraphics g, int x, int y, int w, int h, SearchQuery.Result result,
                     boolean compact, int mx, int my) {
        var font = tab.font();
        int cx = x + w / 2;
        int size = compact ? UiEmpty.ICON_SMALL : UiEmpty.ICON_LARGE;
        int cy = y + (compact ? Theme.SP_6 : h / 10);
        Art.icon(g, "search", cx - size / 2, cy, size, 0xFF8C7B58);
        cy += size + Theme.SP_4;
        String typed = tab.box.getValue().trim();
        String title = typed.isEmpty() ? L.t("wynnlabs.search.none_title") : L.t("wynnlabs.search.none_for", typed);
        title = Type.TITLE.fit(font, title, w - 2 * Theme.SP_4);
        Type.TITLE.draw(g, font, title, cx - Type.TITLE.width(font, title) / 2, cy, Theme.INK);
        cy += Type.TITLE.lineHeight + Theme.SP_5;
        if (result.suggestion() != null) {
            int rw = Math.min(w - 2 * Theme.SP_4, 300);
            int rx = cx - rw / 2;
            Type.META.draw(g, font, L.t("wynnlabs.search.did_you_mean"), rx, cy, Theme.INK_3);
            cy += Type.META.lineHeight + Theme.SP_3;
            var e = result.suggestion().entry();
            String meta = result.filtered() ? L.t("wynnlabs.search.hidden_by_filters", SearchResultsPanel.kindText(e))
                    : e.item() ? SearchResultsPanel.kindText(e) + (e.level() > 0 ? " · Lv " + e.level() : "") : SearchThingCard.sub(e);
            var row = new SearchResultsPanel.Row(e, null, meta, null, L.t("wynnlabs.search.open"), Theme.LINK);
            // the suggestion: as a lit row; a click shows it (the filters that hid it go)
            list.drawRow(g, row, rx, cy, rw, true, compact, y, h, mx, my);
            boolean filtered = result.filtered();
            tab.click(rx, cy, rw, SearchResultsPanel.ROW, () -> {
                if (filtered) {
                    tab.clearFilters();
                    tab.pickCategory(SearchCategory.ALL);
                }
                tab.show(e.key());
            });
            cy += SearchResultsPanel.ROW + Theme.SP_6;
        }
        if (tab.filtersOn() > 0 || tab.category != SearchCategory.ALL) {
            String label = L.t("wynnlabs.search.clear_filters");
            int bw = Ui.iconButtonLabelWidth(font, label);
            int bx = cx - bw / 2;
            Ui.iconButtonLabel(g, font, "eraser", label, bx, cy, bw, mx, my);
            tab.click(bx, cy, bw, Ui.BUTTON_H, () -> {
                tab.clearFilters();
                tab.pickCategory(SearchCategory.ALL);
            });
        }
    }
}
