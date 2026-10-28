package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftChecks;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.SearchRunner;
import io.github.anilken.wynnlabs.crafting.SetSuggestions;
import io.github.anilken.wynnlabs.crafting.Suggestions;
import io.github.anilken.wynnlabs.crafting.Types;
import io.github.anilken.wynnlabs.crafting.WhatCanICraft;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.anilken.wynnlabs.ui.CraftFormat.range;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * Öneriler: what to craft, as one list in two groups: "Elindekiyle şimdi" (from what is owned) and
 * "Birkaç malzeme alırsan" (a few bought). Single pieces, or whole sets with the "Set" chip; the
 * kind chips filter. A row's "Tarife al ›" (a set's row: its pieces) takes it to the Tarif page.
 */
final class CraftSuggestionsPanel extends CraftPanel {

    static final String[] KINDS = {"all", "weapon", "armour", "accessory", "consumable"};

    private final SearchRunner suggestRunner = new SearchRunner();
    private CraftSearch.Budget suggesting;
    private String suggestedFor = "";
    int kind;
    private int listScroll;
    private int listMax;
    private int[] listRect;
    private final ScrollBar bar = new ScrollBar();

    CraftSuggestionsPanel(CraftingTab tab) {
        super(tab);
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

    /** The goal sets for the player's level and skill points. */
    List<Suggestions.GoalSet> goalSets() {
        return Suggestions.goalSets(goalOrder(), combatLevel(), knownPoints());
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
        List<String> t = new ArrayList<>();
        t.add(CraftingTab.defaultType());
        if (t.get(0).equals("ring")) {
            t.clear();
            t.addAll(Types.WEAPONS);
        }
        t.addAll(Types.ARMOUR);
        t.addAll(Types.ACCESSORIES);
        t.addAll(Types.CONSUMABLES);
        return t;
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

    /** Starts the list of the mode shown (pieces or sets) unless it is worked out for this stock already. */
    void startCurrent(boolean force) {
        if (tab.suggestMode == 1) {
            tab.setsPanel.startSets(force);
        } else {
            startSuggestions(force);
        }
    }

    void startSuggestions(boolean force) {
        String k = tab.suggestKey();
        if (!force && k.equals(suggestedFor) && (suggesting != null || !tab.suggestions.isEmpty())) {
            return;
        }
        suggestedFor = k;
        tab.suggestions.clear();
        WhatCanICraft.Stock st = stockForSearch();
        List<Ingredient> all = tab.ingredients;
        Map<String, CraftSearch.Price> pr = new HashMap<>(tab.prices);
        List<String> types = suggestTypes();
        List<Suggestions.GoalSet> goals = goalSets();
        int combat = combatLevel();
        suggesting = suggestRunner.run(120_000, b -> {
            Suggestions.run(types, goals, combat, st, all, pr::get, 150, b, tab.suggestions::add);
            b.cancel();
        });
    }

    boolean running() {
        return tab.suggestMode == 1 ? tab.setsPanel.running() : suggesting != null && !suggesting.cancelled();
    }

    private boolean kindShown(String type) {
        return switch (KINDS[kind]) {
            case "weapon" -> Types.weapon(type);
            case "armour" -> Types.armour(type);
            case "accessory" -> Types.accessory(type);
            case "consumable" -> Types.consumable(type);
            default -> true;
        };
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int cy = filters(g, x, y, w, mx, my) + Theme.SP_4;
        int bottom = y + h;
        boolean noStock = tab.stock.ingredients().isEmpty() && tab.stock.materials().isEmpty() && CraftingTab.previewLevels.isEmpty();
        if (noStock) {
            UiEmpty.draw(g, tab.font, x, cy, w, bottom - cy, w < CraftingTab.TWO_COLUMNS_MIN, "package", L.t("wynnlabs.craft.suggest_no_stock"),
                    List.of(L.t("wynnlabs.craft.suggest_no_stock_1"), L.t("wynnlabs.craft.suggest_no_stock_2"),
                            L.t("wynnlabs.craft.suggest_no_stock_3")), null, null, mx, my);
            return;
        }
        listRect = new int[] {x, cy, w, bottom - cy};
        int cw = w - ScrollBar.W - Theme.SP_2;
        tab.clip = listRect;
        g.enableScissor(x, cy, x + w, bottom);
        int used = tab.suggestMode == 1 ? tab.setsPanel.list(g, x, cy - listScroll, cw, cy, bottom, mx, my)
                : list(g, x, cy - listScroll, cw, cy, bottom, mx, my);
        g.disableScissor();
        tab.clip = null;
        listMax = Math.max(0, used - (bottom - cy));
        listScroll = Math.min(listScroll, listMax);
        bar.place(x + w - ScrollBar.W, cy, bottom - cy, bottom - cy, used, listScroll);
        bar.draw(g, mx, my);
    }

    void scrolled(double mx, double my, double v) {
        listScroll = Math.max(0, Math.min(listMax, listScroll - (int) Math.signum(v) * Theme.SP_7 * 2));
    }

    boolean barPressed(double mx, double my) {
        if (bar.mouseClicked(mx, my)) {
            listScroll = bar.offset();
            return true;
        }
        return false;
    }

    boolean barDragged(double my) {
        if (bar.mouseDragged(my)) {
            listScroll = bar.offset();
            return true;
        }
        return false;
    }

    void barReleased() {
        bar.mouseReleased();
    }

    /** Tek parça / Set, the kind chips, and Yenile on the right; returns the y under them. */
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
        cx += Theme.SP_3;
        int rw = Ui.CHIP_H + 2;
        int limit = x + w - rw - Theme.SP_4;
        for (int i = 0; i < KINDS.length; i++) {
            String label = L.t("wynnlabs.craft.suggest_filter." + KINDS[i]);
            int cw = Ui.chipWidth(tab.font, label);
            if (cx + cw > limit) {
                break;                                  // a narrow page keeps the first ones
            }
            Ui.chip(g, tab.font, label, cx, y, cw, kind == i, false, false, mx, my);
            int k = i;
            tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> {
                kind = k;
                listScroll = 0;
            }, null, null));
            cx += cw + Theme.SP_2;
        }
        Ui.iconButton(g, "refresh", x + w - rw, y, false, mx, my);
        tab.acts.add(new Act(x + w - rw, y, rw, Ui.CHIP_H, () -> startCurrent(true), null, List.of(L.c("wynnlabs.craft.suggest_refresh_tip"))));
        return y + Ui.CHIP_H;
    }

    /** The single pieces in their two groups; returns the height. */
    private int list(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        List<Suggestions.Suggestion> own = new ArrayList<>();
        List<Suggestions.Suggestion> buy = new ArrayList<>();
        List<String> goals = goalOrder();
        for (Suggestions.Suggestion s : tab.suggestions) {
            if (kindShown(s.recipe().type())) {
                (s.fromStock() ? own : buy).add(s);
            }
        }
        java.util.Comparator<Suggestions.Suggestion> order = (a, b) -> {
            int ga = goals.indexOf(a.goal().key());
            int gb = goals.indexOf(b.goal().key());
            return ga != gb ? Integer.compare(ga, gb) : Double.compare(b.layout().score(), a.layout().score());
        };
        own.sort(order);
        buy.sort(order);
        int cy = y;
        if (own.isEmpty() && buy.isEmpty()) {
            Type.MUTED.draw(g, tab.font, L.t(running() ? "wynnlabs.craft.suggest_working" : "wynnlabs.craft.suggest_none"), x, cy, Theme.INK_3);
            return Type.MUTED.lineHeight;
        }
        int rowH = Type.LABEL.lineHeight + Type.META.lineHeight;
        for (int grp = 0; grp < 2; grp++) {
            List<Suggestions.Suggestion> list = grp == 0 ? own : buy;
            if (list.isEmpty()) {
                continue;
            }
            cy = tab.recipePanel.head(g, L.t(grp == 0 ? "wynnlabs.craft.group_own" : "wynnlabs.craft.group_buy"),
                    String.valueOf(list.size()), x, cy, w);
            for (Suggestions.Suggestion s : list) {
                if (cy + rowH > top && cy < bottom) {
                    row(g, s, x, cy, w, rowH, mx, my);
                }
                cy += rowH + Theme.SP_2;
            }
            cy += Theme.SP_5;
        }
        if (running()) {
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.craft.suggest_working"), x, cy, Theme.INK_3);
            cy += Type.MUTED.lineHeight;
        }
        return cy - y;
    }

    private void row(GuiGraphics g, Suggestions.Suggestion s, int x, int y, int w, int rowH, int mx, int my) {
        CraftData.Recipe r = s.recipe();
        CraftSearch.Layout l = s.layout();
        boolean hover = in(mx, my, x, y, w, rowH);
        CraftIcons.typeSlot(g, r.type(), x, y + (rowH - 18) / 2, 18, Theme.INK_2);
        int tx = x + 18 + Theme.SP_3;
        String open = L.t("wynnlabs.craft.suggest_open");
        int ow = Ui.chipWidth(tab.font, open);
        int right = x + w - ow;
        Ui.chip(g, tab.font, open, right, y + (rowH - Ui.CHIP_H) / 2, ow, false, false, false, mx, my);
        clipped(right, y + (rowH - Ui.CHIP_H) / 2, ow, Ui.CHIP_H, () -> openSuggestion(s), null, List.of(L.c("wynnlabs.craft.suggest_open_tip")));
        right -= Theme.SP_4;
        boolean locked = locked(s);
        String mark = locked ? L.t("wynnlabs.craft.suggest_locked", Professions.name(r.profession().toUpperCase(Locale.ROOT)), needs(s))
                : l.bought().isEmpty() ? L.t("wynnlabs.craft.in_stock_all")
                : L.n("wynnlabs.craft.to_buy_n", l.bought().size()) + costPart(tab.liveCost(l.bought()));
        int sw = Type.META.width(tab.font, mark) + 2 * Theme.SP_2;
        Ui.seal(g, tab.font, mark, right - sw, y + (rowH - Ui.SEAL_H) / 2, locked ? Theme.BAD : l.bought().isEmpty() ? Theme.GOOD : Theme.WARN);
        right -= sw + Theme.SP_4;
        String name = L.t("wynnlabs.craft.suggest_name", L.t("wynnlabs.craft.goalset." + s.goal().key()), typeName(r.type()))
                + " · Lv " + r.bracket().replace('-', '–');
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, name, right - tx), tx, y, hover ? Theme.LINK : Theme.TIER_LEGENDARY);
        StringBuilder ids = new StringBuilder();
        int shown = 0;
        for (String id : s.goal().goal().weights().keySet()) {
            Craft.Range v = l.result().ids().get(id);
            if (v == null || shown == 2) {
                continue;
            }
            ids.append(ids.isEmpty() ? "" : " · ").append(tab.statName(id)).append(' ').append(range(v, tab.unit(id)));
            shown++;
        }
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, ids.toString(), right - tx), tx, y + Type.LABEL.lineHeight, Theme.GOOD);
        List<Component> tip = new ArrayList<>();
        for (Ingredient in : l.slots()) {
            tip.add(Component.literal(in.empty() ? "—" : in.name() + (tab.owned(in.name()) > 0 ? " ×" + tab.owned(in.name()) : ""))
                    .withStyle(in.empty() ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        }
        List<CraftChecks.Gap> gaps = r.consumable() ? List.of() : CraftChecks.gaps(l.result().requirements(), tab.points);
        if (!gaps.isEmpty()) {
            tip.add(L.c("wynnlabs.craft.cannot_wear", CraftFormat.skill(gaps.get(0).skill()), gaps.get(0).missing()).withStyle(ChatFormatting.RED));
        }
        clipped(x, y, w - ow - Theme.SP_4, rowH, () -> openSuggestion(s), null, tip);
    }

    /** A suggestion whose recipe or an ingredient needs a higher profession level than the player has. */
    private boolean locked(Suggestions.Suggestion s) {
        int lv = tab.level(s.recipe().profession());
        if (s.recipe().minLevel() > lv) {
            return true;
        }
        for (Ingredient in : s.layout().slots()) {
            if (!in.empty() && in.level() > lv) {
                return true;
            }
        }
        return false;
    }

    /** The profession level a suggestion needs: its recipe's or its highest ingredient's. */
    private static int needs(Suggestions.Suggestion s) {
        int n = s.recipe().minLevel();
        for (Ingredient in : s.layout().slots()) {
            n = Math.max(n, in.empty() ? 0 : in.level());
        }
        return n;
    }

    private void openSuggestion(Suggestions.Suggestion s) {
        tab.type = s.recipe().type();
        tab.minLevel = s.recipe().minLevel();
        tab.tier1 = s.tier1();
        tab.tier2 = s.tier2();
        tab.speed = Craft.Speed.NORMAL;
        for (int i = 0; i < Craft.SLOTS; i++) {
            Ingredient in = s.layout().slots().get(i);
            tab.slots[i] = in.empty() ? null : in.name();
        }
        tab.weights.clear();
        tab.weights.putAll(s.goal().goal().weights());
        tab.view = CraftingTab.VIEW_RECIPE;
        tab.scroll = 0;
        tab.flash.accept(L.t("wynnlabs.craft.applied"));
    }

    /** " · ~3 EB", or nothing while no price is known. */
    static String costPart(String cost) {
        return cost.equals("?") ? "" : " · ~" + cost;
    }

    /** For the sets' rows: whether the suggestions' kind filter keeps a type. */
    boolean shows(String type) {
        return kindShown(type);
    }

    /** A set's pieces are kept by the kind filter when any is. */
    boolean shows(SetSuggestions.CraftSet s) {
        if (kind == 0) {
            return true;
        }
        return s.pieces().stream().anyMatch(p -> p.layout() != null && kindShown(p.type()));
    }
}
