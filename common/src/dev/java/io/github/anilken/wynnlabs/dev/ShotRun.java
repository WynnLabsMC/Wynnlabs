package io.github.anilken.wynnlabs.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The screenshot tool's sequence ({@code /wynnlabs shots <page>}, dev builds only), one client tick
 * at a time so the game keeps drawing: the menu language (and size) switched, then page by page,
 * for each picture a freshly opened menu on its tab, its steps taken (a drawer, a menu, a window of
 * the mod: {@link Stage#act}), the page settled (no field focused), an element hovered for its
 * tooltip when it has one, a few frames waited, the frame checked for anything open that the steps
 * did not open ({@link Stage#unclean}) and saved; then everything put back as it was. A step or a
 * hover with nothing to show skips its picture, a frame with something unexpected open is left out
 * and reported, and the numbers stay in a row. After each page a line says what it took. Esc, a
 * menu closed by anything else or an error put everything back at once.
 *
 * <p>Each picture is taken in {@link Stage#passes} passes, one per backdrop behind the menu (black,
 * then white): the two frames together give every pixel its own alpha, so the saved picture is the
 * mod's window alone on transparency ({@link ShotCrop}).
 *
 * <p>Knows nothing of Minecraft: a {@link Stage} does the work ({@link Shots} in the game, a fake
 * in the tests). Nothing here opens a game menu, clicks a slot, or sends anything to the server.
 */
final class ShotRun {

    /**
     * One picture: its page, the menu tab it starts on, its view's name (null: the page as it
     * opens, "main" in the file), the steps that make the view, the part to hover for a tooltip
     * (null: none), the parts saved as close-ups beside it, and the ticks to wait before the frame
     * is taken.
     */
    record Shot(String page, int tab, String state, int ticks, List<String> steps, String hover, List<String> closes) {
        Shot(String page, int tab, String state, int ticks) {
            this(page, tab, state, ticks, List.of(), null, List.of());
        }

        /** {@code <lang>-<nn>-<page>-<state>.png}. */
        String file(String lang, int n) {
            return String.format(Locale.ROOT, "%s-%02d-%s-%s.png", lang, n, page, state == null ? "main" : state);
        }

        /** A close-up of {@code part} beside it: {@code close/<lang>-<nn>-<page>-<state>-close-<part>.png}. */
        String close(String lang, int n, String part) {
            String f = file(lang, n);
            return "close/" + f.substring(0, f.length() - 4) + "-close-" + part + ".png";
        }

        /** What its steps leave open over the page (a drawer, a menu, a list): see {@link #opens}. */
        int overlays() {
            return steps.stream().mapToInt(ShotRun::opens).sum();
        }
    }

    /** What the sequence does in the game. Every call happens on the client thread, in a tick. */
    interface Stage {
        /** The menu language now (the rail's picker: 0 follows the game, 1 English, 2 Turkish). */
        int lang();

        void lang(int pick);

        /** The menu scale setting now (0: the game's GUI scale, else 2 to 4). */
        int scale();

        void scale(int scale);

        /** The language the menu speaks now, for the file names ("en", "tr"). */
        String code();

        /** The render flags while capturing (no world, HUD or mouse hover; {@code hideName}: the player's name masked), or off again. */
        void flags(boolean on, boolean hideName);

        /** Remembers the screen and the menu's views as they are, then opens the mod's menu. */
        void open();

        /** Whether the menu opened (or the mod's window the run opened over it) is still the one on screen. */
        boolean showing();

        /** A freshly opened menu on {@code tab}: nothing left open, focused or hovered from the picture before. */
        void select(int tab);

        /** One step towards a view (a drawer, a menu, a window); false when there is nothing to show. */
        boolean act(String step);

        /** After the steps: no field focused (no blinking cursor), the mouse away. */
        void settle();

        /**
         * Why the frame drawn last is not the view {@code shot} asks for (a popup, menu or drawer
         * open that its steps did not open, a field focused, another screen), or null when it is;
         * {@link #NOTHING} when what its steps open did not open (nothing to list in it).
         */
        String unclean(Shot shot);

        /** Hovers {@code part} for its tooltip; false when it is not drawn now. Null: the mouse away again. */
        boolean hover(String part);

        /** Frames taken per picture (one per backdrop). */
        int passes();

        /** The backdrop drawn behind the menu for pass {@code pass} (0 black, 1 white). */
        void backdrop(int pass);

        /**
         * Grabs the frame drawn last for pass {@code pass} of {@code file}; after the last pass the
         * picture and its close-ups ({@code closes}: part name → file) are written (later).
         */
        void capture(String file, int pass, java.util.Map<String, String> closes);

        /** Pictures not written yet. */
        int pending();

        /** One page done: its pictures taken, its views with nothing to show, and the ones left out ("state: why"). */
        void page(String page, int taken, int skipped, List<String> rejected);

        /** The screen and the menu's views back as {@link #open} found them. */
        void restore();

        /** The end: {@code count} pictures, {@code stop} null when every one was taken, else why it stopped. */
        void done(int count, Stop stop, String detail);
    }

    enum Stop { ESC, CLOSED, ERROR }

    /** Ticks to wait before a capture: the first one (the pack's textures, icons) longer, then the heavier tabs. */
    static final int FIRST_WAIT = 30;
    static final int SLOW_WAIT = 20;
    static final int WAIT = 8;
    /** Ticks between steps (a frame drawn with the last one, so the next finds what it drew). */
    static final int STEP_WAIT = 3;
    /** Ticks after the hover, so the tooltip is drawn. */
    static final int HOVER_WAIT = 3;
    /** Ticks after a backdrop change before the next pass: a frame drawn with it. */
    static final int BACKDROP_WAIT = 2;
    /** At most this long for the last files to be written (ticks). */
    static final int DRAIN = 400;
    /** Ticks for a search view: the search's worker answers, the card picks its sources. */
    static final int SEARCH_WAIT = 25;
    /** {@link Stage#unclean}'s answer when what the steps open is not open: nothing to show, the view skipped. */
    static final String NOTHING = "nothing open";

    /** The pages in their order, each with the menu tab it starts on (by the tab's name). */
    static final Map<String, String> PAGES = pages();

    private static Map<String, String> pages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("today", "today");
        m.put("home", "home");
        m.put("build", "home");             // the build window over the home
        m.put("quests", "quests");
        m.put("guide", "quests");           // the full quest guide, a window of its own
        m.put("tree", "tree");
        m.put("damage", "tree");            // the tree page's damage drawer
        m.put("endgame", "endgame");
        m.put("bank", "bank");
        m.put("crafting", "craft");
        m.put("search", "search");
        m.put("box", "search");             // the Trade Market window an item card opens
        m.put("mounts", "mounts");
        m.put("road", "road");              // the Mounts tab's road map section
        m.put("places", "places");          // and its places section
        m.put("ai", "ai");
        m.put("settings", "settings");
        m.put("update", "settings");        // the update window About opens
        return java.util.Collections.unmodifiableMap(m);
    }

    private static final java.util.Set<String> SLOW = java.util.Set.of("crafting", "tree", "damage", "endgame", "bank");

    // the one representative of a list of near-alike views (ShotNamesTest checks each against the menu)
    /** The quest filter shown: Mini-quests, whose pane differs from a quest's guide. */
    static final String QUEST_FILTER = "MINI";
    /** The quest kinds shown: a secret (steps pane) and a cave (place pane); the others look like one of them. */
    static final String[] QUEST_KINDS = {"SECRET_DISCOVERY", "CAVE"};
    /** The settings' pages shown after About: a list of switches (glow) and the pages laid out their own way. */
    static final String[] SETTING_PAGES = {"look", "glow", "rules", "data"};
    /** The level road of a gathering profession (spots instead of recipes); the crafting one is the road as it opens. */
    static final String ROAD_GATHER = "MINING";

    /**
     * How many things a step leaves open over the page: a drawer, a popup list or a menu (1), a
     * window's menu over the window (1), anything else (a sub-page, a layout, a filter, a window
     * of the mod that is the screen itself) 0.
     */
    static int opens(String step) {
        String[] p = step.split(":");
        return switch (p[0]) {
            case "open", "buildmenu", "ai" -> 1;
            case "home" -> p[1].equals("goals") || p[1].equals("archetypes") ? 1 : 0;
            case "today" -> 1;
            case "quest" -> p[1].equals("menu") || p[1].equals("types") ? 1 : 0;
            case "tree" -> p[1].equals("archetypes") || p[1].equals("damage") || p[1].equals("deaths") ? 1 : 0;
            case "endgame" -> p[1].equals("pools") || p[1].equals("log") ? 1 : 0;
            case "bank" -> p[1].equals("menu") || p[1].equals("item") || p[1].equals("sell") ? 1 : 0;
            case "craft" -> p[1].equals("picker") || p[1].equals("shop") || p[1].equals("saved") ? 1 : 0;
            case "search" -> p[1].equals("menu") ? 1 : 0;
            default -> 0;
        };
    }

    /** Every view of one page, in a stable order; the first is the page as it opens. Each feature once. */
    static List<Shot> views(String page, int t, int first) {
        List<Shot> out = new ArrayList<>();
        int w = first > 0 ? first : SLOW.contains(page) ? SLOW_WAIT : WAIT;
        switch (page) {
            case "today" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(tip(page, t));
                out.add(view(page, t, "others", "today:others"));
            }
            case "home" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of("gear")));
                out.add(shot(page, t, "slot-tip", WAIT, List.of(), "slot", List.of()));
                out.add(view(page, t, "goals", "home:goals"));
                out.add(view(page, t, "archetypes", "home:archetypes"));
                out.add(view(page, t, "options", "home:options"));
                out.add(view(page, t, "plan", "plan"));
            }
            case "build" -> {
                out.add(shot(page, t, null, w, List.of("open:picker"), null, List.of()));
                out.add(shot(page, t, "tip", WAIT, List.of("open:picker"), "pickrow", List.of()));
                out.add(shot(page, t, "priority", WAIT, List.of("open:picker", "buildmenu:lean"), null, List.of()));
                out.add(view(page, t, "import", "open:import"));
                out.add(shot(page, t, "results", WAIT, List.of("open:results"), null, List.of("results")));
                out.add(view(page, t, "saved", "open:saved"));
                out.add(view(page, t, "edit", "open:edit"));
            }
            case "quests" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(tip(page, t));
                out.add(view(page, t, "points", "quest:points"));
                out.add(view(page, t, "menu", "quest:menu"));
                out.add(view(page, t, "kinds", "quest:types"));
                out.add(view(page, t, "mini", "quest:filter:" + QUEST_FILTER));
                for (String k : QUEST_KINDS) {
                    out.add(view(page, t, lower(k).replace('_', '-'), "quest:type:" + k));
                }
            }
            case "guide" -> out.add(shot(page, t, null, w, List.of("window:guide"), null, List.of()));
            case "tree" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(tip(page, t));
                out.add(view(page, t, "end", "tree:end"));
                out.add(view(page, t, "search", "tree:search"));
                out.add(view(page, t, "archetypes", "tree:archetypes"));
                out.add(view(page, t, "deaths", "tree:deaths"));
            }
            case "damage" -> out.add(shot(page, t, null, w, List.of("tree:damage"), null, List.of()));
            case "endgame" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(tip(page, t));
                out.add(view(page, t, "pool-filter", "endgame:poolfilter"));
                out.add(shot(page, t, "raids", WAIT, List.of("endgame:pools:0"), null, List.of("pools")));
                out.add(shot(page, t, "pool-tip", WAIT, List.of("endgame:pools:0"), "poolrow", List.of()));
                out.add(view(page, t, "camps", "endgame:pools:1"));
                out.add(view(page, t, "log", "endgame:log"));
            }
            case "bank" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(shot(page, t, "tip", WAIT, List.of(), "bankitem", List.of()));
                out.add(view(page, t, "search", "bank:search"));
                out.add(view(page, t, "list", "bank:view:list"));
                out.add(view(page, t, "all", "bank:view:all"));
                out.add(view(page, t, "kind", "bank:kind:ARMOUR"));
                out.add(view(page, t, "menu-filter", "bank:menu:filter"));
                out.add(view(page, t, "menu-banks", "bank:menu:banks"));
                // the sort menu belongs to the list layout
                out.add(shot(page, t, "menu-sort", WAIT, List.of("bank:view:list", "bank:menu:sort"), null, List.of()));
                out.add(view(page, t, "menu-view", "bank:menu:view"));
                out.add(view(page, t, "item", "bank:item"));
                out.add(view(page, t, "sell", "bank:sell"));
            }
            case "crafting" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(tip(page, t));
                out.add(view(page, t, "end", "craft:end"));
                out.add(view(page, t, "picker", "craft:picker"));
                out.add(view(page, t, "shop", "craft:shop"));
                out.add(view(page, t, "saved", "craft:saved"));
                out.add(view(page, t, "suggestions", "craft:suggestions"));
                out.add(view(page, t, "sets", "craft:sets"));
                out.add(view(page, t, "road", "craft:road"));
                out.add(view(page, t, "road-gather", "craft:road:" + ROAD_GATHER));
            }
            case "search" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(shot(page, t, "more", SEARCH_WAIT, List.of("search:more"), null, List.of()));
                out.add(shot(page, t, "card", SEARCH_WAIT, List.of("search:recent"), null, List.of()));
                out.add(shot(page, t, "card-tip", SEARCH_WAIT, List.of("search:recent"), "hit", List.of()));
                for (String m : new String[] {"recent", "marks", "tracked", "sort", "filter"}) {
                    out.add(shot(page, t, "menu-" + m, SEARCH_WAIT, List.of("search:menu:" + m), null, List.of()));
                }
                out.add(shot(page, t, "stats", SEARCH_WAIT, List.of("search:stats"), null, List.of()));
            }
            case "box" -> {
                out.add(shot(page, t, null, w, List.of("window:box"), null, List.of()));
                out.add(shot(page, t, "stats", WAIT, List.of("window:box", "box:stats"), null, List.of()));
            }
            case "mounts", "road", "places" -> out.add(shot(page, t, null, w, List.of(), null, List.of()));
            case "ai" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                out.add(view(page, t, "reads", "ai:reads"));
            }
            case "settings" -> {
                out.add(shot(page, t, null, w, List.of(), null, List.of()));
                for (String p : SETTING_PAGES) {
                    out.add(view(page, t, p, "settings:cat:" + p));
                }
                out.add(view(page, t, "changed", "settings:changed"));
            }
            case "update" -> {
                out.add(shot(page, t, null, w, List.of("window:update"), null, List.of()));
                out.add(view(page, t, "changelog", "window:changelog"));
            }
            default -> throw new IllegalArgumentException("unknown page " + page);
        }
        return out;
    }

    /** The page as it opens with its first element that has a tooltip hovered. */
    private static Shot tip(String page, int t) {
        return shot(page, t, "tip", WAIT, List.of(), "hit", List.of());
    }

    /** A view of one step, no hover, no close-up. */
    private static Shot view(String page, int t, String state, String step) {
        return shot(page, t, state, WAIT, List.of(step), null, List.of());
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static Shot shot(String page, int t, String state, int ticks, List<String> steps, String hover, List<String> closes) {
        return new Shot(page, t, state, ticks, steps, hover, closes);
    }

    /**
     * The pictures of every page ({@code only} "all") or of one page, page after page.
     * {@code tabNames}: each menu tab number's name ("home", "craft" …).
     */
    static List<Shot> plan(String[] tabNames, String only) {
        List<Shot> out = new ArrayList<>();
        List<String> tabs = List.of(tabNames);
        for (Map.Entry<String, String> p : PAGES.entrySet()) {
            if (!only.equals("all") && !only.equals(p.getKey())) {
                continue;
            }
            int t = tabs.indexOf(p.getValue());
            if (t < 0) {
                throw new IllegalStateException("no menu tab " + p.getValue() + " for page " + p.getKey());
            }
            out.addAll(views(p.getKey(), t, out.isEmpty() ? FIRST_WAIT : 0));
        }
        return out;
    }

    private enum Phase { START, ENTER, WAIT, DRAIN, DONE }

    /** What the wait in {@link Phase#WAIT} leads to. */
    private enum Next { STEP, HOVER, GRAB }

    private final Stage stage;
    private final List<Shot> shots;
    /** The language to switch to (as {@link Stage#lang}), or -1 to keep the one on. */
    private final int lang;
    /** The menu scale to draw at (2 to 4), or -1 to keep the one on. */
    private final int scale;
    private final boolean hideName;

    private Phase phase = Phase.START;
    private Next then;
    private int next;
    private int step;
    private int pass;
    private int left;
    private int count;
    private int skipped;
    private int rejected;
    private boolean hovering;
    // the page being taken, for its line
    private String pageNow;
    private int pageTaken;
    private int pageSkipped;
    private final List<String> pageRejected = new ArrayList<>();
    private String code = "en";
    private boolean escaped;

    // what to put back
    private int savedLang;
    private boolean langChanged;
    private int savedScale;
    private boolean scaleChanged;
    private boolean flagsOn;
    private boolean opened;
    private boolean putBack;

    ShotRun(Stage stage, List<Shot> shots, int lang, boolean hideName) {
        this(stage, shots, lang, -1, hideName);
    }

    ShotRun(Stage stage, List<Shot> shots, int lang, int scale, boolean hideName) {
        this.stage = stage;
        this.shots = List.copyOf(shots);
        this.lang = lang;
        this.scale = scale;
        this.hideName = hideName;
    }

    /** Esc while capturing: everything goes back on the next tick. */
    void escape() {
        escaped = true;
    }

    boolean finished() {
        return phase == Phase.DONE;
    }

    /** Pictures taken so far. */
    int count() {
        return count;
    }

    /** Views skipped so far: nothing to show for them. */
    int skipped() {
        return skipped;
    }

    /** Views left out so far: something open in the frame that their steps did not open. */
    int rejected() {
        return rejected;
    }

    /** One client tick; false once the run is over. */
    boolean tick() {
        if (phase == Phase.DONE) {
            return false;
        }
        try {
            switch (phase) {
                case START -> start();
                case ENTER -> {
                    if (guard()) {
                        enter();
                    }
                }
                case WAIT -> {
                    if (guard() && --left <= 0) {
                        advance();
                    }
                }
                case DRAIN -> {
                    if (stage.pending() <= 0 || --left <= 0) {
                        phase = Phase.DONE;
                        stage.done(count, null, null);
                    }
                }
                default -> { }
            }
        } catch (Throwable t) {
            stop(Stop.ERROR, t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
        return phase != Phase.DONE;
    }

    private void start() {
        savedLang = stage.lang();
        savedScale = stage.scale();
        flagsOn = true;
        stage.flags(true, hideName);
        if (lang >= 0 && lang != savedLang) {
            langChanged = true;
            stage.lang(lang);
        }
        if (scale >= 0 && scale != savedScale) {
            scaleChanged = true;
            stage.scale(scale);
        }
        code = stage.code();
        opened = true;
        stage.open();
        if (shots.isEmpty()) {
            finishCaptures();
        } else {
            phase = Phase.ENTER;
        }
    }

    /** Esc or a menu closed by something else: everything back, and false. */
    private boolean guard() {
        if (escaped) {
            stop(Stop.ESC, null);
            return false;
        }
        if (!stage.showing()) {
            stop(Stop.CLOSED, null);
            return false;
        }
        return true;
    }

    private void enter() {
        if (next >= shots.size()) {
            finishCaptures();
            return;
        }
        Shot s = shots.get(next);
        if (!s.page().equals(pageNow)) {
            endPage();
            pageNow = s.page();
        }
        stage.select(s.tab());
        hovering = false;
        step = 0;
        pass = 0;
        stage.backdrop(0);
        phase = Phase.WAIT;
        if (s.steps().isEmpty()) {
            afterSteps(s);
        } else {
            then = Next.STEP;
            left = STEP_WAIT;
        }
    }

    private void afterSteps(Shot s) {
        stage.settle();
        then = s.hover() != null ? Next.HOVER : Next.GRAB;
        left = Math.max(1, s.ticks());
    }

    private void advance() {
        Shot s = shots.get(next);
        switch (then) {
            case STEP -> {
                if (!stage.act(s.steps().get(step))) {
                    skip();
                    return;
                }
                if (++step < s.steps().size()) {
                    left = STEP_WAIT;
                } else {
                    afterSteps(s);
                }
            }
            case HOVER -> {
                if (!stage.hover(s.hover())) {
                    skip();
                    return;
                }
                hovering = true;
                then = Next.GRAB;
                left = HOVER_WAIT;
            }
            case GRAB -> grab(s);
            default -> { }
        }
    }

    /** Nothing to show for this view: on to the next, no number taken. */
    private void skip() {
        skipped++;
        pageSkipped++;
        leave();
    }

    /** On to the next view without a picture. */
    private void leave() {
        unhover();
        next++;
        phase = Phase.ENTER;
    }

    private void grab(Shot s) {
        if (pass == 0) {
            String why = stage.unclean(s);
            if (NOTHING.equals(why)) {
                skip();
                return;
            }
            if (why != null) {
                // not the view asked for: left out and reported, never saved
                rejected++;
                pageRejected.add((s.state() == null ? "main" : s.state()) + ": " + why);
                leave();
                return;
            }
            count++;
            pageTaken++;
        }
        java.util.Map<String, String> closes = new java.util.LinkedHashMap<>();
        for (String part : s.closes()) {
            closes.put(part, s.close(code, count, part));
        }
        stage.capture(s.file(code, count), pass, closes);
        if (++pass < Math.max(1, stage.passes())) {
            stage.backdrop(pass);               // the same view on the next backdrop
            left = BACKDROP_WAIT;
            return;
        }
        unhover();
        next++;
        if (next >= shots.size()) {
            finishCaptures();
        } else {
            phase = Phase.ENTER;
        }
    }

    /** The mouse away from what it hovered, so the next view starts without a tooltip. */
    private void unhover() {
        if (hovering) {
            hovering = false;
            stage.hover(null);
        }
    }

    /** The page being taken is done: its line. */
    private void endPage() {
        if (pageNow != null) {
            stage.page(pageNow, pageTaken, pageSkipped, List.copyOf(pageRejected));
        }
        pageNow = null;
        pageTaken = 0;
        pageSkipped = 0;
        pageRejected.clear();
    }

    /** Every picture taken: the last page's line, the game back at once, then the last files written. */
    private void finishCaptures() {
        endPage();
        putBack();
        left = DRAIN;
        phase = Phase.DRAIN;
    }

    private void stop(Stop why, String detail) {
        putBack();
        phase = Phase.DONE;
        try {
            stage.done(count, why, detail);
        } catch (Throwable ignored) {
            // the message only
        }
    }

    /** The hover, the screen, the size, the language and the render flags back; each on its own, so one failing leaves the others. */
    private void putBack() {
        if (putBack) {
            return;
        }
        putBack = true;
        try {
            unhover();
        } catch (Throwable t) {
            System.out.println("[WynnLabs] screenshots: could not move the mouse back: " + t);
        }
        if (opened) {
            try {
                stage.restore();
            } catch (Throwable t) {
                System.out.println("[WynnLabs] screenshots: could not restore the screen: " + t);
            }
        }
        if (scaleChanged) {
            try {
                stage.scale(savedScale);
            } catch (Throwable t) {
                System.out.println("[WynnLabs] screenshots: could not restore the menu scale: " + t);
            }
        }
        if (langChanged) {
            try {
                stage.lang(savedLang);
            } catch (Throwable t) {
                System.out.println("[WynnLabs] screenshots: could not restore the language: " + t);
            }
        }
        if (flagsOn) {
            try {
                stage.flags(false, false);
            } catch (Throwable t) {
                System.out.println("[WynnLabs] screenshots: could not restore the render flags: " + t);
            }
        }
    }
}
