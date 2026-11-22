package io.github.anilken.wynnlabs.mounts;

/**
 * The mount panel's energy pool. The item says it in points ("Energy 196/204") but only when it is
 * read; Wynntils reads the action bar live, and when its max is {@link #FRAMES} (48, until
 * Wynntils 4.2.18) or {@link #FRAMES_2_2_6} (47, from 4.2.19: the bar lost a frame to the toughness
 * bar in 2.2.6) that is the energy bar's glyph frame, not points. Then the live current is
 * estimated from the frame (round(frame / frames × the item's max), shown with "~") until the item
 * is read again and the frame has not moved since. Any other max from Wynntils is real points. Pure.
 */
public final class RideEnergy {

    /** The energy bar's frames (glyphs U+E000..U+E02F). */
    public static final int FRAMES = 48;
    /** The energy bar's frames since 2.2.6 (glyphs U+E000..U+E02E), as Wynntils 4.2.19 counts them. */
    public static final int FRAMES_2_2_6 = 47;

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
        int frames = live[1];
        if (frames != FRAMES && frames != FRAMES_2_2_6) {
            return new Shown(live[0], live[1], false);          // real points
        }
        if (itemMax <= 0 || live[0] == frameAtRead) {
            return new Shown(itemCur, itemMax, false);           // the item's own number still holds
        }
        int est = Math.round((float) Math.max(0, Math.min(frames, live[0])) / frames * itemMax);
        return new Shown(est, itemMax, true);
    }
}
