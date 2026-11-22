package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.game.Wyn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * The content book filter the player last had. The world event read leaves the book filtered to
 * world events, so the filter seen on the player's own visits, or the one the read's first look at
 * the book found, is kept (across restarts) and put back once the map is closed ("Recommended",
 * the book's own default, while none was ever seen). Part of that read, behind the same setting: one more hidden look
 * at the book's first page after a read, and none when the filter is unknown or already right.
 */
public final class BookFilter {

    static final String WORLD_EVENT = "World Event";
    /** The book's own filter when the player never set one the mod saw. */
    static final String DEFAULT = "Recommended";
    /** The filter button's active line: white dash, grey name ("§f- §7Cave"). */
    private static final String ACTIVE = "§f- ";
    /** How long after a read was asked for before the filter is put back (the read is over by then). */
    private static final long SETTLE = 5_000L;

    private static volatile String kept;
    private static volatile boolean loaded;
    private static volatile boolean moved;
    private static volatile long movedAt;
    /** A read was asked for and its first look at the book has not come yet. */
    private static volatile boolean capturing;

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
            keep(next);
        }
    }

    private static void keep(String next) {
        load();
        if (next.equals(kept)) {
            return;
        }
        kept = next;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), next, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            System.out.println("[WynnLabs] content book filter not saved: " + t);
        }
    }

    /** A world event read was asked for: the book is on world events from now on. */
    static void moved(long now) {
        moved = true;
        movedAt = now;
        capturing = true;
    }

    /**
     * A menu's items came from the server. The read's first look at the book shows the filter as
     * the player left it, before the read turns it to world events: that one is kept.
     */
    public static void fromMenu(List<ItemStack> items) {
        if (!capturing || items == null) {
            return;
        }
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty() || !FILTER.equals(name(stack))) {
                continue;
            }
            capturing = false;
            ItemLore lore = stack.get(DataComponents.LORE);
            String active = lore == null ? null : before(lore.lines().stream().map(Component::getString).toList());
            if (active != null) {
                keep(active);
            }
            return;
        }
    }

    private static final String FILTER = "Filter";

    /**
     * The filter to keep from the button's lore as a read first found it: its active line's name,
     * null when there is none or it is already world events (nothing of the player's to keep).
     */
    static String before(List<String> lore) {
        for (String s : lore) {
            if (s != null && s.startsWith(ACTIVE)) {
                String n = ChatFormatting.stripFormatting(s.substring(ACTIVE.length()));
                n = n == null ? "" : n.trim();
                return n.isEmpty() || WORLD_EVENT.equals(n) ? null : n;
            }
        }
        return null;
    }

    /** An item's plain name: no formatting, no spacing glyphs (private use characters, since 2.2.6). */
    static String name(ItemStack stack) {
        String s = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        return s == null ? "" : s.replaceAll("\\p{Co}", "").trim();
    }

    /** Once a second: puts the player's filter back after a read, once no screen is open. */
    public static void tick(boolean screenOpen, long now) {
        if (!moved || screenOpen || now - movedAt < SETTLE) {
            return;
        }
        moved = false;
        capturing = false;
        load();
        // the book's own default when the player's filter was never seen
        String back = kept == null ? DEFAULT : kept;
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
