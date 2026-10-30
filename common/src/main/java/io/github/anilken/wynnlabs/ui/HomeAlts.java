package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.WynnBuilderExport;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The endgame milestones' other builds on Home ({@link Roadmap.Step#alternatives}): the "Build 1/N"
 * chip in the Gear header, its menu of cards (label, gap to the best, spell DPS, EHP, heal/s, what
 * you own and what the rest costs, the guild tome warning, an export button each) and the card
 * picked per milestone, which the gear rows, the bar and the Stats panel then show. The picks last
 * while the menu stays open and reset when the map is built again. A popup in the menu's sense:
 * drawn last, it owns the mouse while open. A class of its own made on first use (safe to hot swap in).
 */
final class HomeAlts {

    /** The UiExport place of the Gear header's ↗ and the prefix of each card's. */
    static final String GEAR = "gear";
    static final String CARD = "alt:";
    /** The menu's width: the large layout's and the compact one's. */
    private static final int W_LARGE = 248;
    private static final int W_SMALL = 224;

    private final RoadmapScreen menu;
    /** The map the picks belong to (another one: they reset), and the card picked per milestone. */
    private List<Roadmap.Step> of;
    private final Map<Integer, Integer> picked = new HashMap<>();
    /** The last view handed out, kept while the map, the milestone and the card stay. */
    private List<Roadmap.Step> view;
    private int viewAt = -1;
    private int viewK = -1;
    /** The cards' labels for the milestone {@link #labelsAt}, told apart when they share one. */
    private List<String> labels;
    private int labelsAt = -1;
    /** The menu is open; its chip's box; its scroll in pixels and its bar; drawn compact. */
    private boolean open;
    private int[] at;
    private int scroll;
    private final ScrollBar bar = new ScrollBar();
    private boolean compact;

    HomeAlts(RoadmapScreen menu) {
        this.menu = menu;
    }

    // ---- the picks ----

    private void sync(List<Roadmap.Step> steps) {
        if (steps != of) {
            of = steps;
            picked.clear();
            view = null;
            labels = null;
            open = false;
        }
    }

    /** The milestone {@code i}'s cards, empty when it has none (below Lv 100, an old map). */
    private static List<Roadmap.Alternative> cards(List<Roadmap.Step> steps, int i) {
        if (i <= 0 || i >= steps.size()) {
            return List.of();
        }
        List<Roadmap.Alternative> a = steps.get(i).alternatives();
        return a == null || a.size() < 2 ? List.of() : a;
    }

    /** The card shown at milestone {@code i}: 0 the pick. */
    int picked(List<Roadmap.Step> steps, int i) {
        sync(steps);
        int k = picked.getOrDefault(i, 0);
        return k < cards(steps, i).size() ? k : 0;
    }

    /** Whether the chip shows: the selected milestone has other builds and no plan is applied. */
    boolean shown() {
        return Plans.current() == null && !cards(menu.steps, menu.selected).isEmpty();
    }

    /** The map as the Home shows it: the selected milestone as its picked card (no plan applied). */
    List<Roadmap.Step> view(List<Roadmap.Step> steps, int selected) {
        sync(steps);
        int k = Plans.current() != null ? 0 : picked(steps, selected);
        if (k == 0) {
            return steps;
        }
        if (view == null || viewAt != selected || viewK != k) {
            view = Roadmap.withAlternative(steps, selected, k);
            viewAt = selected;
            viewK = k;
        }
        return view;
    }

    /** A card picked from the menu: the milestone shows it; the menu stays open. */
    void pick(int k) {
        sync(menu.steps);
        if (k >= 0 && k < cards(menu.steps, menu.selected).size()) {
            picked.put(menu.selected, k);
        }
    }

    /** The WynnBuilder link to milestone {@code step} as card {@code k} (null when it has no set). */
    static String link(Roadmap.Step step) {
        return step == null || step.set() == null || step.build() == null || step.build().isEmpty() ? null
                : WynnBuilderExport.milestone(step.build(), step.set(), step.level());
    }

    /** Card {@code k}'s ↗: its link on the clipboard. */
    void export(int k) {
        int i = menu.selected;
        if (i > 0 && i < menu.steps.size()) {
            UiExport.copy(CARD + k, link(menu.steps.get(i).as(k)));
        }
    }

