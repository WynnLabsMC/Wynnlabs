package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.TooltipListener;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestGuides;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The J menu without the game: a dev client loads the recorded Shaman (src/test/resources/fixtures),
 * opens the menu in a test world and saves a screenshot of the now tab and every milestone tab to
 * build/preview. Run with gradle runClientGameTest.
 */
public class JMenuPreview implements FabricClientGameTest {

    /** The one screen to shoot (home, crafting, rail), or null for all of them. */
    private static String part;

    private static boolean on(String p) {
        return part == null || part.equals(p);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String only = System.getProperty("wynnlabs.previewOnly");
        if (only != null && !java.util.Set.of("jmenu", "home", "crafting", "rail", "settings", "quests", "today", "tree", "search", "bank", "endgame").contains(only)) {
            return;
        }
        // jmenu (or none): every shot; home / crafting / rail: only that screen's
        part = only == null || only.equals("jmenu") ? null : only;
        Path out = Path.of(System.getProperty("wynnlabs.preview", "preview"));
        Path fixture = Fixtures.root().resolve(System.getProperty("wynnlabs.previewFixture", "shaman-44"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            Files.createDirectories(out);
            // the user's window (PreviewSize: 3440x1440 unless -PpreviewSize says otherwise) at GUI scale 3
            ctx.getInput().resizeWindow(PreviewSize.WIDTH, PreviewSize.HEIGHT);
            ctx.runOnClient(mc -> mc.options.guiScale().set(3));
            ctx.runOnClient(mc -> {
                FixtureState.load(fixture).install(System.getProperty("wynnlabs.previewLang", "tr"));
                Wyn.clearDown();                // marked down before the recording was in
            });
            SearchPreview.loadIndex();
            // straight on the screen being checked: a filtered run never draws Home first
            int first = part == null || part.equals("home") || part.equals("rail") ? 0 : part.equals("today") ? 1 : part.equals("tree") ? RoadmapScreen.TAB_TREE : part.equals("quests") ? 2
                    : part.equals("settings") ? 5 : part.equals("crafting") ? 10 : part.equals("bank") ? RoadmapScreen.TAB_BANK
                    : part.equals("search") ? RoadmapScreen.TAB_SEARCH : part.equals("endgame") ? RoadmapScreen.TAB_ENDGAME : 0;
            ctx.setScreen(() -> RoadmapScreen.on(first));
            ctx.waitForScreen(RoadmapScreen.class);
            ctx.getInput().setCursorPos(2, 2);      // the window's corner: no tooltip over the menu
            // nothing is built on its own: the gear panel offers to; the button builds it, with
            // its progress inside the panel
            ctx.waitTicks(2);
            if (on("home")) {
                shot(ctx, out, "jmenu-none");
            }
            ctx.runOnClient(mc -> Roadmap.forceRefresh());
            ctx.waitTicks(2);
            if (on("home")) {
                shot(ctx, out, "jmenu-loading");
            }
            ctx.waitFor(mc -> !Roadmap.cached().isEmpty() && !Roadmap.isBuilding(), 20 * 60);
            ctx.waitTicks(20);
            // a setting changed by a click: the old map stays up while a fresh one is built
            ctx.runOnClient(mc -> {
                Setup.setGoal("DAMAGE");
                Roadmap.forceRefresh();
            });
            ctx.waitTicks(2);
            if (on("home")) {
                shot(ctx, out, "jmenu-refreshing");
            }
            ctx.waitFor(mc -> !Roadmap.isBuilding(), 20 * 60);
            ctx.waitTicks(20);
            if (part != null && !part.equals("rail")) {
                partShots(ctx, out);
                return;
            }
            // the goal list, open
            ctx.runOnClient(mc -> flag(((RoadmapScreen) mc.screen).home, "goalOpen", true));
            ctx.waitTicks(3);
            shot(ctx, out, "jmenu-goals");
            ctx.runOnClient(mc -> flag(((RoadmapScreen) mc.screen).home, "goalOpen", false));
            ArchetypePreview.shots(ctx, out);
            // every rail tab in the rail's order (jmenu-tab-<n>: the tab's number, not its place)
            for (int t : java.util.Arrays.stream(MenuRail.RAIL).flatMapToInt(java.util.Arrays::stream).toArray()) {
                ctx.runOnClient(mc -> set(mc.screen, "tab", t));
                ctx.waitTicks(10);
                shot(ctx, out, "jmenu-tab-" + t);
            }
            if (part != null) {
                return;                             // rail: the tabs' headers only
            }
            // the ability tree scrolled to its end: the last page and the ultimates
            ctx.runOnClient(mc -> set(mc.screen, "tab", 9));
            ctx.runOnClient(mc -> set(((RoadmapScreen) mc.screen).tree, "treeScroll", 100000));
            ctx.waitTicks(3);
            shot(ctx, out, "jmenu-tree-end");
            // the settings' rules, with the typed emerald value
            ctx.runOnClient(mc -> set(mc.screen, "tab", 5));
            ctx.runOnClient(mc -> {
                try {
                    Field f = SettingsTab.class.getDeclaredField("SETTING_CATS");
                    f.setAccessible(true);
                    int i = java.util.Arrays.asList((String[]) f.get(null)).indexOf("rules");
                    set(((RoadmapScreen) mc.screen).settings, "settingsCat", Math.max(0, i));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            ctx.waitTicks(3);
            shot(ctx, out, "jmenu-settings-rules");
            // the glow settings: more rows than two columns hold, so the list scrolls
            for (int scroll : new int[] {0, 100000}) {
                ctx.runOnClient(mc -> {
                    try {
                        Field f = SettingsTab.class.getDeclaredField("SETTING_CATS");
                        f.setAccessible(true);
                        int i = java.util.Arrays.asList((String[]) f.get(null)).indexOf("glow");
                        set(((RoadmapScreen) mc.screen).settings, "settingsCat", Math.max(0, i));
                        set(((RoadmapScreen) mc.screen).settings, "settingsScroll", scroll);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                });
                ctx.waitTicks(3);
                shot(ctx, out, scroll == 0 ? "jmenu-settings-glow" : "jmenu-settings-glow-end");
            }
            questGuides(ctx, out);
            ctx.setScreen(() -> RoadmapScreen.on(10));
            ctx.waitForScreen(RoadmapScreen.class);
            try {
                craftShots(ctx, out);
                CraftPreview.planShots(ctx, n -> shot(ctx, out, n));
            } catch (RuntimeException e) {
                // the crafting tab without its ingredient sample has no grid to click: its shots are skipped, not the rest
                System.out.println("[preview] crafting shots skipped: " + e);
            }
            SearchPreview.shots(ctx, out);
            // the bag's advice words and where-to-sell icons, on the recorded inventory
            ctx.setScreen(BagPreview::new);
            ctx.waitTicks(5);
            shot(ctx, out, "bag-badges");
            ctx.setScreen(RoadmapScreen::new);
            ctx.waitForScreen(RoadmapScreen.class);
            ctx.runOnClient(mc -> set(mc.screen, "tab", 0));
            ctx.waitTicks(3);
            int steps = ctx.computeOnClient(mc -> Roadmap.cached().size());
            for (int i = 0; i < steps; i++) {
                int sel = i;
                ctx.runOnClient(mc -> set(mc.screen, "selected", sel));
                ctx.waitTicks(3);
                shot(ctx, out, "jmenu-" + (i == 0 ? "now" : "step" + i));
            }
            optionShots(ctx, out);
            try {
                craftScales(ctx, out);
            } catch (RuntimeException e) {
                System.out.println("[preview] crafting scale shots skipped: " + e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FixtureState.uninstall();
        }
    }

    /**
     * -PpreviewOnly=settings: the settings' pages (a category with a row picked, the rules, prices
     * and data, the look, About), a search and the changed settings.
     */
    private static void settingsShots(ClientGameTestContext ctx, Path out) throws Exception {
        ctx.runOnClient(mc -> set(mc.screen, "tab", RoadmapScreen.TAB_SETTINGS));
        int[][] pages = {{3, 1}, {7, 1}, {8, 1}, {1, 2}, {0, 0}};
        String[] names = {"glow", "rules", "data", "look", "about"};
        for (int i = 0; i < pages.length; i++) {
            int[] p = pages[i];
            ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).settings.previewSettings(p[0], p[1], false));
            ctx.waitTicks(3);
            shot(ctx, out, "settings-" + names[i]);
        }
        ctx.runOnClient(mc -> {
            Field f = SettingsTab.class.getDeclaredField("searchBox");
            f.setAccessible(true);
            ((net.minecraft.client.gui.components.EditBox) f.get(((RoadmapScreen) mc.screen).settings)).setValue("parla");
        });
        ctx.waitTicks(3);
        shot(ctx, out, "settings-search");
        ctx.runOnClient(mc -> {
            ((RoadmapScreen) mc.screen).settings.previewSettings(3, -1, false);
            ((RoadmapScreen) mc.screen).settings.showChanged = true;
        });
        ctx.waitTicks(3);
        shot(ctx, out, "settings-changed");
        // the goal row's dropdown, open under its chip
        ctx.runOnClient(mc -> {
            ((RoadmapScreen) mc.screen).settings.showChanged = false;
            ((RoadmapScreen) mc.screen).settings.previewSettings(6, 3, false);
        });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> {
            for (Object[] t : ((RoadmapScreen) mc.screen).toggles) {
                if ("#goal".equals(t[1])) {
                    Field f = SettingsGoalMenu.class.getDeclaredField("at");
                    f.setAccessible(true);
                    f.set(((RoadmapScreen) mc.screen).settings.goalMenu, t[0]);
                }
            }
        });
        ctx.waitTicks(3);
        shot(ctx, out, "settings-goal-menu");
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).settings.closePopup());
    }

    /**
     * -PpreviewOnly=today: the Today tab with two other characters' days and a last session written
     * where the game keeps them (the preview's own config folder), so it is not empty.
     */
    private static void todayShots(ClientGameTestContext ctx, Path out) throws Exception {
        String date = io.github.anilken.wynnlabs.coach.Today.utcDate();
        long now = System.currentTimeMillis();
        java.util.function.BiFunction<int[], Integer, java.util.List<io.github.anilken.wynnlabs.coach.Today.Objective>> objs = (s, n) ->
                java.util.List.of(new io.github.anilken.wynnlabs.coach.Today.Objective("Slay 100 mobs", s[0], 100),
                        new io.github.anilken.wynnlabs.coach.Today.Objective("Gather 64 resources", s[1], 64),
                        new io.github.anilken.wynnlabs.coach.Today.Objective("Finish a dungeon", s[2], 1));
        // this character and 14 others: some done, some partly, some not played for a week
        java.util.List<io.github.anilken.wynnlabs.coach.Today.Day> days = new java.util.ArrayList<>();
        days.add(new io.github.anilken.wynnlabs.coach.Today.Day("me", "SHAMAN", 44, date, objs.apply(new int[] {40, 64, 0}, 0), true, 2, now));
        String[] cls = {"MAGE", "ARCHER", "WARRIOR", "ASSASSIN", "SHAMAN"};
        int[][] alts = {{71, 3, 5}, {102, 1, 20}, {88, 0, 30}, {64, 2, 3}, {106, 3, 50}, {33, 0, 70}, {12, 0, 150}, {105, 3, 9},
            {97, 1, 26}, {21, -1, 400}, {106, 2, 12}, {54, 0, 200}, {77, 3, 40}, {8, -1, 900}};
        for (int k = 0; k < alts.length; k++) {
            int[] a = alts[k];
            int[] sc = a[1] < 0 ? new int[] {0, 0, 0} : new int[] {a[1] >= 1 ? 100 : 30, a[1] >= 2 ? 64 : 10, a[1] >= 3 ? 1 : 0};
            String d = a[2] > 48 ? java.time.LocalDate.parse(date).minusDays(a[2] / 24).toString() : date;
            days.add(new io.github.anilken.wynnlabs.coach.Today.Day("alt" + k, cls[k % cls.length], a[0], d, a[1] < 0 ? java.util.List.of()
                    : objs.apply(sc, 0), null, 0, now - a[2] * 3_600_000L));
        }
        Path me = io.github.anilken.wynnlabs.core.Profile.dir();
        Files.createDirectories(me);
        Files.writeString(me.resolve("last-session.json"), "{\"start\":" + (now - 26 * 3_600_000L) + ",\"end\":" + (now - 24 * 3_600_000L)
                + ",\"levels\":0.62,\"xp\":184000,\"emeralds\":2350,\"deaths\":1}");
        ctx.runOnClient(mc -> {
            io.github.anilken.wynnlabs.coach.Today.previewDays = days;
            set(mc.screen, "tab", 1);
        });
        ctx.waitTicks(70);                         // the tab reads its files every three seconds
        shot(ctx, out, "today-now");
        // the "+N" chip's list of every character
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).today.action("#qtodayothers"));
        ctx.waitTicks(3);
        shot(ctx, out, "today-others");
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).today.closePopup());
        // nothing read today: the empty state
        ctx.runOnClient(mc -> io.github.anilken.wynnlabs.coach.Today.previewDays = java.util.List.of());
        ctx.waitTicks(50);                         // the days are read again every two seconds
        shot(ctx, out, "today-empty");
        // the tree's rail entry with its new strip: Ağaç · Hasar · Ölümler, on Hasar
        ctx.runOnClient(mc -> {
            io.github.anilken.wynnlabs.coach.Today.previewDays = null;
            set(mc.screen, "tab", RoadmapScreen.TAB_DAMAGE);
        });
        ctx.waitTicks(5);
        shot(ctx, out, "today-tree-strip");
    }

    /**
     * -PpreviewOnly=tree: the tree page with three recorded deaths, then its damage drawer and its
     * deaths drawer open.
     */
    private static void treeShots(ClientGameTestContext ctx, Path out) throws Exception {
        long now = System.currentTimeMillis();
        ctx.runOnClient(mc -> io.github.anilken.wynnlabs.coach.DeathCoach.preview(List.of(
                new io.github.anilken.wynnlabs.coach.DeathCoach.Death(now - 3 * 86_400_000L, -760, 79, -666, 41, "Jungle", false, 42, 3300, 1800,
                        9.0, 4, List.of("Jungle Spider", "Cobra"), List.of()),
                new io.github.anilken.wynnlabs.coach.DeathCoach.Death(now - 86_400_000L, -1297, 71, -4776, 43, "Leadin Fortress", false, 45, 3100,
                        1880, 6.1, 0, List.of("Orc Warrior"), List.of()),
                new io.github.anilken.wynnlabs.coach.DeathCoach.Death(now - 3_600_000L, 1019, 21, -1991, 44, "Almuj Emerald Labyrinth", false, 48,
                        2900, 1927, 3.4, 2, List.of("Guard Golem", "Emerald Sentry"), List.of()))));
        ctx.runOnClient(mc -> set(mc.screen, "tab", RoadmapScreen.TAB_TREE));
        ctx.waitTicks(40);                         // the deaths are read again every second
        shot(ctx, out, "tree-page");
        ctx.runOnClient(mc -> set(mc.screen, "tab", RoadmapScreen.TAB_DAMAGE));      // a link's target: the drawer opens
        ctx.waitTicks(5);
        shot(ctx, out, "tree-drawer-damage");
        ctx.runOnClient(mc -> {
            ((RoadmapScreen) mc.screen).tree.closePopup();
            set(mc.screen, "tab", 3);
        });
        ctx.waitTicks(5);
        shot(ctx, out, "tree-drawer-deaths");
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).tree.closePopup());
        // the search ("/"), typed in: the gold ring and the ✕; compact, it opens under the title row
        ctx.runOnClient(mc -> {
            RoadmapScreen s = (RoadmapScreen) mc.screen;
            s.tree.slashTyped();
            s.tree.treeSearch.setValue("Totem");
        });
        ctx.waitTicks(5);
        shot(ctx, out, "tree-search");
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).tree.treeSearch.setValue(""));
    }

    /**
     * A quests pane drawn once more with the layout check on: no text over other text (the foot's
     * buttons over a pane's last lines, the small window's overflow). Fails the run when found.
     */
    private static void paneLayout(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> LayoutCheck.start());
        ctx.waitTicks(1);
        List<LayoutCheck.Drawn> drawn = ctx.computeOnClient(mc -> LayoutCheck.stop());
        List<String> clash = LayoutCheck.textOverlaps(drawn);
        if (!clash.isEmpty()) {
            throw new AssertionError("[wynnlabs-layout] " + name + " at " + PreviewSize.WIDTH + "x" + PreviewSize.HEIGHT + ": " + clash);
        }
        System.out.println("[wynnlabs-layout] " + name + " at " + PreviewSize.WIDTH + "x" + PreviewSize.HEIGHT + ": no text over text");
    }

    /** -PpreviewOnly=endgame: the Endgame page with its preview data, each drawer open (a raid's pool, a camp's, the log), a picked aspect, the empty state. */
    private static void endgameShots(ClientGameTestContext ctx, Path out) throws Exception {
        ctx.runOnClient(mc -> {
            EndgameTab.preview();
            set(mc.screen, "tab", RoadmapScreen.TAB_ENDGAME);
        });
        ctx.waitTicks(10);
        shot(ctx, out, "endgame-page");
        ctx.runOnClient(mc -> EndgameTab.INSTANCE.previewPools(EndgameTab.TAB_RAIDS, null));
        ctx.waitTicks(5);
        shot(ctx, out, "endgame-drawer-raid");
        ctx.runOnClient(mc -> EndgameTab.INSTANCE.previewPools(EndgameTab.TAB_CAMPS, null));
        ctx.waitTicks(5);
        shot(ctx, out, "endgame-drawer-camp");
        ctx.runOnClient(mc -> {
            EndgameTab.INSTANCE.previewPools(-1, null);
            EndgameTab.INSTANCE.previewLog();
        });
        ctx.waitTicks(5);
        shot(ctx, out, "endgame-drawer-log");
        ctx.runOnClient(mc -> {
            EndgameTab.INSTANCE.previewPools(-1, null);
            EndgameTab.INSTANCE.previewSection(EndgameTab.SEC_ASPECTS);
            EndgameTab.INSTANCE.previewPick("Aspect of Damnation");
        });
        ctx.waitTicks(5);
        shot(ctx, out, "endgame-aspect-picked");
        ctx.runOnClient(mc -> {
            EndgameTab.INSTANCE.previewPick(null);
            EndgameTab.INSTANCE.previewSection(EndgameTab.SEC_RAIDS);
            EndgameTab.previewEmpty();
        });
        ctx.waitTicks(5);
        shot(ctx, out, "endgame-empty");
        ctx.runOnClient(mc -> EndgameTab.preview());
    }

    /** The tests' excerpt of the wiki's Mini-Quest page (the run's folder is fabric/run or fabric). */
    private static String miniPage() {
        for (String dir : new String[] {"src/test/resources/wiki/mini-quest.txt", "../src/test/resources/wiki/mini-quest.txt"}) {
            try {
                return java.nio.file.Files.readString(java.nio.file.Path.of(dir));
            } catch (java.io.IOException ignored) {
                // the other folder
            }
        }
        return null;
    }

    /** -PpreviewOnly=search or bank: the page's search field empty, then typed in. */
    private static void searchFieldShots(ClientGameTestContext ctx, Path out, String name, int tab, String typed) {
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).openTab(tab));
        ctx.waitTicks(10);
        shot(ctx, out, name);
        ctx.runOnClient(mc -> {
            RoadmapScreen s = (RoadmapScreen) mc.screen;
            s.page(tab).slashTyped();
            (tab == RoadmapScreen.TAB_BANK ? s.bank.search : s.search.box).setValue(typed);
        });
        ctx.waitTicks(40);
        shot(ctx, out, name + "-typed");
    }

    /** -PpreviewOnly=home or crafting: that screen's shots alone, after the road map is built. */
    private static void partShots(ClientGameTestContext ctx, Path out) throws Exception {
        if (part.equals("endgame")) {
            endgameShots(ctx, out);
            return;
        }
        if (part.equals("search") || part.equals("bank")) {
            boolean bank = part.equals("bank");
            if (bank) {
                var fx = FixtureState.load(Fixtures.root().resolve(System.getProperty("wynnlabs.previewFixture", "shaman-44")));
                ctx.runOnClient(mc -> io.github.anilken.wynnlabs.market.Vault.preview(fx.bankStacks()));
            } else {
                ctx.waitFor(mc -> io.github.anilken.wynnlabs.search.ItemIndex.get().missing() == null, 20 * 60);
            }
            searchFieldShots(ctx, out, bank ? "bank-tab" : "search-tab", bank ? RoadmapScreen.TAB_BANK : RoadmapScreen.TAB_SEARCH,
                    bank ? "Emerald" : "lucky rab");
            return;
        }
        if (part.equals("tree")) {
            treeShots(ctx, out);
            return;
        }
        if (part.equals("today")) {
            todayShots(ctx, out);
            return;
        }
        if (part.equals("settings")) {
            settingsShots(ctx, out);
            return;
        }
        if (part.equals("quests")) {
            // -PpreviewOnly=quests: the quests tab on the recorded quest book (the fixture's quests put
            // in as if read from the content book, Jungle Fever started)
            java.util.List<QuestBook.Entry> book = FixtureState.load(Fixtures.root().resolve(
                    System.getProperty("wynnlabs.previewFixture", "shaman-44"))).quests();
            ctx.runOnClient(mc -> {
                for (QuestBook.Entry e : book) {
                    QuestBook.put(e);
                }
                QuestBook.put(new QuestBook.Entry("Jungle Fever", "STARTED", 48, "Medium", 0, true,
                        "Infiltrate Almuj's Secret Emerald Labyrinth at [1019, 84, -1976] underground and find the Banker",
                        1019, 84, -1976, null, System.currentTimeMillis(), "QUEST", "Medium"));
                set(mc.screen, "tab", 2);
                // the wiki's Mini-Quest table (the tests' excerpt) as if the page had been read; the bank, for what you hold
                io.github.anilken.wynnlabs.quests.MiniQuests.preview(miniPage());
                io.github.anilken.wynnlabs.market.Vault.preview(FixtureState.load(Fixtures.root().resolve(
                        System.getProperty("wynnlabs.previewFixture", "shaman-44"))).bankStacks());
            });
            // the guide of the quest you are on, its places, a quest not started, the order menu, nothing read
            String[][] views = {{"Jungle Fever", "0", "quests-tab"}, {"Jungle Fever", "3", "quests-points"},
                {"Underice", "0", "quests-underice"}, {"Underice", "3", "quests-underice-points"},
                {"Slay Creatures of Nesaak Forest", "0", "quests-mini-open"}, {"Slay Idols", "0", "quests-mini-locked"},
                {"Gather Copper", "0", "quests-mini-gather"}, {"Slay Slimes", "0", "quests-mini-have"}};
            for (String[] v : views) {
                ctx.runOnClient(mc -> {
                    QuestsTab q = ((RoadmapScreen) mc.screen).questsTab;
                    q.questSelected = v[0];
                    q.questDetailTab = Integer.parseInt(v[1]);
                    q.filterOpen = false;
                });
                ctx.waitTicks(10);
                shot(ctx, out, v[2]);
                paneLayout(ctx, v[2]);
            }
            // the tab's draw time with the caches off (every value worked out each frame) and on
            for (String[] v : new String[][] {{"Jungle Fever", "0"}, {"Jungle Fever", "3"}}) {
                for (boolean cached : new boolean[] {false, true}) {
                    ctx.runOnClient(mc -> {
                        QuestsTab q = ((RoadmapScreen) mc.screen).questsTab;
                        q.questSelected = v[0];
                        q.questDetailTab = Integer.parseInt(v[1]);
                        QuestsCache.measure(cached);
                    });
                    ctx.waitTicks(60);
                    ctx.runOnClient(mc -> System.out.printf("[wynnlabs-perf] quests %s view=%s cached=%s: %d frames, %.3f ms a frame%n",
                            v[0], v[1], cached, QuestsCache.frames, QuestsCache.nanos / 1e6 / Math.max(1, QuestsCache.frames)));
                }
            }
            ctx.runOnClient(mc -> QuestsCache.measure(true));
            // the content kinds: the kind menu, a cave on its stageless page (places from a small sample)
            ctx.runOnClient(mc -> {
                io.github.anilken.wynnlabs.quests.Content.preview(PREVIEW_CAVES, PREVIEW_FEATURES);
                QuestsTab q = ((RoadmapScreen) mc.screen).questsTab;
                q.questDetailTab = 0;
                q.typeOpen = true;
            });
            ctx.waitTicks(3);
            shot(ctx, out, "quests-types");
            ctx.runOnClient(mc -> {
                QuestsTab q = ((RoadmapScreen) mc.screen).questsTab;
                q.typeOpen = false;
                q.action("#qtype:CAVE");
                q.action("#qcsel:CAVE\nWolf Den");
            });
            ctx.waitTicks(10);
            shot(ctx, out, "quests-cave");
            paneLayout(ctx, "quests-cave");
            // the secret discoveries as a kind: their list and a guide with its steps
            ctx.runOnClient(mc -> {
                QuestsTab q = ((RoadmapScreen) mc.screen).questsTab;
                q.action("#qtype:SECRET_DISCOVERY");
                q.action("#qcsel:SECRET_DISCOVERY\nNilrem's Sealed Letter");
            });
            ctx.waitTicks(10);
            shot(ctx, out, "quests-secret");
            paneLayout(ctx, "quests-secret");
            ctx.runOnClient(mc -> {
                QuestsTab q = ((RoadmapScreen) mc.screen).questsTab;
                q.action("#qtype:RECOMMENDED");
            });
            ctx.waitTicks(5);
            shot(ctx, out, "quests-recommended");
            ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).questsTab.filterOpen = true);
            ctx.waitTicks(3);
            shot(ctx, out, "quests-menu");
            ctx.runOnClient(mc -> {
                ((RoadmapScreen) mc.screen).questsTab.filterOpen = false;
                QuestBook.previewHidden(true);
            });
            ctx.waitTicks(3);
            shot(ctx, out, "quests-empty");
            ctx.runOnClient(mc -> QuestBook.previewHidden(false));
            return;
        }
        if (part.equals("home")) {
            ctx.runOnClient(mc -> set(mc.screen, "tab", 0));
            ctx.waitTicks(3);
            shot(ctx, out, "jmenu-now");
            // the road map a day old after a level-up: the out-of-date notice over the gear
            ctx.runOnClient(mc -> {
                int lv = io.github.anilken.wynnlabs.game.GameState.current().level();
                HomeTab.previewRoad = new HomeTab.Road(false, false, 1, new Roadmap.Stale(true, lv + 1, lv, 26 * 3_600_000L));
            });
            ctx.waitTicks(3);
            shot(ctx, out, "jmenu-stale");
            ctx.runOnClient(mc -> HomeTab.previewRoad = null);
            // the shared dropdowns on the home: the goal list, the archetype list
            ctx.runOnClient(mc -> flag(((RoadmapScreen) mc.screen).home, "goalOpen", true));
            ctx.waitTicks(3);
            shot(ctx, out, "jmenu-goals");
            ctx.runOnClient(mc -> flag(((RoadmapScreen) mc.screen).home, "goalOpen", false));
            ctx.waitTicks(2);                          // a frame without the popup: the chip's click area is back
            ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).archPicker.openAt("#qarch:"));
            ctx.waitTicks(3);
            shot(ctx, out, "jmenu-archetypes");
            ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).archPicker.close());
            optionShots(ctx, out);
            return;
        }
        ctx.setScreen(() -> RoadmapScreen.on(10));
        ctx.waitForScreen(RoadmapScreen.class);
        try {
            craftShots(ctx, out);
            CraftPreview.planShots(ctx, n -> shot(ctx, out, n));
        } catch (RuntimeException e) {
            System.out.println("[preview] crafting shots skipped: " + e);
        }
    }

    /** The crafting tab with a sample of real ingredients: a weapon, the picker, results, an accessory, a potion. */
    private static void craftShots(ClientGameTestContext ctx, Path out) throws Exception {
        ctx.runOnClient(mc -> {
            CraftingTab.previewIngredients = CraftPreview.ingredients();
            CraftPreview.levels();
            set(mc.screen, "tab", 10);
        });
        ctx.getInput().setCursorPos(2, 2);
        ctx.runOnClient(mc -> CraftPreview.recipe("relik", 103, 3, 2, "Foul Fairy Dust", "Obelisk Core", "Dizzying Void",
                "Depurinated Genome", "Exquisite Marsh Scale", "Gunkfueled Core"));
        ctx.waitTicks(5);
        shot(ctx, out, "craft-weapon");
        // the picker on slot 3, a meta ingredient hovered: the grid previews it
        ctx.runOnClient(mc -> CraftPreview.openSlot(2));
        ctx.waitTicks(3);
        List<int[]> rows = ctx.computeOnClient(mc -> {
            try {
                return CraftPreview.rects("overlay");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        CraftPreview.hover(ctx, rows.get(Math.min(1, rows.size() - 1)));
        ctx.waitTicks(3);
        shot(ctx, out, "craft-picker");
        ctx.getInput().setCursorPos(2, 2);
        ctx.runOnClient(mc -> CraftPreview.set(CraftingTab.INSTANCE, "open",
                CraftPreview.get(CraftingTab.INSTANCE, "open").getClass().getEnumConstants()[0]));
        // the optimiser's three layouts
        ctx.runOnClient(mc -> CraftPreview.call("startSearch"));
        ctx.waitFor(mc -> {
            try {
                return !((List<?>) CraftPreview.get(CraftingTab.INSTANCE, "results")).isEmpty();
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }, 20 * 30);
        ctx.waitTicks(3);
        shot(ctx, out, "craft-results");
        // many goal chips: they wrap instead of running under the search button
        ctx.runOnClient(mc -> {
            @SuppressWarnings("unchecked")
            var w = (java.util.Map<String, Double>) CraftPreview.get(CraftingTab.INSTANCE, "weights");
            w.put("healthRegen", 0.5);
            w.put("healthRegenRaw", 0.05);
            w.put("rawHealth", 0.02);
            w.put("manaRegen", 2.0);
        });
        ctx.waitTicks(3);
        shot(ctx, out, "craft-goals-wrap");
        ctx.runOnClient(mc -> {
            @SuppressWarnings("unchecked")
            var w = (java.util.Map<String, Double>) CraftPreview.get(CraftingTab.INSTANCE, "weights");
            w.clear();
            w.put("spellDamage", 1.0);
        });
        // the bottom: roll chances, shopping, what can I craft
        ctx.runOnClient(mc -> {
            CraftPreview.set(CraftingTab.INSTANCE, "foldWhat", true);
            CraftPreview.call("startWhat");
        });
        ctx.waitTicks(60);
        ctx.runOnClient(mc -> CraftPreview.set(CraftingTab.INSTANCE, "scroll", 100000));
        ctx.waitTicks(3);
        shot(ctx, out, "craft-bottom");
        ctx.runOnClient(mc -> CraftPreview.recipe("ring", 80, 2, 3, "Major's Badge", "Ancient Currency", null,
                null, null, null));
        ctx.waitTicks(3);
        shot(ctx, out, "craft-accessory");
        ctx.runOnClient(mc -> CraftPreview.recipe("potion", 43, 1, 3, "Bat Heart", "Black Sulphur"));
        ctx.waitTicks(3);
        shot(ctx, out, "craft-consumable");
        ctx.runOnClient(mc -> CraftPreview.recipe("potion", 43, 1, 3));
        ctx.waitTicks(3);
        shot(ctx, out, "craft-potion-heal");
        // our suggestions: the second page of the tab, filled in the background
        ctx.runOnClient(mc -> {
            CraftPreview.set(CraftingTab.INSTANCE, "view", 1);
            CraftPreview.call("startSuggestionsNow");
        });
        ctx.runOnClient(mc -> CraftPreview.set(CraftingTab.INSTANCE, "suggestMode", 1));
        ctx.waitTicks(20 * 12);
        shot(ctx, out, "craft-suggestions");
        // the same page as whole sets, four to a page, and one set's pieces
        ctx.runOnClient(mc -> {
            CraftPreview.set(CraftingTab.INSTANCE, "suggestMode", 0);
            CraftPreview.call("startSetsNow");
        });
        ctx.waitTicks(20 * 25);
        shot(ctx, out, "craft-sets");
        ctx.runOnClient(mc -> {
            var sets = (java.util.List<?>) CraftPreview.get(CraftingTab.INSTANCE, "sets");
            if (!sets.isEmpty()) {
                CraftPreview.set(CraftingTab.INSTANCE, "setDetail", sets.get(0));
            }
        });
        ctx.waitTicks(3);
        shot(ctx, out, "craft-set-pieces");
        // a piece's "Aç" opens it in the calculator: a click on the button, not on the row's tip area
        ctx.runOnClient(mc -> {
            if (CraftPreview.get(CraftingTab.INSTANCE, "setDetail") == null) {
                return;
            }
            String tipText = L.t("wynnlabs.craft.suggest_open_tip");
            try {
                for (Object a : (java.util.List<?>) CraftPreview.get(CraftingTab.INSTANCE, "overlay")) {
                    var tip = a.getClass().getDeclaredMethod("tip");
                    tip.setAccessible(true);
                    Object t = tip.invoke(a);
                    if (t instanceof java.util.List<?> l && !l.isEmpty() && l.get(0) instanceof net.minecraft.network.chat.Component c && c.getString().contains(tipText)) {
                        int[] r = new int[4];
                        String[] names = {"x", "y", "w", "h"};
                        for (int k = 0; k < 4; k++) {
                            var m = a.getClass().getDeclaredMethod(names[k]);
                            m.setAccessible(true);
                            r[k] = (int) m.invoke(a);
                        }
                        CraftingTab.INSTANCE.mouseClicked(r[0] + r[2] / 2.0, r[1] + r[3] / 2.0, 0);
                        break;
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            if (CraftPreview.get(CraftingTab.INSTANCE, "setDetail") != null || (int) CraftPreview.get(CraftingTab.INSTANCE, "view") != 0) {
                throw new AssertionError("the set piece's \"Aç\" did not open it");
            }
        });
        ctx.waitTicks(3);
        shot(ctx, out, "craft-set-piece-opened");
        ctx.runOnClient(mc -> CraftPreview.set(CraftingTab.INSTANCE, "setDetail", null));
        ctx.runOnClient(mc -> CraftPreview.set(CraftingTab.INSTANCE, "view", 0));
    }

    /** The saved recipes beside the menu (GUI 2), as a drawer (GUI 3) and the one-column page (GUI 4). */
    private static void craftScales(ClientGameTestContext ctx, Path out) throws Exception {
        ctx.runOnClient(mc -> {
            var saved = Crafting.saved();
            var goal = io.github.anilken.wynnlabs.crafting.SavedRecipes.SavedGoal.of(
                    io.github.anilken.wynnlabs.crafting.CraftSearch.Goal.of("spellDamage"));
            saved.save(new io.github.anilken.wynnlabs.crafting.SavedRecipes.Saved("Relik büyü", "relik", 103, 3, 2,
                    "NORMAL", java.util.Arrays.asList("Foul Fairy Dust", "Obelisk Core", "Dizzying Void",
                            "Depurinated Genome", null, null), goal, 1L), true);
            saved.save(new io.github.anilken.wynnlabs.crafting.SavedRecipes.Saved("Yüzük mana", "ring", 80, 2, 3,
                    "NORMAL", java.util.Arrays.asList("Major's Badge", null, null, null, null, null), goal, 2L), true);
            set(mc.screen, "tab", 10);
            CraftPreview.recipe("relik", 103, 3, 2, "Foul Fairy Dust", "Obelisk Core", "Dizzying Void",
                    "Depurinated Genome", "Exquisite Marsh Scale", "Gunkfueled Core");
        });
        ctx.getInput().setCursorPos(2, 2);
        for (int scale : new int[] {2, 4}) {
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(scale);
                mc.resizeDisplay();
            });
            ctx.waitTicks(5);
            shot(ctx, out, "craft-gui" + scale);
        }
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            CraftPreview.set(CraftingTab.INSTANCE, "drawerOpen", true);
        });
        ctx.waitTicks(5);
        shot(ctx, out, "craft-drawer");
        ctx.runOnClient(mc -> CraftPreview.set(CraftingTab.INSTANCE, "drawerOpen", false));
    }

    /**
     * A slot's comparison panel (the slot with the most options), every option and only the
     * Trade Market's, at GUI scale 2, 3 and 4, and scrolled to its end.
     */
    private static void optionShots(ClientGameTestContext ctx, Path out) throws Exception {
        int[] at = ctx.computeOnClient(mc -> {
            int[] best = {-1, -1, 0};
            List<Roadmap.Step> steps = Roadmap.cached();
            for (int i = 0; i < steps.size(); i++) {
                for (int k = 0; k < steps.get(i).gear().size(); k++) {
                    var o = steps.get(i).gear().get(k).options();
                    if (o != null && o.size() > best[2]) {
                        best = new int[] {i, k, o.size()};
                    }
                }
            }
            return best;
        });
        if (at[0] < 0) {
            return;
        }
        ctx.runOnClient(mc -> {
            HomePage home = ((RoadmapScreen) mc.screen).home;
            set(mc.screen, "selected", at[0]);
            home.detail = Roadmap.cached().get(at[0]).gear().get(at[1]);
            put(home, "detailLabel", home.detail.slot());
        });
        for (int scale : new int[] {2, 3, 4}) {
            for (boolean market : new boolean[] {false, true}) {
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeDisplay();
                    HomePage home = ((RoadmapScreen) mc.screen).home;
                    flag(home, "detailMarket", market);
                    set(home, "detailScroll", 0);
                });
                ctx.waitTicks(5);
                shot(ctx, out, "jmenu-options-gui" + scale + (market ? "-market" : ""));
            }
        }
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            HomePage home = ((RoadmapScreen) mc.screen).home;
            flag(home, "detailMarket", false);
            set(home, "detailScroll", 100000);
        });
        ctx.waitTicks(5);
        shot(ctx, out, "jmenu-options-end");
        ctx.runOnClient(mc -> {
            HomePage home = ((RoadmapScreen) mc.screen).home;
            home.detail = null;
            set(home, "detailScroll", 0);
        });
    }

    /** The recorded bag as a 9 x 4 grid, each slot with its advice word and sell icon. */
    static final class BagPreview extends net.minecraft.client.gui.screens.Screen {
        BagPreview() {
            super(net.minecraft.network.chat.Component.literal("bag"));
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, 0xFF2A2A2A);
            var bag = GameState.current().inventory();
            int x0 = (width - 9 * 18) / 2;
            int y0 = (height - 4 * 18) / 2;
            for (int i = 0; i < Math.min(36, bag.size()); i++) {
                int x = x0 + (i % 9) * 18;
                int y = y0 + (i / 9) * 18;
                g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
                var stack = bag.get(i);
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x, y);
                    TooltipListener.drawBadge(g, x, y, stack);
                }
            }
        }
    }

    /**
     * The quests tab on "Jungle Fever" in the preview's language: the shipped guide while the wiki
     * page is unchanged, then the live English stages with the note when the page changed since.
     */
    private static void questGuides(ClientGameTestContext ctx, Path out) throws ReflectiveOperationException {
        ctx.setScreen(() -> RoadmapScreen.on(2));
        ctx.waitForScreen(RoadmapScreen.class);
        ctx.runOnClient(mc -> QuestBook.put(new QuestBook.Entry("Jungle Fever", "STARTED", 48, "Medium", 0, true,
                "Infiltrate Almuj's Secret Emerald Labyrinth at [1019, 84, -1976] underground and find the Banker",
                1019, 84, -1976, null, System.currentTimeMillis(), "QUEST", "Medium")));
        QuestGuides.Guide g = ctx.computeOnClient(mc -> QuestGuides.find("Jungle Fever"));
        QuestWiki.Info same = new QuestWiki.Info("Medium", "", "", 48, 0, 0, java.util.List.of(), g.english(), java.util.List.of());
        java.util.List<QuestWiki.Stage> changed = new java.util.ArrayList<>(g.english());
        QuestWiki.Stage s3 = changed.get(3);
        changed.set(3, new QuestWiki.Stage(s3.title(), s3.task() + " in the jungle", s3.hints(), s3.at(), s3.points(), s3.names()));
        QuestWiki.Info newer = new QuestWiki.Info("Medium", "", "", 48, 0, 0, java.util.List.of(), changed, java.util.List.of());
        for (QuestWiki.Info live : new QuestWiki.Info[] {same, newer}) {
            ctx.runOnClient(mc -> {
                QuestWiki.putLive("Jungle Fever", live);
                try {
                    set(mc.screen, "tab", 2);
                    Field f = QuestsTab.class.getDeclaredField("questSelected");
                    f.setAccessible(true);
                    f.set(((RoadmapScreen) mc.screen).questsTab, "Jungle Fever");
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            ctx.waitTicks(3);
            shot(ctx, out, live == same ? "quests-jungle-fever" : "quests-newer");
        }
        // the points tab: every place numbered by stage, the passed ones struck through
        ctx.runOnClient(mc -> {
            QuestWiki.putLive("Jungle Fever", same);
            try {
                set(((RoadmapScreen) mc.screen).questsTab, "questDetailTab", 3);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        ctx.waitTicks(3);
        shot(ctx, out, "quests-points");
        // the full guide (Tam çözüm) from the shipped stages, on the stage you are on
        ctx.runOnClient(mc -> QuestWiki.putLive("Jungle Fever", same));
        ctx.setScreen(() -> new GuideScreen("Jungle Fever",
                "Infiltrate Almuj's Secret Emerald Labyrinth at [1019, 84, -1976] underground and find the Banker"));
        ctx.waitTicks(10);
        shot(ctx, out, "quests-full-guide");
        // a quest with a part under its last stage ("▸ İpuçları": the door puzzle's answer)
        ctx.setScreen(() -> new GuideScreen("A Fighting Species", "Return to Darnes"));
        ctx.waitTicks(10);
        shot(ctx, out, "quests-full-guide-parts");
        // the guide's kinds of stage: options that merge, parts as chips, a short stage, pictures
        guideShot(ctx, out, "The Feathers Fly Part II", 4, "guide-options");
        guideShot(ctx, out, "Recipe For Disaster", 5, "guide-parts");
        guideShot(ctx, out, "Recipe For Disaster", 0, "guide-short");
        guideShot(ctx, out, "Desperate Metal", 1, "guide-pictures");
    }

    /** The full guide of a quest on its stage {@code stage} (from 0), as if the game tracked that stage. */
    private static void guideShot(ClientGameTestContext ctx, Path out, String quest, int stage, String name) {
        String task = ctx.computeOnClient(mc -> QuestGuides.allEnglish().stream().filter(g -> g.id().equals(quest))
                .findFirst().map(g -> g.english().get(stage).task()).orElse(""));
        ctx.setScreen(() -> new GuideScreen(quest, task));
        ctx.waitTicks(10);
        shot(ctx, out, name);
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }

    private static void flag(Object screen, String field, boolean value) throws ReflectiveOperationException {
        Field f = screen.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.setBoolean(screen, value);
    }

    private static void put(Object screen, String field, Object value) throws ReflectiveOperationException {
        Field f = screen.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(screen, value);
    }

    private static void set(Object screen, String field, int value) throws ReflectiveOperationException {
        Field f = screen.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(screen, value);
    }

    /** A few caves and places around Nesaak for the quests tab's kinds (Wynntils' cache is not in the preview). */
    private static final String PREVIEW_CAVES = """
            [{"name":"Wolf Den","description":"The home of the wolves all around the frozen area of Nesaak, found around [175, 73, -715]",
              "length":"medium","difficulty":"easy","requirements":"43","rewards":["7000 XP","1 Snow Boots","Various Items"],
              "location":{"x":175,"y":73,"z":-715}},
             {"name":"Breaking Ice","description":"A cave covered in thin ice and full of rocky creatures around [-134, 74, -797]",
              "length":"medium","difficulty":"medium","requirements":"46","rewards":["9000 XP","Various Items"],
              "location":{"x":-134,"y":74,"z":-797}},
             {"name":"Ice Doors","description":"A frosted over doorway at [-118, 71, -923]","length":"long","difficulty":"medium",
              "requirements":"41","rewards":["12000 XP"],"location":{"x":-118,"y":71,"z":-923}},
             {"name":"Raging Survivor","description":"","length":"medium","difficulty":"hard","requirements":"45","rewards":[],
              "location":{"x":-287,"y":27,"z":-555}},
             {"name":"Gold Rush","description":"","length":"medium","difficulty":"easy","requirements":"38","rewards":[],
              "location":{"x":1440,"y":34,"z":-1369}},
             {"name":"Swamp Hill","description":"","length":"short","difficulty":"easy","requirements":"50","rewards":[],
              "location":{"x":-2059,"y":59,"z":-5241}}]""";
    private static final String PREVIEW_FEATURES = """
            [{"categoryId":"wynntils:content:dungeon","attributes":{"label":"Ice Barrows","level":45},"location":{"x":134,"y":62,"z":-639}},
             {"categoryId":"wynntils:content:dungeon","attributes":{"label":"Sand-Swept Tomb","level":36},"location":{"x":1428,"y":62,"z":-1830}},
             {"categoryId":"wynntils:content:boss-altar","attributes":{"label":"Prison of Souls","level":45},"location":{"x":50,"y":60,"z":-487}},
             {"categoryId":"wynntils:content:raid","attributes":{"label":"Nest of the Grootslangs","level":54},"location":{"x":-1977,"y":63,"z":-5635}},
             {"categoryId":"wynntils:content:time-trial","attributes":{"label":"Llevigar Leg Day","level":40},"location":{"x":-2018,"y":60,"z":-4822}}]""";
}
