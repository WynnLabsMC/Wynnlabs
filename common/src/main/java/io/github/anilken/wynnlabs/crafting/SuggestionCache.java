package io.github.anilken.wynnlabs.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The suggestions worked out so far, kept on disk so the page opens with them at once: Singles
 * by level bracket, Sets by weapon and band, each with when it was worked out and a print of what
 * it was worked out from (the owned stock and skill points, which the scores and the "to buy" lists
 * depend on). A cache made for other ingredient data or by another {@link #VERSION} of the scoring
 * is dropped on reading. Only results go in: a suggestion's crafted item is crafted again from its
 * recipe on reading.
 */
public final class SuggestionCache {

    /** Bump when the scoring or the suggestion's shape changes: older caches are dropped. */
    public static final int VERSION = 1;

    /** One bracket's or band's results: the stock print they were worked out from, when, and the items. */
    public record Entry(String stock, long at, JsonArray items) {}

    private final String data;
    private final Map<Integer, Entry> singles = new LinkedHashMap<>();
    private final Map<String, Entry> sets = new LinkedHashMap<>();

    public SuggestionCache(String data) {
        this.data = data;
    }

    public String data() {
        return data;
    }

    // ---- keys ----

    /** A print of the ingredient data: the names, tiers, levels and identifications of every ingredient. */
    public static String dataVersion(List<Ingredient> all) {
        long h = 1125899906842597L;
        for (Ingredient in : all) {
            h = 31 * h + in.name().hashCode();
            h = 31 * h + in.tier();
            h = 31 * h + in.level();
            h = 31 * h + in.ids().keySet().hashCode();
        }
        return all.size() + ":" + Long.toHexString(h);
    }

    /** A print of what a result depends on besides the data: the owned stock, the skill points, the level. */
    public static String stockPrint(WhatCanICraft.Stock stock, int[] points, int combatLevel) {
        long h = 17;
        h = 31 * h + new java.util.TreeMap<>(stock.ingredients()).hashCode();
        h = 31 * h + stock.materials().hashCode();
        h = 31 * h + java.util.Arrays.hashCode(points);
        h = 31 * h + combatLevel;
        return Long.toHexString(h);
    }

    static String setKey(String weapon, int band) {
        return weapon + "|" + band;
    }

    // ---- singles ----

    public boolean hasSingles(int bracket) {
        return singles.containsKey(bracket);
    }

    public Entry singlesEntry(int bracket) {
        return singles.get(bracket);
    }

    public void putSingles(int bracket, String stock, long at, List<Suggestions.Suggestion> items) {
        JsonArray a = new JsonArray();
        items.forEach(s -> a.add(encode(s)));
        singles.put(bracket, new Entry(stock, at, a));
    }

    /** A bracket's suggestions read back (those whose goal, recipe or ingredients are no longer known are left out). */
    public List<Suggestions.Suggestion> singles(int bracket, Function<String, Suggestions.GoalSet> goals,
                                                Function<String, Ingredient> ingredients) {
        Entry e = singles.get(bracket);
        List<Suggestions.Suggestion> out = new ArrayList<>();
        if (e != null) {
            for (JsonElement el : e.items()) {
                Suggestions.Suggestion s = decode(el.getAsJsonObject(), goals, ingredients);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Forgets one bracket, or every one ({@code bracket} null). */
    public void clearSingles(Integer bracket) {
        if (bracket == null) {
            singles.clear();
        } else {
            singles.remove(bracket);
        }
    }

    public java.util.Set<Integer> singlesBrackets() {
        return java.util.Collections.unmodifiableSet(singles.keySet());
    }

    // ---- sets ----

    public boolean hasSets(String weapon, int band) {
        return sets.containsKey(setKey(weapon, band));
    }

    public Entry setsEntry(String weapon, int band) {
        return sets.get(setKey(weapon, band));
    }

    public void putSets(String weapon, int band, String stock, long at, List<SetSuggestions.CraftSet> items) {
        JsonArray a = new JsonArray();
        items.forEach(s -> a.add(encode(s)));
        sets.put(setKey(weapon, band), new Entry(stock, at, a));
    }

    /** Adds sets to a band's entry (a combination worked out later), replacing those of the same goals. */
    public void addSets(String weapon, int band, String stock, long at, List<SetSuggestions.CraftSet> items) {
        Entry old = sets.get(setKey(weapon, band));
        java.util.Set<String> goals = new java.util.HashSet<>();
        items.forEach(x -> goals.add(x.goal().key()));
        JsonArray a = new JsonArray();
        if (old != null) {
            for (JsonElement e : old.items()) {
                if (!goals.contains(e.getAsJsonObject().get("g").getAsString())) {
                    a.add(e);
                }
            }
        }
        items.forEach(x -> a.add(encode(x)));
        sets.put(setKey(weapon, band), new Entry(old == null ? stock : old.stock(), old == null ? at : old.at(), a));
    }

    public List<SetSuggestions.CraftSet> sets(String weapon, int band, Function<String, Suggestions.GoalSet> goals,
                                              Function<String, Ingredient> ingredients) {
        Entry e = sets.get(setKey(weapon, band));
        List<SetSuggestions.CraftSet> out = new ArrayList<>();
        if (e != null) {
            for (JsonElement el : e.items()) {
                SetSuggestions.CraftSet s = decodeSet(el.getAsJsonObject(), goals, ingredients);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public void clearSets(String weapon, int band) {
        sets.remove(setKey(weapon, band));
    }

    public void clearSets(String weapon) {
        sets.keySet().removeIf(k -> k.startsWith(weapon + "|"));
    }

    // ---- the file ----

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("version", VERSION);
        o.addProperty("data", data);
        JsonObject s = new JsonObject();
        singles.forEach((k, e) -> s.add(String.valueOf(k), entry(e)));
        o.add("singles", s);
        JsonObject t = new JsonObject();
        sets.forEach((k, e) -> t.add(k, entry(e)));
        o.add("sets", t);
        return o.toString();
    }

    /** The cache read from its file, or an empty one for {@code data} when it is missing, unreadable, older or for other data. */
    public static SuggestionCache fromJson(String json, String data) {
        SuggestionCache c = new SuggestionCache(data);
        if (json == null || json.isBlank()) {
            return c;
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.get("version").getAsInt() != VERSION || !data.equals(o.get("data").getAsString())) {
                return c;
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("singles").entrySet()) {
                c.singles.put(Integer.parseInt(e.getKey()), entry(e.getValue().getAsJsonObject()));
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("sets").entrySet()) {
                c.sets.put(e.getKey(), entry(e.getValue().getAsJsonObject()));
            }
        } catch (RuntimeException e) {
            return new SuggestionCache(data);
        }
        return c;
    }

    private static JsonObject entry(Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("stock", e.stock());
        o.addProperty("at", e.at());
        o.add("items", e.items());
        return o;
    }

    private static Entry entry(JsonObject o) {
        return new Entry(o.get("stock").getAsString(), o.get("at").getAsLong(), o.getAsJsonArray("items"));
    }

    // ---- one suggestion ----

    static JsonObject encode(Suggestions.Suggestion s) {
        JsonObject o = new JsonObject();
        o.addProperty("g", s.goal().key());
        o.addProperty("tier", s.tier().name());
        o.addProperty("o", s.order());
        o.add("r", recipe(s.recipe(), s.tier1(), s.tier2(), s.layout()));
        return o;
    }

    static Suggestions.Suggestion decode(JsonObject o, Function<String, Suggestions.GoalSet> goals, Function<String, Ingredient> ingredients) {
        Suggestions.GoalSet g = goals.apply(o.get("g").getAsString());
        if (g == null) {
            return null;
        }
        JsonObject r = o.getAsJsonObject("r");
        CraftData.Recipe recipe = CraftData.find(r.get("type").getAsString(), r.get("lv").getAsInt());
        CraftSearch.Layout l = layout(r, recipe, ingredients);
        if (recipe == null || l == null) {
            return null;
        }
        return new Suggestions.Suggestion(g, recipe, r.get("t1").getAsInt(), r.get("t2").getAsInt(), l,
                CraftSearch.Tier.valueOf(o.get("tier").getAsString()), o.get("o").getAsInt());
    }

    static JsonObject encode(SetSuggestions.CraftSet s) {
        JsonObject o = new JsonObject();
        o.addProperty("g", s.goal().key());
        o.addProperty("step", s.step());
        o.addProperty("tier", s.tier().name());
        JsonArray pieces = new JsonArray();
        for (SetSuggestions.Piece p : s.pieces()) {
            JsonObject po = new JsonObject();
            po.addProperty("slot", p.type());
            if (p.recipe() != null) {
                po.add("r", recipe(p.recipe(), p.tier1(), p.tier2(), p.layout()));
            }
            pieces.add(po);
        }
        o.add("pieces", pieces);
        return o;
    }

    static SetSuggestions.CraftSet decodeSet(JsonObject o, Function<String, Suggestions.GoalSet> goals,
                                             Function<String, Ingredient> ingredients) {
        Suggestions.GoalSet g = goals.apply(o.get("g").getAsString());
        if (g == null) {
            return null;
        }
        List<SetSuggestions.Piece> pieces = new ArrayList<>();
        Map<String, Craft.Range> totals = new LinkedHashMap<>();
        int[] maxReq = new int[5];
        List<String> bought = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray("pieces")) {
            JsonObject po = el.getAsJsonObject();
            String slot = po.get("slot").getAsString();
            if (!po.has("r")) {
                pieces.add(new SetSuggestions.Piece(slot, null, 1, 1, null));
                continue;
            }
            JsonObject r = po.getAsJsonObject("r");
            CraftData.Recipe recipe = CraftData.find(r.get("type").getAsString(), r.get("lv").getAsInt());
            if (recipe == null) {
                return null;
            }
            CraftSearch.Layout l = layout(r, recipe, ingredients);
            if (r.has("slots") && l == null) {
                return null;
            }
            pieces.add(new SetSuggestions.Piece(slot, recipe, r.get("t1").getAsInt(), r.get("t2").getAsInt(), l));
            if (l == null) {
                continue;
            }
            bought.addAll(l.bought());
            if (!recipe.consumable()) {
                l.result().ids().forEach((id, v) -> totals.merge(id, v, (a, b) -> new Craft.Range(a.min() + b.min(), a.max() + b.max())));
                for (int k = 0; k < 5; k++) {
                    maxReq[k] = Math.max(maxReq[k], l.result().requirements()[k]);
                }
            }
        }
        return new SetSuggestions.CraftSet(g, o.get("step").getAsInt(), CraftSearch.Tier.valueOf(o.get("tier").getAsString()),
                List.copyOf(pieces), totals, maxReq, List.copyOf(bought));
    }

    private static JsonObject recipe(CraftData.Recipe r, int t1, int t2, CraftSearch.Layout l) {
        JsonObject o = new JsonObject();
        o.addProperty("type", r.type());
        o.addProperty("lv", r.minLevel());
        o.addProperty("t1", t1);
        o.addProperty("t2", t2);
        if (l != null) {
            JsonArray slots = new JsonArray();
            l.slots().forEach(in -> slots.add(in.name()));
            o.add("slots", slots);
            o.addProperty("score", l.score());
            o.add("bought", strings(l.bought()));
            o.addProperty("cost", l.cost());
            o.add("unpriced", strings(l.unpriced()));
            o.add("old", strings(l.oldPrices()));
        }
        return o;
    }

    /** The layout read back, its crafted item crafted again; null without one or with an unknown ingredient. */
    private static CraftSearch.Layout layout(JsonObject o, CraftData.Recipe recipe, Function<String, Ingredient> ingredients) {
        if (recipe == null || !o.has("slots")) {
            return null;
        }
        List<Ingredient> slots = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("slots")) {
            String n = e.getAsString();
            if (n.isEmpty()) {
                slots.add(Ingredient.NONE);
                continue;
            }
            Ingredient in = ingredients.apply(n);
            if (in == null) {
                return null;
            }
            slots.add(in);
        }
        int t1 = o.get("t1").getAsInt();
        int t2 = o.get("t2").getAsInt();
        Craft.Result result = Craft.craft(recipe, t1, t2, Craft.Speed.NORMAL, slots);
        return new CraftSearch.Layout(List.copyOf(slots), result, o.get("score").getAsDouble(), list(o, "bought"),
                o.get("cost").getAsLong(), list(o, "unpriced"), list(o, "old"));
    }

    private static JsonArray strings(List<String> l) {
        JsonArray a = new JsonArray();
        l.forEach(a::add);
        return a;
    }

    private static List<String> list(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (o.has(k)) {
            o.getAsJsonArray(k).forEach(e -> out.add(e.getAsString()));
        }
        return List.copyOf(out);
    }
}
