package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.game.Wyn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which caves on Wynntils' map are done, so the map can draw them red and dim and the ones left
 * stand out. The decision only: the content book's cave lines in, the set of finished names out,
 * and whether one map label is among them. Wynntils' map is told by {@code Wyn.caveMapSync()}.
 * A cave finished just now is known from chat ({@link #onChat}: the "[Cave Completed]" banner
 * and the cave's name), so its icon fades at once, before the content book is read again.
 */
public final class CaveMap {

    /** The setting that turns the fading on (on by default). */
    public static final String SETTING = "caveMapDone";

    /** Wynntils' map category of every cave entrance. */
    public static final String CATEGORY = ContentType.CAVE.feature;

    /**
     * The tint of a finished cave's icon, ARGB: white, drawn faded (see the map's own fade), so a
     * finished cave stands apart from the untouched ones.
     */
    public static final int ICON_ARGB = 0xB0_FF_FF_FF;

    /** The colour of a finished cave's name under the icon, ARGB: white, faded like the icon. */
    public static final int LABEL_ARGB = 0xB0_FF_FF_FF;

    private CaveMap() {}

    /** The completion banner: "[Cave Completed]", "[Cave Complete]", and the game's Turkish translation. */
    private static final Pattern BANNER = Pattern.compile(
            "\\[\\s*cave\\s+complete(?:d)?\\s*]|\\[[^\\]]*mağara tamamlandı\\s*]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** A reward line under the banner ("- Rewards:", "+300 Experience Points"): not the name. */
    private static final Pattern REWARD = Pattern.compile("^[-+•]|rewards?\\s*:", Pattern.CASE_INSENSITIVE);
    /** How far apart the banner and the cave's name may come. */
    private static final long NAME_WINDOW = 3000;

    /** The caves finished this session, by chat, in the same keys as {@link #doneNames}. */
    private static final Set<String> doneNow = new HashSet<>();
    private static long bannerAt;
    /** What happens when chat marks a cave done: the book copy updated and the map told; tests put nothing here. */
    static volatile java.util.function.Consumer<String> onDone = CaveMap::finished;

    /**
     * Chat: the completion banner and the cave's name, in one message with the rewards or as
     * separate messages. The cave is marked done at once, so its map icon fades without waiting
     * for the content book to be opened again; the next book read stays the source.
     */
    public static void onChat(net.minecraft.network.chat.Component message, boolean overlay) {
        if (overlay || message == null) {
            return;
        }
        try {
            onText(message.getString(), System.currentTimeMillis());
        } catch (Throwable t) {
            System.out.println("[WynnLabs] cave chat: " + t);
        }
    }

    /** The lines of one message, cleaned of colour codes; the first name after a banner counts. */
    static void onText(String raw, long now) {
        if (raw == null) {
            return;
        }
        for (String part : raw.split("\\R")) {
            String line = ItemJson.clean(part).trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher b = BANNER.matcher(line);
            if (b.find()) {
                String rest = (line.substring(0, b.start()) + " " + line.substring(b.end())).trim();
                if (!rest.isEmpty() && !REWARD.matcher(rest).find()) {
                    done(rest);
                    bannerAt = 0;
                } else {
                    bannerAt = now;
                }
            } else if (bannerAt != 0 && now - bannerAt <= NAME_WINDOW && !REWARD.matcher(line).find()) {
                done(line);
                bannerAt = 0;
            }
        }
    }

    private static void done(String name) {
        String k = key(name);
        synchronized (doneNow) {
            if (!doneNow.add(k)) {
                return;
            }
        }
        System.out.println("[WynnLabs] cave completed from chat: " + name);
        onDone.accept(name);
    }

    /** In the game: the book copy's line for the cave goes COMPLETED (saved, and the map told), else the map is told alone. */
    private static void finished(String name) {
        String k = key(name);
        List<Wyn.ContentLine> lines = new ArrayList<>();
        for (Wyn.ContentLine c : Content.bookLines(ContentType.CAVE)) {
            if (c != null && c.name() != null && key(c.name()).equals(k) && !"COMPLETED".equals(c.status())) {
                lines.add(new Wyn.ContentLine(c.name(), "COMPLETED", c.level(), c.description()));
            }
        }
        if (!lines.isEmpty()) {
            Content.tookPage(ContentType.CAVE, lines);     // tells the map too
        } else {
            Wyn.caveMapSync();
        }
    }

    /** The caves chat marked done this session (keys). */
    static Set<String> doneNow() {
        synchronized (doneNow) {
            return new HashSet<>(doneNow);
        }
    }

    /** Tests: no cave finished and no chat seen. */
    static void reset() {
        synchronized (doneNow) {
            doneNow.clear();
        }
        bannerAt = 0;
    }

    /** The names (lower-cased, trimmed) of the caves whose content book status is COMPLETED. */
    public static Set<String> doneNames(List<Wyn.ContentLine> caves) {
        Set<String> out = new HashSet<>();
        for (Wyn.ContentLine c : caves) {
            if (c != null && "COMPLETED".equals(c.status()) && c.name() != null) {
                out.add(key(c.name()));
            }
        }
        return out;
    }

    /**
     * The finished caves from both places the mod knows them: the character's own content book
     * copy ({@code Content.bookLines}, filled by the Quests tab's refresh and kept on disk) and
     * what Wynntils' cave model holds live (often empty: Wynntils fills it only on its own read),
     * and the ones chat said were finished this session. A cave finished in any is done.
     */
    public static Set<String> doneNames(List<Wyn.ContentLine> book, List<Wyn.ContentLine> live) {
        Set<String> out = doneNames(book);
        out.addAll(doneNames(live));
        out.addAll(doneNow());
        return out;
    }

    /** Whether the map feature with this label is a finished cave; false for no label. */
    public static boolean isDone(String label, Set<String> done) {
        return label != null && !done.isEmpty() && done.contains(key(label));
    }

    /** The map label and the content book name are the same cave when they match ignoring case and spacing. */
    static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
