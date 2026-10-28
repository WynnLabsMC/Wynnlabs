package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.SavedRecipes;
import io.github.anilken.wynnlabs.crafting.SearchRunner;
import io.github.anilken.wynnlabs.crafting.SetSuggestions;
import io.github.anilken.wynnlabs.crafting.SuggestionBoard;
import io.github.anilken.wynnlabs.crafting.Suggestions;
import io.github.anilken.wynnlabs.crafting.Types;
import io.github.anilken.wynnlabs.crafting.WynnBuilderCode;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.anilken.wynnlabs.ui.CraftFormat.range;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * Sets: the goals' sets as a list on the left (a goal's icon, its name, the band and the set's main
 * gain), the picked set on the right as it is: a header with its key totals large, the rest in one
 * quiet line and what stops you wearing it said once; its pieces as calm cards (the model, the main
 * gain and durability, one status, the 2×3 recipe in small icons); a line of what it takes. A piece
 * opens its recipe in a drawer. A small window shows the list first and the set in its place.
 */
final class CraftSetsPanel extends CraftPanel {

    static final int PIECE_H = CraftSuggestionsPanel.CARD_H;
    private static final int PIECE_MIN_W = 220;
    private static final int ROW_H = 22;
    /** A list row's click area (the row and its wash). */
    static final int ROW_LIST_H = ROW_H + 2 * Theme.SP_2;

    private final SearchRunner setRunner = new SearchRunner();
    private CraftSearch.Budget setting;
    /** The band shown (0 = the highest wearable) and the tier. */
    int step;
    CraftSearch.Tier tier = SuggestionBoard.DEFAULT_TIER;
    /** Kept for the drawer's piece; -1 when none. */
    int piece = -1;
    /** On a small window: the set is open in place of the list. */
    boolean open;

    private final ScrollBar listBar = new ScrollBar();
    private int listScroll;
    private int listMax;
    private int[] listBox;

    CraftSetsPanel(CraftingTab tab) {
        super(tab);
    }

    void stop() {
        setRunner.cancel();
        setting = null;
    }

    boolean running() {
        return setting != null && !setting.cancelled();
    }

    /** The weapon of the sets (null: the class's) and the band's first level (0: the player's). */
    String weapon;
    int band;

    String weaponShown() {
        if (weapon != null) {
            return weapon;
        }
        String w = CraftSuggestionsPanel.classWeapon();
        return w == null ? "spear" : w;
    }

    int bandShown() {
        return band > 0 ? band : Suggestions.bracketOrder(CraftSuggestionsPanel.combatLevel()).get(0);
    }

    /**
     * Shows what the cache has for the weapon and band shown; nothing is worked out unless the
     * player presses Calculate.
     */
    void startSets(boolean force) {
        loadCached();
        if (force) {
            calculate(true);
        }
    }

    /** The weapon and band keys already read from the cache into the list. */
    private java.util.Set<String> loaded;
    private String loadedFor;
    /** A calculation running: how many bands of how many, the one being worked out. */
    volatile int calcDone;
    volatile int calcTotal;
    volatile int calcAt;

    /** Reads the cached sets of the weapon and band shown into the list, once each. */
    void loadCached() {
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        String who = io.github.anilken.wynnlabs.core.Profile.id() + "|" + c.data();
        if (loaded == null || !who.equals(loadedFor)) {
            loaded = java.util.concurrent.ConcurrentHashMap.newKeySet();
            loadedFor = who;
            tab.sets.clear();
        }
        String w = weaponShown();
        int lv = bandShown();
        if (c.hasSets(w, lv) && loaded.add(w + "|" + lv)) {
            tab.sets.addAll(c.sets(w, lv, CraftSuggestCache.goals(tab), CraftSuggestCache.ingredients(tab)));
        }
    }

    /** Calculates the band shown ({@code again}: also when it is cached). */
    void calculate(boolean again) {
        run(List.of(bandShown()), again);
    }

    /** Calculates every band of the weapon shown that is not cached, the player's first. */
    void calculateAll() {
        run(Suggestions.bracketOrder(CraftSuggestionsPanel.combatLevel()), false);
    }

    private void run(List<Integer> bands, boolean again) {
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        String w = weaponShown();
        List<Integer> queue = new ArrayList<>();
        for (int lv : bands) {
            if (again || !c.hasSets(w, lv)) {
                queue.add(lv);
            }
        }
        if (queue.isEmpty()) {
            return;
        }
        tab.suggestionsPanel.stopCalculating();
        setRunner.cancel();
        var st = tab.suggestionsPanel.stockForSearch();
        String print = CraftSuggestCache.stock(tab);
        List<Ingredient> all = tab.ingredients;
        Map<String, CraftSearch.Price> pr = new HashMap<>(tab.prices);
        List<Suggestions.GoalSet> goals = setGoalSets();
        calcDone = 0;
        calcTotal = queue.size();
        calcAt = queue.get(0);
        setting = setRunner.run(60 * 60_000, b -> {
            for (int lv : queue) {
                calcAt = lv;
                List<SetSuggestions.CraftSet> found = new ArrayList<>();
                tab.sets.removeIf(x -> x.step() == lv && x.pieces().get(0).type().equals(w));
                SetSuggestions.runBand(w, goals, lv, st, all, pr::get, 120, b, x -> {
                    found.add(x);
                    tab.sets.add(x);
                });
                if (b.cancelled()) {
                    return;
                }
                c.putSets(w, lv, print, System.currentTimeMillis(), found);
                CraftSuggestCache.save();
                if (loaded != null) {
                    loaded.add(w + "|" + lv);
                }
                calcDone++;
            }
            b.cancel();
        });
    }

