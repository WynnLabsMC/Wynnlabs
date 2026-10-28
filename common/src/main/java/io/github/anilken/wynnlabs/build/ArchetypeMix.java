package io.github.anilken.wynnlabs.build;

import java.util.ArrayList;
import java.util.List;

/**
 * The archetype a character plays: one ("Summoner"), none ("": automatic), or two combined with a
 * split ("Summoner+Ritualist" half each, "Summoner+Ritualist@70" seven tenths the first). The tree
 * planner and the class profile take it as their archetype text ({@link #spec}), so a choice of
 * one archetype is the same text it always was.
 *
 * @param first  the first archetype, "" for automatic
 * @param second the second, "" for none
 * @param split  the first's share in percent (50 for half each; 100 with no second)
 */
public record ArchetypeMix(String first, String second, int split) {

    /** The splits the menu offers, the first's share in percent. */
    public static final int[] SPLITS = {70, 50, 30};

    public ArchetypeMix {
        first = first == null ? "" : first.strip();
        second = second == null ? "" : second.strip();
        if (first.isEmpty() || second.equalsIgnoreCase(first)) {
            second = "";
        }
        split = second.isEmpty() ? 100 : Math.max(10, Math.min(90, split));
    }

    /** One archetype ("" for automatic). */
    public static ArchetypeMix of(String archetype) {
        return new ArchetypeMix(archetype, "", 100);
    }

    /** Two combined, the first's share {@code split} percent. */
    public static ArchetypeMix of(String first, String second, int split) {
        return new ArchetypeMix(first, second, split);
    }

    /** From its text: "Summoner", "Summoner+Ritualist", "Summoner+Ritualist@70", "" (null: ""). */
    public static ArchetypeMix parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return of("");
        }
        String s = spec.strip();
        int split = 50;
        int at = s.indexOf('@');
        if (at >= 0) {
            try {
                split = Integer.parseInt(s.substring(at + 1).strip());
            } catch (NumberFormatException e) {
                split = 50;
            }
            s = s.substring(0, at);
        }
        int plus = s.indexOf('+');
        if (plus < 0) {
            return of(s);
        }
        return new ArchetypeMix(s.substring(0, plus), s.substring(plus + 1), split);
    }

    /** The text the planner and the profile take (see {@link #parse}). */
    public String spec() {
        if (!hybrid()) {
            return first;
        }
        return first + "+" + second + (split == 50 ? "" : "@" + split);
    }

    /** Two archetypes combined. */
    public boolean hybrid() {
        return !second.isEmpty();
    }

    /** No archetype: automatic. */
    public boolean auto() {
        return first.isEmpty();
    }

    /** The archetypes in it, the first first (none for automatic). */
    public List<String> members() {
        List<String> out = new ArrayList<>();
        if (!first.isEmpty()) {
            out.add(first);
        }
        if (!second.isEmpty()) {
            out.add(second);
        }
        return out;
    }

    /** Whether {@code archetype} is one of its archetypes. */
    public boolean has(String archetype) {
        return weight(archetype) > 0;
    }

    /** An archetype's share of the whole: 1 for the one of a single choice, the split for two, 0 for others. */
    public double weight(String archetype) {
        if (archetype == null || archetype.isEmpty() || first.isEmpty()) {
            return 0;
        }
        if (archetype.equalsIgnoreCase(first)) {
            return split / 100.0;
        }
        return archetype.equalsIgnoreCase(second) ? (100 - split) / 100.0 : 0;
    }

    /** The archetype with the larger share (the first at half each): its ultimate is the one planned. */
    public String lead() {
        return hybrid() && split < 50 ? second : first;
    }

    /**
     * How strongly the planner pulls to an archetype's nodes: its share over the lead's (the lead
     * 1, the other of 70/30 3/7, any other 0). For a single choice exactly as before: 1 for its
     * own, and for automatic 1 for the nodes of no archetype.
     */
    public double affinity(String archetype) {
        String a = archetype == null ? "" : archetype;
        if (!hybrid()) {
            return a.equalsIgnoreCase(first) ? 1 : 0;
        }
        double lead = weight(lead());
        return lead <= 0 ? 0 : weight(a) / lead;
    }

    /** As shown: "Summoner", "Summoner + Ritualist", "Summoner + Ritualist 70/30"; "" for automatic. */
    public String label() {
        if (!hybrid()) {
            return first;
        }
        return first + " + " + second + (split == 50 ? "" : " " + split + "/" + (100 - split));
    }

    /**
     * The pairs a class's archetypes make, half each: the hybrids players are known to run first
     * ({@link #KNOWN}, when the tree has both), then every other pair in the tree's order.
     */
    public static List<ArchetypeMix> pairs(List<String> archetypes) {
        List<ArchetypeMix> out = new ArrayList<>();
        for (String[] k : KNOWN) {
            String a = find(archetypes, k[0]);
            String b = find(archetypes, k[1]);
            if (a != null && b != null) {
                out.add(of(a, b, 50));
            }
        }
        for (int i = 0; i < archetypes.size(); i++) {
            for (int j = i + 1; j < archetypes.size(); j++) {
                ArchetypeMix m = of(archetypes.get(i), archetypes.get(j), 50);
                if (out.stream().noneMatch(m::samePair)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** Hybrids players run, one or two a class, the one with more nodes first. */
    private static final String[][] KNOWN = {
        {"Summoner", "Ritualist"}, {"Ritualist", "Acolyte"},
        {"Fallen", "Battle Monk"}, {"Paladin", "Fallen"},
        {"Riftwalker", "Light Bender"}, {"Arcanist", "Riftwalker"},
        {"Boltslinger", "Sharpshooter"}, {"Trapper", "Sharpshooter"},
        {"Shadestepper", "Trickster"}, {"Acrobat", "Shadestepper"},
    };

    private static String find(List<String> archetypes, String name) {
        for (String a : archetypes) {
            if (a.equalsIgnoreCase(name)) {
                return a;
            }
        }
        return null;
    }

    /** Both are two combined of the same two archetypes, in either order and at any split. */
    public boolean samePair(ArchetypeMix o) {
        return o != null && hybrid() && o.hybrid() && has(o.first) && has(o.second);
    }

    /** The same pair in the other order with the split turned round: the same choice. */
    public boolean same(ArchetypeMix o) {
        if (o == null) {
            return false;
        }
        if (!hybrid() || !o.hybrid()) {
            return spec().equalsIgnoreCase(o.spec());
        }
        return weight(o.first) == o.weight(o.first) && weight(o.second) == o.weight(o.second);
    }

    @Override
    public String toString() {
        return auto() ? "auto" : spec();
    }
}
