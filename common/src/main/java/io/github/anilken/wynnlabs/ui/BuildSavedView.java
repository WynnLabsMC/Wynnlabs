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
 * The build window's SAVED view ("My builds"): a row per saved build (a click opens it in the
 * editor, {@link BuildEditView}; Apply on its right), "+ Save current plan" and the undo line
 * after a delete. It owns the list's scroll.
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
        int listH = footY - Theme.SP_4 - y;
        Map<String, String> worn = BuildSources.worn();
        int rows = Math.max(1, listH / ROW);
        int max = Math.max(0, all.size() - rows);
        savedScroll = Math.max(0, Math.min(savedScroll, max));
        int rw = w - (max > 0 ? ScrollBar.W + Theme.SP_3 : 0);
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        for (int i = 0; i < rows && savedScroll + i < all.size(); i++) {
            Plans.Build b = all.get(savedScroll + i);
            int ry = y + i * ROW;
            boolean mine = current != null && current.id().equals(b.id());
            // the whole row opens the build in the editor, lighter under the mouse; Apply on its right
            int rowH = textH + 2 * Theme.SP_2;
            boolean hover = mx >= x && mx < x + rw && my >= ry && my < ry + rowH;
            if (hover) {
                Art.box(g, x, ry, rw, rowH, 4, Theme.CARD_HOVER, 0);
            }
            ui.btn(x, ry, rw, rowH, "e.open", b.id());
            int ty = ry + Theme.SP_2;
            int bx = x + rw - Theme.SP_2;
            if (!mine) {
                bx -= Ui.CHIP_H + 2;
                ui.iconBtn(g, host, bx, ty + (textH - Ui.CHIP_H) / 2, "check", false, "applySaved", b.id(),
                        List.of(L.c("wynnlabs.saved.apply_tip").withStyle(ChatFormatting.GRAY)), mx, my);
                bx -= Theme.SP_2;
            }
            int nx = x + Theme.SP_2;
            int nameRoom = bx - Theme.SP_3 - nx;
            String plan = mine ? L.t("wynnlabs.saved.your_plan") : null;
            int sw = plan == null ? 0 : Type.META.width(ui.font, plan) + 2 * Theme.SP_2 + Theme.SP_3;
            String name = Type.LABEL.fit(ui.font, b.name(), nameRoom - sw);
            Type.LABEL.draw(g, ui.font, name, nx, ty, Theme.INK);
            if (plan != null) {
                Ui.seal(g, ui.font, plan, nx + Type.LABEL.width(ui.font, name) + Theme.SP_3, Type.LABEL.onCaps(ty, Ui.SEAL_H), BuildControls.CHOSEN);
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
        savedBar.place(x + w - ScrollBar.W, y, listH, rows, all.size(), savedScroll);
        savedBar.draw(g, mx, my);
        footer(g, host, x, footY, w, current, mx, my);
    }

    /** A row's pitch: a name and a META line, SP_2 inside its wash above and below, SP_2 of page between washes. */
    private static final int ROW = Type.BODY.lineHeight + Type.META.lineHeight + 3 * Theme.SP_2;

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
        tip.add(L.c("wynnlabs.edit.open_tip").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }
}
