package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.InfoItems;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemMatch;
import io.github.anilken.wynnlabs.search.SearchCategory;
import io.github.anilken.wynnlabs.search.UseKey;

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
     * when it lists the hits like any other. The uses of an item while the tab shows them; Stats,
     * with nothing typed and no stat chosen: every stat and how many items roll it.
     */
    static List<SearchRow> ownRows(SearchTab tab, ItemIndex index, boolean blank) {
        if (tab.usesOf != null) {
            return SearchRowSources.uses(tab, index, tab.usesOf);
        }
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
        if (UseKey.isUse(key)) {
            return UseCard.of(card, key);           // a use of an item: nothing of the index
        }
        if (key.startsWith(InfoItems.MADE)) {
            return MadeCard.of(card, key);          // a crafted item: how it was made
        }
        if (InfoItems.name(key) != null) {
            return InfoCard.of(card, key);          // a gathering tool, a ward, a rune
        }
        Entry e = ItemIndex.get().byKey(key);
        if (e == null) {
            return null;
        }
        return e.item() ? new ItemCard(card, e) : new ThingCard(card, e);
    }

    /**
     * A key's name for the search box and a recent search's chip: a use's ("Ring · Koi Oil"), its
     * entry's, else what follows the key's first colon.
     */
    static String name(String key) {
        UseKey use = UseKey.parse(key);
        if (use != null) {
            return UseCard.name(use);
        }
        if (InfoItems.name(key) != null) {
            return InfoItems.name(key);
        }
        Entry e = ItemIndex.get().byKey(key);
        return e != null ? e.name() : key.substring(key.indexOf(':') + 1);
    }
}
