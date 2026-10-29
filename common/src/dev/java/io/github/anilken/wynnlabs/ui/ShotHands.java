package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.dev.Peek;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.RecentSearches;
import io.github.anilken.wynnlabs.search.SearchCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dev builds only (common/src/dev, in the menu's package so it reaches the views' package-private
 * state and the same preview hooks the gametests use; checkDevTools keeps it out of release jars).
 * The screenshot tool's hands on the mod's own menu: a fresh menu on a tab, one step towards a view
 * (a sub-page, a drawer, a filter, a menu, a window of the mod), and the shared views put back.
 * Only the menu's view state is set: no game menu opened, no slot clicked, nothing sent, no road map
 * build or build search started. The compiler checks every name here; the few private fields go
 * through {@link Peek.Ref} constants, which ShotNamesTest checks.
 */
public final class ShotHands {

    private ShotHands() {}

    // the private fields reached by reflection (ShotNamesTest checks each one exists)
    static final Peek.Ref TABS = new Peek.Ref(RoadmapScreen.class, "TABS");
    static final Peek.Ref PILL_PLAN = new Peek.Ref(Plans.Pills.class, "planId");
    static final Peek.Ref PILL_PICKED = new Peek.Ref(Plans.Pills.class, "picked");
    static final Peek.Ref WINDOW_UNSEEN = new Peek.Ref(BuildWindow.class, "unseenGen");
    static final Peek.Ref WINDOW_FROM_HINT = new Peek.Ref(BuildWindow.class, "fromHint");
    static final Peek.Ref TREE_SCROLL = new Peek.Ref(TreeTab.class, "scroll");
    static final Peek.Ref ENDGAME_DRAWER = new Peek.Ref(EndgameTab.class, "drawerKind");

    /** Every reflected field, for the test. */
    public static List<Peek.Ref> refs() {
        return List.of(TABS, PILL_PLAN, PILL_PICKED, WINDOW_UNSEEN, WINDOW_FROM_HINT, TREE_SCROLL, ENDGAME_DRAWER);
    }

    /** A link WynnBuilder wrote (data 2.2.5.0): six pieces, a bow with powders, a tome, a level. */
    private static final String IMPORT_LINK = "https://wynnbuilder.github.io/builder/#CZ0W9W5G000uQG0500m454+GhcXYfy-200k-Jn0";

    // ---- the tabs ----

    /** The tabs' numbers in rail order (the settings' entry holds Settings and AI). */
    public static int[] railOrder() {
        return java.util.Arrays.stream(MenuRail.RAIL).flatMapToInt(java.util.Arrays::stream).toArray();
    }

    /** Each tab number's language key ("wynnlabs.screen.tab_home" …). */
    public static String[] tabKeys() {
        return (String[]) TABS.get(null);
    }

    /**
     * A fresh menu on tab {@code t}, every view inside it at its start, that keeps nothing for the
     * next opening (the player's own resume state stays). The quests tab starts on the tracked quest,
     * as the player's menu does.
     */
    public static RoadmapScreen menu(int t) {
        RoadmapScreen s = RoadmapScreen.on(t);
        try {
            if (Wyn.tracking()) {
                s.questsTab.questSelected = Wyn.trackedName();
            }
        } catch (Throwable ignored) {
            // nothing tracked
        }
        return s;
    }

    // ---- the shared views (singletons and statics the menu keeps between openings) ----

