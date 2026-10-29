package io.github.anilken.wynnlabs.mounts;

import static io.github.anilken.wynnlabs.mounts.MountStat.*;

import java.util.List;

/**
 * The eight material families and the stats each feeds, first stat gets the most points. The declaration order is
 * the planner's fixed order (it decides ties between equal plans).
 * Two-stat kinds give 8+0.2L and 4+0.06L, three-stat kinds 6+0.15L, 4+0.08L and 2+0.03L (half up).
 */
public enum MaterialKind {
    INGOT("Ingot", TOUGHNESS, ENERGY),
    PAPER("Paper", ALTITUDE, BOOST),
    GRAIN("Grain", SPEED, ALTITUDE),
    MEAT("Meat", ENERGY, ACCELERATION),
    GEM("Gem", TRAINING, SPEED, ENERGY),
    WOOD("Wood", ACCELERATION, TOUGHNESS, SPEED),
    STRING("String", BOOST, HANDLING, ACCELERATION),
    OIL("Oil", HANDLING, TRAINING, ALTITUDE);

    private static final int[][] TWO = {{800, 20}, {400, 6}};
    private static final int[][] THREE = {{600, 15}, {400, 8}, {200, 3}};

    private final String display;
    private final List<MountStat> stats;

    MaterialKind(String display, MountStat... stats) {
        this.display = display;
        this.stats = List.of(stats);
    }

    public String display() {
        return display;
    }

    public List<MountStat> stats() {
        return stats;
    }

    /** Points per fed stat (same order as {@link #stats()}) for a material of this level, before the max cap. */
    public int[] points(int level) {
        int[][] c = stats.size() == 2 ? TWO : THREE;
        int[] out = new int[stats.size()];
        for (int j = 0; j < out.length; j++) {
            out[j] = MountRules.roundPoints(c[j][0], c[j][1], level);
        }
        return out;
    }

    public static MaterialKind parse(String name) {
        for (MaterialKind k : values()) {
            if (k.display.equalsIgnoreCase(name) || k.name().equalsIgnoreCase(name)) {
                return k;
            }
        }
        return null;
    }
}
