package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.Shopping;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemWiki;
import io.github.anilken.wynnlabs.search.Kind;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceRank;
import io.github.anilken.wynnlabs.search.Things;
import io.github.anilken.wynnlabs.search.UseKey;
import io.github.anilken.wynnlabs.search.UseList;
import io.github.anilken.wynnlabs.search.Uses;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The search card of one use of an item ({@link CardView}; nothing of the index behind it). A
 * craft: the item type and its profession and levels, "Each craft" with what one craft costs at
 * the market and the two materials with what the player holds of each (the other material opens
 * its own uses), the nearest station of the profession as a row the compass takes, and "Open in
 * Crafting". An ingredient's profession: the station and the Crafting tab. Mount feed: the points
 * each stat gets, and what the mounts last seen in a feeder would gain. The words of a use are
 * static, for the list's rows too. Read-only: what it changes is which tab or view is open.
 */
final class UseCard extends MenuPaint implements CardView {

    private final SearchCardPanel card;
    private final SearchTab tab;
    private final String item;
    private final UseList.Line line;
    private int[] own = new int[2];
    private int[] other = new int[2];
    /** The profession's station in the index (its card lists every one), null when the index has none. */
    private Entry station;
    private List<MountTips.Line> mounts = List.of();

    private UseCard(SearchCardPanel card, String item, UseList.Line line) {
        super(card.tab.menu);
        this.card = card;
        this.tab = card.tab;
        this.item = item;
        this.line = line;
    }

    /** The card of a use's key, or null when the item has no such use. */
    static UseCard of(SearchCardPanel card, String key) {
        UseKey k = UseKey.parse(key);
        if (k == null) {
            return null;
        }
        UseList.Line line = UseList.find(UseStock.lines(k.item()), key);
        return line == null ? null : new UseCard(card, k.item(), line);
    }

    // ---- the words of a use: the list's rows and the card's head ----

    /** The row of a use in the search list. */
    static SearchRow.UseRow row(String item, UseList.Line l) {
        boolean craft = l.kind() == UseList.Kind.CRAFT;
        return new SearchRow.UseRow(l.key(), badge(l), title(l), meta(item, l),
                craft ? L.n("wynnlabs.search.uses_crafts", l.crafts()) : null, l.crafts() > 0 ? Theme.GOOD : Theme.INK_3);
    }

    /** "Ring", "Armouring", "Mount feed". */
    static String title(UseList.Line l) {
        return switch (l.kind()) {
            case CRAFT -> CraftFormat.typeName(l.craft().type());
            case PROFESSION -> Professions.name(l.profession());
            case FEED -> L.t("wynnlabs.search.uses_feed");
        };
    }

    /** "Jeweling · 4 Koi Oil + 4 Cobalt Gem", "Helmet, Chestplate", "+15 Handling · +9 Training · +4 Altitude". */
    static String meta(String item, UseList.Line l) {
        return switch (l.kind()) {
            case CRAFT -> Professions.name(l.profession()) + " · " + amount(l.bracket().amount(), item)
                    + (l.bracket().other() == null ? "" : " + " + amount(l.bracket().otherAmount(), l.bracket().other()));
            case PROFESSION -> types(l.profession());
            case FEED -> points(l.feed());
        };
    }

    /** A use's name outside its list (a key shown by name): "Ring · Koi Oil". */
    static String name(UseKey k) {
        String what = k.feed() ? L.t("wynnlabs.search.uses_feed") : k.profession() != null ? Professions.name(k.profession())
                : CraftFormat.typeName(k.type());
        return what + " · " + k.item();
    }

    private static String amount(int n, String name) {
        return n + " " + name;
    }

    /** The item types a profession crafts, in words. */
    private static String types(String profession) {
        List<String> out = new ArrayList<>();
        for (String t : UseList.types(profession, CraftData.all())) {
            out.add(CraftFormat.typeName(t));
        }
        return String.join(", ", out);
    }

