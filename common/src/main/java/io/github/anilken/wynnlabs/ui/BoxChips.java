package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.Entry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The box window's stat pieces: a stat as a small pill (its name and value, its roll % coloured
 * by quality, ↑ or ↓ against your own roll), a tiny one for a collapsed row (name and %), the
 * stats in the build's order, and the HD roll bar (a light track, the fill up to its share).
 */
final class BoxChips {

    static final int H = Ui.SEAL_H;
    private static final int FILL = 0xFFF3E7C6;
    private static final int RING = 0x55A08A5A;
    private static final int TOP = 0xFFB07A10;
    private static final int ARROW = 7;

    private BoxChips() {}

    /** A roll's colour: gold for a top roll (97 % and up), green over 70, red under 30, ink between. */
    static int quality(double p) {
        return p >= 97 ? TOP : p > 70 ? Theme.GOOD : p < 30 ? Theme.BAD : Theme.INK_2;
    }

    /** The stats with a roll %, the build's most wanted first (rules' stat weights), the game's order otherwise. */
    /** The ordered stats and the price text per listing, made once per rebuild (the rows only read them). */
    private static final Map<BoxMarket.Listing, List<BoxMarket.StatRoll>> ORDERED = new java.util.IdentityHashMap<>();
    private static final Map<BoxMarket.Listing, String> PRICE = new java.util.IdentityHashMap<>();

    /** The rows were built again: what was made for the old ones goes. */
    static void forget() {
        ORDERED.clear();
        PRICE.clear();
    }

    /** "56.4 LE" (and "×3"), made once per listing. */
    static String price(BoxMarket.Listing l) {
        return PRICE.computeIfAbsent(l, k -> io.github.anilken.wynnlabs.market.Prices.fmt(k.price()) + (k.amount() > 1 ? " ×" + k.amount() : ""));
    }

    static List<BoxMarket.StatRoll> ordered(BoxMarket.Listing l) {
        return ORDERED.computeIfAbsent(l, BoxChips::order);
    }

    private static List<BoxMarket.StatRoll> order(BoxMarket.Listing l) {
        List<BoxMarket.StatRoll> out = new ArrayList<>();
        for (BoxMarket.StatRoll s : l.statRolls()) {
            if (s.percent() != null) {
                out.add(s);
            }
        }
        Map<String, Double> w = new java.util.HashMap<>();
        for (BoxMarket.StatRoll s : out) {
            w.put(s.key(), -Math.abs(Rules.statWeight(s.key())));
        }
        out.sort(Comparator.comparingDouble((BoxMarket.StatRoll s) -> w.get(s.key())));
        return List.copyOf(out);
    }

    /** The stat's name as the game writes it ("1st Spell Cost"), else made from its key. */
    static String name(String key) {
        String n = names.get(key);
        return n != null ? n : SearchResultsPanel.statName(key);
    }

    /** A stat with no roll % (a fixed one, or one of an item without data): the same pill, its name and value. */
    static int plain(GuiGraphics g, Font font, String text, int ink, int x, int y) {
        int w = 2 * Theme.SP_2 + Type.META.width(font, text);
        Art.box(g, x, y, w, H, H / 2, FILL, RING);
        Type.META.draw(g, font, text, x + Theme.SP_2, Type.META.yIn(y, H), ink);
        return w;
    }

    /** The item's stat units by key ("%", "/5s" …), read once per item from the gear data. */
    private static final Map<String, Map<String, String>> UNITS = new java.util.HashMap<>();
    private static Map<String, String> unit = Map.of();
    private static final Map<String, Map<String, String>> NAMES = new java.util.HashMap<>();
    private static Map<String, String> names = Map.of();

    /** The chips that follow are for {@code itemName}: their values get its stats' units. */
    static void use(String itemName) {
        unit = UNITS.computeIfAbsent(itemName, n -> {
            Map<String, String> out = new java.util.HashMap<>();
            io.github.anilken.wynnlabs.model.ItemData d = io.github.anilken.wynnlabs.build.BuildSources.itemOf(n);
            if (d != null) {
                for (io.github.anilken.wynnlabs.model.StatRange r : d.stats()) {
                    out.put(r.key(), suffix(r.unit()));
                }
            }
            return out;
        });
        names = NAMES.computeIfAbsent(itemName, n -> {
            Map<String, String> out = new java.util.HashMap<>();
            io.github.anilken.wynnlabs.model.ItemData d = io.github.anilken.wynnlabs.build.BuildSources.itemOf(n);
            if (d != null) {
                for (io.github.anilken.wynnlabs.model.StatRange r : d.stats()) {
                    if (r.name() != null && !r.name().isBlank()) {
                        out.put(r.key(), r.name());
                    }
                }
            }
            return out;
        });
    }

