package io.github.anilken.wynnlabs.mounts;

import java.util.List;

/** The Mount Enclosures (each with its own Mount Feeder): where they are, and the one nearest a spot. */
public final class Enclosures {

    /** An enclosure: its place name, a short name for chips, where it is, and the mounts it keeps. */
    public record Place(String name, String chip, int x, int y, int z, MountType type) {

        public int[] at() {
            return new int[] {x, y, z};
        }
    }

    public static final List<Place> ALL = List.of(
            new Place("Ternaves", "Ternaves", 859, 71, -1663, MountType.HORSE),
            new Place("Bantisu Air Temple", "Bantisu", 497, 107, -4743, MountType.WYVERN),
            new Place("Aldwell", "Aldwell", -1219, 60, -729, MountType.ADASAUR));

    private Enclosures() {}

    /** The enclosure that keeps this kind of mount. */
    public static Place of(MountType type) {
        for (Place p : ALL) {
            if (p.type() == type) {
                return p;
            }
        }
        return null;
    }

    /** The enclosure nearest x, z (the feeder open now is that one's). */
    public static Place nearest(double x, double z) {
        Place best = ALL.get(0);
        double bd = Double.MAX_VALUE;
        for (Place p : ALL) {
            double d = Math.hypot(p.x() - x, p.z() - z);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }
}
