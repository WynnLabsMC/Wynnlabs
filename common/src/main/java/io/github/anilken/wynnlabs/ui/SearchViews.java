package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;

/**
 * From a list key to what the search tab shows for it: the one place a key is looked up. A key is
 * an entry's today (an item's name, a thing's "kind:name"); a key of something outside the index
 * gets its card and its name here, and every caller (the list's choice, a link, a recent search,
 * the search key) follows.
 */
final class SearchViews {

    private SearchViews() {}

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
