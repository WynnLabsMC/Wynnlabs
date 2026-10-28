package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.SetSuggestions;
import io.github.anilken.wynnlabs.crafting.Suggestions;
import io.github.anilken.wynnlabs.crafting.WhatCanICraft;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.Klass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The J menu's crafting tab, by what the player comes for: Tarif (a type, a level and a goal, the
 * 2×3 grid, the crafted item and the best three layouts), Öneriler (what can be crafted from what is
 * owned now, and with a few bought), Meslek (each profession's level road, step by step). The
 * ingredient picker, the shopping list, the saved recipes and a set's pieces open in a drawer. Its
 * state lives here between openings of the menu. It never feeds the build plan, the road map or any
 * advice.
 */
public final class CraftingTab {

    /** One clickable or hoverable area. */
    record Act(int x, int y, int w, int h, Runnable left, Runnable right, List<Component> tip) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** The open dropdown (UiMenu) under its chip. */
    enum Drop { NONE, TYPE, LEVEL, GOAL, CONDITIONS, SPEED, TIER1, TIER2, ROAD_TYPE, ROAD_PROF, SET_BAND, SET_WEAPON, SUGG_LEVEL, SUGG_KIND, SET_COMBINE, SUGG_GOALS, SET_STATUS, SUGG_FILTER }

    public static final CraftingTab INSTANCE = new CraftingTab();

    /** For the preview without the game: ingredients to use when Wynntils has none. */
    static volatile List<Ingredient> previewIngredients = List.of();
    /** For the preview: profession levels to use instead of the recording's. */
    static volatile Map<String, Integer> previewLevels = Map.of();

    static final int[] FLOORS = {1, 50, 100, 200, 300};
    /** The page is in two columns from this width (one, scrolled, on a small window). */
    static final int TWO_COLUMNS_MIN = 440;
    static final int VIEW_RECIPE = 0;
    static final int VIEW_SUGGEST = 1;
    static final int VIEW_ROAD = 2;

    // ---- the recipe ----
    String type;
    int minLevel;
    int tier1 = 1;
    int tier2 = 1;
    Craft.Speed speed = Craft.Speed.NORMAL;
    final String[] slots = new String[Craft.SLOTS];

    // ---- the goal ----
    final LinkedHashMap<String, Double> weights = new LinkedHashMap<>(Map.of("spellDamage", 1.0));
    final Set<String> noNegative = new LinkedHashSet<>();
    boolean capToPoints;
    int floorIndex;

    // ---- search results ----
    volatile List<CraftSearch.Outcome> results = List.of();
    final List<Suggestions.Suggestion> suggestions = new CopyOnWriteArrayList<>();
    final List<SetSuggestions.CraftSet> sets = new CopyOnWriteArrayList<>();

    // ---- the page ----
    Drop open = Drop.NONE;
    int[] openAnchor;
    int dropScroll;
    /** The open dropdown's state (its scroll bar, the keys' row). */
    final UiMenu.State dropMenu = new UiMenu.State();
    final ScrollBar pageBar = new ScrollBar();
    int scroll;
    int scrollMax;
    /** Tarif (0), Öneriler (1) or Meslek (2). */
    int view;
    /** Suggestions as single pieces (0) or whole sets (1). */
    int suggestMode;
    /** Whether the recipe page is in two columns this frame (one on a narrow page). */
    boolean twoColumns = true;
    List<Ingredient> preview;            // a layout drawn dashed on the grid (hovered row)
    List<Ingredient> nextPreview;

    // ---- caches ----
    List<Ingredient> ingredients = List.of();
    final Map<String, Ingredient> byName = new HashMap<>();
    WhatCanICraft.Stock stock = new WhatCanICraft.Stock(Map.of(), Map.of(), Map.of());
    private long stockAt;
    int[] points = new int[5];
    Map<String, CraftSearch.Price> prices = Map.of();
    /** Ingredients on screen to buy whose price is not known yet: asked for at the next refresh. */
    final Set<String> wantedPrices = new LinkedHashSet<>();

    // ---- per frame ----
    Screen screen;
    Font font;
    EditBox pickSearch;
    /** The suggestions page's search (goal, item type or ingredient). */
    EditBox suggestSearch;
    EditBox saveName;
    final List<Act> acts = new ArrayList<>();
    final List<Act> overlay = new ArrayList<>();
    int[] overlayBox;
    int[] clip;
    java.util.function.Consumer<String> flash = s -> {};

    // ---- the page's parts ----
    final CraftHeaderPanel headerPanel = new CraftHeaderPanel(this);
    final CraftRecipePanel recipePanel = new CraftRecipePanel(this);
    final CraftResultPanel resultPanel = new CraftResultPanel(this);
    final CraftOptimiserPanel optimiserPanel = new CraftOptimiserPanel(this);
    final CraftSuggestionsPanel suggestionsPanel = new CraftSuggestionsPanel(this);
    final CraftSetsPanel setsPanel = new CraftSetsPanel(this);
    final CraftSavedPanel savedPanel = new CraftSavedPanel(this);
    final CraftRoadPanel roadPanel = new CraftRoadPanel(this);
    final CraftDrawers drawers = new CraftDrawers(this);
    final CraftDropdowns dropdowns = new CraftDropdowns(this);

    private CraftingTab() {}

    /** The text boxes, made again each time the menu opens; the screen adds them as widgets. */
    List<EditBox> init(Screen screen, Font font, java.util.function.Consumer<String> flash) {
        this.screen = screen;
        this.font = font;
        this.flash = flash;
        pickSearch = UiInput.box(font, "wynnlabs.craft.search_hint", pickSearch, v -> drawers.pickScroll());
        saveName = UiInput.box(font, "wynnlabs.craft.saved_name", saveName, v -> { });
        suggestSearch = UiInput.box(font, "wynnlabs.craft.filter_search_hint", suggestSearch, v -> suggestionsPanel.queryChanged(v));
        saveName.setMaxLength(40);
        stockAt = 0;
        return List.of(pickSearch, saveName, suggestSearch);
    }

    /** The menu was closed: running searches stop. */
    void closed() {
        optimiserPanel.stop();
        suggestionsPanel.stop();
        setsPanel.stop();
        drawers.close();
        open = Drop.NONE;
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - stockAt < (ingredients.isEmpty() ? 1000 : 5000) && type != null) {        // no list yet: looked for every second
            return;
        }
        stockAt = now;
        List<Ingredient> all = Wyn.craftIngredients();
        if (all.isEmpty()) {
            all = previewIngredients;
        }
        if (all != ingredients) {
            ingredients = all;
            byName.clear();
            for (Ingredient i : all) {
                byName.put(i.name(), i);
            }
        }
        try {
            stock = Crafting.stock();
            points = Crafting.points();
        } catch (Throwable t) {
            // a recording without a bank or skills: an empty stock
        }
        prices = Crafting.prices(all);
        if (!wantedPrices.isEmpty()) {
            // ask Wynnventory (with the player's own key, when switched on) for prices shown without one
            List<String> ask = new ArrayList<>();
            for (String n : wantedPrices) {
                if (!prices.containsKey(n)) {
                    ask.add(n);
                }
            }
            wantedPrices.clear();
            if (!ask.isEmpty()) {
                GameState.current().requestPrices(ask);
            }
        }
        if (type == null) {
            type = defaultType();
            minLevel = bestBracket(type).minLevel();
        }
    }

    static String defaultType() {
        Klass k;
        try {
            k = GameState.current().classType();
        } catch (Throwable t) {
            k = Klass.NONE;
        }
        return switch (k) {
            case MAGE -> "wand";
            case ARCHER -> "bow";
            case WARRIOR -> "spear";
            case ASSASSIN -> "dagger";
            case SHAMAN -> "relik";
            default -> "ring";
        };
    }

    /** The highest bracket of a type the profession level allows (the first when none does). */
    CraftData.Recipe bestBracket(String t) {
        List<CraftData.Recipe> rs = CraftData.ofType(t);
        CraftData.Recipe best = rs.get(0);
        int lv = level(rs.get(0).profession());
        for (CraftData.Recipe r : rs) {
            if (r.minLevel() <= lv) {
                best = r;
            }
        }
        return best;
    }

    /** The profession level as read, 0 when not known yet. */
    int knownLevel(String profession) {
        Integer l = previewLevels.isEmpty() ? stock.levels().get(profession) : previewLevels.get(profession);
        return l == null ? 0 : l;
    }

    /**
     * The level that gates recipes and ingredients: an unknown level (Wynntils reads it from the
     * character menu) locks nothing rather than everything.
     */
    int level(String profession) {
        int l = knownLevel(profession);
        return l > 0 ? l : 200;
    }

    /** The profession of the recipe on the calculator ("WOODWORKING"), or null before the tab was opened. */
    String profession() {
        return type == null ? null : recipe().profession().toUpperCase(Locale.ROOT);
    }

    CraftData.Recipe recipe() {
        CraftData.Recipe r = CraftData.find(type, minLevel);
        return r != null ? r : CraftData.ofType(type).get(0);
    }

    Ingredient ing(String name) {
        if (name == null) {
            return Ingredient.NONE;
        }
        Ingredient i = byName.get(name);
        return i == null ? Ingredient.NONE : i;
    }

    List<Ingredient> layout() {
        List<Ingredient> out = new ArrayList<>(Craft.SLOTS);
        for (String s : slots) {
            out.add(ing(s));
        }
        return out;
    }

    CraftSearch.Goal goal() {
        int[] caps = CraftSearch.Goal.uncapped();
        if (capToPoints) {
            caps = points.clone();
        }
        return new CraftSearch.Goal(new LinkedHashMap<>(weights), CraftSearch.Pick.MEAN, Set.copyOf(noNegative), caps,
                FLOORS[floorIndex]);
    }

    int owned(String name) {
        Integer n = stock.ingredients().get(name);
        return n == null ? 0 : n;
    }

    /** What the recipe and goal look like now: a new search is needed when this changes. */
    String key() {
        return type + minLevel + tier1 + tier2 + speed + weights + noNegative + capToPoints + floorIndex;
    }

    /** True while a popup (a dropdown or a drawer) owns the mouse. */
    boolean popupOpen() {
        return open != Drop.NONE || drawers.showing();
    }

    /**
     * Draws the page into {@code x, y, w, h}; {@code titleY} is the page title's line (the
     * profession and the saved recipes' link sit on it).
     */
    void draw(GuiGraphics g, int x, int y, int w, int h, int titleY, int[] frame, int screenW, int mx, int my, float pt) {
        acts.clear();
        overlay.clear();
        overlayBox = null;
        pageBar.place(0, 0, 0, 1, 1, 0);               // the bars this frame draws place themselves again
        resultPanel.idBar.place(0, 0, 0, 1, 1, 0);
        preview = nextPreview;
        nextPreview = null;
        refresh();
        // an open popup owns the mouse: the page draws as if the mouse were away
        boolean popup = popupOpen();
        int pmx = popup ? -1 : mx;
        int pmy = popup ? -1 : my;
        headerPanel.drawHeader(g, x, titleY, w, pmx, pmy);
        if (ingredients.isEmpty()) {
            headerPanel.noData(g, x, y, w, h, pmx, pmy);
            return;
        }
        if (!optimiserPanel.resultsFor.equals(key()) && !results.isEmpty()) {
            results = List.of();                    // the recipe or goal changed: old layouts no longer apply
        }
        int bar = headerPanel.viewTabs(g, x, y, w, pmx, pmy) + Theme.SP_4;
        y += bar;
        h -= bar;
        twoColumns = w >= TWO_COLUMNS_MIN;
        if (view == VIEW_SUGGEST) {
            suggestionsPanel.draw(g, x, y, w, h, pmx, pmy);
        } else {
            clip = new int[] {x, y, w, h};
            g.enableScissor(x, y, x + w, y + h);
            int top = y - scroll;
            int cw = w - ScrollBar.W - Theme.SP_2;      // room for the scroll bar
            int used = view == VIEW_ROAD ? roadPanel.draw(g, x, top, cw, y, y + h, pmx, pmy)
                    : CraftTarifPage.draw(this, g, x, top, cw, pmx, pmy);
            g.disableScissor();
            clip = null;
            scrollMax = Math.max(0, used - h);
            scroll = Math.min(scroll, scrollMax);
            pageBar.place(x + w - ScrollBar.W, y, h, h, used, scroll);   // offsets in pixels
            pageBar.draw(g, pmx, pmy);
        }
        if (popup) {
            g.nextStratum();
            dropdowns.drawDrop(g, mx, my, pt);
            drawers.draw(g, x, y - bar, w, h + bar, mx, my);
        }
        CraftInput.showTooltip(this, g, mx, my);
    }

    Craft.Result result() {
        return Craft.craft(recipe(), tier1, tier2, speed, layout());
    }

    /** What the suggestions are worked out for: a new run is needed when this changes. */
    String suggestKey() {
        return stock.ingredients().hashCode() + "|" + stock.levels() + "|" + defaultType() + "|" + CraftSuggestionsPanel.combatLevel();
    }

    void toggle(Drop d, int[] anchor) {
        if (open == d) {
            open = Drop.NONE;
            return;
        }
        open = d;
        dropMenu.reset();
        dropScroll = 0;
        openAnchor = anchor;
        if (d == Drop.LEVEL) {
            List<CraftData.Recipe> rs = CraftData.ofType(type);
            for (int i = 0; i < rs.size(); i++) {
                if (rs.get(i).minLevel() == minLevel) {
                    dropScroll = Math.max(0, i - 4);
                }
            }
        }
    }

    // the preview drives these by name (reflection): they stay here

    private void startSearch() {
        optimiserPanel.startSearch();
    }

    /** For the preview: start working the suggestions out now. */
    private void startSuggestionsNow() {
        suggestionsPanel.startSuggestions(true);
    }

    /** For the preview: start working the sets out now. */
    private void startSetsNow() {
        setsPanel.startSets(true);
    }

    // ---- input ------------------------------------------------------------------------------




    boolean mouseDragged(double mx, double my) {
        return CraftInput.mouseDragged(this, mx, my);
    }

    void mouseReleased() {
        CraftInput.mouseReleased(this);
    }

    List<LayoutCheck.Area> clickAreas() {
        return CraftInput.clickAreas(this);
    }

    boolean mouseClicked(double mx, double my, int button) {
        return CraftInput.mouseClicked(this, mx, my, button);
    }

    boolean mouseScrolled(double mx, double my, double v) {
        return CraftInput.mouseScrolled(this, mx, my, v);
    }

    boolean keyPressed(int key) {
        return CraftInput.keyPressed(this, key);
    }
    boolean typing() {
        return pickSearch != null && (pickSearch.isFocused() || saveName.isFocused() || suggestSearch != null && suggestSearch.isFocused());
    }


    /** An identification's shown name, from Wynntils' data ("Spell Damage"). */
    String statName(String id) {
        for (Ingredient i : ingredients) {
            Ingredient.Stat s = i.ids().get(id);
            if (s != null && s.shown() != null && !s.shown().isEmpty()) {
                return s.shown();
            }
        }
        return id;
    }

    String unit(String id) {
        for (Ingredient i : ingredients) {
            Ingredient.Stat s = i.ids().get(id);
            if (s != null) {
                return s.unit() == null ? "" : s.unit();
            }
        }
        return "";
    }

    /**
     * What buying these copies costs at today's prices (they may have come in since the layout
     * was found); the unknown ones are asked for and shown as "+?".
     */
    String liveCost(List<String> bought) {
        long cost = 0;
        boolean priced = false;
        boolean unknown = false;
        for (String n : bought) {
            CraftSearch.Price p = prices.get(n);
            if (p == null) {
                unknown = true;
                wantedPrices.add(n);
            } else {
                cost += p.emeralds();
                priced = true;
            }
        }
        return CraftFormat.costText(cost, priced, unknown);
    }
}