    /** The Gear header's ↗: the set shown at the selected milestone. */
    void exportShown() {
        List<Roadmap.Step> v = view(menu.steps, menu.selected);
        UiExport.copy(GEAR, menu.selected > 0 && menu.selected < v.size() ? link(v.get(menu.selected)) : null);
    }

    // ---- the chip and the header's ↗ ----

    /**
     * The chip and the ↗ ending at {@code right} on the title row at {@code y}: the chip when the
     * milestone has other builds, the ↗ whenever a milestone with a set is selected and no plan is
     * applied. Returns the width they take with the gap before the level pills (0: nothing).
     */
    int chip(GuiGraphics g, Font font, HomeTab.Host host, int y, int right, boolean compact, int mx, int my) {
        this.compact = compact;
        sync(menu.steps);
        int i = menu.selected;
        if (Plans.current() != null || i <= 0 || i >= menu.steps.size()) {
            open = false;
            return 0;
        }
        Roadmap.Step shownStep = view(menu.steps, i).get(i);
        int x = right;
        if (shownStep.set() != null) {
            int bw = UiExport.width(font, GEAR, false);
            x -= bw;
            UiExport.draw(g, font, GEAR, x, y, false, true, mx, my);
            host.area(x, y, bw, Ui.CHIP_H, null, exportTip(L.t("wynnlabs.alt.export_level", shownStep.level()), GEAR, shownStep), "#qgearwb:");
            x -= Theme.SP_2;
        }
        List<Roadmap.Alternative> cards = cards(menu.steps, i);
        if (!cards.isEmpty()) {
            String label = L.t("wynnlabs.alt.chip", picked(menu.steps, i) + 1, cards.size());
            int cw = Ui.iconChipWidth(font, "sparkles", label, true);
            x -= cw;
            UiMenu.chip(g, font, "sparkles", label, x, y, open, mx, my);
            at = new int[] {x, y, cw, Ui.CHIP_H};
            host.area(x, y, cw, Ui.CHIP_H, null, open ? List.of() : List.of(
                    Component.literal(label).withStyle(ChatFormatting.WHITE),
                    L.c("wynnlabs.alt.chip_tip").withStyle(ChatFormatting.GRAY)), "#qalt:");
            x -= Theme.SP_2;
        } else {
            open = false;
        }
        return right - x;
    }

