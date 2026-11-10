package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.ToolLadder;
import io.github.anilken.wynnlabs.crafting.ToolWays;
import io.github.anilken.wynnlabs.endgame.WynnventoryPools;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Runes;
import io.github.anilken.wynnlabs.model.GatherTool;
import io.github.anilken.wynnlabs.model.PoolRegion;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.InfoItems;
import io.github.anilken.wynnlabs.search.ItemWiki;
import io.github.anilken.wynnlabs.search.Source;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The search card of an item the index does not list but the mod knows by name ({@link CardView};
 * {@link InfoItems}), with the facts the box window shows for it and its words. A gathering tool:
 * every way to get it as a row (the Trade Market, the Tool Merchant, upgrading the tier below at
 * the nearest Item Upgrader, a Dungeon Merchant), what the upgrade takes with what the player
 * holds of it, the profession level it needs and the tool it upgrades into. A ward: where it drops
 * this week, what wards are for, and the seven wards' prices. A raid rune: where to get it, the
 * raids it opens and the keys it makes at the Key Forge. Read-only: what it changes is a compass
 * pin and which tab or card is open.
 */
final class InfoCard extends MenuPaint implements CardView {

    private final SearchCardPanel card;
    private final SearchTab tab;
    private final String name;
    private final InfoItems.Kind kind;
    private int[] have = new int[2];
    // a tool: itself, the tier below and above in the game's list (null: not known), its ways and their rows
    private GatherTool tool;
    private GatherTool lower;
    private GatherTool next;
    private List<ToolWays.Way> ways = List.of();
    private List<Source> sources = List.of();
    private ToolWays.Way upgrade;
    private String nextCost;
    /** What the player holds of each thing the upgrade takes: {bag, bank} by name. */
    private Map<String, int[]> partsHeld = Map.of();

    private InfoCard(SearchCardPanel card, String name, InfoItems.Kind kind) {
        super(card.tab.menu);
        this.card = card;
        this.tab = card.tab;
        this.name = name;
        this.kind = kind;
    }

    /** The card of an item's key, or null when the name is none the mod knows. */
    static InfoCard of(SearchCardPanel card, String key) {
        String name = InfoItems.name(key);
        InfoItems.Kind kind = InfoItems.of(name);
        return kind == null ? null : new InfoCard(card, name, kind);
    }

    // ---- the game's tools ----

