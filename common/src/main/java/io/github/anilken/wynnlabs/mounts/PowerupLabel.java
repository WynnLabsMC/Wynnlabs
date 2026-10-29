package io.github.anilken.wynnlabs.mounts;

import io.github.anilken.wynnlabs.model.MountRead;

import java.util.Locale;

/**
 * The text over one powerup for the ridden mount. A stat powerup shows the mount's own name for
 * that stat (a horse's Altitude is "Jump Height") and is dimmed when the stat is maxed (level at
 * or past its limit). Energy Boost shows what it would give, "gain/headroom Energy" (see
 * {@link #ENERGY_BOOST_GAIN}), its name while the pool is unknown or only estimated. Pure.
 */
public final class PowerupLabel {

    /**
     * Energy Boost as "gain/headroom Energy" instead of its name: headroom =
     * round1(30 + 7.5·log10(max(1, Boost level))) − 2 ({@link #NO_BOOST_HEADROOM} without a Boost
     * stat) and gain = min(spent, headroom), spent being the pool's max minus its current; only
     * with the real pool (an estimate shows the name until the item is read). False: just the name.
     */
    public static final boolean ENERGY_BOOST_GAIN = true;
    /** Energy Boost's headroom for a mount without a Boost stat. */
    static final double NO_BOOST_HEADROOM = 50;

    /** An Energy Boost that would not fill its headroom is drawn this much smaller. */
    static final float PARTIAL_SIZE = 0.75f;

    /** A label: its text, colour (RGB), whether it is dimmed and its size (1 = normal). */
    public record Label(String text, int colour, boolean dim, float size) {

        public Label(String text, int colour, boolean dim) {
            this(text, colour, dim, 1f);
        }
    }

    private PowerupLabel() {}

    /**
     * @param mount the ridden mount's last read, null when unknown (then nothing is dimmed)
     * @param pool the energy pool in points (its current maybe estimated), null when unknown
     */
    public static Label of(PowerupTable.Kind kind, MountRead mount, RideEnergy.Shown pool) {
        if (kind.stat() != null) {
            MountRead.Stat s = stat(mount, kind.stat());
            String text = s != null && s.name() != null && !s.name().isBlank() ? s.name() : kind.name();
            return new Label(text, kind.colour(), maxed(s));
        }
        if (kind.energyBoost() && ENERGY_BOOST_GAIN) {
            double[] g = boostGain(mount, pool);
            if (g != null) {
                // less than it could give: smaller, red → yellow → green by how much it would give
                boolean partial = g[0] < g[1];
                return new Label(text(g), partial ? gradient(g[0] / g[1]) : kind.colour(), false,
                        partial ? PARTIAL_SIZE : 1f);
            }
        }
        return new Label(kind.name(), kind.colour(), false);
    }

    /** Whether this stat can rise no further now: its level is at or past its limit. */
    public static boolean maxed(MountRead.Stat s) {
        return s != null && s.limit() > 0 && s.level() >= s.limit();
    }

    /** The ridden mount's line for this stat, null when not read. */
    static MountRead.Stat stat(MountRead mount, MountStat stat) {
        if (mount == null || mount.stats() == null) {
            return null;
        }
        for (MountRead.Stat s : mount.stats()) {
            MountStat t = MountStat.parse(s.id());
            if (t == null) {
                t = MountStat.parse(s.name());
            }
            if (t == stat) {
                return s;
            }
        }
        return null;
    }

    /** Whether this Energy Boost label waits for the real pool (it is only estimated now). */
    public static boolean needsPool(PowerupTable.Kind kind, RideEnergy.Shown pool) {
        return ENERGY_BOOST_GAIN && kind.energyBoost() && pool != null && pool.approx();
    }

    /** What Energy Boost would give ("12.5/28.3 Energy"), null without the real pool. */
    static String energyGain(MountRead mount, RideEnergy.Shown pool) {
        double[] g = boostGain(mount, pool);
        return g == null ? null : text(g);
    }

    /** Energy Boost's {gain, headroom} from the real pool, null without it. */
    static double[] boostGain(MountRead mount, RideEnergy.Shown pool) {
        if (pool == null || pool.approx() || pool.max() <= 0) {
            return null;
        }
        MountRead.Stat boost = stat(mount, MountStat.BOOST);
        double headroom = boost == null ? NO_BOOST_HEADROOM
                : Math.round((30 + 7.5 * Math.log10(Math.max(1, boost.level()))) * 10) / 10.0 - 2;
        double spent = Math.max(0, pool.max() - pool.current());
        return new double[] {Math.min(spent, headroom), headroom};
    }

    private static String text(double[] g) {
        return num(g[0]) + "/" + num(g[1]) + " Energy";
    }

    /** Red at 0, yellow at 0.5, green at 1 (clamped), as RGB. */
    static int gradient(double t) {
        double f = Math.max(0, Math.min(1, t));
        int r = f <= 0.5 ? 255 : (int) Math.round(255 * (1 - f) * 2);
        int g = f >= 0.5 ? 255 : (int) Math.round(255 * f * 2);
        return r << 16 | g << 8 | 0x40;
    }

    private static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
