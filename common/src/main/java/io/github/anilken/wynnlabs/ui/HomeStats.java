package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.BuildStats;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The Home's stats list in its right column, like WynnBuilder's detailed stats: a line per number
 * of the build you wear ("Now"), with the applied plan's after an arrow, or a build the window
 * found ("Detailed" on its card), in groups (survival, defences, mana, damage, the rest). A
 * plan's value is green where it is better, red where worse. Above them a warning strip for what
 * gets you killed: health regen or life steal below zero, an element's defence far below zero.
 * The numbers come from {@link BuildStats}; {@link HomeTab} lays it out. Only reads.
 */
final class HomeStats extends HomePart {

    /** How a value reads: a whole number, or a % after it. */
    private static final int NUM = 0;
    private static final int PCT = 1;

    /** A label's mark: none, an element (1 earth … 5 air, as {@link io.github.anilken.wynnlabs.build.Damage#ELEMENTS}), or health. */
    private static final int HEART = 6;
    /**
     * The marks' icons and colours: the mod's sprites (its font has no ✤ ✦ ❉ ✹ ❋), in the elements'
     * colours darkened for the parchment (gold for thunder, grey for air).
     */
    static final String[] ICON = {"", "leaf", "bolt", "droplet", "flame", "circles", "heart"};
    static final int[] TINT = {Theme.INK, 0xFF3D6E17, 0xFF8A6A00, 0xFF1D6C88, 0xFFA8321A, 0xFF5E5E5E, 0xFFB0281E};
    private static final String[] NAMES = {"", "Earth", "Thunder", "Water", "Fire", "Air"};
    private static final String[] KEYS = {"", "earth", "thunder", "water", "fire", "air"};

    /**
     * One row: its label (a text key, or a stat's English name as is), its value, its kind, whether
     * less is better, whether its value is an identification (green above zero, red below, the
     * other way round when less is better, as WynnBuilder shows them), and its label's mark.
     */
    private record Row(String label, ToDoubleFunction<BuildStats.Side> value, int kind, boolean lessBetter, boolean signed,
            int mark) {

        String text() {
            return label.startsWith("wynnlabs" + ".") ? L.t(label) : label;     // not one literal: checkLang would take it for a prefix
        }
    }

    private record Group(String title, List<Row> rows) {}

    /** A number the model works out (health, DPS): no sign colour. */
    private static Row total(String label, ToDoubleFunction<BuildStats.Side> value, int kind, int mark) {
        return new Row(label, value, kind, false, false, mark);
    }

    private static Row id(String label, String key, int kind) {
        return id(label, key, kind, 0);
    }

    private static Row id(String label, String key, int kind, int mark) {
        return new Row(label, s -> s.stat(key), kind, false, true, mark);
    }

    /** The item data keeps a cost cut as a positive number: shown as WynnBuilder does, a cut below zero (green). */
    private static Row cost(String nth) {
        return new Row(nth + " Spell Cost", s -> -s.stat("raw" + nth + "SpellCost"), NUM, true, true, 0);
    }

    private static Row costPct(String nth) {
        return new Row(nth + " Spell Cost %", s -> -s.stat(nth + "SpellCost"), PCT, true, true, 0);
    }

    /** Five rows, one per element: {@code key} with the element's name in place of "%s" ("%sDamage"). */
    private static List<Row> elements(String label, String key, int kind) {
        List<Row> out = new ArrayList<>();
        for (int i = 1; i < KEYS.length; i++) {
            String k = String.format(Locale.ROOT, key, KEYS[i]);
            String cap = String.format(Locale.ROOT, key, NAMES[i]);
            out.add(id(String.format(Locale.ROOT, label, NAMES[i]), k.startsWith("raw") ? "raw" + cap.substring(3) : k, kind, i));
        }
        return out;
    }

