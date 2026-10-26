package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.endgame.AspectBook;
import io.github.anilken.wynnlabs.endgame.ItemIcons;
import io.github.anilken.wynnlabs.endgame.LootrunLog;
import io.github.anilken.wynnlabs.endgame.PoolView;
import io.github.anilken.wynnlabs.endgame.RaidLog;
import io.github.anilken.wynnlabs.endgame.RaidPool;
import io.github.anilken.wynnlabs.endgame.WynnventoryPools;
import io.github.anilken.wynnlabs.model.WeeklyPool;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The J menu's Endgame tab (the approved build/endgame-mockup/index.html): one page, "Bu hafta
 * senin için" on top (the raid that drops the most aspects you lack, the camp with the most
 * mythics), then this week's raids, lootrun camps and your aspects; a raid's or camp's pool and the
 * Kayıt (this session, pulls since a mythic, every run) open in a drawer over it. It only reads
 * {@link RaidLog}, {@link RaidPool}, {@link LootrunLog} and {@link AspectBook}; its view state lives
 * here while the game runs and is never saved. Nothing in it acts in the game. {@code RoadmapScreen}
 * only routes to it, as for {@link CraftingTab}.
 *
 * <p>The parts are drawn by the panels: {@link EndgameTitleBar} (the session chip), {@link EndgameWeek}
 * (the card, the raids, the camps), {@link EndgameAspectList}, {@link EndgamePoolsView} (a pool) and
 * {@link EndgameLog} (the Kayıt, with {@link EndgameRaids} and {@link EndgameRecentRuns}). What it
 * reads is {@link EndgameData}; the preview's fake data is {@link EndgamePreviewData}.
 */
public final class EndgameTab {

    public static final EndgameTab INSTANCE = new EndgameTab();

    /** The compact layout's sections, in the order of its three cards. */
    static final int SEC_RAIDS = 0;
    static final int SEC_LOOTRUNS = 1;
    static final int SEC_ASPECTS = 2;

    /** The aspect list's filters. */
    static final int F_ALL = 0;
    static final int F_MISSING = 1;
    static final int F_OWNED = 2;
    static final int F_POOL = 3;

    /** The view switch on the title line. */
    static final int VIEW_SUMMARY = 0;
    public static final int VIEW_POOLS = 1;
    /** The sub-tabs of Havuzlar. */
    static final int TAB_RAIDS = 0;
    static final int TAB_CAMPS = 1;

    // view state: kept while the game runs, never saved (the preview tools read some of it by name)
    int section = SEC_RAIDS;
    int filter = F_ALL;
    String pinned;
    private String character = "";
    int listScroll;
    int listScrollMax;
    /** The aspect list's bar. */
    final ScrollBar listBar = new ScrollBar();
    int[] listRect;
    /** Where the mythic camps line was drawn last frame, for the preview's tooltip shot; null when not drawn. */
    int[] mythicRect;

    // view state of the pools: kept while the game runs, never saved
    int view = VIEW_SUMMARY;
    int poolTab = TAB_RAIDS;
    String litRaid;
    String litCamp;
    boolean classOnly;
    final int[][] poolScroll = new int[2][5];
    /** Where each item row was drawn this frame, for the preview's tooltip shot. */
    final Map<String, int[]> rowRects = new LinkedHashMap<>();

    // the frame the panels draw into
    final List<EndgamePanel.Act> acts = new ArrayList<>();
    final List<EndgamePanel.Tip> tips = new ArrayList<>();
    Font font;
    long now;
    EndgameData d;
    private long dataAt;

    // the panels
    private final EndgameTitleBar titleBar = new EndgameTitleBar(this);
    private final EndgameWeek week = new EndgameWeek(this);
    private final EndgameRaids raids = new EndgameRaids(this);
    private final EndgameRecentRuns runs = new EndgameRecentRuns(this);
    private final EndgameLog log = new EndgameLog(this, raids, runs);
    final EndgameAspectList aspectList = new EndgameAspectList(this);
    final EndgamePoolsView pools = new EndgamePoolsView(this);

