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
 * totals. Worded as WynnBuilder is (in the mod's language). With a build
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

    /** The space between two sources' blocks. */
    private static final int GAP = Theme.SP_4;

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

    /** The column's heading: the icon, the title, a hairline under them; returns the y under it. */
    int heading(GuiGraphics g, int x, int y, int w) {
        Art.icon(g, "wand", x, Type.LABEL.onCaps(y, 8), 8, Theme.INK_2);
        Type.LABEL.draw(g, font, Type.LABEL.fit(font, L.t("wynnlabs.spells.title"), w - 8 - Theme.SP_2), x + 8 + Theme.SP_2, y);
        int ry = y + Type.LABEL.lineHeight + Theme.SP_2;
        Kit.hair(g, x, ry, w, 1, Theme.LINE);
        return ry + 1 + Theme.SP_4;
    }

    void draw(GuiGraphics g, HomeTab.Host host, int x, int y, int w, int h, boolean large, int mx, int my) {
        draw(g, host, x, y, w, h, large, true, mx, my);
    }

    /**
     * The spells under their heading, no frame: per source its name (the mana cost at the right),
     * then its numbers as rows, the label on the left and the value right-aligned; {@code headed}
     * false when a tab above already names the column.
     */
    void draw(GuiGraphics g, HomeTab.Host host, int x, int y, int w, int h, boolean large, boolean headed, int mx, int my) {
        frame(UiFont.get(), large);
        int top = headed ? heading(g, x, y, w) : y;
        listRect = null;
        StatsSource.Shown shown = source.shown();
        List<Entry> now = entries(shown.now());
        if (now.isEmpty()) {
            Type.MUTED.drawWrapped(g, font, L.t("wynnlabs.spells.none"), x, top, w, 3, Theme.INK_3);
            return;
        }
        List<Entry> other = entries(shown.other());
        int bottom = y + h;
        int contentH = 0;
        tops = new int[now.size()];
        for (int i = 0; i < now.size(); i++) {
            tops[i] = contentH;
            contentH += height(now.get(i)) + GAP;
        }
        // the furthest scroll: the first block top from which the rest fits
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
        bar.place(x + w + Theme.SP_1, top, view, view, contentH - GAP, scroll);
        g.enableScissor(x - 1, top, x + w + Theme.SP_1, bottom);
        int cy = top - scroll;
        for (Entry e : now) {
            int eh = height(e);
            if (cy >= top && cy < bottom) {
                drawEntry(g, host, e, find(other, e), shown.flipped(), x, cy, w, top, bottom);
            }
            cy += eh + GAP;
        }
        g.disableScissor();
        bar.drawSlim(g, mx, my);
    }

    /** A source's height: its name, then a row per number. */
    private int height(Entry e) {
        return Type.LABEL.lineHeight + e.lines().size() * Type.BODY.lineHeight;
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
     * The compared source's line for {@code l}: the one with its label, so a spell with parts on one
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
     * One source: its name in bold with the mana cost at the row's end (a drop, the cost, its
     * change), then its rows: the label on the left, the value right-aligned in gold (heals green)
     * with its change after it in green or red.
     */
    private void drawEntry(GuiGraphics g, HomeTab.Host host, Entry e, Entry o, boolean flipped, int x, int y, int w, int top,
            int bottom) {
        int h = height(e);
        int right = x + w;
        if (e.cost() > 0) {
            String cost = HomeStats.format(e.cost(), 0, false);
            long d = o == null ? 0 : change(e.cost(), o.cost(), flipped);
            String dv = d == 0 ? "" : " (" + (d > 0 ? "+" : "") + HomeStats.format(d, 0, false) + ")";
            int cw = Type.META.width(font, cost + dv);
            Type.META.draw(g, font, cost + dv, right - cw, Type.META.yIn(y, Type.LABEL.lineHeight), WATER);
            Art.icon(g, "droplet", right - cw - Theme.SP_1 - 7, Type.LABEL.onCaps(y, 7), 7, WATER);
            right -= cw + Theme.SP_1 + 7 + Theme.SP_3;
        }
        Type.LABEL.draw(g, font, Type.LABEL.fit(font, e.title(), right - x), x, y, e.grey() ? Theme.INK_3 : Theme.INK);
        int ly = y + Type.LABEL.lineHeight;
        for (Line l : e.lines()) {
            Line ol = counterpart(o, l);
            numberLine(g, l, ol == null || l.text() != null ? Double.NaN : ol.value(), flipped, e.grey(), x + Theme.SP_2, ly,
                    w - Theme.SP_2);
            ly += Type.BODY.lineHeight;
        }
        if (y >= top && y + h <= bottom) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(e.title()).withStyle(ChatFormatting.WHITE));
            for (Line l : e.lines()) {
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

    /** A number's row: its label on the left, the value right-aligned with its change after it; "12.3k" only when it would not fit. */
    private void numberLine(GuiGraphics g, Line l, double other, boolean flipped, boolean grey, int x, int y, int w) {
        if (l.text() == null && Double.isNaN(l.value())) {
            // a note line ("Ultimate · while active"): the label alone
            Type.MUTED.draw(g, font, Type.MUTED.fit(font, l.label(), w), x, y, l.colour());
            return;
        }
        long d = change(l.value(), other, flipped);
        for (boolean shortBig : new boolean[] {false, true}) {
            String v = l.text() != null ? l.text() : HomeStats.format(l.value(), 0, shortBig) + l.suffix();
            String dv = d == 0 ? "" : " (" + (d > 0 ? "+" : "") + HomeStats.format(d, 0, shortBig) + ")";
            int vw = Type.BODY.width(font, v) + (dv.isEmpty() ? 0 : Type.META.width(font, dv));
            if (!shortBig && vw > w / 2) {
                continue;
            }
            int vx = x + w - vw;
            Type.BODY.draw(g, font, v, vx, y, grey ? Theme.INK_3 : l.colour());
            if (!dv.isEmpty()) {
                Type.META.draw(g, font, dv, vx + Type.BODY.width(font, v), y + 1, d > 0 ? Theme.GOOD : Theme.BAD);
            }
            Type.MUTED.draw(g, font, Type.MUTED.fit(font, l.label(), vx - Theme.SP_3 - x), x, y, grey ? Theme.INK_3 : Theme.INK_2);
            return;
        }
    }
}