    /** The shared views as they are now. */
    public static Object save() {
        BuildWindow w = BuildWindow.INSTANCE;
        EndgameTab e = EndgameTab.INSTANCE;
        CraftingTab c = CraftingTab.INSTANCE;
        Plans.Pills pills = pills();
        int[][] poolScroll = new int[e.poolScroll.length][];
        for (int i = 0; i < poolScroll.length; i++) {
            poolScroll[i] = e.poolScroll[i].clone();
        }
        return new Saved(w.open, w.view, w.editingPlan, w.overwriteId, (int) WINDOW_UNSEEN.get(w), w.awaitGen, w.result, w.card,
                (boolean) WINDOW_FROM_HINT.get(w), new ArrayList<>(w.chosen), w.tuning, w.renameId, w.pick.listScroll, w.pick.type,
                w.pick.range, w.search == null ? null : w.search.getValue(), w.editId, w.editTuning, w.swapSlot,
                e.section, e.view, e.poolTab, e.litRaid, e.litCamp, e.filter, e.listScroll, e.pinned, poolScroll,
                c.view, c.scroll, c.suggestMode, c.roadPanel.profession,
                (String) PILL_PLAN.get(pills), (boolean) PILL_PICKED.get(pills), SearchTab.expanded);
    }

    private record Saved(boolean open, BuildWindow.View view, String editingPlan, String overwriteId, int unseenGen, int awaitGen,
            Around.Result result, int card, boolean fromHint, List<io.github.anilken.wynnlabs.model.ItemData> chosen,
            io.github.anilken.wynnlabs.build.Tuning.Choice tuning, String renameId, int pickScroll, int pickType, int pickRange,
            String search, String editId, io.github.anilken.wynnlabs.build.Tuning.Choice editTuning, Integer swapSlot, int egSection, int egView, int egPoolTab, String litRaid, String litCamp, int egFilter, int egScroll,
            String pinned, int[][] poolScroll, int craftView, int craftScroll, int suggestMode, String profession, String planId,
            boolean picked, boolean expanded) {}

    /** The shared views back as {@link #save} found them, their drawers and menus closed. */
    public static void restore(Object saved) {
        close();
        Saved s = (Saved) saved;
        BuildWindow w = BuildWindow.INSTANCE;
        w.open = s.open();
        w.view = s.view();
        w.editingPlan = s.editingPlan();
        w.overwriteId = s.overwriteId();
        WINDOW_UNSEEN.set(w, s.unseenGen());
        w.awaitGen = s.awaitGen();
        w.result = s.result();
        w.card = s.card();
        WINDOW_FROM_HINT.set(w, s.fromHint());
        w.chosen = new ArrayList<>(s.chosen());
        w.tuning = s.tuning();
        w.renameId = s.renameId();
        w.pick.listScroll = s.pickScroll();
        w.pick.type = s.pickType();
        w.pick.range = s.pickRange();
        w.editId = s.editId();
        w.editTuning = s.editTuning();
        w.swapSlot = s.swapSlot();
        if (w.search != null && s.search() != null) {
            w.search.setValue(s.search());
            w.search.setFocused(false);
        }
        EndgameTab e = EndgameTab.INSTANCE;
        e.section = s.egSection();
        e.view = s.egView();
        e.poolTab = s.egPoolTab();
        e.litRaid = s.litRaid();
        e.litCamp = s.litCamp();
        e.filter = s.egFilter();
        e.listScroll = s.egScroll();
        e.pinned = s.pinned();
        for (int i = 0; i < e.poolScroll.length && i < s.poolScroll().length; i++) {
            System.arraycopy(s.poolScroll()[i], 0, e.poolScroll[i], 0, Math.min(e.poolScroll[i].length, s.poolScroll()[i].length));
        }
        CraftingTab c = CraftingTab.INSTANCE;
        c.view = s.craftView();
        c.scroll = s.craftScroll();
        c.suggestMode = s.suggestMode();
        c.roadPanel.profession = s.profession();
        PILL_PLAN.set(pills(), s.planId());
        PILL_PICKED.set(pills(), s.picked());
        SearchTab.expanded = s.expanded();
    }

