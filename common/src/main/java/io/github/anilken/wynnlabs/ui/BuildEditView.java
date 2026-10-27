package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.BuildEdits;
import io.github.anilken.wynnlabs.build.BuildSources;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The build window's EDIT view: one saved build on one page. Its name (always editable), its nine
 * slots (each with keep, swap and remove), its strip, and Make my plan · Recompute · Save as copy
 * · Delete along the foot. Every change is kept at once ({@link Plans#update}); a delete and a
 * recompute's overwrite can be undone. The Saved tab's rows and the Home's plan Edit open it.
 */
final class BuildEditView {

    /** The slots' names, in {@link Plans#SLOTS} order. */
    private static final String[] SLOT_KEYS = {"wynnlabs.screen.slot_helmet", "wynnlabs.screen.slot_chestplate",
        "wynnlabs.screen.slot_leggings", "wynnlabs.screen.slot_boots", "wynnlabs.screen.slot_weapon", "wynnlabs.screen.slot_ring",
        "wynnlabs.screen.slot_ring", "wynnlabs.screen.slot_bracelet", "wynnlabs.screen.slot_necklace"};
    /** The pick view's type chip for each slot (its TYPE_SLOTS order: all, weapon, helmet …). */
    static final int[] PICK_TYPE = {2, 3, 4, 5, 1, 6, 6, 7, 8};
    private static final int SLOT = 18;

    private final BuildWindow win;
    private final BuildControls ui;
    /** The build's pieces looked up once per version of it (no database scan each frame). */
    private Map<String, ItemData> infos = new HashMap<>();
    private String infosFor;

    BuildEditView(BuildWindow win, BuildControls ui) {
        this.win = win;
        this.ui = ui;
    }

    /** A slot's name for people ("Ring 2"). */
    static String slotName(int i) {
        String n = L.t(SLOT_KEYS[i]);
        return Plans.SLOTS[i].startsWith("RING") ? n + " " + Plans.SLOTS[i].substring(4) : n;
    }

    void draw(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        Plans.Build b = Plans.byId(win.editId);
        if (b == null) {
            win.editId = null;
            win.showView(BuildWindow.View.SAVED);
            return;
        }
        keepTyped(b);
        keepStrip(b);
        b = Plans.byId(win.editId);
        lookUp(b);
        boolean[] worn = Plans.wornSlots(b.items(), BuildSources.worn());
        int footY = y + h - Ui.BUTTON_H;
        // the room decides: two lines a slot (its slot and where it comes from under the name), else one
        int titles = win.compact ? 0 : 2 * (Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3);
        int fixed = UiInput.H + Theme.SP_4 + titles + (win.compact ? 0 : Ui.CHIP_H + Theme.SP_4) + Theme.SP_4 + Ui.BUTTON_H;
        int twoRow = Type.BODY.lineHeight + Type.META.lineHeight;
        boolean two = !win.compact && fixed + Plans.SLOTS.length * (twoRow + Theme.SP_2) <= h;
        int row = two ? twoRow : SLOT;

        // the name, and what the build is
        int wornN = 0;
        for (boolean on : worn) {
            wornN += on ? 1 : 0;
        }
        String facts = L.t("wynnlabs.saved.info", String.format(Locale.ROOT, "%,d", Math.round(b.score())), wornN, b.items().size())
                + (b.level() > 0 ? " · Lv " + b.level() : "");
        int fw = Math.min(Type.META.width(ui.font, facts), w / 3);
        UiInput.input(g, ui.font, win.rename, L.t("wynnlabs.edit.name_hint"), x, y, w - fw - Theme.SP_4, true, false, mx, my);
        ui.btn(x, y, w - fw - Theme.SP_4, UiInput.H, "e.name", null);
        Type.META.drawRight(g, ui.font, Type.META.fit(ui.font, facts, fw), x + w, Type.META.yIn(y, UiInput.H), Theme.INK_3);
        int cy = y + UiInput.H + Theme.SP_4;

        // the nine slots
        if (!win.compact) {
            cy = title(g, L.t("wynnlabs.edit.items"), x, cy, w);
        }
        int bw = Ui.CHIP_H + 2;
        for (int i = 0; i < Plans.SLOTS.length; i++) {
            String slot = Plans.SLOTS[i];
            String name = b.items().get(slot);
            ItemData info = name == null ? null : infos.get(name);
            int sy = cy + (row - SLOT) / 2;
            Kit.slot(g, x, sy, SLOT, 0);
            ItemStack icon = info == null ? ItemStack.EMPTY : host.icon(info);
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + 1, sy + 1);
            }
            // the buttons on the right: keep, swap, remove (an empty slot: swap alone, to fill it)
            int rx = x + w;
            if (name != null) {
                rx -= bw;
                ui.iconBtn(g, host, rx, cy + (row - Ui.CHIP_H) / 2, "x", false, "e.remove", slot,
                        List.of(L.c("wynnlabs.edit.remove_tip").withStyle(ChatFormatting.GRAY)), mx, my);
                rx -= Theme.SP_2;
            }
            rx -= bw;
            ui.iconBtn(g, host, rx, cy + (row - Ui.CHIP_H) / 2, "arrows-horizontal", false, "e.swap", i,
                    List.of(L.c("wynnlabs.edit.swap_tip", slotName(i)).withStyle(ChatFormatting.GRAY)), mx, my);
            if (name != null) {
                boolean kept = b.locked(name);
                rx -= Theme.SP_2 + bw;
                ui.iconBtn(g, host, rx, cy + (row - Ui.CHIP_H) / 2, "lock", kept, "e.keep", slot,
                        List.of(L.c(kept ? "wynnlabs.edit.kept_tip" : "wynnlabs.edit.keep_tip").withStyle(ChatFormatting.GRAY)), mx, my);
            }
            int tx = x + SLOT + Theme.SP_3;
            int room = rx - Theme.SP_4 - tx;
            int ty = two ? cy : Type.BODY.yIn(cy, row);
            if (name == null) {
                Type.MUTED.draw(g, ui.font, Type.MUTED.fit(ui.font, slotName(i) + " · " + L.cap(L.t("wynnlabs.screen.empty")), room), tx, ty,
                        Theme.INK_3);
            } else {
                Integer rgb = info == null ? null : info.tier().colour().getColor();
                int ink = rgb == null ? Theme.INK : 0xFF000000 | Theme.paperRgb(rgb);
                int checkW = worn[i] ? 8 + Theme.SP_2 : 0;
                String n = Type.BODY.fit(ui.font, name, room - checkW);
                Type.BODY.draw(g, ui.font, n, tx, ty, ink);
                if (worn[i]) {
                    Art.icon(g, "check", tx + Type.BODY.width(ui.font, n) + Theme.SP_2, Type.BODY.onCaps(ty, 8), 8, Theme.GOOD);
                }
                String where = slotName(i) + " · " + BuildSources.sourceText(name);
                if (two) {
                    Type.META.draw(g, ui.font, Type.META.fit(ui.font, where, room), tx, ty + Type.BODY.lineHeight, Theme.INK_2);
                }
                if (info != null) {
                    host.item(x, cy, rx - Theme.SP_2 - x, row, info, ItemStack.EMPTY, List.of(
                            Component.literal(where).withStyle(ChatFormatting.DARK_AQUA),
                            (worn[i] ? L.c("wynnlabs.homev.slot_worn") : Component.empty()).withStyle(ChatFormatting.GREEN)));
                }
            }
            cy += row + Theme.SP_2;
        }

        // the strip (a small window: on the foot's row, left of the buttons)
        cy += Theme.SP_2;
        if (!win.compact) {
            cy = title(g, L.t("wynnlabs.pick.lean"), x, cy, w);
            win.pick.stripOf(g, host, win.editTuning, true, x, cy, w, mx, my);
        }

        // an overwrite just done: Undo, over the foot
        Plans.Build back = Plans.overwriteUndoable(System.currentTimeMillis());
        if (back != null && back.id().equals(b.id())) {
            int nw = Math.min(w, 260);
            int[][] hit = new int[1][];
            int ny = footY - Theme.SP_4 - Ui.noticeHeight(1);
            Ui.notice(g, ui.font, Ui.Level.INFO, List.of(L.t("wynnlabs.edit.overwritten", b.name())),
                    new Ui.Action("arrow-left", L.t("wynnlabs.saved.undo")), x + w - nw, ny, nw, Ui.noticeHeight(1), mx, my, hit);
            if (hit[0] != null) {
                ui.btn(hit[0][0], hit[0][1], hit[0][2], hit[0][3], "e.undo", null);
            }
        }

        // the foot: Make my plan (gold), Recompute (purple), Save as copy; Delete on the right
        boolean mine = Plans.current() != null && Plans.current().id().equals(b.id());
        if (win.compact) {
            compactFoot(g, host, b, mine, x, footY, w, mx, my);
            return;
        }
        int fx = x;
        if (!mine) {
            String t = L.t("wynnlabs.edit.make_plan");
            int tw = Ui.iconButtonLabelWidth(ui.font, t);
            Ui.iconButtonLabel(g, ui.font, "check", t, fx, footY, tw, mx, my);
            ui.btn(fx, footY, tw, Ui.BUTTON_H, "e.plan", null);
            host.tip(fx, footY, tw, Ui.BUTTON_H, List.of(L.c("wynnlabs.saved.apply_tip").withStyle(ChatFormatting.GRAY)));
            fx += tw + Theme.SP_2;
        }
        int cyF = footY + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        String re = L.t("wynnlabs.edit.recompute");
        int rw = Ui.iconChipWidth(ui.font, "refresh", re, false);
        Ui.primaryIconChip(g, ui.font, "refresh", re, fx, cyF, rw, mx, my);
        ui.btn(fx, cyF, rw, Ui.CHIP_H, "e.recompute", null);
        host.tip(fx, cyF, rw, Ui.CHIP_H, List.of(L.c("wynnlabs.edit.recompute_tip").withStyle(ChatFormatting.GRAY)));
        fx += rw + Theme.SP_2;
        String cp = L.t("wynnlabs.edit.copy");
        int delX = x + w - (Ui.CHIP_H + 2);
        int cw = Ui.iconChipWidth(ui.font, "copy", cp, false);
        if (fx + cw + Theme.SP_4 > delX) {
            cw = Ui.CHIP_H + 2;
            cp = null;
        }
        Ui.iconChip(g, ui.font, "copy", cp, false, fx, cyF, cw, false, mx, my);
        ui.btn(fx, cyF, cw, Ui.CHIP_H, "e.copy", null);
        host.tip(fx, cyF, cw, Ui.CHIP_H, List.of(L.c("wynnlabs.edit.copy").withStyle(ChatFormatting.GRAY)));
        ui.iconBtn(g, host, delX, cyF, "trash", false, "e.delete", null,
                List.of(L.c("wynnlabs.edit.delete_tip").withStyle(ChatFormatting.GRAY)), mx, my);
    }

    /**
     * The small window's foot: the strip on the left, then Make my plan, Recompute, Save as copy
     * and Delete as icon buttons on the right (their names in the tooltips).
     */
    private void compactFoot(GuiGraphics g, BuildWindow.Host host, Plans.Build b, boolean mine, int x, int footY, int w, int mx, int my) {
        int by = footY + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        int bw = Ui.CHIP_H + 2;
        int bx = x + w - bw;
        ui.iconBtn(g, host, bx, by, "trash", false, "e.delete", null,
                List.of(L.c("wynnlabs.edit.delete_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        bx -= Theme.SP_2 + bw;
        ui.iconBtn(g, host, bx, by, "copy", false, "e.copy", null,
                List.of(L.c("wynnlabs.edit.copy").withStyle(ChatFormatting.WHITE)), mx, my);
        bx -= Theme.SP_2 + bw;
        Ui.primaryIconChip(g, ui.font, "refresh", null, bx, by, bw, mx, my);
        ui.btn(bx, by, bw, Ui.CHIP_H, "e.recompute", null);
        host.tip(bx, by, bw, Ui.CHIP_H, List.of(L.c("wynnlabs.edit.recompute").withStyle(ChatFormatting.WHITE),
                L.c("wynnlabs.edit.recompute_tip").withStyle(ChatFormatting.GRAY)));
        if (!mine) {
            bx -= Theme.SP_2 + bw;
            ui.iconBtn(g, host, bx, by, "check", true, "e.plan", null,
                    List.of(L.c("wynnlabs.edit.make_plan").withStyle(ChatFormatting.WHITE)), mx, my);
        }
        win.pick.stripOf(g, host, win.editTuning, true, x, by, bx - Theme.SP_4 - x, mx, my);
    }

    /** A section's title in LABEL over a hairline; returns the y under it. */
    private int title(GuiGraphics g, String text, int x, int y, int w) {
        Type.LABEL.draw(g, ui.font, text, x, y, Theme.INK);
        int hy = y + Type.LABEL.lineHeight + Theme.SP_1;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    /** The name as typed, kept as it is typed (an empty box keeps the old name). */
    private void keepTyped(Plans.Build b) {
        String typed = win.rename.getValue().strip();
        if (!typed.isEmpty() && !typed.equals(b.name())) {
            Plans.update(BuildEdits.renamed(b, typed), System.currentTimeMillis());
        }
    }

    /** The strip as changed on this page, kept at once. */
    private void keepStrip(Plans.Build b) {
        if (!win.editTuning.equals(b.tuning())) {
            Plans.update(new Plans.Build(b.id(), b.name(), b.locks(), win.editTuning, b.items(), b.score(), b.level(), b.created(),
                    b.updated(), b.points()), System.currentTimeMillis());
        }
    }

    /** The build's pieces from the item database, again only when the build changed. */
    private void lookUp(Plans.Build b) {
        String key = b.id() + "|" + b.updated() + "|" + b.items();
        if (key.equals(infosFor)) {
            return;
        }
        infosFor = key;
        Map<String, ItemData> m = new HashMap<>();
        java.util.Set<String> want = new java.util.HashSet<>(b.items().values());
        GameState.current().allGear().filter(i -> want.contains(i.name())).forEach(i -> m.putIfAbsent(i.name(), i));
        infos = m;
    }
}
