package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One scrolling column of the Mounts tab: its offset, its {@link ScrollBar} (always draggable)
 * and its clip. {@link #begin} opens the clip and gives the content's top and width (the bar's
 * room kept when last frame's content did not fit); {@link #end} takes the content's height,
 * places the bar and closes the clip.
 */
final class MountsScroll {

    private final ScrollBar bar = new ScrollBar();
    private int offset;
    private int contentH;
    private int[] box;

    /** The clip opened over x, y, w, h; returns the y the content starts at (scrolled). */
    int begin(GuiGraphics g, int x, int y, int w, int h) {
        box = new int[] {x, y, w, h};
        offset = Math.max(0, Math.min(offset, Math.max(0, contentH - h)));
        g.enableScissor(x - Theme.SP_2, y, x + w + Theme.SP_2, y + h);
        return y - offset;
    }

    /** The width the content may use: all of it, or less the bar and SP_2. */
    int width(int w) {
        return box != null && contentH > box[3] ? w - ScrollBar.W - Theme.SP_2 : w;
    }

    /** The clip closed, the bar placed and drawn for content {@code h} tall. */
    void end(GuiGraphics g, int h, int mx, int my) {
        g.disableScissor();
        contentH = h;
        bar.place(box[0] + box[2] - ScrollBar.W, box[1], box[3], box[3], contentH, offset);
        bar.draw(g, mx, my);
    }

    /** Not drawn this frame: nothing to click. */
    void hide() {
        bar.place(0, 0, 0, 0, 0, 0);
    }

    /** Whether the content row at {@code y} (screen) is in view. */
    boolean shows(double y) {
        return box != null && y >= box[1] && y < box[1] + box[3];
    }

    boolean press(double x, double y) {
        if (bar.mouseClicked(x, y)) {
            offset = bar.offset();
            return true;
        }
        return false;
    }

    boolean drag(double y) {
        if (bar.mouseDragged(y)) {
            offset = bar.offset();
            return true;
        }
        return false;
    }

    void release() {
        bar.mouseReleased();
    }

    /** The wheel over the column: a line and a half a notch. */
    boolean wheel(double x, double y, double v) {
        if (box == null || !MenuPaint.in(x, y, box)) {
            return false;
        }
        offset = Math.max(0, Math.min(Math.max(0, contentH - box[3]), offset - (int) Math.signum(v) * Type.BODY.lineHeight * 3 / 2));
        return true;
    }

    void top() {
        offset = 0;
    }
}
