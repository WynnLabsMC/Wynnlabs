package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.RecentSearches;
import io.github.anilken.wynnlabs.search.SearchCategory;
import io.github.anilken.wynnlabs.search.SearchQuery;
import io.github.anilken.wynnlabs.search.TrackedMobs;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;

/**
 * The search tab's second row and its title chips. The category strip: one hairline pill split
 * into All, Items, Mobs, Places, Quests, Stats, Services, each with its icon and count, the
 * picked one gold; when the row is narrow the others drop their words, then their counts. After
 * it the chosen stat as a chip with ✕, then the recent searches (where there is room), and on the
 * right "Filter ▾" with how many filters are on. On the title row: "Marked N ▾" (the map marks the
 * player kept), "Tracked items N ▾" and "Tracked N ▾" (the tracked mobs), each only when it has any.
 */
final class SearchStrip extends MenuPaint {

    private static final SearchCategory[] CATS = SearchCategory.values();
    private final SearchTab tab;
    /** The anchors of the menus this row opens, this frame: filter, marks, tracked mobs, tracked items. */
    int[] filterAt;
    int[] marksAt;
    int[] trackedAt;
    int[] itemsAt;

    SearchStrip(SearchTab tab) {
        super(tab.menu);
        this.tab = tab;
    }

    static String label(SearchCategory c) {
        return L.t("wynnlabs.search.cat." + c.name().toLowerCase(Locale.ROOT));
    }

    /** The strip at x, y across w; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, SearchQuery.Result result, boolean compact, int mx, int my) {
        String filter = tab.filtersOn() == 0 ? L.t("wynnlabs.search.filter")
                : L.t("wynnlabs.search.filter_on", tab.filtersOn());
        int fw = Ui.iconChipWidth(font(), "filter", filter, true);
        int fx = x + w - fw;
        filterAt = new int[] {fx, y, fw, Ui.CHIP_H};
        UiMenu.chip(g, font(), "filter", filter, fx, y, tab.menus.open == SearchMenus.FILTER, mx, my);
        tab.click(fx, y, fw, Ui.CHIP_H, () -> tab.menus.toggle(SearchMenus.FILTER));
        int room = fx - Theme.SP_4 - x;
        // the segments' words: all, then the picked one only, then no counts but the picked one's
        int mode = 0;
        while (mode < 2 && width(result, mode) > room) {
            mode++;
        }
        int cx = x;
        int total = width(result, mode);
        Kit.pill(g, x, y, total, Ui.CHIP_H, false, false);
        for (int i = 0; i < CATS.length; i++) {
            SearchCategory c = CATS[i];
            String text = text(c, result, mode);
            int sw = segment(text);
            boolean on = c == tab.category;
            boolean hover = mx >= cx && mx < cx + sw && my >= y && my < y + Ui.CHIP_H;
            if (on || hover) {
                Kit.pill(g, cx, y, sw, Ui.CHIP_H, on, hover);
            } else if (i > 0 && CATS[i - 1] != tab.category) {
                Kit.hair(g, cx, y + Theme.SP_2, 1, Ui.CHIP_H - 2 * Theme.SP_2, Theme.LINE);
            }
            int ink = on ? Theme.INK : Theme.INK_2;
            int ty = Type.BODY.yIn(y, Ui.CHIP_H);
            Art.icon(g, c.icon(), cx + Theme.SP_3, Type.BODY.onCaps(ty, 8), 8, ink);
            if (!text.isEmpty()) {
                Type.BODY.draw(g, font(), text, cx + Theme.SP_3 + 8 + Theme.SP_1, ty, ink);
            }
            if (!on && text.isEmpty()) {
                tab.hint(cx, y, sw, Ui.CHIP_H, label(c));
            }
            tab.click(cx, y, sw, Ui.CHIP_H, () -> tab.pickCategory(c));
            cx += sw;
        }
        cx += Theme.SP_4;
        if (tab.stat != null) {
            String s = SearchResultsPanel.statName(tab.stat);
            int sw = Ui.iconChipWidth(font(), "x", s, false);
            if (cx + sw <= fx - Theme.SP_4) {
                Ui.iconChip(g, font(), "x", s, false, cx, y, sw, false, mx, my);
                tab.click(cx, y, sw, Ui.CHIP_H, () -> tab.pickStat(null));
                cx += sw + Theme.SP_4;
            }
        }
        recentShown = !compact && recent(g, cx, y, fx - Theme.SP_4 - cx, mx, my);
        return Ui.CHIP_H;
    }

    /** Whether the strip showed the recent searches this frame (else the list's top has them). */
    boolean recentShown;

