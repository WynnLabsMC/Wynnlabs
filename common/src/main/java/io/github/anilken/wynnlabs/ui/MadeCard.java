package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.HowMade;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.InfoItems;
import io.github.anilken.wynnlabs.search.ItemWiki;
import io.github.anilken.wynnlabs.search.Source;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The search card of a crafted item ({@link CardView}): how it was most likely made, from the
 * same search the box window runs ({@link BoxMadePanel}; it fills in while "Searching…" shows).
 * The verdict beside the name, then "How it's made": the recipe, the two materials at their tiers
 * and the ingredients found (each opens in Search), how many of the item's values the layout
 * matches, what the ingredients and the materials cost at market prices and what is left to buy
 * for a crafter who gathers the materials; the profession's nearest station as a row the compass
 * takes; and "Open in Crafting" with that layout loaded. Read-only.
 */
final class MadeCard extends MenuPaint implements CardView {

    private final SearchCardPanel card;
    private final SearchTab tab;
    private final BoxMadePanel made;
    private Entry station;

    private MadeCard(SearchCardPanel card, BoxMadePanel made) {
        super(card.tab.menu);
        this.card = card;
        this.tab = card.tab;
        this.made = made;
    }

    /** The card of the crafted item the tab shows, or null when the key is not its. */
    static MadeCard of(SearchCardPanel card, String key) {
        BoxMadePanel made = card.tab.made;
        return made != null && key.equals(key(made.target)) ? new MadeCard(card, made) : null;
    }

    /** A crafted item's name, or its type's when it has none. */
    static String name(HowMade.Target t) {
        return t.name() == null || t.name().isBlank() ? CraftFormat.typeName(t.type()) : t.name();
    }

    static String key(HowMade.Target t) {
        return InfoItems.MADE + name(t);
    }

    private static String kindLine(HowMade.Target t) {
        return L.t("wynnlabs.box.made.kind", CraftFormat.typeName(t.type()).toLowerCase(Locale.ROOT), t.level());
    }

    /** The item's row in the list: its own stack, its name in the crafted colour, "Crafted scroll · Lv 82". */
    static SearchRow.InfoRow row(SearchTab tab) {
        HowMade.Target t = tab.made.target;
        return new SearchRow.InfoRow(key(t), new KindBadge("coin", CraftFormat.typeIcon(t.type()), null), tab.usesStack, name(t),
                Theme.TIER_CRAFTED, kindLine(t), null, 0);
    }

    @Override
    public String key() {
        return key(made.target);
    }

    @Override
    public String name() {
        return name(made.target);
    }

    @Override
    public String title() {
        return L.t("wynnlabs.box.made.made_title");
    }

    @Override
    public void select() {
        // nothing to work out here: the search runs on its own thread and the card follows it
    }

