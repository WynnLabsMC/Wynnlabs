package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.SuggestionCache;
import io.github.anilken.wynnlabs.crafting.Suggestions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * The suggestions page's disk cache ({@link SuggestionCache}) for the character played: one file in
 * its folder (the results depend on its stock and skill points), read once per character and
 * ingredient data, written a moment after each calculation.
 */
final class CraftSuggestCache {

    private static SuggestionCache cache;
    private static String loadedFor;
    private static List<Ingredient> dataOf;
    private static String data;

    private CraftSuggestCache() {}

    static Path file() {
        String id = Profile.id();
        return Dirs.config().resolve("characters").resolve(id == null || id.isEmpty() ? "none" : id).resolve("craft-suggestions.json");
    }

    /** The cache for the character and the ingredient data shown; read from its file when either changed. */
    static synchronized SuggestionCache get(CraftingTab tab) {
        if (dataOf != tab.ingredients) {
            dataOf = tab.ingredients;
            data = SuggestionCache.dataVersion(tab.ingredients);
        }
        String key = Profile.id() + "|" + data;
        if (cache == null || !key.equals(loadedFor)) {
            loadedFor = key;
            String json = null;
            try {
                Path f = file();
                if (Files.exists(f)) {
                    json = Files.readString(f, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                System.out.println("[WynnLabs] could not read the suggestions cache: " + e);
            }
            cache = SuggestionCache.fromJson(json, data);
        }
        return cache;
    }

    /** Written a moment later, off the game thread. */
    static synchronized void save() {
        if (cache != null) {
            SuggestionCache c = cache;
            Saver.mark(file(), c::toJson);
        }
    }

    /** What a result here was worked out from: the owned stock, the skill points and the level. */
    static String stock(CraftingTab tab) {
        return SuggestionCache.stockPrint(tab.suggestionsPanel.stockForSearch(), tab.points, CraftSuggestionsPanel.combatLevel());
    }

    /** A goal set by its key, for reading results back. */
    static Function<String, Suggestions.GoalSet> goals(CraftingTab tab) {
        List<Suggestions.GoalSet> all = tab.suggestionsPanel.goalSets();
        return k -> {
            if (k.startsWith(Suggestions.COMBO)) {
                return Suggestions.combined(Suggestions.parts(k), CraftSuggestionsPanel.combatLevel(), tab.suggestionsPanel.points());
            }
            for (Suggestions.GoalSet g : all) {
                if (g.key().equals(k)) {
                    return g;
                }
            }
            return null;
        };
    }

    static Function<String, Ingredient> ingredients(CraftingTab tab) {
        return n -> {
            Ingredient in = tab.byName.get(n);
            return in == null || in.empty() ? null : in;
        };
    }

    /** "Updated 3 h ago" for when a result was worked out; " · your items changed" when its stock print is not today's. */
    static String updated(CraftingTab tab, SuggestionCache.Entry e) {
        String t = L.t("wynnlabs.craft.calc_updated", MenuPaint.ago(e.at()));
        return e.stock().equals(stock(tab)) ? t : t + " · " + L.t("wynnlabs.craft.calc_changed");
    }

    private static final class L {
        static String t(String k, Object... a) {
            return io.github.anilken.wynnlabs.core.L.t(k, a);
        }
    }
}