    private static String points(Uses.MountFeed f) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < f.stats().size(); i++) {
            if (f.points().get(i) > 0) {
                out.add("+" + f.points().get(i) + " " + f.stats().get(i).display());
            }
        }
        return String.join(" · ", out);
    }

    /** A use's badge: a craft's item type on the market's gold, a profession's station, the mount on green. */
    static KindBadge badge(UseList.Line l) {
        return switch (l.kind()) {
            case CRAFT -> new KindBadge("coin", CraftFormat.typeIcon(l.craft().type()), null);
            case PROFESSION -> KindBadge.profession(l.profession());
            case FEED -> new KindBadge("pick", "horse", null);
        };
    }

    // ---- the card ----

    @Override
    public String key() {
        return line.key();
    }

    @Override
    public String name() {
        return title(line);
    }

    @Override
    public String title() {
        return L.t("wynnlabs.search.uses_chip", item);
    }

    /** Nothing to paste anywhere: a use is not a thing of the game with a name. */
    @Override
    public boolean copyable() {
        return false;
    }

    @Override
    public String pinName() {
        return line.profession() == null ? name() : stationName();
    }

    @Override
    public void select() {
        own = UseStock.of(item);
        Uses.Bracket b = line.bracket();
        other = b == null || b.other() == null ? new int[2] : UseStock.of(b.other());
        mounts = line.feed() == null ? List.of() : MountTips.lines(line.feed().material(), 4);
        if (b != null) {
            // a material shown without a price: asked for like the Crafting tab's (only when the player switched prices on)
            List<String> ask = new ArrayList<>();
            for (String n : new String[] {item, b.other()}) {
                if (n != null && UseStock.price(n) == null) {
                    ask.add(Shopping.materialKey(n, 1));
                }
            }
            try {
                if (!ask.isEmpty()) {
                    GameState.current().requestPrices(ask);
                }
            } catch (RuntimeException ignored) {
                // a recording without prices
            }
        }
    }

    @Override
    public void picture(GuiGraphics g, int x, int y, int size, boolean hover) {
        Kit.kindBadge(g, x, y, size, badge(line));
    }

    @Override
    public int ink() {
        return Theme.INK;
    }

    @Override
    public Head head() {
        return switch (line.kind()) {
            case CRAFT -> new Head(Professions.name(line.profession()) + " · Lv " + line.craft().minLevel() + "–"
                    + line.craft().maxLevel(), Component.empty(), null);
            case PROFESSION -> new Head(item + " · " + types(line.profession()), Component.empty(), null);
            case FEED -> new Head(item + " · Lv " + line.feed().material().level(), Component.literal(UseStock.text(own)), null);
        };
    }

    // ---- over the rows: what a craft takes, what a feed gives ----

    @Override
    public int lead(GuiGraphics g, int x, int y, int w, int mx, int my) {
        return switch (line.kind()) {
            case CRAFT -> eachCraft(g, x, y, w, mx, my) + Theme.SP_5;
            case FEED -> feed(g, x, y, w) + Theme.SP_5;
            case PROFESSION -> y;
        };
    }

    /** "Each craft" with its market cost, then the two materials: beside each other when both fit, else one under the other. */
    private int eachCraft(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Uses.Bracket b = line.bracket();
        int cy = card.sources.title(g, L.t("wynnlabs.search.uses_each"), cost(b), x, y, w);
        String mine = amount(b.amount(), item);
        String mineHave = UseStock.text(own);
        if (b.other() == null) {
            cy = material(g, item, mine, mineHave, null, x, cy, w, mx, my);
        } else {
            String theirs = amount(b.otherAmount(), b.other());
            String theirsHave = UseStock.text(other);
            int col = (w - Theme.SP_5) / 2;
            int need = MAT + Theme.SP_3 + Math.max(Math.max(Type.BODY.width(font(), mine), Type.META.width(font(), mineHave)),
                    Math.max(Type.BODY.width(font(), theirs), Type.META.width(font(), theirsHave)));
            boolean beside = need <= col;
            String name = b.other();
            int under = material(g, item, mine, mineHave, null, x, cy, beside ? col : w, mx, my);
            cy = beside ? material(g, name, theirs, theirsHave, () -> tab.showUses(name), x + col + Theme.SP_5, cy, col, mx, my)
                    : material(g, name, theirs, theirsHave, () -> tab.showUses(name), x, under + Theme.SP_2, w, mx, my);
        }
        if (line.craft().mixed()) {
            // the brackets differ: say which one these amounts are
            String range = b.minLevel() + "–" + b.maxLevel();
            boolean reached = UseList.reached(b, UseStock.level(line.profession()));
            cy = Type.META.drawWrapped(g, font(), L.t(reached ? "wynnlabs.search.uses_bracket_mine" : "wynnlabs.search.uses_bracket_low",
                    range), x, cy + Theme.SP_3, w, 2, Theme.INK_3);
        }
        return cy;
    }

    /** A material's slot: an item at 1x in it, as the list's rows. */
    private static final int MAT = SearchRow.SLOT;

    /**
     * One material: its picture, "4 Koi Oil" and what the player holds under it; {@code open}: a
     * click on its name opens that material's uses. Returns the y under it.
     */
    private int material(GuiGraphics g, String name, String text, String have, Runnable open, int x, int y, int w, int mx, int my) {
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        int h = Math.max(MAT, textH);
        CraftIcons.slot(g, x, y + (h - MAT) / 2, MAT, CraftIcons.of(name));
        int tx = x + MAT + Theme.SP_3;
        int ty = y + (h - textH) / 2;
        String shown = Type.BODY.fit(font(), text, x + w - tx);
        if (open == null) {
            Type.BODY.draw(g, font(), shown, tx, ty, Theme.INK);
        } else {
            card.sources.link(g, shown, tx, ty, x + w - tx, mx, my, open);
            card.tip(tx, ty, Type.BODY.width(font(), shown), Type.BODY.lineHeight, mx, my, L.t("wynnlabs.search.uses_chip", name));
        }
        Type.META.draw(g, font(), Type.META.fit(font(), have, x + w - tx), tx, ty + Type.BODY.lineHeight, Theme.INK_3);
        return y + h;
    }

    /**
     * What one craft costs at the market, both materials at tier 1: "3 EB at market", "+?" after
     * it while one of the two has no price, nothing while neither has.
     */
    private String cost(Uses.Bracket b) {
        long sum = 0;
        boolean priced = false;
        boolean unpriced = false;
        String[] names = {item, b.other()};
        int[] amounts = {b.amount(), b.otherAmount()};
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                continue;
            }
            Double p = UseStock.price(names[i]);
            if (p == null) {
                unpriced = true;
            } else {
                priced = true;
                sum += Math.round(p * amounts[i]);
            }
        }
        return priced ? L.t("wynnlabs.search.uses_cost", CraftFormat.costText(sum, true, unpriced)) : null;
    }

    /** "Each one gives" with a line a stat, then what the mounts last seen in a feeder would gain. */
    private int feed(GuiGraphics g, int x, int y, int w) {
        Uses.MountFeed f = line.feed();
        int cy = card.sources.title(g, L.t("wynnlabs.search.uses_feed_gives"), L.t("wynnlabs.search.uses_feed_caps"), x, y, w);
        for (int i = 0; i < f.stats().size(); i++) {
            if (f.points().get(i) > 0) {
                Type.BODY.draw(g, font(), f.stats().get(i).display(), x, cy, Theme.INK_2);
                Type.BODY.drawRight(g, font(), "+" + f.points().get(i), x + w, cy, Theme.GOOD);
                cy += Type.BODY.lineHeight + Theme.SP_1;
            }
        }
        if (!mounts.isEmpty()) {
            cy = card.sources.title(g, L.t("wynnlabs.search.uses_feed_mounts"), null, x, cy + Theme.SP_5, w);
            for (MountTips.Line m : mounts) {
                cy = card.sources.line(g, L.t("wynnlabs.mounts.tip_gain", m.row(), m.name(), m.gains()), x, cy, w, Theme.INK_2);
            }
        }
        return cy;
    }

    // ---- the rows: the profession's nearest station ----

    /** "Station" while there is one to go to; a feed has no place. */
    @Override
    public String rowsTitle() {
        return card.rows.isEmpty() ? null : L.t("wynnlabs.craft.station");
    }

    @Override
    public boolean rowsCounted() {
        return false;
    }

    /** The nearest station of the profession: the index's (its card lists them all), else the map's. */
    @Override
    public List<Source> rows(double[] pos) {
        station = null;
        String prof = line.profession();
        if (prof == null) {
            return List.of();
        }
        String type = "profession:" + ("armouring".equals(prof) ? "armoring" : prof);    // the map's spelling
        for (Entry e : ItemIndex.get().all()) {
            if (e.kind() == Kind.SERVICE && type.equals(e.type())) {
                List<Area> near = SourceRank.nearest(e.places(), pos, 1);
                if (!near.isEmpty()) {
                    station = e;
                    return List.of(new Source(Source.Type.MERCHANT, e.name(), List.of(near.get(0)), 0, Things.badge(e), 0, null, 0, 0,
                            null));
                }
            }
        }
        try {
            int[] best = null;
            double bestD = Double.MAX_VALUE;
            for (int[] s : Wyn.stations(prof.toUpperCase(Locale.ROOT))) {
                double d = pos == null ? 0 : Math.hypot(s[0] - pos[0], s[2] - pos[1]);
                if (best == null || d < bestD) {
                    best = s;
                    bestD = d;
                }
            }
            if (best != null) {
                return List.of(new Source(Source.Type.OTHER, null, List.of(Area.point(best[0], best[1], best[2])), 0, "hammer", 0,
                        null, 0, 0, null));
            }
        } catch (RuntimeException ignored) {
            // no map to ask
        }
        return List.of();
    }

    @Override
    public String rowText(Source s, ItemWiki.Info wiki) {
        return stationName();
    }

    private String stationName() {
        return L.t("wynnlabs.craft.station_label", Professions.name(line.profession()));
    }

    /** The station's own card: every one of them on the map. */
    @Override
    public Entry link(Source s) {
        return station;
    }

    // ---- the actions ----

    /** "Open on map" for the station and "Open in Crafting"; a feed has neither. */
    @Override
    public List<SearchCardPanel.Action> actions() {
        if (line.profession() == null) {
            return List.of();
        }
        return List.of(card.mapChip(), new SearchCardPanel.Action("hammer", L.t("wynnlabs.search.uses_open_craft"), true, false,
                this::openCrafting));
    }

    /** The Crafting tab, on this craft's recipe at the bracket shown (the recipe on it stays when it is that one already). */
    private void openCrafting() {
        if (line.kind() == UseList.Kind.CRAFT) {
            CraftingTab t = CraftingTab.INSTANCE;
            String type = line.craft().type();
            int min = line.bracket().minLevel();
            if (type.equals(t.type) && t.minLevel == min) {
                t.view = CraftingTab.VIEW_RECIPE;
            } else if (CraftData.find(type, min) != null) {
                t.load(new RecipeCode.Recipe(type, min, 1, 1, Craft.Speed.NORMAL, List.of()));
            }
        }
        menu.openTab(RoadmapScreen.TAB_CRAFT);
    }

    @Override
    public int sections(GuiGraphics g, int x, int y, int w, int mx, int my) {
        return y;
    }
}