    // the drawer: a raid's or camp's pool, or the Kayıt (made with the menu on its first opening)
    private UiDrawer drawer;
    /** What the drawer shows: {@link #D_POOL} or {@link #D_LOG}; and, for a pool, which. */
    private int drawerKind;
    static final int D_POOL = 1;
    static final int D_LOG = 2;
    private boolean drawerRaid;
    private String drawerRegion;
    /** The drawer's own click and hover areas start here in {@link #acts} and {@link #tips}. */
    private int drawerActs;
    private int drawerTips;
    private final List<int[]> drawerBoxes = new ArrayList<>();
    private boolean compactNow;
    private boolean largeNow;

    private EndgameTab() {}

    // =====================================================================================
    // drawing

    /**
     * Draws the tab's page and its hover tooltips.
     *
     * @param x       the page's left edge, GUI pixels
     * @param y       the page's top edge, under the title line
     * @param w       the page's width (516 at the standard size, 358 compact)
     * @param h       the page's height (299 standard, 179 compact)
     * @param titleY  the title line's y, for the session and streak pills on its right
     * @param frame   the panel {x, y, w, h}
     * @param screenW the screen's width, GUI pixels
     * @param mx      mouse x
     * @param my      mouse y
     * @param pt      partial tick
     */
    void draw(GuiGraphics g, int x, int y, int w, int h, int titleY, int[] frame, int screenW, int mx, int my, float pt) {
        font = UiFont.get();                            // the menu's font, as on every other tab (setting: webFont)
        now = System.currentTimeMillis();
        acts.clear();
        tips.clear();
        listRect = null;
        listBar.place(0, 0, 0, 1, 1, 0);
        mythicRect = null;
        pools.newFrame();
        rowRects.clear();
        String who = Profile.id() == null ? "" : Profile.id();
        if (!who.equals(character)) {
            character = who;                            // another character: its own picked aspect
            pinned = null;
            listScroll = 0;
        }
        if (d == null || now - dataAt > 250) {
            d = EndgameData.read(now, classOnly);
            dataAt = now;
        }
        if (pinned != null && AspectBook.byName(pinned) == null) {
            pinned = null;
        }
        // the panel's rail tells the size: an icon rail when compact, a wide panel when large
        boolean compact = x - frame[0] - 12 < MenuLayout.RAIL;
        boolean large = !compact && (frame[2] > 640 || frame[3] > 344);
        compactNow = compact;
        largeNow = large;
        boolean open = drawerOpen();
        // an open drawer owns the mouse: the page draws as if the mouse were away
        int pmx = open ? -1 : mx;
        int pmy = open ? -1 : my;
        titleBar.chip(g, x, titleY, w, compact, pmx, pmy);
        if (compact) {
            week.compact(g, x, y, w, h, pmx, pmy);
        } else {
            week.wide(g, x, y, w, h, large, pmx, pmy);
        }
        drawerActs = acts.size();
        drawerTips = tips.size();
        drawerBoxes.clear();
        if (open) {
            g.nextStratum();
            drawer.page(x, y, w, h);
            drawer.draw(g, mx, my, drawerBoxes);
            // what scrolled out of the drawer's body takes no click and shows no tooltip
            int[] b = drawer.body;
            if (b != null && drawerKind == D_LOG) {
                acts.subList(drawerActs, acts.size()).removeIf(a -> a.y() + a.h() <= b[1] || a.y() >= b[1] + b[3]);
                tips.subList(drawerTips, tips.size()).removeIf(t -> t.y() + t.h() <= b[1] || t.y() >= b[1] + b[3]);
            }
        }
        tooltip(g, mx, my, screenW);
    }

    // ---- the drawer ----

    private boolean drawerOpen() {
        return drawer != null && drawer.showing() && drawerKind != 0;
    }

    /** The menu the drawer belongs to (the screen open now), made the first time. */
    private UiDrawer drawer() {
        if (drawer == null && net.minecraft.client.Minecraft.getInstance().screen instanceof RoadmapScreen rs) {
            drawer = new UiDrawer(rs);
        }
        return drawer;
    }

