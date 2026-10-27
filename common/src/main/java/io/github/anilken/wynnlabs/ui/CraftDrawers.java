package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.LevelRoad;
import io.github.anilken.wynnlabs.crafting.SetSuggestions;
import io.github.anilken.wynnlabs.crafting.Shopping;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.anilken.wynnlabs.ui.CraftFormat.dec;
import static io.github.anilken.wynnlabs.ui.CraftFormat.pct;

/**
 * The crafting tab's drawers ({@link UiDrawer}): a slot's ingredient picker (a search that
 * filters, sorted by what each adds to the goal there), the shopping list (what is missing and
 * where it comes from, what is owned), the saved recipes and a suggested set's pieces.
 */
final class CraftDrawers extends CraftPanel {

    enum Kind { NONE, PICK, SHOP, SAVED, SET }

    private UiDrawer drawer;
    private Kind kind = Kind.NONE;
    private int slot = -1;
    private boolean ownedOnly;
    SetSuggestions.CraftSet set;
    private String statusText;

    CraftDrawers(CraftingTab tab) {
        super(tab);
    }

    private UiDrawer drawer() {
        if (drawer == null && tab.screen instanceof RoadmapScreen rs) {
            drawer = new UiDrawer(rs);
        }
        return drawer;
    }

    boolean showing() {
        return kind != Kind.NONE && drawer != null && drawer.showing();
    }

    /** The slot whose picker is open, or -1. */
    int pickingSlot() {
        return showing() && kind == Kind.PICK ? slot : -1;
    }

    void close() {
        if (drawer != null) {
            drawer.close();
        }
        kind = Kind.NONE;
        slot = -1;
        set = null;
        if (tab.pickSearch != null) {
            tab.pickSearch.setFocused(false);
        }
    }

    /** The picker's list goes back to the top when the search changes. */
    void pickScroll() {
    }

    void openPicker(int s) {
        if (drawer() == null) {
            return;
        }
        kind = Kind.PICK;
        slot = s;
        tab.pickSearch.setValue("");
        UiInput.focus(tab.pickSearch, tab.screen::setFocused);
        drawer.show(L.t("wynnlabs.craft.picker_title", s + 1), this::picker);
    }

    void openShop() {
        if (drawer() == null) {
            return;
        }
        kind = Kind.SHOP;
        statusText = null;
        drawer.show(L.t("wynnlabs.craft.shopping"), this::shop);
    }

    void openSaved() {
        if (drawer() == null) {
            return;
        }
        kind = Kind.SAVED;
        drawer.show(L.t("wynnlabs.craft.saved"), tab.savedPanel::content);
    }

    void openSet(SetSuggestions.CraftSet s) {
        if (drawer() == null) {
            return;
        }
        kind = Kind.SET;
        set = s;
        drawer.showWide(tab.setsPanel.title(s), (g, x, y, w, top, bottom, mx, my) -> tab.setsPanel.detail(g, set, x, y, w, mx, my));
    }

