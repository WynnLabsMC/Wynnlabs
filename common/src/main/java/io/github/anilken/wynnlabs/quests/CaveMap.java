package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.game.Wyn;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which caves on Wynntils' map are done, so the map can draw them red and dim and the ones left
 * stand out. The decision only: the content book's cave lines in, the set of finished names out,
 * and whether one map label is among them. Wynntils' map is told by {@code Wyn.caveMapSync()}.
 */
public final class CaveMap {

    /** The setting that turns the fading on (on by default). */
    public static final String SETTING = "caveMapDone";

    /** Wynntils' map category of every cave entrance. */
    public static final String CATEGORY = "wynntils:content:cave";

    /**
     * The tint of a finished cave's icon, ARGB: a dark red, so the icon reads as dimmed as well as
     * red. The map scales a colour's alpha by zoom itself, so the darkness is what fades it.
     */
    public static final int ICON_ARGB = 0xB0_A8_28_28;

    /** The colour of a finished cave's name under the icon, ARGB: a lighter red that stays readable. */
    public static final int LABEL_ARGB = 0xB0_D8_60_60;

    private CaveMap() {}

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

    /** Whether the map feature with this label is a finished cave; false for no label. */
    public static boolean isDone(String label, Set<String> done) {
        return label != null && !done.isEmpty() && done.contains(key(label));
    }

    /** The map label and the content book name are the same cave when they match ignoring case and spacing. */
    static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
