package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftChecks;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.SavedRecipes;
import io.github.anilken.wynnlabs.crafting.StockView;
import io.github.anilken.wynnlabs.crafting.WynnBuilderCode;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.anilken.wynnlabs.ui.CraftFormat.range;
import static io.github.anilken.wynnlabs.ui.CraftFormat.skill;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * The saved recipes, in their drawer: save the recipe on the page under a name, copy its code or
 * read one from the clipboard (WynnBuilder's, or the mod's own), and the list: a row opens its
 * recipe; on the hovered row copy, rename and delete (a second click deletes).
 */
final class CraftSavedPanel extends CraftPanel {

    private String deleteArmed;
    private long deleteArmedAt;
    String renaming;

    CraftSavedPanel(CraftingTab tab) {
        super(tab);
    }

    private void act(int x, int y, int w, int h, Runnable r, List<Component> tip) {
        tab.overlay.add(new Act(x, y, w, h, r, null, tip));
    }

    /** The drawer's content from y across w; returns its height. */
    int content(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        SavedRecipes saved = Crafting.saved();
        int cy = y;
        // the name and Save (or Rename)
        String save = L.t(renaming != null ? "wynnlabs.craft.rename" : "wynnlabs.craft.save");
        int bw = Ui.chipWidth(tab.font, save);
        int fw = w - bw - Theme.SP_2;
        UiInput.input(g, tab.font, tab.saveName, L.t("wynnlabs.craft.saved_name"), x, cy, fw, true, false, mx, my);
        act(x, cy, fw, UiInput.H, () -> UiInput.focus(tab.saveName, tab.screen::setFocused), null);
        Ui.chip(g, tab.font, save, x + fw + Theme.SP_2, cy, bw, true, false, false, mx, my);
        act(x + fw + Theme.SP_2, cy, bw, Ui.CHIP_H, this::saveCurrent, null);
        cy += UiInput.H + Theme.SP_3;
        // the code: copy this recipe's, or read one
        int cx = x;
        String copy = L.t("wynnlabs.craft.code");
        String paste = L.t("wynnlabs.craft.paste");
        int cw = Ui.iconChipWidth(tab.font, "copy", copy, false);
        Ui.iconChip(g, tab.font, "copy", copy, false, cx, cy, cw, false, mx, my);
        act(cx, cy, cw, Ui.CHIP_H, this::copyCode, List.of(L.c("wynnlabs.craft.tip.copy")));
        cx += cw + Theme.SP_2;
        int pw = Ui.iconChipWidth(tab.font, "clipboard", paste, false);
        Ui.iconChip(g, tab.font, "clipboard", paste, false, cx, cy, pw, false, mx, my);
        act(cx, cy, pw, Ui.CHIP_H, this::pasteCode, List.of(L.c("wynnlabs.craft.tip.paste")));
        cy += Ui.CHIP_H + Theme.SP_5;
        if (saved.all().isEmpty()) {
            return Type.MUTED.drawWrapped(g, tab.font, L.t("wynnlabs.craft.saved_empty"), x, cy, w, 0, Theme.INK_3) - y;
        }
        int rowH = Type.LABEL.lineHeight + Type.META.lineHeight;
        for (SavedRecipes.Saved s : saved.all()) {
            if (cy + rowH > top && cy < bottom) {
                row(g, saved, s, x, cy, w, rowH, mx, my);
            }
            cy += rowH + Theme.SP_2;
        }
        return cy - Theme.SP_2 - y;
    }

