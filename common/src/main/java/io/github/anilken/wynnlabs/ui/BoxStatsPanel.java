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
            text = lines.isEmpty() ? null : String.join(" ", lines);
        }
        aboutFor = name;
        aboutText = text;
        return text;
    }

    static void draw(BoxScreen s, GuiGraphics g, String name, int x, int y, int w, int h) {
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
            if (ward(name)) {
                wardInfo(s, g, name, x, y, w, bottom);   // what wards are for, where it drops, the set's price
                return;
            }
            String text = about(name, s.singleIcon);
            if (text != null) {
                // not in the item list (a ward, a rune…): what the item itself says
                int cy = head(s, g, L.t("wynnlabs.box.stats_about"), null, x, y, w);
                Type.BODY.drawWrapped(g, s.font(), text, x, cy, w, Math.max(1, (bottom - cy) / Type.BODY.lineHeight), Theme.INK_2);
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

    // ---- wards ----

    /** The seven wards: raid ones first, then lootrun ones, each with its colour. */
    private static final String[] WARDS = {"Yellow", "Red", "Blue", "Purple", "Green", "Orange", "Pink"};
    private static final int[] WARD_INK = {0xFFC9A227, 0xFFB8322A, 0xFF2F6FBF, 0xFF6A4BB0, 0xFF4E8A2A, 0xFFC8661E, 0xFFC0507E};

    /** Whether a name is one of the seven wards ("Blue Ward"). */
    static boolean ward(String name) {
        if (name == null || !name.endsWith(" Ward")) {
            return false;
        }
        String colour = name.substring(0, name.length() - 5);
        for (String w : WARDS) {
            if (w.equals(colour)) {
                return true;
            }
        }
        return false;
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
