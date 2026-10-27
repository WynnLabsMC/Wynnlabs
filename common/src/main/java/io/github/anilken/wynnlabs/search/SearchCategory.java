package io.github.anilken.wynnlabs.search;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The search tab's category strip: the thirteen kinds in the seven groups a player asks for.
 * Items (gear, ingredients, materials, tomes, charms), mobs, places (places, content, discoveries),
 * quests, stats (the items that roll a stat), services (services, resources, NPCs). A group's
 * count is the sum of its kinds' counts; ALL lists everything.
 */
public enum SearchCategory {
    ALL("circles"),
    ITEM("box", Kind.GEAR, Kind.INGREDIENT, Kind.MATERIAL, Kind.TOME, Kind.CHARM),
    MOB("skull", Kind.MOB),
    PLACE("map-pin", Kind.PLACE, Kind.CONTENT, Kind.DISCOVERY),
    QUEST("script", Kind.QUEST),
    STAT("bolt", Kind.GEAR, Kind.INGREDIENT, Kind.MATERIAL, Kind.TOME, Kind.CHARM),
    SERVICE("user", Kind.SERVICE, Kind.RESOURCE, Kind.NPC);

    private final String icon;
    private final Set<Kind> kinds;

    SearchCategory(String icon, Kind... kinds) {
        this.icon = icon;
        this.kinds = kinds.length == 0 ? Set.of() : Set.copyOf(EnumSet.of(kinds[0], kinds));
    }

    public String icon() {
        return icon;
    }

    /** The kinds it lists; empty for ALL (every kind). */
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
        return kinds.size() > 1 && this != STAT;
    }
}
