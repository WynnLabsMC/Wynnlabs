package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.crafting.ToolLadder;
import io.github.anilken.wynnlabs.crafting.ToolLadder.Kind;
import io.github.anilken.wynnlabs.crafting.ToolLadder.Rung;
import io.github.anilken.wynnlabs.crafting.ToolLadder.State;
import io.github.anilken.wynnlabs.crafting.ToolLadder.Where;
import io.github.anilken.wynnlabs.crafting.ToolWays;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.GatherTool;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.quests.Waypoints;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tools: the four gathering tool kinds side by side (your best, the next one and its cheapest way),
 * the picked kind's ladder from T1 to T13 (yours, the next, the locked ones), and how to get the
 * picked tier: the upgrade in steps (the tier below, the fragments, an Item Upgrader with a compass)
 * and the other ways in a line each. Reads the bag, the main hand and the remembered bank pages;
 * worked out again every two seconds, never per frame.
 */
final class CraftTools extends CraftPanel {

    /** For the preview without Wynntils: every tool, and the bag's. */
    static volatile List<GatherTool> previewTools;
    static volatile List<GatherTool> previewHeld;

    private static final int CARD_MIN = 110;
    private static final int PAD = Theme.SP_3;
    private static final int BOX_MIN = 40;
    private static final int BOX_MAX = 60;
    /** An item's slot: the common 18 px one. */
    private static final int PIC = 18;
    private static final int STEP_H = Type.LABEL.lineHeight + Type.META.lineHeight;

    /** The picked kind, null until first drawn; the picked tier (0: the next one). */
    Kind kind;
    int picked;
    private String note;

    // ---- read every two seconds ----
    private long readAt;
    private List<GatherTool> all = List.of();
    private final Map<Kind, List<Rung>> ladders = new EnumMap<>(Kind.class);
    private final Map<String, GatherTool> byName = new HashMap<>();
    /** The player's own stacks of the tools they have (by name): drawn instead of the model. */
    private final Map<String, ItemStack> ownIcons = new HashMap<>();
    private Map<String, Integer> bag = Map.of();
    private Map<String, Integer> bank = Map.of();
    private final Map<String, List<ToolWays.Way>> waysCache = new HashMap<>();
    private Area upgrader;
    private boolean upgraderRead;

    CraftTools(CraftingTab tab) {
        super(tab);
    }

    /** Draws the page from y (already scrolled) across w; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        if (kind == null) {
            Kind k = tab.roadPanel.profession == null ? null : Kind.of(tab.roadPanel.profession);
            kind = k != null ? k : Kind.PICKAXE;
        }
        refresh();
        if (all.isEmpty()) {
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, L.t("wynnlabs.tools.no_data"), w), x, y, Theme.INK_3);
            return Type.MUTED.lineHeight;
        }
        int cy = cards(g, x, y, w, mx, my) + Theme.SP_5;
        List<Rung> ladder = ladders.getOrDefault(kind, List.of());
        int lv = tab.knownLevel(kind.profession);
        String prof = Professions.name(kind.profession.toUpperCase(Locale.ROOT)) + " · Lv " + (lv > 0 ? lv : "?");
        cy = tab.recipePanel.head(g, L.t("wynnlabs.tools.ladder", kindName(kind)), prof, x, cy, w);
        cy = ladder(g, ladder, x, cy, w, mx, my) + Theme.SP_5;
        Rung sel = selected(ladder);
        if (sel != null) {
            cy = how(g, ladder, sel, x, cy, w, mx, my);
        }
        if (note != null) {
            cy += Theme.SP_3;
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, note, w), x, cy, Theme.GOOD);
            cy += Type.META.lineHeight;
        }
        return cy - y;
    }

    void pickKind(Kind k) {
        if (k != kind) {
            kind = k;
            picked = 0;
            note = null;
        }
    }

    /** The tier the part below explains: the picked one, else the next, else yours. */
    private Rung selected(List<Rung> ladder) {
        for (Rung r : ladder) {
            if (r.tool().tier() == picked) {
                return r;
            }
        }
        Rung n = ToolLadder.next(ladder);
        return n != null ? n : ToolLadder.yours(ladder);
    }

