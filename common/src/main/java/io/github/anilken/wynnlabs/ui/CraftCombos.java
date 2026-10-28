package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.crafting.Suggestions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's own combined goals for sets ("combo:rawspell+loot"), kept per character in its
 * folder, read once per character.
 */
final class CraftCombos {

    private static List<String> combos;
    private static String loadedFor;

    private CraftCombos() {}

    static Path file() {
        String id = Profile.id();
        return Dirs.config().resolve("characters").resolve(id == null || id.isEmpty() ? "none" : id).resolve("craft-combos.json");
    }

    static synchronized List<String> load() {
        String who = String.valueOf(Profile.id());
        if (combos == null || !who.equals(loadedFor)) {
            loadedFor = who;
            combos = new ArrayList<>();
            try {
                Path f = file();
                if (Files.exists(f)) {
                    combos.addAll(fromJson(Files.readString(f, StandardCharsets.UTF_8)));
                }
            } catch (Exception e) {
                System.out.println("[WynnLabs] could not read the combined goals: " + e);
            }
        }
        return List.copyOf(combos);
    }

    /** Adds a combination (two to four goals); returns its key, or null when it is not one or already there. */
    static synchronized String add(List<String> parts) {
        if (parts.size() < 2 || parts.size() > 4) {
            return null;
        }
        String key = Suggestions.comboKey(parts);
        load();
        if (combos.contains(key)) {
            return key;
        }
        combos.add(key);
        String json = toJson(combos);
        Saver.mark(file(), () -> json);
        return key;
    }

    static synchronized void remove(String key) {
        load();
        if (combos.remove(key)) {
            String json = toJson(combos);
            Saver.mark(file(), () -> json);
        }
    }

    static String toJson(List<String> keys) {
        JsonArray a = new JsonArray();
        keys.forEach(a::add);
        return a.toString();
    }

    /** The keys read back; only combined keys of two to four goals. */
    static List<String> fromJson(String json) {
        List<String> out = new ArrayList<>();
        try {
            JsonParser.parseString(json).getAsJsonArray().forEach(e -> {
                String k = e.getAsString();
                int n = Suggestions.parts(k).size();
                if (k.startsWith(Suggestions.COMBO) && n >= 2 && n <= 4) {
                    out.add(k);
                }
            });
        } catch (RuntimeException e) {
            return List.of();
        }
        return out;
    }
}
