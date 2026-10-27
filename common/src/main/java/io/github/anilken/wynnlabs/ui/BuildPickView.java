package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The build window's PICK view: the search box, the type pills and the level range over the
 * list of items the class can use, the tuning strip, the chosen chips and "Suggest build"; a
 * loading view in the list's place while the search runs. It owns the list's filters and scroll.
 */
final class BuildPickView {

    private static final int ROW_H = 18;

    private final BuildWindow win;
    private final BuildControls ui;

    /** 0 all; then the weapon, helmet, chestplate, leggings, boots, ring, bracelet, necklace. */
    int type;
    int listScroll;
    private List<ItemData> catalogue = List.of();
    private List<ItemData> shown = List.of();
    private String shownFor = "\u0000";
    private int catalogueLevel = -1;
    final ScrollBar listBar = new ScrollBar();

    BuildPickView(BuildWindow win, BuildControls ui) {
        this.win = win;
        this.ui = ui;
    }

    private static final String[] TYPE_KEYS = {"wynnlabs.pick.type_all", null, "wynnlabs.road.slot_helmet",
        "wynnlabs.road.slot_chestplate", "wynnlabs.road.slot_leggings", "wynnlabs.road.slot_boots", "wynnlabs.road.slot_ring",
        "wynnlabs.road.slot_bracelet", "wynnlabs.road.slot_necklace"};
    private static final Slot[] TYPE_SLOTS = {null, null, Slot.HELMET, Slot.CHESTPLATE, Slot.LEGGINGS, Slot.BOOTS, Slot.RING,
        Slot.BRACELET, Slot.NECKLACE};

    /** The type pill's name: the class's weapon type for the second ("Relik"). */
    private static String typeName(int i) {
        if (i == 1) {
            Slot wpn = Slot.weaponOf(GameState.current().classType());
            return wpn == null ? L.t("wynnlabs.road.slot_weapon") : BuildText.nice(wpn.name());
        }
        return L.t(TYPE_KEYS[i]);
    }

    /** The level ranges of the list's range button: all, up to your level, 1–50, 51–80, 81–105, 106 and up. */
    static final int[][] RANGES = {{1, 999}, {1, -1}, {1, 50}, {51, 80}, {81, 105}, {106, 999}};
    /** The range picked (0: all levels, the default). */
    int range;

    /** A range's label: "All", "≤ Lv 44", "1–50" … */
    static String rangeText(int i) {
        if (i == 0) {
            return L.t("wynnlabs.pick.range_all");
        }
        if (i == 1) {
            return L.t("wynnlabs.pick.range_mine", GameState.current().level());
        }
        int[] r = RANGES[i];
        return r[1] >= 999 ? r[0] + "+" : r[0] + "–" + r[1];
    }

