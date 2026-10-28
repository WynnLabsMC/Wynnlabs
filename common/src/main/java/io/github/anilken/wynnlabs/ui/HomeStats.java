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

    /** The compact column's tab: the spells instead of the stats. */
    boolean spellsTab;

    void draw(GuiGraphics g, HomeTab.Host host, int x, int y, int w, int h, boolean large, int mx, int my) {
        draw(g, host, x, y, w, h, large, false, mx, my);
    }

    /**
     * The list under its heading, no frame: the title (or, {@code tabs}, the Stats and Spells tabs of
     * the compact column) with the compare controls right-aligned and a hairline under them, the
     * warning on an amber card, then a row per stat under small group headings: the label on the
     * left, the value right-aligned (red below zero) with its change; the wheel scrolls it.
     */
    void draw(GuiGraphics g, HomeTab.Host host, int x, int y, int w, int h, boolean large, boolean tabs, int mx, int my) {
        frame(UiFont.get(), large);
        rowH = Type.BODY.lineHeight - 1;
        Build.Plan plan = Build.plan();
        StatsSource.Shown shown = source.shown();
        BuildStats.Side now = shown.now();
        listRect = null;
        // the heading row: the controls first (right-aligned), the heading fitted to what is left
        int right = x + w;
        int cy = y;
        if (source.picked()) {
            int bw = Ui.CHIP_H + 2;
            Ui.iconButton(g, "x", right - bw, cy, false, mx, my);
            host.area(right - bw, cy, bw, Ui.CHIP_H, null, List.of(L.c("wynnlabs.stats.clear_tip").withStyle(ChatFormatting.GRAY)),
                    "#qstatsclear:");
            right -= bw + Theme.SP_2;
        }
        int headLeft;
        if (tabs) {
            int sx = x;
            String st = L.t("wynnlabs.stats.title");
            String sp = L.t("wynnlabs.spells.title");
            int w1 = Ui.chipWidth(font, st);
            int w2 = Ui.chipWidth(font, sp);
            // the picker needs two icon buttons at least: too narrow for the names, the tabs as icons
            boolean named = sx + w1 + Theme.SP_2 + w2 + Theme.SP_4 <= right - 2 * (Ui.CHIP_H + 2) - Theme.SP_2;
            if (named) {
                Ui.chip(g, font, st, sx, cy, w1, !spellsTab, false, false, mx, my);
                Ui.chip(g, font, sp, sx + w1 + Theme.SP_2, cy, w2, spellsTab, false, false, mx, my);
            } else {
                w1 = Ui.CHIP_H + 2;
                w2 = Ui.CHIP_H + 2;
                Ui.iconButton(g, "list-check", sx, cy, !spellsTab, mx, my);
                Ui.iconButton(g, "wand", sx + w1 + Theme.SP_2, cy, spellsTab, mx, my);
            }
            host.area(sx, cy, w1, Ui.CHIP_H, null, List.of(Component.literal(st).withStyle(ChatFormatting.WHITE)), "#qstatstab:stats");
            host.area(sx + w1 + Theme.SP_2, cy, w2, Ui.CHIP_H, null, List.of(Component.literal(sp).withStyle(ChatFormatting.WHITE)),
                    "#qstatstab:spells");
            headLeft = sx + w1 + Theme.SP_2 + w2 + Theme.SP_4;
        } else {
            String title = L.t("wynnlabs.stats.title");
            int tw = Math.min(Type.LABEL.width(font, title), (right - x) / 3);
            headLeft = x + 8 + Theme.SP_2 + tw + Theme.SP_4;
        }
        if (now != null) {
            picker.frame(font, large);
            picker.draw(g, host, shown, right, cy, headLeft, mx, my);
        }
        if (!tabs) {
            int ty = Type.LABEL.yIn(cy, Ui.CHIP_H);
            Art.icon(g, "list-check", x, Type.LABEL.onCaps(ty, 8), 8, Theme.INK_2);
            int room = Math.min(right, now == null ? right : picker.left()) - Theme.SP_3 - (x + 8 + Theme.SP_2);
            Type.LABEL.draw(g, font, Type.LABEL.fit(font, L.t("wynnlabs.stats.title"), room), x + 8 + Theme.SP_2, ty);
        }
        int ry = cy + Ui.CHIP_H + Theme.SP_2;
        Kit.hair(g, x, ry, w, 1, Theme.LINE);
        int top = ry + 1 + Theme.SP_4;
        int bottom = y + h;
        if (tabs && spellsTab) {
            spells.draw(g, host, x, top, w, bottom - top, large, false, mx, my);
            return;                             // the open compare list: the home's popup, drawn over all
        }
        if (now == null) {
            Type.MUTED.drawWrapped(g, font, L.t("wynnlabs.stats.none"), x, top, w, 3, Theme.INK_3);
            return;
        }
        BuildStats.Side other = shown.other();
        String otherName = shown.name();
        // your gear and the other build, whichever the main numbers are
        BuildStats.Side worn = shown.flipped() ? other : now;
        BuildStats.Side build = shown.flipped() ? now : other;
        // the warning: what could get you killed, on your gear first, then the plan's
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
            // at most half the column; the notice keeps whole lines
            int sh = Ui.notice(g, font, Ui.Level.WARNING, warns, null, x, top, w, (bottom - top) / 2, -1, -1, new int[1][]);
            host.area(x, top, w, sh, null, warns.stream().map(s -> (Component) Component.literal(s)
                    .withStyle(ChatFormatting.GOLD)).toList(), null);
            top += sh + Theme.SP_4;
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
        int headH = Type.LABEL.lineHeight + Theme.SP_1;
        int contentH = 0;
        boolean first = true;
        for (Object[] o : out) {
            contentH += o[0] instanceof String ? (first ? 0 : Theme.SP_4) + headH : rowH;
            first = false;
        }
        scrollMax = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, scrollMax);
        listRect = new int[] {x, top, w, bottom - top};
        // the scrollbar just outside the column when the lines do not fit
        bar.place(x + w + Theme.SP_1, top, bottom - top, bottom - top, contentH, scroll);
        int lw = w;
        g.enableScissor(x - 1, top, x + w + Theme.SP_1, bottom);
        int ly = top - scroll;
        first = true;
        for (Object[] o : out) {
            if (o[0] instanceof String title) {
                ly += first ? 0 : Theme.SP_4;
                if (ly >= top - headH && ly < bottom) {
                    Type.LABEL.draw(g, font, Type.LABEL.fit(font, L.t(title), lw), x, ly, Theme.INK);
                }
                ly += headH;
                first = false;
                continue;
            }
            first = false;
            Row r = (Row) o[0];
            if (ly >= top - 1 && ly < bottom) {
                // never a line half under the heading: those above the list's top are left out
                line(g, host, r, now, other, shown.flipped(), x, ly, lw, top, bottom);
            }
            ly += rowH;
        }
        g.disableScissor();
        bar.draw(g, mx, my);
    }

    /** One stat's row: its label (after its element's mark), Now's value right-aligned and the difference to the other build. */
    private void line(GuiGraphics g, HomeTab.Host host, Row r, BuildStats.Side now, BuildStats.Side other, boolean flipped,
            int x, int y, int w, int top, int bottom) {
        double a = r.value().applyAsDouble(now);
        String av = format(a, r.kind(), true);
        int vx = x + w;
        long d = 0;
        double b = a;
        if (other != null) {
            b = r.value().applyAsDouble(other);
            // the other build's change ("Now"), or the change from your gear ("New build")
            d = flipped ? Math.round(a) - Math.round(b) : Math.round(b) - Math.round(a);
        }
        if (d != 0) {
            boolean better = r.lessBetter() ? d < 0 : d > 0;
            String dv = (d > 0 ? "+" : "") + format(d, r.kind(), true);
            vx -= Type.META.width(font, dv);
            Type.META.draw(g, font, dv, vx, y + 1, better ? Theme.GOOD : Theme.BAD);
            vx -= Theme.SP_2;
        }
        vx -= Type.BODY.width(font, av);
        // an identification green above zero and red below (the other way round when less is
        // better), as WynnBuilder colours them; a worked-out total plain unless below zero
        long n = Math.round(a);
        int vc = n == 0 ? Theme.INK : r.signed() ? (n > 0) != r.lessBetter() ? Theme.GOOD : Theme.BAD : n < 0 ? Theme.BAD : Theme.INK;
        Type.BODY.draw(g, font, av, vx, y, vc);
        // the label after its element's glyph (or health's heart), the glyph in that colour
        int lx = x;
        if (r.mark() > 0) {
            Art.icon(g, ICON[r.mark()], lx, Type.BODY.onCaps(y, 8), 8, TINT[r.mark()]);
            lx += 8 + Theme.SP_2;
        }
        Type.BODY.draw(g, font, Type.BODY.fit(font, r.text(), vx - lx - Theme.SP_3), lx, y, Theme.INK);
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
            host.area(x, y, w, rowH, null, tip, null);
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
        String out = (n < 0 ? "−" : "") + s;
        return kind == PCT ? L.pct(out) : out;
    }
}
