package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntPredicate;

/**
 * Where the menu was when it closed, so the menu key opens it there again: the tab (its rail
 * entry and section), and the cheap view state that matters: the quests tab's kind, the picked
 * quest or discovery and Rehber or Noktalar; the settings' category; the search's query. Kept in
 * the character's folder ({@code menu-state.json}), read once per character and written (off the
 * render thread) when the menu closes. Popups are never kept.
 */
final class MenuResume {

    /** The menu as it was: {@code tab} -1 for none; the strings null when not set. */
    record State(int tab, String questType, String questSelected, String pickedType, int questView, int settingsCat,
                 String search, String tracked) {

        static final State NONE = new State(-1, null, null, null, 0, 0, null, null);
    }

    private static State last = State.NONE;
    private static String lastFor;

    private MenuResume() {}

    /** The tab to open: the stored one while it still has a page ({@code exists}), else Home (0). */
    static int tab(int stored, IntPredicate exists) {
        return stored >= 0 && exists.test(stored) ? stored : 0;
    }

    /**
     * Whether the stored place wins over the tracked quest: unless a quest is tracked now that was
     * not when the menu closed (the player just tracked it in the game: the quests tab shows it).
     */
    static boolean resumeOverTracked(State s, String trackedNow) {
        return trackedNow == null || trackedNow.isEmpty() || trackedNow.equals(s.tracked());
    }

    // ---- the file ----

    private static Path file() {
        return Profile.dir().resolve("menu-state.json");
    }

    /** This character's last state (read from its file the first time). */
    static State load() {
        String id = Profile.id();
        if (!id.equals(lastFor)) {
            lastFor = id;
            last = State.NONE;
            try {
                Path f = file();
                if (Files.exists(f)) {
                    String text = Files.readString(f, StandardCharsets.UTF_8);
                    Saver.known(f, text);
                    last = parse(text);
                }
            } catch (Exception e) {
                last = State.NONE;              // a broken file: the menu opens on Home
            }
        }
        return last;
    }

    /** The menu closed in {@code s}: kept, and written soon. */
    static void save(State s) {
        last = s;
        lastFor = Profile.id();
        Saver.mark(file(), () -> json(s));
    }

    static State parse(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        return new State(o.has("tab") ? o.get("tab").getAsInt() : -1, str(o, "questType"), str(o, "questSelected"),
                str(o, "pickedType"), o.has("questView") ? o.get("questView").getAsInt() : 0,
                o.has("settingsCat") ? o.get("settingsCat").getAsInt() : 0, str(o, "search"), str(o, "tracked"));
    }

    static String json(State s) {
        JsonObject o = new JsonObject();
        o.addProperty("tab", s.tab());
        put(o, "questType", s.questType());
        put(o, "questSelected", s.questSelected());
        put(o, "pickedType", s.pickedType());
        o.addProperty("questView", s.questView());
        o.addProperty("settingsCat", s.settingsCat());
        put(o, "search", s.search());
        put(o, "tracked", s.tracked());
        return o.toString();
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static void put(JsonObject o, String k, String v) {
        if (v != null && !v.isEmpty()) {
            o.addProperty(k, v);
        }
    }
}
