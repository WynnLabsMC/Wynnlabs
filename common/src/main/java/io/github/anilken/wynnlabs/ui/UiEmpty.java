package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * An empty page: centred in its upper third, a large icon, a TITLE, two or
 * three short numbered steps, one action (only when it can act) and, under it, a very faint ghost
 * of what will appear there (outlines, never slots).
 */
final class UiEmpty {

    private UiEmpty() {}

    /** The one action: an icon, its label, and what the caller does with it. */
    record Action(String icon, String label, String id) {}

    /** What will appear: drawn faintly in the room left under the action. */
    interface Ghost {
        void draw(GuiGraphics g, int x, int y, int w, int h);
    }

    static final int ICON_LARGE = 28;
    static final int ICON_SMALL = 22;
    private static final int STEP_DOT = 11;
    private static final int ICON_INK = 0xFF8C7B58;

    /** Draws it in x, y, w, h; returns the action's box {x, y, w, h}, or null without one. */
    static int[] draw(GuiGraphics g, Font font, int x, int y, int w, int h, boolean compact, String icon, String title,
                      List<String> steps, Action action, Ghost ghost, int mx, int my) {
        int cx = x + w / 2;
        int size = compact ? ICON_SMALL : ICON_LARGE;
        int cy = y + (compact ? Theme.SP_6 : h / 10);
        Art.icon(g, icon, cx - size / 2, cy, size, ICON_INK);
        cy += size + Theme.SP_4;
        String t = Type.TITLE.fit(font, title, w - 2 * Theme.SP_4);
        Type.TITLE.draw(g, font, t, cx - Type.TITLE.width(font, t) / 2, cy, Theme.INK);
        cy += Type.TITLE.lineHeight + Theme.SP_4;
        // the steps as one block, centred, each with its number in a small ring
        int textW = 0;
        for (String s : steps) {
            textW = Math.max(textW, Type.BODY.width(font, s));
        }
        int room = w - 2 * Theme.SP_4 - STEP_DOT - Theme.SP_3;
        textW = Math.min(textW, room);
        int sx = cx - (STEP_DOT + Theme.SP_3 + textW) / 2;
        for (int i = 0; i < steps.size(); i++) {
            int dy = Type.BODY.onCaps(cy, STEP_DOT);
            Kit.pill(g, sx, dy, STEP_DOT, STEP_DOT, false, false);
            String n = String.valueOf(i + 1);
            Type.META.draw(g, font, n, sx + (STEP_DOT - Type.META.width(font, n) + 1) / 2, Type.META.yIn(dy, STEP_DOT), Theme.INK_2);
            Type.BODY.draw(g, font, Type.BODY.fit(font, steps.get(i), textW), sx + STEP_DOT + Theme.SP_3, cy, Theme.INK_2);
            cy += Type.BODY.lineHeight + Theme.SP_2;
        }
        int[] box = null;
        if (action != null) {
            cy += Theme.SP_4 - Theme.SP_2;
            String label = Type.LABEL.fit(font, action.label(), w - 2 * Theme.SP_4 - 8 - 2 * Theme.SP_4);
            int bw = Ui.iconButtonLabelWidth(font, label);
            int bx = cx - bw / 2;
            Ui.iconButtonLabel(g, font, action.icon(), label, bx, cy, bw, mx, my);
            box = new int[] {bx, cy, bw, Ui.BUTTON_H};
            cy += Ui.BUTTON_H;
        }
        cy += Theme.SP_7;
        if (ghost != null && y + h - cy > 2 * Theme.SP_7) {
            ghost.draw(g, x, cy, w, y + h - cy);
        }
        return box;
    }
}