    /** "Recent" and the last searches as chips, as many as fit; false when not even two fit. */
    private boolean recent(GuiGraphics g, int x, int y, int room, int mx, int my) {
        List<String> names = RecentSearches.get().names();
        String head = L.t("wynnlabs.search.recent_short");
        int hw = Type.META.width(font(), head) + Theme.SP_3;
        if (names.isEmpty() || room < hw + Ui.CHIP_MIN * 2) {
            return false;
        }
        Type.META.draw(g, font(), head, x, Type.META.yIn(y, Ui.CHIP_H), Theme.INK_3);
        int cx = x + hw;
        ItemIndex index = ItemIndex.get();
        for (String key : names) {
            var e = index.byKey(key);
            String label = e != null ? e.name() : key.substring(key.indexOf(':') + 1);
            int cw = Ui.iconChipWidth(font(), "clock", label, false);
            if (cx + cw > x + room) {
                break;
            }
            Ui.iconChip(g, font(), "clock", label, false, cx, y, cw, false, mx, my);
            tab.click(cx, y, cw, Ui.CHIP_H, () -> tab.show(key));
            cx += cw + Theme.SP_2;
        }
        return true;
    }

    private String text(SearchCategory c, SearchQuery.Result r, int mode) {
        boolean on = c == tab.category;
        String count = c == SearchCategory.STAT ? "" : " " + HomeHero.compact(c.count(r.counts()));
        return switch (mode) {
            case 0 -> label(c) + count;
            case 1 -> on ? label(c) + count : count.trim();
            default -> on ? label(c) : "";
        };
    }

    private int segment(String text) {
        return Theme.SP_3 + 8 + (text.isEmpty() ? 0 : Theme.SP_1 + Type.BODY.width(font(), text)) + Theme.SP_3;
    }

    private int width(SearchQuery.Result r, int mode) {
        int w = 0;
        for (SearchCategory c : CATS) {
            w += segment(text(c, r, mode));
        }
        return w;
    }

    // ---- the title row ----

    /**
     * "Tracked N ▾", "Tracked items N ▾", the undo after a "Stop all" or "Remove all" and "Marked
     * N ▾", right to left from {@code right}, each while all of them so far fit in {@code room};
     * returns the width taken.
     */
    int title(GuiGraphics g, int right, int y, int room, int mx, int my) {
        int r = right;
        int cy = y - 3;
        int mobs = TrackedMobs.get().names().size();
        int items = TrackedMobs.items().names().size();
        trackedAt = null;
        itemsAt = null;
        marksAt = null;
        if (mobs > 0 && right - r + chipW("target", L.t("wynnlabs.search.tracked_n", mobs)) <= room) {
            trackedAt = menuChip(g, "target", L.t("wynnlabs.search.tracked_n", mobs), r, cy, SearchMenus.TRACKED, mx, my);
            r = trackedAt[0] - Theme.SP_2;
        }
        if (items > 0 && right - r + chipW("backpack", L.t("wynnlabs.search.tracked_items_n", items)) <= room) {
            itemsAt = menuChip(g, "backpack", L.t("wynnlabs.search.tracked_items_n", items), r, cy, SearchMenus.ITEMS, mx, my);
            r = itemsAt[0] - Theme.SP_2;
        }
        if (tab.menus.undo() != null) {
            // just after "Remove all" / "Stop all": the way back, until it times out or the list changes
            String t = L.t("wynnlabs.search.undo");
            int w = Ui.iconChipWidth(font(), "arrow-left", t, false);
            if (right - r + w <= room) {
                r -= w;
                Ui.iconChip(g, font(), "arrow-left", t, false, r, cy, w, false, mx, my);
                tab.click(r, cy, w, Ui.CHIP_H, tab.menus::undoNow);
                r -= Theme.SP_2;
            }
        }
        int marks = tab.card.keptMarks().size();
        if (marks > 0 && right - r + chipW("map-2", L.t("wynnlabs.search.marked_n", marks)) <= room) {
            marksAt = menuChip(g, "map-2", L.t("wynnlabs.search.marked_n", marks), r, cy, SearchMenus.MARKS, mx, my);
            r = marksAt[0] - Theme.SP_2;
        }
        return r == right ? 0 : right - r - Theme.SP_2;
    }

    private int chipW(String icon, String label) {
        return Ui.iconChipWidth(font(), icon, label, true);
    }

    /** A chip with a caret ending at {@code right} that opens menu {@code which}; returns its box. */
    private int[] menuChip(GuiGraphics g, String icon, String label, int right, int y, int which, int mx, int my) {
        int w = chipW(icon, label);
        int x = right - w;
        UiMenu.chip(g, font(), icon, label, x, y, tab.menus.open == which, mx, my);
        tab.click(x, y, w, Ui.CHIP_H, () -> tab.menus.toggle(which));
        return new int[] {x, y, w, Ui.CHIP_H};
    }
}
