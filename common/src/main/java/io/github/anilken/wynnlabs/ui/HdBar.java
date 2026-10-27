package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The one progress bar: a light rounded track with a faint ring, the fill in its colour up to its
 * share (never thinner than the bar is tall, so a small share still shows). Used for every bar the
 * mod draws on paper: a search, the road map, the XP, a guide's stages, a download.
 */
final class HdBar {

    static final int TRACK = 0xFFE4D6B0;
    static final int RING = 0x55A08A5A;
    /** The fill most bars take: a deep teal that reads on the light track. */
    static final int TEAL = 0xFF2C7A7E;

    private HdBar() {}

    static void draw(GuiGraphics g, int x, int y, int w, int h, double frac, int fill) {
        if (w <= 0 || h <= 0) {
            return;
        }
        Art.box(g, x, y, w, h, h / 2, TRACK, RING);
        double f = Math.max(0, Math.min(1, frac));
        if (f > 0) {
            Art.box(g, x, y, Math.max(h, (int) Math.round(w * f)), h, h / 2, fill, 0);
        }
    }
}
