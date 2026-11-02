package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The About page's "Shortcuts": each of the mod's keys as a keycap with what it does. The menu,
 * search and box keys show what the player bound them to in Controls (the game's own name for the
 * key, "unbound" when none); the menu's fixed keys (1–9, /, Esc) as they are. Two columns when
 * there is room.
 */
final class AboutKeys {

    /** {binding name or null, the fixed key, what it does}. */
    private static final String[][] KEYS = {
        {"key.wynnlabs.roadmap", null, "wynnlabs.about.key_menu"},
        {"key.wynnlabs.search", null, "wynnlabs.about.key_search"},
        {"key.wynnlabs.box_guide", null, "wynnlabs.about.key_box"},
        {"key.wynnlabs.crowd", null, "wynnlabs.about.key_crowd"},
        {null, "1–9", "wynnlabs.about.key_tabs"},
        {null, "/", "wynnlabs.about.key_find"},
        {null, "Esc", "wynnlabs.about.key_back"}};

    private AboutKeys() {}

    /** The key bound to {@code name} as the game writes it, "unbound" when none, null when no such binding. */
    static String bound(String name) {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (k.getName().equals(name)) {
                return k.isUnbound() ? L.t("wynnlabs.about.unbound") : k.getTranslatedKeyMessage().getString();
            }
        }
        return null;
    }

    /** The section's full height in {@code w}. */
    static int height(Font font, int w) {
        int cols = w >= 420 ? 2 : 1;
        int rows = (KEYS.length + cols - 1) / cols;
        return Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3 + rows * (Ui.CHIP_H + Theme.SP_2) - Theme.SP_2;
    }

    /** Draws the section from {@code y}, rows that would pass {@code bottom} left out; returns the bottom. */
    static int draw(GuiGraphics g, Font font, int x, int y, int w, int bottom) {
        Type.LABEL.draw(g, font, L.t("wynnlabs.about.keys"), x, y);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        int cy = y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
        int cols = w >= 420 ? 2 : 1;
        int colW = w / cols;
        // every keycap as wide as the widest, so the texts start in one column
        int capW = Ui.CHIP_H + 2;
        for (String[] k : KEYS) {
            String cap = k[0] != null ? bound(k[0]) : k[1];
            if (cap != null) {
                capW = Math.max(capW, Type.LABEL.width(font, cap) + 2 * Theme.SP_3);
            }
        }
        int n = 0;
        for (String[] k : KEYS) {
            String cap = k[0] != null ? bound(k[0]) : k[1];
            if (cap == null) {
                continue;
            }
            int kx = x + (n % cols) * colW;
            int ky = cy + (n / cols) * (Ui.CHIP_H + Theme.SP_2);
            if (ky + Ui.CHIP_H > bottom) {
                break;
            }
            boolean unbound = k[0] != null && cap.equals(L.t("wynnlabs.about.unbound"));
            int kw = capW;
            if (unbound) {
                Art.box(g, kx, ky, kw, Ui.CHIP_H, 3, 0, 0x77A08A5A);
                Type.META.draw(g, font, cap, kx + (kw - Type.META.width(font, cap)) / 2, Type.META.yIn(ky, Ui.CHIP_H), Theme.INK_3);
            } else {
                Art.box(g, kx, ky, kw, Ui.CHIP_H, 3, 0xFFF4EAD0, 0x77A08A5A);
                Type.LABEL.draw(g, font, cap, kx + (kw - Type.LABEL.width(font, cap)) / 2, Type.LABEL.yIn(ky, Ui.CHIP_H), Theme.INK);
            }
            String what = L.t(k[2]);
            int tx = kx + kw + Theme.SP_3;
            Type.BODY.draw(g, font, Type.BODY.fit(font, what, kx + colW - Theme.SP_4 - tx), tx, Type.BODY.yIn(ky, Ui.CHIP_H), Theme.INK_2);
            n++;
        }
        return cy + Math.max(1, (n + cols - 1) / cols) * (Ui.CHIP_H + Theme.SP_2) - Theme.SP_2;
    }
}
