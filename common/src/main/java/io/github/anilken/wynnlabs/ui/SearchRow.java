package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.RecentSearches;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One row of the search list: a group's title, an entry, a stat, or the empty page's "More ↓".
 * Each knows its key, whether it can be the list's choice, how tall it is and how to draw itself
 * (its click areas with it), so the list only stacks rows and finds the chosen one by key; a row
 * kind without an {@link Entry} needs nothing from the list.
 */
sealed interface SearchRow {

    /** The slot and the badge of every row, one size: an item at 1x in it, Wynntils' icons at 1x with room. */
    int SLOT = SearchCardRows.BADGE;

    /** What a row needs of the frame it is drawn in: the list, its window ({@code top}, {@code h}) and the mouse. */
    record Frame(SearchResultsPanel list, boolean compact, int top, int h, int mx, int my) {
        /** Whether the mouse is on the part of a row at x, y that is in view. */
        boolean over(int x, int y, int w) {
            return mx >= x && mx < x + w && my >= Math.max(y, top) && my < Math.min(y + SearchResultsPanel.ROW, top + h);
        }
    }

    /** Its name in the list: what {@code SearchTab.selName} holds while it is chosen. */
    String key();

    /** Whether it can be the list's choice (the arrow keys land on it, its card opens). */
    boolean pickable();

    int height();

    /** Its value in the right column (the column is as wide as the widest), or null. */
    default String right() {
        return null;
    }

    /** Draws it at y (its pitch's top) and adds its click areas. */
    void draw(GuiGraphics g, Frame f, int x, int y, int w, boolean chosen);

    /** A group's title with its META and hairline; {@code clear}: the recent searches' "Clear" at its end. */
    record Header(String title, String meta, boolean clear) implements SearchRow {
        @Override
        public String key() {
            return null;
        }

        @Override
        public boolean pickable() {
            return false;
        }

        @Override
        public int height() {
            return SearchResultsPanel.HEADER;
        }

        @Override
        public void draw(GuiGraphics g, Frame f, int x, int y, int fullW, boolean chosen) {
            var font = f.list().font();
            int w = fullW - (clear ? SearchEmptyRows.clear(f.list(), g, x, y, fullW, f.mx(), f.my()) + Theme.SP_3 : 0);
            int ty = y + Theme.SP_3;
            Type.LABEL.draw(g, font, title, x, ty, Theme.INK_2);
            int mx = x + Type.LABEL.width(font, title) + Theme.SP_3;
            String fit = Type.META.fit(font, meta, Math.max(0, x + w - mx - Theme.SP_6));
            Type.META.draw(g, font, fit, mx, ty + 1, Theme.INK_3);
            int lx = mx + Type.META.width(font, fit) + Theme.SP_3;
            if (lx < x + w) {
                Kit.hair(g, lx, Type.LABEL.onCaps(ty, 1) + 2, x + w - lx, 1, Theme.LINE);
            }
        }
    }

    /**
     * An entry: its META line and its right column, worked out once; {@code recent}: one of the
     * recent searches, with a ✕ that forgets it while hovered.
     */
    record EntryRow(Entry e, String meta, String right, int rightInk, boolean recent) implements SearchRow {
        @Override
        public String key() {
            return e.key();
        }

        @Override
        public boolean pickable() {
            return true;
        }

        @Override
        public int height() {
            return SearchResultsPanel.ROW;
        }

        @Override
        public void draw(GuiGraphics g, Frame f, int x, int y, int w, boolean chosen) {
            SearchTab tab = f.list().tab;
            int trail = recent ? SearchEmptyRows.forgetRoom(x, y, w, f.top(), f.h(), f.mx(), f.my()) : 0;
            boolean hover = f.over(x, y, w);
            int sy = lit(g, x, y, w, chosen);
            if (e.item()) {
                Kit.slot(g, x, sy, SLOT, 0);
                g.renderItem(tab.icons.of(e), x + (SLOT - 16) / 2, sy + (SLOT - 16) / 2);
                if (hover && f.mx() < x + SLOT) {
                    f.list().menu.hits.add(new MenuHit(x, sy, SLOT, SLOT, tab.icons.of(e), null, null));
                }
            } else {
                SearchThingCard.picture(g, e, x, sy, SLOT);
            }
            int ink = hover && !chosen ? Theme.LINK : SearchResultsPanel.colour(e);
            text(g, f, x, y, w, trail, e.name(), ink, meta, right, rightInk, () -> pick(tab, f.compact()));
            if (recent) {
                SearchEmptyRows.forget(f.list(), g, key(), x, y, w, f.top(), f.h(), f.mx(), f.my());
            }
        }

