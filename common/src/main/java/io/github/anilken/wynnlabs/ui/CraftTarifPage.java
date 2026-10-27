package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.Types;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import io.github.anilken.wynnlabs.ui.CraftingTab.Drop;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.github.anilken.wynnlabs.ui.CraftFormat.typeIcon;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * The Tarif page: one row of choices (type, level, goal, speed, conditions) with the one main
 * action "En iyisini bul ›" on its right; then the recipe (materials and the 2×3 grid) beside the
 * crafted item; then the best three layouts. On a narrow page the parts stack and scroll.
 */
final class CraftTarifPage {

    private CraftTarifPage() {}

    /** Draws the page from {@code y} (already scrolled) across {@code w}; returns its height. */
    static int draw(CraftingTab tab, GuiGraphics g, int x, int y, int w, int mx, int my) {
        int cy = chips(tab, g, x, y, w, mx, my) + Theme.SP_4;
        if (tab.twoColumns) {
            int lw = Math.max(200, (w - Theme.SP_7) * 42 / 100);
            int left = tab.recipePanel.draw(g, x, cy, lw, mx, my);
            int rx = x + lw + Theme.SP_7;
            int right = tab.resultPanel.draw(g, rx, cy, x + w - rx, mx, my);
            cy += Math.max(left, right) + Theme.SP_6;
        } else {
            cy += tab.recipePanel.draw(g, x, cy, w, mx, my) + Theme.SP_5;
            cy += tab.resultPanel.draw(g, x, cy, w, mx, my) + Theme.SP_6;
        }
        cy += tab.optimiserPanel.draw(g, x, cy, w, mx, my);
        return cy - y + Theme.SP_4;
    }

    /** The row of choices and the main action; returns the y under it. */
    private static int chips(CraftingTab tab, GuiGraphics g, int x, int y, int w, int mx, int my) {
        boolean running = tab.optimiserPanel.running();
        String go = L.t(running ? "wynnlabs.craft.stop" : "wynnlabs.craft.find_best");
        int bw = Ui.iconButtonLabelWidth(tab.font, go);
        int bx = x + w - bw;
        Ui.iconButtonLabel(g, tab.font, running ? "x" : "sparkles", go, bx, y - 1, bw, mx, my);
        tab.acts.add(new Act(bx, y - 1, bw, Ui.BUTTON_H, running ? tab.optimiserPanel::stop : tab.optimiserPanel::startSearch, null,
                List.of(L.c("wynnlabs.craft.search_tip"))));
        int limit = bx - Theme.SP_4;
        int cx = x;
        int cy = y;
        List<Object[]> chips = new ArrayList<>();     // {icon, label, drop}
        chips.add(new Object[] {typeIcon(tab.type), typeName(tab.type), Drop.TYPE});
        chips.add(new Object[] {null, "Lv " + tab.recipe().bracket().replace('-', '–'), Drop.LEVEL});
        chips.add(new Object[] {"target", goalLabel(tab), Drop.GOAL});
        if (Types.weapon(tab.type)) {
            chips.add(new Object[] {"clock", L.t("wynnlabs.craft.speed." + tab.speed.name().toLowerCase(Locale.ROOT)), Drop.SPEED});
        }
        int conds = tab.noNegative.size() + (tab.capToPoints ? 1 : 0) + (tab.floorIndex > 0 ? 1 : 0);
        chips.add(new Object[] {"plus", conds == 0 ? L.t("wynnlabs.craft.conditions_add") : L.n("wynnlabs.craft.conditions_n", conds),
            Drop.CONDITIONS});
        for (Object[] c : chips) {
            String icon = (String) c[0];
            String label = (String) c[1];
            int cw = Ui.iconChipWidth(tab.font, icon, label, true);
            if (cx > x && cx + cw > limit) {
                cx = x;
                cy += Ui.CHIP_H + Theme.SP_2;
                limit = x + w;
            }
            Drop d = (Drop) c[2];
            UiMenu.chip(g, tab.font, icon, label, cx, cy, tab.open == d, mx, my);
            int[] anchor = {cx, cy, cw, Ui.CHIP_H};
            tab.acts.add(new Act(cx, cy, cw, Ui.CHIP_H, () -> tab.toggle(d, anchor), null, null));
            cx += cw + Theme.SP_2;
        }
        // clear the grid: a quiet icon after the chips
        int iw = Ui.CHIP_H + 2;
        if (cx + iw > limit) {
            cx = x;
            cy += Ui.CHIP_H + Theme.SP_2;
        }
        Ui.iconButton(g, "eraser", cx, cy, false, mx, my);
        tab.acts.add(new Act(cx, cy, iw, Ui.CHIP_H, () -> clear(tab), null, List.of(L.c("wynnlabs.craft.clear_tip"))));
        return Math.max(cy + Ui.CHIP_H, y - 1 + Ui.BUTTON_H);
    }

    /** "Hedef: Spell Damage", "+1" more when there are more. */
    private static String goalLabel(CraftingTab tab) {
        if (tab.weights.isEmpty()) {
            return L.t("wynnlabs.craft.goal_none");
        }
        String first = tab.statName(tab.weights.keySet().iterator().next());
        return L.t("wynnlabs.craft.goal_chip", first) + (tab.weights.size() > 1 ? " +" + (tab.weights.size() - 1) : "");
    }

    static void clear(CraftingTab tab) {
        Arrays.fill(tab.slots, null);
        tab.tier1 = 1;
        tab.tier2 = 1;
        tab.speed = Craft.Speed.NORMAL;
        tab.results = List.of();
    }
}