    /** Opens a raid's or camp's pool in the drawer (a row, the card's "Havuzu gör ›"). */
    void openPool(boolean raid, String region) {
        if (drawer() == null) {
            return;
        }
        drawerKind = D_POOL;
        drawerRaid = raid;
        drawerRegion = region;
        view = VIEW_POOLS;
        poolTab = raid ? TAB_RAIDS : TAB_CAMPS;
        pick(raid, region);
        PoolView.Region r = pools.best(raid, region);
        String title = r == null ? L.t(raid ? "wynnlabs.endgame.raids" : "wynnlabs.endgame.camps") : EndgamePoolsView.regionName(r);
        drawer.showWide(title, (g, x, y, w, top, bottom, mx, my) -> {
            pools.regionPane(g, drawerRaid, drawerRegion, x, y, w, bottom - top, compactNow, largeNow, mx, my);
            return bottom - top;
        });
    }

    /** Opens the Kayıt: this session, the pulls since a mythic, every raid, the recent runs. */
    void openLog() {
        if (drawer() == null) {
            return;
        }
        drawerKind = D_LOG;
        view = VIEW_SUMMARY;
        drawer.show(L.t("wynnlabs.endgame.log_title"), log::draw);
    }

    /** Closes the drawer (a click outside it, its ✕, Esc); true when one was open. */
    boolean closeDrawer() {
        if (!drawerOpen()) {
            return false;
        }
        drawer.close();
        drawerKind = 0;
        view = VIEW_SUMMARY;
        return true;
    }

    /** Opens Havuzlar on this sub-tab at this region (the links from Özet): now its pool in the drawer. */
    void openPools(int tab, String region) {
        openPool(tab == TAB_RAIDS, region);
    }

    /** Lights this region and starts its lists at the top. */
    void pick(boolean raid, String region) {
        if (raid) {
            litRaid = region;
        } else {
            litCamp = region;
        }
        int[] sc = poolScroll[raid ? TAB_RAIDS : TAB_CAMPS];
        for (int i = 0; i < EndgamePoolsView.S_REGIONS; i++) {
            sc[i] = 0;
        }
    }

    /** Back from a picked aspect to the recommendation. */
    void unpin() {
        pinned = null;
    }

    private void tooltip(GuiGraphics g, int mx, int my, int screenW) {
        List<Component> tip = null;
        for (int i = tips.size() - 1; i >= (drawerOpen() ? drawerTips : 0); i--) {
            if (tips.get(i).in(mx, my)) {
                tip = tips.get(i).lines();
                break;
            }
        }
        if (tip == null || tip.isEmpty()) {
            return;
        }
        Glossary.draw(g, font, tip, mx, my, screenW);
    }

    // =====================================================================================
    // input

