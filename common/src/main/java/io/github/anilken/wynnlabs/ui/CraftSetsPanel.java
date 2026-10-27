package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftChecks;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.SavedRecipes;
import io.github.anilken.wynnlabs.crafting.SearchRunner;
import io.github.anilken.wynnlabs.crafting.SetSuggestions;
import io.github.anilken.wynnlabs.crafting.Suggestions;
import io.github.anilken.wynnlabs.crafting.Types;
import io.github.anilken.wynnlabs.crafting.WynnBuilderCode;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.anilken.wynnlabs.ui.CraftFormat.range;
import static io.github.anilken.wynnlabs.ui.CraftFormat.skill;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * Whole suggested sets on the Öneriler page: one row a set in the same two groups as the pieces
 * (from what is owned, with a few bought); its "Parçalar ›" opens the pieces in a drawer, where each
 * goes to the Tarif page or its code is copied, and the whole set is saved or its codes copied.
 */
final class CraftSetsPanel extends CraftPanel {

    private final SearchRunner setRunner = new SearchRunner();
    private CraftSearch.Budget setting;
    private String setsFor = "";

    CraftSetsPanel(CraftingTab tab) {
        super(tab);
    }

    void stop() {
        setRunner.cancel();
        setting = null;
        setsFor = "";
    }

    boolean running() {
        return setting != null && !setting.cancelled();
    }

    void startSets(boolean force) {
        String k = tab.suggestKey();
        if (!force && k.equals(setsFor) && (setting != null || !tab.sets.isEmpty())) {
            return;
        }
        setsFor = k;
        tab.sets.clear();
        var st = tab.suggestionsPanel.stockForSearch();
        List<Ingredient> all = tab.ingredients;
        Map<String, CraftSearch.Price> pr = new HashMap<>(tab.prices);
        String weapon = tab.suggestionsPanel.suggestTypes().get(0);
        List<Suggestions.GoalSet> goals = Suggestions.goalSets(tab.suggestionsPanel.goalOrder());
        int combat = CraftSuggestionsPanel.combatLevel();
        setting = setRunner.run(300_000, b -> {
            SetSuggestions.run(weapon, goals, combat, st, all, pr::get, 120, b, tab.sets::add);
            b.cancel();
        });
    }