    void stopCalculating() {
        setRunner.cancel();
    }

    /** Works out one goal or combination for the band shown (a combination added after the band was calculated). */
    void calculateGoal(String key) {
        Suggestions.GoalSet goal = null;
        for (Suggestions.GoalSet g : setGoalSets()) {
            if (g.key().equals(key)) {
                goal = g;
            }
        }
        if (goal == null) {
            return;
        }
        tab.suggestionsPanel.stopCalculating();
        setRunner.cancel();
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        String w = weaponShown();
        int lv = bandShown();
        var st = tab.suggestionsPanel.stockForSearch();
        String print = CraftSuggestCache.stock(tab);
        List<Ingredient> all = tab.ingredients;
        Map<String, CraftSearch.Price> pr = new HashMap<>(tab.prices);
        List<Suggestions.GoalSet> one = List.of(goal);
        calcDone = 0;
        calcTotal = 1;
        calcAt = lv;
        setting = setRunner.run(10 * 60_000, b -> {
            List<SetSuggestions.CraftSet> found = new ArrayList<>();
            SetSuggestions.runBand(w, one, lv, st, all, pr::get, 120, b, x -> {
                found.add(x);
                tab.sets.add(x);
            });
            if (!b.cancelled()) {
                c.addSets(w, lv, print, System.currentTimeMillis(), found);
                CraftSuggestCache.save();
                calcDone = 1;
            }
            b.cancel();
        });
    }

    boolean calculating() {
        return running();
    }

    String progressText() {
        return L.t("wynnlabs.craft.calc_progress", CraftSuggestionsPanel.levelLabel(calcAt), Math.min(calcTotal, calcDone + 1), calcTotal);
    }

    boolean anyBandMissing() {
        io.github.anilken.wynnlabs.crafting.SuggestionCache c = CraftSuggestCache.get(tab);
        String w = weaponShown();
        for (int lv : Suggestions.bracketLevels()) {
            if (!c.hasSets(w, lv)) {
                return true;
            }
        }
        return false;
    }

    /** "Updated 3 h ago" for the band and weapon shown, or that it is not calculated yet. */
    String updatedText() {
        var e = CraftSuggestCache.get(tab).setsEntry(weaponShown(), bandShown());
        return e == null ? L.t("wynnlabs.craft.calc_none_band", CraftSuggestionsPanel.levelLabel(bandShown()), typeName(weaponShown()))
                : CraftSuggestCache.updated(tab, e);
    }

    /** A goal's set for the weapon, band and tier shown; null when none (yet). */
    SetSuggestions.CraftSet of(String goal) {
        String w = weaponShown();
        int lv = bandShown();
        List<SetSuggestions.CraftSet> mine = new ArrayList<>();
        for (SetSuggestions.CraftSet s : tab.sets) {
            if (s.step() == lv && !s.pieces().isEmpty() && s.pieces().get(0).type().equals(w)) {
                mine.add(s);
            }
        }
        return SuggestionBoard.pickSet(mine, goal, lv, tier);
    }

    SetSuggestions.CraftSet current() {
        return of(currentSetGoal());
    }

    /** The goal (or combination) shown in Sets, null for the first with a set. */
    String setGoal;
    /** The goals ticked in the Combine menu. */
    private List<String> combining;

    String currentSetGoal() {
        if (setGoal != null) {
            return setGoal;
        }
        List<String> g = goals();
        return g.isEmpty() ? tab.suggestionsPanel.goalOrder().get(0) : g.get(0);
    }

