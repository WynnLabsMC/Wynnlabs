package io.github.anilken.wynnlabs.ui;

/**
 * Where Wynnventory's price box is on the screen this frame, so our item tooltip stays out from
 * under it. Wynnventory works out where the game's own tooltip would stand (its size, at the
 * mouse) and puts the price box beside that; our tooltip is another size and lands elsewhere,
 * so the two could overlap. The mixin on its coordinate maths notes the box (screen GUI units);
 * the tooltip positioner then moves ours to the mouse's other side, or past the box, when they
 * would meet. Nothing drawn by Wynnventory changes.
 */
public final class PriceBox {

    private PriceBox() {}

    /** A note older than this is from an earlier tooltip: ignored. */
    static final long FRESH_NANOS = 120_000_000L;

    private static volatile int[] box;
    private static volatile long notedAt;

    /** Wynnventory's price box this frame: its top-left and size in screen GUI units, frame included. */
    public static void note(int x, int y, int w, int h) {
        box = new int[] {x, y, w, h};
        notedAt = System.nanoTime();
    }

    /** Tests and the screen close: no box. */
    public static void forget() {
        box = null;
    }

    /**
     * Where our tooltip's left edge should be so it does not meet the price box: {@code x} as
     * placed when there is no box, or none this frame, or they do not meet; else the mouse's other
     * side when that fits the screen, else just past the box, else {@code x} after all. All in
     * screen GUI units; {@code sw}/{@code sh} the tooltip's drawn size, {@code edge} the frame
     * around it, {@code gap} the distance from the mouse.
     */
    static float avoid(float x, float y, float sw, float sh, int mouseX, int gap, float edge, int screenW) {
        return avoid(box, System.nanoTime() - notedAt, x, y, sw, sh, mouseX, gap, edge, screenW);
    }

    static float avoid(int[] b, long age, float x, float y, float sw, float sh, int mouseX, int gap, float edge, int screenW) {
        if (b == null || age > FRESH_NANOS || !hits(b, x - edge, y - edge, sw + 2 * edge, sh + 2 * edge)) {
            return x;
        }
        float right = mouseX + gap;
        float left = mouseX - gap - sw;
        float other = Math.abs(x - right) <= Math.abs(x - left) ? left : right;
        float beyond = b[0] + b[2] + gap;
        float before = b[0] - gap - sw;
        for (float c : new float[] {other, beyond, before}) {
            if (c >= edge && c + sw + edge <= screenW && !hits(b, c - edge, y - edge, sw + 2 * edge, sh + 2 * edge)) {
                return c;
            }
        }
        return x;
    }

    private static boolean hits(int[] b, float x, float y, float w, float h) {
        return x < b[0] + b[2] && b[0] < x + w && y < b[1] + b[3] && b[1] < y + h;
    }
}
