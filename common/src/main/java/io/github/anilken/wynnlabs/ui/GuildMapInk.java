package io.github.anilken.wynnlabs.ui;

/**
 * The guild map's dark palette: the card and the side panel sit on the map like the territory
 * pages players know from the web, light text on near-black, so the map around them stays the
 * brightest thing on screen. Names follow {@link Theme}'s, so a part reads the same in both.
 */
final class GuildMapInk {

    private GuildMapInk() {}

    static final int FACE = 0xFF121418;
    static final int RIM = 0xFF2C2F36;
    static final int INK = 0xFFE8E8E8;
    static final int INK_2 = 0xFFB4B8BF;
    static final int INK_3 = 0xFF8A8F98;
    static final int PRICE = 0xFFF2C24A;
    static final int GOOD = 0xFF8FE39A;
    static final int BAD = 0xFFFF8A8A;
    static final int LINE = 0xFF2C2F36;
    static final int WELL = 0xFF24272D;
    static final int CARD = 0xFF1A1D22;
    static final int CARD_HOVER = 0x20FFFFFF;
    static final int SELECTED = 0xFF2A2516;
    static final int GOLD_EDGE = 0xFFC6A23A;

    /** The card's and the panel's face: near-black with a one-pixel rim. */
    static void face(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, FACE);
        g.renderOutline(x, y, w, h, RIM);
    }

    /** A pill, a tile or a box: a flat face with a one-pixel ring. */
    static void box(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h, int fill, int ring) {
        g.fill(x, y, x + w, y + h, fill);
        if (ring != 0) {
            g.renderOutline(x, y, w, h, ring);
        }
    }
}
