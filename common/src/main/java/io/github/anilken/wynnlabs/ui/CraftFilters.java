package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.crafting.SuggestionFilter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The suggestions page's filters ({@link SuggestionFilter}) for the character played, kept in its folder. */
final class CraftFilters {

    private static SuggestionFilter filter;
    private static String loadedFor;

    private CraftFilters() {}

    static Path file() {
        String id = Profile.id();
        return Dirs.config().resolve("characters").resolve(id == null || id.isEmpty() ? "none" : id).resolve("craft-filters.json");
    }

    /** The character's filters, read once per character. */
    static synchronized SuggestionFilter get() {
        String who = String.valueOf(Profile.id());
        if (filter == null || !who.equals(loadedFor)) {
            loadedFor = who;
            String json = null;
            try {
                Path f = file();
                if (Files.exists(f)) {
                    json = Files.readString(f, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                System.out.println("[WynnLabs] could not read the suggestion filters: " + e);
            }
            filter = SuggestionFilter.fromJson(json);
        }
        return filter;
    }

    /** Whose filters are loaded (the panel copies them in once per character). */
    static synchronized String owner() {
        get();
        return loadedFor;
    }

    static synchronized void save() {
        if (filter != null) {
            String json = filter.toJson();
            Saver.mark(file(), () -> json);
        }
    }
}