    /**
     * A click on the page: true when the tab used it. With the drawer open it owns the mouse:
     * inside, its ✕, its bar and its own areas; outside, the click only closes it.
     */
    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (drawerOpen()) {
            boolean inside = drawerBoxes.stream().anyMatch(b -> mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]);
            if (!inside) {
                closeDrawer();
                return true;
            }
            var ev = new net.minecraft.client.input.MouseButtonEvent(mx, my, new net.minecraft.client.input.MouseButtonInfo(button, 0));
            if (drawer.clicked(ev) || pools.barPressed(mx, my)) {
                if (!drawer.showing()) {
                    drawerKind = 0;
                    view = VIEW_SUMMARY;
                }
                return true;
            }
            for (int i = acts.size() - 1; i >= drawerActs; i--) {
                if (acts.get(i).in(mx, my)) {
                    acts.get(i).run().run();
                    return true;
                }
            }
            return true;
        }
        if (listBar.mouseClicked(mx, my)) {
            listScroll = listBar.offset();
            return true;
        }
        for (int i = drawerActs - 1; i >= 0; i--) {
            EndgamePanel.Act a = acts.get(i);
            if (a.in(mx, my)) {
                a.run().run();
                return true;
            }
        }
        return false;
    }

    /** A drag with a list's bar held; true while one is. */
    boolean barDragged(double my) {
        if (drawer != null && drawer.drag(0, my)) {
            return true;
        }
        if (pools.barDragged(my)) {
            return true;
        }
        if (listBar.mouseDragged(my)) {
            listScroll = listBar.offset();
            return true;
        }
        return false;
    }

    void barReleased() {
        if (drawer != null) {
            drawer.release();
        }
        pools.barReleased();
        listBar.mouseReleased();
    }

    /** The wheel: over the drawer its lists (a pool's columns) or its body; over the page the aspect list. */
    boolean mouseScrolled(double mx, double my, double v) {
        if (drawerOpen()) {
            if (!pools.mouseScrolled(mx, my, v)) {
                drawer.scrolled(mx, my, v);
            }
            return true;
        }
        int[] r = listRect;
        if (r == null || mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3] || listScrollMax <= 0) {
            return false;
        }
        listScroll = Math.max(0, Math.min(listScrollMax, listScroll - (int) Math.signum(v)));
        return true;
    }

    /** For the preview's layout check: the clickable areas drawn last frame. */
    public List<LayoutCheck.Area> clickAreas() {
        List<LayoutCheck.Area> out = new ArrayList<>();
        for (EndgamePanel.Act a : acts) {
            out.add(new LayoutCheck.Area(a.x(), a.y(), a.w(), a.h(), a.what()));
        }
        return out;
    }

    /** The menu was closed (view state is kept for the next opening). */
    void closed() {
        closeDrawer();                                  // the menu reopens on the page, the drawer closed
        d = null;                                       // read afresh on the next opening
        ItemIcons.clear();                              // the pack may change before the next opening
    }

    /** For the preview: show this compact section. */
    void previewSection(int s) {
        section = Math.max(0, Math.min(2, s));
    }

    // ---- preview ----

    /**
     * For the no-game preview (LayoutMatrix): the fake data of design decision 9 for the
     * character in play, through the preview hooks of the logs, the pool and the aspect book.
     */
    static void preview() {
        EndgamePreviewData.load();
        INSTANCE.pinned = null;
        INSTANCE.filter = F_ALL;
        INSTANCE.listScroll = 0;
        INSTANCE.resetPools();
        INSTANCE.d = null;
    }

    /** For the preview's empty-state shot: no runs, no pool, aspects never read (fake character char0002). */
    static void previewEmpty() {
        EndgamePreviewData.loadEmpty();
        INSTANCE.pinned = null;
        INSTANCE.filter = F_ALL;
        INSTANCE.listScroll = 0;
        INSTANCE.d = null;
    }

    /** For the preview's fallback shot: the data of {@link #preview()} without Wynnventory's pools (only the in-game pool). */
    static void previewFallback() {
        preview();
        WynnventoryPools.preview(WeeklyPool.NONE, WeeklyPool.NONE);
        INSTANCE.d = null;
    }

    /** For the preview: the aspect list's pool filter on (or back to all). */
    public void previewPoolFilter(boolean on) {
        filter = on ? F_POOL : F_ALL;
        listScroll = 0;
    }

    /** For the preview: this aspect picked into the card, as a click on its row does (null: the recommendation). */
    void previewPick(String aspect) {
        pinned = aspect;
    }

    /** For the preview: where the mythic camps line was drawn last frame {x, y, w, h}; null when not drawn. */
    int[] previewMythicLine() {
        return mythicRect;
    }

    /** For the preview: a pool in the drawer on this sub-tab at this region (null: the best), or none when {@code tab} is negative. */
    public void previewPools(int tab, String region) {
        if (tab < 0) {
            closeDrawer();
            return;
        }
        openPool(tab == TAB_RAIDS, region);
    }

    /** For the preview: the Kayıt drawer. */
    void previewLog() {
        openLog();
    }

    /** For the preview: where an item row was drawn last frame {x, y, w, h} (a shiny one: its name and "*"); null when not drawn. */
    int[] previewRow(String name) {
        return rowRects.get(name);
    }

    /** Back to the first-time view state of the pools (Özet, Raidler, no region lit, the class chip off). */
    private void resetPools() {
        view = VIEW_SUMMARY;
        poolTab = TAB_RAIDS;
        litRaid = null;
        litCamp = null;
        classOnly = false;
        for (int[] sc : poolScroll) {
            java.util.Arrays.fill(sc, 0);
        }
    }
}
