package io.github.anilken.wynnlabs.dev;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The screenshot tool's sequence on a fake game: page by page, each view from a fresh menu showing
 * only what its steps open, the skips, the views left out, the hovers, and everything put back at
 * the end, on Esc and on an error.
 */
class ShotRunTest {

    /** Each menu tab number's name, as the menu numbers them. */
    private static final String[] NAMES = {"home", "today", "quests", "deaths", "bank", "settings", "ai", "damage", "discoveries",
        "tree", "craft", "endgame", "search", "mounts", "road", "places"};

    /** A game that writes down what is done to it. A picture is two grabs (over black, over white); "capture" marks the last. */
    private static final class Fake implements ShotRun.Stage {
        final List<String> log = new ArrayList<>();
        /** Steps and hover parts with nothing to show. */
        final Set<String> missing = new HashSet<>();
        int lang = 2;
        int scale = 0;
        int backdrop = -1;
        String hovered;
        boolean flags;
        boolean hidden;
        boolean open;
        int pending;
        Integer doneCount;
        ShotRun.Stop stop;
        boolean done;
        RuntimeException failOn;
        final List<Map<String, String>> closes = new ArrayList<>();
        /** Views whose frame shows something more than their steps opened (by state, "main" for none). */
        final Set<String> dirty = new HashSet<>();
        /** What is open over the page now: a fresh menu has nothing, each step adds what it opens. */
        int openNow;
        /** Steps that run but open nothing (an empty list stays shut). */
        final Set<String> shut = new HashSet<>();

        @Override
        public int lang() {
            return lang;
        }

        @Override
        public void lang(int pick) {
            log.add("lang " + pick);
            lang = pick;
        }

        @Override
        public int scale() {
            return scale;
        }

        @Override
        public void scale(int s) {
            log.add("scale " + s);
            scale = s;
        }

        @Override
        public String code() {
            return lang == 2 ? "tr" : "en";
        }

        @Override
        public void flags(boolean on, boolean hideName) {
            log.add("flags " + on + (hideName ? " hidename" : ""));
            flags = on;
            hidden = hideName;
        }

        @Override
        public void open() {
            log.add("open");
            open = true;
        }

        @Override
        public boolean showing() {
            return open;
        }

        @Override
        public void select(int tab) {
            if (hovered != null) {
                throw new AssertionError("a view selected while " + hovered + " is hovered");
            }
            log.add("select " + NAMES[tab]);
            openNow = 0;
        }

        @Override
        public boolean act(String step) {
            log.add("act " + step);
            if (missing.contains(step)) {
                return false;
            }
            openNow += shut.contains(step) ? 0 : ShotRun.opens(step);
            return true;
        }

        @Override
        public void settle() {
            log.add("settle");
        }

        @Override
        public String unclean(ShotRun.Shot shot) {
            if (dirty.contains(shot.state() == null ? "main" : shot.state())) {
                return "a menu open";
            }
            if (openNow < shot.overlays()) {
                return ShotRun.NOTHING;
            }
            return openNow == shot.overlays() ? null : openNow + " open, " + shot.overlays() + " expected";
        }

        @Override
        public void page(String page, int taken, int skipped, List<String> rejected) {
            log.add("page " + page + " " + taken + " " + skipped + " " + rejected);
        }

        @Override
        public boolean hover(String part) {
            log.add("hover " + part);
            if (part != null && missing.contains(part)) {
                return false;
            }
            hovered = part;
            return true;
        }

        @Override
        public int passes() {
            return 2;
        }

        @Override
        public void backdrop(int pass) {
            log.add("backdrop " + pass);
            backdrop = pass;
        }

        @Override
        public void capture(String file, int pass, Map<String, String> close) {
            if (failOn != null && file.contains("endgame")) {
                throw failOn;
            }
            if (backdrop != pass) {
                throw new AssertionError("grabbed pass " + pass + " over backdrop " + backdrop);
            }
            log.add("grab " + file + " " + pass);
            if (pass == passes() - 1) {
                log.add("capture " + file);
                closes.add(close);
                pending++;
            }
        }

        @Override
        public int pending() {
            return pending;
        }

        @Override
        public void restore() {
            log.add("restore");
            open = false;
        }

        @Override
        public void done(int count, ShotRun.Stop why, String detail) {
            log.add("done " + count + (why == null ? "" : " " + why));
            done = true;
            doneCount = count;
            stop = why;
        }
    }

    private static int runToEnd(ShotRun run, Fake game) {
        int ticks = 0;
        while (run.tick()) {
            if (++ticks > 100_000) {
                throw new AssertionError("the run never ended");
            }
            if (ticks % 7 == 0) {
                game.pending = 0;                   // the writes finish in the background
            }
        }
        return ticks;
    }

    private static List<String> only(List<String> log, String prefix) {
        return log.stream().filter(l -> l.startsWith(prefix)).toList();
    }