    /** The combinations listed: the class's presets, then the player's own. */
    List<String> combos() {
        List<String> out = new ArrayList<>();
        boolean caster = CraftSuggestionsPanel.classWeapon() != null
                && (CraftSuggestionsPanel.classWeapon().equals("wand") || CraftSuggestionsPanel.classWeapon().equals("relik"));
        for (List<String> pr : Suggestions.presets(caster, tab.suggestionsPanel.points())) {
            out.add(Suggestions.comboKey(pr));
        }
        for (String k : CraftCombos.load()) {
            if (!out.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    /** Every goal and combination a band's sets are worked out for. */
    List<Suggestions.GoalSet> setGoalSets() {
        List<Suggestions.GoalSet> out = new ArrayList<>(tab.suggestionsPanel.goalSets());
        for (String k : combos()) {
            Suggestions.GoalSet g = Suggestions.combined(Suggestions.parts(k), CraftSuggestionsPanel.combatLevel(), tab.suggestionsPanel.points());
            if (g != null) {
                out.add(g);
            }
        }
        return out;
    }

    /** "Lv 105–107": the set's armour band. */
    static String band(SetSuggestions.CraftSet s) {
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.recipe() != null && Types.armour(pc.type())) {
                return "Lv " + pc.recipe().bracket().replace('-', '–');
            }
        }
        return "";
    }

    String title(SetSuggestions.CraftSet s) {
        return L.t("wynnlabs.craft.set_title", CraftSuggestDetail.goalLabel(s.goal().key()));
    }

    static String goalIcon(String key) {
        return switch (key) {
            case "rawspell" -> "wand";
            case "spell" -> "sparkles";
            case "mana" -> "crystal-ball";
            case "rawmelee", "melee" -> "sword";
            case "attackspeed" -> "clock";
            case "poison" -> "skull";
            case "health" -> "shield";
            case "earth" -> "leaf";
            case "thunder" -> "bolt";
            case "water" -> "droplet";
            case "fire" -> "flame";
            case "air" -> "circles";
            case "speed" -> "shoe";
            case "lifesteal" -> "heart";
            case "loot" -> "gift";
            default -> "trending-up";
        };
    }

    /** The goals that have a set, in the class's order (the combinations are listed apart). */
    private List<String> goals() {
        List<String> out = new ArrayList<>();
        for (String g : tab.suggestionsPanel.goalOrder()) {
            if (of(g) != null) {
                out.add(g);
            }
        }
        return out;
    }

    /** Draws the page in {x, y, w, h}. */
    void page(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        listBox = null;
        listBar.place(0, 0, 0, 1, 1, 0);
        List<String> goals = goals();
        if (goals.isEmpty()) {
            // nothing for this band and weapon (yet): its menus stay, to pick another, and the player starts it
            chips(g, x, y, x + w, mx, my);
            int top = y + Ui.CHIP_H + Theme.SP_4;
            if (running() || CraftSuggestCache.get(tab).hasSets(weaponShown(), bandShown())) {
                Type.MUTED.draw(g, tab.font, L.t(running() ? "wynnlabs.craft.set_working" : "wynnlabs.craft.suggest_none"), x, top, Theme.INK_3);
                return;
            }
            int[] box = UiEmpty.draw(g, tab.font, x, top, w, y + h - top, w < CraftingTab.TWO_COLUMNS_MIN, "sparkles", updatedText(),
                    List.of(L.t("wynnlabs.craft.calc_set_step_1"), L.t("wynnlabs.craft.calc_step_2"), L.t("wynnlabs.craft.calc_step_3")),
                    new UiEmpty.Action("player-play", L.t("wynnlabs.craft.calc_band"), "calc"), null, mx, my);
            if (box != null) {
                tab.acts.add(new Act(box[0], box[1], box[2], box[3], () -> calculate(false), null, null));
            }
            return;
        }
        String cur = currentSetGoal();
        if (!goals.contains(cur) && !combos().contains(cur)) {
            cur = goals.get(0);
            setGoal = cur;
        }
        SetSuggestions.CraftSet s = of(cur);
        if (w >= CraftSuggestionsPanel.SPLIT_MIN) {
            int lw = Math.max(180, Math.min(220, w / 4));
            list(g, goals, cur, x, y, lw, h, mx, my);
            int rx = x + lw + Theme.SP_7;
            Kit.hair(g, x + lw + Theme.SP_7 / 2, y, 1, h, 0x449A855C);
            set(g, s, rx, y, w - lw - Theme.SP_7, h, false, mx, my);
            return;
        }
        if (!open) {
            list(g, goals, cur, x, y, w, h, mx, my);
            return;
        }
        String back = L.t("wynnlabs.craft.sets_back");
        int bw = Ui.iconChipWidth(tab.font, "chevron-left", back, false);
        Ui.iconChip(g, tab.font, "chevron-left", back, false, x, y, bw, false, mx, my);
        tab.acts.add(new Act(x, y, bw, Ui.CHIP_H, () -> open = false, null, null));
        int top = y + Ui.CHIP_H + Theme.SP_4;
        set(g, s, x, top, w, y + h - top, true, mx, my);
    }

    // ---- the list ----

    /**
     * The list's rows, one list: the goals with a set in the class's order, each followed by the
     * combinations that start with it; the combinations not worked out for this band yet last.
     */
    private List<String> rows(List<String> goals) {
        List<String> all = unfiltered(goals);
        List<String> out = new ArrayList<>();
        for (String k : all) {
            if (shownByFilters(k)) {
                out.add(k);
            }
        }
        return out;
    }

    /** A row passes the goal groups, the search (goal, pieces, ingredients), "wearable now" and the status. */
    private boolean shownByFilters(String key) {
        io.github.anilken.wynnlabs.crafting.SuggestionFilter f = tab.suggestionsPanel.filter();
        if (!f.goalMatches(key)) {
            return false;
        }
        SetSuggestions.CraftSet s = of(key);
        List<String> names = new ArrayList<>();
        names.add(CraftSuggestDetail.goalLabel(key));
        if (s == null) {
            return !f.wearable && f.sets == null && io.github.anilken.wynnlabs.crafting.SuggestionFilter.queryMatches(f.query, names);
        }
        if (f.wearable && !io.github.anilken.wynnlabs.crafting.SuggestionFilter.wearable(s.maxRequirements(), tab.points)) {
            return false;
        }
        if (f.sets != null && io.github.anilken.wynnlabs.crafting.SuggestionFilter.setStatus(SuggestionBoard.pieces(s, tab::level)) != f.sets) {
            return false;
        }
        for (SetSuggestions.Piece pc : s.pieces()) {
            names.add(typeName(pc.type()));
            if (pc.layout() != null) {
                for (Ingredient in : pc.layout().slots()) {
                    if (!in.empty()) {
                        names.add(in.name());
                    }
                }
            }
        }
        return io.github.anilken.wynnlabs.crafting.SuggestionFilter.queryMatches(f.query, names);
    }

    private List<String> unfiltered(List<String> goals) {
        List<String> combos = combos();
        List<String> out = new ArrayList<>();
        for (String gk : goals) {
            out.add(gk);
            for (String c : combos) {
                if (Suggestions.parts(c).get(0).equals(gk) && of(c) != null) {
                    out.add(c);
                }
            }
        }
        for (String c : combos) {
            if (!out.contains(c) && of(c) != null) {
                out.add(c);
            }
        }
        for (String c : combos) {
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    private void list(GuiGraphics g, List<String> goals, String cur, int x, int y, int w, int h, int mx, int my) {
        List<String> rows = rows(goals);
        // the head: "Sets 16", and on its right the way to combine goals (never under the last row)
        String add = L.t("wynnlabs.craft.combine");
        int aw = Ui.iconChipWidth(tab.font, "plus", add, true);
        int ax = x + w - aw;
        Ui.iconChip(g, tab.font, "plus", add, true, ax, y, aw, tab.open == CraftingTab.Drop.SET_COMBINE, mx, my);
        combineAnchor = new int[] {ax, y, aw, Ui.CHIP_H};
        tab.acts.add(new Act(ax, y, aw, Ui.CHIP_H, this::openCombine, null, List.of(L.c("wynnlabs.craft.combine_tip"))));
        String head = L.t("wynnlabs.craft.sets");
        int ty = Type.LABEL.yIn(y, Ui.CHIP_H);
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, head, ax - Theme.SP_3 - x), x, ty, Theme.INK_2);
        int nx = x + Type.LABEL.width(tab.font, head) + Theme.SP_3;
        String n = String.valueOf(rows.size());
        if (nx + Type.META.width(tab.font, n) < ax - Theme.SP_3) {
            Type.META.draw(g, tab.font, n, nx, ty + 1, Theme.INK_3);
            int after = nx + Type.META.width(tab.font, n) + Theme.SP_4;
            tab.toolbar.age(g, ax - Theme.SP_4, ty + 1, ax - Theme.SP_4 - after);
        }
        int top = y + Ui.CHIP_H + Theme.SP_3 + Theme.SP_2;
        int bottom = y + h;
        int pitch = ROW_H + 3 * Theme.SP_2;
        // the last row's wash ends SP_2 under it; SP_2 of plain page more before the list's foot
        int used = rows.isEmpty() ? Type.MUTED.lineHeight : rows.size() * pitch - Theme.SP_2;
        boolean bar = used > bottom - top;
        int cw = bar ? w - ScrollBar.W - Theme.SP_2 : w;
        listMax = Math.max(0, used - (bottom - top));
        listScroll = Math.min(listScroll, listMax);
        listBox = new int[] {x, top - Theme.SP_2, w, bottom - top + Theme.SP_2};
        tab.clip = listBox;
        g.enableScissor(x - Theme.SP_2, top - Theme.SP_2, x + w + Theme.SP_2, bottom);
        int ry = top - listScroll;
        if (rows.isEmpty()) {
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.craft.filter_none"), x, ry, Theme.INK_3);
        }
        for (String key : rows) {
            row(g, key, cur, x, ry, cw, top, bottom, mx, my);
            ry += pitch;
        }
        g.disableScissor();
        tab.clip = null;
        if (bar) {
            listBar.place(x + w - ScrollBar.W, top, bottom - top, bottom - top, used, listScroll);
            listBar.draw(g, mx, my);
        }
    }

    /** One row of the list: the goal's icon, its name, the band and the set's main gain ("…" while not calculated). */
    private void row(GuiGraphics g, String key, String cur, int x, int ry, int cw, int top, int bottom, int mx, int my) {
        if (ry + ROW_H < top - Theme.SP_2 || ry > bottom) {
            return;
        }
        SetSuggestions.CraftSet s = of(key);
        boolean picked = key.equals(cur);
        boolean hover = in(mx, my, x, ry - Theme.SP_2, cw, ROW_H + 2 * Theme.SP_2) && my >= top - Theme.SP_2 && my < bottom;
        if (picked) {
            Kit.rowLit(g, x - Theme.SP_2, ry - Theme.SP_2, cw + 2 * Theme.SP_2, ROW_H + 2 * Theme.SP_2);
        }
        Kit.tile(g, x, ry + 2, 18, picked ? 0xFFF6E3A8 : 0xFFF4E9C8, picked ? Theme.GOLD_EDGE : 0x669A855C);
        boolean combo = key.startsWith(Suggestions.COMBO);
        String icon = goalIcon(Suggestions.parts(key).get(0));
        Art.icon(g, icon, x + Ui.iconIn(18, 10), ry + 2 + Ui.iconIn(18, 10), 10, picked ? Theme.INK : Theme.INK_2);
        if (combo) {
            // a combination: its first goal's icon with a small plus in the tile's corner
            Art.icon(g, "plus", x + 18 - 7, ry + 2 + 1, 6, picked ? Theme.GOLD_EDGE : Theme.INK_3);
        }
        int tx = x + 18 + Theme.SP_3;
        String gain = s == null ? "…" : mainGain(s);
        int gw = Type.LABEL.width(tab.font, gain);
        Type.LABEL.drawRight(g, tab.font, gain, x + cw, Type.LABEL.yIn(ry, ROW_H), s == null ? Theme.INK_3 : picked ? Theme.GOOD : Theme.INK_2);
        String name = CraftSuggestDetail.goalLabel(key);
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, name, cw - gw - Theme.SP_3 - (tx - x)), tx, ry,
                hover && !picked ? Theme.LINK : Theme.INK);
        Type.META.draw(g, tab.font, CraftSuggestionsPanel.levelLabel(bandShown()), tx, ry + Type.LABEL.lineHeight, Theme.INK_3);
        List<net.minecraft.network.chat.Component> tip = key.startsWith(Suggestions.COMBO) && CraftCombos.load().contains(key)
                ? List.of(L.c("wynnlabs.craft.combo_remove_tip")) : null;
        clipped(x, ry - Theme.SP_2, cw, ROW_H + 2 * Theme.SP_2, () -> {
            setGoal = key;
            piece = -1;
            open = true;
            tab.suggestionsPanel.resetScroll();
        }, tip == null ? null : () -> {
            CraftCombos.remove(key);
            if (key.equals(setGoal)) {
                setGoal = null;
            }
        }, tip);
    }

    /** The Combine chip's area this frame. */
    private int[] combineAnchor;

    /** Opens (or closes) the Combine menu, nothing ticked yet. */
    void openCombine() {
        if (combineAnchor == null) {
            return;
        }
        combining = new ArrayList<>();
        tab.toggle(CraftingTab.Drop.SET_COMBINE, combineAnchor);
    }

    /** The Combine menu's rows: every goal to tick (two to four), then the action. */
    List<UiMenu.Item> combineItems() {
        List<String> picked = combining == null ? List.of() : combining;
        UiMenu.Items items = new UiMenu.Items();
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.combine_pick")));
        for (String k : tab.suggestionsPanel.goalOrder()) {
            items.add(UiMenu.Item.of(k, null, L.t("wynnlabs.craft.goalset." + k), null, picked.contains(k)));
        }
        // the action stays in view under the scrolling goals; dim until two are ticked
        UiMenu.Item add = UiMenu.Item.of("#add", "plus", L.t("wynnlabs.craft.combine_add", picked.size()), null, false).asLink().pin();
        items.add(picked.size() < 2 || picked.size() > 4 ? add.asDim() : add);
        return items.list;
    }

    /** A row of the Combine menu: a goal ticks or unticks (the menu stays open); the action adds the combination. */
    boolean pickCombine(String id) {
        if (combining == null) {
            combining = new ArrayList<>();
        }
        if (id.equals("#add")) {
            if (combining.size() < 2 || combining.size() > 4) {
                return false;
            }
            String key = CraftCombos.add(List.copyOf(combining));
            if (key != null) {
                setGoal = key;
                piece = -1;
            }
            combining = null;
            return true;
        }
        if (!combining.remove(id) && combining.size() < 4) {
            combining.add(id);
        }
        return false;
    }

    /** A combination not worked out for this band yet: its own Calculate. */
    private void missing(GuiGraphics g, String key, int x, int y, int w, int h, int mx, int my) {
        int[] box = UiEmpty.draw(g, tab.font, x, y, w, h, w < CraftingTab.TWO_COLUMNS_MIN, "hierarchy",
                L.t("wynnlabs.craft.calc_none_combo", CraftSuggestDetail.goalLabel(key)),
                List.of(L.t("wynnlabs.craft.calc_set_step_1"), L.t("wynnlabs.craft.calc_step_2")),
                new UiEmpty.Action("player-play", L.t("wynnlabs.craft.calc_band"), "calc"), null, mx, my);
        if (box != null && !running()) {
            tab.acts.add(new Act(box[0], box[1], box[2], box[3], () -> calculateGoal(key), null, null));
        }
    }

    /** A set's main gain: its first goal identification added up ("+458–562"). */
    private String mainGain(SetSuggestions.CraftSet s) {
        List<Map.Entry<String, Craft.Range>> ids = CraftSuggestDetail.ids(s.totals(), s.goal().goal().weights());
        if (ids.isEmpty()) {
            return "";
        }
        Map.Entry<String, Craft.Range> e = ids.get(0);
        Craft.Range v = e.getValue();
        return (v.min() > 0 ? "+" : "") + range(v, tab.unit(e.getKey()));
    }

    // ---- the set ----

    private void set(GuiGraphics g, SetSuggestions.CraftSet s, int x, int y, int w, int h, boolean small, int mx, int my) {
        if (s == null) {
            int bx = chips(g, x, y, x + w, mx, my);
            missing(g, currentSetGoal(), x, y + Ui.CHIP_H + Theme.SP_4, w, h - Ui.CHIP_H - Theme.SP_4, mx, my);
            return;
        }
        int cy = head(g, s, x, y, w, small, mx, my);
        int footH = small ? 0 : Ui.CHIP_H + Theme.SP_4 + 1;
        int bottom = y + h - footH;
        int cols = w >= 2 * PIECE_MIN_W + Theme.SP_5 ? 2 : 1;
        int rows = (s.pieces().size() + cols - 1) / cols;
        tab.suggestionsPanel.scrolled(g, x, cy, w, bottom - cy, mx, my, (gg, px, top, pw, ctop, cbottom) -> {
            int cw = (pw - (cols - 1) * Theme.SP_5) / cols;
            for (int i = 0; i < s.pieces().size(); i++) {
                SetSuggestions.Piece pc = s.pieces().get(i);
                int col = cols == 2 ? i / rows : 0;
                int row = cols == 2 ? i % rows : i;
                int bx = px + col * (cw + Theme.SP_5);
                int by = top + row * (PIECE_H + Theme.SP_4);
                if (pc.recipe() == null || by + PIECE_H < ctop || by > cbottom) {
                    continue;
                }
                boolean hover = in(mx, my, bx, by, cw, PIECE_H) && my >= ctop && my < cbottom;
                tab.suggestionsPanel.detail.calmCard(gg, s.goal(), typeName(pc.type()), null, pc.recipe(), pc.layout(), bx, by, cw,
                        hover, false);
                if (pc.layout() != null) {
                    clipped(bx, by, cw, PIECE_H, () -> openPiece(s, pc), null, CraftSuggestDetail.ingredientTip(pc.layout()));
                }
            }
            int used = rows * (PIECE_H + Theme.SP_4) - Theme.SP_4;
            if (small) {
                used += Theme.SP_5 + foot(gg, s, px, top + used + Theme.SP_5, pw, mx, my);
            }
            return used;
        });
        if (!small) {
            foot(g, s, x, y + h - Ui.CHIP_H, w, mx, my);
        }
    }

    /** The head: name and band, the actions; the key totals large; the rest in one line; returns the y for the pieces. */
    private int head(GuiGraphics g, SetSuggestions.CraftSet s, int x, int y, int w, boolean small, int mx, int my) {
        Type role = small ? Type.LABEL : Type.HEADING;
        String t = title(s);
        // the actions on the right: codes, links, save
        int right = x + w;
        int ib = Ui.CHIP_H + 2;
        right -= ib;
        Ui.iconButton(g, "copy", right, y, false, mx, my);
        tab.acts.add(new Act(right, y, ib, Ui.CHIP_H, () -> copySetCodes(s), null, List.of(L.c("wynnlabs.craft.set_copy_all"))));
        right -= Theme.SP_2 + ib;
        Ui.iconButton(g, "world", right, y, false, mx, my);
        tab.acts.add(new Act(right, y, ib, Ui.CHIP_H, () -> copyLinks(s), null, List.of(L.c("wynnlabs.craft.set_links_tip"))));
        right -= Theme.SP_2 + ib;
        Ui.iconButton(g, "device-floppy", right, y, true, mx, my);
        tab.acts.add(new Act(right, y, ib, Ui.CHIP_H, () -> saveSet(s), null,
                List.of(L.c("wynnlabs.craft.set_save_all"), L.c("wynnlabs.craft.set_save_tip"))));
        int ty = role.yIn(y, Ui.CHIP_H);
        String tt = role.fit(tab.font, t, right - Theme.SP_4 - x);
        role.draw(g, tab.font, tt, x, ty, Theme.INK);
        int bx = x + role.width(tab.font, tt) + Theme.SP_4;
        chips(g, bx, y, right, mx, my);
        int cy = y + Math.max(Ui.CHIP_H, role.lineHeight) + Theme.SP_4;
        // the key totals, large
        List<Map.Entry<String, Craft.Range>> ids = keyTotals(s);
        Type big = small ? Type.HEADING : Type.TITLE;
        int keys = 0;
        int kx = x;
        List<String> rest = new ArrayList<>();
        String need = needText(s);
        int needW = small || need == null ? 0 : Type.BODY.width(tab.font, need) + Theme.SP_6;
        for (Map.Entry<String, Craft.Range> e : ids) {
            Craft.Range v = e.getValue();
            String val = (v.min() > 0 ? "+" : "") + range(v, tab.unit(e.getKey()));
            String lab = tab.statName(e.getKey());
            int bw = Math.max(big.width(tab.font, val), Type.META.width(tab.font, lab));
            if (keys < (small ? 2 : 3) && (v.max() > 0 || v.max() < 0 && e.getKey().endsWith("SpellCost")) && kx + bw <= x + w - needW) {
                big.draw(g, tab.font, val, kx, cy, Theme.GOOD);
                Type.META.draw(g, tab.font, lab, kx, cy + big.lineHeight, Theme.INK_3);
                kx += bw + Theme.SP_7;
                keys++;
            } else {
                rest.add(lab + " " + val);
            }
        }
        if (needW > 0) {
            Type.BODY.drawRight(g, tab.font, need, x + w, cy + 2, Theme.BAD);
        }
        SetSuggestions.Piece weak = SuggestionBoard.weakest(s);
        if (!small && weak != null) {
            Type.META.drawRight(g, tab.font, L.t("wynnlabs.craft.set_weakest", weak.layout().result().durability().min(),
                    typeName(weak.type())), x + w, cy + 2 + Type.BODY.lineHeight + Theme.SP_1, Theme.INK_3);
        }
        cy += big.lineHeight + Type.META.lineHeight + Theme.SP_3;
        if (small && need != null) {
            rest.add(need);
        }
        if (!rest.isEmpty()) {
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, String.join(" · ", rest), w), x, cy, Theme.INK_3);
            cy += Type.META.lineHeight;
        }
        cy += Theme.SP_4;
        Kit.hair(g, x, cy, w, 1, Theme.LINE);
        return cy + 1 + Theme.SP_5;
    }

    /** One chip for the band and the weapon ("Lv 105–107 · Relik ▾"), its menu both; returns the x after it. */
    private int chips(GuiGraphics g, int bx, int y, int right, int mx, int my) {
        String label = CraftSuggestionsPanel.levelLabel(bandShown()) + " · " + typeName(weaponShown());
        String icon = CraftFormat.typeIcon(weaponShown());
        int cw = Ui.iconChipWidth(tab.font, icon, label, true);
        if (bx + cw >= right - Theme.SP_4) {
            label = CraftSuggestionsPanel.levelLabel(bandShown());
            cw = Ui.iconChipWidth(tab.font, icon, label, true);
        }
        if (bx + cw < right - Theme.SP_4) {
            Ui.iconChip(g, tab.font, icon, label, true, bx, y, cw, tab.open == CraftingTab.Drop.SET_BANDWEAPON, mx, my);
            int[] anchor = {bx, y, cw, Ui.CHIP_H};
            tab.acts.add(new Act(bx, y, cw, Ui.CHIP_H, () -> tab.toggle(CraftingTab.Drop.SET_BANDWEAPON, anchor), null,
                    List.of(L.c("wynnlabs.craft.set_weapon_tip"))));
            bx += cw;
        }
        return bx;
    }

    /**
     * The totals the header shows large: for a combination each part's strongest total first (raw
     * spell, then mana, then the spell cost cut), then the rest; for one goal, its own order.
     */
    private List<Map.Entry<String, Craft.Range>> keyTotals(SetSuggestions.CraftSet s) {
        List<Map.Entry<String, Craft.Range>> all = CraftSuggestDetail.ids(s.totals(), s.goal().goal().weights());
        if (!s.goal().key().startsWith(Suggestions.COMBO)) {
            return all;
        }
        List<Map.Entry<String, Craft.Range>> out = new ArrayList<>();
        for (Suggestions.GoalSet part : Suggestions.goalSets(Suggestions.parts(s.goal().key()), CraftSuggestionsPanel.combatLevel(), null)) {
            Map.Entry<String, Craft.Range> best = null;
            double bestV = 0;
            for (Map.Entry<String, Craft.Range> e : s.totals().entrySet()) {
                Double wt = part.goal().weights().get(e.getKey());
                if (wt == null) {
                    continue;
                }
                double v = wt * e.getValue().mean();
                if (v > bestV) {
                    bestV = v;
                    best = e;
                }
            }
            if (best != null && !out.contains(best)) {
                out.add(best);
            }
        }
        for (Map.Entry<String, Craft.Range> e : all) {
            if (!out.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** What stops the player wearing it, once: "Agility 64 needed · you have 60"; null when nothing does. */
    private String needText(SetSuggestions.CraftSet s) {
        int worst = -1;
        for (int k = 0; k < 5; k++) {
            int gap = s.maxRequirements()[k] - tab.points[k];
            if (s.maxRequirements()[k] > 0 && gap > 0 && (worst < 0 || gap > s.maxRequirements()[worst] - tab.points[worst])) {
                worst = k;
            }
        }
        return worst < 0 ? null : L.t("wynnlabs.craft.set_need_points", CraftFormat.skill(worst), s.maxRequirements()[worst], tab.points[worst]);
    }

    /** The foot: what it takes in one line, and the way to the missing levels; returns its height. */
    private int foot(GuiGraphics g, SetSuggestions.CraftSet s, int x, int y, int w, int mx, int my) {
        Kit.hair(g, x, y - Theme.SP_4, w, 1, 0x809A855C);
        Map<SuggestionBoard.Status, List<SetSuggestions.Piece>> by = SuggestionBoard.pieces(s, tab::level);
        Map<String, Integer> missing = SuggestionBoard.missingLevels(s, tab::level);
        List<String> parts = new ArrayList<>();
        if (!by.get(SuggestionBoard.Status.READY).isEmpty()) {
            parts.add(L.t("wynnlabs.craft.set_ready", by.get(SuggestionBoard.Status.READY).size()));
        }
        if (!s.bought().isEmpty()) {
            String cost = tab.liveCost(s.bought());
            parts.add(cost.equals("?") ? L.n("wynnlabs.craft.to_buy_n", s.bought().size())
                    : L.t("wynnlabs.craft.set_buy_for", s.bought().size(), cost));
        }
        if (!missing.isEmpty()) {
            List<String> profs = new ArrayList<>();
            missing.keySet().forEach(p -> profs.add(Professions.name(p.toUpperCase(Locale.ROOT))));
            parts.add(L.t("wynnlabs.craft.set_need_level", by.get(SuggestionBoard.Status.LEVEL).size(),
                    String.join(", ", profs) + " " + missing.values().iterator().next()));
        }
        int right = x + w;
        if (!missing.isEmpty()) {
            String label = L.t("wynnlabs.craft.view_road");
            int cw = Ui.iconChipWidth(tab.font, "trending-up", label, false);
            right -= cw;
            Ui.iconChip(g, tab.font, "trending-up", label, false, right, y, cw, false, mx, my);
            String prof = missing.keySet().iterator().next().toUpperCase(Locale.ROOT);
            clipOrAct(right, y, cw, () -> {
                tab.roadPanel.pickProf(prof);
                tab.view = CraftingTab.VIEW_ROAD;
                tab.scroll = 0;
            });
            right -= Theme.SP_4;
        }
        Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, String.join(" · ", parts), right - x), x, Type.BODY.yIn(y, Ui.CHIP_H), Theme.INK_2);
        return Ui.CHIP_H;
    }

    private void clipOrAct(int x, int y, int w, Runnable r) {
        if (tab.clip != null) {
            clipped(x, y, w, Ui.CHIP_H, r, null, null);
        } else {
            tab.acts.add(new Act(x, y, w, Ui.CHIP_H, r, null, null));
        }
    }

    private static CraftSuggestDetail.Shown shown(SetSuggestions.CraftSet s, SetSuggestions.Piece pc) {
        return new CraftSuggestDetail.Shown(s.goal(), pc.recipe(), pc.tier1(), pc.tier2(), pc.layout());
    }

    /** A piece's recipe in a drawer: its name the title, "‹ <set>" back. */
    private void openPiece(SetSuggestions.CraftSet s, SetSuggestions.Piece pc) {
        piece = s.pieces().indexOf(pc);
        String name = L.t("wynnlabs.craft.suggest_name", CraftSuggestDetail.goalLabel(s.goal().key()), typeName(pc.type()));
        tab.drawers.openDetail(name, (g, x, y, w, top, bottom, mx, my) -> {
            int h = Math.max(bottom - y, CraftSuggestDetail.MIN_H + Ui.CHIP_H + Theme.SP_3);
            tab.suggestionsPanel.detail.draw(g, shown(s, pc), x, y, w, h, null, null, title(s), tab.drawers::close, false, tab.overlay, mx, my);
            return h;
        });
    }

    // ---- the list's own bar ----

    boolean overList(double mx, double my) {
        return listBox != null && in(mx, my, listBox[0], listBox[1], listBox[2], listBox[3]);
    }

    void scrollList(double v) {
        listScroll = Math.max(0, Math.min(listMax, listScroll - (int) Math.signum(v) * Theme.SP_7 * 2));
    }

    boolean listPressed(double mx, double my) {
        if (listBar.mouseClicked(mx, my)) {
            listScroll = listBar.offset();
            return true;
        }
        return false;
    }

    boolean listDragged(double my) {
        if (listBar.mouseDragged(my)) {
            listScroll = listBar.offset();
            return true;
        }
        return false;
    }

    void listReleased() {
        listBar.mouseReleased();
    }

    // ---- the band menu ----

    /** The band and weapon menu: every band, then every weapon (the class's marked). */
    List<UiMenu.Item> bandWeaponItems() {
        UiMenu.Items items = new UiMenu.Items();
        int cur = bandShown();
        String w = weaponShown();
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_weapon")));
        String cls = CraftSuggestionsPanel.classWeapon();
        for (String t : Types.WEAPONS) {
            items.add(UiMenu.Item.of("w:" + t, CraftFormat.typeIcon(t), typeName(t), t.equals(cls) ? L.t("wynnlabs.craft.your_class") : null,
                    t.equals(w)));
        }
        items.add(UiMenu.Item.heading(L.t("wynnlabs.craft.filter_level")));
        for (int lv : Suggestions.bracketLevels()) {
            items.add(UiMenu.Item.of("b:" + lv, null, CraftSuggestionsPanel.levelLabel(lv),
                    CraftSuggestCache.get(tab).hasSets(w, lv) ? null : "…", lv == cur));
        }
        return items.list;
    }

    void pickBandWeapon(String id) {
        if (id.startsWith("w:")) {
            weapon = id.substring(2);
        } else {
            band = Integer.parseInt(id.substring(2));
        }
        piece = -1;
        loadCached();
    }

    // ---- actions ----

    /** Puts a WynnBuilder crafter link on the clipboard, or says WynnBuilder has no id for the recipe. */
    static void copyLink(CraftingTab tab, String link) {
        if (link == null) {
            tab.flash.accept(L.t("wynnlabs.craft.wb_link_none"));
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(link);
        tab.flash.accept(L.t("wynnlabs.craft.wb_link_copied"));
    }

    private void copyLinks(SetSuggestions.CraftSet s) {
        List<String> lines = new ArrayList<>();
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.layout() != null) {
                String link = WynnBuilderCode.link(CraftSuggestDetail.recipeOf(shown(s, pc)));
                if (link != null) {
                    lines.add(typeName(pc.type()) + ": " + link);
                }
            }
        }
        if (lines.isEmpty()) {
            tab.flash.accept(L.t("wynnlabs.craft.wb_link_none"));
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));
        tab.flash.accept(L.n("wynnlabs.craft.set_links_copied", lines.size()));
    }

    private void copySetCodes(SetSuggestions.CraftSet s) {
        List<String> lines = new ArrayList<>();
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.layout() != null) {
                lines.add(typeName(pc.type()) + ": " + CraftSuggestDetail.code(shown(s, pc)));
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));
        tab.flash.accept(L.n("wynnlabs.craft.set_codes_copied", lines.size()));
    }

    /** Saves every piece as "Raw spell set – Helmet" …; the second ring only once. */
    private void saveSet(SetSuggestions.CraftSet s) {
        SavedRecipes saved = Crafting.saved();
        String goal = CraftSuggestDetail.goalLabel(s.goal().key());
        java.util.Set<String> done = new java.util.HashSet<>();
        int n = 0;
        for (SetSuggestions.Piece pc : s.pieces()) {
            if (pc.layout() == null || !done.add(pc.type())) {
                continue;
            }
            CraftSuggestDetail.saveOne(saved, shown(s, pc), saved.free(L.t("wynnlabs.craft.set_piece_name", goal, typeName(pc.type()))));
            n++;
        }
        Crafting.persist();
        tab.flash.accept(L.n("wynnlabs.craft.set_saved", n));
    }
}