    /**
     * Every piece the class can use at any level, from the item database (never your bag or
     * bank), highest level first; the type pill, the level range and the win.search narrow it.
     */
    private List<ItemData> catalogue() {
        GameState state = GameState.current();
        int level = state.level();
        if (level != catalogueLevel || catalogue.isEmpty()) {
            Klass cls = state.classType();
            List<ItemData> all = new ArrayList<>(state.allGear().filter(i -> Locks.usable(i, cls)).toList());
            all.sort((a, b) -> a.level() != b.level() ? Integer.compare(b.level(), a.level()) : a.name().compareTo(b.name()));
            catalogue = all;
            catalogueLevel = level;
            shownFor = "\u0000";
        }
        String key = type + "|" + range + "|" + win.search.getValue().toLowerCase(Locale.ROOT).strip();
        if (!key.equals(shownFor)) {
            String q = win.search.getValue().toLowerCase(Locale.ROOT).strip();
            int lo = RANGES[range][0];
            int hi = RANGES[range][1] < 0 ? level : RANGES[range][1];
            List<ItemData> out = new ArrayList<>();
            for (ItemData i : catalogue) {
                if (type == 1 && !i.slot().isWeapon() || type > 1 && i.slot() != TYPE_SLOTS[type]) {
                    continue;
                }
                if (i.level() < lo || i.level() > hi) {
                    continue;
                }
                if (!q.isEmpty() && !i.name().toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                out.add(i);
            }
            shown = out;
            shownFor = key;
        }
        return shown;
    }

    /** A row's pitch: the 18 px slot with SP_1 above and below its wash, SP_2 of page between washes. */
    private static final int PITCH = 18 + 2 * Theme.SP_1 + Theme.SP_2 + 2;

    void drawPick(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my, float pt) {
        int bottom = y + h;
        if (win.imports().shown != null) {
            win.imports().draw(g, host, x, y, w, h, mx, my);    // a WynnBuilder link read: the whole view
            return;
        }
        // the search, the level range beside it
        String rt = rangeText(range);
        int rw = Ui.iconChipWidth(ui.font, "filter", rt, true);
        UiInput.Hit sh = UiInput.search(g, ui.font, win.search, L.t("wynnlabs.pick.search_hint"), false, x, y, w - rw - Theme.SP_3, mx, my);
        ui.btn(x, y, w - rw - Theme.SP_3, UiInput.H, "search", null);
        if (sh.clear() != null) {
            int[] c = sh.clear();
            ui.btn(c[0], c[1], c[2], c[3], "searchclear", null);       // the last one added wins
        }
        int[] at = {x + w - rw, y, rw, Ui.CHIP_H};
        UiMenu.chip(g, ui.font, "filter", rt, at[0], at[1], win.menu == BuildWindow.MENU_RANGE, mx, my);
        ui.btn(at[0], at[1], at[2], at[3], "menu", BuildWindow.MENU_RANGE);
        if (win.menu == BuildWindow.MENU_RANGE) {
            win.menuAt = at;
        }
        int cy = y + Ui.CHIP_H + Theme.SP_3;
        // the type chips, on as many lines as they need (never cut)
        int px = x;
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            String t = typeName(i);
            int tw = Ui.chipWidth(ui.font, t);
            if (px + tw > x + w && px > x) {
                px = x;
                cy += Ui.CHIP_H + Theme.SP_2;
            }
            Ui.chip(g, ui.font, t, px, cy, tw, i == type, false, false, mx, my);
            ui.btn(px, cy, tw, Ui.CHIP_H, "type", i);
            px += tw + Theme.SP_2;
        }
        cy += Ui.CHIP_H + Theme.SP_4;
        // what cannot be worn together: a warning over the list
        Locks.Problem problem = Locks.problem(win.chosen, BuildWindow.planLevel(win.chosen));
        if (problem != null) {
            cy += Ui.notice(g, ui.font, Ui.Level.WARNING, List.of(L.t("wynnlabs.pick.not_wearable", problem.text())), null, x, cy, w, 40,
                    mx, my, new int[1][]) + Theme.SP_4;
        }
        // the bottom: the chosen items and Suggest, the strip over them
        int trayY = bottom - Ui.BUTTON_H;
        int stripY = trayY - Theme.SP_4 - Ui.CHIP_H;
        int listH = stripY - Theme.SP_4 - cy;
        if (Around.running() && win.awaitGen >= 0 || Around.previewLoading != null) {
            drawLoading(g, host, x, cy, w, listH, mx, my);
        } else {
            drawList(g, host, x, cy, w, listH, mx, my);
        }
        strip(g, host, x, stripY, w, mx, my);
        drawChips(g, host, x, trayY, w, problem, mx, my);
    }