    private static List<String> captured(Fake game) {
        return only(game.log, "capture ").stream().map(l -> l.substring("capture ".length())).toList();
    }

    @Test
    void planGoesPageByPageWithEachFeatureOnce() {
        List<ShotRun.Shot> plan = ShotRun.plan(NAMES, "all");
        // the pages one after another, in their order, each in one piece
        List<String> pages = new ArrayList<>();
        for (ShotRun.Shot shot : plan) {
            if (pages.isEmpty() || !pages.get(pages.size() - 1).equals(shot.page())) {
                assertFalse(pages.contains(shot.page()), shot.page() + " comes back later");
                pages.add(shot.page());
            }
        }
        assertEquals(List.copyOf(ShotRun.PAGES.keySet()), pages);
        assertEquals(List.of("today", "home", "build", "quests", "guide", "tree", "damage", "endgame", "bank", "crafting", "search",
                "box", "mounts", "road", "places", "ai", "settings", "update"), pages);
        for (String page : pages) {
            List<ShotRun.Shot> views = ShotRun.plan(NAMES, page);
            // each view of a page once, and few enough that no list of near-alike views comes back
            assertEquals(views.size(), views.stream().map(ShotRun.Shot::state).distinct().count(), page + " plans a view twice");
            assertTrue(views.size() <= 12, page + " has " + views.size() + " views");
            assertNull(views.get(0).state(), page + " does not start as it opens");
            for (ShotRun.Shot v : views) {
                // a view opens at most one thing over its page (the build window's own menu over the window aside)
                int most = v.state() != null && v.state().equals("priority") ? 2 : 1;
                assertTrue(v.overlays() <= most, page + "-" + v.state() + " opens " + v.overlays() + " things");
                // the menu tab under the page is the one it names
                assertEquals(ShotRun.PAGES.get(page), NAMES[v.tab()]);
            }
        }
        assertEquals(List.of("home-main", "home-slot-tip", "home-goals", "home-archetypes", "home-options", "home-plan"),
                ShotRun.plan(NAMES, "home").stream().map(v -> v.page() + "-" + (v.state() == null ? "main" : v.state())).toList());
        assertTrue(plan.size() < 100, plan.size() + " views in all");
        assertEquals(ShotRun.FIRST_WAIT, plan.get(0).ticks());
        // the same plan every time: the numbers are stable
        assertEquals(plan, ShotRun.plan(NAMES, "all"));
        ShotRun.Shot main = ShotRun.plan(NAMES, "home").get(0);
        assertEquals("en-01-home-main.png", main.file("en", 1));
        assertEquals("close/en-01-home-main-close-gear.png", main.close("en", 1, "gear"));
        assertEquals("tr-12-today-main.png", new ShotRun.Shot("today", 1, null, 1).file("tr", 12));
        assertEquals(List.of("gear"), main.closes());
    }