    private void row(GuiGraphics g, SavedRecipes saved, SavedRecipes.Saved s, int x, int y, int w, int rowH, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, rowH);
        CraftIcons.typeSlot(g, s.type(), x, y + (rowH - 18) / 2, 18, Theme.INK_2);
        int tx = x + 18 + Theme.SP_3;
        CraftData.Recipe r = s.recipe();
        String sub = typeName(s.type()) + " · Lv " + (r == null ? "?" : r.bracket().replace('-', '–'));
        String mark = null;
        boolean ok = true;
        if (r != null) {
            List<Ingredient> lay = new ArrayList<>();
            for (String n : s.slots()) {
                lay.add(tab.ing(n));
            }
            Craft.Result c = Craft.craft(r, s.tier1(), s.tier2(), s.attackSpeed(), lay);
            String main = s.goal() == null || s.goal().weights() == null || s.goal().weights().isEmpty() ? null
                    : s.goal().weights().keySet().iterator().next();
            Craft.Range v = main == null ? null : c.ids().get(main);
            if (v != null) {
                sub += " · " + tab.statName(main) + " " + range(v, tab.unit(main));
            }
            List<CraftChecks.Gap> gaps = r.consumable() ? List.of() : CraftChecks.gaps(c.requirements(), tab.points);
            boolean mats = StockView.missing(r, s.tier1(), s.tier2(), tab.stock).isEmpty();
            ok = gaps.isEmpty() && mats;
            mark = !gaps.isEmpty() ? L.t("wynnlabs.craft.cannot_wear", skill(gaps.get(0).skill()), gaps.get(0).missing())
                    : mats ? L.t("wynnlabs.craft.mats_ok") : L.t("wynnlabs.craft.mats_short");
        }
        int right = x + w;
        if (hover) {
            // copy, rename, delete on the hovered row
            String name = s.name();
            boolean armed = name.equals(deleteArmed) && System.currentTimeMillis() - deleteArmedAt < 3000;
            String[][] icons = {{"trash", armed ? "wynnlabs.craft.delete_again" : "wynnlabs.craft.delete_tip"},
                {"pencil", "wynnlabs.craft.rename_tip"}, {"copy", "wynnlabs.craft.copy_tip"}};
            for (String[] ic : icons) {
                right -= Ui.CHIP_H + 2;
                int by = y + (rowH - Ui.CHIP_H) / 2;
                Ui.iconButton(g, ic[0], right, by, ic[0].equals("trash") && armed, mx, my);
                String what = ic[0];
                act(right, by, Ui.CHIP_H + 2, Ui.CHIP_H, () -> rowAction(saved, name, what), List.of(L.c(ic[1])));
                right -= Theme.SP_1;
            }
            right -= Theme.SP_3;
        } else if (mark != null) {
            int sw = Type.META.width(tab.font, mark) + 2 * Theme.SP_2;
            Ui.seal(g, tab.font, mark, right - sw, y + (rowH - Ui.SEAL_H) / 2, ok ? Theme.GOOD : Theme.WARN);
            right -= sw + Theme.SP_3;
        }
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, s.name(), right - tx), tx, y, hover ? Theme.LINK : Theme.INK);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, right - tx), tx, y + Type.LABEL.lineHeight, Theme.INK_3);
        act(x, y, w, rowH, () -> openSaved(s), null);
    }

    private void rowAction(SavedRecipes saved, String name, String what) {
        switch (what) {
            case "copy" -> {
                SavedRecipes.Saved c = saved.copy(name);
                Crafting.persist();
                if (c != null) {
                    tab.flash.accept(L.t("wynnlabs.craft.copied", c.name()));
                }
            }
            case "pencil" -> {
                if (name.equals(renaming)) {
                    stopRenaming();
                    return;
                }
                renaming = name;
                tab.saveName.setValue(name);
                UiInput.focus(tab.saveName, tab.screen::setFocused);
            }
            default -> deleteClick(name);
        }
    }

    private void deleteClick(String name) {
        long now = System.currentTimeMillis();
        if (name.equals(deleteArmed) && now - deleteArmedAt < 3000) {
            Crafting.saved().delete(name);
            Crafting.persist();
            deleteArmed = null;
            tab.flash.accept(L.t("wynnlabs.craft.deleted", name));
        } else {
            deleteArmed = name;
            deleteArmedAt = now;
        }
    }

    /** Back from renaming a saved recipe to saving a new one: the button reads "Kaydet" again. */
    void stopRenaming() {
        renaming = null;
        tab.saveName.setValue("");
        tab.saveName.setFocused(false);
    }

    void saveCurrent() {
        String name = tab.saveName.getValue().strip();
        if (name.isEmpty()) {
            UiInput.focus(tab.saveName, tab.screen::setFocused);
            tab.flash.accept(L.t("wynnlabs.craft.name_first"));
            return;
        }
        SavedRecipes saved = Crafting.saved();
        if (renaming != null) {
            if (name.equals(renaming)) {
                stopRenaming();
                return;
            }
            if (saved.rename(renaming, name)) {
                tab.flash.accept(L.t("wynnlabs.craft.renamed", name));
                renaming = null;
                tab.saveName.setValue("");
                Crafting.persist();
            } else {
                tab.flash.accept(L.t("wynnlabs.craft.name_taken", name));
            }
            return;
        }
        SavedRecipes.Saved s = new SavedRecipes.Saved(name, tab.type, tab.minLevel, tab.tier1, tab.tier2, tab.speed.name(),
                java.util.Collections.unmodifiableList(Arrays.asList(tab.slots.clone())), SavedRecipes.SavedGoal.of(tab.goal()),
                System.currentTimeMillis());
        boolean replace = name.equals(deleteArmed);      // a second click on Save with the same taken name replaces it
        if (!saved.save(s, replace)) {
            deleteArmed = name;
            deleteArmedAt = System.currentTimeMillis();
            tab.flash.accept(L.t("wynnlabs.craft.name_taken_replace", name));
            return;
        }
        deleteArmed = null;
        Crafting.persist();
        tab.saveName.setValue("");
        tab.flash.accept(L.t("wynnlabs.craft.saved_one", name));
    }

    private void copyCode() {
        List<String> names = new ArrayList<>(Arrays.asList(tab.slots));
        RecipeCode.Recipe here = new RecipeCode.Recipe(tab.type, tab.minLevel, tab.tier1, tab.tier2, tab.speed, names);
        // WynnBuilder's code, the one everyone shares; the mod's own when WynnBuilder lacks an id
        String code = WynnBuilderCode.encode(here);
        Minecraft.getInstance().keyboardHandler.setClipboard(code != null ? code : RecipeCode.encode(here));
        tab.flash.accept(L.t("wynnlabs.craft.code_copied"));
    }

    private void pasteCode() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        RecipeCode.Recipe r = WynnBuilderCode.looksLike(text) ? WynnBuilderCode.decode(text) : RecipeCode.decode(text, tab.byName.keySet());
        if (r == null) {
            tab.flash.accept(L.t("wynnlabs.craft.code_bad"));
            return;
        }
        tab.type = r.type();
        tab.minLevel = r.minLevel();
        tab.tier1 = r.tier1();
        tab.tier2 = r.tier2();
        tab.speed = r.speed();
        for (int i = 0; i < Craft.SLOTS; i++) {
            tab.slots[i] = r.slots().get(i);
        }
        tab.drawers.close();
        tab.view = CraftingTab.VIEW_RECIPE;
        tab.flash.accept(L.t("wynnlabs.craft.code_read"));
    }

    private void openSaved(SavedRecipes.Saved s) {
        if (s.recipe() == null) {
            return;
        }
        tab.type = s.type();
        tab.minLevel = s.minLevel();
        tab.tier1 = s.tier1();
        tab.tier2 = s.tier2();
        tab.speed = s.attackSpeed();
        for (int i = 0; i < Craft.SLOTS; i++) {
            tab.slots[i] = i < s.slots().size() ? s.slots().get(i) : null;
        }
        if (s.goal() != null) {
            CraftSearch.Goal goal = s.goal().goal();
            tab.weights.clear();
            tab.weights.putAll(goal.weights());
            tab.noNegative.clear();
            tab.noNegative.addAll(goal.noNegative());
            tab.floorIndex = 0;
            for (int i = 0; i < CraftingTab.FLOORS.length; i++) {
                if (CraftingTab.FLOORS[i] == goal.durabilityFloor()) {
                    tab.floorIndex = i;
                }
            }
        }
        tab.drawers.close();
        tab.view = CraftingTab.VIEW_RECIPE;
        tab.flash.accept(L.t("wynnlabs.craft.opened", s.name()));
    }
}