    /** Every shared view closed and at its start: the build window, the Endgame drawer, the crafting page, the level pill. */
    public static void close() {
        BuildWindow.INSTANCE.menu = 0;
        if (BuildWindow.INSTANCE.isOpen()) {
            BuildWindow.INSTANCE.close();
        }
        EndgameTab e = EndgameTab.INSTANCE;
        e.previewPools(-1, null);
        e.previewPoolFilter(false);
        e.pinned = null;
        CraftingTab c = CraftingTab.INSTANCE;
        c.drawers.close();
        c.open = CraftingTab.Drop.NONE;
        c.view = CraftingTab.VIEW_RECIPE;
        c.scroll = 0;
        c.suggestMode = 0;
        pills().pickLevel();
        SearchTab.expanded = false;
    }

    private static Plans.Pills pills() {
        return HomeLevels.planPills;
    }

    // ---- the steps ----

    /** One step of a view ({@code "bank:view:list"}) on menu {@code s}; false when there is nothing to show for it. */
    public static boolean act(RoadmapScreen s, String step) {
        String[] p = step.split(":", 3);
        String arg = p.length > 1 ? p[1] : "";
        String more = p.length > 2 ? p[2] : "";
        return switch (p[0]) {
            case "open" -> open(arg);
            case "buildmenu" -> {
                // the build window's Priority menu, under its chip
                if (!arg.equals("lean") || !BuildWindow.INSTANCE.isOpen()) {
                    throw new IllegalArgumentException("unknown build menu " + arg);
                }
                BuildWindow.INSTANCE.menu = BuildWindow.MENU_LEAN;
                BuildWindow.INSTANCE.menuState.reset();
                yield true;
            }
            case "plan" -> {
                if (Plans.current() == null) {
                    yield false;
                }
                pills().pickPlan();
                yield true;
            }
            case "home" -> home(s, arg);
            case "today" -> today(s, arg);
            case "quest" -> quest(s, arg, more);
            case "tree" -> tree(s, arg);
            case "endgame" -> endgame(arg, more);
            case "bank" -> bank(s, arg, more);
            case "craft" -> craft(arg, more);
            case "search" -> search(s, arg, more);
            case "settings" -> settings(s, arg, more);
            case "ai" -> {
                s.ai.openReads();
                yield true;
            }
            default -> throw new IllegalArgumentException("unknown step " + step);
        };
    }

    // the build window (over any tab)

    private static boolean open(String what) {
        BuildWindow w = BuildWindow.INSTANCE;
        switch (what) {
            case "picker" -> w.openPick();
            case "import" -> w.previewImport(IMPORT_LINK);
            case "results" -> {
                Around.Result r = Around.result();
                if (r == null) {
                    return false;               // only a result that was worked out already
                }
                w.openResults(r);
            }
            case "saved" -> {
                if (Plans.saved().isEmpty()) {
                    return false;
                }
                w.openSaved();
            }
            case "edit" -> {
                // the editor of the first saved build
                if (Plans.saved().isEmpty()) {
                    return false;
                }
                w.openEditor(Plans.saved().get(0).id());
            }
            default -> throw new IllegalArgumentException("unknown window " + what);
        }
        if (w.search != null) {
            w.search.setFocused(false);         // no blinking cursor in the pictures
        }
        return true;
    }

    private static boolean home(RoadmapScreen s, String what) {
        switch (what) {
            case "goals" -> s.home.action("#qgoal:", 0);
            case "archetypes" -> s.archPicker.openAt("#qarch:");
            case "options" -> {
                // the slot with the most options on the road map, its comparison panel open
                List<Roadmap.Step> steps = Roadmap.cached();
                int bestStep = -1;
                Roadmap.Gear best = null;
                for (int i = 0; i < steps.size(); i++) {
                    for (Roadmap.Gear g : steps.get(i).gear()) {
                        if (g.options() != null && g.options().size() > 1 && (best == null || g.options().size() > best.options().size())) {
                            best = g;
                            bestStep = i;
                        }
                    }
                }
                if (best == null) {
                    return false;
                }
                s.selected = bestStep;
                s.home.detail = best;
                s.home.detailLabel = best.slot();
            }
            default -> throw new IllegalArgumentException("unknown home step " + what);
        }
        return true;
    }

