package io.github.anilken.wynnlabs.search;

/**
 * The key of one use of an item in the search list: {@code use:<item>:<type>}, where the type is
 * a craft's item type ("ring"), {@link #FEED} for feeding a mount, or {@link #PROFESSION} and a
 * profession ("prof-armouring") for an ingredient. An item's name may hold a colon; a type never does.
 */
public record UseKey(String item, String type) {

    public static final String PREFIX = "use:";
    public static final String FEED = "feed";
    public static final String PROFESSION = "prof-";

    /** The key of a use of {@code item}. */
    public static String of(String item, String type) {
        return PREFIX + item + ":" + type;
    }

    /** Whether a list key is a use's (and so never a recent search). */
    public static boolean isUse(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    /** The parts of a use's key, or null when it is not one (no item, no type). */
    public static UseKey parse(String key) {
        if (!isUse(key)) {
            return null;
        }
        int at = key.lastIndexOf(':');
        if (at <= PREFIX.length() || at == key.length() - 1) {
            return null;
        }
        return new UseKey(key.substring(PREFIX.length(), at), key.substring(at + 1));
    }

    public String key() {
        return of(item, type);
    }

    public boolean feed() {
        return FEED.equals(type);
    }

    /** The profession of an ingredient's use ("armouring"), or null for another kind of use. */
    public String profession() {
        return type.startsWith(PROFESSION) && type.length() > PROFESSION.length() ? type.substring(PROFESSION.length()) : null;
    }
}
