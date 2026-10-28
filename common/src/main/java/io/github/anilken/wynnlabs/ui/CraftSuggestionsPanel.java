package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.SearchRunner;
import io.github.anilken.wynnlabs.crafting.SuggestionBoard;
import io.github.anilken.wynnlabs.crafting.SuggestionFilter;
import io.github.anilken.wynnlabs.crafting.Suggestions;
import io.github.anilken.wynnlabs.crafting.Types;
import io.github.anilken.wynnlabs.crafting.WhatCanICraft;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Öneriler: Singles is one list of the best recipes of every goal and item type, best first (each
 * goal's scores against its own best, so goals compare), each on a calm card with its goal as a tag;
 * the status chips filter and count it, and the picked one shows in full beside it (on a small
 * window, in a drawer). Sets lists the goals' sets ({@link CraftSetsPanel}). The search tier is the
 * page's, in the top row.
 */
final class CraftSuggestionsPanel extends CraftPanel {

    /** From this page width the detail card sits beside the cards; below it opens in a drawer. */
    static final int SPLIT_MIN = 600;
    static final int CARD_H = 48;

    private final SearchRunner suggestRunner = new SearchRunner();
    private CraftSearch.Budget suggesting;
    private String suggestedFor = "";
    private int listScroll;
    private int listMax;
    private final ScrollBar bar = new ScrollBar();

    /** The goal shown ("rawspell"), null for the class's first. */
    String goal;
    /** The status the cards are filtered to, null for all. */
    SuggestionBoard.Status only;
    /** The picked suggestion (its order), -1 for the first. */
    int picked = -1;
    final CraftSuggestDetail detail;

    CraftSuggestionsPanel(CraftingTab tab) {
        super(tab);
        detail = new CraftSuggestDetail(tab);
    }

    /** The menu was closed: a running search stops. */
    void stop() {
        suggestRunner.cancel();
        suggesting = null;
        suggestedFor = "";
    }

    /** The class's order of goal sets: spell first for casters, melee first for the others, then the build's elements. */
    List<String> goalOrder() {
        Klass k;
        try {
            k = GameState.current().classType();
        } catch (Throwable t) {
            k = Klass.NONE;
        }
        return Suggestions.order(k == Klass.MAGE || k == Klass.SHAMAN, knownPoints());
    }

    /** The goal shown: the picked one, else the class's first. */
    String currentGoal() {
        List<String> order = goalOrder();
        return goal != null && order.contains(goal) ? goal : order.get(0);
    }

    Suggestions.GoalSet goalSet(String key) {
        for (Suggestions.GoalSet g : goalSets()) {
            if (g.key().equals(key)) {
                return g;
            }
        }
        return goalSets().get(0);
    }

    /** The goal sets for the player's level and skill points. */
    List<Suggestions.GoalSet> goalSets() {
        return Suggestions.goalSets(goalOrder(), combatLevel(), knownPoints());
    }

    /** The player's skill points, or null while none are known. */
    int[] points() {
        return knownPoints();
    }

    /** The player's skill points, or null while none are known. */
    private int[] knownPoints() {
        for (int p : tab.points) {
            if (p > 0) {
                return tab.points.clone();
            }
        }
        return null;
    }

    /** The class's weapon, every armour piece and accessory, and the consumables. */
    List<String> suggestTypes() {
        // every weapon, not only the class's: a piece can be crafted for someone else
        List<String> t = new ArrayList<>(Types.WEAPONS);
        t.addAll(Types.ARMOUR);
        t.addAll(Types.ACCESSORIES);
        t.addAll(Types.CONSUMABLES);
        return t;
    }

    /** The player's class weapon ("relik"), or null without a class. */
    static String classWeapon() {
        String w = CraftingTab.defaultType();
        return w.equals("ring") ? null : w;
    }

    static int combatLevel() {
        try {
            return Math.max(1, GameState.current().level());
        } catch (Throwable t) {
            return 1;
        }
    }

    WhatCanICraft.Stock stockForSearch() {
        return CraftingTab.previewLevels.isEmpty() ? tab.stock
                : new WhatCanICraft.Stock(tab.stock.ingredients(), tab.stock.materials(), CraftingTab.previewLevels);
    }

    /**
     * Shows what the cache has for the mode shown (nothing is worked out on its own: the player
     * presses Calculate). {@code force}: the refresh, which calculates again what is shown.
     */
    void startCurrent(boolean force) {
        if (tab.suggestMode == 1) {
            if (force) {
                tab.setsPanel.calculate(true);
            } else {
                tab.setsPanel.startSets(false);
            }
            return;
        }
        loadCached();
        if (force) {
            // the level shown, or every level calculated so far (not all 48: that is "Calculate all")
            calculate(level == null ? new ArrayList<>(CraftSuggestCache.get(tab).singlesBrackets()) : List.of(level), true);
        }
    }

    /** For the preview: work out the player's bracket now. */
    void startSuggestions(boolean force) {
        loadCached();
        calculate(List.of(Suggestions.bracketOrder(combatLevel()).get(0)), force);
    }

    /** The character and data the list was read from the cache for. */
    private String loadedFor;
    /** The next suggestion's number. */
    private volatile int nextOrder;
    /** A calculation running: how many of how many brackets, and the one being worked out. */
    volatile int calcDone;
    volatile int calcTotal;
    volatile int calcAt;
    /** Reading the cache: shown as working, without a progress count. */
    volatile boolean loading;

    /** Puts the cached brackets in the list, once per character and data (read off the game thread). */
    void loadCached() {
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        String key = io.github.anilken.wynnlabs.core.Profile.id() + "|" + c.data();
        if (key.equals(loadedFor)) {
            return;
        }
        loadedFor = key;
        suggestRunner.cancel();
        tab.suggestions.clear();
        nextOrder = 0;
        var goals = CraftSuggestCache.goals(tab);
        var ings = CraftSuggestCache.ingredients(tab);
        List<Integer> brackets = new ArrayList<>(c.singlesBrackets());
        if (brackets.isEmpty()) {
            return;
        }
        loading = true;
        suggesting = suggestRunner.run(60_000, b -> {
            int max = 0;
            for (int lv : brackets) {
                if (b.cancelled()) {
                    break;
                }
                for (Suggestions.Suggestion s : c.singles(lv, goals, ings)) {
                    tab.suggestions.add(s);
                    max = Math.max(max, s.order() + 1);
                }
            }
            nextOrder = Math.max(nextOrder, max);
            loading = false;
            b.cancel();
        });
    }

    /**
     * Works the brackets out one after another (the player's first, then outward), replacing what
     * the list and the cache had for each; {@code again}: also the brackets already calculated.
     */
    void calculate(List<Integer> brackets, boolean again) {
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        List<Integer> queue = new ArrayList<>();
        for (int lv : Suggestions.bracketOrder(combatLevel())) {
            if (brackets.contains(lv) && (again || !c.hasSingles(lv))) {
                queue.add(lv);
            }
        }
        if (queue.isEmpty()) {
            return;
        }
        tab.setsPanel.stopCalculating();
        suggestRunner.cancel();
        loading = false;
        WhatCanICraft.Stock st = stockForSearch();
        String print = CraftSuggestCache.stock(tab);
        List<Ingredient> all = tab.ingredients;
        Map<String, CraftSearch.Price> pr = new HashMap<>(tab.prices);
        List<String> types = suggestTypes();
        List<Suggestions.GoalSet> goals = goalSets();
        calcDone = 0;
        calcTotal = queue.size();
        calcAt = queue.get(0);
        suggesting = suggestRunner.run(60 * 60_000, b -> {
            for (int lv : queue) {
                calcAt = lv;
                List<Suggestions.Suggestion> found = new ArrayList<>();
                tab.suggestions.removeIf(x -> x.recipe().minLevel() == lv);
                nextOrder = Suggestions.runBracket(types, goals, lv, st, all, pr::get, 60, b, nextOrder, x -> {
                    found.add(x);
                    tab.suggestions.add(x);
                });
                if (b.cancelled()) {
                    return;                         // stopped: this bracket stays as far as it got, not cached
                }
                c.putSingles(lv, print, System.currentTimeMillis(), found);
                CraftSuggestCache.save();
                calcDone++;
            }
            b.cancel();
        });
    }

    /** Stops a calculation (the brackets done stay). */
    void stopCalculating() {
        suggestRunner.cancel();
    }

    /** The bracket shown (its first level), null for every level. */
    Integer level;
    /** The kind of item shown ("weapon", "armour" …), null for all. */
    String kind;

    /** A bracket picked in the level menu: only shown; it is calculated when the player asks. */
    void pickLevel(Integer lv) {
        level = lv;
        filtersChanged();
    }

    /** The level menu's rows: every level, then each bracket ("…" while not calculated). */
    List<UiMenu.Item> levelItems() {
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        UiMenu.Items items = new UiMenu.Items();
        items.add(UiMenu.Item.of("all", null, L.t("wynnlabs.craft.all_levels"), null, level == null));
        items.divide();
        for (int lv : Suggestions.bracketLevels()) {
            items.add(UiMenu.Item.of(String.valueOf(lv), null, levelLabel(lv), c.hasSingles(lv) ? null : "…", level != null && level == lv));
        }
        return items.list;
    }

    static final String[] KINDS = {"weapon", "armour", "accessory", "consumable"};

    /** The type menu's rows: all, then each kind. */
    List<UiMenu.Item> kindItems() {
        UiMenu.Items items = new UiMenu.Items();
        items.add(UiMenu.Item.of("all", null, L.t("wynnlabs.craft.suggest_filter.all"), null, kind == null));
        items.divide();
        for (String k : KINDS) {
            items.add(UiMenu.Item.of(k, null, L.t("wynnlabs.craft.suggest_filter." + k), null, k.equals(kind)));
        }
        return items.list;
    }

    void pickKind(String k) {
        kind = k.equals("all") ? null : k;
        filtersChanged();
    }

    boolean kindShown(String type) {
        if (kind == null) {
            return true;
        }
        return switch (kind) {
            case "weapon" -> Types.weapon(type);
            case "armour" -> Types.armour(type);
            case "accessory" -> Types.accessory(type);
            default -> Types.consumable(type);
        };
    }

    static String levelLabel(Integer lv) {
        if (lv == null) {
            return L.t("wynnlabs.craft.all_levels");
        }
        io.github.anilken.wynnlabs.crafting.CraftData.Recipe r = io.github.anilken.wynnlabs.crafting.CraftData.find("ring", lv);
        return "Lv " + (r == null ? String.valueOf(lv) : r.bracket().replace('-', '–'));
    }

    boolean running() {
        return tab.suggestMode == 1 ? tab.setsPanel.running() : suggesting != null && !suggesting.cancelled();
    }

    /** The line under the top row: what is calculated and when, Calculate all, or the progress and Stop; returns the y under it. */
    private int calcBar(GuiGraphics g, int x, int y, int w, int mx, int my) {
        boolean sets = tab.suggestMode == 1;
        boolean busy = sets ? tab.setsPanel.calculating() : running() && !loading;
        int right = x + w;
        String text;
        int ink = Theme.INK_3;
        if (busy) {
            String stop = L.t("wynnlabs.craft.calc_stop");
            int cw = Ui.iconChipWidth(tab.font, "x", stop, false);
            right -= cw;
            Ui.iconChip(g, tab.font, "x", stop, false, right, y, cw, false, mx, my);
            Runnable halt = sets ? tab.setsPanel::stopCalculating : this::stopCalculating;
            tab.acts.add(new Act(right, y, cw, Ui.CHIP_H, halt, null, null));
            text = sets ? tab.setsPanel.progressText() : L.t("wynnlabs.craft.calc_progress", levelLabel(calcAt), Math.min(calcTotal, calcDone + 1), calcTotal);
            ink = Theme.INK_2;
        } else {
            boolean missing = sets ? tab.setsPanel.anyBandMissing() : anyBracketMissing();
            if (missing) {
                String all = L.t(sets ? "wynnlabs.craft.calc_all_bands" : "wynnlabs.craft.calc_all");
                int cw = Ui.iconChipWidth(tab.font, "player-play", all, false);
                right -= cw;
                Ui.iconChip(g, tab.font, "player-play", all, false, right, y, cw, false, mx, my);
                Runnable go = sets ? tab.setsPanel::calculateAll : () -> calculate(Suggestions.bracketLevels(), false);
                tab.acts.add(new Act(right, y, cw, Ui.CHIP_H, go, null,
                        List.of(L.c(sets ? "wynnlabs.craft.calc_all_bands_tip" : "wynnlabs.craft.calc_all_tip"))));
            }
            text = sets ? tab.setsPanel.updatedText() : updatedText();
            if (text.contains(L.t("wynnlabs.craft.calc_changed"))) {
                ink = Theme.WARN;
            }
        }
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, text, right - Theme.SP_4 - x), x, Type.META.yIn(y, Ui.CHIP_H), ink);
        return y + Ui.CHIP_H;
    }

    private boolean anyBracketMissing() {
        return CraftSuggestCache.get(tab).singlesBrackets().size() < Suggestions.bracketLevels().size();
    }

    /** "Updated 3 h ago" for the level shown, or how many levels are calculated (All levels). */
    private String updatedText() {
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        if (loading) {
            return L.t("wynnlabs.craft.calc_loading");
        }
        if (level != null) {
            var e = c.singlesEntry(level);
            return e == null ? L.t("wynnlabs.craft.calc_none_level", levelLabel(level)) : CraftSuggestCache.updated(tab, e);
        }
        io.github.anilken.wynnlabs.crafting.SuggestionCache.Entry old = null;
        for (int lv : c.singlesBrackets()) {
            var e = c.singlesEntry(lv);
            if (old == null || e.at() < old.at()) {
                old = e;
            }
        }
        String t = L.t("wynnlabs.craft.calc_count", c.singlesBrackets().size(), Suggestions.bracketLevels().size());
        return old == null ? t : t + " · " + CraftSuggestCache.updated(tab, old);
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        filter();
        int cy = filters(g, x, y, w, mx, my) + Theme.SP_3;
        int bottom = y + h;
        if (!(tab.stock.ingredients().isEmpty() && tab.stock.materials().isEmpty() && CraftingTab.previewLevels.isEmpty())) {
            cy = calcBar(g, x, cy, w, mx, my) + Theme.SP_3;
            cy = filterRow(g, x, cy, w, mx, my) + Theme.SP_4;
        }
        boolean noStock = tab.stock.ingredients().isEmpty() && tab.stock.materials().isEmpty() && CraftingTab.previewLevels.isEmpty();
        if (noStock) {
            UiEmpty.draw(g, tab.font, x, cy, w, bottom - cy, w < CraftingTab.TWO_COLUMNS_MIN, "package", L.t("wynnlabs.craft.suggest_no_stock"),
                    List.of(L.t("wynnlabs.craft.suggest_no_stock_1"), L.t("wynnlabs.craft.suggest_no_stock_2"),
                            L.t("wynnlabs.craft.suggest_no_stock_3")), null, null, mx, my);
            return;
        }
        if (tab.suggestMode == 1) {
            tab.setsPanel.page(g, x, cy, w, bottom - cy, mx, my);
        } else {
            singles(g, x, cy, w, bottom - cy, mx, my);
        }
    }

    /** What a scrolled list draws: from x, y (already scrolled) across w; returns its height. */
    interface Body {
        int draw(GuiGraphics g, int x, int y, int w, int top, int bottom);
    }

    /** A scrolled list in {x, y, w, h} with the shared bar: its click areas clipped to it. */
    void scrolled(GuiGraphics g, int x, int y, int w, int h, int mx, int my, Body body) {
        int cw = w - ScrollBar.W - Theme.SP_2;
        tab.clip = new int[] {x, y, w, h};
        g.enableScissor(x, y, x + w, y + h);
        int used = body.draw(g, x, y - listScroll, cw, y, y + h);
        g.disableScissor();
        tab.clip = null;
        listMax = Math.max(0, used - h);
        listScroll = Math.min(listScroll, listMax);
        bar.place(x + w - ScrollBar.W, y, h, h, used, listScroll);
        bar.draw(g, mx, my);
    }

    void resetScroll() {
        listScroll = 0;
    }

    // ---- singles: one ranked list across goals ----

    private List<SuggestionBoard.Ranked> ranked;
    private String rankedFor;

    private void singles(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        String key = tab.suggestions.size() + "|" + tab.setsPanel.tier + "|" + level + "|" + loadedFor + "|" + calcDone;
        if (ranked == null || !key.equals(rankedFor)) {
            // worked out again only when something came in or a choice changed, not every frame
            ranked = SuggestionBoard.ranked(List.copyOf(tab.suggestions), tab.setsPanel.tier, level,
                    Suggestions.bracketOrder(combatLevel()).get(0), tab::level);
            rankedFor = key;
        }
        List<SuggestionBoard.Ranked> all = new ArrayList<>();
        for (SuggestionBoard.Ranked r : ranked) {
            if (kindShown(r.pick().recipe().type()) && shownByFilters(r.pick())) {
                all.add(r);
            }
        }
        Map<SuggestionBoard.Status, Integer> counts = SuggestionBoard.rankedCounts(all);
        List<SuggestionBoard.Ranked> shown = new ArrayList<>();
        for (SuggestionBoard.Ranked r : all) {
            if (only == null || r.status() == only) {
                shown.add(r);
            }
        }
        boolean split = w >= SPLIT_MIN;
        int dw = split ? Math.max(240, Math.min(380, w * 2 / 5)) : 0;
        int gw = split ? w - dw - Theme.SP_6 : w;
        boolean running = running();
        int ly = statusHead(g, L.t("wynnlabs.craft.best_recipes"), String.valueOf(shown.size()), counts, x, y, gw, mx, my);
        SuggestionBoard.Ranked lit = null;
        for (SuggestionBoard.Ranked r : shown) {
            if (r.pick().order() == picked) {
                lit = r;
            }
        }
        if (lit == null && split && !shown.isEmpty()) {
            lit = shown.get(0);
        }
        if (shown.isEmpty()) {
            io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
            boolean none = level != null ? !c.hasSingles(level) : c.singlesBrackets().isEmpty();
            if (running || !none) {
                String why = running ? "wynnlabs.craft.suggest_working" : filter().active() ? "wynnlabs.craft.filter_none" : "wynnlabs.craft.suggest_none";
                Type.MUTED.draw(g, tab.font, L.t(why), x, ly, Theme.INK_3);
                return;
            }
            // nothing calculated for what is shown: the player starts it
            int target = level != null ? level : Suggestions.bracketOrder(combatLevel()).get(0);
            String title = level != null ? L.t("wynnlabs.craft.calc_none_level", levelLabel(level)) : L.t("wynnlabs.craft.calc_none");
            int[] box = UiEmpty.draw(g, tab.font, x, ly, w, y + h - ly, w < CraftingTab.TWO_COLUMNS_MIN, "sparkles", title,
                    List.of(L.t("wynnlabs.craft.calc_step_1"), L.t("wynnlabs.craft.calc_step_2"), L.t("wynnlabs.craft.calc_step_3")),
                    new UiEmpty.Action("player-play", L.t("wynnlabs.craft.calc_level", levelLabel(target)), "calc"), null, mx, my);
            if (box != null) {
                tab.acts.add(new Act(box[0], box[1], box[2], box[3], () -> calculate(List.of(target), false), null, null));
            }
            return;
        }
        SuggestionBoard.Ranked litF = lit;
        scrolled(g, x, ly, gw, y + h - ly, mx, my, (gg, lx, top, lw, ctop, cbottom) -> {
            int cy = top;
            for (SuggestionBoard.Ranked r : shown) {
                if (cy + CARD_H >= ctop && cy <= cbottom) {
                    Suggestions.Suggestion s = r.pick();
                    boolean hover = in(mx, my, lx, cy, lw, CARD_H) && my >= ctop && my < cbottom;
                    String name = CraftFormat.typeName(s.recipe().type()) + " · Lv " + s.recipe().bracket().replace('-', '–');
                    String tag = CraftSuggestDetail.goalLabel(s.goal().key());
                    if (s.recipe().type().equals(classWeapon())) {
                        tag += " · " + L.t("wynnlabs.craft.your_class");
                    }
                    detail.calmCard(gg, s.goal(), name, tag, s.recipe(), s.layout(), lx, cy, lw, hover, r == litF);
                    clipped(lx, cy, lw, CARD_H, () -> pick(r, split), null, CraftSuggestDetail.ingredientTip(s.layout()));
                }
                cy += CARD_H + Theme.SP_3;
            }
            if (running) {
                Type.MUTED.draw(gg, tab.font, L.t("wynnlabs.craft.suggest_working"), lx, cy, Theme.INK_3);
                cy += Type.MUTED.lineHeight + Theme.SP_3;
            }
            return cy - Theme.SP_3 - top;
        });
        if (split && lit != null) {
            Suggestions.Suggestion s = lit.pick();
            detail.draw(g, new CraftSuggestDetail.Shown(s.goal(), s.recipe(), s.tier1(), s.tier2(), s.layout()), x + gw + Theme.SP_6, y, dw, h,
                    null, null, null, null, true, tab.acts, mx, my);
        }
    }

    private void pick(SuggestionBoard.Ranked r, boolean split) {
        picked = r.pick().order();
        if (split) {
            return;
        }
        Suggestions.Suggestion s = r.pick();
        tab.drawers.openDetail(L.t("wynnlabs.craft.suggest_name", CraftSuggestDetail.goalLabel(s.goal().key()),
                CraftFormat.typeName(s.recipe().type())), (g, x, y, w, top, bottom, mx, my) -> {
                    int h = Math.max(bottom - y, CraftSuggestDetail.MIN_H);
                    detail.draw(g, new CraftSuggestDetail.Shown(s.goal(), s.recipe(), s.tier1(), s.tier2(), s.layout()), x, y, w, h,
                            null, null, null, null, false, tab.overlay, mx, my);
                    return h;
                });
    }

    /**
     * A list's head: its title (LABEL), a count (META), and on the right the three status chips,
     * each filtering to its status (again: all); a hairline under it. Returns the y for the list.
     */
    int statusHead(GuiGraphics g, String title, String meta, Map<SuggestionBoard.Status, Integer> counts, int x, int y, int w,
                   int mx, int my) {
        int right = x + w;
        SuggestionBoard.Status[] order = {SuggestionBoard.Status.LEVEL, SuggestionBoard.Status.BUY, SuggestionBoard.Status.READY};
        int statusW = 0;
        for (SuggestionBoard.Status st : order) {
            statusW += Ui.iconChipWidth(tab.font, CraftSuggestDetail.statusIcon(st),
                    L.t("wynnlabs.craft.status_chip." + st.name().toLowerCase(Locale.ROOT), counts.get(st)), false) + Theme.SP_2;
        }
        String kl = L.t(kind == null ? "wynnlabs.craft.all_types" : "wynnlabs.craft.suggest_filter." + kind);
        int kw = Ui.iconChipWidth(tab.font, null, kl, true);
        if (right - statusW - kw - Theme.SP_3 >= x + Type.LABEL.width(tab.font, title)) {
            int kx = right - statusW - Theme.SP_2 - kw;
            Ui.iconChip(g, tab.font, null, kl, true, kx, y, kw, kind != null || tab.open == CraftingTab.Drop.SUGG_KIND, mx, my);
            int[] anchor = {kx, y, kw, Ui.CHIP_H};
            tab.acts.add(new Act(kx, y, kw, Ui.CHIP_H, () -> tab.toggle(CraftingTab.Drop.SUGG_KIND, anchor), null, null));
        }
        for (SuggestionBoard.Status st : order) {
            String label = L.t("wynnlabs.craft.status_chip." + st.name().toLowerCase(Locale.ROOT), counts.get(st));
            String icon = CraftSuggestDetail.statusIcon(st);
            int cw = Ui.iconChipWidth(tab.font, icon, label, false);
            if (right - cw < x + Type.LABEL.width(tab.font, title)) {
                break;
            }
            right -= cw;
            Ui.iconChip(g, tab.font, icon, label, false, right, y, cw, only == st, mx, my);
            tab.acts.add(new Act(right, y, cw, Ui.CHIP_H, () -> {
                only = only == st ? null : st;
                filtersChanged();
            }, null, List.of(L.c("wynnlabs.craft.status_tip." + st.name().toLowerCase(Locale.ROOT)))));
            right -= Theme.SP_2;
        }
        if (right - kw - Theme.SP_3 >= x + Type.LABEL.width(tab.font, title)) {
            right -= kw + Theme.SP_2;
        }
        int ty = Type.LABEL.yIn(y, Ui.CHIP_H);
        String t = Type.LABEL.fit(tab.font, title, right - Theme.SP_3 - x);
        Type.LABEL.draw(g, tab.font, t, x, ty, Theme.INK);
        int mx0 = x + Type.LABEL.width(tab.font, t) + Theme.SP_3;
        if (meta != null && mx0 + Type.META.width(tab.font, meta) < right - Theme.SP_3) {
            Type.META.draw(g, tab.font, meta, mx0, ty + 1, Theme.INK_3);
        }
        int line = y + Ui.CHIP_H + Theme.SP_2;
        Kit.hair(g, x, line, w, 1, Theme.LINE);
        return line + 1 + Theme.SP_4;
    }

    void scrolled(double mx, double my, double v) {
        if (tab.suggestMode == 1 && tab.setsPanel.overList(mx, my)) {
            tab.setsPanel.scrollList(v);
            return;
        }
        listScroll = Math.max(0, Math.min(listMax, listScroll - (int) Math.signum(v) * Theme.SP_7 * 2));
    }

    boolean barPressed(double mx, double my) {
        if (tab.suggestMode == 1 && tab.setsPanel.listPressed(mx, my)) {
            return true;
        }
        if (bar.mouseClicked(mx, my)) {
            listScroll = bar.offset();
            return true;
        }
        return false;
    }

    boolean barDragged(double my) {
        if (tab.setsPanel.listDragged(my)) {
            return true;
        }
        if (bar.mouseDragged(my)) {
            listScroll = bar.offset();
            return true;
        }
        return false;
    }

    void barReleased() {
        bar.mouseReleased();
        tab.setsPanel.listReleased();
    }

    /** Singles / Sets, the goal chips (the rest in "More ▾"), and refresh on the right; returns the y under them. */
    private int filters(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int cx = x;
        String[] modes = {L.t("wynnlabs.craft.suggest_mode.singles"), L.t("wynnlabs.craft.suggest_mode.sets")};
        for (int i = 0; i < 2; i++) {
            int cw = Ui.chipWidth(tab.font, modes[i]);
            Ui.chip(g, tab.font, modes[i], cx, y, cw, tab.suggestMode == i, false, false, mx, my);
            int m = i;
            tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> {
                tab.suggestMode = m;
                listScroll = 0;
                startCurrent(false);
            }, null, null));
            cx += cw + Theme.SP_2;
        }
        cx += Theme.SP_1;
        Kit.hair(g, cx, y + Theme.SP_2, 1, Ui.CHIP_H - 2 * Theme.SP_2, Theme.LINE);
        cx += 1 + Theme.SP_3;
        int rw = Ui.CHIP_H + 2;
        Ui.iconButton(g, "refresh", x + w - rw, y, false, mx, my);
        tab.acts.add(new Act(x + w - rw, y, rw, Ui.CHIP_H, () -> startCurrent(true), null, List.of(L.c("wynnlabs.craft.suggest_refresh_tip"))));
        int limit = x + w - rw - Theme.SP_4;
        // the search tier for the page; the goal is picked in the sets' list, singles rank every goal
        for (CraftSearch.Tier t : SuggestionBoard.TIERS.reversed()) {
            String label = L.t("wynnlabs.craft.tier." + t.name().toLowerCase(Locale.ROOT));
            int cw = Ui.chipWidth(tab.font, label);
            if (cx + cw > limit) {
                break;
            }
            Ui.chip(g, tab.font, label, cx, y, cw, t == tab.setsPanel.tier, false, false, mx, my);
            tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> tab.setsPanel.tier = t, null,
                    List.of(L.c("wynnlabs.craft.tier_tip." + t.name().toLowerCase(Locale.ROOT)))));
            cx += cw + Theme.SP_2;
        }
        if (tab.suggestMode == 0) {
            String label = levelLabel(level);
            int cw = Ui.iconChipWidth(tab.font, null, label, true);
            int lx = Math.max(cx + Theme.SP_2, limit - cw);
            if (lx + cw <= limit) {
                Ui.iconChip(g, tab.font, null, label, true, lx, y, cw, level != null || tab.open == CraftingTab.Drop.SUGG_LEVEL, mx, my);
                int[] anchor = {lx, y, cw, Ui.CHIP_H};
                tab.acts.add(new Act(lx, y, cw, Ui.CHIP_H, () -> tab.toggle(CraftingTab.Drop.SUGG_LEVEL, anchor), null, null));
            }
        }
        return y + Ui.CHIP_H;
    }

    void pickGoal(String key) {
        goal = key;
        picked = -1;
        listScroll = 0;
        tab.setsPanel.piece = -1;
    }


    // ---- filters ----

    /** Whose filters the fields hold, and the search field's area this frame. */
    private String filtersFor;
    int[] searchBox;

    /** The filters, copied into the page's fields once per character. */
    SuggestionFilter filter() {
        SuggestionFilter f = CraftFilters.get();
        String who = CraftFilters.owner();
        if (!who.equals(filtersFor)) {
            filtersFor = who;
            only = f.singles;
            kind = f.kind;
            level = f.level;
            if (tab.suggestSearch != null && !tab.suggestSearch.getValue().equals(f.query)) {
                tab.suggestSearch.setValue(f.query);
            }
        }
        return f;
    }

    /** A filter changed: kept, and the lists go back to the top. */
    void filtersChanged() {
        SuggestionFilter f = filter();
        f.singles = only;
        f.kind = kind;
        f.level = level;
        CraftFilters.save();
        picked = -1;
        listScroll = 0;
    }

    void queryChanged(String q) {
        SuggestionFilter f = filter();
        if (!q.equals(f.query)) {
            f.query = q;
            filtersChanged();
        }
    }

    /** The row of filters: the search, then the goals and "wearable now" (one Filter menu on a small window), Clear. */
    private int filterRow(GuiGraphics g, int x, int y, int w, int mx, int my) {
        SuggestionFilter f = filter();
        boolean sets = tab.suggestMode == 1;
        boolean wide = w >= SPLIT_MIN;
        int cx = x;
        int sw = wide ? 170 : 120;
        if (tab.suggestSearch != null) {
            UiInput.Hit hit = UiInput.search(g, tab.font, tab.suggestSearch, L.t("wynnlabs.craft.filter_search_hint"), false, cx, y, sw, mx, my);
            searchBox = hit.field();
            tab.acts.add(new Act(cx, y, sw, UiInput.H, () -> UiInput.focus(tab.suggestSearch, tab.screen::setFocused), null, null));
            if (hit.clear() != null) {
                int[] c = hit.clear();
                tab.acts.add(new Act(c[0], c[1], c[2], c[3], () -> tab.suggestSearch.setValue(""), null, null));
            }
            cx += sw + Theme.SP_3;
        }
        if (wide) {
            String gl = f.groups.isEmpty() ? L.t("wynnlabs.craft.filter_goals") : L.t("wynnlabs.craft.filter_goals_n", f.groups.size());
            cx += menuChip(g, "target", gl, !f.groups.isEmpty(), CraftingTab.Drop.SUGG_GOALS, cx, y, mx, my) + Theme.SP_2;
            if (sets) {
                String sl = f.sets == null ? L.t("wynnlabs.craft.filter_status_any")
                        : L.t("wynnlabs.craft.set_status." + f.sets.name().toLowerCase(Locale.ROOT));
                cx += menuChip(g, null, sl, f.sets != null, CraftingTab.Drop.SET_STATUS, cx, y, mx, my) + Theme.SP_2;
            }
            String wl = L.t("wynnlabs.craft.filter_wearable");
            int ww = Ui.iconChipWidth(tab.font, "user", wl, false);
            Ui.iconChip(g, tab.font, "user", wl, false, cx, y, ww, f.wearable, mx, my);
            tab.acts.add(new Act(cx, y, ww, Ui.CHIP_H, () -> {
                f.wearable = !f.wearable;
                filtersChanged();
            }, null, List.of(L.c("wynnlabs.craft.filter_wearable_tip"))));
            cx += ww + Theme.SP_2;
        } else {
            int n = f.groups.size() + (f.wearable ? 1 : 0) + (sets && f.sets != null ? 1 : 0);
            String fl = n == 0 ? L.t("wynnlabs.craft.filter") : L.t("wynnlabs.craft.filter_n", n);
            cx += menuChip(g, "filter", fl, n > 0, CraftingTab.Drop.SUGG_FILTER, cx, y, mx, my) + Theme.SP_2;
        }
        if (f.active()) {
            String cl = L.t("wynnlabs.craft.filter_clear");
            int cw = Ui.iconChipWidth(tab.font, "x", cl, false);
            if (cx + cw <= x + w) {
                Ui.iconChip(g, tab.font, "x", cl, false, cx, y, cw, false, mx, my);
                tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> {
                    f.clear();
                    only = null;
                    kind = null;
                    level = null;
                    if (tab.suggestSearch != null) {
                        tab.suggestSearch.setValue("");
                    }
                    filtersChanged();
                }, null, null));
            }
        }
        return y + Ui.CHIP_H;
    }

    private int menuChip(GuiGraphics g, String icon, String label, boolean on, CraftingTab.Drop drop, int x, int y, int mx, int my) {
        int cw = Ui.iconChipWidth(tab.font, icon, label, true);
        Ui.iconChip(g, tab.font, icon, label, true, x, y, cw, on || tab.open == drop, mx, my);
        int[] anchor = {x, y, cw, Ui.CHIP_H};
        tab.acts.add(new Act(x, y, cw, Ui.CHIP_H, () -> tab.toggle(drop, anchor), null, null));
        return cw;
    }

    /** The filter menus' rows: the goal groups to tick; on a small window also "wearable now" and, in Sets, the status. */
    List<UiMenu.Item> filterItems(CraftingTab.Drop drop) {
        SuggestionFilter f = filter();
        UiMenu.Items items = new UiMenu.Items();
        if (drop != CraftingTab.Drop.SET_STATUS) {
            for (String gk : SuggestionFilter.GROUPS.keySet()) {
                items.add(UiMenu.Item.of("g:" + gk, null, L.t("wynnlabs.craft.filter_group." + gk), null, f.groups.contains(gk)));
            }
        }
        if (drop == CraftingTab.Drop.SUGG_FILTER) {
            items.divide();
            items.add(UiMenu.Item.of("wear", "user", L.t("wynnlabs.craft.filter_wearable"), null, f.wearable));
        }
        if (drop == CraftingTab.Drop.SET_STATUS || drop == CraftingTab.Drop.SUGG_FILTER && tab.suggestMode == 1) {
            items.divide();
            items.add(UiMenu.Item.of("st:all", null, L.t("wynnlabs.craft.filter_status_any"), null, f.sets == null));
            for (SuggestionBoard.Status st : SuggestionBoard.Status.values()) {
                items.add(UiMenu.Item.of("st:" + st.name(), CraftSuggestDetail.statusIcon(st),
                        L.t("wynnlabs.craft.set_status." + st.name().toLowerCase(Locale.ROOT)), null, st == f.sets));
            }
        }
        return items.list;
    }

    /** A filter menu's row picked; true when the menu closes (a status), false when it stays (a tick). */
    boolean pickFilter(String id) {
        SuggestionFilter f = filter();
        boolean close = false;
        if (id.startsWith("g:")) {
            String gk = id.substring(2);
            if (!f.groups.remove(gk)) {
                f.groups.add(gk);
            }
        } else if (id.equals("wear")) {
            f.wearable = !f.wearable;
        } else if (id.startsWith("st:")) {
            f.sets = id.equals("st:all") ? null : SuggestionBoard.Status.valueOf(id.substring(3));
            close = true;
        }
        filtersChanged();
        return close;
    }

    /** A single suggestion passes the goal groups, "wearable now" and the search. */
    boolean shownByFilters(Suggestions.Suggestion s) {
        SuggestionFilter f = filter();
        if (!f.goalMatches(s.goal().key())) {
            return false;
        }
        if (f.wearable && !s.recipe().consumable() && !SuggestionFilter.wearable(s.layout().result().requirements(), tab.points)) {
            return false;
        }
        if (f.query.isBlank()) {
            return true;
        }
        List<String> names = new ArrayList<>();
        names.add(CraftSuggestDetail.goalLabel(s.goal().key()));
        names.add(CraftFormat.typeName(s.recipe().type()));
        for (Ingredient in : s.layout().slots()) {
            if (!in.empty()) {
                names.add(in.name());
            }
        }
        return SuggestionFilter.queryMatches(f.query, names);
    }

    /** " · ~3 EB", or nothing while no price is known. */
    static String costPart(String cost) {
        return cost.equals("?") ? "" : " · ~" + cost;
    }
}
