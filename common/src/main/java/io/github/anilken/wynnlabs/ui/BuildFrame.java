package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.core.L;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The build window's header (its tabs, WynnBuilder, ✕) and its open menu (level range or budget), over the view. */
final class BuildFrame {

    private final BuildWindow win;

    BuildFrame(BuildWindow win) {
        this.win = win;
    }

    /** The header: the three tabs (Results only while there are some), WynnBuilder on the items, ✕; returns its height. */
    int tabs(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int mx, int my) {
        int cx = x;
        int saves = Plans.saved().size();
        Object[][] tabs = {
            {BuildWindow.View.PICK, "target", L.t("wynnlabs.pick.tab")},
            {BuildWindow.View.RESULTS, "sparkles", win.result == null ? null : L.t("wynnlabs.results.tab", win.result.builds().size())},
            {BuildWindow.View.SAVED, "bookmark", L.t("wynnlabs.saved.tab", saves)},
        };
        for (Object[] t : tabs) {
            if (t[2] == null) {
                continue;
            }
            boolean on = t[0] == win.view;
            String label = win.compact && !on ? "" : (String) t[2];
            int tw = Ui.iconChipWidth(win.ui.font, (String) t[1], label, false);
            Ui.iconChip(g, win.ui.font, (String) t[1], label, false, cx, y, tw, on, mx, my);
            win.ui.btn(cx, y, tw, Ui.CHIP_H, "tab", t[0]);
            if (label.isEmpty()) {
                host.tip(cx, y, tw, Ui.CHIP_H, List.of(Component.literal((String) t[2])));
            }
            cx += tw + Theme.SP_2;
        }
        int r = x + w;
        r -= win.ui.iconBtn(g, host, r - Ui.CHIP_H - 2, y, "x", false, "close", null,
                List.of(L.c("wynnlabs.pick.close_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        if (win.view == BuildWindow.View.PICK) {
            String imp = L.t("wynnlabs.pick.import");
            int room = r - Theme.SP_4 - cx;
            int iw = win.ui.controlW("clipboard", imp, room);
            win.ui.control(g, host, r - Theme.SP_2 - iw, y, Ui.CHIP_H, "clipboard", imp, BuildControls.WOOD, win.imports().shown != null, room,
                    "wbImport", null, List.of(L.c("wynnlabs.pick.import_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        }
        int hy = y + Ui.CHIP_H + Theme.SP_2;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_4 - y;
    }

    /** The Priority menu's "None" row. */
    static final int LEAN_NONE = 99;

    /** The open menu (level range, budget or priority) over everything, its rows clickable. */
    void menuList(GuiGraphics g, int mx, int my) {
        List<UiMenu.Item> items = new ArrayList<>();
        if (win.menu == BuildWindow.MENU_RANGE) {
            for (int i = 0; i < BuildPickView.RANGES.length; i++) {
                items.add(UiMenu.Item.of(String.valueOf(i), null, BuildPickView.rangeText(i), null, i == win.pick.range));
            }
        } else if (win.menu == BuildWindow.MENU_LEAN || win.menu == BuildWindow.MENU_EDIT_LEAN) {
            // the leanings, any number at once: a row turns one on or off and the menu stays open
            io.github.anilken.wynnlabs.build.Tuning.Choice t = win.menu == BuildWindow.MENU_LEAN ? win.tuning : win.editTuning;
            for (int i = 0; i < io.github.anilken.wynnlabs.build.Tuning.LEANS.length; i++) {
                String[] l = BuildPickView.LEANS[i];
                items.add(UiMenu.Item.of(String.valueOf(i), l[0], L.t(l[1]), null, t.leans(io.github.anilken.wynnlabs.build.Tuning.LEANS[i]))
                        .sub(L.t(l[2])));
            }
            items.add(UiMenu.Item.of(String.valueOf(LEAN_NONE), null, L.t("wynnlabs.pick.lean_none"), null, t.lean() == 0).group());
        } else {
            long now = win.menu == BuildWindow.MENU_BUDGET ? win.tuning.budget() : win.editTuning.budget();
            for (int i = 0; i < io.github.anilken.wynnlabs.build.Tuning.BUDGETS.length; i++) {
                long b = io.github.anilken.wynnlabs.build.Tuning.BUDGETS[i];
                items.add(UiMenu.Item.of(String.valueOf(i), b > 0 ? "coin" : null, BuildText.budgetText(b), null, b == now));
            }
        }
        g.nextStratum();
        int screenW = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        UiMenu.draw(g, win.ui.font, UiMenu.Spec.under(win.menuAt, screenW, screenH), win.menuState, items, mx, my,
                row -> win.ui.btn(row.x(), row.y(), row.w(), row.h(), "menuPick", Integer.parseInt(row.item().id())));
    }
}
