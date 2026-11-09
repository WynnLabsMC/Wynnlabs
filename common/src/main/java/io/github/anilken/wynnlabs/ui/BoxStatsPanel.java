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
        lines += way.at() != null || way.kind() == io.github.anilken.wynnlabs.crafting.ToolWays.Kind.MARKET ? 1 : 0;
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
        // under the title: the dungeon it is at, or the market's "Cheapest"
        String sub = way.at() != null ? way.at()
                : way.kind() == io.github.anilken.wynnlabs.crafting.ToolWays.Kind.MARKET
                ? (best ? L.t("wynnlabs.box.ways_best") : L.t("wynnlabs.box.ways_market_sub")) : null;
        if (sub != null) {
            if (way.at() != null) {
                Art.icon(g, "map-pin", tx + 2, cy + 2, 8, Theme.INK_3);
            }
            Type.META.draw(g, font, Type.META.fit(font, sub, end - tx - (way.at() != null ? 15 : 0)), tx + (way.at() != null ? 15 : 0),
                    cy + 2, best ? Theme.GOOD : Theme.INK_3);
            cy += PART_H;
        }
        // what it takes, each with its picture
        if (way.kind() != io.github.anilken.wynnlabs.crafting.ToolWays.Kind.MARKET) {
            for (var part : way.parts()) {
                net.minecraft.world.item.ItemStack pic = part.name().equals(lowerName) ? lowerIcon : CraftIcons.of(part.name());
                if (pic != null && !pic.isEmpty()) {
                    CraftIcons.slot(g, tx, cy, 12, pic);
                } else {
                    Art.icon(g, part.fragment() ? "diamond" : "package", tx + 2, cy + 2, 8, part.fragment() ? Theme.ACCENT : Theme.INK_3);
                }
                String each = part.fragment() ? "" : part.owned() ? L.t("wynnlabs.box.ways_yours")
                        : part.each() == null ? L.t("wynnlabs.box.ways_no_price")
                        : io.github.anilken.wynnlabs.market.Prices.fmt(part.each() * part.count());
                int ew = each.isEmpty() ? 0 : Type.META.width(font, each) + Theme.SP_3;
                Type.META.draw(g, font, Type.META.fit(font, part.count() + "× " + part.name(), end - (tx + 15) - ew), tx + 15, cy + 2, Theme.INK_2);
                if (!each.isEmpty()) {
                    Type.META.drawRight(g, font, each, end, cy + 2, part.owned() ? Theme.GOOD : Theme.INK_3);
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
