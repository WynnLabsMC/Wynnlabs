package io.github.anilken.wynnlabs.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The suggestions page's filters, kept per character: goal groups (a combination matches when any
 * of its goals does), "wearable now" (requirements within the player's skill points), a search over
 * names, and Singles' item type, level and status and Sets' status (a set counts as its worst piece).
 */
public final class SuggestionFilter {

    /** The goal groups offered, and the goals each stands for. */
    public static final Map<String, List<String>> GROUPS = groups();

    public final Set<String> groups = new LinkedHashSet<>();
    public boolean wearable;
    public String query = "";
    /** Singles: the item kind ("weapon" …), the level bracket and the status; null for all. */
    public String kind;
    public Integer level;
    public SuggestionBoard.Status singles;
    /** Sets: the status; null for all. */
    public SuggestionBoard.Status sets;
    /** The search tier (Best, a few bought, owned only); null for {@link SuggestionBoard#DEFAULT_TIER}. */
    public CraftSearch.Tier tier;

    /** The tier the page shows. */
    public CraftSearch.Tier tierShown() {
        return tier == null ? SuggestionBoard.DEFAULT_TIER : tier;
    }

    /**
     * How many choices of the Filter menu are on, for its badge: the tier when not the default, each
     * goal group, wearable now, and Sets' status or Singles' item type and level.
     */
    public int count(boolean sets) {
        int n = groups.size() + (wearable ? 1 : 0) + (tier != null && tier != SuggestionBoard.DEFAULT_TIER ? 1 : 0);
        if (sets) {
            return n + (this.sets != null ? 1 : 0);
        }
        return n + (kind != null ? 1 : 0) + (level != null ? 1 : 0);
    }

    private static Map<String, List<String>> groups() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("spell", List.of("rawspell", "spell"));
        m.put("melee", List.of("rawmelee", "melee", "attackspeed"));
        m.put("elements", List.of("earth", "thunder", "water", "fire", "air"));
        m.put("tank", List.of("health"));
        m.put("mana", List.of("mana"));
        m.put("spellcost", List.of("spellcost"));
        m.put("speed", List.of("speed"));
        m.put("poison", List.of("poison"));
        m.put("lifesteal", List.of("lifesteal"));
        m.put("lootxp", List.of("loot", "xp"));
        return java.util.Collections.unmodifiableMap(m);
    }

    /** Whether any filter is on (the page shows "Clear filters"). */
    public boolean active() {
        return !groups.isEmpty() || wearable || !query.isBlank() || kind != null || level != null || singles != null || sets != null
                || tier != null && tier != SuggestionBoard.DEFAULT_TIER;
    }

    public void clear() {
        groups.clear();
        wearable = false;
        query = "";
        kind = null;
        level = null;
        singles = null;
        sets = null;
        tier = null;
    }

    /** A goal or combination ("combo:rawspell+mana") is shown when no group is picked or any of its goals is in one. */
    public boolean goalMatches(String key) {
        if (groups.isEmpty()) {
            return true;
        }
        for (String part : Suggestions.parts(key)) {
            for (String g : groups) {
                if (GROUPS.getOrDefault(g, List.of()).contains(part)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Requirements within the skill points (unknown points: nothing is hidden). */
    public static boolean wearable(int[] req, int[] points) {
        if (points == null || java.util.Arrays.stream(points).allMatch(p -> p == 0)) {
            return true;
        }
        for (int k = 0; k < 5 && k < req.length; k++) {
            if (req[k] > points[k]) {
                return false;
            }
        }
        return true;
    }

    /** Every word of the search is in one of the names (any case); an empty search matches everything. */
    public static boolean queryMatches(String query, Collection<String> names) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String hay = String.join("\n", names).toLowerCase(Locale.ROOT);
        for (String w : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!hay.contains(w)) {
                return false;
            }
        }
        return true;
    }

    /** A set's status: its worst piece's (a level missing, else something to buy, else ready). */
    public static SuggestionBoard.Status setStatus(Map<SuggestionBoard.Status, ? extends Collection<?>> pieces) {
        if (!pieces.get(SuggestionBoard.Status.LEVEL).isEmpty()) {
            return SuggestionBoard.Status.LEVEL;
        }
        return pieces.get(SuggestionBoard.Status.BUY).isEmpty() ? SuggestionBoard.Status.READY : SuggestionBoard.Status.BUY;
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        JsonArray g = new JsonArray();
        groups.forEach(g::add);
        o.add("groups", g);
        o.addProperty("wearable", wearable);
        o.addProperty("query", query);
        if (kind != null) {
            o.addProperty("kind", kind);
        }
        if (level != null) {
            o.addProperty("level", level);
        }
        if (singles != null) {
            o.addProperty("singles", singles.name());
        }
        if (sets != null) {
            o.addProperty("sets", sets.name());
        }
        if (tier != null) {
            o.addProperty("tier", tier.name());
        }
        return o.toString();
    }

    /** The filters read back; unknown groups or statuses are dropped, anything unreadable gives no filter. */
    public static SuggestionFilter fromJson(String json) {
        SuggestionFilter f = new SuggestionFilter();
        if (json == null || json.isBlank()) {
            return f;
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("groups")) {
                o.getAsJsonArray("groups").forEach(e -> {
                    if (GROUPS.containsKey(e.getAsString())) {
                        f.groups.add(e.getAsString());
                    }
                });
            }
            f.wearable = o.has("wearable") && o.get("wearable").getAsBoolean();
            f.query = o.has("query") ? o.get("query").getAsString() : "";
            f.kind = o.has("kind") ? o.get("kind").getAsString() : null;
            f.level = o.has("level") ? o.get("level").getAsInt() : null;
            f.singles = status(o, "singles");
            f.sets = status(o, "sets");
            if (o.has("tier")) {
                try {
                    f.tier = CraftSearch.Tier.valueOf(o.get("tier").getAsString());
                } catch (IllegalArgumentException e) {
                    f.tier = null;
                }
            }
        } catch (RuntimeException e) {
            return new SuggestionFilter();
        }
        return f;
    }

    private static SuggestionBoard.Status status(JsonObject o, String k) {
        if (!o.has(k)) {
            return null;
        }
        try {
            return SuggestionBoard.Status.valueOf(o.get(k).getAsString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
