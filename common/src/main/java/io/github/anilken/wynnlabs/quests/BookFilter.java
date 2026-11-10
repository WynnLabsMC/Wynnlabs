package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.game.Wyn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The content book filter the player last had. The world event read leaves the book filtered to
 * world events, so the filter seen on the player's own visits is kept (across restarts) and put
 * back once the map is closed. Part of that read, behind the same setting: one more hidden look
 * at the book's first page after a read, and none when the filter is unknown or already right.
 */
public final class BookFilter {

    static final String WORLD_EVENT = "World Event";
    /** How long after a read was asked for before the filter is put back (the read is over by then). */
    private static final long SETTLE = 5_000L;

    private static volatile String kept;
    private static volatile boolean loaded;
    private static volatile boolean moved;
    private static volatile long movedAt;

    private BookFilter() {}

    /** The filter to keep after the open book showed {@code seen}; a read's own leftover is not the player's. */
    static String keeps(String kept, boolean moved, String seen) {
        if (seen == null || seen.isBlank() || moved && WORLD_EVENT.equals(seen)) {
            return kept;
        }
        return seen;
    }

    /** The player has the book open, filtered to {@code name} (null when the button is unreadable). */
    public static void seen(String name) {
        load();
        String next = keeps(kept, moved, name);
        if (name != null && !(moved && WORLD_EVENT.equals(name))) {
            moved = false;                          // the player chose a filter since: nothing to put back
        }
        if (next != null && !next.equals(kept)) {
            kept = next;
            try {
                Files.createDirectories(file().getParent());
                Files.writeString(file(), next, StandardCharsets.UTF_8);
            } catch (Throwable t) {
                System.out.println("[WynnLabs] content book filter not saved: " + t);
            }
        }
    }

    /** A world event read was asked for: the book is on world events from now on. */
    static void moved(long now) {
        moved = true;
        movedAt = now;
    }

    /** Once a second: puts the player's filter back after a read, once no screen is open. */
    public static void tick(boolean screenOpen, long now) {
        if (!moved || screenOpen || now - movedAt < SETTLE) {
            return;
        }
        moved = false;
        load();
        String back = kept;
        if (back != null && !WORLD_EVENT.equals(back)) {
            Wyn.showBookFilter(back);
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (Files.exists(file())) {
                String s = Files.readString(file(), StandardCharsets.UTF_8).trim();
                kept = s.isEmpty() ? null : s;
            }
        } catch (Throwable t) {
            System.out.println("[WynnLabs] content book filter unreadable: " + t);
        }
    }

    private static Path file() {
        return Dirs.config().resolve("book-filter.txt");
    }
}
