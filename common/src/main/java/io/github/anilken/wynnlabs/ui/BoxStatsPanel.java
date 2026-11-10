package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceText;
import io.github.anilken.wynnlabs.search.Sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The one-item window's stats: for gear, "Yours 72%" with your own rolls as the listings' chips
 * and every stat's lowest … highest on the right (so the listings' percentages read against
 * something); for an item without rolls (an ingredient, a material), what it needs and where it is
 * used, then its stats. Under them one line on where it comes from besides the market. Lines past
 * the column's height are left out.
 */
final class BoxStatsPanel {

    /** The easiest source, read once per item and then every half minute, never per frame. */
    private static String readFor;
    private static long readAt;
    private static Source from;

    /** The item's own words, read once per item. */
    private static String aboutFor;
    private static String aboutText;

    private BoxStatsPanel() {}

    /**
     * The item's description from its own lore, as one paragraph without the game's glyphs and
     * colours; it stops at the market's price block. Null when the lore says nothing.
     */
    static String about(String name, net.minecraft.world.item.ItemStack stack) {
        if (name != null && name.equals(aboutFor)) {
            return aboutText;
        }
        String text = null;
        var lore = stack == null ? null : stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            List<String> lines = new ArrayList<>();
            boolean price = false;
            for (Component c : lore.lines()) {
                String line = io.github.anilken.wynnlabs.game.ItemJson.clean(c.getString()).strip();
                // the market's price block (on the Trade Market it comes before the description)
                // runs to the next empty line; it and "Click to …" are skipped, not the end
                String head = line.replaceFirst("^[^\\p{L}]+", "");
                if (head.startsWith("Price") || head.startsWith("Fiyat")) {
                    price = true;
                    continue;
                }
                if (price) {
                    price = !line.isEmpty();
                    continue;
                }
                if (line.contains("Click")) {
                    continue;
                }
                line = line.replaceAll("\\[[^\\]\\p{L}\\p{N}]*\\]", "").strip();   // a tier bar ("[■■■■■□□]") says nothing as text
                if (line.codePoints().filter(Character::isLetter).count() >= 2) {
                    lines.add(line);
                }
            }
            text = lines.isEmpty() ? null : String.join("\n", lines);   // one line each, as the item shows them
        }
        aboutFor = name;
        aboutText = text;
        return text;
    }

    static void draw(BoxScreen s, GuiGraphics g, String name, int x, int y, int w, int h) {
        int toolTier = io.github.anilken.wynnlabs.crafting.ToolWays.tier(name);
        if (toolTier > 0) {
            toolWays(s, g, name, toolTier, x, y, w, y + h);   // a gathering tool is not crafted: the ways to get it
            return;
        }
        Entry e = ItemIndex.get().byName(name);
        BoxChips.use(name);
        int bottom = y + h;
        if (e == null) {
            if (s.single != null && s.single.ownRoll() != null && !s.own.isEmpty()) {
                // no item data yet: yours as chips all the same, without the ranges
                int cy = head(s, g, L.t("wynnlabs.box.own_seal", BoxScreen.pct(Math.round(s.single.ownRoll()))), null, x, y, w);
                for (io.github.anilken.wynnlabs.search.BoxMarket.StatRoll st : s.single.ownStats()) {
                    if (cy + BoxChips.H > bottom) {
                        return;
                    }
                    BoxChips.draw(g, s.font(), st, null, false, false, x, cy);
                    cy += BoxChips.H + Theme.SP_2;
                }
                return;
            }
            io.github.anilken.wynnlabs.market.Runes.Rune rune = io.github.anilken.wynnlabs.market.Runes.of(name);
            if (rune != null) {
                runeInfo(s, g, rune, x, y, w, bottom);   // the raids it opens, the keys it makes, where to get it
                return;
            }
            if (ward(name)) {
                wardInfo(s, g, name, x, y, w, bottom);   // what wards are for, where it drops, the set's price
                return;
            }
            String text = about(name, s.singleIcon);
            if (text != null) {
                // not in the item list (a ward, a rune…): what the item itself says
                int cy = head(s, g, L.t("wynnlabs.box.stats_about"), null, x, y, w);
                // line by line: a heading ("Effect on Weapons:") dark, a "- " line as a bullet, the rest grey
                for (String line : text.split("\n")) {
                    int left = (bottom - cy) / Type.BODY.lineHeight;
                    if (left < 1) {
                        break;
                    }
                    boolean bullet = line.startsWith("- ");
                    boolean heading = !bullet && line.endsWith(":");
                    if (heading && cy > y + Type.LABEL.lineHeight * 2) {
                        cy += Theme.SP_2;
                    }
                    int ix = bullet ? x + Theme.SP_4 : x;
                    if (bullet) {
                        Type.BODY.draw(g, s.font(), "•", x, cy, Theme.INK_3);
                        line = line.substring(2);
                    }
                    cy = Type.BODY.drawWrapped(g, s.font(), line, ix, cy, x + w - ix, left, heading || bullet ? Theme.INK : Theme.INK_2)
                            + (heading || bullet ? 0 : Theme.SP_1);
                }
                return;
            }
            Type.MUTED.draw(g, s.font(), Type.MUTED.fit(s.font(), L.t("wynnlabs.box.stats_unknown"), w), x, y, Theme.INK_3);
            return;
        }
        int cy = y;
        if (!s.rolled()) {
            List<String[]> needs = new ArrayList<>();
            needs.add(new String[] {L.t("wynnlabs.box.stats_level"), String.valueOf(e.level())});
            if (e.klass() != null && e.klass() != Klass.NONE) {
                needs.add(new String[] {L.t("wynnlabs.box.stats_class"), SearchResultsPanel.words(e.klass().name())});
            }
            for (Map.Entry<String, Integer> sk : e.skills().entrySet()) {
                if (sk.getValue() != null && sk.getValue() > 0) {
                    needs.add(new String[] {SearchResultsPanel.words(sk.getKey()), String.valueOf(sk.getValue())});
                }
            }
            if (e.kind() == Kind.INGREDIENT && !e.professions().isEmpty()) {
                List<String> prof = new ArrayList<>();
                e.professions().forEach(p -> prof.add(SearchResultsPanel.words(p)));
                needs.add(new String[] {L.t("wynnlabs.box.stats_used_in"), String.join(", ", prof)});
            }
            cy = head(s, g, L.t("wynnlabs.box.stats_needs"), null, x, cy, w);
            for (String[] r : needs) {
                if (cy + Type.BODY.lineHeight > bottom) {
                    return;
                }
                pair(s, g, r[0], r[1], Theme.INK, x, cy, w);
                cy += Type.BODY.lineHeight + Theme.SP_1;
            }
            cy += Theme.SP_3;
        }
        Map<String, Double> own = s.own;
        boolean yours = s.rolled() && s.single != null && s.single.ownRoll() != null;
        if (!e.stats().isEmpty() || !e.modifiers().isEmpty()) {
            if (cy + Type.LABEL.lineHeight > bottom) {
                return;
            }
            String title = yours ? L.t("wynnlabs.box.own_seal", BoxScreen.pct(Math.round(s.single.ownRoll()))) : L.t("wynnlabs.box.stats_title");
            cy = head(s, g, title, L.t("wynnlabs.box.stats_range"), x, cy, w);
        }
        for (Entry.Stat st : e.stats()) {
            Double p = own.get(st.key());
            int rowH = BoxChips.H + Theme.SP_2;
            if (cy + rowH > bottom) {
                return;
            }
            String range = st.min() == st.max() ? BoxChips.signed(st.min()) : BoxChips.signed(st.min()) + " … " + BoxChips.signed(st.max());
            if (p != null) {
                // yours as the listings' chip, the range on the right
                Type.META.drawRight(g, s.font(), range, x + w, Type.META.yIn(cy, BoxChips.H), Theme.INK_3);
                BoxChips.draw(g, s.font(), s.ownRoll(st.key()), null, false, false, x, cy);
            } else {
                // no roll of yours: the same pill with its value (a fixed stat) or its range
                if (st.min() != st.max()) {
                    Type.META.drawRight(g, s.font(), range, x + w, Type.META.yIn(cy, BoxChips.H), Theme.INK_3);
                }
                String v = st.min() == st.max() ? " " + range : "";
                BoxChips.plain(g, s.font(), BoxChips.name(st.key()) + v, st.max() < 0 && st.min() < 0 ? Theme.BAD : Theme.INK_2, x, cy);
            }
            cy += rowH;
        }
        for (Map.Entry<String, Integer> m : e.modifiers().entrySet()) {
            if (cy + Type.BODY.lineHeight > bottom) {
                return;
            }
            pair(s, g, SearchResultsPanel.statName(m.getKey()), BoxChips.signed(m.getValue()), m.getValue() < 0 ? Theme.BAD : Theme.GOOD, x, cy, w);
            cy += Type.BODY.lineHeight + Theme.SP_1;
        }
        source(s, g, e, x, cy, w, bottom);
    }

    // ---- runes ----

    /**
     * A raid rune's page: the raids it opens (a row opens that raid's pool in the Endgame tab), the
     * corrupted keys it makes at the Key Forge with what each costs and sells for, where to get it,
     * and its powder exchange.
     */
    private static void runeInfo(BoxScreen s, GuiGraphics g, io.github.anilken.wynnlabs.market.Runes.Rune rune, int x, int y, int w,
                                 int bottom) {
        var font = s.font();
        int mx = s.mouseX;
        int my = s.mouseY;
        int cy = y;
        int rowH = Math.max(16, Type.BODY.lineHeight) + 2;
        if (!rune.raids().isEmpty()) {
            cy = head(s, g, L.t("wynnlabs.box.rune.opens"), null, x, cy, w);
            for (io.github.anilken.wynnlabs.market.Runes.Raid r : rune.raids()) {
                if (cy + rowH > bottom) {
                    return;
                }
                boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + rowH;
                if (hover) {
                    Art.box(g, x - Theme.SP_1, cy, w + Theme.SP_1 * 2, rowH, 3, Theme.CARD_HOVER, 0);
                }
                Kit.box(g, x, cy + (rowH - 14) / 2, 14, 14, Theme.PLAQUE, 0);
                Art.icon(g, "skull", x + 3, cy + (rowH - 14) / 2 + 3, 8, Theme.INK_2);
                String go = L.t("wynnlabs.box.rune.pool") + " ›";
                String lv = "Lv " + r.level();
                int gw = Type.META.width(font, go);
                int lw = Type.META.width(font, lv);
                Type.META.drawRight(g, font, go, x + w, Type.META.yIn(cy, rowH), hover ? Theme.LINK : Theme.INK_3);
                Type.META.drawRight(g, font, lv, x + w - gw - Theme.SP_4, Type.META.yIn(cy, rowH), Theme.INK_3);
                Type.BODY.draw(g, font, Type.BODY.fit(font, r.raid(), w - 14 - Theme.SP_3 - gw - lw - Theme.SP_4 * 2), x + 14 + Theme.SP_3,
                        Type.BODY.yIn(cy, rowH), Theme.INK);
                String raid = r.raid();
                s.hit(x, cy, w, rowH, () -> {
                    EndgameTab.INSTANCE.showPool(true, raid);
                    net.minecraft.client.Minecraft.getInstance().setScreen(RoadmapScreen.endgame(s));
                });
                cy += rowH + Theme.SP_1;
            }
            cy += Theme.SP_4;
        }
        if (!rune.keys().isEmpty()) {
            if (cy + Type.LABEL.lineHeight > bottom) {
                return;
            }
            cy = head(s, g, L.t("wynnlabs.box.rune.keys"), null, x, cy, w);
            int cardH = Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_3 * 2;
            for (io.github.anilken.wynnlabs.market.Runes.KeyTrade t : rune.keys()) {
                if (cy + cardH > bottom) {
                    return;
                }
                var m = io.github.anilken.wynnlabs.market.Runes.margin(rune, t, BoxStatsPanel::typical);
                Double profit = m.profit();
                Kit.box(g, x, cy, w, cardH, Theme.CARD, profit == null ? Theme.LINE : profit >= 0 ? Theme.GOOD : Theme.BAD);
                int ix = x + Theme.SP_3;
                int iw = w - Theme.SP_3 * 2;
                String sells = m.sells() == null ? "?" : "~" + io.github.anilken.wynnlabs.market.Prices.fmt(m.sells());
                int sw = Type.META.width(font, sells);
                Type.META.drawRight(g, font, sells, ix + iw, cy + Theme.SP_3 + 1, Theme.PRICE);
                String key = t.key().replace("Corrupted ", "").replace(" Key", "");
                Type.BODY.draw(g, font, Type.BODY.fit(font, L.t("wynnlabs.box.rune.key_name", key), iw - sw - Theme.SP_3), ix, cy + Theme.SP_3,
                        Theme.INK);
                String cost = L.t("wynnlabs.box.rune.key_cost", t.runes(),
                        m.cost() == null ? "?" : io.github.anilken.wynnlabs.market.Prices.fmt(m.cost()));
                String verdict = profit == null ? "" : L.t(profit >= 0 ? "wynnlabs.box.rune.profit" : "wynnlabs.box.rune.loss",
                        io.github.anilken.wynnlabs.market.Prices.fmt(Math.abs(profit)));
                int vw = verdict.isEmpty() ? 0 : Type.META.width(font, verdict);
                int ly = cy + Theme.SP_3 + Type.BODY.lineHeight;
                Type.META.draw(g, font, Type.META.fit(font, cost, iw - vw - Theme.SP_3), ix, ly, Theme.INK_3);
                if (!verdict.isEmpty()) {
                    Type.META.drawRight(g, font, verdict, ix + iw, ly, profit >= 0 ? Theme.GOOD : Theme.BAD);
                }
                cy += cardH + Theme.SP_2;
            }
            cy += Theme.SP_3;
        }
        // where to get it, and what else it does
        List<String> lines = new ArrayList<>();
        lines.add(L.t("wynnlabs.box.rune.events", rune.eventLow(), rune.eventHigh()));
        if (rune.lootrun()) {
            lines.add(L.t(rune.name().startsWith("Ek") ? "wynnlabs.box.rune.lootrun_fruma" : "wynnlabs.box.rune.lootrun"));
        }
        if (rune.powderTier() > 0) {
            lines.add(L.t("wynnlabs.box.rune.powder", rune.powderTier(), rune.name()));
        }
        if (!"Tol Rune".equals(rune.name()) && !"Ek Rune".equals(rune.name())) {
            lines.add(L.t("wynnlabs.box.rune.altar"));
        }
        if (cy + Type.LABEL.lineHeight + Type.META.lineHeight > bottom) {
            return;
        }
        cy = head(s, g, L.t("wynnlabs.box.rune.get"), null, x, cy, w);
        for (String line : lines) {
            if (cy + Type.META.lineHeight > bottom) {
                return;
            }
            Type.META.draw(g, font, "•", x, cy, Theme.INK_3);
            cy = Type.META.drawWrapped(g, font, line, x + Theme.SP_4, cy, w - Theme.SP_4, Math.max(1, (bottom - cy) / Type.META.lineHeight),
                    Theme.INK_2) + Theme.SP_1;
        }
    }

    /** A market price by name, or null; an unknown one is asked for once. */
    private static Double typical(String name) {
        io.github.anilken.wynnlabs.market.Prices.Price p = io.github.anilken.wynnlabs.market.Prices.of(name);
        Double v = p == null ? null : p.typical();
        if (v == null) {
            io.github.anilken.wynnlabs.market.Prices.refreshSoon(name);
        }
        return v;
    }

    // ---- wards ----

    /** The seven wards: raid ones first, then lootrun ones, each with its colour. */
    private static final String[] WARDS = io.github.anilken.wynnlabs.search.InfoItems.WARDS.toArray(new String[0]);
    private static final int[] WARD_INK = {0xFFC9A227, 0xFFB8322A, 0xFF2F6FBF, 0xFF6A4BB0, 0xFF4E8A2A, 0xFFC8661E, 0xFFC0507E};

    /** Whether a name is one of the seven wards ("Blue Ward"). */
    static boolean ward(String name) {
        return io.github.anilken.wynnlabs.search.InfoItems.ward(name);
    }

    /** A ward's picture when it was seen this session, else null. */
    static net.minecraft.world.item.ItemStack wardIcon(String name) {
        return WARD_ICONS.get(name);
    }

    /**
     * A ward's page: what wards are for (ascending a Mythic), where this one drops in this week's
     * pools (a row opens that pool in the Endgame tab), and the seven wards' market prices with
     * their total.
     */
    private static void wardInfo(BoxScreen s, GuiGraphics g, String name, int x, int y, int w, int bottom) {
        var font = s.font();
        // where it drops this week, the full set, then what wards are for in the room left
        int cy = head(s, g, L.t("wynnlabs.box.ward.drops_title"), null, x, y, w);
        List<io.github.anilken.wynnlabs.model.PoolRegion> where = io.github.anilken.wynnlabs.endgame.WynnventoryPools.holding(name);
        if (where.isEmpty()) {
            Type.MUTED.draw(g, font, Type.MUTED.fit(font, L.t("wynnlabs.box.ward.drops_none"), w), x, cy, Theme.INK_3);
            cy += Type.MUTED.lineHeight;
        }
        int rowH = Math.max(16, Type.BODY.lineHeight) + 2;
        for (io.github.anilken.wynnlabs.model.PoolRegion r : where) {
            if (cy + rowH > bottom) {
                return;
            }
            boolean raid = "RAID".equalsIgnoreCase(r.type());
            int mx = s.mouseX;
            int my = s.mouseY;
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + rowH;
            if (hover) {
                Art.box(g, x - Theme.SP_1, cy, w + Theme.SP_1 * 2, rowH, 3, Theme.CARD_HOVER, 0);
            }
            Kit.box(g, x, cy + (rowH - 14) / 2, 14, 14, Theme.PLAQUE, 0);
            Art.icon(g, raid ? "skull" : "route", x + 3, cy + (rowH - 14) / 2 + 3, 8, Theme.INK_2);
            String go = L.t(raid ? "wynnlabs.box.ward.raid" : "wynnlabs.box.ward.lootrun") + " ›";
            int gw = Type.META.width(font, go);
            Type.META.drawRight(g, font, go, x + w, Type.META.yIn(cy, rowH), hover ? Theme.LINK : Theme.INK_3);
            Type.BODY.draw(g, font, Type.BODY.fit(font, r.name(), w - 14 - Theme.SP_3 - gw - Theme.SP_3), x + 14 + Theme.SP_3,
                    Type.BODY.yIn(cy, rowH), Theme.INK);
            String region = r.name();
            s.hit(x, cy, w, rowH, () -> {
                EndgameTab.INSTANCE.showPool(raid, region);
                net.minecraft.client.Minecraft.getInstance().setScreen(RoadmapScreen.endgame(s));
            });
            cy += rowH + Theme.SP_1;
        }
        cy += Theme.SP_5;
        if (cy + Type.LABEL.lineHeight > bottom) {
            return;
        }
        // the full set: each ward's picture with its price under it, four to a row
        cy = head(s, g, L.t("wynnlabs.box.ward.set_title"), null, x, cy, w);
        seeWards(s);
        long total = 0;
        boolean unknown = false;
        int cols = 4;
        int tw = (w - (cols - 1) * Theme.SP_2) / cols;
        int th = 18 + Type.META.lineHeight + Theme.SP_1 + 4;
        for (int i = 0; i < WARDS.length; i++) {
            String ward = WARDS[i] + " Ward";
            io.github.anilken.wynnlabs.market.Prices.Price p = io.github.anilken.wynnlabs.market.Prices.of(ward);
            Double v = p == null ? null : p.typical();
            if (v == null) {
                unknown = true;
                io.github.anilken.wynnlabs.market.Prices.refreshSoon(ward);
            } else {
                total += Math.round(v);
            }
            int tx = x + (i % cols) * (tw + Theme.SP_2);
            int ty = cy + (i / cols) * (th + Theme.SP_2);
            if (ty + th > bottom) {
                return;
            }
            boolean me = ward.equals(name);
            Kit.box(g, tx, ty, tw, th, me ? Theme.CARD_HOVER : Theme.CARD, me ? Theme.INK_2 : Theme.LINE);
            net.minecraft.world.item.ItemStack pic = WARD_ICONS.get(ward);
            int ix = tx + (tw - 16) / 2;
            io.github.anilken.wynnlabs.model.PoolItem pooled = pic == null ? io.github.anilken.wynnlabs.endgame.WynnventoryPools.item(ward) : null;
            if (pic != null) {
                g.renderItem(pic, ix, ty + 2);
            } else if (pooled != null) {
                // not seen yet: its picture as the Endgame pools draw it
                io.github.anilken.wynnlabs.endgame.ItemIcons.draw(g, pooled, null, ix, ty + 2, 16, false);
            } else {
                // not seen yet this session: its colour instead of its picture
                g.fill(ix + 3, ty + 5, ix + 13, ty + 15, WARD_INK[i]);
            }
            String price = v == null ? "?" : io.github.anilken.wynnlabs.market.Prices.slot(v);
            Type.META.draw(g, font, Type.META.fit(font, price, tw - 4), tx + (tw - Math.min(tw - 4, Type.META.width(font, price))) / 2,
                    ty + 18 + Theme.SP_1, v == null ? Theme.INK_3 : Theme.PRICE);
            int mx = s.mouseX;
            int my = s.mouseY;
            if (mx >= tx && mx < tx + tw && my >= ty && my < ty + th) {
                s.tip(List.of(Component.literal(ward + (v == null ? "" : " · " + io.github.anilken.wynnlabs.market.Prices.fmt(v)))));
            }
        }
        cy += ((WARDS.length + cols - 1) / cols) * (th + Theme.SP_2) + Theme.SP_1;
        if (cy + Type.LABEL.lineHeight <= bottom) {
            String sum = total == 0 && unknown ? "?" : io.github.anilken.wynnlabs.market.Prices.fmt(total) + (unknown ? " +?" : "");
            Type.LABEL.draw(g, font, L.t("wynnlabs.box.ward.set_total"), x, cy, Theme.INK);
            Type.LABEL.drawRight(g, font, sum, x + w, cy, Theme.PRICE);
            cy += Type.LABEL.lineHeight;
        }
        cy += Theme.SP_5;
        if (cy + Type.LABEL.lineHeight + Type.META.lineHeight * 2 > bottom) {
            return;
        }
        cy = head(s, g, L.t("wynnlabs.box.ward.use_title"), null, x, cy, w);
        Type.META.drawWrapped(g, font, L.t("wynnlabs.box.ward.use"), x, cy, w, Math.max(1, (bottom - cy) / Type.META.lineHeight), Theme.INK_2);
    }

    /** Ward pictures seen this session, by name: the item list has no wards, so they come from the game's own stacks. */
    private static final Map<String, net.minecraft.world.item.ItemStack> WARD_ICONS = new java.util.HashMap<>();

    /** Keeps the pictures of the wards in view: the one this window is on, and any in the open menu or the bag. */
    private static void seeWards(BoxScreen s) {
        if (WARD_ICONS.size() == WARDS.length) {
            return;
        }
        List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
        if (s.singleIcon != null) {
            stacks.add(s.singleIcon);
        }
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            player.containerMenu.slots.forEach(sl -> stacks.add(sl.getItem()));
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                stacks.add(player.getInventory().getItem(i));
            }
        }
        for (net.minecraft.world.item.ItemStack st : stacks) {
            if (st == null || st.isEmpty()) {
                continue;
            }
            String n = io.github.anilken.wynnlabs.game.ItemJson.clean(st.getHoverName().getString()).replaceAll("[^\\p{L} ]", "").strip();
            if (ward(n) && !WARD_ICONS.containsKey(n)) {
                WARD_ICONS.put(n, st.copyWithCount(1));
            }
        }
    }

    private static String lowerFor;
    private static String lowerName;
    private static net.minecraft.world.item.ItemStack lowerIcon;
    private static boolean lowerOwned;
    private static long lowerAt;

    /**
     * A gathering tool's ways to get it, cheapest first: the Trade Market, the Tool Merchant,
     * upgrading the tier below (free of its price when it is in the bag) and a Dungeon Merchant,
     * each with its cost at the market's prices.
     */
    private static void toolWays(BoxScreen s, GuiGraphics g, String name, int tier, int x, int y, int w, int bottom) {
        long now = System.currentTimeMillis();
        if (!name.equals(lowerFor) || now - lowerAt > 2000) {
            lowerFor = name;
            lowerAt = now;
            lowerName = null;
            lowerOwned = false;
            try {
                // the same kind of tool ("Pickaxe") one tier below
                java.util.regex.Matcher km = java.util.regex.Pattern.compile(" (Pickaxe|Axe|Scythe|Rod) T[0-9]+$").matcher(name);
                String kind = km.find() ? " " + km.group(1) + " " : null;
                for (var t : io.github.anilken.wynnlabs.game.Wyn.gatherTools()) {
                    if (kind != null && t.tier() == tier - 1 && (t.name() + " ").contains(kind)) {
                        lowerName = t.name();
                        lowerIcon = t.icon();
                    }
                }
                for (var t : io.github.anilken.wynnlabs.game.Wyn.heldTools()) {
                    lowerOwned |= t.name().equals(lowerName);
                }
            } catch (Throwable ignored) {
                // no tool data: the upgrade is left out
            }
        }
        var ways = io.github.anilken.wynnlabs.crafting.ToolWays.ways(name, tier, lowerName, lowerOwned, n -> {
            io.github.anilken.wynnlabs.market.Prices.Price p = io.github.anilken.wynnlabs.market.Prices.of(n);
            Double v = p == null ? null : p.typical();
            if (v == null) {
                io.github.anilken.wynnlabs.market.Prices.refreshSoon(n);   // a tool's price (fragments are never asked)
            }
            return v;
        });
        int cy = head(s, g, L.t("wynnlabs.box.ways"), null, x, y, w);
        boolean first = true;
        for (var way : ways) {
            boolean best = first && way.total() != null && way.fragments() == 0 && ways.size() > 1;
            first = false;
            int h = wayHeight(way);
            if (cy + h > bottom) {
                return;
            }
            cy = wayCard(s, g, way, tier, best, x, cy, w, h) + Theme.SP_3;
        }
    }

    private static final int WAY_PAD = Theme.SP_3;
    private static final int WAY_BADGE = 18;
    private static final int PART_H = 13;
    private static final int CHIP_H = 11;

    /** A way's card height: its title line, a line per part, and the fragments chip when it needs fragments. */
    private static int wayHeight(io.github.anilken.wynnlabs.crafting.ToolWays.Way way) {
        int lines = way.kind() == io.github.anilken.wynnlabs.crafting.ToolWays.Kind.MARKET ? 0 : way.parts().size();
        lines += 1;                                     // the place line (or the market's)
        int body = Math.max(WAY_BADGE, Type.LABEL.lineHeight + (lines > 0 ? Theme.SP_1 + lines * PART_H : 0)
                + (way.fragments() > 0 ? Theme.SP_2 + CHIP_H + 2 : 0));
        return WAY_PAD * 2 + body;
    }

    /**
     * One way on its own card: a badge with its icon (the market's cart, the merchant's coin, the
     * upgrader's hammer, the dungeon's skull), its title (the cheapest one green with a tag), its
     * cost on a chip at the right, then each thing it takes with its picture, and a purple chip
     * with the fragments it needs from dungeon runs. Returns the y under the card.
     */
    private static int wayCard(BoxScreen s, GuiGraphics g, io.github.anilken.wynnlabs.crafting.ToolWays.Way way, int tier,
                               boolean best, int x, int y, int w, int h) {
        var font = s.font();
        Kit.box(g, x, y, w, h, Theme.CARD, best ? Theme.GOOD : Theme.LINE);
        int bx = x + WAY_PAD;
        int by = y + WAY_PAD;
        Kit.box(g, bx, by, WAY_BADGE, WAY_BADGE, Theme.PLAQUE, 0);
        String icon = switch (way.kind()) {
            case MARKET -> "shopping-cart";
            case MERCHANT -> "coin";
            case UPGRADE -> "hammer";
            case DUNGEON -> "skull";
        };
        Art.icon(g, icon, bx + (WAY_BADGE - 8) / 2, by + (WAY_BADGE - 8) / 2, 8, best ? Theme.GOOD : Theme.INK_2);
        int tx = bx + WAY_BADGE + Theme.SP_3;
        int right = x + w - WAY_PAD;
        // the cost chip on the right
        String money = way.total() == null ? L.t("wynnlabs.box.ways_no_price")
                : way.total() > 0 || way.fragments() == 0 ? io.github.anilken.wynnlabs.market.Prices.fmt(way.total()) : null;
        if (money != null) {
            int cw = Type.META.width(font, money) + Theme.SP_3 * 2;
            int cx = right - cw;
            Kit.box(g, cx, by, cw, CHIP_H + 2, best ? 0xFFD6E5C0 : Theme.PLAQUE, 0);
            Type.META.draw(g, font, money, cx + Theme.SP_3, by + 2, way.total() == null ? Theme.INK_3 : best ? Theme.GOOD : Theme.PRICE);
            right = cx - Theme.SP_3;
        }
        // the title, and the cheapest one's tag
        String title = switch (way.kind()) {
            case MARKET -> L.t("wynnlabs.box.ways_market");
            case MERCHANT -> L.t("wynnlabs.box.ways_merchant");
            case UPGRADE -> L.t("wynnlabs.box.ways_upgrade", tier - 1);
            case DUNGEON -> L.t("wynnlabs.box.ways_dungeon_short");
        };
        Type.LABEL.draw(g, font, Type.LABEL.fit(font, title, right - tx), tx, by + 1, best ? Theme.GOOD : Theme.INK);
        int cy = by + Type.LABEL.lineHeight + Theme.SP_1;
        int end = x + w - WAY_PAD;
        // under the title: where to go (the dungeon, the Item Upgrader, the Tool Merchant) with a
        // compass to it, or the market's "Cheapest"
        boolean market = way.kind() == io.github.anilken.wynnlabs.crafting.ToolWays.Kind.MARKET;
        String sub = market ? (best ? L.t("wynnlabs.box.ways_best") : L.t("wynnlabs.box.ways_market_sub"))
                : way.at() != null ? way.at()
                : L.t(way.kind() == io.github.anilken.wynnlabs.crafting.ToolWays.Kind.UPGRADE ? "wynnlabs.box.ways_upgrader" : "wynnlabs.box.ways_merchant");
        io.github.anilken.wynnlabs.search.Area place = market ? null : wayPlace(way);
        int subEnd = end;
        if (place != null) {
            // the compass to the place, at the line's end
            int bw = Ui.CHIP_H + 2;
            int bxx = end - bw;
            int byy = cy + (PART_H - Ui.CHIP_H) / 2;
            Ui.iconButton(g, "compass", bxx, byy, false, s.mouseX, s.mouseY);
            String label = sub;
            s.hit(bxx, byy, bw, Ui.CHIP_H, () -> io.github.anilken.wynnlabs.quests.Waypoints.go(place.x(), place.y(), place.z(), label));
            if (s.mouseX >= bxx && s.mouseX < bxx + bw && s.mouseY >= byy && s.mouseY < byy + Ui.CHIP_H) {
                s.tip(List.of(Component.literal(L.t("wynnlabs.box.ways_compass", label))));
            }
            double d = io.github.anilken.wynnlabs.search.Compass.distance(place, SearchTab.position());
            if (!Double.isNaN(d)) {
                String dist = RoadmapScreen.dist((int) Math.round(d));
                Type.META.drawRight(g, font, dist, bxx - Theme.SP_2, cy + 2, Theme.INK_3);
                subEnd = bxx - Theme.SP_2 - Type.META.width(font, dist) - Theme.SP_2;
            } else {
                subEnd = bxx - Theme.SP_2;
            }
        }
        if (!market) {
            Art.icon(g, "map-pin", tx + 2, cy + 2, 8, Theme.INK_3);
        }
        Type.META.draw(g, font, Type.META.fit(font, sub, subEnd - tx - (market ? 0 : 15)), tx + (market ? 0 : 15),
                cy + 2, best ? Theme.GOOD : Theme.INK_3);
        cy += PART_H;
        // what it takes, each with its picture
        if (way.kind() != io.github.anilken.wynnlabs.crafting.ToolWays.Kind.MARKET) {
            for (var part : way.parts()) {
                net.minecraft.world.item.ItemStack pic = part.name().equals(lowerName) ? lowerIcon : CraftIcons.of(part.name());
                if (pic != null && !pic.isEmpty()) {
                    CraftIcons.slot(g, tx, cy, 12, pic);
                } else {
                    Art.icon(g, part.fragment() ? "diamond" : "package", tx + 2, cy + 2, 8, part.fragment() ? Theme.ACCENT : Theme.INK_3);
                }
                int[] have = owned(part.name());
                String each = have[0] + have[1] > 0
                        ? L.t(have[1] == 0 ? "wynnlabs.box.ways_have_bag" : have[0] == 0 ? "wynnlabs.box.ways_have_bank" : "wynnlabs.box.ways_have_both",
                                have[0] + have[1])
                        : part.fragment() ? "" : part.owned() ? L.t("wynnlabs.box.ways_yours")
                        : part.each() == null ? L.t("wynnlabs.box.ways_no_price")
                        : io.github.anilken.wynnlabs.market.Prices.fmt(part.each() * part.count());
                boolean got = have[0] + have[1] >= part.count() || part.owned();
                int ew = each.isEmpty() ? 0 : Type.META.width(font, each) + Theme.SP_3;
                Type.META.draw(g, font, Type.META.fit(font, part.count() + "× " + part.name(), end - (tx + 15) - ew), tx + 15, cy + 2, Theme.INK_2);
                if (!each.isEmpty()) {
                    Type.META.drawRight(g, font, each, end, cy + 2, got ? Theme.GOOD : have[0] + have[1] > 0 ? Theme.WARN : Theme.INK_3);
                }
                cy += PART_H;
            }
        }
        if (way.fragments() > 0) {
            // the fragments it needs, on a purple chip: they drop from dungeon runs
            String chip = L.t("wynnlabs.box.ways_runs");
            int cw = Math.min(end - tx, Type.META.width(font, chip) + Theme.SP_3 * 2);
            cy += Theme.SP_2;
            Kit.box(g, tx, cy, cw, CHIP_H + 2, 0xFFE3DAF0, Theme.ACCENT);
            Type.META.draw(g, font, Type.META.fit(font, chip, cw - Theme.SP_3 * 2), tx + Theme.SP_3, cy + 2, Theme.ACCENT);
        }
        return y + h;
    }

    /** Where a way is done: its dungeon, the nearest Item Upgrader or Tool Merchant; null when the item list knows none. */
    static io.github.anilken.wynnlabs.search.Area wayPlace(io.github.anilken.wynnlabs.crafting.ToolWays.Way way) {
        double[] pos = SearchTab.position();
        io.github.anilken.wynnlabs.search.Area best = null;
        double bd = Double.MAX_VALUE;
        if (way.kind() == io.github.anilken.wynnlabs.crafting.ToolWays.Kind.UPGRADE) {
            // the shipped Item Upgraders (the item index has none)
            for (io.github.anilken.wynnlabs.search.Area a : io.github.anilken.wynnlabs.crafting.Upgraders.all()) {
                double d = io.github.anilken.wynnlabs.search.Compass.distance(a, pos);
                if (best == null || d < bd) {
                    best = a;
                    bd = d;
                }
            }
        }
        for (Entry e : ItemIndex.get().entries()) {
            boolean match = switch (way.kind()) {
                case DUNGEON -> e.kind() == Kind.CONTENT && e.name().equalsIgnoreCase(way.at());
                case UPGRADE -> e.kind() == Kind.SERVICE && e.name().toLowerCase(java.util.Locale.ROOT).contains("upgrader");
                case MERCHANT -> e.kind() == Kind.SERVICE && "merchant:tool".equals(e.type());
                default -> false;
            };
            if (!match) {
                continue;
            }
            for (io.github.anilken.wynnlabs.search.Area a : e.places()) {
                double d = io.github.anilken.wynnlabs.search.Compass.distance(a, pos);
                if (best == null || d < bd) {
                    best = a;
                    bd = d;
                }
            }
        }
        return best;
    }

    private static String ownedFor;
    private static int[] ownedCount = {0, 0};
    private static long ownedAt;

    /**
     * {in the bag, in the bank} of an item by name: the bag now, the bank as its pages were last
     * opened (the account bank and this character's), read again every two seconds.
     */
    static int[] owned(String name) {
        long now = System.currentTimeMillis();
        if (name.equals(ownedFor) && now - ownedAt < 2000) {
            return ownedCount;
        }
        int bag = 0;
        int bank = 0;
        try {
            for (net.minecraft.world.item.ItemStack st : io.github.anilken.wynnlabs.game.GameState.current().inventory()) {
                if (st != null && !st.isEmpty() && name.equalsIgnoreCase(plainName(st))) {
                    bag += st.getCount();
                }
            }
            for (String key : io.github.anilken.wynnlabs.market.Vault.pageKeys()) {
                if (!key.startsWith("acct:") && !key.startsWith("char:" + io.github.anilken.wynnlabs.core.Profile.id() + ":")) {
                    continue;
                }
                for (io.github.anilken.wynnlabs.market.Vault.Stored st : io.github.anilken.wynnlabs.market.Vault.page(key)) {
                    if (name.equalsIgnoreCase(st.name())) {
                        bank += Math.max(1, st.count());
                    }
                }
            }
        } catch (Throwable t) {
            // no game state yet: nothing owned
        }
        ownedFor = name;
        ownedAt = now;
        ownedCount = new int[] {bag, bank};
        return ownedCount;
    }

    private static String plainName(net.minecraft.world.item.ItemStack st) {
        return io.github.anilken.wynnlabs.game.ItemJson.clean(st.getHoverName().getString()).replaceAll("[^\\p{L}\\p{N}' -]", "").strip();
    }

    /** A section's LABEL title, a META note on the right, a hairline; returns the first line's y. */
    private static int head(BoxScreen s, GuiGraphics g, String title, String note, int x, int y, int w) {
        Type.LABEL.draw(g, s.font(), title, x, y, Theme.INK);
        if (note != null) {
            Type.META.drawRight(g, s.font(), Type.META.fit(s.font(), note, w - Type.LABEL.width(s.font(), title) - Theme.SP_3), x + w, y + 1,
                    Theme.INK_3);
        }
        int hy = y + Type.LABEL.lineHeight + Theme.SP_1;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    private static void pair(BoxScreen s, GuiGraphics g, String name, String value, int ink, int x, int y, int w) {
        int vw = Type.BODY.width(s.font(), value);
        Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), name, w - vw - Theme.SP_3), x, y, Theme.INK_2);
        Type.BODY.drawRight(g, s.font(), value, x + w, y, ink);
    }

    /**
     * The easiest way to get the item besides the market, as one line under the stats: its badge,
     * a short text and how far away it is, from {@code cy} down to {@code bottom}.
     */
    private static void source(BoxScreen s, GuiGraphics g, Entry e, int x, int cy, int w, int bottom) {
        long now = System.currentTimeMillis();
        if (!e.name().equals(readFor) || now - readAt > 30_000) {
            readFor = e.name();
            readAt = now;
            from = null;
            for (Source src : Sources.of(e, ItemIndex.get().places(), Sources.Owned.NONE, null, SearchTab.position())) {
                if (src.type() != Source.Type.MARKET) {
                    from = src;                 // the market is this whole window
                    break;
                }
            }
        }
        Source src = from;
        cy += Theme.SP_3;
        if (src == null || cy + 1 + Theme.SP_3 + KindBadge.MIN > bottom) {
            return;
        }
        Kit.hair(g, x, cy, w, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        int bs = Kit.kindBadge(g, x, Type.BODY.onCaps(cy, KindBadge.MIN), KindBadge.MIN, KindBadge.of(src));
        double[] pos = SearchTab.position();
        Area a = src.main();
        double d = Compass.distance(a, pos);
        String dist = a == null || Double.isNaN(d) ? "" : RoadmapScreen.dist((int) Math.round(d));
        int dw = dist.isEmpty() ? 0 : Type.META.width(s.font(), dist) + Theme.SP_3;
        int tx = x + bs + Theme.SP_3;
        Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), SourceText.row(src, null), x + w - dw - tx), tx, cy, Theme.INK);
        if (!dist.isEmpty()) {
            Type.META.drawRight(g, s.font(), dist, x + w, cy + 1, Theme.INK_3);
        }
    }
}
