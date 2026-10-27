package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.BuildSources;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The build window's SAVED view ("My builds"): a row per saved build with Apply, Edit, Rename
 * and Delete, the edit area under the list, "+ Save current plan" and the undo line. It owns
 * the list's scroll.
 */
final class BuildSavedView {

    private final BuildWindow win;
    private final BuildControls ui;

    int savedScroll;
    final ScrollBar savedBar = new ScrollBar();

    BuildSavedView(BuildWindow win, BuildControls ui) {
        this.win = win;
        this.ui = ui;
    }

    void drawSaved(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my, float pt) {
        List<Plans.Build> all = Plans.saved();
        int footY = y + h - Ui.BUTTON_H;
        Plans.Build current = Plans.current();
        if (all.isEmpty()) {
            // nothing saved: the three steps there, and the way to the first
            int[] box = UiEmpty.draw(g, ui.font, x, y, w, footY - Theme.SP_4 - y, win.compact, "bookmark", L.t("wynnlabs.saved.none"),
                    List.of(L.t("wynnlabs.saved.step1"), L.t("wynnlabs.saved.step2"), L.t("wynnlabs.saved.step3")),
                    new UiEmpty.Action("target", L.t("wynnlabs.pick.tab"), "pick"), null, mx, my);
            if (box != null) {
                ui.btn(box[0], box[1], box[2], box[3], "tab", BuildWindow.View.PICK);
            }
            footer(g, host, x, footY, w, current, mx, my);
            return;
        }
        int editH = win.editId == null ? 0 : EDIT_H + Theme.SP_4;
        int listH = footY - Theme.SP_4 - editH - y;
        Map<String, String> worn = BuildSources.worn();
        int rows = Math.max(1, listH / ROW);
        int max = Math.max(0, all.size() - rows);
        savedScroll = Math.max(0, Math.min(savedScroll, max));
        int rw = w - (max > 0 ? ScrollBar.W + Theme.SP_3 : 0);
        boolean renaming = false;
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        for (int i = 0; i < rows && savedScroll + i < all.size(); i++) {
            Plans.Build b = all.get(savedScroll + i);
            int ry = y + i * ROW;
            boolean mine = current != null && current.id().equals(b.id());
            boolean lit = b.id().equals(win.editId) || b.id().equals(win.renameId);
            if (lit) {
                Kit.rowLit(g, x, ry, rw, textH + 2 * Theme.SP_2);
            }
            int ty = ry + Theme.SP_2;
            // the actions on the right: Apply (not on your plan), Edit, Rename, Delete
            int bx = x + rw - Theme.SP_2;
            String[][] all4 = {{"trash", "delete", "wynnlabs.saved.delete_tip"}, {"pencil", "rename", "wynnlabs.saved.rename_tip"},
                {"edit", "edit", "wynnlabs.saved.edit_tip"}, {"check", "applySaved", "wynnlabs.saved.apply_tip"}};
            String[][] acts = mine ? java.util.Arrays.copyOf(all4, 3) : all4;
            int by = ty + (textH - Ui.CHIP_H) / 2;
            for (String[] a : acts) {
                bx -= Ui.CHIP_H + 2;
                ui.iconBtn(g, host, bx, by, a[0], false, a[1], b.id(), List.of(L.c(a[2]).withStyle(ChatFormatting.GRAY)), mx, my);
                bx -= Theme.SP_2;
            }
            int nx = x + Theme.SP_2;
            int nameRoom = bx - Theme.SP_3 - nx;
            if (b.id().equals(win.renameId)) {
                renaming = true;
                int okW = 2 * (Ui.CHIP_H + 2) + Theme.SP_2;
                int fw = Math.max(40, nameRoom - okW - Theme.SP_2);
                win.rename.setFocused(true);
                UiInput.input(g, ui.font, win.rename, "", nx, ty - 1, fw, true, false, mx, my);
                int okX = nx + fw + Theme.SP_2;
                ui.iconBtn(g, host, okX, ty - 1, "check", true, "renameOk", b.id(),
                        List.of(L.c("wynnlabs.saved.rename_ok").withStyle(ChatFormatting.GRAY)), mx, my);
                ui.iconBtn(g, host, okX + Ui.CHIP_H + 2 + Theme.SP_2, ty - 1, "x", false, "renameCancel", b.id(),
                        List.of(L.c("wynnlabs.saved.rename_cancel").withStyle(ChatFormatting.GRAY)), mx, my);
            } else {
                String plan = mine ? L.t("wynnlabs.saved.your_plan") : null;
                int sw = plan == null ? 0 : Type.META.width(ui.font, plan) + 2 * Theme.SP_2 + Theme.SP_3;
                String name = Type.LABEL.fit(ui.font, b.name(), nameRoom - sw);
                Type.LABEL.draw(g, ui.font, name, nx, ty, Theme.INK);
                if (plan != null) {
                    Ui.seal(g, ui.font, plan, nx + Type.LABEL.width(ui.font, name) + Theme.SP_3, Type.LABEL.onCaps(ty, Ui.SEAL_H), BuildControls.CHOSEN);
                }
            }
            // the nine pieces as small marks, the score and how much of it you wear
            boolean[] on = Plans.wornSlots(b.items(), worn);
            int wornN = 0;
            for (int k = 0; k < Plans.SLOTS.length; k++) {
                wornN += on[k] ? 1 : 0;
            }
            int my2 = ty + Type.BODY.lineHeight;
            int sqEnd = savedSquares(g, nx, Type.META.onCaps(my2, 6), b, on);
            String info = L.t("wynnlabs.saved.info", String.format(Locale.ROOT, "%,d", Math.round(b.score())), wornN, b.items().size());
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, info, bx - Theme.SP_3 - sqEnd - Theme.SP_3), sqEnd + Theme.SP_3, my2, Theme.INK_3);
            host.tip(x, ry, Math.max(1, bx - x), textH + 2 * Theme.SP_2, savedTip(b));
        }
        if (!renaming) {
            win.rename.setFocused(false);
        }
        savedBar.place(x + w - ScrollBar.W, y, listH, rows, all.size(), savedScroll);
        savedBar.draw(g, mx, my);
        if (win.editId != null) {
            drawEdit(g, host, x, y + listH + Theme.SP_4, w, EDIT_H, mx, my);
        }
        footer(g, host, x, footY, w, current, mx, my);
    }

    /** A row's pitch: a name and a META line, SP_2 inside its wash above and below, SP_2 of page between washes. */
    private static final int ROW = Type.BODY.lineHeight + Type.META.lineHeight + 3 * Theme.SP_2;
    private static final int EDIT_H = 2 * Theme.SP_4 + Type.LABEL.lineHeight + 2 * Theme.SP_3 + 2 * Ui.CHIP_H;

    /** "+ Save current plan" (only with a plan) on the left; after a delete, "Deleted · Undo" as a notice on the right. */
    private void footer(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, Plans.Build current, int mx, int my) {
        boolean can = current != null;
        List<Component> addTip = List.of(L.c(can ? "wynnlabs.saved.add_tip" : "wynnlabs.saved.add_none").withStyle(ChatFormatting.GRAY));
        int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        int aw = ui.control(g, host, x, cy, Ui.CHIP_H, "plus", L.t("wynnlabs.saved.add"), BuildControls.WOOD, false, w / 2,
                can ? "addCurrent" : null, null, addTip, mx, my);
        Plans.Build gone = Plans.undoable(System.currentTimeMillis());
        if (gone != null) {
            int nw = Math.min(w - aw - Theme.SP_5, 240);
            int[][] hit = new int[1][];
            Ui.notice(g, ui.font, Ui.Level.INFO, List.of(L.t("wynnlabs.saved.deleted", gone.name())),
                    new Ui.Action("arrow-left", L.t("wynnlabs.saved.undo")), x + w - nw, y - Theme.SP_1, nw, Ui.BUTTON_H + 2 * Theme.SP_1, mx, my, hit);
            if (hit[0] != null) {
                ui.btn(hit[0][0], hit[0][1], hit[0][2], hit[0][3], "undo", null);
            }
        }
    }

    /** The nine pieces as small rounded marks: purple a chosen item, green one you wear, plain the rest; returns the x after them. */
    private int savedSquares(GuiGraphics g, int x, int y, Plans.Build b, boolean[] worn) {
        for (int i = 0; i < Plans.SLOTS.length; i++) {
            String name = b.items().get(Plans.SLOTS[i]);
            int c = name != null && b.locked(name) ? BuildControls.CHOSEN : worn[i] ? Theme.GOOD : Theme.WELL;
            Art.box(g, x + i * 8, y, 6, 6, 2, c, 0);
        }
        return x + 9 * 8 - 2;
    }

    private static List<Component> savedTip(Plans.Build b) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(b.name()).withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(UiFont::bold));
        for (String slot : Plans.SLOTS) {
            String n = b.items().get(slot);
            if (n != null) {
                tip.add(Component.literal((b.locked(n) ? "◆ " : "  ") + n).withStyle(b.locked(n) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
            }
        }
        tip.add(L.c("wynnlabs.saved.row_tip", BuildText.summary(b.tuning())).withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    /** The edit card under the list: the build's chosen items (✕ each) and "+ Item", its strip (changeable), and its actions. */
    private void drawEdit(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        Plans.Build b = Plans.byId(win.editId);
        if (b == null) {
            win.editId = null;
            return;
        }
        Kit.softCard(g, x, y, w, h);
        Kit.outline(g, x, y, w, h + 1, BuildControls.CHOSEN);
        int cx = x + Theme.SP_4;
        int cy = y + Theme.SP_4;
        int iw = w - 2 * Theme.SP_4;
        Type.LABEL.draw(g, ui.font, Type.LABEL.fit(ui.font, L.t("wynnlabs.saved.editing", b.name()), iw), cx, cy, Theme.INK);
        cy += Type.LABEL.lineHeight + Theme.SP_3;
        int plusW = ui.controlW("plus", L.t("wynnlabs.saved.add_item"), 80);
        for (String n : win.editLocks) {
            if (cx + 60 + Theme.SP_2 + plusW > x + w - Theme.SP_4) {
                break;
            }
            cx += ui.chosenChip(g, host, cx, cy, n, Math.min(120, x + w - Theme.SP_4 - plusW - Theme.SP_2 - cx), "editUnlock", n, mx, my) + Theme.SP_2;
        }
        ui.control(g, host, cx, cy, Ui.CHIP_H, "plus", L.t("wynnlabs.saved.add_item"), BuildControls.WOOD, false, 80, "editAdd", null,
                List.of(L.c("wynnlabs.saved.add_item_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        cy += Ui.CHIP_H + Theme.SP_3;
        // the actions on the right of the strip: Recompute (purple), Save as new (a disk), Cancel (✕)
        int bx = x + w - Theme.SP_4;
        String re = L.t("wynnlabs.saved.recompute");
        int rw = ui.controlW("refresh", re, w / 3, BuildControls.PRIMARY);
        bx -= rw;
        ui.control(g, host, bx, cy - 1, Ui.BUTTON_H, "refresh", re, BuildControls.PRIMARY, false, w / 3, "editRecompute", null,
                List.of(L.c("wynnlabs.saved.recompute_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        bx -= Theme.SP_2 + Ui.CHIP_H + 2;
        ui.iconBtn(g, host, bx, cy, "device-floppy", false, "editSaveNew", null, List.of(Component.literal(L.t("wynnlabs.saved.save_new")),
                L.c("wynnlabs.saved.save_new_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        bx -= Theme.SP_2 + Ui.CHIP_H + 2;
        ui.iconBtn(g, host, bx, cy, "x", false, "editCancel", null, List.of(Component.literal(L.t("wynnlabs.pick.cancel")),
                L.c("wynnlabs.saved.edit_cancel_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        // the strip, changeable here: Recompute searches with it
        win.pick.stripOf(g, host, win.editTuning, true, x + Theme.SP_4, cy, bx - Theme.SP_3 - x - Theme.SP_4, mx, my);
    }
}
