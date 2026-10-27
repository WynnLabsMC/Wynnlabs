package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftChecks;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.SearchRunner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.anilken.wynnlabs.ui.CraftFormat.dec;
import static io.github.anilken.wynnlabs.ui.CraftFormat.range;
import static io.github.anilken.wynnlabs.ui.CraftFormat.skill;

/**
 * The best three layouts for the goal ("En iyisini bul ›" starts the search): with what is owned,
 * with up to two bought, and the strongest; each row shows its six ingredients, its result for the
 * goal, what it needs bought and what that costs, and "Uygula". A hover lays the row on the grid.
 */
final class CraftOptimiserPanel extends CraftPanel {

    private static final int MINI = 16;

    private final SearchRunner runner = new SearchRunner();
    private CraftSearch.Budget searching;
    String resultsFor = "";

    CraftOptimiserPanel(CraftingTab tab) {
        super(tab);
    }

    void stop() {
        runner.cancel();
        searching = null;
    }

    boolean running() {
        return searching != null && !searching.cancelled() && searching.progress() < 1.0;
    }

    /** Draws the section from y across w; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String goal = tab.weights.isEmpty() ? null : tab.statName(tab.weights.keySet().iterator().next());
        int cy = tab.recipePanel.head(g, L.t("wynnlabs.craft.best"), goal == null ? null : L.t("wynnlabs.craft.best_for", goal), x, y, w);
        if (running()) {
            Kit.tealBar(g, x, cy + 2, Math.min(w, 240), 6, searching.progress(), false);
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.craft.searching"), x, cy + 2 + 6 + Theme.SP_2, Theme.INK_3);
            return cy + 2 + 6 + Theme.SP_2 + Type.MUTED.lineHeight - y;
        }
        if (tab.results.isEmpty()) {
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, L.t("wynnlabs.craft.search_first"), w), x, cy, Theme.INK_3);
            return cy + Type.MUTED.lineHeight - y;
        }
        String[] names = {L.t("wynnlabs.craft.tier_own"), L.t("wynnlabs.craft.tier_two"), L.t("wynnlabs.craft.tier_any")};
        String mainId = tab.weights.isEmpty() ? null : tab.weights.keySet().iterator().next();
        int labelW = 0;
        for (String n : names) {
            labelW = Math.max(labelW, Type.LABEL.width(tab.font, n));
        }
        String apply = L.t("wynnlabs.craft.apply");
        int aw = Ui.chipWidth(tab.font, apply);
        boolean wide = w >= 380;
        for (int i = 0; i < tab.results.size(); i++) {
            CraftSearch.Outcome o = tab.results.get(i);
            int rh = MINI;
            int ty = Type.BODY.yIn(cy, rh);
            Type.LABEL.draw(g, tab.font, names[i], x, ty);
            int sx = x + labelW + Theme.SP_5;
            CraftSearch.Layout l = o.best();
            if (l == null || l.slots().stream().allMatch(Ingredient::empty)) {
                Type.MUTED.draw(g, tab.font, L.t(i == 0 ? "wynnlabs.craft.no_own" : "wynnlabs.craft.no_layout"), sx, ty, Theme.INK_3);
                cy += rh + Theme.SP_2 + Theme.SP_2;
                continue;
            }
            if (wide) {
                for (int k = 0; k < Craft.SLOTS; k++) {
                    Ingredient in = l.slots().get(k);
                    CraftIcons.slot(g, sx + k * (MINI + Theme.SP_1), cy, MINI, in.empty() ? null : CraftIcons.of(in.name()));
                }
                sx += Craft.SLOTS * (MINI + Theme.SP_1) + Theme.SP_4;
            }
            Craft.Range main = mainId == null ? null : l.result().ids().get(mainId);
            String val = main == null ? dec(l.score(), 1) : tab.statName(mainId) + " " + range(main, tab.unit(mainId));
            if (!o.complete()) {
                val = "≈ " + val;
            }
            int r = x + w - aw;
            boolean ah = in(mx, my, r, cy + (rh - Ui.CHIP_H) / 2, aw, Ui.CHIP_H);
            Ui.chip(g, tab.font, apply, r, cy + (rh - Ui.CHIP_H) / 2, aw, false, false, false, mx, my);
            List<CraftChecks.Gap> gaps = CraftChecks.gaps(l.result().requirements(), tab.points);
            List<Component> tip = layoutTip(o, gaps);
            clipped(r, cy + (rh - Ui.CHIP_H) / 2, aw, Ui.CHIP_H, () -> apply(l), null, tip);
            r -= Theme.SP_4;
            String live = l.bought().isEmpty() ? "?" : tab.liveCost(l.bought());
            if (!live.equals("?")) {
                String cost = "~" + live;
                Type.NUMBER.drawRight(g, tab.font, cost, r, ty, Theme.PRICE);
                r -= Type.NUMBER.width(tab.font, cost) + Theme.SP_4;
            }
            String need = l.bought().isEmpty() ? L.t("wynnlabs.craft.in_stock_all") : L.n("wynnlabs.craft.to_buy_n", l.bought().size());
            int sw = Type.META.width(tab.font, need) + 2 * Theme.SP_2;
            Ui.seal(g, tab.font, need, r - sw, cy + (rh - Ui.SEAL_H) / 2, l.bought().isEmpty() ? Theme.GOOD : Theme.WARN);
            r -= sw + Theme.SP_4;
            Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, val, r - sx), sx, ty, gaps.isEmpty() ? Theme.GOOD : Theme.BAD);
            clipped(x, cy, w - aw - Theme.SP_4, rh, null, null, tip);
            if (ah || in(mx, my, x, cy, w, rh)) {
                tab.nextPreview = l.slots();
            }
            cy += rh + Theme.SP_2 + Theme.SP_2;
        }
        return cy - Theme.SP_2 - y;
    }

    private List<Component> layoutTip(CraftSearch.Outcome o, List<CraftChecks.Gap> gaps) {
        List<Component> tip = new ArrayList<>();
        for (Ingredient i : o.best().slots()) {
            tip.add(Component.literal(i.empty() ? "—" : i.name()).withStyle(i.empty() ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        }
        for (CraftChecks.Gap g : gaps) {
            tip.add(L.c("wynnlabs.craft.gap", skill(g.skill()), g.need(), g.have()).withStyle(ChatFormatting.RED));
        }
        if (!o.best().bought().isEmpty()) {
            tip.add(L.c("wynnlabs.craft.to_buy", String.join(", ", o.best().bought())).withStyle(ChatFormatting.GOLD));
        }
        for (String u : o.best().unpriced()) {
            tip.add(L.c("wynnlabs.craft.no_price", u).withStyle(ChatFormatting.GRAY));
        }
        for (String u : o.best().oldPrices()) {
            tip.add(L.c("wynnlabs.craft.old_price", u).withStyle(ChatFormatting.GRAY));
        }
        if (!o.complete()) {
            tip.add(L.c("wynnlabs.craft.maybe_not_best").withStyle(ChatFormatting.GRAY));
        }
        tip.add(L.c("wynnlabs.craft.best_hint").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    void startSearch() {
        if (tab.weights.isEmpty()) {
            tab.flash.accept(L.t("wynnlabs.craft.goal_empty"));
            return;
        }
        CraftData.Recipe r = tab.recipe();
        Map<String, CraftSearch.Price> pr = new HashMap<>(tab.prices);
        CraftSearch.Request q = new CraftSearch.Request(r, tab.tier1, tab.tier2, tab.speed, tab.goal(), tab.ingredients, tab.stock.ingredients(),
                tab.level(r.profession()), false, pr::get);
        tab.results = List.of();
        resultsFor = tab.key();
        String forKey = resultsFor;
        searching = runner.submit(q, 3000, out -> {
            if (forKey.equals(resultsFor)) {
                tab.results = List.copyOf(out);
            }
        });
    }

    void apply(CraftSearch.Layout l) {
        for (int i = 0; i < Craft.SLOTS; i++) {
            Ingredient in = l.slots().get(i);
            tab.slots[i] = in.empty() ? null : in.name();
        }
        tab.flash.accept(L.t("wynnlabs.craft.applied"));
    }
}