    private static boolean today(RoadmapScreen s, String what) {
        if (!what.equals("others")) {
            throw new IllegalArgumentException("unknown today step " + what);
        }
        // the "+N" list: only with other characters' days read
        return io.github.anilken.wynnlabs.coach.Today.all(Minecraft.getInstance()).size() > 1 && s.today.action("#qtodayothers");
    }

    private static boolean quest(RoadmapScreen s, String what, String arg) {
        QuestsTab q = s.questsTab;
        if (!Wyn.up(Wyn.Feature.QUESTS) || QuestAdvisor.known() == 0) {
            return false;
        }
        switch (what) {
            case "points" -> {
                // the picked quest's places and its map (the list picks its first row while drawn)
                if (q.questSelected == null || q.pickedType != null) {
                    return false;
                }
                q.action("#qview:3");
            }
            case "menu" -> q.action("#qmenu");
            case "types" -> q.action("#qtypes");
            case "filter" -> {
                List<QuestAdvisor.Suggestion> list;
                try {
                    list = QuestAdvisor.list(arg);
                } catch (Throwable t) {
                    return false;
                }
                if (list.isEmpty()) {
                    return false;
                }
                q.action("#qfilter:" + arg);
                q.questSelected = null;         // the list's first row, as a fresh filter shows it
            }
            case "type" -> {
                ContentType t = ContentType.of(arg);
                boolean any = t == ContentType.SECRET_DISCOVERY ? !q.secrets.rows("").isEmpty() : !QuestsTypes.lines(t).isEmpty();
                if (!any) {
                    return false;
                }
                q.action("#qtype:" + t.name());
            }
            default -> throw new IllegalArgumentException("unknown quest step " + what);
        }
        return true;
    }

    private static boolean tree(RoadmapScreen s, String what) {
        TreeTab t = s.tree;
        switch (what) {
            case "end" -> TREE_SCROLL.set(t, 100_000);
            case "damage", "deaths" -> t.openDrawer(what);
            case "archetypes" -> s.archPicker.openAt("#tree:arch");
            case "search" -> {
                // a word of the tree's first ability: the search's ring and its matches
                List<AbilityNode> nodes = GameState.current().abilityTree();
                String word = nodes == null || nodes.isEmpty() ? null : firstWord(nodes.get(0).name());
                if (word == null) {
                    return false;
                }
                t.slashTyped();
                t.treeSearch.setValue(word);
                unfocus(s, t.treeSearch);
            }
            default -> throw new IllegalArgumentException("unknown tree step " + what);
        }
        return true;
    }

    private static boolean endgame(String what, String arg) {
        EndgameTab e = EndgameTab.INSTANCE;
        switch (what) {
            case "poolfilter" -> e.previewPoolFilter(true);
            case "log" -> e.previewLog();
            case "pools" -> e.previewPools(Integer.parseInt(arg), null);
            case "region" -> {
                // the n-th region of the raids' ("raid:n") or camps' ("camp:n") pools, as the drawer lists them
                String[] r = arg.split(":");
                boolean raid = r[0].equals("raid");
                int n = Integer.parseInt(r[1]);
                if (e.d == null || e.d.v() == null) {
                    return false;
                }
                var regions = e.d.v().regions(raid);
                if (regions.size() < n) {
                    return false;
                }
                e.previewPools(raid ? EndgameTab.TAB_RAIDS : EndgameTab.TAB_CAMPS, regions.get(n - 1).name());
            }
            default -> throw new IllegalArgumentException("unknown endgame step " + what);
        }
        return true;
    }

    /** The first row of the pool drawer drawn last frame, in logical pixels; null when none. */
    public static int[] firstPoolRow() {
        EndgameTab e = EndgameTab.INSTANCE;
        if (e.view != EndgameTab.VIEW_POOLS || e.rowRects.isEmpty()) {
            return null;
        }
        return e.rowRects.values().iterator().next();
    }