    // ---- the four kinds ----

    private int cards(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int gap = Theme.SP_4;
        int cols = w >= 4 * CARD_MIN + 3 * gap ? 4 : w >= 2 * CARD_MIN + gap ? 2 : 1;
        int cw = (w - (cols - 1) * gap) / cols;
        int ch = 2 * PAD + Type.LABEL.lineHeight + Theme.SP_2 + PIC + Theme.SP_2 + 2 * Type.META.lineHeight;
        Kind[] kinds = Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            int cx = x + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + Theme.SP_2);
            card(g, kinds[i], cx, cy, cw, ch, mx, my);
        }
        int rows = (kinds.length + cols - 1) / cols;
        return y + rows * ch + (rows - 1) * Theme.SP_2;
    }

    /** One kind: "Pickaxe · 70", your best with a tick (or None), the next one and its cheapest way. */
    private void card(GuiGraphics g, Kind k, int x, int y, int w, int h, int mx, int my) {
        boolean on = k == kind;
        boolean hover = in(mx, my, x, y, w, h);
        Kit.box(g, x, y, w, h, on ? Theme.SELECTED : hover ? Theme.CARD_HOVER : Theme.CARD, on ? Theme.GOLD_EDGE : Theme.LINE);
        int ix = x + PAD;
        int iw = w - 2 * PAD;
        int cy = y + PAD;
        int lv = tab.knownLevel(k.profession);
        String prof = k.profession.toUpperCase(Locale.ROOT);
        Art.icon(g, CraftRoadPanel.profIcon(prof), ix, Type.LABEL.onCaps(cy, 8), 8, on ? Theme.INK : Theme.INK_2);
        String title = kindName(k) + " · " + (lv > 0 ? lv : "?");
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, title, iw - 8 - Theme.SP_2), ix + 8 + Theme.SP_2, cy, Theme.INK);
        cy += Type.LABEL.lineHeight + Theme.SP_2;
        List<Rung> ladder = ladders.getOrDefault(k, List.of());
        Rung yours = ToolLadder.yours(ladder);
        if (yours != null) {
            CraftIcons.slot(g, ix, cy, PIC, icon(yours.tool().name()));
            int tx = ix + PIC + Theme.SP_2;
            Art.icon(g, "check", ix + iw - 8, cy + (PIC - 8) / 2, 8, Theme.GOOD);
            Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, yours.tool().name(), ix + iw - 8 - Theme.SP_2 - tx), tx, Type.BODY.yIn(cy, PIC),
                    Theme.INK);
        } else {
            Kit.slot(g, ix, cy, PIC, 0);
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.tools.none"), ix + PIC + Theme.SP_2, Type.MUTED.yIn(cy, PIC), Theme.INK_3);
        }
        cy += PIC + Theme.SP_2;
        Rung next = ToolLadder.next(ladder);
        if (next == null) {
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, L.t("wynnlabs.tools.top"), iw), ix, cy, Theme.GOOD);
        } else {
            boolean now = next.state() == State.NEXT;
            String when = now ? L.t("wynnlabs.tools.now") : "Lv " + next.tool().level();
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, L.t("wynnlabs.tools.next", next.tool().name(), when), iw), ix, cy,
                    now ? Theme.GOOD : Theme.INK_2);
            String cost = ToolLadder.summary(ToolLadder.cheapest(ways(ladder, next)));
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, cost, iw), ix, cy + Type.META.lineHeight, Theme.PRICE);
        }
        clipped(x, y, w, h, () -> pickKind(k), null, List.of(
                Component.literal(Professions.name(prof)).withStyle(ChatFormatting.WHITE),
                L.c("wynnlabs.tools.card_tip").withStyle(ChatFormatting.GRAY)));
    }

    // ---- the ladder ----

    private int ladder(GuiGraphics g, List<Rung> ladder, int x, int y, int w, int mx, int my) {
        if (ladder.isEmpty()) {
            return y;
        }
        int gap = Theme.SP_2;
        int perRow = Math.max(1, Math.min(ladder.size(), (w + gap) / (BOX_MIN + gap)));
        int bw = Math.min(BOX_MAX, (w - (perRow - 1) * gap) / perRow);
        int bh = PAD + PIC + Theme.SP_1 + Type.LABEL.lineHeight + Type.META.lineHeight + PAD - Theme.SP_1;
        Rung sel = selected(ladder);
        for (int i = 0; i < ladder.size(); i++) {
            int bx = x + (i % perRow) * (bw + gap);
            int by = y + (i / perRow) * (bh + gap);
            box(g, ladder, ladder.get(i), sel != null && sel.tool().tier() == ladder.get(i).tool().tier(), bx, by, bw, bh, mx, my);
        }
        int rows = (ladder.size() + perRow - 1) / perRow;
        return y + rows * bh + (rows - 1) * gap;
    }

    /** One tier: its picture, "T5", and Yours / Next / "Lv 45"; the past ones and the locked ones faded. */
    private void box(GuiGraphics g, List<Rung> ladder, Rung r, boolean sel, int x, int y, int w, int h, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, h);
        int fill = sel ? Theme.SELECTED : r.state() == State.YOURS ? 0xFFD9E7C7 : hover ? Theme.CARD_HOVER : Theme.CARD;
        int ring = r.state() == State.NEXT ? Theme.GOOD : sel ? Theme.GOLD_EDGE : Theme.LINE;
        Kit.box(g, x, y, w, h, fill, ring);
        if (r.state() == State.NEXT) {
            Kit.box(g, x + 1, y + 1, w - 2, h - 2, 0, Theme.GOOD);         // a thicker green ring
        }
        int cy = y + PAD;
        CraftIcons.slot(g, x + (w - PIC) / 2, cy, PIC, icon(r.tool().name()));
        cy += PIC + Theme.SP_1;
        String t = "T" + r.tool().tier();
        Type.LABEL.draw(g, tab.font, t, x + (w - Type.LABEL.width(tab.font, t)) / 2, cy, Theme.INK);
        cy += Type.LABEL.lineHeight;
        String status = switch (r.state()) {
            case YOURS -> L.t("wynnlabs.box.ways_yours");
            case NEXT -> L.t("wynnlabs.tools.next_short");
            // held but above this character's level (another character's, in the account bank)
            case LOCKED -> r.where() != null ? L.t("wynnlabs.tools.held_locked", r.tool().level()) : "Lv " + r.tool().level();
            default -> "Lv " + r.tool().level();
        };
        int ink = r.state() == State.YOURS || r.state() == State.NEXT ? Theme.GOOD
                : r.state() == State.LOCKED && r.where() != null ? Theme.WARN : Theme.INK_3;
        String s = Type.META.fit(tab.font, status, w - 2);
        Type.META.draw(g, tab.font, s, x + (w - Type.META.width(tab.font, s)) / 2, cy, ink);
        if ((r.state() == State.PAST || r.state() == State.LOCKED && r.where() == null) && !sel) {
            Kit.box(g, x, y, w, h, 0x8CD8C595, 0);                          // faded under the paper
        }
        int tier = r.tool().tier();
        GatherTool tool = byName.get(r.tool().name());
        // the tooltip (and its way's price) only for the box under the mouse: the rest ask nothing
        List<Component> tip = hover ? tip(ladder, r, tool) : null;
        clipped(x, y, w, h, () -> {
            picked = tier;
            note = null;
        }, () -> openMarket(r.tool().name(), r.tool().level(), icon(r.tool().name())), tip);
    }

    private List<Component> tip(List<Rung> ladder, Rung r, GatherTool tool) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(r.tool().name()).withStyle(ChatFormatting.WHITE));
        if (tool != null) {
            out.add(L.c("wynnlabs.tools.tip_stats", tool.level(), tool.speed(), tool.maxDurability()).withStyle(ChatFormatting.GRAY));
        }
        if (r.where() != null) {
            out.add(Component.literal(whereText(r.where())).withStyle(ChatFormatting.GREEN));
        }
        ToolWays.Way w = ToolLadder.cheapest(ways(ladder, r));
        if (w != null) {
            out.add(L.c("wynnlabs.tools.tip_cheapest", wayTitle(w, r.tool().tier()), ToolLadder.summary(w)).withStyle(ChatFormatting.GOLD));
        }
        out.add(L.c("wynnlabs.tools.tip_click").withStyle(ChatFormatting.GRAY));
        return out;
    }

    // ---- how to get the picked tier ----

    private int how(GuiGraphics g, List<Rung> ladder, Rung sel, int x, int y, int w, int mx, int my) {
        String market = L.t("wynnlabs.plan.tool_market_go");
        int bw = Ui.iconButtonLabelWidth(tab.font, market);
        int bx = x + w - bw;
        int by = y + (Type.LABEL.lineHeight - Ui.BUTTON_H) / 2;
        Ui.iconButtonLabel(g, tab.font, "tag", market, bx, by, bw, mx, my);
        String name = sel.tool().name();
        int level = sel.tool().level();
        clipped(bx, by, bw, Ui.BUTTON_H, () -> openMarket(name, level, icon(name)), null, List.of(L.c("wynnlabs.tools.market_tip")));
        int cy = tab.recipePanel.head(g, L.t("wynnlabs.tools.how", name), "Lv " + level, x, y, w - bw - Theme.SP_3);
        List<ToolWays.Way> ways = ways(ladder, sel);
        ToolWays.Way upgrade = null;
        for (ToolWays.Way way : ways) {
            if (way.kind() == ToolWays.Kind.UPGRADE) {
                upgrade = way;
            }
        }
        if (upgrade != null) {
            cy = steps(g, ladder, sel, upgrade, x, cy, w, mx, my) + Theme.SP_4;
        }
        List<ToolWays.Way> others = new ArrayList<>(ways);
        others.remove(upgrade);
        if (!others.isEmpty()) {
            Type.META.draw(g, tab.font, L.t(upgrade != null ? "wynnlabs.tools.other_ways" : "wynnlabs.box.ways"), x, cy, Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_1;
            ToolWays.Way best = ToolLadder.cheapest(ways);
            for (ToolWays.Way way : others) {
                cy = wayLine(g, way, way == best, sel.tool().tier(), x, cy, w) + Theme.SP_2;
            }
            cy -= Theme.SP_2;
        }
        return cy;
    }

    /** The upgrade in three steps: the tier below, the fragments, an Item Upgrader (with a compass). */
    private int steps(GuiGraphics g, List<Rung> ladder, Rung sel, ToolWays.Way up, int x, int y, int w, int mx, int my) {
        int cy = y;
        ToolWays.Part lower = up.parts().get(0);
        ToolWays.Part frag = up.parts().get(1);
        // 1) the tier below
        Rung below = null;
        for (Rung r : ladder) {
            if (r.tool().tier() == sel.tool().tier() - 1) {
                below = r;
            }
        }
        String sub1;
        int ink1;
        if (below != null && below.where() != null) {
            sub1 = whereText(below.where());
            ink1 = Theme.GOOD;
        } else {
            sub1 = lower.each() == null ? L.t("wynnlabs.tools.step_get", sel.tool().tier() - 1)
                    : L.t("wynnlabs.tools.step_buy", "~" + Prices.fmt(lower.each()));
            ink1 = Theme.INK_3;
        }
        cy = step(g, 1, icon(lower.name()), null, L.t("wynnlabs.tools.step_have", lower.name()), sub1, ink1, null, x, cy, w) + Theme.SP_2;
        // 2) the fragments
        int inBag = bag.getOrDefault(frag.name(), 0);
        int inBank = bank.getOrDefault(frag.name(), 0);
        int have = inBag + inBank;
        String sub2 = L.t("wynnlabs.tools.step_frag", ToolLadder.dungeonOf(frag.name()));
        String count = have + " / " + frag.count();
        int stepY = cy;
        cy = step(g, 2, CraftIcons.of(frag.name()), "diamond", frag.count() + "× " + frag.name(), sub2, Theme.INK_3,
                new String[] {count, have >= frag.count() ? "good" : "bad"}, x, cy, w) + Theme.SP_2;
        clipped(x, stepY, w, STEP_H, null, null, List.of(L.c("wynnlabs.tools.frag_tip", inBag, inBank)));
        // 3) an Item Upgrader
        Area a = upgrader();
        String sub3;
        if (a == null) {
            sub3 = L.t("wynnlabs.road.no_place");
        } else {
            double[] pos = CraftRoadData.position();
            double d = Compass.distance(a, pos);
            String dir = Compass.direction(a, pos);
            sub3 = Double.isNaN(d) ? L.t("wynnlabs.tools.upgrader_far")
                    : RoadmapScreen.dist((int) Math.round(d)) + (dir == null ? "" : " " + SearchCardRows.dirName(dir));
        }
        String go = L.t("wynnlabs.road.go_compass");
        int gw = Ui.iconButtonLabelWidth(tab.font, go);
        int gx = x + w - gw;
        int gy = cy + (STEP_H - Ui.BUTTON_H) / 2;
        step(g, 3, null, "sparkles", L.t("wynnlabs.tools.step_upgrader"), sub3, Theme.INK_3, null, x, cy, w - gw - Theme.SP_4);
        Ui.iconButtonLabel(g, tab.font, "compass", go, gx, gy, gw, mx, my);
        clipped(gx, gy, gw, Ui.BUTTON_H, () -> pin(a), null, null);
        return cy + STEP_H;
    }

    /**
     * One step: its number, a picture (an item, else an icon), a title and a line under it, and on the
     * right a count ({@code right[0]}, green when {@code right[1]} is "good").
     */
    private int step(GuiGraphics g, int n, ItemStack pic, String glyph, String title, String sub, int subInk, String[] right,
                     int x, int y, int w) {
        int nb = 12;
        int ny = y + (STEP_H - nb) / 2;
        Kit.box(g, x, ny, nb, nb, Theme.PLAQUE, Theme.LINE);
        String num = String.valueOf(n);
        Type.META.draw(g, tab.font, num, x + (nb - Type.META.width(tab.font, num)) / 2 + 1, Type.META.yIn(ny, nb), Theme.INK);
        int px = x + nb + Theme.SP_3;
        int py = y + (STEP_H - 18) / 2;
        if (pic != null && !pic.isEmpty()) {
            CraftIcons.slot(g, px, py, 18, pic);
        } else {
            Kit.box(g, px, py, 18, 18, Theme.PLAQUE, 0);
            Art.icon(g, glyph == null ? "package" : glyph, px + 5, py + 5, 8, "diamond".equals(glyph) ? Theme.ACCENT : Theme.INK_2);
        }
        int tx = px + 18 + Theme.SP_3;
        int end = x + w;
        if (right != null) {
            Type.LABEL.drawRight(g, tab.font, right[0], end, Type.LABEL.yIn(y, STEP_H), "good".equals(right[1]) ? Theme.GOOD : Theme.BAD);
            end -= Type.LABEL.width(tab.font, right[0]) + Theme.SP_4;
        }
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, title, end - tx), tx, y, Theme.INK);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, end - tx), tx, y + Type.LABEL.lineHeight, subInk);
        return y + STEP_H;
    }

    /** Another way on one line: its icon, its name, its cost, and the dungeon it is at. */
    private int wayLine(GuiGraphics g, ToolWays.Way way, boolean best, int tier, int x, int y, int w) {
        String icon = switch (way.kind()) {
            case MARKET -> "shopping-cart";
            case MERCHANT -> "coin";
            case UPGRADE -> "hammer";
            case DUNGEON -> "skull";
        };
        int rowH = Type.BODY.lineHeight;
        Art.icon(g, icon, x, Type.BODY.onCaps(y, 8), 8, best ? Theme.GOOD : Theme.INK_2);
        String cost;
        if (way.kind() == ToolWays.Kind.DUNGEON) {
            // two merchants can sit in one dungeon: the fragment tells them apart
            ToolWays.Part f = way.parts().get(0);
            cost = (way.emeralds() > 0 ? Prices.fmt(way.emeralds()) + " + " : "") + f.count() + "× " + f.name();
        } else {
            cost = way.kind() == ToolWays.Kind.MERCHANT && way.total() != null ? Prices.fmt(way.total()) : ToolLadder.summary(way);
        }
        String line = wayTitle(way, tier) + " · " + cost + (way.at() != null ? " · " + way.at() : "");
        int tx = x + 8 + Theme.SP_2;
        Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, line, x + w - tx), tx, y, best ? Theme.GOOD : Theme.INK);
        return y + rowH;
    }

    private static String wayTitle(ToolWays.Way w, int tier) {
        return switch (w.kind()) {
            case MARKET -> L.t("wynnlabs.box.ways_market");
            case MERCHANT -> L.t("wynnlabs.box.ways_merchant");
            case UPGRADE -> L.t("wynnlabs.box.ways_upgrade", tier - 1);
            case DUNGEON -> L.t("wynnlabs.box.ways_dungeon_short");
        };
    }

    // ---- data ----

    /** Reads the tools, the bag, the main hand and the bank again, at most every two seconds. */
    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - readAt < 2000) {
            return;
        }
        readAt = now;
        waysCache.clear();
        upgraderRead = false;
        if (previewTools != null) {
            all = previewTools;
        } else if (all.isEmpty()) {
            try {
                all = Wyn.gatherTools();
            } catch (Throwable t) {
                all = List.of();
            }
        }
        List<GatherTool> held;
        if (previewHeld != null) {
            held = previewHeld;
        } else {
            try {
                held = Wyn.heldTools();
            } catch (Throwable t) {
                held = List.of();
            }
        }
        ItemStack main = ItemStack.EMPTY;
        try {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                main = player.getMainHandItem();
            }
        } catch (Throwable ignored) {
            // no player: nothing in hand
        }
        Map<String, Integer> bagNow = new HashMap<>();
        try {
            for (ItemStack s : GameState.current().inventory()) {
                if (s != null && !s.isEmpty()) {
                    bagNow.merge(ItemJson.clean(s.getHoverName().getString()), s.getCount(), Integer::sum);
                }
            }
        } catch (Throwable ignored) {
            // a recording without a bag
        }
        Map<String, Integer> bankNow = new HashMap<>();
        try {
            for (String key : Vault.pageKeys()) {
                if (!key.startsWith("acct:") && !key.startsWith("char:" + Profile.id() + ":")) {
                    continue;
                }
                for (Vault.Stored st : Vault.page(key)) {
                    bankNow.merge(st.name(), st.count(), Integer::sum);
                }
            }
        } catch (Throwable ignored) {
            // no bank read yet
        }
        bag = bagNow;
        bank = bankNow;
        byName.clear();
        ownIcons.clear();
        for (GatherTool t : all) {
            byName.put(t.name(), t);
        }
        for (Kind k : Kind.values()) {
            List<ToolLadder.Tool> tools = new ArrayList<>();
            for (GatherTool t : all) {
                if (t.profession().equalsIgnoreCase(k.profession)) {
                    tools.add(new ToolLadder.Tool(t.name(), t.tier(), t.level()));
                }
            }
            Map<Integer, Where> owned = new HashMap<>();
            for (GatherTool t : held) {
                if (!t.profession().equalsIgnoreCase(k.profession)) {
                    continue;
                }
                boolean inHand = !main.isEmpty() && t.icon() != null && ItemStack.isSameItemSameComponents(main, t.icon());
                owned.merge(t.tier(), inHand ? Where.HAND : Where.BAG, ToolLadder::nearer);
                if (t.icon() != null && !t.icon().isEmpty()) {
                    ownIcons.put(t.name(), t.icon());
                }
            }
            for (ToolLadder.Tool t : tools) {
                for (String n : bankNow.keySet()) {
                    if (ToolLadder.isTool(n, k, t.tier())) {
                        owned.merge(t.tier(), Where.BANK, ToolLadder::nearer);
                    }
                }
            }
            ladders.put(k, ToolLadder.ladder(tools, tab.knownLevel(k.profession), owned));
        }
    }

    /** A tier's ways, cheapest first (worked out once per read); asks the market only for what is shown. */
    private List<ToolWays.Way> ways(List<Rung> ladder, Rung r) {
        return waysCache.computeIfAbsent(r.tool().name(), n -> {
            String lower = null;
            boolean ownLower = false;
            for (Rung o : ladder) {
                if (o.tool().tier() == r.tool().tier() - 1) {
                    lower = o.tool().name();
                    ownLower = o.where() != null;
                }
            }
            return ToolWays.ways(n, r.tool().tier(), lower, ownLower, CraftTools::price);
        });
    }

    private static Double price(String name) {
        Prices.Price p = Prices.of(name);
        Double v = p == null ? null : p.typical();
        if (v == null) {
            Prices.refreshSoon(name);
        }
        return v;
    }

    /** The nearest Item Upgrader, looked up once per read. */
    private Area upgrader() {
        if (upgraderRead) {
            return upgrader;
        }
        upgraderRead = true;
        upgrader = null;
        double[] pos = CraftRoadData.position();
        double bd = Double.MAX_VALUE;
        try {
            for (Entry e : ItemIndex.get().entries()) {
                if (e.kind() != io.github.anilken.wynnlabs.search.Kind.SERVICE) {
                    continue;
                }
                String t = e.type() == null ? "" : e.type();
                String n = e.name() == null ? "" : e.name().toLowerCase(Locale.ROOT);
                if (!t.contains("upgrader") && !n.contains("upgrader")) {
                    continue;
                }
                for (Area a : e.places()) {
                    double d = Compass.distance(a, pos);
                    if (upgrader == null || d < bd) {
                        upgrader = a;
                        bd = d;
                    }
                }
            }
        } catch (Throwable ignored) {
            // no index yet
        }
        return upgrader;
    }

    private ItemStack icon(String name) {
        ItemStack own = ownIcons.get(name);
        if (own != null) {
            return own;
        }
        GatherTool t = byName.get(name);
        return t != null && t.icon() != null ? t.icon() : CraftIcons.of(name);
    }

    private static String kindName(Kind k) {
        return L.t("wynnlabs.tools.kind." + k.name().toLowerCase(Locale.ROOT));
    }

    private static String whereText(Where w) {
        return switch (w) {
            case HAND -> L.t("wynnlabs.tools.yours_hand");
            case BAG -> L.t("wynnlabs.tools.yours_bag");
            case BANK -> L.t("wynnlabs.tools.yours_bank");
        };
    }

    private void pin(Area a) {
        String label = L.t("wynnlabs.tools.upgrader");
        if (a == null) {
            note = L.t("wynnlabs.road.no_place");
            return;
        }
        note = Waypoints.go(a.x(), a.y(), a.z(), label) ? L.t("wynnlabs.plan.compass_set", label) : L.t("wynnlabs.plan.compass_failed");
    }

    /** The tool's market window, which lists every way to get it. */
    private static void openMarket(String name, int level, ItemStack icon) {
        BoxMarket.Item item = new BoxMarket.Item(name, Slot.UNKNOWN, Tier.NORMAL, level, null, 0, true);
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new BoxScreen(new BoxScreen.Single(item, null, false), icon, mc.screen));
    }
}
