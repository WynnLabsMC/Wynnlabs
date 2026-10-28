package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.GameState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * The build the Home's stats compare with, as picked in the header's menu, kept per character
 * ({@code stats-compare.txt} in the character's folder), and the one rule that turns it into what
 * is compared: a pick that is still there wins; otherwise ("Automatic", or a saved build since
 * deleted, a level pill no longer selected) the default order decides.
 */
final class StatsChoice {

    private static final String FILE = "stats-compare.txt";

    /** The character the choice was read for; null before the first read. */
    private static String loadedFor;
    private static String choice = StatsSource.AUTO;

    private StatsChoice() {}

    /** The picked id ({@link StatsSource#AUTO} when none). */
    static String get() {
        String id = characterId();
        if (!id.equals(loadedFor)) {
            loadedFor = id;
            choice = read(id);
        }
        return choice;
    }

    static void set(String c) {
        String v = c == null || c.isBlank() ? StatsSource.AUTO : c;
        get();
        if (v.equals(choice)) {
            return;
        }
        choice = v;
        String id = characterId();
        if (!id.isEmpty()) {
            Saver.mark(file(id), () -> v);
        }
    }

    /** Tests: the next read takes the file again. */
    static void forget() {
        loadedFor = null;
        choice = StatsSource.AUTO;
    }

    /**
     * What is compared: {@code choice} when it is among {@code available} (or "none"), else the
     * default order: the Plan pill's plan, the level pill's build, the "Detailed" build, the
     * applied plan, the suggestion; "none" when none of them is there.
     */
    static String target(String choice, Set<String> available, boolean planPill) {
        if (StatsSource.NONE.equals(choice)) {
            return StatsSource.NONE;
        }
        if (choice != null && !StatsSource.AUTO.equals(choice) && available.contains(choice)) {
            return choice;
        }
        if (planPill && available.contains("plan")) {
            return "plan";
        }
        for (String c : new String[] {"level", "pick", "plan", "suggested"}) {
            if (available.contains(c)) {
                return c;
            }
        }
        return StatsSource.NONE;
    }

    /** The row ticked in the menu: the pick while it is there, "Automatic" otherwise. */
    static String ticked(String choice, Set<String> available) {
        return StatsSource.NONE.equals(choice) || choice != null && available.contains(choice) ? choice : StatsSource.AUTO;
    }

    private static String read(String id) {
        if (id.isEmpty()) {
            return StatsSource.AUTO;
        }
        try {
            Path f = file(id);
            Saver.settle(f);
            if (!Files.exists(f)) {
                return StatsSource.AUTO;
            }
            String text = Files.readString(f, StandardCharsets.UTF_8).trim();
            Saver.known(f, text);
            return text.isEmpty() ? StatsSource.AUTO : text;
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not read " + FILE + ": " + t);
            return StatsSource.AUTO;
        }
    }

    private static Path file(String id) {
        Path root = Locks.rootOverride != null ? Locks.rootOverride : Dirs.config().resolve("characters");
        return root.resolve(id).resolve(FILE);
    }

    private static String characterId() {
        try {
            String id = GameState.current().characterId();
            return id == null ? "" : id;
        } catch (Throwable t) {
            return "";
        }
    }
}