    /** An export button's tooltip: what it copies, then how the last copy went. */
    private static List<Component> exportTip(String title, String where, Roadmap.Step step) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
        // a set counted with a guild tome you don't have: the link carries it, say so before you copy
        var tome = step == null || step.set() == null ? null : step.set().tome();
        if (tome != null && tome.assumed()) {
            tip.add(L.c("wynnlabs.stats.assumes_tome", tome.text()).withStyle(ChatFormatting.GOLD));
        }
        int s = UiExport.state(where, System.currentTimeMillis());
        if (s != 0) {
            tip.add(L.c(s > 0 ? "wynnlabs.wb.copied" : "wynnlabs.wb.copy_failed").withStyle(s > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return tip;
    }

    // ---- the menu ----

    boolean open() {
        return open;
    }

    void openMenu() {
        open = shown();
        scroll = 0;
    }

    void close() {
        open = false;
        bar.mouseReleased();
    }

    boolean press(double x, double y) {
        if (bar.mouseClicked(x, y)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    boolean drag(double y) {
        if (bar.mouseDragged(y)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    void release() {
        bar.mouseReleased();
    }

    void wheel(double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v) * 3 * Type.BODY.lineHeight);   // clamped when drawn
    }

    /** A card's height: its lines with SP_2 above and below. */
    private int cardH(Roadmap.Alternative a) {
        int lines = compact ? 1 : 2 + (warns(a) || a.noTome() ? 1 : 0);
        return Theme.SP_2 + Type.BODY.lineHeight + lines * Type.META.lineHeight + Theme.SP_2;
    }

    /** The open menu under the chip, its cards and their click areas; its box (null when closed). */
    int[] drawList(GuiGraphics g, HomeTab.Host host, int mx, int my) {
        if (!open || at == null || !shown()) {
            open = false;
            return null;
        }
        Font font = UiFont.get();
        int i = menu.selected;
        List<Roadmap.Alternative> cards = cards(menu.steps, i);
        List<String> names = labels(menu.steps.get(i), cards);
        int k0 = picked(menu.steps, i);
        int content = 0;
        for (Roadmap.Alternative a : cards) {
            content += cardH(a) + Theme.SP_2;
        }
        content -= Theme.SP_2;
        var mc = net.minecraft.client.Minecraft.getInstance();
        int sw = mc.screen == null ? 10_000 : mc.screen.width;
        int sh = mc.screen == null ? 10_000 : mc.screen.height;
        int[] bounds = UiMenu.bounds(UiMenu.Spec.under(at, sw, sh));
        int room = Math.max(bounds[3] - (at[1] + at[3]), at[1] - bounds[1]) - Theme.SP_1 - Theme.SP_2 - 2 * Theme.SP_2;
        int viewH = Math.min(content, Math.max(cardH(cards.get(0)), room));
        boolean scrolls = viewH < content;
        int w = Math.min((compact ? W_SMALL : W_LARGE) + (scrolls ? ScrollBar.HIT : 0), bounds[2] - bounds[0] - 2 * Theme.SP_2);
        int[] b = UiMenu.place(at, w, viewH + 2 * Theme.SP_2, bounds);
        int x = b[0];
        int y = b[1];
        int rw = w - (scrolls ? ScrollBar.HIT : 0);
        scroll = Math.max(0, Math.min(scroll, content - viewH));
        Kit.softCard(g, x, y, w, b[3]);
        int top = y + Theme.SP_2;
        g.enableScissor(x, top, x + rw, top + viewH);
        int ry = top - scroll;
        for (int k = 0; k < cards.size(); k++) {
            Roadmap.Alternative a = cards.get(k);
            int h = cardH(a);
            if (ry + h > top && ry < top + viewH) {
                card(g, font, host, a, names.get(k), k, k == k0, x, ry, rw, h, top, top + viewH, mx, my);
            }
            ry += h + Theme.SP_2;
        }
        g.disableScissor();
        if (scrolls) {
            bar.place(x + rw, top, viewH, viewH, content, scroll);
            bar.draw(g, mx, my);
        } else {
            bar.place(0, 0, 0, 0, 0, 0);
        }
        return b;
    }

    /** One card: the label, the gap seal and the ↗ on its first line, its numbers and what you have under it. */
    private void card(GuiGraphics g, Font font, HomeTab.Host host, Roadmap.Alternative a, String name, int k, boolean chosen,
                      int x, int y, int w, int h, int clipTop, int clipBottom, int mx, int my) {
        boolean inView = my >= clipTop && my < clipBottom;
        boolean hover = inView && mx >= x && mx < x + w && my >= y && my < y + h;
        if (chosen) {
            Kit.rowLit(g, x + Theme.SP_1, y, w - 2 * Theme.SP_1, h);
        } else if (hover) {
            g.fill(x + 1, y, x + w - 1, y + h, Theme.CARD_HOVER);
        }
        int tx = x + Theme.SP_3;
        int ly = y + Theme.SP_2;
        // the ↗ at the right end, the gap seal before it, (compact) the tome's mark before that
        int bw = UiExport.width(font, CARD + k, false);
        int bx = x + w - Theme.SP_3 - bw;
        int by = Type.BODY.onCaps(ly, Ui.CHIP_H);
        UiExport.draw(g, font, CARD + k, bx, by, false, true, inView ? mx : -1, inView ? my : -1);
        int[] btn = clip(bx, by, bw, Ui.CHIP_H, clipTop, clipBottom);
        if (btn != null) {
            host.area(btn[0], btn[1], btn[2], btn[3], null, exportTip(L.t("wynnlabs.wb.export_tip_this"), CARD + k, menu.steps.get(menu.selected).as(k)), "#qaltwb:" + k);
        }
        String gap = k == 0 || a.gapPct() > -0.5 ? L.t("wynnlabs.alt.best") : L.t("wynnlabs.alt.gap", Math.round(-a.gapPct()));
        int gw = Type.META.width(font, gap) + 2 * Theme.SP_2;
        int gx = bx - Theme.SP_2 - gw;
        int gy = Type.BODY.onCaps(ly, Ui.SEAL_H);
        boolean best = k == 0 || a.gapPct() > -0.5;
        Kit.seal(g, gx, gy, gw, Ui.SEAL_H, best ? Theme.GOLD : Theme.LINE);
        Type.META.draw(g, font, gap, gx + Theme.SP_2, Type.META.yIn(gy, Ui.SEAL_H), best ? Theme.INK : Theme.SEAL_TEXT);
        int nameRight = gx - Theme.SP_2;
        if (compact && (warns(a) || a.noTome())) {
            nameRight -= 8;
            Art.icon(g, warns(a) ? "alert-triangle" : "check", nameRight, Type.BODY.onCaps(ly, 8), 8,
                    warns(a) ? Theme.WARN : Theme.GOOD);
            nameRight -= Theme.SP_2;
        }
        Type role = chosen ? Type.LABEL : Type.BODY;
        int tick = chosen ? 8 + Theme.SP_1 : 0;
        // too long: the piece that tells two cards apart goes first (the tooltip keeps it)
        String shown = role.width(font, name) <= nameRight - tx - tick ? name : role.fit(font, a.label(), nameRight - tx - tick);
        role.draw(g, font, shown, tx, ly, Theme.INK);
        if (chosen) {
            Art.icon(g, "check", tx + role.width(font, shown) + Theme.SP_1, Type.BODY.onCaps(ly, 8), 8, Theme.INK);
        }
        ly += Type.BODY.lineHeight;
        String nums = L.t(compact ? "wynnlabs.alt.stats_short" : "wynnlabs.alt.stats", k(a.spellDps()), k(a.ehp()))
                + (!compact && a.healPerSecond() > 0 ? L.t("wynnlabs.alt.heal", k(a.healPerSecond())) : "");
        String own = own(a);
        int ownInk = a.owned() >= a.pieces() ? Theme.GOOD : Double.isNaN(a.price()) ? Theme.INK_3 : Theme.PRICE;
        int room = x + w - Theme.SP_3 - tx;
        if (compact) {
            // never cut: the price goes first, then what you own; the tooltip has it all
            String head = nums + " · ";
            String tail = own;
            if (Type.META.width(font, head + tail) > room) {
                tail = a.owned() >= a.pieces() ? own : L.t("wynnlabs.alt.own", a.owned(), a.pieces());
            }
            if (Type.META.width(font, head + tail) > room) {
                head = nums;
                tail = "";
            }
            Type.META.draw(g, font, head, tx, ly, Theme.INK_2);
            Type.META.draw(g, font, tail, tx + Type.META.width(font, head), ly, ownInk);
        } else {
            Type.META.draw(g, font, Type.META.fit(font, nums, room), tx, ly, Theme.INK_2);
            ly += Type.META.lineHeight;
            Type.META.draw(g, font, Type.META.fit(font, own, room), tx, ly, ownInk);
            if (warns(a) || a.noTome()) {
                ly += Type.META.lineHeight;
                boolean warn = warns(a);
                String t = warn ? L.t("wynnlabs.alt.tome_short", effect(a.tome())) : L.t("wynnlabs.road.alt_no_tome");
                Art.icon(g, warn ? "alert-triangle" : "check", tx, Type.META.onCaps(ly, 7), 7, warn ? Theme.WARN : Theme.GOOD);
                Type.META.draw(g, font, Type.META.fit(font, t, room - 7 - Theme.SP_2), tx + 7 + Theme.SP_2, ly, warn ? Theme.WARN : Theme.GOOD);
            }
        }
        int[] row = clip(x, y, w, h, clipTop, clipBottom);
        if (row != null) {
            host.area(row[0], row[1], row[2], row[3], null, cardTip(a, name, best), "#qaltpick:" + k);
        }
    }

    /** A box cut to the menu's view; null when none of it shows. */
    private static int[] clip(int x, int y, int w, int h, int top, int bottom) {
        int a = Math.max(y, top);
        int b = Math.min(y + h, bottom);
        return b <= a ? null : new int[] {x, a, w, b - a};
    }

    /** A card's tooltip: its name, the gap, its numbers in full and the tome line. */
    private List<Component> cardTip(Roadmap.Alternative a, String name, boolean best) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(name).withStyle(ChatFormatting.WHITE));
        if (!best) {
            tip.add(L.c("wynnlabs.alt.gap_tip", Math.round(-a.gapPct())).withStyle(ChatFormatting.GRAY));
        }
        if (compact) {
            tip.add(Component.literal(L.t("wynnlabs.alt.stats", k(a.spellDps()), k(a.ehp()))
                    + (a.healPerSecond() > 0 ? L.t("wynnlabs.alt.heal", k(a.healPerSecond())) : "")).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal(own(a)).withStyle(ChatFormatting.GRAY));
        }
        // the guild tome by name: the card says only that it needs one, and what it gives
        if (a.tome() != null) {
            tip.add(L.c("wynnlabs.stats.assumes_tome", a.tome().text()).withStyle(warns(a) ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        } else if (compact && a.noTome()) {
            tip.add(L.c("wynnlabs.road.alt_no_tome").withStyle(ChatFormatting.GREEN));
        }
        return tip;
    }

    /** Whether the card warns about the guild tome: its points only fit with it. */
    private static boolean warns(Roadmap.Alternative a) {
        return a.needsTome() && a.tome() != null;
    }

    /** What a guild tome gives, short for a card: "+1 all", "+4 Int". */
    static String effect(io.github.anilken.wynnlabs.build.Tomes.Tome t) {
        int[] p = t.points();
        int at = -1;
        for (int i = 0; i < p.length; i++) {
            if (p[i] != 0) {
                if (at >= 0) {
                    return L.t("wynnlabs.alt.tome_all");
                }
                at = i;
            }
        }
        String[] skill = {"Str", "Dex", "Int", "Def", "Agi"};
        return at < 0 || at >= skill.length ? L.t("wynnlabs.alt.tome_all") : "+" + p[at] + " " + skill[at];
    }

    /** "2/9 owned · 7.1 STX to go", "All 9 owned", "2/9 owned · no price yet". */
    private static String own(Roadmap.Alternative a) {
        if (a.owned() >= a.pieces()) {
            return L.t("wynnlabs.alt.own_all", a.pieces());
        }
        String have = L.t("wynnlabs.alt.own", a.owned(), a.pieces());
        if (Double.isNaN(a.price())) {
            return have + " · " + L.t("wynnlabs.alt.no_price");
        }
        return a.price() > 0 ? have + " · " + L.t("wynnlabs.alt.to_go", Prices.fmt(a.price())) : have;
    }

    /** 980, 48.2k, 312k. */
    static String k(double v) {
        if (v >= 100_000) {
            return Math.round(v / 1000) + "k";
        }
        if (v >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1000);
        }
        return String.valueOf(Math.round(v));
    }

    /** The cards' names; one that shares its label with an earlier card gets its first piece that differs. */
    private List<String> labels(Roadmap.Step step, List<Roadmap.Alternative> cards) {
        if (labels != null && labelsAt == menu.selected && labels.size() == cards.size()) {
            return labels;
        }
        labels = names(step, cards);
        labelsAt = menu.selected;
        return labels;
    }

    /** {@link #labels}, worked out (pure but for the steps' sets). */
    static List<String> names(Roadmap.Step step, List<Roadmap.Alternative> cards) {
        List<String> out = new ArrayList<>();
        for (int k = 0; k < cards.size(); k++) {
            String label = cards.get(k).label();
            int first = -1;
            for (int j = 0; j < k; j++) {
                if (cards.get(j).label().equals(label)) {
                    first = j;
                    break;
                }
            }
            if (first < 0) {
                out.add(label);
                continue;
            }
            Map<String, Damage.Piece> mine = step.as(k).build();
            Map<String, Damage.Piece> theirs = step.as(first).build();
            String diff = null;
            for (String slot : Plans.SLOTS) {
                Damage.Piece p = mine.get(slot);
                Damage.Piece q = theirs.get(slot);
                if (p != null && (q == null || !Objects.equals(p.name(), q.name()))) {
                    diff = p.name();
                    break;
                }
            }
            out.add(diff == null ? label : label + " · " + diff);
        }
        return out;
    }
}
