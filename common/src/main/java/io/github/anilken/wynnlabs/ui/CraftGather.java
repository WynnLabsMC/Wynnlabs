package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.ProfessionPlan;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.GatherTool;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.quests.Waypoints;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A gathering profession's page under its bombs: the tool (in the bag, and the best the level
 * allows) beside the gathering kit, then the best spots beside the nearest ones. On a narrow
 * window the four blocks go one under another.
 */
final class CraftGather {

    private static final int ICON = 16;
    /** The narrowest a column may be before the blocks go one under another. */
    private static final int COL_MIN = 230;

    private final CraftingTab tab;
    private final CraftRoadPanel panel;
    final CraftGatherData data;

    CraftGather(CraftingTab tab, CraftRoadPanel panel) {
        this.tab = tab;
        this.panel = panel;
        this.data = new CraftGatherData(panel.data);
    }

    /** Draws the blocks from y across w; returns the y under them. */
    int draw(GuiGraphics g, String profession, int level, int x, int y, int w, int mx, int my) {
        int rowH = Type.BODY.lineHeight + Type.META.lineHeight;
        boolean two = w >= COL_MIN * 2 + Theme.SP_7;
        int cw = two ? (w - Theme.SP_7) / 2 : w;
        int rx = two ? x + cw + Theme.SP_7 : x;
        // the spots first, side by side; then the tool and the kit, each column on its own
        if (two) {
            int top = Math.max(spots(g, profession, level, false, x, y, cw, rowH, false, mx, my),
                    spots(g, profession, level, true, rx, y, cw, rowH, false, mx, my)) + Theme.SP_5;
            return Math.max(tools(g, profession, level, x, top, cw, rowH, mx, my), kit(g, rx, top, cw, rowH, mx, my));
        }
        int cy = spots(g, profession, level, false, x, y, w, rowH, true, mx, my) + Theme.SP_5;
        cy = spots(g, profession, level, true, x, cy, w, rowH, true, mx, my) + Theme.SP_5;
        cy = tools(g, profession, level, x, cy, w, rowH, mx, my) + Theme.SP_5;
        return kit(g, x, cy, w, rowH, mx, my);
    }

    // ---- spots ----

