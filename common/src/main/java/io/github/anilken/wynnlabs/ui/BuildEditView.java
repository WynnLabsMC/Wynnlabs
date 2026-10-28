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
        int fixed = UiInput.H + (win.compact ? 0 : Theme.SP_2 + BAR_H) + Theme.SP_4 + titles + (win.compact ? 0 : Ui.CHIP_H + Theme.SP_4) + Theme.SP_4 + Ui.BUTTON_H;
        int twoRow = Type.BODY.lineHeight + Type.META.lineHeight;
        boolean two = !win.compact && fixed + Plans.SLOTS.length * (twoRow + Theme.SP_2) <= h;
        int row = two ? twoRow : SLOT;

        // the name, and what the build is
        int wornN = 0;
        for (boolean on : worn) {
            wornN += on ? 1 : 0;
        }
        // the score in the price colour, what you wear of it and the level in META
        String score = String.format(Locale.ROOT, "%,d", Math.round(b.score()));
        String rest = " · " + L.t("wynnlabs.edit.equipped", wornN, b.items().size()) + (b.level() > 0 ? " · Lv " + b.level() : "");
        int sw = Type.NUMBER.width(ui.font, score);
        int fw = Math.min(sw + Type.META.width(ui.font, rest), w / 3);
        UiInput.input(g, ui.font, win.rename, L.t("wynnlabs.edit.name_hint"), x, y, w - fw - Theme.SP_4, true, false, mx, my);
        ui.btn(x, y, w - fw - Theme.SP_4, UiInput.H, "e.name", null);
        int fx0 = x + w - fw;
        Type.NUMBER.draw(g, ui.font, score, fx0, Type.NUMBER.yIn(y, UiInput.H), Theme.PRICE);
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, rest, fw - sw), fx0 + sw, Type.META.yIn(y, UiInput.H), Theme.INK_3);
        // how much of it you wear, as the Home's plan bar (the chosen items' purple, green when all)
        // (a small window has no room for it: the count beside the name says it)
        int by = y + UiInput.H + Theme.SP_2;
        int cy = y + UiInput.H + Theme.SP_4;
        if (!win.compact) {
            int total = Math.max(1, b.items().size());
            HdBar.draw(g, x, by, w, BAR_H, wornN / (double) total, wornN >= total ? Theme.GREEN : PLAN);
            cy = by + BAR_H + Theme.SP_4;
        }

        // the nine slots
        if (!win.compact) {
            cy = title(g, "hanger", L.t("wynnlabs.edit.items"), x, cy, w);
        }
        int bw = Ui.CHIP_H + 2;
        for (int i = 0; i < Plans.SLOTS.length; i++) {
            String slot = Plans.SLOTS[i];
            String name = b.items().get(slot);
            ItemData info = name == null ? null : infos.get(name);
            int sy = cy + (row - SLOT) / 2;
            Kit.slot(g, x, sy, SLOT, ring(info));
            ItemStack icon = info == null ? ItemStack.EMPTY : host.icon(info);
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + 1, sy + 1);
            }
            // the buttons on the right: keep, swap, remove (an empty slot: swap alone, to fill it)
            int rx = x + w;
            if (name != null) {
                rx -= bw;
                removeBtn(g, host, rx, cy + (row - Ui.CHIP_H) / 2, slot, mx, my);
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
                int checkW = worn[i] ? TICK_W + Theme.SP_2 : 0;
                String n = Type.BODY.fit(ui.font, name, room - checkW);
                Type.BODY.draw(g, ui.font, n, tx, ty, ink);
                if (worn[i]) {
                    int px = tx + Type.BODY.width(ui.font, n) + Theme.SP_2;
                    int py = Type.BODY.onCaps(ty, Ui.SEAL_H);
                    Kit.seal(g, px, py, TICK_W, Ui.SEAL_H, Theme.PAPER_DEEP);
                    Art.icon(g, "check", px + Ui.iconIn(TICK_W, 8), py + Ui.iconIn(Ui.SEAL_H, 8), 8, Theme.GOOD);
                }
                String where = slotName(i) + " · " + BuildSources.sourceText(name);
                if (two) {
                    source(g, i, name, tx, ty + Type.BODY.lineHeight, room);
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
            cy = title(g, "sparkles", L.t("wynnlabs.pick.lean"), x, cy, w);
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

    /** A section's title: its icon, LABEL, a hairline under; returns the y under it. */
    private int title(GuiGraphics g, String icon, String text, int x, int y, int w) {
        Art.icon(g, icon, x, Type.LABEL.onCaps(y, 8), 8, Theme.INK_2);
        Type.LABEL.draw(g, ui.font, text, x + 8 + Theme.SP_2, y, Theme.INK);
        int hy = y + Type.LABEL.lineHeight + Theme.SP_1;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    /** The equipped bar's height, its fill (the chosen items' purple) and the tick pill's width. */
    private static final int BAR_H = 3;
    private static final int PLAN = 0xFF8A6CC0;
    private static final int TICK_W = 15;

    /** An item's tier colour for its slot's frame (0 when it has none, as on the Home). */
    private static int ring(ItemData info) {
        Integer c = info == null ? null : info.tier().colour().getColor();
        return c == null || (c & 0xFFFFFF) == 0xFFFFFF ? 0 : 0xFF000000 | c;
    }

    /**
     * Where a piece comes from as the Home's source chip: its kind badge, the slot and the source
     * in META, a Trade Market price in the price colour; an unknown price faint.
     */
    private void source(GuiGraphics g, int i, String name, int x, int y, int room) {
        int d = KindBadge.MIN;
        Kit.kindBadge(g, x, Type.META.onCaps(y, d), d, KindBadge.of(BuildSources.sourceIcon(name)));
        int tx = x + d + Theme.SP_2;
        int end = x + room;
        Object[] s = BuildSources.source(name);
        String head = slotName(i) + " · ";
        if (s[1] != null) {
            String market = io.github.anilken.wynnlabs.market.ShoppingSources.where("TRADE_MARKET") + " · ";
            String price = io.github.anilken.wynnlabs.market.Prices.fmt((double) s[1]);
            String lead = Type.META.fit(ui.font, head + market, end - tx);
            Type.META.draw(g, ui.font, lead, tx, y, Theme.INK_2);
            int px = tx + Type.META.width(ui.font, lead);
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, price, end - px), px, y, Theme.PRICE);
            return;
        }
        String text = (String) s[0];
        boolean unknown = text.equals(L.t("wynnlabs.results.price_unknown")) || text.equals(L.t("wynnlabs.results.market_unknown"));
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, head + text, end - tx), tx, y, unknown ? Theme.INK_3 : Theme.INK_2);
    }

    /** Remove: an icon button whose ✕ turns red under the mouse. */
    private void removeBtn(GuiGraphics g, BuildWindow.Host host, int x, int y, String slot, int mx, int my) {
        int w = Ui.CHIP_H + 2;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + Ui.CHIP_H;
        Kit.pill(g, x, y, w, Ui.CHIP_H, false, hover);
        Art.icon(g, "x", x + Ui.iconIn(w, 8), y + Ui.iconIn(Ui.CHIP_H, 8), 8, hover ? Theme.BAD : Theme.INK_2);
        ui.btn(x, y, w, Ui.CHIP_H, "e.remove", slot);
        host.tip(x, y, w, Ui.CHIP_H, List.of(L.c("wynnlabs.edit.remove_tip").withStyle(ChatFormatting.GRAY)));
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
