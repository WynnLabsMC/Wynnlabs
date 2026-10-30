package io.github.anilken.wynnlabs.mounts;

import io.github.anilken.wynnlabs.model.MountRead;

import java.util.List;
import java.util.Locale;

/**
 * What the mount panel shows while riding, worked out from whatever is known, so it shows
 * something even when the mount's item did not read: the header (the mount's skin or name, and
 * its type from the read, Wynntils or the vehicle), the energy row (the item's points, an estimate
 * between reads, else Wynntils' bar as a percentage) and the stats, or {@link #NOT_READ} instead
 * of them; the game's free mount ({@link MountRead#free}) has neither. Pure.
 */
public record RideView(MountRead mount, String title, String kind, String energy, float fill, List<MountRead.Stat> stats,
                       boolean maxKnown, String note) {

    /** The note's language key when no stats were read. */
    public static final String NOT_READ = "wynnlabs.ride.not_read";

    /**
     * @param m the mount's read, null when its item did not read
     * @param wynntilsType Wynntils' current mount type ("WYVERN"; null, "none" or "no model" when unknown)
     * @param vehicle the vehicle's type ("entity.minecraft.llama"), null when none
     * @param live Wynntils' live energy {current, max}, null when unknown
     * @param frameAtRead the live frame when the item was read
     */
    public static RideView of(MountRead m, String wynntilsType, String vehicle, int[] live, int frameAtRead) {
        String type = m != null && !m.type().isEmpty() ? m.type() : known(wynntilsType) ? wynntilsType : null;
        String kind = type != null ? cap(type) : vehicle != null && !vehicle.isBlank() ? cap(last(vehicle)) : "Mount";
        String title;
        if (m != null) {
            title = m.skin() != null && !m.skin().isBlank() ? m.skin() : m.name();
        } else {
            title = kind;
            kind = "";
        }
        String energy;
        float fill;
        if (m != null && m.energyMax() > 0) {
            RideEnergy.Shown e = RideEnergy.shown(m.energy(), m.energyMax(), live, frameAtRead);
            energy = (e.approx() ? "~" : "") + e.current() + "/" + e.max();
            fill = Math.min(1f, (float) e.current() / e.max());
        } else if (live != null && live.length > 1 && live[1] > 0) {
            int pct = Math.round(100f * Math.max(0, Math.min(live[0], live[1])) / live[1]);
            energy = pct + "%";
            fill = pct / 100f;
        } else {
            energy = "–";
            fill = 0;
        }
        if (m != null && m.free()) {
            // the game's free mount: every stat is 1/1 for good, so only its name and energy show
            return new RideView(m, title, kind, energy, fill, List.of(), false, null);
        }
        List<MountRead.Stat> stats = m == null ? List.of() : m.stats();
        return new RideView(m, title, kind, energy, fill, stats, m != null && !m.estimated(), stats.isEmpty() ? NOT_READ : null);
    }

    private static boolean known(String t) {
        return t != null && !t.isBlank() && !t.equals("none") && !t.equals("no model") && !t.startsWith("error");
    }

    private static String last(String id) {
        int dot = Math.max(id.lastIndexOf('.'), id.lastIndexOf(':'));
        return dot < 0 ? id : id.substring(dot + 1);
    }

    private static String cap(String s) {
        String t = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
