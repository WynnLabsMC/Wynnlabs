package io.github.anilken.wynnlabs.search;

import io.github.anilken.wynnlabs.crafting.ToolLadder;
import io.github.anilken.wynnlabs.crafting.ToolWays;
import io.github.anilken.wynnlabs.market.Runes;

import java.util.List;

/**
 * The items the search index does not list but the mod knows by name: gathering tools ("Cobalt
 * Rod T8"), the seven wards and the raid runes. Their facts are shipped with the crafting data
 * ({@link ToolWays}, {@link Runes}); here is only which name is which, and the list keys of their
 * cards ({@code info:<name>}; a crafted item's is {@code made:<name>}). Plain data.
 */
public final class InfoItems {

    public enum Kind { TOOL, WARD, RUNE }

    public static final String PREFIX = "info:";
    /** The key prefix of a crafted item's card: how it was made. */
    public static final String MADE = "made:";
    /** The seven wards by colour: the raids' first, then the lootruns'. */
    public static final List<String> WARDS = List.of("Yellow", "Red", "Blue", "Purple", "Green", "Orange", "Pink");

    private InfoItems() {}

    /** What a name is, or null when it is none of these. */
    public static Kind of(String name) {
        if (name == null) {
            return null;
        }
        if (ToolWays.tier(name) > 0) {
            return Kind.TOOL;
        }
        if (ward(name)) {
            return Kind.WARD;
        }
        return Runes.of(name) != null ? Kind.RUNE : null;
    }

    /** Whether a name is one of the seven wards ("Blue Ward"). */
    public static boolean ward(String name) {
        return name != null && name.endsWith(" Ward") && WARDS.contains(name.substring(0, name.length() - 5));
    }

    /** The kind of a gathering tool by its name ("Cobalt Rod T8": the rod), or null when it is no tool. */
    public static ToolLadder.Kind toolKind(String name) {
        int tier = ToolWays.tier(name);
        if (tier <= 0) {
            return null;
        }
        for (ToolLadder.Kind k : ToolLadder.Kind.values()) {
            if (ToolLadder.isTool(name, k, tier)) {
                return k;
            }
        }
        return null;
    }

    /** The list key of an item's card. */
    public static String key(String name) {
        return PREFIX + name;
    }

    /** The item of a list key, or null when the key is not one of these. */
    public static String name(String key) {
        return key != null && key.startsWith(PREFIX) && key.length() > PREFIX.length() ? key.substring(PREFIX.length()) : null;
    }

    /**
     * Whether a list key may be kept as a recent search: an entry's own key. A use of an item, one
     * of these cards and a crafted item's are rows of a view, not things to search for.
     */
    public static boolean recent(String key) {
        return key != null && !UseKey.isUse(key) && !key.startsWith(PREFIX) && !key.startsWith(MADE);
    }
}