        /** Clicked: it is the choice (and, on a small window, its card opens). */
        private void pick(SearchTab tab, boolean compact) {
            tab.selName = key();
            RecentSearches.get().add(key());
            if (compact) {
                tab.openDrawer(e.item() ? SearchResultsPanel.kindText(e) : SearchThingCard.typeText(e));
            }
        }
    }

    /** A stat of the Stats category's list: a click lists the items that roll it. Never the choice. */
    record StatRow(String stat, String name, String meta) implements SearchRow {
        @Override
        public String key() {
            return "stat:" + stat;
        }

        @Override
        public boolean pickable() {
            return false;
        }

        @Override
        public int height() {
            return SearchResultsPanel.ROW;
        }

        @Override
        public void draw(GuiGraphics g, Frame f, int x, int y, int w, boolean chosen) {
            int sy = lit(g, x, y, w, chosen);
            Kit.slot(g, x, sy, SLOT, 0);
            Art.icon(g, "bolt", x + Ui.iconIn(SLOT, 8), sy + Ui.iconIn(SLOT, 8), 8, Theme.INK_2);
            int ink = f.over(x, y, w) && !chosen ? Theme.LINK : Theme.INK;
            text(g, f, x, y, w, 0, name, ink, meta, null, 0, () -> f.list().tab.pickStat(stat));
        }
    }

    /** The empty page's "More ↓" ({@code more}) or "Less ↑", centred in a row's height. */
    record MoreRow(boolean more) implements SearchRow {
        @Override
        public String key() {
            return more ? "#more" : "#less";
        }

        @Override
        public boolean pickable() {
            return false;
        }

        @Override
        public int height() {
            return SearchResultsPanel.ROW;
        }

        @Override
        public void draw(GuiGraphics g, Frame f, int x, int y, int w, boolean chosen) {
            SearchEmptyRows.more(f.list(), g, more, x, y, w, f.top(), f.h(), f.mx(), f.my());
        }
    }

    // ---- what an entry's and a stat's row share ----

    /** The chosen row's wash, SP_2 around the text; returns the slot's top (the slot sits on the text block). */
    private static int lit(GuiGraphics g, int x, int y, int w, boolean chosen) {
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        if (chosen) {
            Kit.rowLit(g, x - Theme.SP_2, y, w + 2 * Theme.SP_2, textH + 2 * Theme.SP_2);
        }
        return y + Theme.SP_2 + (textH - SLOT) / 2;
    }

    /**
     * The name, the META line under it and the right-hand value (moved left by {@code trail}: room
     * for a button at the row's end), then the row's click area: the part of it in view.
     */
    private static void text(GuiGraphics g, Frame f, int x, int y, int w, int trail, String name, int ink, String meta, String right,
                             int rightInk, Runnable pick) {
        var font = f.list().font();
        int ty = y + Theme.SP_2;
        int tx = x + SLOT + Theme.SP_4;
        int rightW = f.list().rightW();
        int end = x + w - (rightW > 0 ? rightW + Theme.SP_5 : 0) - trail;
        Type.BODY.draw(g, font, Type.BODY.fit(font, name, end - tx), tx, ty, ink);
        Type.META.draw(g, font, Type.META.fit(font, meta, end - tx), tx, ty + Type.BODY.lineHeight, Theme.INK_3);
        if (right != null) {
            Type.BODY.drawRight(g, font, right, x + w - trail, ty, rightInk);
        }
        int cy = Math.max(y, f.top());
        int ch = Math.min(y + SearchResultsPanel.ROW, f.top() + f.h()) - cy;
        if (ch > 0) {
            f.list().tab.click(x, cy, w, ch, pick);
        }
    }
}
