package io.github.anilken.wynnlabs.build;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a reference build is for, so the benchmarks judge it with the goal its players chose:
 * HEALER for heal builds, TANK for tank builds, DAMAGE for the rest. Read from the build's own
 * words first (a Discord note, the guide's text around the link), then its stats (Healing
 * Efficiency) and ability tree (the heals it takes, the archetype), then how much health it
 * keeps against the level's floor when scored for damage.
 */
final class BuildRoles {

    private BuildRoles() {}

    /** The goal ({@link Setup#GOALS}) and why, for the report ("text: healer", "he 45", ...). */
    record Call(String goal, String why) {}

    private static final Pattern HEALER_TEXT = Pattern.compile("(?i)\\b(healers?|support|heal(ing)? (build|mage|shaman)|healbot|pure heal)\\b");
    /** "tank", "tanky", but not the verb ("it does tank the build's potential"). */
    private static final Pattern TANK_TEXT = Pattern.compile("(?i)\\b(tank|tanks|tanky|tanking)\\b(?! the\\b)");
    /** WynnBuilder's copied item list ("Wynnbuilder build:", a name a line): item names ("Planet Healer") say nothing about the role. */
    private static final Pattern ITEM_LIST = Pattern.compile("(?is)wynnbuilder build:.*?(\\n\\s*\\n|$)");
    /** The archetypes whose tree is built around healing the party. */
    private static final Set<String> HEAL_ARCHETYPES = Set.of("Light Bender", "Acolyte");
    /** The archetypes whose tree is built around soaking damage. */
    private static final Set<String> TANK_ARCHETYPES = Set.of("Paladin");

    /** Healing Efficiency (%) at which any build is a heal build, and the lower bar for a heal archetype. */
    static final double HE_HEALER = 40;
    static final double HE_HEAL_ARCHETYPE = 15;
    /** Heal nodes the link's tree takes (Heal itself counts) at which a heal archetype is a heal build. */
    static final int HEALS_HEAL_ARCHETYPE = 4;
    /** Elemental EHP, as a multiple of the level's floor, at which a tank archetype (else any build) is a tank. */
    static final double EHP_TANK_ARCHETYPE = 2.5;
    static final double EHP_TANK = 4.0;

    /**
     * The role of a build of {@code archetype} ("" for none) described by {@code text} (null for
     * none), wearing {@code pieces}, whose tree takes {@code heals} heal nodes (-1 when the link
     * has no tree), with elemental EHP {@code ehpX} times the floor when scored for damage (NaN
     * when it was not scored).
     */
    static Call of(String archetype, String text, Collection<Damage.Piece> pieces, int heals, double ehpX) {
        String t = text == null ? "" : ITEM_LIST.matcher(text).replaceAll("\n");
        if (HEALER_TEXT.matcher(t).find()) {
            return new Call("HEALER", "text");
        }
        if (TANK_TEXT.matcher(t).find()) {
            return new Call("TANK", "text");
        }
        double he = healingEfficiency(pieces);
        if (he >= HE_HEALER) {
            return new Call("HEALER", "healing efficiency");
        }
        boolean healArch = HEAL_ARCHETYPES.contains(archetype);
        if (healArch && he >= HE_HEAL_ARCHETYPE) {
            return new Call("HEALER", "heal archetype, healing efficiency");
        }
        if (healArch && heals >= HEALS_HEAL_ARCHETYPE) {
            return new Call("HEALER", "heal archetype, heal nodes");
        }
        if (!Double.isNaN(ehpX) && ehpX >= (TANK_ARCHETYPES.contains(archetype) ? EHP_TANK_ARCHETYPE : EHP_TANK)) {
            return new Call("TANK", TANK_ARCHETYPES.contains(archetype) ? "tank archetype, health" : "health");
        }
        return new Call("DAMAGE", "");
    }

    /** The pieces' Healing Efficiency IDs added up (%). */
    static double healingEfficiency(Collection<Damage.Piece> pieces) {
        double he = 0;
        for (Damage.Piece p : pieces) {
            if (p != null) {
                he += p.stats().getOrDefault("healingEfficiency", 0.0);
            }
        }
        return he;
    }

    /** The heal nodes a tree overlay takes; -1 for none. */
    static int heals(TreeModel.Overlay o) {
        return o == null || o.heals == null ? -1 : o.heals.size();
    }

    /** Elemental EHP as a multiple of the level's floor; NaN when there is no floor or no result. */
    static double ehpX(Damage.Result r, int level) {
        double floor = Damage.ehpFloor(level);
        return r == null || floor <= 0 ? Double.NaN : r.ehpElemental() / floor;
    }
}
