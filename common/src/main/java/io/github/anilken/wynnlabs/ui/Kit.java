package io.github.anilken.wynnlabs.ui;


import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The J menu's wood-and-parchment pieces (tools/art/kit.mjs draws them): cards, slots, seals,
 * pills, hairlines and washes ({@link KitFrame} has the window, the rail and the page). Each call draws
 * on the coordinates it is given, so a screen keeps its layout and only changes its paint.
 * Text on paper goes through {@link #text}: no shadow, colours in their paper tone.
 */
final class Kit {

    private Kit() {}

    /** The paper's grain, tiled 256 px at a time (the page and the slate). */
    static final Identifier GRAIN = Identifier.fromNamespaceAndPath("wynnlabs", "textures/gui/sprites/kit/grain.png");
    static final int GRAIN_SIZE = 256;

    /** {@code kit/slot} nine-sliced to 18 and 24 px ahead of time (kit/slot_18.png, kit/slot_24.png). */
    private static final Identifier SLOT_18 = Identifier.fromNamespaceAndPath("wynnlabs", "kit/slot_18");
    private static final Identifier SLOT_24 = Identifier.fromNamespaceAndPath("wynnlabs", "kit/slot_24");
    private static final Identifier SLOT_20 = Identifier.fromNamespaceAndPath("wynnlabs", "kit/slot_20");
    private static final Identifier SLOT_22 = Identifier.fromNamespaceAndPath("wynnlabs", "kit/slot_22");

    static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("wynnlabs", "kit/" + name);
    }

    /** A kit sprite over the box: nine-sliced ones through {@link NineSlice}, so borders stay even at scale 3. */
    static void nine(GuiGraphics g, String name, int x, int y, int w, int h) {
        nine(g, name, x, y, w, h, -1);
    }

    private static void nine(GuiGraphics g, String name, int x, int y, int w, int h, int tint) {
        NineSlice.draw(g, sprite(name), x, y, w, h, tint);
    }

    // ---- cards, slots and seals ----

    /** A parchment card with a small shadow, and an optional coloured strip along its top. */
    static void card(GuiGraphics g, int x, int y, int w, int h, int accent) {
        card(g, x, y, w, h, accent, false);
    }

    /** A card; {@code lit} gives it a gold edge and a lighter face (hover, or the selected one). */
    static void card(GuiGraphics g, int x, int y, int w, int h, int accent, boolean lit) {
        // a 2px shadow under the card, so cards in a column stay apart
        g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0x88624E3C);
        g.fill(x + 3, y + h + 1, x + w - 3, y + h + 2, 0x44624E3C);
        nine(g, lit ? "card_lit" : "card", x, y, w, h);
        if (accent != 0) {
            g.fill(x + 2, y + 1, x + w - 2, y + 3, accent);
        }
    }

    /**
     * A half-circle gauge (58x29), {@code frac} of it filled from the left, the rest {@code bg};
     * {@code cx, cy} its centre on the flat side. Two sprites at four times the GUI density:
     * the track, and the fill cut off at the angle (a vertical cut, which the ring's thickness
     * makes near enough radial). {@code r} and {@code t} are the sprite's 29 and 5.
     */
    static void gauge(GuiGraphics g, int cx, int cy, int r, int t, double frac, int bg, int fg, int fgDeep) {
        int x = cx - 29;
        int y = cy - 29;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("gauge_track"), x, y, 58, 29, bg);
        double f = Math.max(0, Math.min(1, frac));
        if (f <= 0) {
            return;
        }
        int cut = (int) Math.round(cx - 29 * Math.cos(Math.PI * f));
        g.enableScissor(x, y, Math.min(x + 58, cut + 1), cy);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("gauge_fill"), x, y, 58, 29, fg);
        g.disableScissor();
    }

    /** The ability tree's board: dark slate with a faint grain, shaded towards its rim. */
    static void slate(GuiGraphics g, int x, int y, int w, int h) {
        nine(g, "card", x, y, w, h, 0xFF3A424A);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF262B30);
        int gx0 = x + 2;
        int gy0 = y + 2;
        int gw = w - 4;
        int gh = h - 4;
        for (int oy = 0; oy < gh; oy += GRAIN_SIZE) {
            for (int ox = 0; ox < gw; ox += GRAIN_SIZE) {
                int pw = Math.min(GRAIN_SIZE, gw - ox);
                int ph = Math.min(GRAIN_SIZE, gh - oy);
                g.blit(RenderPipelines.GUI_TEXTURED, GRAIN, gx0 + ox, gy0 + oy, 0, 0, pw, ph, GRAIN_SIZE, GRAIN_SIZE, 0x60FFFFFF);
            }
        }
        g.fill(x + 2, y + 2, x + w - 2, y + 5, 0x48000000);
        g.fill(x + 2, y + h - 5, x + w - 2, y + h - 2, 0x48000000);
        g.fill(x + 2, y + 5, x + 5, y + h - 5, 0x48000000);
        g.fill(x + w - 5, y + 5, x + w - 2, y + h - 5, 0x48000000);
    }

    /** An inset item slot; {@code ring} (0 = none) is a 1px tier-colour line just inside it. */
    static void slot(GuiGraphics g, int x, int y, int size, int ring) {
        Identifier ready = size == 18 ? SLOT_18 : size == 20 ? SLOT_20 : size == 22 ? SLOT_22 : size == 24 ? SLOT_24 : null;
        if (ready != null) {
            // the common sizes, drawn ahead as one picture: one quad instead of nine (the bank draws hundreds)
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ready, x, y, size, size);
        } else {
            nine(g, "slot", x, y, size, size);
        }
        if (ring != 0) {
            // on a small slot the ring is its edge, so it never touches the item
            int in = size <= 20 ? 0 : 1;
            outline(g, x + in, y + in, size - 2 * in, size - 2 * in, ring);
        }
    }

    /** The picked slot's gold ring (and a search's match), 2 px outside a slot of {@code size}. */
    static void slotRing(GuiGraphics g, int x, int y, int size) {
        nine(g, "slot_ring_gold", x - 2, y - 2, size + 4, size + 4);
    }

    /** A rectangle outline, one screen pixel thick (a hairline at every scale). */
    static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        hair(g, x, y, w, 1, colour);
        hair(g, x, y + h - 1, w, 1, colour);
        hair(g, x, y + 1, 1, h - 2, colour);
        hair(g, x + w - 1, y + 1, 1, h - 2, colour);
    }

    /** A rounded seal or pill in a colour (gain seals, the nearest bank / Trade Market). */
    static void seal(GuiGraphics g, int x, int y, int w, int h, int colour) {
        nine(g, "seal", x, y, w, h, colour);
    }

    /** A kind's tile: a rounded square in {@code fill}, a soft top light, a one-screen-pixel ring in {@code ring}. */
    static void tile(GuiGraphics g, int x, int y, int size, int fill, int ring) {
        nine(g, "tile", x, y, size, size, fill);
        nine(g, "tile_shine", x, y, size, size);
        nine(g, "tile_ring", x, y, size, size, ring);
    }

    /** A kind's badge (a town, a merchant, a mob, a chest ...): see {@link KindBadge}; returns the size drawn. */
    static int kindBadge(GuiGraphics g, int x, int y, int size, KindBadge badge) {
        return KindBadge.draw(g, x, y, size, badge);
    }

    /** An outlined pill. */
    static void pillLine(GuiGraphics g, int x, int y, int w, int h, int colour) {
        nine(g, "pill_line", x, y, w, h, colour);
    }

    /** Notches every tenth of a bar (the XP bar). */
    static void notches(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 1; i < 10; i++) {
            int nx = x + 1 + (w - 2) * i / 10;
            hair(g, nx, y + 1, 1, h - 2, 0x662E2E2E);
        }
    }

    /** A section title: the title in ink where it always was, then a brass ◆ and a rule to {@code x + w}. */
    static void section(GuiGraphics g, Font font, String title, int x, int y, int w) {
        g.drawString(font, title, x, y, Theme.INK_2, false);
        int dx = x + font.width(title) + 3;
        nine(g, "diamond", dx, y + 2, 5, 5);
        int lx = dx + 8;
        if (lx < x + w) {
            hair(g, lx, y + 4, x + w - lx, 1, Theme.LINE);
        }
    }

    // ---- soft shapes and hairlines (the HD look: baked 6x nine-slices, lines one screen pixel wide) ----

    /**
     * A hairline: a 1px-wide line (w or h is 1) drawn one screen pixel thick, centred in that
     * logical pixel, whatever the GUI or menu scale.
     */
    static void hair(GuiGraphics g, int x, int y, int w, int h, int colour) {
        // screen pixels per unit here: the window's GUI scale times the pose's own (MenuScale's, or none)
        int s;
        try {
            s = Math.max(1, Math.round((float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale() * g.pose().m00()));
        } catch (Throwable t) {
            s = 1;
        }
        if (s == 1 || w <= 0 || h <= 0) {
            g.fill(x, y, x + w, y + h, colour);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1f / s, 1f / s);
        int mid = (s - 1) / 2;
        if (h == 1) {
            g.fill(0, mid, w * s, mid + 1, colour);
        } else {
            g.fill(mid, 0, mid + 1, h * s, colour);
        }
        g.pose().popMatrix();
    }

    /** A soft card (kit/card_soft): rounded, a light gradient, a faint edge and a 2px shadow under it. */
    static void softCard(GuiGraphics g, int x, int y, int w, int h) {
        nine(g, "card_soft", x, y, w, h + 2);
    }

    /** A pill: gold when {@code on} (a button, the picked chip), else a thin outline; lighter on hover. */
    static void pill(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean hover) {
        nine(g, on ? (hover ? "pill_gold_hover" : "pill_gold") : (hover ? "pill_thin_hover" : "pill_thin"), x, y, w, h);
    }

    /** A text field's ring over its pill: gold while typed in, red on an invalid value. */
    static void ring(GuiGraphics g, int x, int y, int w, int h, boolean error) {
        nine(g, error ? "pill_error" : "pill_focus", x, y, w, h);
    }

    /** A field that is off: the thin pill, faded. */
    static void hairPill(GuiGraphics g, int x, int y, int w, int h) {
        nine(g, "pill_thin", x, y, w, h, 0x80FFFFFF);
    }

    /** A primary pill (the items you chose, Save, the picked plan): the archetype's purple, lighter on hover. */
    static void pillPrimary(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        nine(g, hover ? "pill_purple_hover" : "pill_purple", x, y, w, h);
    }

    /** The picked row of a list: a soft gold wash. */
    static void rowLit(GuiGraphics g, int x, int y, int w, int h) {
        // a wash is never cut by a clip: an end outside the open scissor moves in (both ends stay round)
        int mid = y + h / 2;
        int in = 0;
        while (in < 2 * Theme.SP_3 && in < w / 2 && !inClip(g, x + in, mid)) {
            in++;
        }
        int out = 0;
        while (out < 2 * Theme.SP_3 && out < w / 2 && !inClip(g, x + w - 1 - out, mid)) {
            out++;
        }
        nine(g, "row_lit", x + in, y, w - in - out, h);
    }

    /** Whether a point (in this pose's units) is inside the open scissor; true without one. */
    static boolean inClip(GuiGraphics g, int x, int y) {
        org.joml.Vector2f v = g.pose().transformPosition(x + 0.5f, y + 0.5f, new org.joml.Vector2f());
        return g.containsPointInScissor((int) Math.floor(v.x), (int) Math.floor(v.y));
    }

    /** A soft shadow around a window drawn at x, y, w, h (call before {@link #window}). */
    static void windowShadow(GuiGraphics g, int x, int y, int w, int h) {
        nine(g, "window_shadow", x - 4, y - 4, w + 8, h + 8);
    }

    /** A small drawn icon (kit/icon_check, icon_up/down/left/right, icon_pin) in a colour. */
    static void icon(GuiGraphics g, String name, int x, int y, int w, int h, int colour) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(name), x, y, w, h, colour);
    }

    // ---- text ----

    /** Text on paper: no shadow, its colours turned to their paper tone. */
    static void text(GuiGraphics g, Font font, Component c, int x, int y, int colour) {
        g.drawString(font, ink(c.getVisualOrderText()), x, y, colour, false);
    }

    /** Plain text on paper in one colour, no shadow. */
    static void text(GuiGraphics g, Font font, String s, int x, int y, int colour) {
        g.drawString(font, s, x, y, colour, false);
    }

    /**
     * A sequence whose styled colours are swapped for their paper tone. Runs of one style share
     * the swapped style, so nothing is allocated per character.
     */
    static FormattedCharSequence ink(FormattedCharSequence seq) {
        return sink -> {
            Style[] last = new Style[2];
            return seq.accept((index, style, cp) -> {
                if (style != last[0]) {
                    last[0] = style;
                    last[1] = paperStyle(style);
                }
                return sink.accept(index, last[1], cp);
            });
        };
    }

    static Style paperStyle(Style s) {
        TextColor c = s.getColor();
        if (c == null) {
            return s;
        }
        int p = Theme.paperRgb(c.getValue());
        return (p & 0xFFFFFF) == (c.getValue() & 0xFFFFFF) ? s : s.withColor(p & 0xFFFFFF);
    }
}