    String title(SetSuggestions.CraftSet s) {
        String bracket = "";
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.recipe() != null && Types.armour(pc.type())) {
                bracket = pc.recipe().bracket().replace('-', '–');
                break;
            }
        }
        return L.t("wynnlabs.craft.set_name", L.t("wynnlabs.craft.goalset." + s.goal().key()), bracket);
    }

    private String cost(SetSuggestions.CraftSet s) {
        return tab.liveCost(s.bought());
    }

    /** The sets in their two groups; returns the height. */
    int list(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        List<SetSuggestions.CraftSet> own = new ArrayList<>();
        List<SetSuggestions.CraftSet> buy = new ArrayList<>();
        for (SetSuggestions.CraftSet s : tab.sets) {
            if (tab.suggestionsPanel.shows(s)) {
                (s.fromStock() ? own : buy).add(s);
            }
        }
        List<String> goals = tab.suggestionsPanel.goalOrder();
        java.util.Comparator<SetSuggestions.CraftSet> order = (a, b) -> {
            int ga = goals.indexOf(a.goal().key());
            int gb = goals.indexOf(b.goal().key());
            return ga != gb ? Integer.compare(ga, gb) : Integer.compare(a.step(), b.step());
        };
        own.sort(order);
        buy.sort(order);
        int cy = y;
        if (own.isEmpty() && buy.isEmpty()) {
            Type.MUTED.draw(g, tab.font, L.t(running() ? "wynnlabs.craft.set_working" : "wynnlabs.craft.suggest_none"), x, cy, Theme.INK_3);
            return Type.MUTED.lineHeight;
        }
        int rowH = Type.LABEL.lineHeight + Type.META.lineHeight;
        for (int grp = 0; grp < 2; grp++) {
            List<SetSuggestions.CraftSet> list = grp == 0 ? own : buy;
            if (list.isEmpty()) {
                continue;
            }
            cy = tab.recipePanel.head(g, L.t(grp == 0 ? "wynnlabs.craft.group_own" : "wynnlabs.craft.group_buy"),
                    String.valueOf(list.size()), x, cy, w);
            for (SetSuggestions.CraftSet s : list) {
                if (cy + rowH > top && cy < bottom) {
                    row(g, s, x, cy, w, rowH, mx, my);
                }
                cy += rowH + Theme.SP_2;
            }
            cy += Theme.SP_5;
        }
        return cy - y;
    }

    private void row(GuiGraphics g, SetSuggestions.CraftSet s, int x, int y, int w, int rowH, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, rowH);
        CraftIcons.typeSlot(g, s.pieces().get(0).type(), x, y + (rowH - 18) / 2, 18, Theme.INK_2);
        int tx = x + 18 + Theme.SP_3;
        String open = L.t("wynnlabs.craft.set_view");
        int ow = Ui.chipWidth(tab.font, open);
        int right = x + w - ow;
        Ui.chip(g, tab.font, open, right, y + (rowH - Ui.CHIP_H) / 2, ow, false, false, false, mx, my);
        clipped(right, y + (rowH - Ui.CHIP_H) / 2, ow, Ui.CHIP_H, () -> tab.drawers.openSet(s), null, List.of(L.c("wynnlabs.craft.set_view_tip")));
        right -= Theme.SP_4;
        int notWorn = 0;
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.layout() != null && !pc.recipe().consumable() && !CraftChecks.gaps(pc.layout().result().requirements(), tab.points).isEmpty()) {
                notWorn++;
            }
        }
        String mark = s.bought().isEmpty() ? L.t("wynnlabs.craft.in_stock_all") : L.n("wynnlabs.craft.to_buy_n", s.bought().size()) + CraftSuggestionsPanel.costPart(cost(s));
        int sw = Type.META.width(tab.font, mark) + 2 * Theme.SP_2;
        Ui.seal(g, tab.font, mark, right - sw, y + (rowH - Ui.SEAL_H) / 2, s.bought().isEmpty() ? Theme.GOOD : Theme.WARN);
        right -= sw + Theme.SP_4;
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, title(s), right - tx), tx, y, hover ? Theme.LINK : Theme.TIER_LEGENDARY);
        StringBuilder tot = new StringBuilder();
        int shown = 0;
        for (String id : s.goal().goal().weights().keySet()) {
            Craft.Range v = s.totals().get(id);
            if (v == null || shown == 2) {
                continue;
            }
            tot.append(tot.isEmpty() ? "" : " · ").append(tab.statName(id)).append(' ').append(range(v, tab.unit(id)));
            shown++;
        }
        String sub = L.n("wynnlabs.craft.set_pieces", s.found()) + (tot.isEmpty() ? "" : " · " + tot)
                + (notWorn > 0 ? " · " + L.t("wynnlabs.craft.set_not_worn", notWorn) : "");
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, right - tx), tx, y + Type.LABEL.lineHeight, notWorn > 0 ? Theme.WARN : Theme.GOOD);
        clipped(x, y, w - ow - Theme.SP_4, rowH, () -> tab.drawers.openSet(s), null, null);
    }

    /** The drawer: the set's pieces, then save all and copy all codes; returns the height. */
    int detail(GuiGraphics g, SetSuggestions.CraftSet s, int x, int y, int w, int mx, int my) {
        if (s == null) {
            return 0;
        }
        int cy = y;
        int maxSkill = -1;
        for (int k = 0; k < 5; k++) {
            if (s.maxRequirements()[k] > 0 && (maxSkill < 0 || s.maxRequirements()[k] > s.maxRequirements()[maxSkill])) {
                maxSkill = k;
            }
        }
        String req = maxSkill < 0 ? L.t("wynnlabs.craft.set_no_req")
                : L.t("wynnlabs.craft.set_req", skill(maxSkill), s.maxRequirements()[maxSkill], tab.points[maxSkill]);
        boolean reqOk = maxSkill < 0 || tab.points[maxSkill] >= s.maxRequirements()[maxSkill];
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, req, w), x, cy, reqOk ? Theme.INK_2 : Theme.BAD);
        cy += Type.META.lineHeight + Theme.SP_4;
        int rowH = Type.BODY.lineHeight + Type.META.lineHeight;
        for (SetSuggestions.Piece pc : s.pieces()) {
            CraftIcons.typeSlot(g, pc.type(), x, cy + (rowH - 18) / 2, 18, pc.layout() == null ? Theme.INK_3 : Theme.INK_2);
            int tx = x + 18 + Theme.SP_3;
            String nm = typeName(pc.type()) + (pc.recipe() == null ? "" : " · Lv " + pc.recipe().bracket().replace('-', '–'));
            if (pc.layout() == null) {
                Type.BODY.draw(g, tab.font, nm, tx, cy, Theme.INK_3);
                Type.META.draw(g, tab.font, L.t("wynnlabs.craft.set_piece_none"), tx, cy + Type.BODY.lineHeight, Theme.INK_3);
                cy += rowH + Theme.SP_2;
                continue;
            }
            String openL = L.t("wynnlabs.craft.suggest_open");
            String codeL = L.t("wynnlabs.craft.code");
            int ow = Ui.chipWidth(tab.font, openL);
            int cw = Ui.chipWidth(tab.font, codeL);
            int bx = x + w - ow;
            int by = cy + (rowH - Ui.CHIP_H) / 2;
            Ui.chip(g, tab.font, openL, bx, by, ow, false, false, false, mx, my);
            SetSuggestions.Piece piece = pc;
            tab.overlay.add(new Act(bx, by, ow, Ui.CHIP_H, () -> openPiece(s, piece), null, List.of(L.c("wynnlabs.craft.suggest_open_tip"))));
            bx -= Theme.SP_2 + cw;
            Ui.chip(g, tab.font, codeL, bx, by, cw, false, false, false, mx, my);
            tab.overlay.add(new Act(bx, by, cw, Ui.CHIP_H, () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(pieceCode(piece));
                tab.flash.accept(L.t("wynnlabs.craft.code_copied"));
            }, null, null));
            int right = bx - Theme.SP_4;
            Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, nm, right - tx), tx, cy);
            String v = pieceValue(s, pc);
            String note = pc.layout().bought().isEmpty() ? L.t("wynnlabs.craft.in_stock_all") : L.n("wynnlabs.craft.to_buy_n", pc.layout().bought().size());
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, v + " · " + note, right - tx), tx, cy + Type.BODY.lineHeight, Theme.GOOD);
            List<Component> tip = new ArrayList<>();
            for (Ingredient in : pc.layout().slots()) {
                tip.add(Component.literal(in.empty() ? "—" : in.name()).withStyle(in.empty() ? ChatFormatting.GRAY : ChatFormatting.WHITE));
            }
            tab.overlay.add(new Act(x, cy, right - x, rowH, null, null, tip));
            cy += rowH + Theme.SP_2;
        }
        cy += Theme.SP_4;
        String save = L.t("wynnlabs.craft.set_save_all");
        String codes = L.t("wynnlabs.craft.set_copy_all");
        int sw = Ui.iconChipWidth(tab.font, "device-floppy", save, false);
        Ui.iconChip(g, tab.font, "device-floppy", save, false, x, cy, sw, false, mx, my);
        tab.overlay.add(new Act(x, cy, sw, Ui.CHIP_H, () -> saveSet(s), null, List.of(L.c("wynnlabs.craft.set_save_tip"))));
        int cw = Ui.iconChipWidth(tab.font, "copy", codes, false);
        Ui.iconChip(g, tab.font, "copy", codes, false, x + sw + Theme.SP_2, cy, cw, false, mx, my);
        tab.overlay.add(new Act(x + sw + Theme.SP_2, cy, cw, Ui.CHIP_H, () -> copySetCodes(s), null, null));
        return cy + Ui.CHIP_H - y;
    }

    private String pieceValue(SetSuggestions.CraftSet s, SetSuggestions.Piece pc) {
        for (String id : s.goal().goal().weights().keySet()) {
            Craft.Range v = pc.layout().result().ids().get(id);
            if (v != null) {
                return tab.statName(id) + " " + range(v, tab.unit(id));
            }
        }
        return "✔";
    }

    private RecipeCode.Recipe pieceRecipe(SetSuggestions.Piece pc) {
        List<String> names = new ArrayList<>();
        for (Ingredient in : pc.layout().slots()) {
            names.add(in.empty() ? null : in.name());
        }
        return new RecipeCode.Recipe(pc.recipe().type(), pc.recipe().minLevel(), pc.tier1(), pc.tier2(), Craft.Speed.NORMAL, names);
    }

    private String pieceCode(SetSuggestions.Piece pc) {
        RecipeCode.Recipe r = pieceRecipe(pc);
        String code = WynnBuilderCode.encode(r);
        return code != null ? code : RecipeCode.encode(r);
    }

    private void copySetCodes(SetSuggestions.CraftSet s) {
        List<String> lines = new ArrayList<>();
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.layout() != null) {
                lines.add(typeName(pc.type()) + ": " + pieceCode(pc));
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));
        tab.flash.accept(L.n("wynnlabs.craft.set_codes_copied", lines.size()));
    }

    private void openPiece(SetSuggestions.CraftSet s, SetSuggestions.Piece pc) {
        tab.type = pc.recipe().type();
        tab.minLevel = pc.recipe().minLevel();
        tab.tier1 = pc.tier1();
        tab.tier2 = pc.tier2();
        tab.speed = Craft.Speed.NORMAL;
        for (int i = 0; i < Craft.SLOTS; i++) {
            Ingredient in = pc.layout().slots().get(i);
            tab.slots[i] = in.empty() ? null : in.name();
        }
        tab.weights.clear();
        tab.weights.putAll(s.goal().goal().weights());
        tab.drawers.close();
        tab.view = CraftingTab.VIEW_RECIPE;
        tab.scroll = 0;
        tab.flash.accept(L.t("wynnlabs.craft.applied"));
    }

    /** Saves every piece as "Spell set – Helmet" …; the second ring only once. */
    private void saveSet(SetSuggestions.CraftSet s) {
        SavedRecipes saved = Crafting.saved();
        String goal = L.t("wynnlabs.craft.goalset." + s.goal().key());
        java.util.Set<String> done = new java.util.HashSet<>();
        int n = 0;
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.layout() == null || !done.add(pc.type())) {
                continue;
            }
            RecipeCode.Recipe r = pieceRecipe(pc);
            String name = saved.free(L.t("wynnlabs.craft.set_piece_name", goal, typeName(pc.type())));
            saved.save(new SavedRecipes.Saved(name, r.type(), r.minLevel(), r.tier1(), r.tier2(), r.speed().name(),
                    java.util.Collections.unmodifiableList(new ArrayList<>(r.slots())),
                    SavedRecipes.SavedGoal.of(s.goal().goal()), System.currentTimeMillis()), false);
            n++;
        }
        Crafting.persist();
        tab.flash.accept(L.n("wynnlabs.craft.set_saved", n));
    }
}
