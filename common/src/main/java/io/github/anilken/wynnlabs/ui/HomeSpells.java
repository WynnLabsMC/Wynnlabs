package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.BuildStats;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Sources;
import io.github.anilken.wynnlabs.core.L;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Home's spell panel in its left column, like WynnBuilder's spell list: a card per source of
 * the build's damage and healing ({@link Sources}), in the class's order: the main attack (its
 * DPS, attack speed and one hit), each spell (its mana cost after Intelligence and the cost IDs,
 * and its damage per cast), the lasting spells, each summon and area of the tree (its DPS, one
 * hit times how many), the heals in green, and the ultimates last, greyed, as they are not in the
 * totals. Worded as WynnBuilder is, in English in every language. With a build
 * to compare ({@link StatsSource}), each number has its change beside it, green when better. Only
 * reads.
 */
final class HomeSpells extends HomePart {

    /** The attack speed tiers' names, as the game has them. */
    private static final String[] SPEEDS = {"Super Slow", "Very Slow", "Slow", "Normal", "Fast", "Very Fast", "Super Fast"};
    /** The kinds in the order the cards come. */
    private static final List<String> ORDER = List.of("main", "spell", "lasting", "summon", "area", "other", "heal", "ultimate");

    /** A card's line: its label, a number (or {@code text} instead), what follows it, and its colour. */
    private record Line(String label, double value, String text, String suffix, int colour) {}

    /** One card: what matches it with the compared build's, its title, mana cost (0: none), lines, and whether greyed. */
    private record Entry(String key, String title, double cost, List<Line> lines, boolean grey) {}

    /** Mana: the water colour, dark enough for the parchment. Numbers: WynnBuilder's gold, darkened. Heals: green. */
    private static final int WATER = 0xFF1D6C88;
    private static final int GOLD = 0xFF9A5A00;
    private static final int HEAL = Theme.GOOD;

    /** The space between cards, and inside a card above its first line and below its last. */
    private static final int GAP = 4;
    private static final int PAD = 5;

    private final StatsSource source;
    private final ScrollBar bar = new ScrollBar();
    private int scroll;
    private int scrollMax;
    private int[] listRect;
    /** A line's height this frame: the font's and a pixel. */
    private int lineH = 10;

    HomeSpells(StatsSource source) {
        this.source = source;
    }

