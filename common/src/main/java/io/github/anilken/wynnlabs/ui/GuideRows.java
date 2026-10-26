package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.GuideCoords;
import io.github.anilken.wynnlabs.quests.GuideLayout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * A stage's rows as blocks for the guide's scrolling pane: steps and places with their number
 * right-aligned in the mark column and wrapped lines hanging under the text, choices with a brass
 * diamond and a hairline down their steps, items on a green seal, places with their "Yol ›". Text
 * stops at the reading width; the buttons keep the column at the right edge.
 */
final class GuideRows {

    /** What a block draws at {@code y}. */
    interface Paint {
        void draw(GuiGraphics g, int y, int mx, int my);
    }

    /** A block: the space above it, its height, how it draws. */
    record Block(int gap, int h, Paint paint) {}

    private GuideRows() {}

    /**
     * The blocks of {@code groups} (the common rows, then the option's; a block gap between them),
     * laid out from {@code x} over {@code w}; {@code points} are the stage's places.
     */
    static List<Block> build(GuideScreen s, Font font, List<List<GuideLayout.Row>> groups, List<int[]> points, int x, int w) {
        List<Block> out = new ArrayList<>();
        int measure = Math.min(GuidePane.MEASURE, w - GuidePane.goW(font) - Theme.SP_4);
        boolean first = true;
        for (List<GuideLayout.Row> rows : groups) {
            if (rows.isEmpty()) {
                continue;
            }
            GuideLayout.Row prev = null;
            List<int[]> run = new ArrayList<>();
            for (int i = 0; i <= rows.size(); i++) {
                GuideLayout.Row r = i < rows.size() ? rows.get(i) : null;
                // after a run of places with coordinates: one link puts them all on the compass
                if ((r == null || r.kind() != GuideLayout.Kind.PLACE) && run.size() >= 2) {
                    List<int[]> all = List.copyOf(run);
                    boolean under = prev != null && prev.under();
                    out.add(new Block(Theme.SP_2, Type.LINK.lineHeight, (g, y, mx, my) -> {
                        String t = L.t("wynnlabs.guide.add_all");
                        int tx = x + (under ? GuidePane.MARK_W : 0) + GuidePane.MARK_W;
                        int tw = Type.LINK.width(font, t);
                        boolean hover = mx >= tx && mx < tx + tw && my >= y && my < y + Type.LINK.lineHeight;
                        if (under) {
                            Kit.hair(g, x + 5, y - Theme.SP_2, 1, Type.LINK.lineHeight + Theme.SP_2, Theme.LINE);
                        }
                        Type.LINK.draw(g, font, t, tx, y, hover ? Theme.GOLD_EDGE : Theme.LINK);
                        if (hover) {
                            Kit.hair(g, tx, y + Type.LINK.lineHeight - 2, tw, 1, Theme.GOLD_EDGE);
                        }
                        s.tap(tx, y, tw, Type.LINK.lineHeight, () -> s.compassAll(all));
                    }));
                }
                if (r == null) {
                    break;
                }
                if (r.kind() != GuideLayout.Kind.PLACE) {
                    run.clear();
                } else if (r.at() != null) {
                    run.add(r.at());
                }
                int gap = first ? 0 : prev == null ? Theme.SP_5
                        : r.kind() == GuideLayout.Kind.CHOICE || r.kind() == GuideLayout.Kind.PART ? Theme.SP_5 : Theme.SP_3;
                out.add(block(s, font, r, gap, points, x, w, measure));
                prev = r;
                first = false;
            }
        }
        return out;
    }