    /**
     * Each element's defence as WynnBuilder shows it (the pieces' defences raised by the element's
     * and the all-element %), and the element's own %.
     */
    private static List<Row> defences() {
        List<Row> out = new ArrayList<>();
        for (int i = 1; i < KEYS.length; i++) {
            String el = KEYS[i];
            out.add(new Row(NAMES[i] + " Defence", s -> s.defenceTotal(el), NUM, false, true, i));
            out.add(id(NAMES[i] + " Defence %", el + "Defence", PCT, i));
        }
        return out;
    }

    private static List<Row> join(List<Row> a, List<Row> b, List<Row> c) {
        List<Row> out = new ArrayList<>(a);
        out.addAll(b);
        out.addAll(c);
        return out;
    }

    private static final List<Group> GROUPS = List.of(
        new Group("wynnlabs.stats.g_survival", List.of(
            total("wynnlabs.stats.health", s -> s.result().health(), NUM, HEART),
            total("wynnlabs.stats.ehp", s -> s.result().ehp(), NUM, 0),
            total("wynnlabs.stats.ehp_elements", s -> s.result().ehpElemental(), NUM, 0),
            total("wynnlabs.stats.ehp_no_agility", BuildStats.Side::ehpNoAgility, NUM, 0),
            id("Health Regen", "healthRegenRaw", NUM, HEART),
            id("Health Regen %", "healthRegen", PCT, HEART),
            new Row("wynnlabs.stats.regen_total", BuildStats.Side::regen, NUM, false, true, HEART),
            new Row("wynnlabs.stats.regen_effective", BuildStats.Side::effectiveRegen, NUM, false, true, HEART),
            id("Life Steal", "lifeSteal", NUM),
            id("Heal Effectiveness %", "healingEfficiency", PCT))),
        new Group("wynnlabs.stats.g_defence", join(defences(), List.of(id("Elemental Defence %", "elementalDefence", PCT)), List.of())),
        new Group("wynnlabs.stats.g_mana", List.of(
            id("Mana Regen", "manaRegen", NUM),
            id("Mana Steal", "manaSteal", NUM),
            total("wynnlabs.stats.mana_total", BuildStats.Side::mana, NUM, 0))),
        new Group("wynnlabs.stats.g_damage", join(List.of(
            id("Spell Damage", "rawSpellDamage", NUM),
            id("Spell Damage %", "spellDamage", PCT),
            id("Elemental Spell Damage", "rawElementalSpellDamage", NUM),
            id("Elemental Spell Damage %", "elementalSpellDamage", PCT),
            id("Main Attack Damage", "rawMainAttackDamage", NUM),
            id("Main Attack Damage %", "mainAttackDamage", PCT),
            id("Damage %", "damage", PCT),
            id("Elemental Damage %", "elementalDamage", PCT)),
            join(elements("%s Damage %%", "%sDamage", PCT), elements("%s Spell Damage", "raw%sSpellDamage", NUM),
                    elements("%s Spell Damage %%", "%sSpellDamage", PCT)),
            List.of(total("wynnlabs.stats.spell_dps", s -> s.result().spellDps(), NUM, 0),
                    total("wynnlabs.stats.melee_dps", s -> s.result().mainDps(), NUM, 0)))),
        new Group("wynnlabs.stats.g_other", List.of(
            cost("1st"), costPct("1st"), cost("2nd"), costPct("2nd"), cost("3rd"), costPct("3rd"), cost("4th"), costPct("4th"),
            id("Attack Speed", "rawAttackSpeed", NUM),
            new Row("Walk Speed", s -> s.result().speed(), PCT, false, true, 0),
            id("Sprint Bonus %", "sprint", PCT),
            id("Jump Height", "jumpHeight", NUM),
            new Row("XP Bonus", s -> s.result().xp(), PCT, false, true, 0),
            id("Loot Bonus %", "lootBonus", PCT),
            id("Reflection %", "reflection", PCT),
            id("Thorns %", "thorns", PCT),
            id("Stealing %", "stealing", PCT))));

    /** A line's height: the font's, a pixel between lines, at least 10 (set each frame). */
    private int rowH = 10;

    /** The list's scrollbar, when the lines do not fit (offsets in pixels). */
    private final ScrollBar bar = new ScrollBar();