    @Test
    void everyViewStartsFromAFreshMenuAndShowsOnlyWhatItsStepsOpen() {
        Fake game = new Fake();
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "all"), 1, false);
        runToEnd(run, game);
        assertEquals(0, run.rejected(), game.log.toString());
        assertNull(game.stop);
        // between two pictures: a fresh menu, the steps, the page settled, then the grab
        int lastGrab = -1;
        for (int i = 0; i < game.log.size(); i++) {
            String l = game.log.get(i);
            if (l.startsWith("grab ") && l.endsWith(" 0")) {
                List<String> between = game.log.subList(lastGrab + 1, i);
                assertTrue(between.stream().anyMatch(b -> b.startsWith("select ")), "no fresh menu before " + l);
                assertTrue(between.contains("settle"), "not settled before " + l);
                assertTrue(between.lastIndexOf("settle") > lastIndexOf(between, "act "), "a step after settling before " + l);
                lastGrab = i;
            }
        }
    }

    private static int lastIndexOf(List<String> log, String prefix) {
        for (int i = log.size() - 1; i >= 0; i--) {
            if (log.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aFrameWithSomethingElseOpenIsLeftOutAndReported() {
        Fake game = new Fake();
        game.dirty.add("goals");
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "home"), 1, false);
        runToEnd(run, game);
        assertEquals(List.of("en-01-home-main.png", "en-02-home-slot-tip.png", "en-03-home-archetypes.png",
                "en-04-home-options.png", "en-05-home-plan.png"), captured(game));
        assertEquals(1, run.rejected());
        assertEquals(0, run.skipped());
        assertEquals(List.of("page home 5 0 [goals: a menu open]"), only(game.log, "page "));
    }

    @Test
    void aMenuThatStaysShutIsSkippedNotLeftOut() {
        Fake game = new Fake();
        game.shut.add("search:menu:marks");
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "search"), 1, false);
        runToEnd(run, game);
        assertTrue(captured(game).stream().noneMatch(c -> c.contains("menu-marks")));
        assertEquals(1, run.skipped());
        assertEquals(0, run.rejected());
    }

    @Test
    void eachPageEndsWithItsLine() {
        Fake game = new Fake();
        game.missing.add("open:results");
        runToEnd(new ShotRun(game, ShotRun.plan(NAMES, "all"), 1, false), game);
        List<String> lines = only(game.log, "page ");
        assertEquals(ShotRun.PAGES.size(), lines.size());
        assertTrue(lines.contains("page build 6 1 []"), lines.toString());
        // a page's line comes before the next page's first view
        int build = game.log.indexOf("page build 6 1 []");
        assertTrue(build < game.log.indexOf("grab en-" + String.format("%02d", 3 + 6 + 6 + 1) + "-quests-main.png 0"), game.log.toString());
    }

    @Test
    void capturesInOrderThenPutsEverythingBack() {
        Fake game = new Fake();
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "settings"), 1, false);
        runToEnd(run, game);
        // the language first, then the menu; each view selected and its steps taken before its capture
        assertEquals(List.of("flags true", "lang 1", "open"), game.log.subList(0, 3));
        assertEquals(List.of("en-01-settings-main.png", "en-02-settings-world.png", "en-03-settings-mounts.png",
                "en-04-settings-trading.png", "en-05-settings-advanced.png", "en-06-settings-changed.png"), captured(game));
        int look = game.log.indexOf("act settings:cat:world");
        assertEquals(List.of("select settings", "backdrop 0", "act settings:cat:world", "settle"), game.log.subList(look - 2, look + 2));
        int first = game.log.indexOf("grab en-02-settings-world.png 0");
        assertEquals(List.of("grab en-02-settings-world.png 0", "backdrop 1", "grab en-02-settings-world.png 1",
                "capture en-02-settings-world.png"), game.log.subList(first, first + 4));
        // then the page's line, the screen, the language and the flags back, and one message with the count
        int restore = game.log.indexOf("restore");
        assertEquals(List.of("page settings 6 0 []", "restore", "lang 2", "flags false", "done 6"),
                game.log.subList(restore - 1, game.log.size()));
        assertEquals(2, game.lang);
        assertFalse(game.flags);
        assertNull(game.stop);
        assertTrue(run.finished());
        assertFalse(run.tick());
    }

    @Test
    void viewsWithNothingToShowAreSkippedAndTheNumbersStayInARow() {
        Fake game = new Fake();
        // no road map to compare options on, no plan
        game.missing.addAll(List.of("home:options", "plan"));
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "home"), 1, false);
        runToEnd(run, game);
        assertEquals(List.of("en-01-home-main.png", "en-02-home-slot-tip.png", "en-03-home-goals.png", "en-04-home-archetypes.png"),
                captured(game));
        assertEquals(4, game.doneCount);
        assertEquals(2, run.skipped());
        // a skipped view is never grabbed
        assertTrue(only(game.log, "grab").stream().noneMatch(g -> g.contains("options") || g.contains("plan")));
        // the close-ups asked for beside the main view
        assertEquals(Map.of("gear", "close/en-01-home-main-close-gear.png"), game.closes.get(0));
    }

    @Test
    void aTooltipShotHoversThenMovesTheMouseAway() {
        Fake game = new Fake();
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "home"), 1, false);
        runToEnd(run, game);
        int hover = game.log.indexOf("hover slot");
        int grab = game.log.indexOf("grab en-02-home-slot-tip.png 0");
        int away = game.log.indexOf("hover null");
        int nextSelect = game.log.subList(hover, game.log.size()).indexOf("select home") + hover;
        assertTrue(hover < grab, "hovered before the grab");
        assertTrue(grab < away && away < nextSelect, "the mouse away before the next view: " + game.log);
        assertNull(game.hovered);
        // a view without a hover never moves the mouse
        assertEquals(List.of("hover slot", "hover null"), only(game.log, "hover"));
    }

    @Test
    void aHoverWithNothingDrawnIsSkippedWithoutATooltip() {
        Fake game = new Fake();
        game.missing.add("slot");
        runToEnd(new ShotRun(game, ShotRun.plan(NAMES, "home"), 1, false), game);
        assertTrue(captured(game).stream().noneMatch(c -> c.contains("slot-tip")));
        assertNull(game.hovered);
    }

    @Test
    void waitsSomeTicksBeforeEachCapture() {
        Fake game = new Fake();
        ShotRun run = new ShotRun(game, List.of(new ShotRun.Shot("today", 1, null, 5)), -1, false);
        run.tick();                                 // start
        run.tick();                                 // select
        for (int i = 0; i < 4; i++) {
            run.tick();
            assertTrue(only(game.log, "grab").isEmpty(), "grabbed before the frames settled");
        }
        run.tick();
        assertEquals(List.of("grab tr-01-today-main.png 0"), only(game.log, "grab"));
        // the white backdrop drawn for a frame before the second grab
        run.tick();
        assertEquals(1, only(game.log, "grab").size());
        run.tick();
        assertEquals(List.of("tr-01-today-main.png"), captured(game));
    }

    @Test
    void escStopsMidHoverAndPutsEverythingBack() {
        Fake game = new Fake();
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "all"), 1, 3, true);
        while (game.hovered == null) {
            assertTrue(run.tick());
        }
        assertTrue(game.hidden);
        assertEquals(1, game.lang);
        assertEquals(3, game.scale);
        run.escape();
        assertFalse(run.tick());
        assertEquals(ShotRun.Stop.ESC, game.stop);
        int away = game.log.lastIndexOf("hover null");
        assertEquals(List.of("hover null", "restore", "scale 0", "lang 2", "flags false", "done " + run.count() + " ESC"),
                game.log.subList(away, game.log.size()));
        assertNull(game.hovered);
        assertEquals(2, game.lang);
        assertEquals(0, game.scale);
        assertFalse(game.flags);
        assertFalse(game.hidden);
        assertFalse(run.tick());
        assertEquals(1, only(game.log, "restore").size(), "put back once");
    }

    @Test
    void theSizeIsSetBeforeTheMenuOpensAndPutBack() {
        Fake game = new Fake();
        runToEnd(new ShotRun(game, ShotRun.plan(NAMES, "today"), -1, 4, false), game);
        assertEquals(List.of("flags true", "scale 4", "open"), game.log.subList(0, 3));
        assertTrue(game.log.indexOf("scale 0") > game.log.indexOf("restore"));
        assertEquals(0, game.scale);
        Fake same = new Fake();
        same.scale = 3;
        runToEnd(new ShotRun(same, ShotRun.plan(NAMES, "today"), -1, 3, false), same);
        assertTrue(only(same.log, "scale").isEmpty(), same.log.toString());
    }

    @Test
    void aMenuClosedByAnythingElseStopsTheRun() {
        Fake game = new Fake();
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "all"), 1, false);
        run.tick();
        run.tick();
        game.open = false;                          // the player opened the chat, a server menu replaced it
        assertFalse(run.tick());
        assertEquals(ShotRun.Stop.CLOSED, game.stop);
        assertEquals(2, game.lang);
        assertTrue(game.log.contains("restore"));
        assertFalse(game.flags);
    }

    @Test
    void anErrorPutsEverythingBack() {
        Fake game = new Fake();
        game.failOn = new IllegalStateException("no frame");
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "all"), 1, false);
        runToEnd(run, game);
        assertEquals(ShotRun.Stop.ERROR, game.stop);
        assertEquals(2, game.lang);
        assertFalse(game.flags);
        assertNull(game.hovered);
        assertTrue(game.log.contains("restore"));
        assertFalse(captured(game).stream().anyMatch(c -> c.contains("endgame")));
    }

    @Test
    void theCurrentLanguageIsLeftAlone() {
        Fake game = new Fake();
        runToEnd(new ShotRun(game, ShotRun.plan(NAMES, "today"), -1, false), game);
        assertTrue(only(game.log, "lang").isEmpty(), game.log.toString());
        assertEquals(List.of("tr-01-today-main.png", "tr-02-today-tip.png", "tr-03-today-others.png"), captured(game));
    }

    @Test
    void theLastFilesAreAwaitedBeforeTheMessage() {
        Fake game = new Fake();
        ShotRun run = new ShotRun(game, ShotRun.plan(NAMES, "today"), 1, false);
        int ticks = 0;
        while (!game.log.contains("restore")) {
            run.tick();
            ticks++;
            assertTrue(ticks < 1000);
        }
        assertFalse(game.done);
        run.tick();
        assertFalse(game.done);
        game.pending = 0;
        assertFalse(run.tick());
        assertTrue(game.done);
    }

    @Test
    void theNameIsFoundWhateverItsCase() {
        assertEquals(6, Shots.maskedAt("Hello anilKen!", "anilken", 0));
        assertEquals(-1, Shots.maskedAt("Hello there", "anilken", 0));
        assertEquals(-1, Shots.maskedAt("anything", "", 0));
    }

    @Test
    void theIndexListsEveryPicture() {
        String html = Shots.index("Run", List.of("en-01-home-main.png", "en-02-home-slot-tip.png"),
                List.of("close/en-01-home-main-close-gear.png"));
        assertTrue(html.contains("<img src=\"en-02-home-slot-tip.png\""));
        assertTrue(html.contains("<figcaption>close/en-01-home-main-close-gear.png</figcaption>"));
        assertTrue(html.contains("(2 + 1 close-ups)"));
    }
}
