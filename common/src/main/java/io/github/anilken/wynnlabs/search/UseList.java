package io.github.anilken.wynnlabs.search;

import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * The uses of an item as the lines of a list, in the order shown: the crafts it goes into, the
 * professions an ingredient is crafted with, then the other uses. Each line has its list key
 * ({@link UseKey}); a craft's also has the level bracket shown for the player and how many crafts
 * their stock allows. Plain data: the Search tab turns a line into words.
 */
public final class UseList {

    public enum Kind { CRAFT, PROFESSION, FEED }

    /**
     * One line. {@code craft}, {@code bracket} and {@code crafts} belong to a {@link Kind#CRAFT}
     * line, {@code profession} to a craft's and a profession's, {@code feed} to a mount feed's;
     * the others are null (0).
     */
    public record Line(Kind kind, String key, Uses.Craft craft, Uses.Bracket bracket, String profession, Uses.MountFeed feed,
                       long crafts) {}

    private UseList() {}

    /**
     * The lines of {@code uses}. {@code have}: how many of a material the player holds, by its
     * shown name; {@code level}: their level in a profession ("jeweling"), 0 when not known.
     */
    public static List<Line> of(Uses.Result uses, ToLongFunction<String> have, ToIntFunction<String> level) {
        List<Line> out = new ArrayList<>();
        long own = uses.crafts().isEmpty() ? 0 : have.applyAsLong(uses.name());
        for (Uses.Craft c : uses.crafts()) {
            Uses.Bracket b = bracket(c, level.applyAsInt(c.profession()));
            long other = b.other() == null ? 0 : have.applyAsLong(b.other());
            out.add(new Line(Kind.CRAFT, UseKey.of(uses.name(), c.type()), c, b, c.profession(), null,
                    Uses.craftsWith(own, other, b)));
        }
        for (String p : uses.ingredientProfessions()) {
            out.add(new Line(Kind.PROFESSION, UseKey.of(uses.name(), UseKey.PROFESSION + p), null, null, p, null, 0));
        }
        for (Uses.Other o : uses.other()) {
            if (o instanceof Uses.MountFeed f) {
                out.add(new Line(Kind.FEED, UseKey.of(uses.name(), UseKey.FEED), null, null, null, f, 0));
            }
        }
        return out;
    }

    /** The line with this list key, or null. */
    public static Line find(List<Line> lines, String key) {
        for (Line l : lines) {
            if (l.key().equals(key)) {
                return l;
            }
        }
        return null;
    }

    /** How many lines are of this kind. */
    public static int count(List<Line> lines, Kind kind) {
        int n = 0;
        for (Line l : lines) {
            n += l.kind() == kind ? 1 : 0;
        }
        return n;
    }

    /**
     * The bracket of a craft shown for a profession level: the highest one that level reaches, the
     * lowest when the level is not known (0) or below them all.
     */
    public static Uses.Bracket bracket(Uses.Craft c, int level) {
        Uses.Bracket best = c.brackets().get(0);
        for (Uses.Bracket b : c.brackets()) {
            if (level > 0 && b.minLevel() <= level) {
                best = b;
            }
        }
        return best;
    }

    /** Whether the bracket shown for this level is the player's own (their level reaches it), not the lowest by default. */
    public static boolean reached(Uses.Bracket b, int level) {
        return level > 0 && b.minLevel() <= level;
    }

    /** The item types a profession crafts ("armouring": helmet, chestplate), in the Crafting tab's order. */
    public static List<String> types(String profession, List<CraftData.Recipe> recipes) {
        List<String> out = new ArrayList<>();
        for (String t : Types.ALL) {
            for (CraftData.Recipe r : recipes) {
                if (r.type().equals(t) && r.profession().equalsIgnoreCase(profession)) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }
}