    /** What is shown and compared, shared with the spell panel. */
    final StatsSource source = new StatsSource();
    /** The spell panel of the Home's left column. */
    final HomeSpells spells = new HomeSpells(source);
    /** The header's compare button, its list and the view toggle. */
    final StatsPicker picker = new StatsPicker(source);
    private int scroll;
    private int scrollMax;
    /** The list's area last frame, for the wheel. */
    private int[] listRect;

    /** "Detailed" on a found build's card: that build in the Plan column. */
    void pick(Roadmap.Candidate c, int level, int rank) {
        source.pick(c, level, rank);
        scroll = 0;
    }

    /** The ✕ by a found build's name: the applied plan again. */
    void clearPick() {
        source.clearPick();
    }

    /** Whether the wheel at (x, y) is over the list or the spell panel. */
    boolean over(double x, double y) {
        int[] r = listRect;
        return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3] || spells.over(x, y);
    }

    /** The wheel at (x, y): the spell panel under it, else the list. */
    void scroll(double x, double y, double v) {
        if (spells.over(x, y)) {
            spells.scroll(v);
            return;
        }
        scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.signum(v) * rowH * 2));
    }

    /** A press on a scrollbar (the list's or the spell panel's): a drag on its thumb, a page on its track. */
    boolean barClicked(double mx, double my) {
        if (listRect != null && bar.mouseClicked(mx, my)) {
            scroll = bar.offset();
            return true;
        }
        return spells.barClicked(mx, my);
    }

    /** A thumb follows the mouse while dragged; true while one is. */
    boolean barDragged(double my) {
        if (bar.mouseDragged(my)) {
            scroll = bar.offset();
            return true;
        }
        return spells.barDragged(my);
    }

    void barReleased() {
        bar.mouseReleased();
        spells.barReleased();
    }

    /**
     * The list in a card: the title with what the values compare, the warning strip, then a line
     * per stat ("label ···· now +diff") under small group headings; the wheel scrolls it.
     */
    void draw(GuiGraphics g, HomeTab.Host host, int x, int y, int w, int h, boolean large, int mx, int my) {
        frame(UiFont.get(), large);
        rowH = Math.max(10, font.lineHeight + 1);
        card(g, x, y, w, h, 0, false);
        Build.Plan plan = Build.plan();
        StatsSource.Shown shown = source.shown();
        BuildStats.Side now = shown.now();
        int ix = x + 5;
        int iw = w - 10;
        Art.icon(g, "list-check", ix, y + 6, 8, Theme.INK_2);
        ink(g, Component.literal(fit(L.t("wynnlabs.stats.title"), iw / 2, true)).withStyle(UiFont::bold), ix + 11, y + 6, Theme.INK_2);
        listRect = null;
        if (now == null) {
            lines(g, L.t("wynnlabs.stats.none"), ix, y + 20, iw, y + h - 4, Theme.INK_3);
            return;
        }
        // what the values are compared with (StatsSource)
        BuildStats.Side other = shown.other();
        String otherName = shown.name();
        // the title's right: what the differences are against, a ✕ back to the plan for a found build
        int right = ix + iw;
        if (source.picked()) {
            boolean hov = mx >= right - 9 && mx < right + 1 && my >= y + 5 && my < y + 15;
            Art.icon(g, "x", right - 8, y + 6, 8, hov ? Theme.BAD : Theme.INK_3);
            host.area(right - 9, y + 5, 10, 10, null, List.of(L.c("wynnlabs.stats.clear_tip").withStyle(ChatFormatting.GRAY)),
                    "#qstatsclear:");
            right -= 12;
        }
        picker.frame(font, large);
        picker.draw(g, host, shown, right, y + 4, ix + 11 + Math.min(width(L.t("wynnlabs.stats.title"), true), iw / 3) + 4, mx, my);
        int top = y + 18;
        // your gear and the other build, whichever the main numbers are
        BuildStats.Side worn = shown.flipped() ? other : now;
        BuildStats.Side build = shown.flipped() ? now : other;
        int bottom = y + h - 4;
        // the warning strip: what could get you killed, on your gear first, then the plan's
        List<String> warns = new ArrayList<>();
        warn(warns, worn, null);
        if (build != null && build.notWearable() != null) {
            // worked out as the build would be at its own level and points: say so first
            warns.add(0, L.t("wynnlabs.stats.not_wearable", build.level(), build.notWearable()));
        } else if (build != null && build.level() > 0 && plan != null && build.level() != plan.level()) {
            warns.add(0, L.t("wynnlabs.stats.at_level", build.level()));
        }
        if (build != null) {
            warn(warns, build, otherName);
        }
        if (!warns.isEmpty()) {
            int n = 0;
            for (String s : warns) {
                n += font.split(Component.literal(s), iw - 13).size();
            }
            int sh = Math.min(n * 10 + 5, (bottom - top) / 2);     // lines() steps 10 a line
            g.fill(ix - 1, top, ix + iw + 1, top + sh, 0x40E8A040);
            Kit.outline(g, ix - 1, top, iw + 2, sh, Theme.WARN);
            int ly = top + 2;
            for (String s : warns) {
                Art.icon(g, "alert-triangle", ix + 1, ly, 8, Theme.WARN);
                ly = lines(g, s, ix + 11, ly, iw - 13, top + sh - 1, Theme.WARN);
            }
            host.area(ix - 1, top, iw + 2, sh, null, warns.stream().map(s -> (Component) Component.literal(s)
                    .withStyle(ChatFormatting.GOLD)).toList(), null);
            top += sh + 3;
        }
        // the lines: a heading per group with any line, the lines with a value on either side
        List<Object[]> out = new ArrayList<>();        // {heading} or {row}
        for (Group gr : GROUPS) {
            boolean head = false;
            for (Row r : gr.rows()) {
                double a = r.value().applyAsDouble(now);
                double b = other == null ? 0 : r.value().applyAsDouble(other);
                if (Math.round(a) == 0 && Math.round(b) == 0) {
                    continue;
                }
                if (!head) {
                    out.add(new Object[] {gr.title()});
                    head = true;
                }
                out.add(new Object[] {r});
            }
        }
        int contentH = 0;
        for (Object[] o : out) {
            contentH += o[0] instanceof String ? rowH + 3 : rowH;
        }
        scrollMax = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, scrollMax);
        listRect = new int[] {x, top, w, bottom - top};
        // the scrollbar on the right edge when the lines do not fit; the lines make room for it
        bar.place(x + w - ScrollBar.W - 1, top, bottom - top, bottom - top, contentH, scroll);
        int lw = bar.shown() ? iw - ScrollBar.W - 2 : iw;
        g.enableScissor(x, top, x + w, bottom);
        int ly = top - scroll;
        int stripe = 0;
        for (Object[] o : out) {
            if (o[0] instanceof String title) {
                ly += 3;
                if (ly >= top && ly < bottom) {
                    ink(g, Component.literal(fit(L.t(title), lw, true)).withStyle(UiFont::bold), ix, ly, Theme.INK_2);
                    g.fill(ix + width(L.t(title), true) + 4, ly + 4, ix + lw, ly + 5, Theme.LINE);
                }
                ly += rowH;
                stripe = 0;
                continue;
            }
            Row r = (Row) o[0];
            if (ly >= top - 1 && ly < bottom) {
                // never a line half under the heading: those above the list's top are left out
                line(g, host, r, now, other, shown.flipped(), ix, ly, lw, stripe, top, bottom);
            }
            ly += rowH;
            stripe++;
        }
        g.disableScissor();
        bar.drawSlim(g, mx, my);
        picker.drawList(g, host, mx, my);
    }

    /** One stat's line: its label, a dotted leader, Now's value and the difference to the other build, in green or red. */
    private void line(GuiGraphics g, HomeTab.Host host, Row r, BuildStats.Side now, BuildStats.Side other, boolean flipped,
            int x, int y, int w, int stripe, int top, int bottom) {
        Kit.row(g, x - 2, y - 1, w + 4, rowH, stripe, false);
        double a = r.value().applyAsDouble(now);
        String av = format(a, r.kind(), true);
        int vx = x + w;
        int colour = Theme.INK;
        long d = 0;
        double b = a;
        if (other != null) {
            b = r.value().applyAsDouble(other);
            // the other build's change ("Now"), or the change from your gear ("New build")
            d = flipped ? Math.round(a) - Math.round(b) : Math.round(b) - Math.round(a);
        }
        if (d != 0) {
            boolean better = r.lessBetter() ? d < 0 : d > 0;
            colour = better ? Theme.GOOD : Theme.BAD;
            String dv = (d > 0 ? "+" : "") + format(d, r.kind(), true);
            vx -= width(dv, false);
            ink(g, dv, vx, y, colour);
            vx -= 4;
        }
        vx -= width(av, false);
        // an identification green above zero and red below (the other way round when less is
        // better), as WynnBuilder colours them; a worked-out total plain unless below zero
        long n = Math.round(a);
        int vc = n == 0 ? Theme.INK : r.signed() ? (n > 0) != r.lessBetter() ? Theme.GOOD : Theme.BAD : n < 0 ? Theme.BAD : Theme.INK;
        ink(g, av, vx, y, vc);
        // the label after its element's glyph (or health's heart), in that colour
        int lx = x;
        if (r.mark() > 0) {
            Art.icon(g, ICON[r.mark()], lx, y, 8, TINT[r.mark()]);
            lx += 10;
        }
        String label = fit(r.text(), vx - lx - 6);
        if (r.mark() > 0) {
            ink(g, label, lx, y, TINT[r.mark()]);
        } else {
            ink(g, label, lx, y, Theme.INK);
        }
        // the leader between them, a dot every 3px
        for (int dx = lx + width(label, false) + 3; dx < vx - 3; dx += 3) {
            g.fill(dx, y + 7, dx + 1, y + 8, Theme.LINE);
        }
        if (y >= top && y + rowH <= bottom && !picker.open()) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(r.text()).withStyle(ChatFormatting.WHITE));
            // your value and the build's, whichever is the main one
            double mine = flipped ? b : a;
            double theirs = flipped ? a : b;
            tip.add(L.c("wynnlabs.stats.tip_now", format(mine, r.kind(), false)).withStyle(ChatFormatting.GRAY));
            if (other != null) {
                tip.add(L.c("wynnlabs.stats.tip_other", format(theirs, r.kind(), false)).withStyle(ChatFormatting.LIGHT_PURPLE));
                tip.add(L.c("wynnlabs.stats.tip_diff", (d > 0 ? "+" : "") + format(d, r.kind(), false))
                        .withStyle(d == 0 ? ChatFormatting.GRAY : (r.lessBetter() ? d < 0 : d > 0) ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            host.area(x, y - 1, w, rowH, null, tip, null);
        }
    }

    /** The warnings of one build as lines, {@code who} before them when it is the plan's. */
    private static void warn(List<String> out, BuildStats.Side s, String who) {
        for (BuildStats.Warn wn : s.warnings()) {
            String v = format(wn.value(), wn.kind().equals("defence") ? PCT : NUM, false);
            String line = switch (wn.kind()) {
                case "regen" -> L.t("wynnlabs.stats.warn_regen", v);
                case "steal" -> L.t("wynnlabs.stats.warn_steal", v);
                default -> L.t("wynnlabs.stats.warn_defence", nice(wn.element()), v);
            };
            out.add(who == null ? line : who + " · " + line);
        }
    }

    private static String nice(String el) {
        return Character.toUpperCase(el.charAt(0)) + el.substring(1);
    }

    /** "1,234", "−45", "35%"; {@code shortBig}: 10,000 and up as "12.3k", for the narrow column. */
    static String format(double v, int kind, boolean shortBig) {
        long n = Math.round(v);
        long abs = Math.abs(n);
        String s = shortBig && abs >= 10_000 ? String.format(Locale.ROOT, "%.1fk", abs / 1000.0)
                : String.format(Locale.ROOT, "%,d", abs);
        return (n < 0 ? "−" : "") + s + (kind == PCT ? "%" : "");
    }
}