    private static boolean bank(RoadmapScreen s, String what, String arg) {
        BankTab b = s.bank;
        BankIndex idx = BankIndex.get();
        if (idx.groups.isEmpty()) {
            return false;                       // the bank never read: its empty page is the main shot
        }
        switch (what) {
            case "view" -> b.previewView(switch (arg) {
                case "pages" -> BankPages.VIEW_PAGES;
                case "list" -> BankPages.VIEW_LIST;
                case "all" -> BankPages.VIEW_ALL;
                default -> throw new IllegalArgumentException("unknown bank layout " + arg);
            });
            case "search" -> {
                String word = null;
                for (String n : itemNames(idx)) {
                    word = firstWord(n);
                    if (word != null) {
                        break;
                    }
                }
                if (word == null) {
                    return false;
                }
                b.search.setValue(word);
                unfocus(s, b.search);
            }
            case "kind" -> b.previewKind(BankIndex.Kind.valueOf(arg));
            case "menu" -> b.previewMenu(switch (arg) {
                case "filter" -> BankMenus.MENU_FILTER;
                case "tier" -> BankMenus.MENU_TIER;
                case "banks" -> BankMenus.MENU_BANKS;
                case "sort" -> BankMenus.MENU_SORT;
                case "view" -> BankMenus.MENU_VIEW;
                default -> throw new IllegalArgumentException("unknown bank menu " + arg);
            });
            case "item" -> {
                List<String> names = itemNames(idx);
                if (names.isEmpty()) {
                    return false;
                }
                b.previewItem(names.get(0));
            }
            case "sell" -> {
                if (idx.junk.isEmpty()) {
                    return false;
                }
                b.previewSell();
            }
            default -> throw new IllegalArgumentException("unknown bank step " + what);
        }
        return true;
    }

    private static List<String> itemNames(BankIndex idx) {
        List<String> out = new ArrayList<>();
        for (BankIndex.Group gr : idx.groups) {
            for (BankIndex.Page p : gr.pages()) {
                for (BankIndex.Item it : p.slots().values()) {
                    out.add(it.st().name());
                }
            }
        }
        return out;
    }

    private static boolean craft(String what, String arg) {
        CraftingTab c = CraftingTab.INSTANCE;
        switch (what) {
            case "end" -> c.scroll = 100_000;
            case "suggestions", "sets" -> {
                // our suggestions only when they were worked out already: never started here
                boolean sets = what.equals("sets");
                if ((sets ? c.sets : c.suggestions).isEmpty()) {
                    return false;
                }
                c.view = CraftingTab.VIEW_SUGGEST;
                c.suggestMode = sets ? 1 : 0;
            }
            case "picker" -> c.drawers.openPicker(0);
            case "shop" -> c.drawers.openShop();
            case "saved" -> {
                if (Crafting.saved().all().isEmpty()) {
                    return false;
                }
                c.drawers.openSaved();
            }
            case "road" -> {
                c.view = CraftingTab.VIEW_ROAD;
                if (!arg.isEmpty()) {
                    c.roadPanel.pickProf(arg);
                }
            }
            default -> throw new IllegalArgumentException("unknown craft step " + what);
        }
        return true;
    }

    /** The professions of the level road, in its order. */
    public static List<String> professions() {
        return Professions.ALL;
    }

