package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Platform;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;
import io.github.anilken.wynnlabs.search.RecentSearches;
import io.github.anilken.wynnlabs.search.SearchCategory;
import io.github.anilken.wynnlabs.search.SearchQuery;
import io.github.anilken.wynnlabs.search.SearchWorker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The search tab ("Ara"). The box across the page with the sort beside it; under it the category
 * strip with counts, the recent searches and "Filter ▾" ({@link SearchStrip}); then the whole
 * catalogue before anything is typed (the road map's missing pieces first), which typing and the
 * strip only narrow ({@link SearchResultsPanel}); the chosen entry's card on the right, or in a
 * drawer on a small window ({@link SearchCardPanel}). Typing runs the query on a background
 * thread ({@link SearchWorker}); the frame draws its last answer. The title row shows the map
 * marks kept and the mobs tracked. Shows only: what it changes is a compass pin, the map's marks
 * and the list of tracked mobs.
 */
final class SearchTab extends MenuTab {

    /** A click area of this frame and what it does. */
    record Click(int[] rect, Runnable run) {}

    final List<Click> clicks = new ArrayList<>();
    final SearchIcons icons = new SearchIcons();
    final SearchResultsPanel results = new SearchResultsPanel(this);
    final SearchCardPanel card = new SearchCardPanel(this);
    final SearchStrip strip = new SearchStrip(this);
    final SearchMenus menus = new SearchMenus(this);
    private final UiDrawer drawer;
    EditBox box;
    /** The query the menu closed with last time, put in the box when it is made. */
    String typedBefore;
    SearchQuery.Sort sort = SearchQuery.Sort.EASIEST;
    SearchCategory category = SearchCategory.ALL;
    /** The category's own kinds the filter menu narrowed it to (empty: all of them). */
    final Set<Kind> kinds = EnumSet.noneOf(Kind.class);
    /** The stat the Stats category lists the items of (null: the list of stats). */
    String stat;
    /** 0: every level, 1: up to mine, 2: mine and the ten below. */
    int levelMode;
    boolean myClass;
    boolean owned;
    boolean onMarket;
    int[] sortAt;
    SearchQuery.Result lastResult;
    private SearchWorker worker;
    private SearchQuery submitted;
    private ItemIndex submittedFor;
    /** Lower-case names of what the player holds (bag and every seen bank page), as of the last query. */
    Set<String> ownedNames = Set.of();
    /** The chosen entry's key (an item's name, a thing's "kind:name"); the row it is on is found again each frame. */
    String selName;
    /** An entry asked for from outside (the search key over a hovered item, a card's link), chosen once the index is there. */
    private String wanted;
    /** The first click area a popup added this frame: a click in the popup looks only from here on. */
    private int popupFrom;
    private int mouseX;
    private int mouseY;
    private UiInput.Hit searchHit;

    SearchTab(RoadmapScreen menu) {
        super(menu);
        drawer = new UiDrawer(menu);
    }

    @Override
    List<EditBox> init() {
        String typed = box == null ? (typedBefore == null ? "" : typedBefore) : box.getValue();
        typedBefore = null;
        box = UiInput.box(font(), "wynnlabs.search.box_hint", null, v -> results.scroll = 0);
        box.setMaxLength(60);
        box.setValue(typed);
        return List.of(box);
    }

    @Override
    void beginFrame() {
        clicks.clear();
    }

    /** Adds a click area for this frame. */
    void click(int x, int y, int w, int h, Runnable run) {
        clicks.add(new Click(new int[] {x, y, w, h}, run));
    }

    /** A tooltip over this box while the mouse is on it. */
    void hint(int x, int y, int w, int h, String text) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            menu.hits.add(new MenuHit(x, y, w, h, null, List.of(Component.literal(text)), null));
        }
    }

    /**
     * Opens on this entry, by its key (an item's name, a thing's "kind:name"): its name in the box
     * and its card shown (the search key over a hovered item, a card's link to a mob or an item).
     */
    void show(String key) {
        wanted = key;
        if (box != null) {
            Entry e = ItemIndex.get().byKey(key);
            box.setValue(e != null ? e.name() : key.substring(key.indexOf(':') + 1));
        }
        selName = key;
        category = SearchCategory.ALL;
        stat = null;
        if (menu.layout != null && menu.layout.compact()) {
            Entry e = ItemIndex.get().byKey(key);
            openDrawer(e == null ? "" : e.item() ? SearchResultsPanel.kindText(e) : SearchThingCard.typeText(e));
        }
    }

    /** The tab is shown: the box takes the keys. */
    void focus() {
        UiInput.focus(box, menu::setFocused);
    }

    @Override
    boolean slashTyped() {
        return UiInput.focus(box, menu::setFocused);
    }

    /** The tab is left or the menu closes: the worker stops, the map's marks go unless kept. */
    void closed() {
        if (worker != null) {
            worker.close();
            worker = null;
        }
        menus.open = SearchMenus.NONE;
        drawer.close();
        card.closed();
    }

    // ---- the filters ----

    int filtersOn() {
        return (category.narrows() ? kinds.size() : 0) + (levelMode != 0 ? 1 : 0) + (myClass ? 1 : 0) + (owned ? 1 : 0)
                + (onMarket ? 1 : 0);
    }

    void clearFilters() {
        kinds.clear();
        levelMode = 0;
        myClass = false;
        owned = false;
        onMarket = false;
        results.scroll = 0;
    }

    void pickCategory(SearchCategory c) {
        category = c;
        kinds.clear();
        if (c != SearchCategory.STAT) {
            stat = null;
        }
        results.scroll = 0;
    }

    void pickStat(String key) {
        stat = key;
        if (key != null) {
            category = SearchCategory.STAT;
        }
        results.scroll = 0;
    }

    // ---- drawing ----

    @Override
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        return strip.title(g, right, y, room, mx, my);
    }

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        mouseX = mx;
        mouseY = my;
        drawer.page(x, y, w, h);
        boolean compact = menu.layout.compact();
        ItemIndex index = ItemIndex.get();
        if (index.missing() != null) {
            ItemIndex.startLoading(Platform.get().gameDir(), Dirs.config().resolve("chests.json"));
        }
        // the box across the page, the sort beside it; the key hint in the box's tooltip
        String sortLabel = L.t(SearchMenus.SORTS[sort.ordinal()]);
        int sortW = Ui.iconChipWidth(font(), "arrow-down", sortLabel, true);
        searchHit = UiInput.search(g, font(), box, L.t("wynnlabs.search.box_hint"), true, x, y, w - sortW - Theme.SP_3, mx, my);
        hint(x, y, w - sortW - Theme.SP_3, Ui.CHIP_H, L.t("wynnlabs.search.keys"));
        int sx = x + w - sortW;
        sortAt = new int[] {sx, y, sortW, Ui.CHIP_H};
        UiMenu.chip(g, font(), "arrow-down", sortLabel, sx, y, menus.open == SearchMenus.SORT, mx, my);
        click(sx, y, sortW, Ui.CHIP_H, () -> menus.toggle(SearchMenus.SORT));

        SearchQuery q = query();
        SearchQuery.Result result = submit(q, index);
        lastResult = result;
        int cy = y + Ui.CHIP_H + Theme.SP_3;
        cy += strip.draw(g, x, cy, w, result, compact, mx, my) + Theme.SP_4;
        int bodyH = y + h - cy;
        int cardW = compact ? 0 : Math.max(180, Math.min(320, (w - Theme.SP_7) * 45 / 100));
        int listW = compact ? w : w - cardW - Theme.SP_7;
        if (index.missing() != null) {
            results.loading(g, x, cy, w, bodyH, "loading".equals(index.missing()), compact, mx, my);
            card.clear();
            return;
        }
        String pick = results.draw(g, x, cy, listW, bodyH, result, compact, mx, my);
        choose(pick, index);
        if (!compact) {
            card.draw(g, x + listW + Theme.SP_7, cy, cardW, bodyH, mx, my);
        } else if (card.entry == null && drawer.showing()) {
            drawer.close();
        }
    }

    /** Opens the drawer on the chosen card (a small window), under the entry's name. */
    void openDrawer(String title) {
        drawer.show(title, card::flow);
    }

    // ---- the popups: a menu, or the card's drawer ----

    private final Popup popups = new Popup() {
        @Override
        public boolean open() {
            return menus.showing() || drawer.open();
        }

        @Override
        public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
            mouseX = mx;
            mouseY = my;
            popupFrom = clicks.size();
            if (menus.showing()) {
                int[] b = menus.draw(g, mx, my);
                if (b != null) {
                    boxes.add(b);
                }
                return;
            }
            drawer.draw(g, mx, my, boxes);
        }

        @Override
        public void close() {
            menus.open = SearchMenus.NONE;
            drawer.close();
        }

        @Override
        public boolean clicked(MouseButtonEvent event, boolean doubleClick) {
            if (menus.showing() && menus.state.press(event.x(), event.y())) {
                return true;
            }
            if (!menus.showing() && drawer.clicked(event, doubleClick)) {
                return true;
            }
            for (int i = clicks.size() - 1; i >= popupFrom; i--) {
                Click c = clicks.get(i);
                if (in(event.x(), event.y(), c.rect())) {
                    c.run().run();
                    return true;
                }
            }
            return menus.showing();
        }

        @Override
        public void scrolled(double x, double y, double v) {
            if (menus.showing()) {
                menus.state.wheel(v);
            } else {
                drawer.scrolled(x, y, v);
            }
        }

        @Override
        public boolean key(int key) {
            return menus.showing() && menus.key(key);
        }
    };

    @Override
    Popup popup() {
        return popups;
    }

    // ---- the query ----

    SearchQuery query() {
        int lv = GameState.current().level();
        int lo = levelMode == 2 ? Math.max(0, lv - 10) : 0;
        int hi = levelMode == 0 || lv <= 0 ? Integer.MAX_VALUE : lv;
        return new SearchQuery(box.getValue(), sort, category.query(kinds), lo, hi, myClass, owned, onMarket,
                category == SearchCategory.STAT ? stat : null);
    }

    /** Sends the query to the worker when it or the index changed; the last finished answer. */
    private SearchQuery.Result submit(SearchQuery q, ItemIndex index) {
        if (index.missing() != null) {
            return SearchQuery.Result.EMPTY;
        }
        if (worker == null) {
            worker = new SearchWorker();
            submitted = null;
        }
        if (!q.equals(submitted) || index != submittedFor) {
            ownedNames = ownedNow();
            Set<String> have = ownedNames;
            Klass k = GameState.current().classType();
            worker.submit(q, new SearchQuery.Facts(k == null ? Klass.NONE : k, n -> have.contains(n.toLowerCase(Locale.ROOT)),
                    n -> {
                        Prices.Price p = Prices.of(n);
                        return p == null ? null : p.typical();
                    }));
            submitted = q;
            submittedFor = index;
        }
        return worker.latest();
    }

    /** What the player holds now: the bag's names and every bank group's, lower case. */
    static Set<String> ownedNow() {
        Set<String> out = new HashSet<>();
        try {
            for (ItemStack s : GameState.current().inventory()) {
                if (!s.isEmpty()) {
                    out.add(ItemJson.clean(s.getHoverName().getString()).toLowerCase(Locale.ROOT));
                }
            }
            for (Vault.Group gr : Vault.groups()) {
                out.add(gr.name().toLowerCase(Locale.ROOT));
            }
        } catch (RuntimeException ignored) {
            // no bag or bank read yet
        }
        return out;
    }

    /** The row the results panel shows as chosen; a new one opens its card, none clears it. */
    private void choose(String pick, ItemIndex index) {
        if (wanted != null && index.byKey(wanted) != null) {
            pick = index.byKey(wanted).key();
            wanted = null;
            RecentSearches.get().add(pick);
        }
        if (pick == null) {
            if (wanted == null) {
                card.clear();                       // nothing listed: no card left from before
            }
        } else if (!pick.equals(card.key())) {
            Entry e = index.byKey(pick);
            if (e != null) {
                card.select(e);
            }
        }
        selName = pick;
    }

    /** The road map's next milestone: the pieces it wants that the player does not hold yet. */
    List<String> missingPieces() {
        List<String> out = new ArrayList<>();
        Set<String> have = ownedNames;
        for (Roadmap.Step s : menu.steps) {
            for (Roadmap.Gear gear : s.gear()) {
                String n = gear.info().name();
                if (!have.contains(n.toLowerCase(Locale.ROOT)) && !out.contains(n)) {
                    out.add(n);
                }
            }
            if (!out.isEmpty()) {
                break;
            }
        }
        return out;
    }

    // ---- input ----

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double ex = event.x();
        double ey = event.y();
        // the two scroll bars first: a press on a thumb starts a drag, on a track it pages
        if (card.barPress(ex, ey) || results.barPress(ex, ey)) {
            return true;
        }
        for (int i = clicks.size() - 1; i >= 0; i--) {
            Click c = clicks.get(i);
            if (in(ex, ey, c.rect())) {
                c.run().run();
                return true;
            }
        }
        return UiInput.clicked(searchHit, box, event, doubleClick, menu::setFocused);
    }

    @Override
    boolean sharedClicks() {
        return false;
    }

    /** A drag with the button held: a scroll bar's thumb follows; true while one is dragged. */
    boolean mouseDragged(double x, double y) {
        return menus.state.drag(y) || drawer.drag(x, y) || card.barDrag(y) || results.barDrag(y);
    }

    void mouseReleased() {
        menus.state.release();
        drawer.release();
        card.barRelease();
        results.barRelease();
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        if (!card.scrolled(x, y, v)) {
            results.scrolled(v);
        }
    }

    @Override
    boolean keyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                results.move(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                results.move(key == GLFW.GLFW_KEY_PAGE_UP ? -8 : 8);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (card.key() != null) {
                    RecentSearches.get().add(card.key());
                    card.pinEasiest();
                }
                return true;
            }
            default -> {
                return UiInput.escape(box, key);
            }
        }
    }

    /** The player's {x, z} now, or null out of the world. */
    static double[] position() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : new double[] {p.getX(), p.getZ()};
    }
}
