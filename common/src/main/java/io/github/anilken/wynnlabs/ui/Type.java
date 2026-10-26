package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The mod's type scale: every piece of text on its screens is one of these roles, so sizes,
 * weights, line heights and colours stay the same from screen to screen. A role is one of the
 * UI font's sizes (Noto Sans, sharp at every GUI scale: see {@link UiFont}), plain or bold, with
 * its line height (the step from one line to the next, in logical px) and its ink on paper.
 *
 * <pre>
 *   TITLE     big bold    16  a window's title
 *   HEADING   large bold  14  a page's or a stage's title
 *   LEAD      large       16  the one thing to read first (a stage's task)
 *   LABEL     base bold   12  a section's or a choice's title, a picked tab
 *   BODY      base        12  running text, list rows
 *   MUTED     base        12  secondary text beside body text
 *   NUMBER    base        12  step numbers, counts in a column
 *   LINK      base        12  "Go ›" and other clickable words
 *   META      small       11  captions, hints, a value's unit, coordinates
 *   MICRO     tiny        9   footers, key hints, tags
 * </pre>
 *
 * Use at most three or four roles on one screen. Spacing between blocks comes from
 * {@link Theme}'s SP_* steps, never from ad-hoc numbers.
 */
enum Type {
    TITLE("big", true, 16, Theme.ACCENT),
    HEADING("large", true, 14, Theme.INK),
    LEAD("large", false, 16, Theme.INK),
    LABEL("", true, 12, Theme.INK),
    BODY("", false, 12, Theme.INK),
    MUTED("", false, 12, Theme.INK_2),
    NUMBER("", false, 12, Theme.PRICE),
    LINK("", false, 12, Theme.LINK),
    META("small", false, 11, Theme.INK_2),
    MICRO("tiny", false, 9, Theme.INK_3);

    /** The UI font size ("" for base). */
    final String size;
    final boolean bold;
    /** Logical px from one line's top to the next. */
    final int lineHeight;
    /** The role's ink; a caller may pass another for state (done, picked, warning). */
    final int colour;

    Type(String size, boolean bold, int lineHeight, int colour) {
        this.size = size;
        this.bold = bold;
        this.lineHeight = lineHeight;
        this.colour = colour;
    }

    /** The font's size in GUI px, as its ui_*.json gives it. */
    float px() {
        return switch (size) {
            case "micro" -> 4.3f;
            case "tiny" -> 7f;
            case "small" -> 7.8f;
            case "large" -> 11.5f;
            case "big" -> 13.5f;
            default -> 9f;
        };
    }

    /** How far down the font json moves its glyphs ("shift"), so large text shares the base line's baseline. */
    int shift() {
        return switch (size) {
            case "large" -> 2;
            case "big" -> 3;
            default -> 0;
        };
    }

    /** The y to draw this role's text at so its capitals sit centred in a box (a button, a chip, a row). */
    int yIn(int boxY, int boxH) {
        return Ui.centreY(boxY, boxH, px(), shift(), !io.github.anilken.wynnlabs.core.Settings.on("webFont"));
    }

    /** The top of an {@code h} tall icon or box centred on the capitals of this role's line drawn at {@code lineY}. */
    int onCaps(int lineY, int h) {
        return Ui.onCaps(lineY, h, px(), shift(), !io.github.anilken.wynnlabs.core.Settings.on("webFont"));
    }

    /** The text in this role's font. */
    MutableComponent of(String text) {
        return of(Component.literal(text));
    }

    MutableComponent of(Component text) {
        return text.copy().withStyle(s -> UiFont.sizedTo(s, size, bold));
    }

    /** Its width in this role's font. */
    int width(Font font, String text) {
        return font.width(of(text));
    }

    /** One line in the role's ink; returns the next line's y. */
    int draw(GuiGraphics g, Font font, String text, int x, int y) {
        return draw(g, font, text, x, y, colour);
    }

    int draw(GuiGraphics g, Font font, String text, int x, int y, int ink) {
        Kit.text(g, font, of(text), x, y, ink);
        return y + lineHeight;
    }

    /** Right-aligned at {@code right}: a number column, a link at a row's end. */
    void drawRight(GuiGraphics g, Font font, String text, int right, int y, int ink) {
        Kit.text(g, font, of(text), right - width(font, text), y, ink);
    }

    /** The text cut to {@code width} with "…" when it does not fit. */
    String fit(Font font, String text, int width) {
        if (width(font, text) <= width) {
            return text;
        }
        int dots = width(font, "…");
        String s = text;
        while (!s.isEmpty() && width(font, s) + dots > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s.stripTrailing() + "…";
    }

    /** The text wrapped to {@code width} in this role's font. */
    List<FormattedCharSequence> wrap(Font font, String text, int width) {
        return font.split(of(text), Math.max(1, width));
    }

    /**
     * Wrapped text drawn line by line in the role's ink, at most {@code maxLines} (0: no limit);
     * returns the y under the last line.
     */
    int drawWrapped(GuiGraphics g, Font font, String text, int x, int y, int width, int maxLines, int ink) {
        List<FormattedCharSequence> lines = wrap(font, text, width);
        for (int i = 0; i < lines.size() && (maxLines <= 0 || i < maxLines); i++) {
            g.drawString(font, Kit.ink(lines.get(i)), x, y, ink, false);
            y += lineHeight;
        }
        return y;
    }

    /** The height {@code text} takes wrapped to {@code width}. */
    int height(Font font, String text, int width) {
        return wrap(font, text, width).size() * lineHeight;
    }
}