    private static boolean search(RoadmapScreen s, String what, String arg) {
        SearchTab t = s.search;
        switch (what) {
            case "more" -> SearchTab.expanded = true;
            case "recent" -> {
                // the newest search again (it stays the newest): its card
                List<String> recent = RecentSearches.get().newest(1);
                if (recent.isEmpty()) {
                    return false;
                }
                t.show(recent.get(0));
            }
            case "menu" -> {
                int which = switch (arg) {
                    case "marks" -> SearchMenus.MARKS;
                    case "tracked" -> SearchMenus.TRACKED;
                    case "recent" -> SearchMenus.RECENT;
                    case "sort" -> SearchMenus.SORT;
                    case "filter" -> SearchMenus.FILTER;
                    default -> throw new IllegalArgumentException("unknown search menu " + arg);
                };
                if (which == SearchMenus.FILTER) {
                    t.pickCategory(SearchCategory.ITEM);
                }
                t.menus.toggle(which);
            }
            case "stats" -> t.pickCategory(SearchCategory.STAT);
            default -> throw new IllegalArgumentException("unknown search step " + what);
        }
        if (t.box != null) {
            unfocus(s, t.box);
        }
        return true;
    }

    private static boolean settings(RoadmapScreen s, String what, String arg) {
        SettingsTab t = s.settings;
        switch (what) {
            case "cat" -> {
                int cat = java.util.Arrays.asList(SettingsRows.SETTING_CATS).indexOf(arg);
                if (cat < 0) {
                    throw new IllegalArgumentException("unknown settings page " + arg);
                }
                t.previewSettings(cat, -1, false);
            }
            case "changed" -> {
                t.previewSettings(0, -1, false);
                t.showChanged = true;
            }
            default -> throw new IllegalArgumentException("unknown settings step " + what);
        }
        return true;
    }

    /** The settings' pages by their number (0 Basics). */
    public static String[] settingPages() {
        return SettingsRows.SETTING_CATS.clone();
    }

    // ---- the mod's own windows over the menu ----

    /**
     * A window of the mod over menu {@code s}, freshly opened as the menu opens it: "update" (the
     * update window), "changelog" (its notes, only with an update), "guide" (the full guide of the
     * tracked quest, else the first suggested one), "box" (the Trade Market window of the newest
     * item searched, else of a piece you wear). Null when there is nothing to show. Opening the box
     * window asks Wynnventory for that one item's listings when live prices are on, as its button does.
     */
    public static Screen window(RoadmapScreen s, String what) {
        switch (what) {
            case "update" -> {
                return new UpdateScreen(s, false);
            }
            case "changelog" -> {
                return UpdateCheck.state().status() == UpdateCheck.Status.NEWER ? new UpdateScreen(s, true) : null;
            }
            case "guide" -> {
                String quest = null;
                String task = "";
                try {
                    if (Wyn.tracking()) {
                        quest = Wyn.trackedName();
                        task = Wyn.trackedTask() == null ? "" : Wyn.trackedTask();
                    }
                } catch (Throwable ignored) {
                    // nothing tracked
                }
                if (quest == null && QuestAdvisor.known() > 0) {
                    List<QuestAdvisor.Suggestion> list = QuestAdvisor.list("RECOMMENDED");
                    quest = list.isEmpty() ? null : list.get(0).quest().name();
                }
                return quest == null ? null : new GuideScreen(quest, task);
            }
            case "box" -> {
                Entry e = boxItem();
                if (e == null) {
                    return null;
                }
                io.github.anilken.wynnlabs.model.Tier rarity;
                try {
                    rarity = io.github.anilken.wynnlabs.model.Tier.valueOf(e.tier().toUpperCase(Locale.ROOT));
                } catch (RuntimeException ex) {
                    rarity = io.github.anilken.wynnlabs.model.Tier.NORMAL;
                }
                var item = new io.github.anilken.wynnlabs.search.BoxMarket.Item(e.name(), io.github.anilken.wynnlabs.model.Slot.UNKNOWN,
                        rarity, e.level(), e.klass(), 0, true);
                return new BoxScreen(new BoxScreen.Single(item, null, false), s.search.icons.of(e), s);
            }
            default -> throw new IllegalArgumentException("unknown window " + what);
        }
    }

    /** The item the box window shows: the newest one searched, else the first piece you wear the item list knows. */
    private static Entry boxItem() {
        ItemIndex index = ItemIndex.get();
        for (String key : RecentSearches.get().newest(5)) {
            Entry e = index.byKey(key);
            if (e != null && e.item()) {
                return e;
            }
        }
        return GameState.current().allGear().map(g -> index.byKey(g.name())).filter(e -> e != null && e.item())
                .findFirst().orElse(null);
    }