    private static Block block(GuideScreen s, Font font, GuideLayout.Row r, int gap, List<int[]> points, int x, int w, int measure) {
        int ind = r.under() ? GuidePane.MARK_W : 0;
        int tx = x + ind + GuidePane.MARK_W;              // the text column; marks end SP_1 before it
        int width = measure - ind - GuidePane.MARK_W;
        switch (r.kind()) {
            case PART -> {
                return new Block(gap, Type.LABEL.lineHeight, (g, y, mx, my) -> {
                    Type.LABEL.draw(g, font, r.text(), x, y, Theme.ACCENT);
                    int cx = x + Type.LABEL.width(font, r.text()) + Theme.SP_3;
                    Kit.hair(g, cx, y + 6, x + w - cx, 1, Theme.LINE);
                });
            }
            case CHOICE -> {
                List<List<GuideText.Tok>> lines = GuideText.wrap(font, Type.LABEL, r.text(), points, measure - GuidePane.MARK_W);
                return new Block(gap, GuideText.height(Type.LABEL, lines), (g, y, mx, my) -> {
                    GuidePane.diamond(g, x + 3, y + 3);
                    GuideText.draw(g, font, Type.LABEL, lines, x + GuidePane.MARK_W, y, Type.LABEL.colour, mx, my, s, true);
                });
            }
            case ITEM -> {
                int sp = r.text().indexOf(' ');
                String count = sp > 0 ? r.text().substring(0, sp) : r.text();
                String name = sp > 0 ? r.text().substring(sp + 1) : "";
                return new Block(gap, Type.BODY.lineHeight, (g, y, mx, my) -> {
                    rail(g, r, x, y, gap, Type.BODY.lineHeight);
                    int qw = Ui.seal(g, font, count, tx, Type.BODY.onCaps(y, Ui.SEAL_H), Theme.SEAL_2);
                    Type.BODY.draw(g, font, Type.BODY.fit(font, name, width - qw - Theme.SP_2), tx + qw + Theme.SP_2, y, Theme.GOOD);
                });
            }
            case PLACE -> {
                return place(s, font, r, gap, points, x, w, tx, width);
            }
            default -> {
                String mark = r.kind() == GuideLayout.Kind.STEP ? r.number() + "." : "•";
                List<List<GuideText.Tok>> lines = GuideText.wrap(font, Type.BODY, r.text(), points, width);
                int h = GuideText.height(Type.BODY, lines);
                return new Block(gap, h, (g, y, mx, my) -> {
                    rail(g, r, x, y, gap, h);
                    if (r.kind() == GuideLayout.Kind.STEP) {
                        Type.NUMBER.drawRight(g, font, mark, tx - Theme.SP_1, y, Type.NUMBER.colour);
                    } else {
                        Type.MUTED.drawRight(g, font, mark, tx - Theme.SP_1 - Theme.SP_2, y, Theme.INK_3);
                    }
                    GuideText.draw(g, font, Type.BODY, lines, tx, y, Type.BODY.colour, mx, my, s, true);
                });
            }
        }
    }

    /** A place: its number (a pin when alone), name (or "Point n") and coordinates, the line about it under them, its "Yol ›". */
    private static Block place(GuideScreen s, Font font, GuideLayout.Row r, int gap, List<int[]> points, int x, int w, int tx, int width) {
        String name = r.text().isEmpty() ? L.t("wynnlabs.guide.point") + (r.number() > 0 ? " " + r.number() : "") : r.text();
        List<List<GuideText.Tok>> note = r.note() == null ? List.of() : GuideText.wrap(font, Type.MUTED, r.note(), points, width);
        int h = Type.BODY.lineHeight + GuideText.height(Type.MUTED, note);
        int[] at;
        boolean knowsY;
        if (r.at() != null) {
            at = r.at();
            knowsY = true;
        } else {
            // "Granite cave (-1797 -2120)": x and z only; the height of a stage point near it, or the player's
            List<GuideCoords.Spot> spots = r.place() == null ? List.of() : GuideCoords.find(r.place(), points);
            at = spots.isEmpty() ? null : spots.get(0).at();
            knowsY = !spots.isEmpty() && spots.get(0).knowsY();
        }
        return new Block(gap, h, (g, y, mx, my) -> {
            rail(g, r, x, y, gap, h);
            if (r.number() > 0) {
                Type.NUMBER.drawRight(g, font, r.number() + ".", tx - Theme.SP_1, y, Type.NUMBER.colour);
            } else {
                Kit.icon(g, "icon_pin", tx - Theme.SP_1 - Theme.SP_2 - GuideText.PIN_W, Type.BODY.onCaps(y, GuideText.PIN_H),
                        GuideText.PIN_W, GuideText.PIN_H, Theme.GOLD_EDGE);
            }
            String shown = Type.BODY.fit(font, name, width);
            Type.BODY.draw(g, font, shown, tx, y);
            if (r.place() != null) {
                // the coordinates as a clickable group with its pin, like every other
                int cx = tx + Type.BODY.width(font, shown) + Theme.SP_3;
                GuideText.draw(g, font, Type.BODY, GuideText.wrap(font, Type.BODY, r.place(), points, x + w - cx), cx, y,
                        Type.BODY.colour, mx, my, s, true);
            }
            GuideText.draw(g, font, Type.MUTED, note, tx, y + Type.BODY.lineHeight, Type.MUTED.colour, mx, my, s, true);
            if (at != null) {
                GuidePane.goButton(s, g, mx, my, x + w - GuidePane.goW(font), y, () -> s.compass(at, knowsY));
            }
        });
    }

    /** The hairline down a choice's (or a lead-in line's) rows, through the gap above each one. */
    private static void rail(GuiGraphics g, GuideLayout.Row r, int x, int y, int gap, int h) {
        if (r.under()) {
            Kit.hair(g, x + 5, y - gap, 1, h + gap, Theme.LINE);
        }
    }
}
