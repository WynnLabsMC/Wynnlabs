package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.WynnBuilderExport;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
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
            {BuildWindow.View.EDIT, "edit", editName()},
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
        // Export | Import from WynnBuilder: both with their words when both fit, else both as icons
        r -= Theme.SP_2;
        int room = r - Theme.SP_2 - cx;
        boolean pickView = win.view == BuildWindow.View.PICK;
        String imp = L.t("wynnlabs.pick.import");
        int ew = UiExport.width(win.ui.font, UiExport.WINDOW, true);
        int iwFull = win.ui.controlW("clipboard", imp, Integer.MAX_VALUE);
        boolean words = ew + (pickView ? Theme.SP_2 + iwFull : 0) <= room;
        if (pickView) {
            int iw = words ? iwFull : BuildControls.ICON_W;
            r -= iw;
            win.ui.control(g, host, r, y, Ui.CHIP_H, "clipboard", imp, BuildControls.WOOD, win.imports().shown != null, iw,
                    "wbImport", null, List.of(L.c("wynnlabs.pick.import_tip").withStyle(ChatFormatting.GRAY)), mx, my);
            r -= Theme.SP_2;
        }
        boolean can = canExport(win);
        int xw = UiExport.width(win.ui.font, UiExport.WINDOW, words);
        r -= xw;
        UiExport.draw(g, win.ui.font, UiExport.WINDOW, r, y, words, can, mx, my);
        if (can) {
            win.ui.btn(r, y, xw, Ui.CHIP_H, "wbExport", null);
        }
        List<Component> tip = new ArrayList<>();
        if (!words) {
            tip.add(L.c("wynnlabs.wb.export").withStyle(ChatFormatting.WHITE));
        }
        int s = UiExport.state(UiExport.WINDOW, System.currentTimeMillis());
        if (s != 0 && !words) {
            tip.add(L.c(s > 0 ? "wynnlabs.wb.copied" : "wynnlabs.wb.copy_failed").withStyle(s > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        tip.add(L.c(!can ? "wynnlabs.wb.export_none" : pickView ? "wynnlabs.wb.export_tip_now" : "wynnlabs.wb.export_tip_this")
                .withStyle(ChatFormatting.GRAY));
        host.tip(r, y, xw, Ui.CHIP_H, tip);
        int hy = y + Ui.CHIP_H + Theme.SP_2;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_4 - y;
    }

    /**
     * Whether the open tab has a build to export: Choose items, what you wear (in a world);
     * Results, the picked build; Edit, the build open in it. The saved list has none of its own.
     */
    static boolean canExport(BuildWindow win) {
        return switch (win.view) {
            case PICK -> GameState.current().inGame();
            case RESULTS -> win.result != null && !win.result.builds().isEmpty();
            case EDIT -> win.editId != null && Plans.byId(win.editId) != null;
            case SAVED -> false;
        };
    }

    /** The open tab's WynnBuilder link (see {@link #canExport}), null when there is none. */
    static String exportLink(BuildWindow win) {
        if (!canExport(win)) {
            return null;
        }
        return switch (win.view) {
            case PICK -> WynnBuilderExport.worn();
            case RESULTS -> {
                Roadmap.Candidate c = win.result.builds().get(Math.max(0, Math.min(win.card, win.result.builds().size() - 1)));
                yield WynnBuilderExport.milestone(c.set(), c.eval(), win.result.level());
            }
            case EDIT -> WynnBuilderExport.saved(Plans.byId(win.editId));
            case SAVED -> null;
        };
    }

    /** The Priority menu's "None" row. */
    static final int LEAN_NONE = 99;

    /** The edited build's name for its tab, or null when none is open. */
    private String editName() {
        Plans.Build b = win.editId == null ? null : Plans.byId(win.editId);
        return b == null ? null : Type.BODY.fit(win.ui.font, b.name(), 120);
    }

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
