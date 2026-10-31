package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.game.Wyn;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Content Book the player has open by hand. Wynntils only fills its lists through its
 * own scripted read, and the mod's own ⟳ needs a press; a player who simply opens the book on
 * the Cave page and scrolls through it has shown the mod everything it needs. While the book is
 * open this takes the entries of the page on screen and adds them to the character's book copy
 * ({@link Content#tookPage}), page by page. Only looks at the open menu; never clicks or pages.
 */
public final class ContentBookPage {

    /** The filter button's active line: white dash, grey name ("§f- §7Cave"); the others are grey and dark. */
    private static final String ACTIVE_FILTER = "§f- ";
    /** The level an entry asks for, in its lore ("Combat Lv. Min: 32", "Recommended Combat Lv: 32"). */
    private static final Pattern LEVEL = Pattern.compile("Combat Lv(?:\\. Min)?: (\\d+)\\s*$");

    private ContentBookPage() {}

    /** Reads the open Content Book's page, when one is open and filtered to a kind the mod keeps. */
    public static void capture() {
        Wyn.BookPage page = Wyn.contentBookPage();
        if (page == null) {
            return;                             // another screen, or none
        }
        ContentType type = filterOf(page.filter());
        List<Wyn.ContentLine> lines = lines(page.entries());
        diagnose(page, type, lines.size());
        if (type == null || !type.book()) {
            return;                             // "All", or a kind the mod has no list for
        }
        if (!lines.isEmpty()) {
            Content.tookPage(type, lines);
        }
    }

    private static String lastDiag = "";

    /** One log line per change of what the open book shows, so a silent miss can be told from a miss. */
    private static void diagnose(Wyn.BookPage page, ContentType type, int usable) {
        String d = (type == null ? "no filter kind" : type.shown) + ", " + page.entries().size() + " items, " + usable + " entries"
                + (page.filter() == null ? ", no filter button" : "");
        if (!d.equals(lastDiag)) {
            lastDiag = d;
            System.out.println("[WynnLabs] content book open: " + d);
        }
    }

    /** The kind the book is filtered to (its name on the active line), or null when none or unknown. */
    static ContentType filterOf(Wyn.BookSlot filter) {
        if (filter == null) {
            return null;
        }
        for (String line : filter.lore()) {
            if (line.startsWith(ACTIVE_FILTER)) {
                String name = plain(line.substring(ACTIVE_FILTER.length()));
                for (ContentType t : ContentType.values()) {
                    if (t.shown.equals(name)) {
                        return t;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /** The page's entries as content book lines; items that are not entries (no status line) are skipped. */
    static List<Wyn.ContentLine> lines(List<Wyn.BookSlot> entries) {
        List<Wyn.ContentLine> out = new ArrayList<>();
        for (Wyn.BookSlot s : entries) {
            if (s == null || s.name() == null || s.name().isBlank()) {
                continue;
            }
            String status = status(s.lore());
            if (status != null) {
                out.add(new Wyn.ContentLine(plain(s.name()), status, level(s.lore()), ""));
            }
        }
        return out;
    }

    /**
     * An entry's status from its first lore line ("Already completed", "Currently in progress",
     * "Can be started", "Cannot be started yet"; a prefix before " - " is extra info), in the names
     * Wynntils' content book read uses; null when the line is none of these (not an entry).
     */
    static String status(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        String first = plain(lore.get(0));
        int dash = first.lastIndexOf(" - ");
        String s = dash < 0 ? first : first.substring(dash + 3).trim();
        // a finished cave or similar reads "Can be explored again" (the check mark shows it)
        if (s.startsWith("Already completed") || s.endsWith(" again")) {
            return "COMPLETED";
        }
        if (s.startsWith("Currently ")) {
            return "STARTED";
        }
        if (s.startsWith("Can be ")) {
            return "AVAILABLE";
        }
        if (s.startsWith("Cannot be ")) {
            return "UNAVAILABLE";
        }
        return null;
    }

    /** The combat level the entry asks for or recommends; 0 when none is listed. */
    static int level(List<String> lore) {
        if (lore == null) {
            return 0;
        }
        for (String line : lore) {
            Matcher m = LEVEL.matcher(plain(line));
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 0;
    }

    /** Text without colour codes and the book's private font glyphs, trimmed. */
    static String plain(String s) {
        String p = ChatFormatting.stripFormatting(s);
        if (p == null) {
            return "";
        }
        // the book pads some lines with glyphs of Wynncraft's own font (surrogate pairs, U+00C0)
        return p.replaceAll("[\\uD800-\\uDFFF\\u00C0]", "").trim();
    }
}