    /** A step inside a window opened by {@link #window}: the box window's stats tab ("box:stats"). */
    public static boolean inWindow(Screen w, String step) {
        if (step.equals("box:stats") && w instanceof BoxScreen b) {
            b.tab = 1;
            return true;
        }
        return false;
    }

    // ---- a clean frame ----

    /** No field focused on {@code screen} or in the build window: no blinking cursor, no field drawn as active. */
    public static void settle(Screen screen) {
        BuildWindow w = BuildWindow.INSTANCE;
        CraftingTab c = CraftingTab.INSTANCE;
        for (EditBox b : new EditBox[] {w.search, w.rename, c.pickSearch, c.saveName}) {
            if (b != null) {
                b.setFocused(false);
            }
        }
        for (var child : screen.children()) {
            if (child instanceof EditBox b) {
                b.setFocused(false);
            }
        }
        screen.setFocused(null);
    }

    /** Whether a field of {@code screen} is focused (drawn as active, with its cursor). */
    public static boolean focused(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof EditBox b && b.isFocused()) {
                return true;
            }
        }
        return false;
    }

    /** What is open over the page of {@code screen} in the frame drawn last: popups, lists, drawers, menus, the build window. */
    public static int overlays(Screen screen) {
        if (screen instanceof BoxScreen b) {
            return b.bar.menuOpen() ? 1 : 0;
        }
        if (!(screen instanceof RoadmapScreen s)) {
            return 0;
        }
        int n = s.popupBoxes.size();               // the page's popups and drawers, the archetype list
        BuildWindow w = BuildWindow.INSTANCE;
        if (w.isOpen()) {
            n += 1 + (w.menu != 0 ? 1 : 0);
        }
        if (s.tab() == RoadmapScreen.TAB_ENDGAME && (int) ENDGAME_DRAWER.get(EndgameTab.INSTANCE) != 0) {
            n++;
        }
        if (s.tab() == RoadmapScreen.TAB_CRAFT) {
            CraftingTab c = CraftingTab.INSTANCE;
            n += (c.drawers.showing() ? 1 : 0) + (c.open != CraftingTab.Drop.NONE ? 1 : 0);
        }
        return n;
    }

    // ---- helpers ----

    /** A word of {@code name} that a search finds it by (letters only, four or more), lower case; null when none. */
    static String firstWord(String name) {
        for (String w : name.split(" ")) {
            String clean = w.replaceAll("[^\\p{L}]", "");
            if (clean.length() >= 4) {
                return clean.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** No blinking cursor in the pictures. */
    private static void unfocus(RoadmapScreen s, EditBox b) {
        b.setFocused(false);
        s.setFocused(null);
    }

    /**
     * The box of a part the menu does not report itself (logical pixels), for a tooltip shot's
     * hover; null when not drawn. "hit": the page's first element with a tooltip or an item, as
     * drawn last frame (a stat, an ability, a quest, an ingredient …).
     */
    public static int[] part(RoadmapScreen s, String name) {
        return switch (name) {
            case "slot" -> HomeTab.INSTANCE.firstSlot;
            case "source" -> HomeTab.INSTANCE.firstPill;
            case "poolrow" -> firstPoolRow();
            case "hit" -> firstHit(s);
            default -> null;
        };
    }

    private static int[] firstHit(RoadmapScreen s) {
        for (MenuHit h : List.copyOf(s.hits)) {
            boolean shows = h.stack() != null && !h.stack().isEmpty() || h.tip() != null && !h.tip().isEmpty();
            if (shows && h.w() > 0 && h.h() > 0) {
                return new int[] {h.x(), h.y(), h.w(), h.h()};
            }
        }
        return null;
    }
}
