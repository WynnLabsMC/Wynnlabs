package io.github.anilken.wynnlabs.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The build window's controls and click areas: its buttons in the mod's sprites, the chosen
 * chips, the click areas drawn this frame, and text cut to a width in the window's font.
 */
final class BuildControls {

    static final int CHOSEN = HomeTab.CHOSEN;

    /** A click area drawn this frame: its box, what it does, and on what. */
    record Btn(int x, int y, int w, int h, String act, Object arg) {}

    Font font;
    final List<Btn> btns = new ArrayList<>();

    /** A chip: a thin pill, gold when on (Kit.pill). */
    static final int WOOD = 0;
    /** The purple primary action (Kit.pillPrimary). */
    static final int PRIMARY = 1;
    /** An icon-only button's width. */
    static final int ICON_W = 16;

    /**
     * One control in the mod's button sprites: {@code text} after the icon when the whole of it
     * fits {@code maxW} comfortably, else the icon alone, centred, with the label as the first
     * tooltip line (the one rule for every control here: text when it fits, never cut). Returns
     * its width. {@code on}: a selected wood control (gold); {@code text} null: always the icon.
     */
    int control(GuiGraphics g, BuildWindow.Host host, int x, int y, int h, String icon, String text, int kind, boolean on, int maxW,
            String act, Object arg, List<Component> tip, int mx, int my) {
        int full = text == null ? Integer.MAX_VALUE : fullW(icon, text, kind);
        boolean words = text != null && full <= maxW;
        int w = words ? full : ICON_W;
        boolean hov = in(mx, my, x, y, w, h);
        if (kind == PRIMARY) {
            Kit.pillPrimary(g, x, y, w, h, hov);
        } else {
            Kit.pill(g, x, y, w, h, on, hov);
        }
        int ink = kind == PRIMARY ? Theme.CREAM : on ? Theme.INK : Theme.INK_2;
        Type role = kind == PRIMARY ? Type.LABEL : Type.BODY;
        int ty = role.yIn(y, h);
        int iy = role.onCaps(ty, 8);
        if (words) {
            int pad = kind == PRIMARY ? Theme.SP_4 : Theme.SP_3;
            int tx = x + pad;
            if (icon != null) {
                Art.icon(g, icon, tx, iy, 8, ink);
                tx += 8 + Theme.SP_1;
            }
            role.draw(g, font, text, tx, ty, ink);
        } else if (icon != null) {
            iy = y + (h - 8) / 2;
            Art.icon(g, icon, x + (w - 8) / 2, iy, 8, ink);
        }
        if (act != null) {
            btn(x, y, w, h, act, arg);
        }
        List<Component> lines = new ArrayList<>();
        if (!words && text != null) {
            lines.add(Component.literal(text).withStyle(ChatFormatting.WHITE));
        }
        if (tip != null) {
            lines.addAll(tip);
        }
        if (!lines.isEmpty()) {
            host.tip(x, y, w, h, lines);
        }
        return w;
    }

    /** {@link #control}'s width without drawing it (a chip's; a primary's is a touch wider). */
    int controlW(String icon, String text, int maxW) {
        return controlW(icon, text, maxW, WOOD);
    }

    int controlW(String icon, String text, int maxW, int kind) {
        int full = text == null ? Integer.MAX_VALUE : fullW(icon, text, kind);
        return text != null && full <= maxW ? full : ICON_W;
    }

    private int fullW(String icon, String text, int kind) {
        Type role = kind == PRIMARY ? Type.LABEL : Type.BODY;
        int pad = kind == PRIMARY ? Theme.SP_4 : Theme.SP_3;
        return 2 * pad + (icon == null ? 0 : 8 + Theme.SP_1) + role.width(font, text);
    }

    void btn(int x, int y, int w, int h, String act, Object arg) {
        btns.add(new Btn(x, y, w, h, act, arg));
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    String fit(String s, int w, boolean bold) {
        if (s == null || w <= 0) {
            return "";
        }
        if (width(s, bold) <= w) {
            return s;
        }
        String cut = font.plainSubstrByWidth(s, Math.max(0, w - width("…", bold)));
        while (!cut.isEmpty() && width(cut + "…", bold) > w) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    int width(String s, boolean bold) {
        return bold ? font.width(Component.literal(s).withStyle(UiFont::bold)) : font.width(s);
    }

    /** A chosen item's chip: the purple pill (lighter on hover); its name and ✕ cream on it. */
    static void chip(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        Kit.pillPrimary(g, x, y, w, h, hover);
    }

    /**
     * A chosen item as a chip at x, y: its name (cut to {@code maxW}) and a ✕, the click {@code act}
     * on {@code arg}; returns its width.
     */
    int chosenChip(GuiGraphics g, BuildWindow.Host host, int x, int y, String name, int maxW, String act, Object arg, int mx, int my) {
        int cw = Math.min(Type.BODY.width(font, name) + 2 * Theme.SP_3 + Theme.SP_2 + 6, maxW);
        chip(g, x, y, cw, Ui.CHIP_H, in(mx, my, x, y, cw, Ui.CHIP_H));
        int ty = Type.BODY.yIn(y, Ui.CHIP_H);
        Type.BODY.draw(g, font, Type.BODY.fit(font, name, cw - 2 * Theme.SP_3 - Theme.SP_2 - 6), x + Theme.SP_3, ty, Theme.CREAM);
        Art.icon(g, "x", x + cw - Theme.SP_3 - 6, Type.BODY.onCaps(ty, 6), 6, Theme.CREAM);
        btn(x, y, cw, Ui.CHIP_H, act, arg);
        host.tip(x, y, cw, Ui.CHIP_H, List.of(Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE),
                io.github.anilken.wynnlabs.core.L.c("wynnlabs.pick.chip_tip").withStyle(ChatFormatting.DARK_GRAY)));
        return cw;
    }

    /** An icon-only square button (Ui.iconButton) with its click and tooltip; returns its width. */
    int iconBtn(GuiGraphics g, BuildWindow.Host host, int x, int y, String icon, boolean on, String act, Object arg, List<Component> tip,
            int mx, int my) {
        Ui.iconButton(g, icon, x, y, on, mx, my);
        int w = Ui.CHIP_H + 2;
        btn(x, y, w, Ui.CHIP_H, act, arg);
        if (tip != null) {
            host.tip(x, y, w, Ui.CHIP_H, tip);
        }
        return w;
    }
}