    boolean over(double x, double y) {
        int[] r = listRect;
        return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    /** Each card's top in the list this frame: the list scrolls a whole card at a time, so none hangs under the heading. */
    private int[] tops = new int[0];

    void scroll(double v) {
        int i = 0;
        while (i + 1 < tops.length && tops[i + 1] <= scroll) {
            i++;
        }
        i = Math.max(0, Math.min(tops.length - 1, i - (int) Math.signum(v)));
        scroll = tops.length == 0 ? 0 : Math.min(scrollMax, tops[i]);
    }

    /** {@code offset} moved back to the top of the card it falls in (the last one that leaves the rest in view at most). */
    private int snap(int offset) {
        int best = 0;
        for (int t : tops) {
            if (t <= offset && t <= scrollMax) {
                best = t;
            }
        }
        return best;
    }

    boolean barClicked(double mx, double my) {
        if (listRect != null && bar.mouseClicked(mx, my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    boolean barDragged(double my) {
        if (bar.mouseDragged(my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    void barReleased() {
        bar.mouseReleased();
    }

    /** A build's cards, from its sources; empty without them. */
    static List<Entry> entries(BuildStats.Side s) {
        List<Entry> out = new ArrayList<>();
        Damage.Breakdown b = s == null ? null : s.parts();
        if (b == null) {
            return out;
        }
        List<Sources.Source> list = new ArrayList<>(b.sources());
        list.sort(java.util.Comparator.comparingInt(src -> {
            int i = ORDER.indexOf(src.kind());
            return i < 0 ? ORDER.size() : i;
        }));
        for (Sources.Source src : list) {
            String key = src.kind() + ":" + src.name();
            List<Line> lines = new ArrayList<>();
            switch (src.kind()) {
                case "main" -> {
                    lines.add(new Line(L.t("wynnlabs.spells.avg_dps"), src.dps(), null, "", GOLD));
                    lines.add(new Line(L.t("wynnlabs.spells.attack_speed"), 0, SPEEDS[s.attackTier()], "", GOLD));
                    lines.add(new Line(L.t("wynnlabs.spells.per_attack"), src.perHit(), null, "", GOLD));
                    out.add(new Entry(key, L.t("wynnlabs.spells.melee"), 0, lines, false));
                }
                case "spell" -> {
                    // the model's own parts when it has them (Aura: Single Wave, First Wave, Total)
                    if (!parts(lines, src)) {
                        lines.add(new Line(L.t("wynnlabs.spells.per_cast"), src.perCast(), null, "", GOLD));
                    }
                    out.add(new Entry(key, src.name(), cost(b, src.name()), lines, false));
                }
                case "lasting" -> {
                    // Totem: Tick, Tick DPS per Totem, Tick DPS
                    if (!parts(lines, src)) {
                        lines.add(new Line(L.t("wynnlabs.spells.dps"), src.dps(), null, "", GOLD));
                    }
                    out.add(new Entry(key, src.name(), cost(b, src.name()), lines, false));
                }
                case "heal" -> {
                    // a heal every few seconds as a rate, a heal on a cast per cast
                    boolean rate = src.hitsPerSecond() > 0;
                    lines.add(new Line(L.t(rate ? "wynnlabs.spells.heal_rate" : "wynnlabs.spells.heal_cast"),
                            rate ? src.healPerSecond() : src.heal(), null, rate ? "/s" : "", HEAL));
                    out.add(new Entry(key, src.name(), 0, lines, false));
                }
                case "ultimate" -> {
                    // a grey line under the name: not in the totals; its number still readable
                    lines.add(new Line(L.t("wynnlabs.spells.ultimate"), Double.NaN, null, "", Theme.INK_3));
                    lines.add(new Line(L.t("wynnlabs.spells.dps"), src.dps(), null, "", Theme.INK_2));
                    out.add(new Entry(key, src.name(), 0, lines, true));
                }
                default -> {
                    // a summon or area of the tree; "other" is what the tree does not name
                    String name = src.kind().equals("other") ? L.t("wynnlabs.spells.other") : src.name();
                    // the card's title names it: "Total DPS" under it
                    lines.add(new Line(L.t("wynnlabs.spells.total_dps"), src.dps(), null, "", GOLD));
                    if (src.perHit() > 0) {
                        lines.add(new Line(L.t("wynnlabs.spells.per_hit"), src.perHit(), null,
                                src.count() > 1 ? " × " + Math.round(src.count()) : "", GOLD));
                    }
                    if (src.hitsPerSecond() > 0) {
                        lines.add(new Line(L.t("wynnlabs.spells.hits_per_second"), 0,
                                String.format(java.util.Locale.ROOT, "%.1f", src.hitsPerSecond()), "", GOLD));
                    }
                    parts(lines, src);
                    out.add(new Entry(key, name, 0, lines, false));
                }
            }
        }
        return out;
    }

    /** The source's own parts ({@link Sources.Part}) as lines; false when it has none. */
    private static boolean parts(List<Line> lines, Sources.Source src) {
        if (src.parts() == null || src.parts().isEmpty()) {
            return false;
        }
        for (Sources.Part p : src.parts()) {
            lines.add(new Line(p.name(), p.value(), null, "", GOLD));
        }
        return true;
    }

    /** A spell's mana cost from the model's spell parts (0: none known). */
    private static double cost(Damage.Breakdown b, String name) {
        for (Damage.SpellPart sp : b.spells()) {
            if (sp.name().equals(name)) {
                return sp.cost();
            }
        }
        return 0;
    }

    void draw(GuiGraphics g, HomeTab.Host host, int x, int y, int w, int h, boolean large, int mx, int my) {
        frame(UiFont.get(), large);
        card(g, x, y, w, h, 0, false);
        int ix = x + 5;
        int iw = w - 10;
        Art.icon(g, "wand", ix, y + 6, 8, Theme.INK_2);
        ink(g, Component.literal(fit(L.t("wynnlabs.spells.title"), iw - 11, true)).withStyle(UiFont::bold), ix + 11, y + 6, Theme.INK_2);
        listRect = null;
        StatsSource.Shown shown = source.shown();
        List<Entry> now = entries(shown.now());
        if (now.isEmpty()) {
            lines(g, L.t("wynnlabs.spells.none"), ix, y + 20, iw, y + h - 4, Theme.INK_3);
            return;
        }
        List<Entry> other = entries(shown.other());
        int top = y + 18;
        int bottom = y + h - 4;
        lineH = font.lineHeight + 2;
        int contentH = 0;
        tops = new int[now.size()];
        for (int i = 0; i < now.size(); i++) {
            tops[i] = contentH;
            contentH += height(now.get(i)) + GAP;
        }
        // the furthest scroll: the first card top from which the rest fits
        int view = bottom - top;
        scrollMax = 0;
        for (int t : tops) {
            if (contentH - GAP - t <= view) {
                scrollMax = t;
                break;
            }
        }
        if (bar.dragging()) {
            scroll = bar.offset();
        }
        scroll = snap(Math.min(scroll, scrollMax));
        listRect = new int[] {x, top, w, view};
        bar.place(x + w - ScrollBar.W - 1, top, view, view, contentH - GAP, scroll);
        int cw = bar.shown() ? iw - ScrollBar.W - 2 : iw;
        g.enableScissor(x, top, x + w, bottom);
        int cy = top - scroll;
        for (Entry e : now) {
            int eh = height(e);
            if (cy >= top && cy < bottom) {
                drawEntry(g, host, e, find(other, e), shown.flipped(), ix, cy, cw, top, bottom);
            }
            cy += eh + GAP;
        }
        g.disableScissor();
        bar.drawSlim(g, mx, my);
    }

    /** A card's height from its lines (the title and each number), with the padding above and below. */
    private int height(Entry e) {
        return 2 * PAD + (1 + e.lines().size()) * lineH - 1;
    }

    private static Entry find(List<Entry> list, Entry e) {
        for (Entry o : list) {
            if (o.key().equals(e.key())) {
                return o;
            }
        }
        return null;
    }

    /**
     * The compared card's line for {@code l}: the one with its label, so a spell with parts on one
     * side and Per Cast on the other, or a heal per cast against one a second, compares nothing
     * rather than another line. Null when it has none.
     */
    private static Line counterpart(Entry o, Line l) {
        if (o == null) {
            return null;
        }
        for (Line ol : o.lines()) {
            if (ol.label().equals(l.label())) {
                return ol;
            }
        }
        return null;
    }

    /**
     * One card, as WynnBuilder lays its spells out, every line centred: the name in bold with the
     * mana cost in brackets ("Totem (12 ◆)", the cost's change after it), then "Label: value" lines,
     * the value in gold (heals green) with its change in green or red.
     */
    private void drawEntry(GuiGraphics g, HomeTab.Host host, Entry e, Entry o, boolean flipped, int x, int y, int w, int top,
            int bottom) {
        int h = height(e);
        Kit.card(g, x, y, w, h, 0);
        int cx = x + w / 2;
        int ly = y + PAD;
        List<Object[]> title = new ArrayList<>();
        title.add(new Object[] {e.title(), e.grey() ? Theme.INK_3 : Theme.INK, true});
        if (e.cost() > 0) {
            title.add(new Object[] {" (" + HomeStats.format(e.cost(), 0, false), WATER, false});
            title.add(new Object[] {"@droplet", WATER, false});
            title.add(new Object[] {")", WATER, false});
            long d = o == null ? 0 : change(e.cost(), o.cost(), flipped);
            if (d != 0) {
                title.add(new Object[] {" (" + (d > 0 ? "+" : "") + HomeStats.format(d, 0, false) + ")", Theme.INK_3, false});
            }
        }
        centre(g, title, cx, ly, w - 6);
        for (int i = 0; i < e.lines().size(); i++) {
            ly += lineH;
            Line l = e.lines().get(i);
            Line ol = counterpart(o, l);
            numberLine(g, l, ol == null || l.text() != null ? Double.NaN : ol.value(), flipped, e.grey(), cx, ly, w - 6);
        }
        if (y >= top && y + h <= bottom) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(e.title()).withStyle(ChatFormatting.WHITE));
            for (int i = 0; i < e.lines().size(); i++) {
                Line l = e.lines().get(i);
                if (l.text() != null) {
                    tip.add(Component.literal(l.label() + ": " + l.text()).withStyle(ChatFormatting.GOLD));
                    continue;
                }
                if (Double.isNaN(l.value())) {
                    continue;                       // the ultimate's note: its tip line follows
                }
                Line ol = counterpart(o, l);
                tip.add(Component.literal(l.label() + ": " + HomeStats.format(l.value(), 0, false) + l.suffix()
                        + (ol == null ? "" : " → " + HomeStats.format(ol.value(), 0, false) + ol.suffix()))
                        .withStyle(l.colour() == HEAL ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            }
            if (e.cost() > 0) {
                tip.add(L.c("wynnlabs.spells.cost_tip", HomeStats.format(e.cost(), 0, false)
                        + (o == null ? "" : " → " + HomeStats.format(o.cost(), 0, false))).withStyle(ChatFormatting.AQUA));
            }
            if (e.grey()) {
                tip.add(L.c("wynnlabs.spells.ultimate_tip").withStyle(ChatFormatting.GRAY));
            }
            host.area(x, y, w, h, null, tip, null);
        }
    }

    /** The change {@code other} brings ({@code flipped}: the change from your gear, which {@code other} then is). */
    private static long change(double v, double other, boolean flipped) {
        if (Double.isNaN(other)) {
            return 0;
        }
        return flipped ? Math.round(v) - Math.round(other) : Math.round(other) - Math.round(v);
    }

    /** "Label: value" centred, its change after it; shortened to "12.3k" only when it would not fit. */
    private void numberLine(GuiGraphics g, Line l, double other, boolean flipped, boolean grey, int cx, int y, int room) {
        if (l.text() == null && Double.isNaN(l.value())) {
            // a note line ("Ultimate · while active"): the label alone
            List<Object[]> note = new ArrayList<>();
            note.add(new Object[] {l.label(), l.colour(), false});
            centre(g, note, cx, y, room);
            return;
        }
        long d = change(l.value(), other, flipped);
        for (boolean shortBig : new boolean[] {false, true}) {
            List<Object[]> segs = new ArrayList<>();
            segs.add(new Object[] {l.label() + ": ", grey ? Theme.INK_3 : Theme.INK, false});
            String v = l.text() != null ? l.text() : HomeStats.format(l.value(), 0, shortBig) + l.suffix();
            segs.add(new Object[] {v, l.colour(), false});
            if (d != 0) {
                // WynnBuilder's way: the change in brackets after the value
                segs.add(new Object[] {" (" + (d > 0 ? "+" : "") + HomeStats.format(d, 0, shortBig) + ")", d > 0 ? Theme.GOOD : Theme.BAD, false});
            }
            if (shortBig || widthOf(segs) <= room) {
                centre(g, segs, cx, y, room);
                return;
            }
        }
    }

    /** The width of a line's parts: text (bold when asked) and "@icon" sprites, 8px and a space. */
    private int widthOf(List<Object[]> segs) {
        int w = 0;
        for (Object[] s : segs) {
            String t = (String) s[0];
            w += t.startsWith("@") ? 9 : width(t, (Boolean) s[2]);
        }
        return w;
    }

    /** A line's parts centred on {@code cx}; the first part is cut with "…" when the line is too wide. */
    private void centre(GuiGraphics g, List<Object[]> segs, int cx, int y, int room) {
        int total = widthOf(segs);
        if (total > room) {
            Object[] first = segs.get(0);
            int rest = total - width((String) first[0], (Boolean) first[2]);
            segs.set(0, new Object[] {fit((String) first[0], Math.max(10, room - rest), (Boolean) first[2]), first[1], first[2]});
            total = widthOf(segs);
        }
        int x = cx - total / 2;
        for (Object[] s : segs) {
            String t = (String) s[0];
            int colour = (Integer) s[1];
            if (t.startsWith("@")) {
                // a pixel before it, none after, centred on the text's height
                Art.icon(g, t.substring(1), x + 1, y + Math.max(0, (font.lineHeight - 9) / 2), 8, colour);
                x += 9;
            } else if ((Boolean) s[2]) {
                ink(g, Component.literal(t).withStyle(UiFont::bold), x, y, colour);
                x += width(t, true);
            } else {
                ink(g, t, x, y, colour);
                x += width(t, false);
            }
        }
    }
}