    /** The item list: a row per piece (its slot, its name in its tier's colour, type and level), the chosen ones lit with a purple tick. */
    private void drawList(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        List<ItemData> items = catalogue();
        if (items.isEmpty()) {
            boolean narrowed = !win.search.getValue().isBlank() || type != 0 || range != 0;
            int[] act = UiEmpty.draw(g, ui.font, x, y, w, h, true, "search", L.t("wynnlabs.pick.none"), List.of(),
                    narrowed ? new UiEmpty.Action("eraser", L.t("wynnlabs.pick.clear_search"), "clearSearch") : null, null, mx, my);
            if (act != null) {
                ui.btn(act[0], act[1], act[2], act[3], "clearSearch", null);
            }
            return;
        }
        int rows = Math.max(1, h / PITCH);
        int max = Math.max(0, items.size() - rows);
        listScroll = Math.max(0, Math.min(listScroll, max));
        int rw = w - (max > 0 ? ScrollBar.W + Theme.SP_3 : 0);
        for (int i = 0; i < rows && listScroll + i < items.size(); i++) {
            ItemData it = items.get(listScroll + i);
            int ry = y + i * PITCH;
            boolean on = win.chosen.stream().anyMatch(c -> c.name().equals(it.name()));
            boolean hov = BuildControls.in(mx, my, x, ry, rw, PITCH);
            if (on) {
                Kit.rowLit(g, x, ry, rw, 18 + 2 * Theme.SP_1 + 2);
            }
            int sy = ry + Theme.SP_1 + 1;
            Kit.slot(g, x + Theme.SP_2, sy, 18, 0);
            ItemStack icon = host.icon(it);
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + Theme.SP_2 + 1, sy + 1);
            }
            int ty = Type.BODY.yIn(sy, 18);
            String meta = BuildText.typeOf(it) + " · Lv " + it.level();
            int right = x + rw - Theme.SP_2;
            if (on) {
                int d = 12;
                int dy = sy + 3;
                Kit.seal(g, right - d, dy, d, d, BuildControls.CHOSEN);
                Art.icon(g, "check", right - d + Ui.iconIn(d, 8), dy + Ui.iconIn(d, 8), 8, Theme.CREAM);
                right -= d + Theme.SP_3;
            }
            int nx = x + Theme.SP_2 + 18 + Theme.SP_2;
            int metaW = Type.META.width(ui.font, meta);
            if (right - metaW - Theme.SP_4 - nx < 40) {
                meta = "Lv " + it.level();
                metaW = Type.META.width(ui.font, meta);
            }
            Type.META.draw(g, ui.font, meta, right - metaW, Type.META.yIn(sy, 18), Theme.INK_3);
            String name = (on ? Type.LABEL : Type.BODY).fit(ui.font, it.name(), right - metaW - Theme.SP_4 - nx);
            // the name in its tier's colour, bold when picked
            Integer rgb = it.tier().colour().getColor();
            (on ? Type.LABEL : Type.BODY).draw(g, ui.font, name, nx, ty, rgb == null ? Theme.INK : 0xFF000000 | Theme.paperRgb(rgb));
            ui.btn(x, ry, rw, PITCH, "item", it);
            if (DevHooks.ON) {
                DevHooks.part("pickrow", g.pose(), x, ry, rw, PITCH);
            }
            // the item's own tooltip (stats, ranges, requirements) with what a click does under it
            host.item(x, ry, rw, PITCH, it, icon, List.of(
                    L.c(on ? "wynnlabs.pick.row_off" : "wynnlabs.pick.row_on").withStyle(ChatFormatting.DARK_GRAY)));
        }
        // the scrollbar: drag its thumb, click its track for a page (ScrollBar)
        listBar.place(x + w - ScrollBar.W, y, h, rows, items.size(), listScroll);
        listBar.draw(g, mx, my);
    }

    /**
     * While the search runs, in the list's place, as an empty page is laid out: a spinner and
     * "Building around …", the phase and the sets tried, a bar of the milestones done, the best
     * score so far and "Cancel". The preview freezes it (Around.previewLoading).
     */
    private void drawLoading(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        Roadmap.Job j = Around.progress();
        double[] pv = Around.previewLoading;
        double frac = pv != null ? pv[0] : j == null ? 0 : j.done / (double) Math.max(1, j.total);
        int phase = pv != null ? (int) pv[1] : j == null ? 0 : j.phase;
        long tried = pv != null ? (long) pv[2] : j == null ? 0 : j.tried.get();
        double best = pv != null ? pv[3] : j == null ? Double.NaN : j.best;
        List<String> forNames = pv != null ? BuildWindow.names(win.chosen) : Around.runningFor();
        String who = forNames.isEmpty() ? L.t("wynnlabs.pick.loading_none") : L.t("wynnlabs.pick.loading", String.join(", ", forNames));
        int cx = x + w / 2;
        int need = 16 + Theme.SP_4 + Type.HEADING.lineHeight + Theme.SP_2 + Type.META.lineHeight + Theme.SP_4 + 6 + Theme.SP_3
                + Type.META.lineHeight + Theme.SP_5 + Ui.CHIP_H;
        int ty = y + Math.max(0, (h - need) / 3);
        spinner(g, cx, ty + 8, pv != null);
        ty += 16 + Theme.SP_4;
        String t = Type.HEADING.fit(ui.font, who, w - 2 * Theme.SP_4);
        Type.HEADING.draw(g, ui.font, t, cx - Type.HEADING.width(ui.font, t) / 2, ty, Theme.INK);
        ty += Type.HEADING.lineHeight + Theme.SP_2;
        String ph = L.t(PHASES[Math.max(0, Math.min(PHASES.length - 1, phase))]) + " · "
                + L.n("wynnlabs.pick.tried", tried, String.format(Locale.ROOT, "%,d", tried));
        ph = Type.META.fit(ui.font, ph, w - 2 * Theme.SP_4);
        Type.META.draw(g, ui.font, ph, cx - Type.META.width(ui.font, ph) / 2, ty, Theme.INK_3);
        ty += Type.META.lineHeight + Theme.SP_4;
        int bw = Math.min(260, w - 2 * Theme.SP_6);
        Art.box(g, cx - bw / 2, ty, bw, 6, 3, Theme.WELL, 0);
        int fw = (int) Math.round(bw * Math.max(0, Math.min(1, frac)));
        if (fw >= 6) {
            Art.box(g, cx - bw / 2, ty, fw, 6, 3, BuildControls.CHOSEN, 0);
        }
        ty += 6 + Theme.SP_3;
        if (!Double.isNaN(best) && best > 0) {
            String b = L.t("wynnlabs.pick.best_so_far", String.format(Locale.ROOT, "%,d", Math.round(best)));
            Type.META.draw(g, ui.font, b, cx - Type.META.width(ui.font, b) / 2, ty, Theme.INK_2);
        }
        ty += Type.META.lineHeight + Theme.SP_5;
        String cancel = L.t("wynnlabs.pick.cancel");
        int cw = ui.controlW("x", cancel, w);
        ui.control(g, host, cx - cw / 2, ty, Ui.CHIP_H, "x", cancel, BuildControls.WOOD, false, w, "cancelRun", null,
                List.of(L.c("wynnlabs.pick.cancel_tip").withStyle(ChatFormatting.GRAY)), mx, my);
    }

    /** A spinner of eight dots on a ring, one bright and two fading behind it, turning by the clock; still in the preview. */
    private static void spinner(GuiGraphics g, int cx, int cy, boolean still) {
        int head = still ? 0 : (int) (System.currentTimeMillis() / 90 % 8);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int dx = (int) Math.round(Math.cos(a) * 6);
            int dy = (int) Math.round(Math.sin(a) * 6);
            int age = Math.floorMod(head - i, 8);
            int alpha = age == 0 ? 0xFF : age == 1 ? 0xB0 : age == 2 ? 0x70 : 0x30;
            Art.box(g, cx + dx - 2, cy + dy - 2, 4, 4, 2, alpha << 24 | (BuildControls.CHOSEN & 0xFFFFFF), 0);
        }
    }

    /** The engine's phases (Roadmap.Job#phase), as the loading view names them. */
    private static final String[] PHASES = {"wynnlabs.pick.phase_items", "wynnlabs.pick.phase_slots", "wynnlabs.pick.phase_sets",
        "wynnlabs.pick.phase_points", "wynnlabs.pick.phase_budget", "wynnlabs.pick.phase_rank"};
    private static final String[] FOCUS = {"wynnlabs.pick.focus_damage", "wynnlabs.pick.focus_balanced", "wynnlabs.pick.focus_tanky"};
    private static final String[] FOCUS_TIPS = {"wynnlabs.pick.focus_damage_tip", "wynnlabs.pick.focus_balanced_tip",
        "wynnlabs.pick.focus_tanky_tip"};
    private static final String[] FOCUS_ICONS = {"sword", "scale", "shield"};
    private static final int[] FOCUS_ORDER = {Tuning.DAMAGE, Tuning.BALANCED, Tuning.TANKY};

    // ---- the strip ----

    /** The strip on {@code t}: Damage / Balanced / Tanky, Mana, Speed, and the budget's menu; {@code edit}: the edit card's. */
    void strip(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int mx, int my) {
        stripOf(g, host, win.tuning, false, x, y, w, mx, my);
    }

    /** The strip's chips: their names on a large window, icons alone on a small one (the name in the tooltip). */
    void stripOf(GuiGraphics g, BuildWindow.Host host, Tuning.Choice t, boolean edit, int x, int y, int w, int mx, int my) {
        int room = win.compact || edit ? 0 : w;
        int px = x;
        for (int i = 0; i < 3; i++) {
            String name = L.t(FOCUS[i]);
            px += ui.control(g, host, px, y, Ui.CHIP_H, FOCUS_ICONS[i], name, BuildControls.WOOD, t.focus() == FOCUS_ORDER[i], room,
                    edit ? "editFocus" : "focus", FOCUS_ORDER[i], List.of(L.c(FOCUS_TIPS[i]).withStyle(ChatFormatting.GRAY)), mx, my)
                    + Theme.SP_1;
        }
        px += Theme.SP_4;
        px += ui.control(g, host, px, y, Ui.CHIP_H, "droplet", L.t("wynnlabs.pick.mana"), BuildControls.WOOD, t.mana(), room,
                edit ? "editMana" : "mana", null, List.of(L.c("wynnlabs.pick.mana_tip").withStyle(ChatFormatting.GRAY)), mx, my) + Theme.SP_2;
        px += ui.control(g, host, px, y, Ui.CHIP_H, "shoe", L.t("wynnlabs.pick.speed"), BuildControls.WOOD, t.speed(), room,
                edit ? "editSpeed" : "speed", null, List.of(L.c("wynnlabs.pick.speed_tip").withStyle(ChatFormatting.GRAY)), mx, my) + Theme.SP_2;
        int menuId = edit ? BuildWindow.MENU_EDIT_BUDGET : BuildWindow.MENU_BUDGET;
        String budget = L.t("wynnlabs.pick.budget", BuildText.budgetText(t.budget()));
        int bw = Ui.iconChipWidth(ui.font, "coin", budget, true);
        int[] at = {px, y, bw, Ui.CHIP_H};
        UiMenu.chip(g, ui.font, "coin", budget, px, y, win.menu == menuId, mx, my);
        ui.btn(px, y, bw, Ui.CHIP_H, "menu", menuId);
        host.tip(px, y, bw, Ui.CHIP_H, List.of(L.c("wynnlabs.pick.budget_tip").withStyle(ChatFormatting.GRAY)));
        if (win.menu == menuId) {
            win.menuAt = at;
        }
    }

    /**
     * The chosen items as purple chips (a click takes one out), Clear (an eraser), and the purple
     * "Suggest build" on the right (or "Make it the plan" when they cannot be worn together).
     */
    private void drawChips(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, Locks.Problem problem, int mx, int my) {
        boolean can = win.chosen.isEmpty() || problem == null;
        String sug = L.t(can ? "wynnlabs.pick.suggest" : "wynnlabs.pick.as_plan");
        String icon = can ? "sparkles" : "check";
        int sw = ui.controlW(icon, sug, win.compact ? 0 : w / 3, BuildControls.PRIMARY);
        int sx = x + w - sw;
        ui.control(g, host, sx, y, Ui.BUTTON_H, icon, sug, BuildControls.PRIMARY, false, win.compact ? 0 : w / 3, can ? "suggest" : "pickPlan", null,
                List.of(L.c(can ? "wynnlabs.pick.suggest_tip" : "wynnlabs.pick.as_plan_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        int room = sx - Theme.SP_4;
        if (win.chosen.isEmpty()) {
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, L.t("wynnlabs.pick.chips_none"), room - x), x, Type.META.yIn(cy, Ui.CHIP_H), Theme.INK_3);
            return;
        }
        int clearW = Ui.CHIP_H + 2;
        int cx = x;
        for (int i = 0; i < win.chosen.size(); i++) {
            ItemData it = win.chosen.get(i);
            int left = win.chosen.size() - i;
            String more = "+" + left;
            int moreW = Type.BODY.width(ui.font, more) + Theme.SP_3;
            int cw = Math.min(Type.BODY.width(ui.font, it.name()) + 2 * Theme.SP_3 + Theme.SP_2 + 6, 120);
            if (cx + cw + Theme.SP_2 + clearW + (left > 1 ? moreW + Theme.SP_2 : 0) > room) {
                Type.BODY.draw(g, ui.font, more, cx, Type.BODY.yIn(cy, Ui.CHIP_H), Theme.INK_2);
                cx += moreW;
                break;
            }
            cx += ui.chosenChip(g, host, cx, cy, it.name(), 120, "unchoose", it, mx, my) + Theme.SP_2;
        }
        ui.iconBtn(g, host, cx, cy, "eraser", false, "clear", null, List.of(Component.literal(L.t("wynnlabs.pick.clear")),
                L.c("wynnlabs.pick.clear_tip").withStyle(ChatFormatting.GRAY)), mx, my);
    }

}
