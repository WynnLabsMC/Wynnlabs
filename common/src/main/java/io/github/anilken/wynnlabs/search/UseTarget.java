package io.github.anilken.wynnlabs.search;

/**
 * What the uses key opens over a hovered item, so the key is never dead over one: the item with
 * its uses when the index knows it or something uses it, the card of an item the mod knows by
 * name (a gathering tool, a ward, a rune), else the plain search for its name.
 */
public enum UseTarget {
    /** An item of the index, or a name the recipes or the mounts use: its row, its card, its uses. */
    ITEM,
    /** A gathering tool, a ward or a rune: its own card. */
    INFO,
    /** Nothing known by that name: the search, with the name typed. */
    SEARCH;

    /** {@code knownItem}: the index has an item of that name; {@code uses}: what the name is used for. */
    public static UseTarget of(boolean knownItem, Uses.Result uses, String name) {
        if (InfoItems.of(name) != null) {
            return INFO;
        }
        return knownItem || !uses.empty() ? ITEM : SEARCH;
    }
}
