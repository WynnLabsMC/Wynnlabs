package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.BoxVerdict;
import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Listings;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.search.BoxInsight;
import io.github.anilken.wynnlabs.search.BoxMarket;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The box contents window: every item an unidentified gear box can hold, with what the Trade
 * Market lists for it now (Wynnventory's listings: count, prices, roll percentages); or, for any
 * other tradable item, that one item's listings. A soft card over the container screen the item
 * is in: the title row (the thing, when it was read, Search, Refresh, close), the summary card
 * (the numbers, the verdict or your roll, the answer line), the filters, then two panes (possible
 * items or stats | listings); a small window shows one pane at a time. Esc goes back to the
 * container, which stays open behind it; Search opens over it and Esc there comes back here.
 * Information only: nothing is clicked, identified, listed or sent to the server. Requests go
 * through the mod's one Wynnventory worker ({@link Listings}); the rows are rebuilt when an answer
 * arrives or a choice changes, never per frame.
 */
public final class BoxScreen extends Screen {

    /** One item instead of a box: its row, its own overall roll (null if none shown), unidentified, its own stat rolls. */
    record Single(BoxMarket.Item item, Float ownRoll, boolean unidentified, List<BoxMarket.StatRoll> ownStats) {

        Single(BoxMarket.Item item, Float ownRoll, boolean unidentified) {
            this(item, ownRoll, unidentified, List.of());
        }
    }

    /** The box; null for one item. */
    final ItemView.Box box;
    /** The one item; null for a box. */
    final Single single;
    final net.minecraft.world.item.ItemStack singleIcon;
    private final Screen parent;
    /** "RELIK:LEGENDARY:70-80" or "item:Name": what a refresh is for. */
    private final String boxKey;
    final List<BoxMarket.Item> items;
    final BoxItemsPanel left = new BoxItemsPanel(this);
    final BoxListingsPanel right = new BoxListingsPanel(this);
    final BoxBar bar = new BoxBar(this);
    /** Your own rolls by stat key: the % for the arrows, the roll for the Stats view's chips. */
    final Map<String, Double> own = new HashMap<>();
    private final Map<String, BoxMarket.StatRoll> ownRolls = new HashMap<>();

    BoxMarket.Filter filter = BoxMarket.Filter.ALL;
    BoxMarket.Sort sort = BoxMarket.Sort.ROLL;
    /** Only the items that add to your build. */
    boolean buildOnly;
    /** A small window's one-item view: 0 the listings, 1 the stats. */
    int tab;
    /** The row shown on the right, by name. */
    String selected;

    List<BoxMarket.Row> rows = List.of();
    BoxMarket.Summary summary;
    BoxVerdict.Result verdict;
    BoxInsight.Identify identify;
    /** "+N build" by item: the shopping list's gain for what fits your class and level (positive only). */
    private final Map<String, Integer> gains = new HashMap<>();
    /** What the rows were built for: the stores' versions and the choices (compared each frame, nothing built). */
    private int builtListings = -1;
    private int builtPrices = -1;
    private Object[] builtChoices = new Object[6];
    private int verdictFor = -1;
    /** Counts the rebuilds: what was made from the rows (the summary card) is made again when it moves. */
    int generation;
    /** Whether the window is small (one pane at a time), set each frame. */
    boolean compact;

    /** This frame's click areas: {x, y, w, h} and what a click does. */
    record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private List<Component> tip;

    public BoxScreen(ItemView.Box box, Screen parent) {
        super(Component.translatable("wynnlabs.box.title"));
        this.box = box;
        this.single = null;
        this.singleIcon = null;
        this.parent = parent;
        this.boxKey = box.slot().name() + ":" + box.tier().name() + ":" + box.low() + "-" + box.high();
        this.items = BoxBar.items(box);
        // the player's own class and level first: those are what they would look at first
        Klass cls = GameState.current().classType();
        int level = GameState.current().level();
        List<String> ask = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (BoxMarket.Item it : items) {
            if (it.tradable()) {
                boolean mine = (it.klass() == null || it.klass() == Klass.NONE || it.klass() == cls)
                        && Math.abs(it.level() - level) <= 10;
                (mine ? ask : rest).add(it.name());
            }
        }
        ask.addAll(rest);
        Listings.request(ask);
    }

    /** The window for one tradable item; an unidentified piece starts on its unidentified listings. */
    BoxScreen(Single one, net.minecraft.world.item.ItemStack icon, Screen parent) {
        super(Component.translatable("wynnlabs.box.title"));
        this.box = null;
        this.single = one;
        this.singleIcon = icon;
        this.parent = parent;
        this.boxKey = "item:" + one.item().name();
        this.items = List.of(one.item());
        this.selected = one.item().name();
        for (BoxMarket.StatRoll s : one.ownStats()) {
            if (s.percent() != null) {
                own.put(s.key(), s.percent());
                ownRolls.put(s.key(), s);
            }
        }
        if (one.unidentified()) {
            filter = BoxMarket.Filter.UNID;
        }
        Listings.request(List.of(one.item().name()));
    }

    @Override
    protected void init() {
        UiFont.apply(this);
        io.github.anilken.wynnlabs.search.ItemIndex.startLoading(io.github.anilken.wynnlabs.core.Platform.get().gameDir(),
                io.github.anilken.wynnlabs.core.Dirs.config().resolve("chests.json"));
        super.init();
    }

    /** The rows again when an answer came, a choice changed, or the prices moved. */
    private void rebuild() {
        boolean live = Listings.live();
        if (!rolled()) {
            filter = BoxMarket.Filter.ALL;
            sort = BoxMarket.Sort.PRICE;
        }
        Object[] c = builtChoices;
        if (builtListings == Listings.version && builtPrices == Prices.version && c[0] == filter && c[1] == sort
                && Boolean.valueOf(live).equals(c[2]) && Boolean.valueOf(buildOnly).equals(c[3]) && java.util.Objects.equals(c[4], selected)) {
            return;
        }
        builtListings = Listings.version;
        builtPrices = Prices.version;
        c[0] = filter;
        c[1] = sort;
        c[2] = live;
        c[3] = buildOnly;
        c[4] = selected;
        BoxChips.forget();
        generation++;
        Map<String, BoxMarket.Held> held = new HashMap<>();
        Map<String, Double> past = new HashMap<>();
        gains.clear();
        for (BoxMarket.Item it : items) {
            BoxMarket.Held h = Listings.held(it.name());
            if (h != null) {
                held.put(it.name(), h);
            }
            Prices.Price p = it.tradable() ? Prices.of(it.name()) : null;
            if (p != null && p.typical() != null) {
                past.put(it.name(), p.typical());
            }
            Shopping.Pick pick = Shopping.find(it.name());
            if (pick != null && pick.gain() > 0) {
                gains.put(it.name(), (int) Math.max(1, Math.round(pick.gain())));
            }
        }
        List<BoxMarket.Row> all = BoxMarket.rows(items, held, past, live, filter, sort);
        if (buildOnly && box != null && !gains.isEmpty()) {
            all = all.stream().filter(r -> gains.containsKey(r.name())).toList();
        }
        rows = all;
        if (box != null && (verdictFor != Prices.version || verdict == null)) {
            verdictFor = Prices.version;
            verdict = BoxVerdict.of(box, GameState.current().classType(), GameState.current().level());
            identify = BoxInsight.identify(verdict.value(), verdict.priced(), verdict.outcomes(), verdict.costLow(), verdict.costHigh(),
                    BoxVerdict.marketFee());
        }
        summary = BoxMarket.summary(rows, verdict == null ? null : verdict.value());
        if ((selected == null || rows.stream().noneMatch(r -> r.name().equals(selected))) && !rows.isEmpty()) {
            selected = rows.get(0).name();
        }
    }

    /** The selected row, or null. */
    BoxMarket.Row selectedRow() {
        for (BoxMarket.Row r : rows) {
            if (r.name().equals(selected)) {
                return r;
            }
        }
        return null;
    }

    /** "+N" for the item when it adds to your build, else 0. */
    int gainOf(String name) {
        return gains.getOrDefault(name, 0);
    }

    /** How many of the box's items add to your build. */
    int fitting() {
        return gains.size();
    }

    /** Your own roll of a stat as a listing's stat (the Stats view's chips); null when not shown. */
    BoxMarket.StatRoll ownRoll(String key) {
        return ownRolls.get(key);
    }

    /** "Unidentified Leggings": the box's own name (Search knows some of them). */
    String boxName() {
        return box == null ? single.item().name() : "Unidentified " + MenuPaint.nice(box.slot().name());
    }

    @Override
    public void tick() {
        // the server closed the container behind (or opened another): nothing to go back to
        if (parent instanceof AbstractContainerScreen<?> c && minecraft != null
                && (minecraft.player == null || minecraft.player.containerMenu != c.getMenu())) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);        // back to the container, still open; nothing is sent
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        MenuScale.render(this, g, mx, my, (x, y) -> draw(g, x, y, pt));
    }

    private void draw(GuiGraphics g, int mx, int my, float pt) {
        long t0 = System.nanoTime();
        super.render(g, mx, my, pt);
        rebuild();
        hits.clear();
        tip = null;
        int ww = Math.min(width - 16, Math.max(340, Math.min(520, width * 4 / 5)));
        int wh = Math.min(height - 16, Math.max(230, Math.min(330, height * 4 / 5)));
        compact = ww < 420;
        int wx = (width - ww) / 2;
        int wy = (height - wh) / 2;
        if (DevHooks.ON && DevHooks.driving()) {
            DevHooks.frame(g.pose(), wx - 4, wy - 4, ww + 8, wh + 8);    // the dev screenshot tool: the window alone, no dim
        } else {
            g.fill(0, 0, width, height, 0x55201810);
        }
        Kit.windowShadow(g, wx, wy, ww, wh);
        g.fill(wx + 4, wy + 4, wx + ww - 4, wy + wh - 3, 0xFFEADCB4);
        Kit.softCard(g, wx, wy, ww, wh);
        // an open menu owns the mouse: the page is drawn with it away, the menu then with it
        boolean popup = bar.menuOpen();
        int pmx = popup ? -1 : mx;
        int pmy = popup ? -1 : my;
        int x = wx + Theme.SP_5;
        int y = wy + Theme.SP_5;
        int w = ww - 2 * Theme.SP_5;
        int bottom = wy + wh - Theme.SP_5;
        y = bar.title(g, x, y, w, pmx, pmy);
        if (box != null && items.isEmpty()) {
            UiEmpty.draw(g, font, x, y, w, bottom - y, compact, "box", L.t("wynnlabs.box.unread_title"),
                    List.of(L.t("wynnlabs.box.unread_step1"), L.t("wynnlabs.box.unread_step2")), null, null, pmx, pmy);
        } else {
            y = page(g, x, y, w, bottom, pmx, pmy);
        }
        if (popup) {
            hits.clear();
            tip = null;
            g.nextStratum();
            bar.drawMenu(g, mx, my);
        }
        if (tip != null) {
            Glossary.draw(g, font, tip, mx, my, width);
        }
        drawNanos = System.nanoTime() - t0;
    }

    /** The last frame's drawing time and the last magnifier drawn {x, y, w, h}: what the preview checks. */
    static long drawNanos;
    final int[] searchRect = new int[4];

    /** Under the title: the live-off notice, the summary card, the filters, the panes. */
    private int page(GuiGraphics g, int x, int y, int w, int bottom, int mx, int my) {
        if (!Listings.live()) {
            int[][] hit = new int[1][];
            y += Ui.notice(g, font, Ui.Level.WARNING, List.of(L.t("wynnlabs.box.off_notice")),
                    new Ui.Action("settings", L.t("wynnlabs.box.off_action")), x, y, w, 40, mx, my, hit) + Theme.SP_4;
            if (hit[0] != null) {
                int[] a = hit[0];
                hit(a[0], a[1], a[2], a[3], () -> minecraft.setScreen(RoadmapScreen.setting("priceApi", this)));
            }
        }
        y += BoxSummary.draw(this, g, x, y, w, mx, my) + Theme.SP_4;
        y = bar.filters(g, x, y, w, mx, my);
        int gap = Theme.SP_5;
        if (single != null) {
            int lw = Math.max(150, Math.min(210, w * 2 / 5));
            if (compact) {
                if (tab == 1) {
                    BoxStatsPanel.draw(this, g, single.item().name(), x, y, w, bottom - y);
                } else {
                    right.draw(g, x, y, w, bottom - y, mx, my);
                }
            } else {
                BoxStatsPanel.draw(this, g, single.item().name(), x, y, lw - Theme.SP_2, bottom - y);
                Kit.hair(g, x + lw + gap / 2 - 1, y, 1, bottom - y, 0x40A08A5A);
                right.draw(g, x + lw + gap, y, w - lw - gap, bottom - y, mx, my);
            }
        } else if (compact) {
            right.draw(g, x, y, w, bottom - y, mx, my);
        } else {
            int lw = Math.max(170, Math.min(250, w * 42 / 100));
            left.draw(g, x, y, lw, bottom - y, mx, my);
            Kit.hair(g, x + lw + gap / 2 - 1, y, 1, bottom - y, 0x40A08A5A);
            right.draw(g, x + lw + gap, y, w - lw - gap, bottom - y, mx, my);
        }
        return bottom;
    }

    // ---- shared pieces ----

    boolean canRefresh() {
        Listings.Refresh r = Listings.refreshOf(boxKey);
        return Listings.live() && (r == null || !r.running());
    }

    void refresh() {
        if (canRefresh()) {
            Listings.refresh(boxKey, items.stream().filter(BoxMarket.Item::tradable).map(BoxMarket.Item::name).toList(), selected);
        }
    }

    Listings.Refresh refreshState() {
        return Listings.refreshOf(boxKey);
    }

    void hit(int x, int y, int w, int h, Runnable action) {
        if (w > 0 && h > 0) {
            hits.add(new Hit(x, y, w, h, action));
        }
    }

    /** This frame's tooltip (the first one asked for wins). */
    void tip(List<Component> lines) {
        if (tip == null) {
            tip = lines;
        }
    }

    /** Whether the items have rolls (gear); ingredients and materials sell without, so no roll filter or sort. */
    boolean rolled() {
        if (single == null) {
            return true;
        }
        io.github.anilken.wynnlabs.search.Entry e = io.github.anilken.wynnlabs.search.ItemIndex.get().byName(single.item().name());
        return e != null ? e.kind() == io.github.anilken.wynnlabs.search.Kind.GEAR
                : single.item().slot() != io.github.anilken.wynnlabs.model.Slot.UNKNOWN;
    }

    /** "72%" or "%72". */
    static String pct(Object v) {
        return L.t("wynnlabs.box.pct", v);
    }

    /** "12 EB", or "?" when unknown (never 0). */
    static String money(Double v) {
        return v == null ? "?" : Prices.fmt(v);
    }

    Font font() {
        return font;
    }

    /** The high-roll threshold (rules boxHighRoll, 80 %). */
    static double highRoll() {
        return Rules.num("boxHighRoll", 80);
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        event = MenuScale.event(event);
        double ex = event.x();
        double ey = event.y();
        if (bar.menuOpen()) {
            bar.menuClick(ex, ey);
            return true;
        }
        if (left.mouseClicked(ex, ey) || right.mouseClicked(ex, ey)) {
            return true;
        }
        for (Hit h : List.copyOf(hits)) {
            if (BoxChips.in(ex, ey, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        double f = MenuScale.factor();
        MouseButtonEvent e = MenuScale.event(event);
        if (bar.menuOpen()) {
            return bar.menuDrag(e.y());
        }
        if (left.mouseDragged(e.y()) || right.mouseDragged(e.y())) {
            return true;
        }
        return super.mouseDragged(e, dx * f, dy * f);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        bar.menuRelease();
        left.mouseReleased();
        right.mouseReleased();
        return super.mouseReleased(MenuScale.event(event));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        x = MenuScale.at(x);
        y = MenuScale.at(y);
        if (bar.menuOpen()) {
            bar.menuWheel(v);
            return true;
        }
        if (!left.mouseScrolled(x, y, v)) {
            right.mouseScrolled(x, y, v);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (bar.menuOpen()) {
            if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                bar.closeMenu();
                return true;
            }
            bar.menuKey(k);
            return true;
        }
        if ((k == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || k == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) && right.toggle()) {
            return true;
        }
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || k == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            if (right.key(k == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }
}
