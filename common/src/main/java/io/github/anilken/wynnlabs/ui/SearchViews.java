package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemMatch;
import io.github.anilken.wynnlabs.search.SearchCategory;

import java.util.List;

/**
 * From the search tab's state to what it shows, in one place. A list key to its card and its
 * name: a key is an entry's today (an item's name, a thing's "kind:name"); a key of something
 * outside the index gets its card and its name here, and every caller (the list's choice, a link,
 * a recent search, the search key) follows. And a category to the list of its own it shows in
 * place of the query's hits ({@code search/SearchCategory} itself knows nothing of the screen).
 */
final class SearchViews {

    /** The stats of the index they were counted in, counted once. */
    private static List<ItemMatch.StatCount> stats;
    private static ItemIndex statsFor;

    private SearchViews() {}

    /**
     * The list a category shows of its own, in place of the empty page and the query's hits; null
     * when it lists the hits like any other. Stats, with nothing typed and no stat chosen: every
     * stat and how many items roll it.
     */
    static List<SearchRow> ownRows(SearchTab tab, ItemIndex index, boolean blank) {
        if (!blank || tab.category != SearchCategory.STAT || tab.stat != null) {
            return null;
        }
        if (stats == null || statsFor != index) {
            stats = ItemMatch.stats(index);
            statsFor = index;
        }
        return SearchRowSources.stats(stats);
    }

    /** The card of a key, or null when nothing has that key. */
    static CardView card(SearchCardPanel card, String key) {
        if (key == null) {
            return null;
        }
        Entry e = ItemIndex.get().byKey(key);
        if (e == null) {
            return null;
        }
        return e.item() ? new ItemCard(card, e) : new ThingCard(card, e);
    }

    /** A key's name for the search box and a recent search's chip: its entry's, else what follows the key's first colon. */
    static String name(String key) {
        Entry e = ItemIndex.get().byKey(key);
        return e != null ? e.name() : key.substring(key.indexOf(':') + 1);
    }
}
