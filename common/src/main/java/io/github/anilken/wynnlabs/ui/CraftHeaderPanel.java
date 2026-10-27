package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The crafting tab's frame: on the title line the recipe's profession and level and the saved
 * recipes' link, under it the three pages (Tarif, Öneriler, Meslek), and the empty page shown
 * while there is no ingredient data.
 */
final class CraftHeaderPanel extends CraftPanel {

    CraftHeaderPanel(CraftingTab tab) {
        super(tab);
    }

    /** Wynntils has no ingredient list yet: the shared empty page, a ghost of the grid under it. */
    void noData(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        UiEmpty.draw(g, tab.font, x, y, w, h, w < CraftingTab.TWO_COLUMNS_MIN, "hammer", L.t("wynnlabs.craft.no_data"),
                List.of(L.t("wynnlabs.craft.no_data_step1"), L.t("wynnlabs.craft.no_data_step2"), L.t("wynnlabs.craft.no_data_step3")),
                null, (gg, gx, gy, gw, gh) -> {
                    int s = 30;
                    int ox = gx + (gw - (2 * s + Theme.SP_4)) / 2;
                    for (int i = 0; i < 6 && (i / 2 + 1) * (s + Theme.SP_3) <= gh; i++) {
                        int sx = ox + (i % 2) * (s + Theme.SP_4);
                        int sy = gy + (i / 2) * (s + Theme.SP_3);
                        gg.fill(sx, sy, sx + s, sy + s, 0x0A000000);
                        Kit.outline(gg, sx, sy, s, s, 0x30624E3C);
                    }
                }, mx, my);
    }

    /** On the title line, right: the recipe's profession and level, then the saved recipes' button (and its count). */
    void drawHeader(GuiGraphics g, int x, int ty, int w, int mx, int my) {
        int r = x + w;
        int by = ty - 3;                            // the chip on the title's line, as every title row's control
        int n = Crafting.saved().all().size();
        if (n > 0) {
            String c = String.valueOf(n);
            int sw = Type.META.width(tab.font, c) + 2 * Theme.SP_2;
            Ui.seal(g, tab.font, c, r - sw, by + (Ui.CHIP_H - Ui.SEAL_H) / 2, Theme.GOLD_EDGE);
            r -= sw + Theme.SP_1;
        }
        String label = L.t("wynnlabs.craft.saved_button");
        int cw = Ui.iconChipWidth(tab.font, "bookmark", label, false);
        r -= cw;
        Ui.iconChip(g, tab.font, "bookmark", label, false, r, by, cw, false, mx, my);
        tab.acts.add(new Act(r, by, cw, Ui.CHIP_H, () -> tab.drawers.openSaved(), null, List.of(L.c("wynnlabs.craft.saved_tip"))));
        r -= Theme.SP_5;
        if (tab.type == null) {
            return;
        }
        CraftData.Recipe rec = tab.recipe();
        int known = tab.knownLevel(rec.profession());
        String prof = Professions.name(rec.profession().toUpperCase(Locale.ROOT)) + " " + (known > 0 ? known : "?");
        int pw = Type.META.width(tab.font, prof);
        Type.META.draw(g, tab.font, prof, r - pw, Type.META.yIn(by, Ui.CHIP_H), Theme.INK_3);
    }

    /** The three pages as the shared tab bar; returns its height. */
    int viewTabs(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String[][] tabs = {{"hammer", L.t("wynnlabs.craft.view_recipe")}, {"star", L.t("wynnlabs.craft.view_suggest")},
            {"trending-up", L.t("wynnlabs.craft.view_road")}};
        List<Ui.Tab> list = new ArrayList<>();
        for (String[] t : tabs) {
            list.add(new Ui.Tab(t[0], t[1]));
        }
        int[][] hits = new int[tabs.length][];
        int used = Ui.tabBar(g, tab.font, x, y, w, list, tab.view, mx, my, hits);
        for (int i = 0; i < tabs.length; i++) {
            int[] h = hits[i];
            int v = i;
            tab.acts.add(new Act(h[0], h[1], h[2], h[3], () -> {
                if (tab.view != v) {
                    tab.scroll = 0;
                }
                tab.view = v;
                tab.open = CraftingTab.Drop.NONE;
                if (v == CraftingTab.VIEW_SUGGEST) {
                    tab.suggestionsPanel.startCurrent(false);
                }
            }, null, List.of(Component.literal(tabs[i][1]))));
        }
        return used;
    }
}
