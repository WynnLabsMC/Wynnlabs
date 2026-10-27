package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A material's tier as one chip: a star and the tier number with a caret, opening a menu of the
 * three tiers. The star, the number and the caret are one group, centred in the chip both ways:
 * the 8px star 3px from the top of the 14px chip (a whole pixel), the number's capitals on the
 * chip's middle, and the group's start rounded to a whole pixel.
 */
final class UiTierSeal {

    static final int H = Ui.CHIP_H;
    static final int STAR = 8;
    static final int CARET = 6;

    private UiTierSeal() {}

    /** The chip's width for a number {@code numW} wide. */
    static int width(int numW) {
        return 2 * Theme.SP_3 + group(numW);
    }

    /** The star, the number and the caret, with SP_1 between them. */
    static int group(int numW) {
        return STAR + Theme.SP_1 + numW + Theme.SP_1 + CARET;
    }

    /** Where the group starts in a chip at x, {@code w} wide (pure, tested). */
    static int groupX(int x, int w, int numW) {
        return x + Math.round((w - group(numW)) / 2f);
    }

    /** The star's top in a chip at y: centred, a whole pixel since chip and star are both even (pure, tested). */
    static int starY(int y) {
        return y + (H - STAR) / 2;
    }

    /** Draws it; returns its width. {@code open} while its menu is open. */
    static int draw(GuiGraphics g, Font font, int tier, int x, int y, boolean open, boolean off, int mx, int my) {
        String n = String.valueOf(tier);
        int nw = Type.LABEL.width(font, n);
        int w = width(nw);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + H;
        Kit.pill(g, x, y, w, H, open, hover);
        int gx = groupX(x, w, nw);
        Art.icon(g, "star-filled", gx, starY(y), STAR, off ? Theme.INK_3 : Theme.GOLD_DEEP);
        int nx = gx + STAR + Theme.SP_1;
        int ty = Type.LABEL.yIn(y, H);
        Type.LABEL.draw(g, font, n, nx, ty, off ? Theme.INK_3 : Theme.INK);
        Art.icon(g, "chevron-down", nx + nw + Theme.SP_1, Type.LABEL.onCaps(ty, CARET), CARET, Theme.INK_3);
        return w;
    }
}
