package io.github.anilken.wynnlabs.mounts;

/**
 * The mount panel's energy pool. The item says it in points ("Energy 196/204") but only when it is
 * read; Wynntils reads the action bar live, and when its max is {@link #FRAMES} that is the energy
 * bar's glyph frame, not points. Then the live current is estimated from the frame
 * (round(frame / 48 × the item's max), shown with "~") until the item is read again and the frame
 * has not moved since. Any other max from Wynntils is real points. Pure.
 */
public final class RideEnergy {

    /** The energy bar's frames (glyphs U+E000..U+E02F). */
    public static final int FRAMES = 48;

    private RideEnergy() {}

    /** What the row shows: current, max, and whether current is an estimate. */
    public record Shown(int current, int max, boolean approx) {}

    /**
     * @param itemCur the item's current points, {@code itemMax} its max
     * @param live Wynntils' {current, max}, null when unknown
     * @param frameAtRead the live frame when the item was last read (-1 when unknown)
     */
    public static Shown shown(int itemCur, int itemMax, int[] live, int frameAtRead) {
        if (live == null || live.length < 2 || live[1] <= 0) {
            return new Shown(itemCur, itemMax, false);
        }
        if (live[1] != FRAMES) {
            return new Shown(live[0], live[1], false);          // real points
        }
        if (itemMax <= 0 || live[0] == frameAtRead) {
            return new Shown(itemCur, itemMax, false);           // the item's own number still holds
        }
        int est = Math.round((float) Math.max(0, Math.min(FRAMES, live[0])) / FRAMES * itemMax);
        return new Shown(est, itemMax, true);
    }
}