    /** Wynntils' stat unit as the game writes it after the number. */
    static String suffix(String u) {
        return u == null ? "" : switch (u) {
            case "PERCENT" -> "%";
            case "PER_FIVE_SECONDS" -> "/5s";
            case "PER_THREE_SECONDS" -> "/3s";
            case "TIER" -> " tier";
            default -> "";
        };
    }

    static String value(BoxMarket.StatRoll s) {
        if (s.value() == null) {
            return "";
        }
        long v = Math.round(s.value());
        return (v > 0 ? "+" + v : String.valueOf(v)) + unit.getOrDefault(s.key(), "");
    }

    private static String pct(double p) {
        return BoxScreen.pct(Math.round(p));
    }

    /** The full chip's width: name, value, %, and room for the arrow when yours is known. */
    static int width(Font font, BoxMarket.StatRoll s, boolean arrow) {
        String v = value(s);
        return 2 * Theme.SP_2 + Type.META.width(font, name(s.key()) + (v.isEmpty() ? "" : " " + v)) + Theme.SP_2
                + Type.META.width(font, pct(s.percent())) + (arrow ? ARROW + 1 : 0);
    }

    /** The full chip; {@code own} your roll % of the stat (null: no arrow), {@code grey} for a possibly sold listing. */
    static int draw(GuiGraphics g, Font font, BoxMarket.StatRoll s, Double own, boolean arrows, boolean grey, int x, int y) {
        int w = width(font, s, arrows);
        Art.box(g, x, y, w, H, H / 2, FILL, RING);
        String v = value(s);
        String t = name(s.key()) + (v.isEmpty() ? "" : " " + v);
        int ty = Type.META.yIn(y, H);
        Type.META.draw(g, font, t, x + Theme.SP_2, ty, grey ? Theme.INK_3 : Theme.INK_2);
        int px = x + Theme.SP_2 + Type.META.width(font, t) + Theme.SP_2;
        String p = pct(s.percent());
        Type.META.draw(g, font, p, px, ty, grey ? Theme.INK_3 : quality(s.percent()));
        if (own != null && !grey && Math.round(own) != Math.round(s.percent())) {
            boolean up = s.percent() > own;
            Art.icon(g, up ? "arrow-up" : "arrow-down", px + Type.META.width(font, p) + 1, y + Ui.iconIn(H, ARROW), ARROW,
                    up ? Theme.GOOD : Theme.BAD);
        }
        return w;
    }

    static int miniWidth(Font font, BoxMarket.StatRoll s) {
        return 2 * Theme.SP_2 + Type.META.width(font, name(s.key())) + Theme.SP_1 + Type.META.width(font, pct(s.percent()));
    }

    /** The collapsed row's tiny chip: name and roll %. */
    static int mini(GuiGraphics g, Font font, BoxMarket.StatRoll s, boolean grey, int x, int y) {
        int w = miniWidth(font, s);
        Art.box(g, x, y, w, H, H / 2, FILL, RING);
        int ty = Type.META.yIn(y, H);
        String n = name(s.key());
        Type.META.draw(g, font, n, x + Theme.SP_2, ty, grey ? Theme.INK_3 : Theme.INK_2);
        Type.META.draw(g, font, pct(s.percent()), x + Theme.SP_2 + Type.META.width(font, n) + Theme.SP_1,
                ty, grey ? Theme.INK_3 : quality(s.percent()));
        return w;
    }

    /** The full chips' height in {@code w}: lines of {@link #H}, SP_1 apart. */
    static int height(Font font, List<BoxMarket.StatRoll> stats, boolean arrows, int w) {
        int cx = 0;
        int lines = 1;
        for (BoxMarket.StatRoll s : stats) {
            int cw = width(font, s, arrows);
            if (cx > 0 && cx + cw > w) {
                cx = 0;
                lines++;
            }
            cx += cw + Theme.SP_1;
        }
        return stats.isEmpty() ? 0 : lines * H + (lines - 1) * Theme.SP_1;
    }