    private int spots(GuiGraphics g, String profession, int level, boolean nearest, int x, int y, int w, int rowH,
                      boolean wide, int mx, int my) {
        int cy = tab.recipePanel.head(g, L.t(nearest ? "wynnlabs.plan.spots_near" : "wynnlabs.plan.spots"), null, x, y, w);
        List<ProfessionPlan.Spot> list = data.spots(profession, level, nearest);
        if (list.isEmpty()) {
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, L.t("wynnlabs.plan.no_spots"), w), x, cy, Theme.INK_3);
            return cy + Type.MUTED.lineHeight;
        }
        for (int i = 0; i < list.size(); i++) {
            cy = spotRow(g, list.get(i), profession, x, cy, w, rowH, wide, mx, my) + (i < list.size() - 1 ? Theme.SP_3 : 0);
        }
        return cy;
    }

    /** A gathering spot: the resource, its level and nodes, (when wide) its materials, how far, and "Compass ›". */
    private int spotRow(GuiGraphics g, ProfessionPlan.Spot s, String profession, int x, int y, int w, int rowH,
                        boolean wide, int mx, int my) {
        int iy = y + (rowH - ICON) / 2;
        Kit.pill(g, x, iy, ICON, ICON, false, false);
        Art.icon(g, CraftRoadPanel.profIcon(profession), x + (ICON - 8) / 2, iy + (ICON - 8) / 2, 8, Theme.INK_2);
        int tx = x + ICON + Theme.SP_3;
        String go = L.t("wynnlabs.road.go_compass");
        int bw = Ui.iconButtonLabelWidth(tab.font, go);
        int bx = x + w - bw;
        int by = y + (rowH - Ui.BUTTON_H) / 2;
        Ui.iconButtonLabel(g, tab.font, "compass", go, bx, by, bw, mx, my);
        Area a = s.node();
        String label = s.resource().name();
        tab.acts.add(new Act(bx, by, bw, Ui.BUTTON_H, () -> pin(a, label), null, null));
        String d = Double.isNaN(s.distance()) ? "" : RoadmapScreen.dist((int) Math.round(s.distance()))
                + dirName(Compass.direction(a, CraftRoadData.position()));
        String sub = wide ? String.join(", ", s.resource().materials()) + (d.isEmpty() ? "" : " · " + d) : d;
        int tw = bx - Theme.SP_4 - tx;
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, s.resource().name() + " · Lv " + s.resource().level() + " · "
                + L.n("wynnlabs.plan.spot_nodes", s.nodes()), tw), tx, y);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, tw), tx, y + Type.LABEL.lineHeight, Theme.INK_3);
        return y + rowH;
    }

    // ---- tool ----

    /**
     * The tool rows: the best one in the bag, then the best one the level allows when it is
     * better (where it is sold: the Tool Merchant sells only tiers 1 and 2, the rest come from
     * the Trade Market), or that the bag's is the best yet.
     */
    private int tools(GuiGraphics g, String profession, int level, int x, int y, int w, int rowH, int mx, int my) {
        GatherTool held = data.heldTool(profession);
        GatherTool open = data.tool(profession, level, true);
        GatherTool later = data.tool(profession, level, false);
        int cy = tab.recipePanel.head(g, L.t("wynnlabs.plan.tool_head"), null, x, y, w);
        if (held != null) {
            boolean worn = held.durability() >= 0 && held.maxDurability() > 0 && held.durability() * 100 < held.maxDurability() * 15;
            cy = row(g, held.icon(), held.name() + " · Lv " + held.level(), Theme.INK,
                    L.t("wynnlabs.plan.tool_line", held.speed(), Math.max(0, held.durability()), held.maxDurability()),
                    worn ? Theme.BAD : Theme.INK_3, null, null, null, x, cy, w, rowH, mx, my);
        }
        if (open != null && (held == null || open.tier() > held.tier())) {
            String speed = L.t("wynnlabs.plan.tool_usable") + " · " + L.t("wynnlabs.plan.tool_speed", open.speed())
                    + (held != null ? " (+" + (open.speed() - held.speed()) + ")" : "");
            String title = open.name() + " · Lv " + open.level();
            if (held != null) {
                cy += Theme.SP_3;
            }
            if (open.tier() <= 2) {
                Area m = panel.data.service("merchant:tool");
                String where = L.t("wynnlabs.plan.tool_merchant");
                String d = m == null ? "" : " · " + RoadmapScreen.dist((int) Math.round(Compass.distance(m, CraftRoadData.position())))
                        + dirName(Compass.direction(m, CraftRoadData.position()));
                cy = row(g, open.icon(), title, Theme.GOOD, speed + " · " + where + d, Theme.INK_3,
                        "compass", L.t("wynnlabs.road.go_compass"), () -> pin(m, where), x, cy, w, rowH, mx, my);
            } else {
                Double typical = price(open.name());
                String market = L.t("wynnlabs.plan.tool_market") + (typical == null ? "" : " ~" + Prices.fmt(typical));
                cy = row(g, open.icon(), title, Theme.GOOD, speed + " · " + market, Theme.INK_3,
                        "tag", L.t("wynnlabs.plan.tool_market_go"), () -> openMarket(open.name(), open.level(), open.icon()),
                        x, cy, w, rowH, mx, my);
            }
        } else if (held != null && later != null) {
            cy += Theme.SP_2;
            String line = L.t("wynnlabs.plan.tool_best", later.name(), later.level());
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, line, w), x, cy, Theme.INK_3);
            cy += Type.MUTED.lineHeight;
        }
        return cy;
    }

    /** One item row: the item, a title and a line under it, and a button on the right when given. */
    private int row(GuiGraphics g, ItemStack icon, String title, int titleColor, String sub, int subColor,
                    String buttonIcon, String button, Runnable action, int x, int y, int w, int rowH, int mx, int my) {
        int size = Math.min(rowH, 20);
        CraftIcons.slot(g, x, y + (rowH - size) / 2, size, icon);
        int tx = x + size + Theme.SP_3;
        int right = x + w;
        if (button != null) {
            int bw = Ui.iconButtonLabelWidth(tab.font, button);
            int bx = x + w - bw;
            int by = y + (rowH - Ui.BUTTON_H) / 2;
            Ui.iconButtonLabel(g, tab.font, buttonIcon, button, bx, by, bw, mx, my);
            tab.acts.add(new Act(bx, by, bw, Ui.BUTTON_H, action, null, null));
            right = bx - Theme.SP_4;
        }
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, title, right - tx), tx, y, titleColor);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, right - tx), tx, y + Type.LABEL.lineHeight, subColor);
        return y + rowH;
    }

    // ---- kit ----

    /**
     * The gathering kit: ingredients that give gathering XP or speed, for crafts that are worn
     * (armour and accessories: the bonus stays) and for those that are used up (potions, food,
     * scrolls: drink or eat before gathering), the best two of each; then the charm to aim for.
     */
    private int kit(GuiGraphics g, int x, int y, int w, int rowH, int mx, int my) {
        int combat = GameState.current().level();
        List<List<Entry>> kit = data.kit(combat);
        int cy = tab.recipePanel.head(g, L.t("wynnlabs.plan.kit_head"), null, x, y, w);
        boolean any = false;
        List<Entry> shown = new ArrayList<>();
        for (int group = 0; group < 2; group++) {
            List<Entry> rows = new ArrayList<>();
            for (int k = group * 2; k < group * 2 + 2 && k < kit.size(); k++) {
                for (Entry e : kit.get(k)) {
                    if (!rows.contains(e) && !shown.contains(e)) {   // one worn and used up shows once
                        rows.add(e);
                    }
                }
            }
            shown.addAll(rows);
            if (rows.isEmpty()) {
                continue;
            }
            any = true;
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, L.t(group == 0 ? "wynnlabs.plan.kit_gear" : "wynnlabs.plan.kit_use"), w),
                    x, cy, Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_1;
            for (Entry e : rows) {
                cy = kitRow(g, e, group == 0 ? CraftGatherData.KIT_GEAR : CraftGatherData.KIT_USE, x, cy, w, Ui.CHIP_H + 2, mx, my) + Theme.SP_1;
            }
            cy += Theme.SP_2;
        }
        if (!any) {
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, L.t("wynnlabs.plan.kit_none"), w), x, cy, Theme.INK_3);
            cy += Type.MUTED.lineHeight;
        }
        Entry charm = data.kitCharm();
        int[] cx = charm == null ? null : CraftGatherData.charmXp(charm);
        if (cx != null) {
            String line = L.t(charm.level() > combat ? "wynnlabs.plan.kit_charm_later" : "wynnlabs.plan.kit_charm",
                    charm.name(), charm.level(), range(cx));
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, line, w), x, cy, Theme.INK_3);
            cy += Type.MUTED.lineHeight;
        }
        return cy;
    }

    /**
     * One kit ingredient on one line: its picture and name, its gathering stats in colour (the
     * gains first, a loss after), its price on the right and a small market button; its level
     * and crafts show on hover.
     */
    private int kitRow(GuiGraphics g, Entry e, List<String> crafts, int x, int y, int w, int rowH, int mx, int my) {
        String name = e.name();
        ItemStack icon = CraftIcons.of(name);
        int size = 14;
        CraftIcons.slot(g, x, y + (rowH - size) / 2, size, icon);
        int tx = x + size + Theme.SP_3;
        int bx = x + w - (Ui.CHIP_H + 2);
        int by = y + (rowH - Ui.CHIP_H) / 2;
        Ui.iconButton(g, "tag", bx, by, false, mx, my);
        int lv = e.level();
        tab.acts.add(new Act(bx, by, Ui.CHIP_H + 2, Ui.CHIP_H, () -> openMarket(name, lv, icon), null,
                List.of(net.minecraft.network.chat.Component.literal(L.t("wynnlabs.plan.tool_market_go")))));
        int right = bx - Theme.SP_3;
        Double v = price(name);
        int ty = y + (rowH - Type.LABEL.lineHeight) / 2;
        int my2 = y + (rowH - Type.META.lineHeight) / 2;
        if (v != null) {
            String pr = Prices.fmt(v);
            Type.META.drawRight(g, tab.font, pr, right, my2, Theme.INK_3);
            right -= Type.META.width(tab.font, pr) + Theme.SP_4;
        }
        // the stats: gains, then losses
        List<String> parts = new ArrayList<>();
        List<Integer> inks = new ArrayList<>();
        for (boolean gains : new boolean[] {true, false}) {
            for (String[] st : new String[][] {{CraftGatherData.KIT_XP, "wynnlabs.plan.kit_xp"}, {CraftGatherData.KIT_SPEED, "wynnlabs.plan.kit_speed"}}) {
                int[] r = CraftGatherData.stat(e, st[0]);
                if (r != null && (r[1] > 0) == gains) {
                    parts.add(L.t(st[1], range(r)));
                    inks.add(gains ? Theme.GOOD : Theme.BAD);
                }
            }
        }
        int statsW = 0;
        for (String p : parts) {
            statsW += Type.META.width(tab.font, " " + p);
        }
        int nameW = Math.max(0, Math.min(Type.LABEL.width(tab.font, name), right - tx - statsW));
        String fit = Type.LABEL.fit(tab.font, name, nameW);
        Type.LABEL.draw(g, tab.font, fit, tx, ty, Theme.INK);
        int sx = tx + Type.LABEL.width(tab.font, fit);
        for (int k = 0; k < parts.size() && sx < right; k++) {
            String f = Type.META.fit(tab.font, " " + parts.get(k), right - sx);
            Type.META.draw(g, tab.font, f, sx, my2, inks.get(k));
            sx += Type.META.width(tab.font, f);
        }
        // the level and the crafts on hover
        List<String> used = new ArrayList<>();
        for (String c : e.professions()) {
            if (crafts.contains(c)) {
                used.add(Professions.name(c));
            }
        }
        tab.acts.add(new Act(x, y, right - x, rowH, null, null, List.of(
                net.minecraft.network.chat.Component.literal(name),
                net.minecraft.network.chat.Component.literal("Lv " + lv + " · " + String.join(", ", used)))));
        return y + rowH;
    }

    // ---- helpers ----

    /** An item's typical price, or null; an unknown one is asked of the market once. */
    private static Double price(String name) {
        Prices.Price p = Prices.of(name);
        Double v = p == null ? null : p.typical();
        if (v == null) {
            Prices.refreshSoon(name);
        }
        return v;
    }

    /** A stat's range with its sign: "+5-6%", "-4%", "-4 to -5%" (the smaller loss first). */
    private static String range(int[] r) {
        if (r[1] <= 0) {
            return r[0] == r[1] ? r[0] + "%" : r[1] + " to " + r[0] + "%";
        }
        return r[0] == r[1] ? "+" + r[0] + "%" : "+" + r[0] + "-" + r[1] + "%";
    }

    /** The Trade Market window of an item (its listings and price). */
    private static void openMarket(String name, int level, ItemStack icon) {
        BoxMarket.Item item = new BoxMarket.Item(name, Slot.UNKNOWN, Tier.NORMAL, level, null, 0, true);
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new BoxScreen(new BoxScreen.Single(item, null, false), icon, mc.screen));
    }

    private void pin(Area a, String label) {
        if (a == null) {
            panel.note = L.t("wynnlabs.road.no_place");
            return;
        }
        panel.note = Waypoints.go(a.x(), a.y(), a.z(), label) ? L.t("wynnlabs.plan.compass_set", label) : L.t("wynnlabs.plan.compass_failed");
    }

    private static String dirName(String d) {
        return d == null ? "" : " " + SearchCardRows.dirName(d);
    }
}
