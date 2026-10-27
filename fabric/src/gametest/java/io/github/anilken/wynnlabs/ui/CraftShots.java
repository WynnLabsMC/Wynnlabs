package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.ProfessionXp;
import io.github.anilken.wynnlabs.crafting.SavedRecipes;
import io.github.anilken.wynnlabs.model.ProfBomb;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The crafting tab's shots (-PpreviewOnly=crafting): Tarif with a relik, the optimiser's layouts,
 * a tier menu, the picker, shopping and saved drawers, Öneriler (pieces and sets, a set's drawer),
 * Meslek (Weaponsmithing 23 on its road with a bomb, a finished one, Mining's spots), and the empty
 * pages (no ingredient data, no stock). The sample ingredients stand in for Wynntils'.
 */
final class CraftShots {

    private CraftShots() {}

    static void shots(ClientGameTestContext ctx, Consumer<String> shot) throws Exception {
        CraftingTab tab = CraftingTab.INSTANCE;
        ctx.runOnClient(mc -> {
            CraftingTab.previewIngredients = List.of();
            ((RoadmapScreen) mc.screen).openTab(RoadmapScreen.TAB_CRAFT);
        });
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(5);
        shot.accept("craft-empty");
        ctx.runOnClient(mc -> {
            CraftingTab.previewIngredients = CraftPreview.ingredients();
            CraftPreview.levels();
            CraftPreview.recipe("relik", 103, 3, 2, "Foul Fairy Dust", "Obelisk Core", "Dizzying Void",
                    "Depurinated Genome", "Exquisite Marsh Scale", "Gunkfueled Core");
            tab.view = CraftingTab.VIEW_RECIPE;
        });
        ctx.waitTicks(30);                          // the tab looks for the list again every second
        shot.accept("craft-recipe");
        ctx.runOnClient(mc -> CraftPreview.call("startSearch"));
        ctx.waitFor(mc -> !tab.results.isEmpty(), 20 * 30);
        ctx.waitTicks(3);
        shot.accept("craft-results");
        ctx.runOnClient(mc -> tab.toggle(CraftingTab.Drop.TIER1, firstTierSeal(tab)));
        ctx.waitTicks(3);
        shot.accept("craft-tier-menu");
        ctx.runOnClient(mc -> tab.open = CraftingTab.Drop.NONE);
        ctx.runOnClient(mc -> tab.drawers.openPicker(2));
        ctx.waitTicks(4);
        shot.accept("craft-drawer-picker");
        ctx.runOnClient(mc -> {
            tab.drawers.close();
            tab.drawers.openShop();
        });
        ctx.waitTicks(4);
        shot.accept("craft-drawer-shop");
        ctx.runOnClient(mc -> {
            tab.drawers.close();
            var saved = Crafting.saved();
            var goal = SavedRecipes.SavedGoal.of(CraftSearch.Goal.of("spellDamage"));
            saved.save(new SavedRecipes.Saved("Relik büyü", "relik", 103, 3, 2, "NORMAL", Arrays.asList("Foul Fairy Dust", "Obelisk Core",
                    "Dizzying Void", "Depurinated Genome", null, null), goal, 1L), true);
            saved.save(new SavedRecipes.Saved("Yüzük mana", "ring", 80, 2, 3, "NORMAL", Arrays.asList("Major's Badge", null, null, null,
                    null, null), goal, 2L), true);
            tab.drawers.openSaved();
        });
        ctx.waitTicks(4);
        shot.accept("craft-drawer-saved");
        ctx.runOnClient(mc -> {
            tab.drawers.close();
            tab.view = CraftingTab.VIEW_SUGGEST;
            tab.suggestMode = 0;
            CraftPreview.call("startSuggestionsNow");
        });
        ctx.waitTicks(20 * 12);
        shot.accept("craft-suggest");
        ctx.runOnClient(mc -> {
            tab.suggestMode = 1;
            CraftPreview.call("startSetsNow");
        });
        ctx.waitTicks(20 * 25);
        shot.accept("craft-suggest-sets");
        ctx.runOnClient(mc -> {
            if (!tab.sets.isEmpty()) {
                tab.drawers.openSet(tab.sets.get(0));
            }
        });
        ctx.waitTicks(4);
        shot.accept("craft-drawer-set");
        // Meslek: Weaponsmithing 23 with 40% of its level, a Profession XP bomb here and two elsewhere
        ctx.waitFor(mc -> !"loading".equals(io.github.anilken.wynnlabs.search.ItemIndex.get().missing()), 20 * 60);
        ctx.runOnClient(mc -> {
            tab.drawers.close();
            Map<String, Integer> lv = new LinkedHashMap<>(CraftingTab.previewLevels);
            lv.put("weaponsmithing", 23);
            lv.put("mining", 34);
            lv.put("woodcutting", 18);
            CraftingTab.previewLevels = lv;
            CraftRoadData.previewXp = new int[] {1640, 4100};
            CraftRoadData.previewBombs = List.of(new ProfBomb(false, "WC12", 12 * 60_000, true),
                    new ProfBomb(false, "WC31", 3 * 60_000, false), new ProfBomb(false, "WC7", 14 * 60_000, false));
            ProfessionXp.gained("MINING", 38);
            tab.view = CraftingTab.VIEW_ROAD;
            tab.roadPanel.pickProf("WEAPONSMITHING");
            // the first step done by hand: it shows faded with its tick
            io.github.anilken.wynnlabs.crafting.RoadChoices.get().ticked("WEAPONSMITHING").add("23:Gold Ingot");
        });
        ctx.waitTicks(5);
        shot.accept("craft-road");
        ctx.runOnClient(mc -> CraftRoadData.previewBombs = List.of());
        ctx.runOnClient(mc -> tab.roadPanel.pickProf("ALCHEMISM"));
        ctx.waitTicks(4);
        shot.accept("craft-road-done");
        ctx.runOnClient(mc -> tab.roadPanel.pickProf("MINING"));
        ctx.waitTicks(4);
        shot.accept("craft-road-gather");
        ctx.runOnClient(mc -> {
            io.github.anilken.wynnlabs.crafting.RoadChoices.get().ticked("WEAPONSMITHING").clear();
            tab.roadPanel.pickProf("FISHING");
        });
        ctx.waitTicks(4);
        shot.accept("craft-road-fishing");
        // Öneriler without a stock: the empty page
        ctx.runOnClient(mc -> {
            CraftingTab.previewLevels = Map.of();
            tab.stock = new io.github.anilken.wynnlabs.crafting.WhatCanICraft.Stock(Map.of(), Map.of(), Map.of());
            tab.view = CraftingTab.VIEW_SUGGEST;
        });
        ctx.waitTicks(3);
        shot.accept("craft-suggest-empty");
        ctx.runOnClient(mc -> {
            CraftPreview.levels();
            CraftRoadData.previewXp = null;
            CraftRoadData.previewBombs = null;
            tab.view = CraftingTab.VIEW_RECIPE;
        });
    }

    /** The first material's tier chip, from the last frame's click areas (the smallest one on the first material row). */
    private static int[] firstTierSeal(CraftingTab tab) {
        for (CraftingTab.Act a : tab.acts) {
            if (a.h() == UiTierSeal.H && a.tip() != null && !a.tip().isEmpty()
                    && a.tip().get(0).getString().contains("Tier")) {
                return new int[] {a.x(), a.y(), a.w(), a.h()};
            }
        }
        return new int[] {200, 120, 30, UiTierSeal.H};
    }
}
