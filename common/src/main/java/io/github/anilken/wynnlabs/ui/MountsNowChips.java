package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The road map's "Now": a chip for each mount (or group of mounts with one plan) of the generation
 * still fed, with its state; a click shows its plan. When they do not fit on one line (small
 * windows) one menu chip lists them instead. "Calculate all" when more than one has no exact plan.
 */
final class MountsNowChips {

    private MountsNowChips() {}

    /** "R1 Azure-Horn" for one mount, "×5 same plan" for a group. */
    static String name(MountsGroups.Group gr) {
        if (gr.size() > 1) {
            return L.t("wynnlabs.mounts.same_plan", gr.size());
        }
        MountsGroups.Member m = gr.first();
        return (m.row() > 0 ? "R" + m.row() + " " : "") + m.name();
    }

    /** Ready, or the time left on the food being eaten. */
    static String status(MountsGroups.Group gr) {
        return gr.ready() ? L.t("wynnlabs.mounts.st_ready") : MountsText.durShort(gr.timer());
    }

    static String label(MountsGroups.Group gr) {
        return name(gr) + " · " + status(gr);
    }

    /** Draws the row; returns its height. */
    static int draw(MountsTab tab, MountsNow now, GuiGraphics g, int x, int y, int w, List<MountsGroups.Group> groups, int picked,
                    int mx, int my) {
        var font = tab.font();
        // "Calculate all": the mounts with no exact plan yet, when more than one
        List<MountsModel.Owned> none = new ArrayList<>();
        for (MountsGroups.Group gr : groups) {
            for (MountsGroups.Member m : gr.members()) {
                if (m.owned() != null && tab.model.feed(m.owned()).state() == MountsModel.FeedState.NONE) {
                    none.add(m.owned());
                }
            }
        }
        boolean all = none.size() > 1 && MountsModel.queued() == 0;
        String allText = L.t("wynnlabs.mounts.calc_all", none.size());
        int aw = all ? Ui.iconChipWidth(font, "player-play", allText, false) : 0;
        int room = w - (all ? aw + Theme.SP_4 : 0);
        int need = 0;
        for (MountsGroups.Group gr : groups) {
            need += Ui.chipWidth(font, label(gr)) + (need > 0 ? Theme.SP_2 : 0);
        }
        if (need <= room) {
            int cx = x;
            for (int i = 0; i < groups.size(); i++) {
                MountsGroups.Group gr = groups.get(i);
                String text = label(gr);
                int cw = Ui.chipWidth(font, text);
                Ui.chip(g, font, text, cx, y, cw, i == picked, false, false, mx, my);
                String key = gr.first().key();
                tab.click(cx, y, cw, Ui.CHIP_H, () -> now.picked = key);
                cx += cw + Theme.SP_2;
            }
        } else if (picked >= 0) {
            // no room for every chip: one chip opens the list (the shared dropdown)
            String text = Type.BODY.fit(font, label(groups.get(picked)),room - Ui.iconChipWidth(font, "horse", "", true));
            int cw = UiMenu.chip(g, font, "horse", text, x, y, tab.open == MountsTab.MOUNT_MENU, mx, my);
            tab.mountAt = new int[] {x, y, cw, Ui.CHIP_H};
            tab.click(x, y, cw, Ui.CHIP_H, () -> tab.toggle(MountsTab.MOUNT_MENU));
        }
        if (all) {
            int ax = x + w - aw;
            Ui.iconChip(g, font, "player-play", allText, false, ax, y, aw, false, mx, my);
            tab.click(ax, y, aw, Ui.CHIP_H, () -> tab.model.startAll(none));
            if (mx >= ax && mx < ax + aw && my >= y && my < y + Ui.CHIP_H) {
                tab.tip(ax, y, aw, Ui.CHIP_H, List.of(Component.literal(L.t("wynnlabs.mounts.calc_all_tip")).withStyle(ChatFormatting.GRAY)));
            }
        }
        return Ui.CHIP_H;
    }

    /** The mounts menu's rows: each group, its state on the right, the shown one ticked. */
    static List<UiMenu.Item> items(List<MountsGroups.Group> groups, int picked) {
        UiMenu.Items out = new UiMenu.Items();
        for (int i = 0; groups != null && i < groups.size(); i++) {
            MountsGroups.Group gr = groups.get(i);
            out.add(UiMenu.Item.of("m:" + gr.first().key(), null, name(gr), status(gr), i == picked));
        }
        return out.list;
    }
}