    /** Draws the open drawer over the page {x, y, w, h}; its box takes the clicks. */
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        if (!showing()) {
            return;
        }
        List<int[]> boxes = new ArrayList<>();
        int from = tab.overlay.size();
        drawer.page(x, y, w, h);
        drawer.draw(g, mx, my, boxes);
        int[] b = drawer.body;
        if (b != null) {
            // what scrolled out of the body takes no click and shows no tooltip
            tab.overlay.subList(from, tab.overlay.size()).removeIf(a -> a.y() + a.h() <= b[1] || a.y() >= b[1] + b[3]);
        }
        tab.overlayBox = boxes.isEmpty() ? null : boxes.get(0);
    }

    /** A click inside the drawer: its ✕ or its bar; true when taken. */
    boolean clicked(double mx, double my, int button) {
        if (drawer == null) {
            return false;
        }
        boolean took = drawer.clicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(button, 0)));
        if (took && !drawer.showing()) {
            close();
        }
        return took;
    }

    void scrolled(double mx, double my, double v) {
        if (drawer != null) {
            drawer.scrolled(mx, my, v);
        }
    }

    boolean drag(double mx, double my) {
        return drawer != null && drawer.drag(mx, my);
    }

    void release() {
        if (drawer != null) {
            drawer.release();
        }
    }

    private void act(int x, int y, int w, int h, Runnable r, List<net.minecraft.network.chat.Component> tip) {
        tab.overlay.add(new Act(x, y, w, h, r, null, tip));
    }

    // ---- the picker ----

    private int picker(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        CraftData.Recipe r = tab.recipe();
        int lv = tab.level(r.profession());
        List<Ingredient> lay = tab.layout();
        int slotEff = Craft.effectiveness(lay)[slot];
        String q = tab.pickSearch.getValue().strip().toLowerCase(Locale.ROOT);
        CraftSearch.Goal goal = tab.goal();
        int cy = y;
        String main = tab.weights.isEmpty() ? null : tab.statName(tab.weights.keySet().iterator().next());
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, L.t("wynnlabs.craft.picker_sub", main == null ? "—" : main, pct(slotEff)), w),
                x, cy, Theme.INK_3);
        cy += Type.META.lineHeight + Theme.SP_3;
        UiInput.Hit hit = UiInput.search(g, tab.font, tab.pickSearch, L.t("wynnlabs.craft.search_hint"), false, x, cy, w, mx, my);
        act(x, cy, w, UiInput.H, () -> UiInput.focus(tab.pickSearch, tab.screen::setFocused), null);
        if (hit.clear() != null) {
            int[] c = hit.clear();
            act(c[0], c[1], c[2], c[3], () -> tab.pickSearch.setValue(""), null);
        }
        cy += UiInput.H + Theme.SP_3;
        int cx = x;
        for (int i = 0; i < 2; i++) {
            String label = L.t(i == 0 ? "wynnlabs.craft.picker_all" : "wynnlabs.craft.picker_owned");
            int cw = Ui.chipWidth(tab.font, label);
            boolean on = (i == 1) == ownedOnly;
            Ui.chip(g, tab.font, label, cx, cy, cw, on, false, false, mx, my);
            boolean owned = i == 1;
            act(cx, cy, cw, Ui.CHIP_H, () -> ownedOnly = owned, null);
            cx += cw + Theme.SP_2;
        }
        cy += Ui.CHIP_H + Theme.SP_4;
        record Row(Ingredient ing, double gain, int own) {}
        List<Row> rows = new ArrayList<>();
        for (Ingredient i : tab.ingredients) {
            if (!i.usableBy(r.profession()) || i.level() > r.maxLevel()) {
                continue;
            }
            if (!q.isEmpty() && !i.name().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            int own = tab.owned(i.name());
            if (ownedOnly && own <= 0) {
                continue;
            }
            double gain = 0;
            for (Map.Entry<String, Double> e : goal.weights().entrySet()) {
                Ingredient.Stat st = i.ids().get(e.getKey());
                if (st != null) {
                    gain += e.getValue() * Craft.scaled(st, slotEff).mean();
                }
            }
            rows.add(new Row(i, gain, own));
        }
        rows.sort((a, b) -> {
            boolean la = a.ing().level() > lv;
            boolean lb = b.ing().level() > lv;
            if (la != lb) {
                return la ? 1 : -1;
            }
            int c = Double.compare(b.gain(), a.gain());
            return c != 0 ? c : a.ing().name().compareTo(b.ing().name());
        });
        if (rows.isEmpty()) {
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.craft.picker_empty"), x, cy, Theme.INK_3);
            return cy + Type.MUTED.lineHeight - y;
        }
        int rowH = Type.BODY.lineHeight + Type.META.lineHeight;
        for (Row row : rows) {
            if (cy + rowH > top && cy < bottom) {
                pickRow(g, row.ing(), row.gain(), row.own(), lv, lay, x, cy, w, rowH, mx, my);
            }
            cy += rowH + Theme.SP_2;
        }
        return cy - Theme.SP_2 - y;
    }

    private void pickRow(GuiGraphics g, Ingredient in, double gain, int own, int lv, List<Ingredient> lay, int x, int y, int w, int rowH,
                         int mx, int my) {
        boolean locked = in.level() > lv;
        boolean hover = in(mx, my, x, y, w, rowH);
        CraftIcons.slot(g, x, y + (rowH - 18) / 2, 18, CraftIcons.of(in.name()));
        int tx = x + 18 + Theme.SP_3;
        CraftSearch.Price p = tab.prices.get(in.name());
        String price = p == null ? "—" : "~" + Prices.fmt(p.emeralds());
        String g1 = (gain >= 0 ? "+" : "") + dec(gain, gain == Math.rint(gain) ? 0 : 1);
        String ownT = own > 0 ? "×" + own : "";
        int pw = 48;
        int ow = 26;
        int gw = 44;
        int right = x + w;
        Type.META.drawRight(g, tab.font, price, right, y + 1, p == null ? Theme.INK_3 : Theme.PRICE);
        right -= pw + Theme.SP_3;
        Type.META.drawRight(g, tab.font, ownT, right, y + 1, Theme.GOOD);
        right -= ow + Theme.SP_3;
        Type.NUMBER.drawRight(g, tab.font, g1, right, y, gain > 0 ? Theme.GOOD : gain < 0 ? Theme.BAD : Theme.INK_3);
        right -= gw + Theme.SP_3;
        int nx = tx;
        if (locked) {
            Art.icon(g, "lock", nx, Type.BODY.onCaps(y, 8), 8, Theme.BAD);
            nx += 8 + Theme.SP_1;
        }
        String name = Type.BODY.fit(tab.font, in.name(), right - nx - in.tier() * 6);
        Type.BODY.draw(g, tab.font, name, nx, y, locked ? Theme.INK_3 : hover ? Theme.LINK : Theme.INK);
        int sx = nx + Type.BODY.width(tab.font, name) + Theme.SP_2;
        for (int k = 0; k < in.tier(); k++) {
            Art.icon(g, "star-filled", sx + k * 6, Type.BODY.onCaps(y, 5), 5, Theme.GOLD_DEEP);
        }
        List<String> profs = new ArrayList<>();
        for (String pr : in.professions()) {
            profs.add(Professions.name(pr.toUpperCase(Locale.ROOT)));
        }
        String sub = "Lv " + in.level() + " · " + String.join(", ", profs);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, right - tx), tx, y + Type.BODY.lineHeight, locked ? Theme.BAD : Theme.INK_3);
        String name2 = in.name();
        act(x, y, w, rowH, () -> {
            tab.slots[slot] = name2;
            close();
        }, card(in));
        if (hover) {
            List<Ingredient> p2 = new ArrayList<>(lay);
            p2.set(slot, in);
            tab.nextPreview = p2;
        }
    }

    // ---- shopping ----

    private int shop(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        CraftData.Recipe r = tab.recipe();
        Shopping.Needs n = tab.recipePanel.needs(r);
        int cy = y;
        if (n.empty()) {
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.craft.shopping_none"), x, cy, Theme.GOOD);
            cy += Type.MUTED.lineHeight + Theme.SP_4;
        } else {
            cy = tab.recipePanel.head(g, L.t("wynnlabs.craft.shop_missing"), String.valueOf(n.lines().size()), x, cy, w);
            for (Shopping.Line l : n.lines()) {
                cy = shopRow(g, l, x, cy, w, mx, my) + Theme.SP_2;
            }
            cy += Theme.SP_4 - Theme.SP_2;
        }
        // what is owned of the grid's ingredients
        List<String> owned = new ArrayList<>();
        for (String s : tab.slots) {
            if (s != null && tab.owned(s) > 0 && !owned.contains(s)) {
                owned.add(s);
            }
        }
        if (!owned.isEmpty()) {
            cy = tab.recipePanel.head(g, L.t("wynnlabs.craft.shop_owned"), String.valueOf(owned.size()), x, cy, w);
            int rowH = 18;
            for (String s : owned) {
                CraftIcons.slot(g, x, cy, rowH, CraftIcons.of(s));
                int ty = Type.BODY.yIn(cy, rowH);
                Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, s, w - 80), x + rowH + Theme.SP_3, ty);
                Type.META.drawRight(g, tab.font, L.t("wynnlabs.craft.shop_have", tab.owned(s)), x + w, ty + 1, Theme.GOOD);
                cy += rowH + Theme.SP_2;
            }
            cy += Theme.SP_4 - Theme.SP_2;
        }
        // the total, the list to copy and the station
        if (n.cost() > 0) {
            Type.LABEL.draw(g, tab.font, L.t("wynnlabs.craft.total", Prices.fmt(n.cost())), x, Type.LABEL.yIn(cy, Ui.CHIP_H), Theme.PRICE);
        }
        String copy = L.t("wynnlabs.craft.copy_list");
        String station = L.t("wynnlabs.craft.station");
        int sw = Ui.iconChipWidth(tab.font, "map-pin", station, false);
        int cw = Ui.iconChipWidth(tab.font, "copy", copy, false);
        int sx = x + w - sw;
        Ui.iconChip(g, tab.font, "map-pin", station, false, sx, cy, sw, false, mx, my);
        String prof = r.profession();
        act(sx, cy, sw, Ui.CHIP_H, () -> {
            int d = Crafting.pinStation(prof, L.t("wynnlabs.craft.station_label", Professions.name(prof.toUpperCase(Locale.ROOT))));
            statusText = d < 0 ? L.t("wynnlabs.craft.station_none") : L.t("wynnlabs.craft.station_pinned", d);
        }, List.of(L.c("wynnlabs.craft.station_tip", Professions.name(prof.toUpperCase(Locale.ROOT)))));
        int cx = sx - Theme.SP_2 - cw;
        Ui.iconChip(g, tab.font, "copy", copy, false, cx, cy, cw, false, mx, my);
        act(cx, cy, cw, Ui.CHIP_H, () -> {
            StringBuilder b = new StringBuilder();
            for (Shopping.Line l : n.lines()) {
                b.append(l.count()).append("x ").append(l.name()).append(l.material() ? " T" + l.tier() : "").append('\n');
            }
            Minecraft.getInstance().keyboardHandler.setClipboard(b.toString().strip());
            tab.flash.accept(L.t("wynnlabs.craft.list_copied"));
        }, null);
        cy += Ui.CHIP_H;
        if (statusText != null) {
            cy += Theme.SP_3;
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, statusText, w), x, cy, Theme.GOOD);
            cy += Type.META.lineHeight;
        }
        return cy - y;
    }

    /** A missing line: the item, how many, where it comes from (gather or the Trade Market) and its price. */
    private int shopRow(GuiGraphics g, Shopping.Line l, int x, int y, int w, int mx, int my) {
        int rowH = Type.BODY.lineHeight + Type.META.lineHeight;
        CraftIcons.slot(g, x, y + (rowH - 18) / 2, 18, CraftIcons.of(l.name()));
        int tx = x + 18 + Theme.SP_3;
        String price = l.cost() == null ? (l.material() ? "" : "—") : "~" + Prices.fmt(l.cost());
        int pw = Type.NUMBER.width(tab.font, price);
        Type.NUMBER.drawRight(g, tab.font, price, x + w, y, l.cost() == null ? Theme.INK_3 : Theme.PRICE);
        String name = l.name() + "  ×" + l.count() + (l.material() ? " · " + L.t("wynnlabs.craft.tier_n", l.tier()) : "");
        Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, name, x + w - pw - Theme.SP_4 - tx), tx, y);
        String where;
        boolean link = false;
        if (l.material()) {
            LevelRoad.Source s = tab.roadPanel.sourceOf(l.name());
            where = s == null ? L.t("wynnlabs.craft.from_market")
                    : L.t("wynnlabs.craft.from_gather", Professions.name(s.profession().toUpperCase(Locale.ROOT)), s.level(), s.resource());
        } else {
            where = L.t("wynnlabs.craft.from_market_search");
            link = true;
        }
        String shown = Type.META.fit(tab.font, where, x + w - tx);
        Type.META.draw(g, tab.font, shown, tx, y + Type.BODY.lineHeight, link ? Theme.LINK : Theme.INK_2);
        if (link) {
            String n = l.name();
            act(tx, y + Type.BODY.lineHeight, Type.META.width(tab.font, shown), Type.META.lineHeight, () -> openSearch(n), null);
        }
        return y + rowH;
    }

    /** "Ara'da aç": the search tab on this name (where it drops, who sells it). */
    private void openSearch(String name) {
        close();
        if (tab.screen instanceof RoadmapScreen rs) {
            rs.openTab(RoadmapScreen.TAB_SEARCH);
            rs.search.box.setValue(name);
        }
    }
}
