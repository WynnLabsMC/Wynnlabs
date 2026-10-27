package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * What the mod draws over the game itself, in two shared shapes. A panel: a semi-transparent dark
 * rounded card in a corner with a faint cream ring and a 2 px accent on the left, a bold title and
 * up to two small lines (the DPS box, the coach's hunt). A notice: one centred pill with an icon
 * in its accent and cream text (a tracked mob nearby in gold, the death warning in red). Callers
 * hand in text they made when it changed; nothing here builds strings.
 */
public final class Hud {

    public static final int PANEL = 0xD8141210;
    public static final int RING = 0x55F4E9C8;
    public static final int CREAM = 0xFFF4E9C8;
    public static final int DIM = 0xFFB9AE96;
    public static final int GOLD = 0xFFF0C24A;
    public static final int RED = 0xFFE0604A;
    public static final int MUTED = 0xFF8C8578;

    private static final int PAD = Theme.SP_3;

    private Hud() {}

    /** The font the HUD draws with: the menu's (Noto Sans with the web font, else the game's). */
    public static Font font() {
        return UiFont.get();
    }

    /** The panel's width for these texts (worked out by the caller when the texts change). */
    public static int panelWidth(Font font, String title, String line1, String line2) {
        int w = Type.LABEL.width(font, title);
        if (line1 != null) {
            w = Math.max(w, Type.META.width(font, line1));
        }
        if (line2 != null) {
            w = Math.max(w, Type.META.width(font, line2));
        }
        return w + 2 + 2 * PAD;
    }

    /** The panel's height: the title and the lines given, with PAD around. */
    public static int panelHeight(boolean line1, boolean line2) {
        return 2 * PAD + Type.LABEL.lineHeight + (line1 ? Type.META.lineHeight : 0) + (line2 ? Type.META.lineHeight : 0);
    }

    /** A panel at x, y, {@code w} wide: the title in {@code titleInk}, the lines dim; null lines are left out. */
    public static void panel(GuiGraphics g, Font font, int x, int y, int w, String title, int titleInk, String line1, String line2,
                             int accent) {
        int h = panelHeight(line1 != null, line2 != null);
        Art.box(g, x, y, w, h, 4, PANEL, RING);
        Art.box(g, x, y + PAD / 2, 2, h - PAD, 1, accent, 0);
        int tx = x + 2 + PAD;
        int ty = y + PAD;
        Type.LABEL.draw(g, font, title, tx, ty, titleInk);
        ty += Type.LABEL.lineHeight;
        if (line1 != null) {
            Type.META.draw(g, font, line1, tx, ty, DIM);
            ty += Type.META.lineHeight;
        }
        if (line2 != null) {
            Type.META.draw(g, font, line2, tx, ty, DIM);
        }
    }

    /** The notice's width for its text (worked out by the caller when the text changes). */
    public static int noticeWidth(Font font, String text) {
        return 2 * PAD + 8 + Theme.SP_2 + Type.BODY.width(font, text);
    }

    /** A notice pill centred on {@code centreX} at {@code y}: the icon in the accent, the text cream. */
    public static void notice(GuiGraphics g, Font font, int centreX, int y, int w, String icon, String text, int accent) {
        int h = Ui.CHIP_H + Theme.SP_1;
        int x = centreX - w / 2;
        Art.box(g, x, y, w, h, h / 2, PANEL, accent);
        Art.icon(g, icon, x + PAD, y + Ui.iconIn(h, 8), 8, accent);
        Type.BODY.draw(g, font, text, x + PAD + 8 + Theme.SP_2, Type.BODY.yIn(y, h), CREAM);
    }
}
