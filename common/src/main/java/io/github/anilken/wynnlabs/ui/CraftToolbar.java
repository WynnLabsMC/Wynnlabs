package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.SuggestionBoard;
import io.github.anilken.wynnlabs.crafting.SuggestionCache;
import io.github.anilken.wynnlabs.crafting.SuggestionFilter;
import io.github.anilken.wynnlabs.crafting.Suggestions;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The suggestions page's one toolbar row: Singles | Sets, the search, one Filter menu (the search
 * tier, the goal groups, wearable now, and Sets' status or Singles' type and level) with a count of
 * what is on, and one calculation button (a brown dot when the items changed since; the progress
 * and Stop while it runs; its menu calculates again or everything). Under it, only when any filter
 * is on, each one as a chip to remove, and Clear all. The calculation's age sits in the lists'
 * heads ({@link #age}).
 */
final class CraftToolbar extends CraftPanel {

    /** The Filter chip's area this frame (the menu opens under it, the level menu too). */
    private int[] filterAnchor;
    /** A page too narrow for the Filter menu: it opens as a drawer. */
    private boolean narrow;

    CraftToolbar(CraftingTab tab) {
        super(tab);
    }

    private CraftSuggestionsPanel sp() {
        return tab.suggestionsPanel;
    }

    private boolean sets() {
        return tab.suggestMode == 1;
    }

    /** The row; returns the y under it. */
    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        narrow = w < CraftSuggestionsPanel.SPLIT_MIN;
        int cx = x;
        String[] modes = {L.t("wynnlabs.craft.suggest_mode.singles"), L.t("wynnlabs.craft.suggest_mode.sets")};
        for (int i = 0; i < 2; i++) {
            int cw = Ui.chipWidth(tab.font, modes[i]);
            Ui.chip(g, tab.font, modes[i], cx, y, cw, tab.suggestMode == i, false, false, mx, my);
            int m = i;
            tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> {
                tab.suggestMode = m;
                sp().resetScroll();
                sp().startCurrent(false);
            }, null, null));
            cx += cw + (i == 0 ? Theme.SP_1 : 0);
        }
        cx += Theme.SP_4;
        // from the right: the calculation, then Filter; the search takes what is left
        int right = x + w - calc(g, x + w, y, mx, my) - Theme.SP_3;
        SuggestionFilter f = sp().filter();
        int n = f.count(sets());
        String fl = L.t("wynnlabs.craft.filter");
        int fw = countChipWidth(fl, n);
        right -= fw;
        countChip(g, fl, n, right, y, fw, n > 0 || tab.open == CraftingTab.Drop.FILTER, mx, my);
        int[] anchor = {right, y, fw, Ui.CHIP_H};
        filterAnchor = anchor;
        tab.acts.add(new Act(right, y, fw, Ui.CHIP_H, this::openFilter, null, null));
        right -= Theme.SP_3;
        int sw = right - cx;
        sp().searchBox = null;
        if (tab.suggestSearch != null && sw >= UiInput.MIN_W) {
            UiInput.Hit hit = UiInput.search(g, tab.font, tab.suggestSearch, L.t("wynnlabs.craft.filter_search_hint"), false, cx, y, sw, mx, my);
            sp().searchBox = hit.field();
            tab.acts.add(new Act(cx, y, sw, UiInput.H, () -> UiInput.focus(tab.suggestSearch, tab.screen::setFocused), null, null));
            if (hit.clear() != null) {
                int[] c = hit.clear();
                tab.acts.add(new Act(c[0], c[1], c[2], c[3], () -> tab.suggestSearch.setValue(""), null, null));
            }
        }
        return y + Ui.CHIP_H;
    }

    /** The calculation control ending at {@code right}; returns its width. */
    private int calc(GuiGraphics g, int right, int y, int mx, int my) {
        if (calculating()) {
            String t = L.t("wynnlabs.craft.calc_running", progress()[0], progress()[1]);
            int cw = Ui.iconChipWidth(tab.font, "x", t, false);
            Ui.iconChip(g, tab.font, "x", t, false, right - cw, y, cw, false, mx, my);
            Runnable stop = sets() ? tab.setsPanel::stopCalculating : sp()::stopCalculating;
            tab.acts.add(new Act(right - cw, y, cw, Ui.CHIP_H, stop, null,
                    List.of(net.minecraft.network.chat.Component.literal(sets() ? tab.setsPanel.progressText() : sp().progressText()))));
            return cw;
        }
        int bw = Ui.CHIP_H + 2;
        int bx = right - bw;
        Ui.iconButton(g, "refresh", bx, y, tab.open == CraftingTab.Drop.CALC, mx, my);
        if (changed()) {
            // the items changed since: a small brown dot on the button's corner
            g.fill(bx + bw - 4, y + 1, bx + bw - 1, y + 4, Theme.WARN);
        }
        int[] anchor = {bx, y, bw, Ui.CHIP_H};
        tab.acts.add(new Act(bx, y, bw, Ui.CHIP_H, () -> tab.toggle(CraftingTab.Drop.CALC, anchor), null,
                List.of(net.minecraft.network.chat.Component.literal(sets() ? tab.setsPanel.updatedText() : sp().updatedText()))));
        return bw;
    }

    private boolean calculating() {
        return sets() ? tab.setsPanel.calculating() : sp().running() && !sp().loading;
    }

    private int[] progress() {
        return sets() ? new int[] {Math.min(tab.setsPanel.calcTotal, tab.setsPanel.calcDone + 1), tab.setsPanel.calcTotal}
                : new int[] {Math.min(sp().calcTotal, sp().calcDone + 1), sp().calcTotal};
    }

    /** The result shown's cache entry (Singles: the level shown, else the oldest calculated), or null. */
    private SuggestionCache.Entry entry() {
        SuggestionCache c = CraftSuggestCache.get(tab);
        if (sets()) {
            return c.setsEntry(tab.setsPanel.weaponShown(), tab.setsPanel.bandShown());
        }
        if (sp().level != null) {
            return c.singlesEntry(sp().level);
        }
        SuggestionCache.Entry old = null;
        for (int lv : c.singlesBrackets()) {
            SuggestionCache.Entry e = c.singlesEntry(lv);
            if (old == null || e.at() < old.at()) {
                old = e;
            }
        }
        return old;
    }

    /** Whether the items or skill points changed since what is shown was calculated. */
    boolean changed() {
        SuggestionCache.Entry e = entry();
        return e != null && !e.stock().equals(CraftSuggestCache.stock(tab));
    }

    /**
     * The calculation's age in a list's head ending at {@code right}: "1 min ago", a brown dot before
     * it when the items changed since; returns its width (0 when nothing is calculated or it does not fit).
     */
    int age(GuiGraphics g, int right, int lineY, int room) {
        SuggestionCache.Entry e = entry();
        if (e == null || calculating()) {
            return 0;
        }
        String t = MenuPaint.ago(e.at());
        boolean dot = changed();
        int w = Type.META.width(tab.font, t) + (dot ? 3 + Theme.SP_2 : 0);
        if (w > room) {
            return 0;
        }
        int x = right - w;
        if (dot) {
            int dy = Type.META.onCaps(lineY, 3);
            g.fill(x, dy, x + 3, dy + 3, Theme.WARN);
            x += 3 + Theme.SP_2;
        }
        Type.META.draw(g, tab.font, t, x, lineY, Theme.INK_3);
        tab.acts.add(new Act(right - w, lineY, w, Type.META.lineHeight, null, null,
                List.of(net.minecraft.network.chat.Component.literal(sets() ? tab.setsPanel.updatedText() : sp().updatedText()))));
        return w;
    }

    /** The calculation menu: when, again, everything not calculated. */
    List<UiMenu.Item> calcItems() {
        UiMenu.Items items = new UiMenu.Items();
        items.add(UiMenu.Item.heading(sets() ? tab.setsPanel.updatedText() : sp().updatedText()));
        String again = sets() ? L.t("wynnlabs.craft.calc_again_band") : sp().level == null ? L.t("wynnlabs.craft.calc_again_all")
                : L.t("wynnlabs.craft.calc_again_level", CraftSuggestionsPanel.levelLabel(sp().level));
        items.add(UiMenu.Item.of("again", "refresh", again, null, false).tip(List.of(L.c("wynnlabs.craft.suggest_refresh_tip"))));
        boolean missing = sets() ? tab.setsPanel.anyBandMissing() : sp().anyBracketMissing();
        UiMenu.Item all = UiMenu.Item.of("all", "player-play", L.t(sets() ? "wynnlabs.craft.calc_all_bands" : "wynnlabs.craft.calc_all"),
                null, false).tip(List.of(L.c(sets() ? "wynnlabs.craft.calc_all_bands_tip" : "wynnlabs.craft.calc_all_tip")));
        items.add(missing ? all : all.asDim());
        return items.list;
    }

    void pickCalc(String id) {
        if (id.equals("again")) {
            sp().startCurrent(true);
        } else if (id.equals("all")) {
            if (sets()) {
                tab.setsPanel.calculateAll();
            } else {
                sp().calculate(Suggestions.bracketLevels(), false);
            }
        }
    }

    // ---- the Filter menu ----

    /** A small window: the filters in a drawer (the menu would not fit); else the menu under the chip. */
    void openFilter() {
        if (narrow || filterAnchor == null) {
            tab.drawers.openDetail(L.t("wynnlabs.craft.filter"), this::drawer);
            return;
        }
        tab.toggle(CraftingTab.Drop.FILTER, filterAnchor);
    }

    /** The menu's rows by section: tier, goals, show, then Sets' status or Singles' type and level ({@code inline}: every level). */
    List<UiMenu.Item> filterItems(boolean inline) {
        SuggestionFilter f = sp().filter();
        UiMenu.Items items = new UiMenu.Items();
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_tier")));
        for (CraftSearch.Tier t : SuggestionBoard.TIERS.reversed()) {
            items.add(UiMenu.Item.of("t:" + t.name(), null, L.t("wynnlabs.craft.tier." + t.name().toLowerCase(Locale.ROOT)), null,
                    t == f.tierShown()).tip(List.of(L.c("wynnlabs.craft.tier_tip." + t.name().toLowerCase(Locale.ROOT)))));
        }
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_goals")));
        for (String gk : SuggestionFilter.GROUPS.keySet()) {
            items.add(UiMenu.Item.of("g:" + gk, null, L.t("wynnlabs.craft.filter_group." + gk), null, f.groups.contains(gk)));
        }
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_show")));
        items.add(UiMenu.Item.of("wear", "user", L.t("wynnlabs.craft.filter_wearable"), null, f.wearable)
                .tip(List.of(L.c("wynnlabs.craft.filter_wearable_tip"))));
        if (sets()) {
            items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_status")));
            items.add(UiMenu.Item.of("st:all", null, L.t("wynnlabs.craft.filter_status_any"), null, f.sets == null));
            for (SuggestionBoard.Status st : SuggestionBoard.Status.values()) {
                items.add(UiMenu.Item.of("st:" + st.name(), CraftSuggestDetail.statusIcon(st),
                        L.t("wynnlabs.craft.set_status." + st.name().toLowerCase(Locale.ROOT)), null, st == f.sets));
            }
            return items.list;
        }
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_type")));
        items.add(UiMenu.Item.of("k:all", null, L.t("wynnlabs.craft.all_types"), null, sp().kind == null));
        for (String k : CraftSuggestionsPanel.KINDS) {
            items.add(UiMenu.Item.of("k:" + k, null, L.t("wynnlabs.craft.suggest_filter." + k), null, k.equals(sp().kind)));
        }
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_level")));
        if (inline) {
            items.list.addAll(sp().levelItems());
        } else {
            items.add(UiMenu.Item.of("open:level", "chevron-right", CraftSuggestionsPanel.levelLabel(sp().level), null, sp().level != null));
        }
        return items.list;
    }

    /** A row of the Filter menu (or its drawer) picked; true when the menu closes. */
    boolean pickFilter(String id) {
        SuggestionFilter f = sp().filter();
        if (id.equals("open:level")) {
            tab.toggle(CraftingTab.Drop.SUGG_LEVEL, filterAnchor);
            return false;
        }
        if (id.startsWith("t:")) {
            f.tier = CraftSearch.Tier.valueOf(id.substring(2));
            tab.setsPanel.tier = f.tierShown();
        } else if (id.startsWith("g:")) {
            String gk = id.substring(2);
            if (!f.groups.remove(gk)) {
                f.groups.add(gk);
            }
        } else if (id.equals("wear")) {
            f.wearable = !f.wearable;
        } else if (id.startsWith("st:")) {
            f.sets = id.equals("st:all") ? null : SuggestionBoard.Status.valueOf(id.substring(3));
        } else if (id.startsWith("k:")) {
            sp().kind = id.equals("k:all") ? null : id.substring(2);
        } else if (id.equals("all")) {
            sp().level = null;
        } else if (id.matches("\\d+")) {
            sp().level = Integer.valueOf(id);
        }
        sp().filtersChanged();
        return false;
    }

    /** The filters in a drawer (a small window): the menu's rows, every level inline; ticks keep it open. */
    private int drawer(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        int cy = y;
        int row = Type.BODY.lineHeight + 2 * Theme.SP_2;
        boolean first = true;
        for (UiMenu.Item it : filterItems(true)) {
            if (it.heading()) {
                cy += first ? 0 : Theme.SP_3;
                Type.META.draw(g, tab.font, it.label(), x, cy, Theme.INK_3);
                cy += Type.META.lineHeight + Theme.SP_1;
                Kit.hair(g, x, cy, w, 1, Theme.LINE);
                cy += 1 + Theme.SP_2;
                first = false;
                continue;
            }
            if (it.groupStart() && !first) {
                cy += Theme.SP_2;
            }
            first = false;
            boolean hover = in(mx, my, x, cy, w, row) && my >= top && my < bottom;
            if (it.chosen()) {
                Kit.rowLit(g, x - Theme.SP_2, cy, w + 2 * Theme.SP_2, row);
            } else if (hover) {
                g.fill(x - Theme.SP_2, cy, x + w + Theme.SP_2, cy + row, Theme.CARD_HOVER);
            }
            int ty = Type.BODY.yIn(cy, row);
            Type role = it.chosen() ? Type.LABEL : Type.BODY;
            role.draw(g, tab.font, role.fit(tab.font, it.label(), w - 8 - Theme.SP_4), x, ty, it.dim() ? Theme.INK_3 : it.chosen() ? Theme.INK : Theme.INK_2);
            if (it.chosen()) {
                Art.icon(g, "check", x + w - 8, Type.BODY.onCaps(ty, 8), 8, Theme.INK);
            } else if (it.meta() != null) {
                Type.META.drawRight(g, tab.font, it.meta(), x + w, ty + 1, Theme.INK_3);
            }
            String id = it.id();
            tab.overlay.add(new Act(x, cy, w, row, () -> pickFilter(id), null, it.tip()));
            cy += row + Theme.SP_2;
        }
        return cy - y;
    }

    // ---- the chips of what is on ----

    /** Each filter that is on as a chip to remove, then Clear all; nothing when none is on. Returns the y under it. */
    int active(GuiGraphics g, int x, int y, int w, int mx, int my) {
        SuggestionFilter f = sp().filter();
        if (f.count(sets()) == 0) {
            return y;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> removes = new ArrayList<>();
        if (f.tier != null && f.tier != SuggestionBoard.DEFAULT_TIER) {
            labels.add(L.t("wynnlabs.craft.tier." + f.tier.name().toLowerCase(Locale.ROOT)));
            removes.add(() -> {
                f.tier = null;
                tab.setsPanel.tier = f.tierShown();
            });
        }
        for (String gk : List.copyOf(f.groups)) {
            labels.add(L.t("wynnlabs.craft.filter_group." + gk));
            removes.add(() -> f.groups.remove(gk));
        }
        if (f.wearable) {
            labels.add(L.t("wynnlabs.craft.filter_wearable"));
            removes.add(() -> f.wearable = false);
        }
        if (sets() && f.sets != null) {
            labels.add(L.t("wynnlabs.craft.set_status." + f.sets.name().toLowerCase(Locale.ROOT)));
            removes.add(() -> f.sets = null);
        }
        if (!sets() && sp().kind != null) {
            labels.add(L.t("wynnlabs.craft.suggest_filter." + sp().kind));
            removes.add(() -> sp().kind = null);
        }
        if (!sets() && sp().level != null) {
            labels.add(CraftSuggestionsPanel.levelLabel(sp().level));
            removes.add(() -> sp().level = null);
        }
        String clear = L.t("wynnlabs.craft.filter_clear");
        int clearW = Ui.chipWidth(tab.font, clear);
        int cx = x;
        for (int i = 0; i < labels.size(); i++) {
            int cw = Ui.iconChipWidth(tab.font, "x", labels.get(i), false);
            if (cx + cw + Theme.SP_2 + clearW > x + w) {
                break;
            }
            Ui.iconChip(g, tab.font, "x", labels.get(i), false, cx, y, cw, false, mx, my);
            Runnable r = removes.get(i);
            tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> {
                r.run();
                sp().filtersChanged();
            }, null, null));
            cx += cw + Theme.SP_2;
        }
        Ui.chip(g, tab.font, clear, cx, y, clearW, false, false, false, mx, my);
        tab.acts.add(new Act(cx, y, clearW, Ui.CHIP_H, () -> {
            String q = f.query;
            f.clear();
            f.query = q;                                    // the search has its own ✕
            sp().only = null;
            sp().kind = null;
            sp().level = null;
            tab.setsPanel.tier = f.tierShown();
            sp().filtersChanged();
        }, null, null));
        return y + Ui.CHIP_H;
    }

    // ---- the Filter chip: icon, label, a gold count, chevron ----

    private int countChipWidth(String label, int n) {
        int w = Ui.iconChipWidth(tab.font, "filter", label, true);
        return n > 0 ? w + Type.META.width(tab.font, String.valueOf(n)) + 2 * Theme.SP_2 + Theme.SP_1 : w;
    }

    private void countChip(GuiGraphics g, String label, int n, int x, int y, int w, boolean on, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + Ui.CHIP_H;
        Kit.pill(g, x, y, w, Ui.CHIP_H, on, hover);
        int ink = on ? Theme.INK : Theme.INK_2;
        int cx = x + Theme.SP_4;
        int ty = Type.BODY.yIn(y, Ui.CHIP_H);
        Art.icon(g, "filter", cx, Type.BODY.onCaps(ty, 8), 8, ink);
        cx += 8 + Theme.SP_1;
        Type.BODY.draw(g, tab.font, label, cx, ty, ink);
        cx += Type.BODY.width(tab.font, label) + Theme.SP_1;
        if (n > 0) {
            cx += Ui.seal(g, tab.font, String.valueOf(n), cx, y + (Ui.CHIP_H - Ui.SEAL_H) / 2, Theme.GOLD_EDGE) + Theme.SP_1;
        }
        Art.icon(g, "chevron-down", cx, Type.BODY.onCaps(ty, 6), 6, Theme.INK_3);
    }
}