    @Override
    public void picture(GuiGraphics g, int x, int y, int size, boolean hover) {
        ItemStack stack = tab.usesStack;
        if (stack == null || stack.isEmpty()) {
            Kit.kindBadge(g, x, y, size, new KindBadge("coin", CraftFormat.typeIcon(made.target.type()), null));
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
        return Theme.TIER_CRAFTED;
    }

    /** "Crafted scroll · Lv 82", and the verdict as the seal: "Searching…", then how sure the answer is. */
    @Override
    public Head head() {
        String seal;
        if (!made.done()) {
            seal = Ui.busy(L.t("wynnlabs.box.made.searching"));
        } else {
            HowMade.Answer a = made.answer();
            seal = switch (a == null ? HowMade.Verdict.NONE : a.verdict()) {
                case EXACT -> L.t("wynnlabs.box.made.exact");
                case POSSIBLE -> L.t("wynnlabs.box.made.possible");
                case CLOSEST, NONE -> L.t("wynnlabs.box.made.closest");
            };
        }
        return new Head(kindLine(made.target), Component.empty(), seal);
    }

    // ---- over the rows: how it was made ----

    @Override
    public int lead(GuiGraphics g, int x, int y, int w, int mx, int my) {
        CraftData.Recipe recipe = made.recipe();
        HowMade.Answer a = made.answer();
        HowMade.Found f = a == null ? null : a.best();
        String matches = null;
        if (f != null) {
            int ok = 0;
            List<HowMade.Row> rows = HowMade.rows(made.target, recipe, f.result());
            for (HowMade.Row r : rows) {
                ok += r.ok() ? 1 : 0;
            }
            matches = L.t("wynnlabs.box.made.rows_folded", ok, rows.size());
        }
        int cy = card.sources.title(g, L.t("wynnlabs.box.made.made_title"), matches, x, y, w);
        if (recipe == null || made.done() && f == null) {
            return card.sources.line(g, L.t(recipe == null ? "wynnlabs.box.made.no_recipe" : "wynnlabs.box.made.nothing"), x, cy, w,
                    Theme.INK_3) + Theme.SP_5;
        }
        cy = pair(g, L.t("wynnlabs.craft.recipe"), L.t("wynnlabs.box.made.recipe_value", CraftFormat.typeName(recipe.type()),
                recipe.minLevel() + "–" + recipe.maxLevel()), Theme.INK, x, cy, w);
        if (f == null) {
            return cy + Theme.SP_5;                 // still searching: the recipe is known, the layout is not yet
        }
        // the two materials at the tiers found, then the ingredients; each opens in Search (where to gather or buy it)
        cy = card.sources.title(g, L.t("wynnlabs.box.made.cell_materials"), known(made.materialsCost(f)), x, cy + Theme.SP_4, w);
        for (int m = 0; m < 2 && m < recipe.materials().size(); m++) {
            CraftData.Material mat = recipe.materials().get(m);
            String shown = mat.shown();
            cy = card.sources.link(g, mat.amount() + " " + shown + " T" + (m == 0 ? f.tier1() : f.tier2()), x, cy, w, mx, my,
                    () -> tab.showUses(shown));
        }
        HowMade.Cost ing = made.ingredientsCost(f);
        cy = card.sources.title(g, L.t("wynnlabs.box.made.cell_ingredients"), known(ing), x, cy + Theme.SP_4, w);
        Map<String, Integer> count = new LinkedHashMap<>();
        for (Ingredient i : f.slots()) {
            if (i != null && !i.empty()) {
                count.merge(i.name(), 1, Integer::sum);
            }
        }
        if (count.isEmpty()) {
            cy = card.sources.line(g, L.t("wynnlabs.box.made.empty"), x, cy, w, Theme.INK_3);
        }
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            String item = e.getKey();
            cy = card.sources.link(g, (e.getValue() > 1 ? e.getValue() + "× " : "") + item, x, cy, w, mx, my, () -> tab.showUses(item));
        }
        // the whole craft at market prices, and what is left to buy when the materials are gathered
        cy += Theme.SP_4;
        Kit.hair(g, x, cy, w, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        cy = cost(g, L.t("wynnlabs.box.made.cell_total"), ing.plus(made.materialsCost(f)), x, cy, w);
        cy = cost(g, L.t("wynnlabs.box.made.gathered"), ing, x, cy, w);
        return cy + Theme.SP_5;
    }

    /** A cost as the box window writes it ("1.2k", "1.2k +?"), or null when nothing in it has a price: no bare "?". */
    private static String known(HowMade.Cost c) {
        String text = BoxMadePanel.cost(c);
        return "?".equals(text) ? null : text;
    }

    /** A cost on its line; "No price" in muted ink when nothing in it has one. */
    private int cost(GuiGraphics g, String label, HowMade.Cost c, int x, int y, int w) {
        String text = known(c);
        return pair(g, label, text == null ? L.t("wynnlabs.box.ways_no_price") : text, text == null ? Theme.INK_3 : Theme.PRICE, x, y, w);
    }

    private int pair(GuiGraphics g, String left, String right, int ink, int x, int y, int w) {
        int rw = Type.BODY.width(font(), right) + Theme.SP_4;
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), left, w - rw), x, y, Theme.INK_2);
        Type.BODY.drawRight(g, font(), right, x + w, y, ink);
        return y + Type.BODY.lineHeight + Theme.SP_1;
    }

    // ---- the rows: the profession's nearest station ----

    @Override
    public String rowsTitle() {
        return card.rows.isEmpty() ? null : L.t("wynnlabs.craft.station");
    }

    @Override
    public boolean rowsCounted() {
        return false;
    }

    @Override
    public List<Source> rows(double[] pos) {
        CraftData.Recipe recipe = made.recipe();
        UseCard.Station s = recipe == null ? null : UseCard.station(recipe.profession(), pos);
        station = s == null ? null : s.entry();
        return s == null ? List.of() : List.of(s.source());
    }

    @Override
    public String rowText(Source s, ItemWiki.Info wiki) {
        return L.t("wynnlabs.craft.station_label", io.github.anilken.wynnlabs.game.Professions.name(made.recipe().profession()));
    }

    @Override
    public String pinName() {
        return made.recipe() == null ? name() : rowText(null, null);
    }

    @Override
    public Entry link(Source s) {
        return station;
    }

    /** "Open on map" for the station, and "Open in Crafting" with the layout found loaded, as the box window's "Recipe ›". */
    @Override
    public List<SearchCardPanel.Action> actions() {
        return List.of(card.mapChip(), new SearchCardPanel.Action("hammer", L.t("wynnlabs.search.uses_open_craft"), made.recipe() != null,
                false, () -> {
                    HowMade.Answer a = made.answer();
                    if (a != null && a.best() != null) {
                        CraftingTab.INSTANCE.load(made.recipeOf(a.best()));
                    }
                    menu.openTab(RoadmapScreen.TAB_CRAFT);
                }));
    }

    @Override
    public int sections(GuiGraphics g, int x, int y, int w, int mx, int my) {
        // why this is a best guess, as the box window's verdict says on hover
        return Type.META.drawWrapped(g, font(), L.t("wynnlabs.box.made.verdict_tip"), x, y, w, 0, Theme.INK_3);
    }
}
