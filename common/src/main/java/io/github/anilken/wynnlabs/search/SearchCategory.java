package io.github.anilken.wynnlabs.search;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The search tab's category strip: the thirteen kinds in the seven groups a player asks for.
 * Items (gear, ingredients, materials, tomes, charms), mobs, places (places, content, discoveries),
 * quests, stats (the items that roll a stat), services (services, resources, NPCs). A group's
 * count is the sum of its kinds' counts; ALL lists everything. Stats is a facet: a second way
 * through the kinds Items lists already, so it has no count of its own and no kinds to narrow to.
 */
public enum SearchCategory {
    ALL("circles", false),
    ITEM("box", false, Kind.GEAR, Kind.INGREDIENT, Kind.MATERIAL, Kind.TOME, Kind.CHARM),
    MOB("skull", false, Kind.MOB),
    PLACE("map-pin", false, Kind.PLACE, Kind.CONTENT, Kind.DISCOVERY),
    QUEST("script", false, Kind.QUEST),
    STAT("bolt", true, Kind.GEAR, Kind.INGREDIENT, Kind.MATERIAL, Kind.TOME, Kind.CHARM),
    SERVICE("user", false, Kind.SERVICE, Kind.RESOURCE, Kind.NPC);

    private final String icon;
    private final boolean facet;
    private final Set<Kind> kinds;

    SearchCategory(String icon, boolean facet, Kind... kinds) {
        this.icon = icon;
        this.facet = facet;
        // an EnumSet, so the filter menu lists them in Kind's order on every launch (Set.copyOf's order is salted per JVM)
        this.kinds = kinds.length == 0 ? Set.of() : Collections.unmodifiableSet(EnumSet.of(kinds[0], kinds));
    }

    public String icon() {
        return icon;
    }

    /** The kinds it lists, in {@link Kind}'s order; empty for ALL (every kind). */
    public Set<Kind> kinds() {
        return kinds;
    }

    /** Its count: the sum of its kinds' (every kind's for ALL). */
    public int count(Map<Kind, Integer> counts) {
        int n = 0;
        for (Map.Entry<Kind, Integer> e : counts.entrySet()) {
            if (kinds.isEmpty() || kinds.contains(e.getKey())) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** The kinds a query asks for: the group's, narrowed to {@code picked} when any of them are picked. */
    public Set<Kind> query(Set<Kind> picked) {
        if (picked == null || picked.isEmpty()) {
            return kinds;
        }
        Set<Kind> out = EnumSet.noneOf(Kind.class);
        for (Kind k : picked) {
            if (kinds.isEmpty() || kinds.contains(k)) {
                out.add(k);
            }
        }
        return out.isEmpty() ? kinds : out;
    }

    /** Whether it has kinds of its own to narrow to in the filter menu (more than one). */
    public boolean narrows() {
        return kinds.size() > 1 && !facet;
    }

    /** Whether the strip shows its count (a facet's would repeat another category's). */
    public boolean counted() {
        return !facet;
    }
}