    /** Every gathering tool the game has (the Crafting tab's preview list in a preview); empty without tool data. */
    private static List<GatherTool> tools() {
        List<GatherTool> p = CraftTools.previewTools;
        if (p != null) {
            return p;
        }
        try {
            return Wyn.gatherTools();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<GatherTool> held() {
        List<GatherTool> p = CraftTools.previewHeld;
        if (p != null) {
            return p;
        }
        try {
            return Wyn.heldTools();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** The tool of the same kind as {@code name} at {@code tier}, or null. */
    private static GatherTool sibling(String name, int tier) {
        ToolLadder.Kind k = InfoItems.toolKind(name);
        if (k == null || tier < 1) {
            return null;
        }
        for (GatherTool t : tools()) {
            if (ToolLadder.isTool(t.name(), k, tier)) {
                return t;
            }
        }
        return null;
    }

    private static boolean owns(String tool) {
        for (GatherTool t : held()) {
            if (t.name().equals(tool)) {
                return true;
            }
        }
        int[] h = BoxStatsPanel.owned(tool);
        return h[0] + h[1] > 0;
    }

    /** A market price already kept, or null. */
    private static Double price(String item) {
        Prices.Price p = Prices.of(item);
        return p == null ? null : p.typical();
    }

    /** The way that upgrades {@code from} into {@code to}, or null when the data has none. */
    private static ToolWays.Way upgrade(String to, String from, boolean ownFrom) {
        for (ToolWays.Way w : ToolWays.ways(to, ToolWays.tier(to), from, ownFrom, InfoCard::price)) {
            if (w.kind() == ToolWays.Kind.UPGRADE) {
                return w;
            }
        }
        return null;
    }

    // ---- the list's rows ----

    /** The item's row in the list; {@code right}: its right column (a way's cost), or null. */
    static SearchRow.InfoRow row(String name, ItemStack stack, String right) {
        InfoItems.Kind kind = InfoItems.of(name);
        return new SearchRow.InfoRow(InfoItems.key(name), badge(kind), icon(name, stack), name, Theme.INK, kindLine(name, kind), right,
                Theme.PRICE);
    }

    /** The row of the tool a tool upgrades into, with what the upgrade costs; null for anything else and for the last tier. */
    static SearchRow nextRow(String name) {
        GatherTool n = sibling(name, ToolWays.tier(name) + 1);
        if (n == null) {
            return null;
        }
        ToolWays.Way up = upgrade(n.name(), name, owns(name));
        return row(n.name(), null, up == null ? null : ToolLadder.summary(up));
    }

    /** The item's own picture: the stack the key was pressed over, a tool's model, a ward seen this session; else null. */
    private static ItemStack icon(String name, ItemStack hovered) {
        if (hovered != null && !hovered.isEmpty()) {
            return hovered;
        }
        for (GatherTool t : tools()) {
            if (t.name().equals(name)) {
                return t.icon();
            }
        }
        return BoxStatsPanel.wardIcon(name);
    }

    private static KindBadge badge(InfoItems.Kind kind) {
        return switch (kind) {
            case TOOL -> new KindBadge("pick", "pick", null);
            case WARD -> new KindBadge("trophy", "shield", null);
            case RUNE -> new KindBadge("trophy", "sparkles", null);
        };
    }

    /** "Fishing · T8 · Lv 70", "Ward", "Rune · Fire": the line under the name. */
    private static String kindLine(String name, InfoItems.Kind kind) {
        switch (kind) {
            case TOOL -> {
                ToolLadder.Kind k = InfoItems.toolKind(name);
                String out = (k == null ? "" : Professions.name(k.profession) + " · ") + "T" + ToolWays.tier(name);
                for (GatherTool t : tools()) {
                    if (t.name().equals(name) && t.level() > 0) {
                        out += " · Lv " + t.level();
                    }
                }
                return out;
            }
            case WARD -> {
                return L.t("wynnlabs.search.info_ward");
            }
            default -> {
                Runes.Rune r = Runes.of(name);
                return L.t("wynnlabs.search.info_rune") + (r == null ? "" : " · " + r.element());
            }
        }
    }

    // ---- the card ----

    @Override
    public String key() {
        return InfoItems.key(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String title() {
        ToolLadder.Kind k = InfoItems.toolKind(name);
        return switch (kind) {
            case TOOL -> k == null ? name : L.t("wynnlabs.tools.kind." + k.name().toLowerCase(Locale.ROOT));
            case WARD -> L.t("wynnlabs.search.info_ward");
            case RUNE -> L.t("wynnlabs.search.info_rune");
        };
    }

    @Override
    public void select() {
        have = BoxStatsPanel.owned(name).clone();
        if (kind != InfoItems.Kind.TOOL) {
            return;
        }
        int tier = ToolWays.tier(name);
        tool = sibling(name, tier);
        lower = sibling(name, tier - 1);
        next = sibling(name, tier + 1);
        boolean ownLower = lower != null && owns(lower.name());
        ways = ToolWays.ways(name, tier, lower == null ? null : lower.name(), ownLower, InfoCard::price);
        upgrade = null;
        Map<String, int[]> held = new LinkedHashMap<>();
        for (ToolWays.Way w : ways) {
            if (w.kind() == ToolWays.Kind.UPGRADE) {
                upgrade = w;
                for (ToolWays.Part p : w.parts()) {
                    held.put(p.name(), BoxStatsPanel.owned(p.name()).clone());
                }
            }
        }
        partsHeld = held;
        ToolWays.Way up = next == null ? null : upgrade(next.name(), name, have[0] + have[1] > 0 || owns(name));
        nextCost = up == null ? null : ToolLadder.summary(up);
    }

    @Override
    public void picture(GuiGraphics g, int x, int y, int size, boolean hover) {
        ItemStack stack = icon(name, name.equals(tab.usesOf) ? tab.usesStack : null);
        if (stack == null || stack.isEmpty()) {
            Kit.kindBadge(g, x, y, size, badge(kind));
            return;
        }
        Kit.slot(g, x, y, size, 0);
        g.renderItem(stack, x + (size - 16) / 2, y + (size - 16) / 2);
        if (hover) {
            menu.hits.add(new MenuHit(x, y, size, size, stack, null, null));
        }
    }

    @Override
    public int ink() {
        return Theme.INK;
    }

    /** The kind's line, and what the player holds of it as the box window says it. */
    @Override
    public Head head() {
        return new Head(kindLine(name, kind), Component.literal(haveText(have)), null);
    }

    /** "You have 2", "3 in your bank", "You have 5 (bag and bank)"; empty for none. */
    private static String haveText(int[] h) {
        int n = h[0] + h[1];
        return n <= 0 ? "" : L.t(h[1] == 0 ? "wynnlabs.box.ways_have_bag" : h[0] == 0 ? "wynnlabs.box.ways_have_bank"
                : "wynnlabs.box.ways_have_both", n);
    }

    // ---- the rows: a tool's ways, each where it is done ----

    @Override
    public String rowsTitle() {
        return card.rows.isEmpty() ? null : L.t("wynnlabs.search.how");
    }

    @Override
    public List<Source> rows(double[] pos) {
        List<Source> out = new ArrayList<>();
        for (ToolWays.Way w : ways) {
            Area at = w.kind() == ToolWays.Kind.MARKET ? null : BoxStatsPanel.wayPlace(w);
            Source.Type type = switch (w.kind()) {
                case MARKET -> Source.Type.MARKET;
                case MERCHANT -> Source.Type.MERCHANT;
                case UPGRADE -> Source.Type.OTHER;
                case DUNGEON -> Source.Type.DUNGEON_MERCHANT;
            };
            String badge = switch (w.kind()) {
                case MARKET -> "coin";
                case MERCHANT -> "shopping-cart";
                case UPGRADE -> "hammer";
                case DUNGEON -> "skull";
            };
            out.add(new Source(type, wayTitle(w), at == null ? List.of() : List.of(at), out.size(), badge, 0, null, 0, 0, null));
        }
        sources = out;
        return out;
    }

    /** "Trade Market", "Tool Merchant", "Upgrade your T7 at an Item Upgrader", "Dungeon Merchant · Galleon's Graveyard". */
    private String wayTitle(ToolWays.Way w) {
        return switch (w.kind()) {
            case MARKET -> L.t("wynnlabs.box.ways_market");
            case MERCHANT -> L.t("wynnlabs.box.ways_merchant");
            case UPGRADE -> L.t("wynnlabs.box.ways_upgrade", ToolWays.tier(name) - 1);
            case DUNGEON -> L.t("wynnlabs.box.ways_dungeon_short") + (w.at() == null ? "" : " · " + w.at());
        };
    }

    /** A way and what it costs: "Trade Market · ~1LE 35EB", "Upgrade your T7 at an Item Upgrader · 4 frags". */
    @Override
    public String rowText(Source s, ItemWiki.Info wiki) {
        for (int i = 0; i < sources.size() && i < ways.size(); i++) {
            if (sources.get(i) == s) {
                String cost = ToolLadder.summary(ways.get(i));
                return cost.isEmpty() ? s.name() : s.name() + " · " + cost;
            }
        }
        return s.name();
    }

    // ---- the actions ----

    /** "Open on map" for a place row, and the Trade Market's listings in the box window. */
    @Override
    public List<SearchCardPanel.Action> actions() {
        return List.of(card.mapChip(), new SearchCardPanel.Action("shopping-cart", L.t("wynnlabs.search.market"), true, false, this::market));
    }

    /** The box window for this item, as its key opens it over a container: listings, and its own page of facts. */
    private void market() {
        BoxMarket.Item item = new BoxMarket.Item(name, Slot.UNKNOWN, Tier.NORMAL, 0, null, 0, true);
        Minecraft.getInstance().setScreen(new BoxScreen(new BoxScreen.Single(item, null, false),
                icon(name, name.equals(tab.usesOf) ? tab.usesStack : null), menu));
    }

    // ---- the sections ----

    @Override
    public int sections(GuiGraphics g, int x, int y, int w, int mx, int my) {
        return switch (kind) {
            case TOOL -> toolSections(g, x, y, w, mx, my);
            case WARD -> wardSections(g, x, y, w, mx, my);
            case RUNE -> runeSections(g, x, y, w, mx, my);
        };
    }

    /** What the upgrade takes with what the player holds of it, then "Uses": the level it needs and the tool it becomes. */
    private int toolSections(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int cy = y;
        if (upgrade != null) {
            cy = card.sources.title(g, L.t("wynnlabs.box.ways_upgrade", ToolWays.tier(name) - 1), null, x, cy, w);
            String fragment = null;
            for (ToolWays.Part p : upgrade.parts()) {
                int[] h = partsHeld.getOrDefault(p.name(), new int[2]);
                int n = h[0] + h[1];
                String right = n > 0 ? haveText(h) : p.fragment() ? L.t("wynnlabs.search.uses_have_none")
                        : p.owned() ? L.t("wynnlabs.box.ways_yours") : p.each() == null ? L.t("wynnlabs.box.ways_no_price")
                        : Prices.fmt(p.each() * p.count());
                boolean got = n >= p.count() || p.owned();
                cy = pair(g, p.count() + "× " + p.name(), right, got ? Theme.GOOD : n > 0 ? Theme.WARN : Theme.INK_3, x, cy, w);
                fragment = p.fragment() ? p.name() : fragment;
            }
            if (fragment != null) {
                Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.tools.step_frag", ToolLadder.dungeonOf(fragment)), w), x, cy,
                        Theme.INK_3);
                cy += Type.META.lineHeight;
            }
            cy += Theme.SP_5;
        }
        cy = card.sources.title(g, L.t("wynnlabs.search.sec_uses"), null, x, cy, w);
        ToolLadder.Kind k = InfoItems.toolKind(name);
        if (k != null) {
            int level = tool == null ? 0 : tool.level();
            int mine = UseStock.level(k.profession);
            cy = pair(g, Professions.name(k.profession), level > 0 ? "Lv " + level : "",
                    level <= 0 || mine <= 0 ? Theme.INK_2 : mine >= level ? Theme.GOOD : Theme.BAD, x, cy, w);
        }
        if (next != null) {
            String label = L.t("wynnlabs.search.info_next");
            int lw = Type.BODY.width(font(), label) + Theme.SP_3;
            Type.BODY.draw(g, font(), label, x, cy, Theme.INK_2);
            String cost = nextCost == null || nextCost.isEmpty() ? "" : nextCost;
            int cw = cost.isEmpty() ? 0 : Type.BODY.width(font(), cost) + Theme.SP_3;
            String to = next.name();
            card.sources.link(g, to, x + lw, cy, w - lw - cw, mx, my, () -> tab.selName = InfoItems.key(to));
            if (!cost.isEmpty()) {
                Type.BODY.drawRight(g, font(), cost, x + w, cy, Theme.PRICE);
            }
            cy += Type.BODY.lineHeight + Theme.SP_1;
        }
        return cy;
    }

    /** Where it drops this week (a row opens that pool in the Endgame tab), what wards are for, the seven wards' prices. */
    private int wardSections(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int cy = card.sources.title(g, L.t("wynnlabs.box.ward.drops_title"), null, x, y, w);
        List<PoolRegion> where = WynnventoryPools.holding(name);
        if (where.isEmpty()) {
            cy = card.sources.line(g, L.t("wynnlabs.box.ward.drops_none"), x, cy, w, Theme.INK_3);
        }
        for (PoolRegion r : where) {
            boolean raid = "RAID".equalsIgnoreCase(r.type());
            cy = poolLink(g, r.name(), L.t(raid ? "wynnlabs.box.ward.raid" : "wynnlabs.box.ward.lootrun"), raid, x, cy, w, mx, my);
        }
        cy = card.sources.title(g, L.t("wynnlabs.box.ward.use_title"), null, x, cy + Theme.SP_5, w);
        cy = Type.META.drawWrapped(g, font(), L.t("wynnlabs.box.ward.use"), x, cy, w, 0, Theme.INK_2);
        cy = card.sources.title(g, L.t("wynnlabs.box.ward.set_title"), null, x, cy + Theme.SP_5, w);
        long total = 0;
        boolean unknown = false;
        for (String colour : InfoItems.WARDS) {
            String ward = colour + " Ward";
            Double v = price(ward);
            unknown |= v == null;
            total += v == null ? 0 : Math.round(v);
            cy = pair(g, ward, v == null ? L.t("wynnlabs.box.ways_no_price") : Prices.fmt(v), v == null ? Theme.INK_3 : Theme.PRICE, x, cy, w,
                    ward.equals(name));
        }
        if (total > 0) {
            Type.LABEL.draw(g, font(), L.t("wynnlabs.box.ward.set_total"), x, cy, Theme.INK);
            Type.LABEL.drawRight(g, font(), Prices.fmt(total) + (unknown ? " +?" : ""), x + w, cy, Theme.PRICE);
            cy += Type.LABEL.lineHeight;
        }
        return cy;
    }

    /** Where to get it and what else it does, the raids it opens, the keys it makes at the Key Forge with their margin. */
    private int runeSections(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Runes.Rune rune = Runes.of(name);
        if (rune == null) {
            return y;
        }
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
        int cy = card.sources.title(g, L.t("wynnlabs.box.rune.get"), null, x, y, w);
        for (String line : lines) {
            Type.META.draw(g, font(), "•", x, cy, Theme.INK_3);
            cy = Type.META.drawWrapped(g, font(), line, x + Theme.SP_4, cy, w - Theme.SP_4, 0, Theme.INK_2) + Theme.SP_1;
        }
        if (!rune.raids().isEmpty()) {
            cy = card.sources.title(g, L.t("wynnlabs.box.rune.opens"), null, x, cy + Theme.SP_5, w);
            for (Runes.Raid r : rune.raids()) {
                cy = poolLink(g, r.raid(), "Lv " + r.level(), true, x, cy, w, mx, my);
            }
        }
        if (!rune.keys().isEmpty()) {
            cy = card.sources.title(g, L.t("wynnlabs.box.rune.keys"), null, x, cy + Theme.SP_5, w);
            for (Runes.KeyTrade t : rune.keys()) {
                Runes.Margin m = Runes.margin(rune, t, InfoCard::price);
                String key = t.key().replace("Corrupted ", "").replace(" Key", "");
                cy = pair(g, L.t("wynnlabs.box.rune.key_name", key), m.sells() == null ? "" : "~" + Prices.fmt(m.sells()), Theme.PRICE, x,
                        cy, w) - Theme.SP_1;
                Double profit = m.profit();
                String cost = L.t("wynnlabs.box.rune.key_cost", t.runes(), m.cost() == null ? L.t("wynnlabs.box.ways_no_price").toLowerCase(Locale.ROOT) : Prices.fmt(m.cost()));
                String verdict = profit == null ? "" : L.t(profit >= 0 ? "wynnlabs.box.rune.profit" : "wynnlabs.box.rune.loss",
                        Prices.fmt(Math.abs(profit)));
                int vw = verdict.isEmpty() ? 0 : Type.META.width(font(), verdict) + Theme.SP_3;
                Type.META.draw(g, font(), Type.META.fit(font(), cost, w - vw), x, cy, Theme.INK_3);
                if (!verdict.isEmpty()) {
                    Type.META.drawRight(g, font(), verdict, x + w, cy, profit >= 0 ? Theme.GOOD : Theme.BAD);
                }
                cy += Type.META.lineHeight + Theme.SP_2;
            }
        }
        return cy;
    }

    /** A raid or a lootrun camp as a line that opens its pool in the Endgame tab, with a word at its end. */
    private int poolLink(GuiGraphics g, String region, String note, boolean raid, int x, int y, int w, int mx, int my) {
        int nw = Type.META.width(font(), note) + Theme.SP_3;
        Type.META.drawRight(g, font(), note, x + w, y + 1, Theme.INK_3);
        return card.sources.link(g, region + " ›", x, y, w - nw, mx, my, () -> {
            EndgameTab.INSTANCE.showPool(raid, region);
            menu.openTab(RoadmapScreen.TAB_ENDGAME);
        });
    }

    private int pair(GuiGraphics g, String left, String right, int ink, int x, int y, int w) {
        return pair(g, left, right, ink, x, y, w, false);
    }

    /** A line with a value at its end; {@code strong}: this card's own line among its like. */
    private int pair(GuiGraphics g, String left, String right, int ink, int x, int y, int w, boolean strong) {
        int rw = right.isEmpty() ? 0 : Type.BODY.width(font(), right) + Theme.SP_4;
        Type role = strong ? Type.LABEL : Type.BODY;
        role.draw(g, font(), Type.BODY.fit(font(), left, w - rw), x, y, strong ? Theme.INK : Theme.INK_2);
        if (!right.isEmpty()) {
            Type.BODY.drawRight(g, font(), right, x + w, y, ink);
        }
        return y + Type.BODY.lineHeight + Theme.SP_1;
    }
}
