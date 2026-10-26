package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.quests.GuideCoords;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * A guide line wrapped word by word, its coordinate groups kept whole ("-1655, -2937" never
 * breaks) and drawn in a quieter ink with a map pin after them; a click on a group or its pin
 * points the compass there. Wrapped lines start where the first one does (hanging indent).
 */
final class GuideText {

    /** A word, or a coordinate group with its trailing punctuation ({@code spot} set). */
    record Tok(String text, int w, GuideCoords.Spot spot) {}

    static final int PIN_W = 5;
    static final int PIN_H = 7;

    private GuideText() {}

    /** {@code s} in {@code role}'s font wrapped to {@code width}; {@code points}: the stage's places, for x/z pairs. */
    static List<List<Tok>> wrap(Font font, Type role, String s, List<int[]> points, int width) {
        List<Tok> toks = new ArrayList<>();
        int last = 0;
        for (GuideCoords.Spot sp : GuideCoords.find(s, points)) {
            words(font, role, s.substring(last, sp.start()), toks);
            int end = sp.end();
            while (end < s.length() && ",.;:)".indexOf(s.charAt(end)) >= 0) {
                end++;                                       // "-2937," keeps its comma
            }
            String t = s.substring(sp.start(), end);
            toks.add(new Tok(t, role.width(font, t) + Theme.SP_1 + PIN_W, sp));
            last = end;
        }
        words(font, role, s.substring(last), toks);
        int space = role.width(font, " ");
        List<List<Tok>> lines = new ArrayList<>();
        List<Tok> cur = new ArrayList<>();
        int w = 0;
        for (Tok t : toks) {
            if (!cur.isEmpty() && w + space + t.w() > width) {
                lines.add(cur);
                cur = new ArrayList<>();
                w = 0;
            }
            w += (cur.isEmpty() ? 0 : space) + t.w();
            cur.add(t);
        }
        if (!cur.isEmpty()) {
            lines.add(cur);
        }
        return lines;
    }

    private static void words(Font font, Type role, String part, List<Tok> out) {
        for (String w : part.split(" ")) {
            if (!w.isEmpty()) {
                out.add(new Tok(w, role.width(font, w), null));
            }
        }
    }

    /** The lines' height. */
    static int height(Type role, List<List<Tok>> lines) {
        return lines.size() * role.lineHeight;
    }

    /**
     * Draws the lines from {@code y}; a group's pin and text answer the mouse (hover: link ink and
     * a hairline under it) and, when {@code clickable}, a click shows the way there.
     */
    static void draw(GuiGraphics g, Font font, Type role, List<List<Tok>> lines, int x, int y, int ink,
                     int mx, int my, GuideScreen screen, boolean clickable) {
        int space = role.width(font, " ");
        for (List<Tok> line : lines) {
            int cx = x;
            StringBuilder run = new StringBuilder();
            for (int i = 0; i <= line.size(); i++) {
                Tok t = i < line.size() ? line.get(i) : null;
                if (t != null && t.spot() == null) {
                    run.append(run.isEmpty() ? "" : " ").append(t.text());
                    continue;
                }
                if (!run.isEmpty()) {
                    String words = run.toString();
                    role.draw(g, font, words, cx, y, ink);
                    cx += role.width(font, words) + space;
                    run.setLength(0);
                }
                if (t == null) {
                    break;
                }
                boolean hover = clickable && mx >= cx && mx < cx + t.w() && my >= y && my < y + role.lineHeight;
                int c = hover ? Theme.LINK : Theme.INK_2;
                int tw = role.width(font, t.text());
                role.draw(g, font, t.text(), cx, y, c);
                Kit.icon(g, "icon_pin", cx + tw + Theme.SP_1, role.onCaps(y, PIN_H), PIN_W, PIN_H, hover ? Theme.LINK : Theme.GOLD_EDGE);
                if (hover) {
                    Kit.hair(g, cx, y + role.lineHeight - 2, tw, 1, Theme.LINK);
                }
                if (clickable) {
                    GuideCoords.Spot sp = t.spot();
                    screen.tap(cx, y, t.w(), role.lineHeight, () -> screen.compass(sp.at(), sp.knowsY()));
                }
                cx += t.w() + space;
            }
            y += role.lineHeight;
        }
    }
}
