package io.github.anilken.wynnlabs.search;

/**
 * What the search list shows before anything is typed: on "All", the last searches (or, before
 * there are any, the road map's missing pieces) and a "More ↓" button; opened, the missing pieces
 * and the whole catalogue under it, the button turned "Less ↑". Another category, a filter or a
 * stat lists as before. Pure: the panel builds its rows from the plan.
 */
public final class EmptyPage {

    private EmptyPage() {}

    /** The most rows the first group shows while the page is closed. */
    public static final int FIRST_ROWS = 5;

    public enum Group { RECENT, MISSING, NONE }

    public enum Button { MORE, LESS, NONE }

    /**
     * {@code first}: the group at the top and {@code firstCount} its rows; {@code button}: More or
     * Less under it; {@code missingAfter}: the missing pieces as their own group after it;
     * {@code catalogue}: the full list under everything.
     */
    public record Plan(Group first, int firstCount, Button button, boolean missingAfter, boolean catalogue) {}

    /**
     * {@code home}: the query is empty on "All" with no filter; {@code listing}: the query is empty
     * on "All" or "Items" (where the recent and missing groups may show); {@code hits}: whether the
     * catalogue has anything.
     */
    public static Plan plan(boolean home, boolean listing, int recent, int missing, boolean expanded, boolean hits) {
        Group first = listing && recent > 0 ? Group.RECENT : listing && missing > 0 ? Group.MISSING : Group.NONE;
        int count = switch (first) {
            case RECENT -> Math.min(FIRST_ROWS, recent);
            case MISSING -> home && !expanded ? Math.min(FIRST_ROWS, missing) : missing;
            case NONE -> 0;
        };
        Button button = home && hits && first != Group.NONE ? (expanded ? Button.LESS : Button.MORE) : Button.NONE;
        boolean open = button != Button.MORE;
        return new Plan(first, count, button, open && first == Group.RECENT && missing > 0, open);
    }
}