    /**
     * All chips from x, y wrapping in {@code w}; the hovered chip's tooltip goes to {@code tip}
     * (name, value, roll, the item's range, yours). Returns the bottom.
     */
    static int wrap(GuiGraphics g, Font font, List<BoxMarket.StatRoll> stats, Map<String, Double> own, boolean grey, Entry item,
                    int x, int y, int w, int mx, int my, List<Component> tip) {
        boolean arrows = own != null && !own.isEmpty();
        int cx = x;
        int line = y;
        for (BoxMarket.StatRoll s : stats) {
            int cw = width(font, s, arrows);
            if (cx > x && cx + cw > x + w) {
                cx = x;
                line += H + Theme.SP_1;
            }
            draw(g, font, s, arrows ? own.get(s.key()) : null, arrows, grey, cx, line);
            if (tip != null && tip.isEmpty() && mx >= cx && mx < cx + cw && my >= line && my < line + H) {
                tip.addAll(tipOf(s, own == null ? null : own.get(s.key()), item));
            }
            cx += cw + Theme.SP_1;
        }
        return stats.isEmpty() ? y : line + H;
    }

    /** A chip's tooltip: the stat, its roll, the item's range when known, and yours. */
    static List<Component> tipOf(BoxMarket.StatRoll s, Double own, Entry item) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(name(s.key()) + " " + value(s)).withStyle(ChatFormatting.WHITE));
        out.add(L.c("wynnlabs.box.chip_roll", pct(s.percent())).withStyle(ChatFormatting.GRAY));
        Entry.Stat range = rangeOf(item, s.key());
        if (range != null) {
            out.add(L.c("wynnlabs.box.chip_range", signed(range.min()), signed(range.max())).withStyle(ChatFormatting.GRAY));
        }
        if (own != null) {
            out.add(L.c("wynnlabs.box.chip_yours", pct(own)).withStyle(ChatFormatting.DARK_GREEN));
        }
        return out;
    }

    static Entry.Stat rangeOf(Entry item, String key) {
        if (item == null) {
            return null;
        }
        for (Entry.Stat st : item.stats()) {
            if (st.key().equals(key)) {
                return st;
            }
        }
        return null;
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    /** The HD roll bar: a light track, the fill in the roll's colour up to its share (grey for a possibly sold listing). */
    static void bar(GuiGraphics g, int x, int y, int w, int h, double roll, boolean grey) {
        Art.box(g, x, y, w, h, h / 2, 0xFFE4D6B0, RING);
        int col = grey ? 0xFFB9A57E : roll > 70 ? 0xFF4E9A3A : roll >= 30 ? 0xFFC98A2A : 0xFFB2402E;
        int fw = Math.max(h, (int) Math.round(w * Math.max(0, Math.min(100, roll)) / 100.0));
        Art.box(g, x, y, fw, h, h / 2, col, 0);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static int buildSealWidth(Font font, int gain, boolean word) {
        return 2 * Theme.SP_2 + 7 + Theme.SP_1 + Type.META.width(font, label(gain, word));
    }

    private static String label(int gain, boolean word) {
        return word ? L.t("wynnlabs.box.build_seal", gain) : "+" + gain;
    }

    /** "+12 build": a light seal in the build's purple; returns its width. */
    static int buildSeal(GuiGraphics g, Font font, int gain, boolean word, int x, int y) {
        int w = buildSealWidth(font, gain, word);
        Art.box(g, x, y, w, Ui.SEAL_H, Ui.SEAL_H / 2, 0xFFEDE4F4, BuildControls.CHOSEN);
        Art.icon(g, "target", x + Theme.SP_2, y + Ui.iconIn(Ui.SEAL_H, 7), 7, BuildControls.CHOSEN);
        Type.META.draw(g, font, label(gain, word), x + Theme.SP_2 + 7 + Theme.SP_1, Type.META.yIn(y, Ui.SEAL_H), BuildControls.CHOSEN);
        return w;
    }

}
