package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The bank tab, for finding things: a wide search on top, the kind chips and the menus (tier, which
 * banks, page order), then every page as in the game, side by side under its bank's title. A slot
 * opens its drawer (where it is first, selling last); "To sell ›" opens the list of what to sell.
 */
public final class BankTab extends MenuTab {

    BankTab(RoadmapScreen menu) {
        super(menu);
        drawer = new UiDrawer(menu);
    }

    /** The search over the bank's items. */
    public EditBox search;
    private UiInput.Hit searchHit;
    final BankPages pages = new BankPages();
    private final BankList list = new BankList(pages);
    private final BankDrawers drawers = new BankDrawers(this);
    private final UiDrawer drawer;
    int scroll;
    private int scrollMax;
    private final ScrollBar bar = new ScrollBar();
    private int[] listRect;
    /** Scroll to the first match on the next frame (the search or a filter changed). */
    boolean jump;
    /** Scroll to this page on the next frame (a place picked in the drawer). */
    private String jumpPage;
    /** This frame's click areas on the page: {x, y, w, h} and what they do. */
    final List<Object[]> acts = new ArrayList<>();
    private final BankMenus menus = new BankMenus(this);

    // ---- the tab ----

    @Override
    List<EditBox> init() {
        search = UiInput.box(font(), "wynnlabs.bank.search_hint", search, v -> {
            pages.query = v.trim().toLowerCase(Locale.ROOT);
            jump = true;
        });
        return List.of(search);
    }

    @Override
    boolean slashTyped() {
        return UiInput.focus(search, menu::setFocused);
    }

    @Override
    boolean keyPressed(int key) {
        return UiInput.escape(search, key);
    }

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        drawer.page(x, y, w, h);
        if (!drawer.showing()) {
            pages.picked = null;                // the gold edge goes with the drawer
        }
        acts.clear();
        listRect = null;
        bar.place(0, 0, 0, 1, 1, 0);
        BankIndex idx = BankIndex.get();
        boolean compact = menu.layout.compact();
        if (idx.read == 0) {
            empty(g, idx, x, y, w, h, compact, mx, my);
            return;
        }
        // the search, wide, and the way to what to sell on its right
        String sell = idx.junk.isEmpty() ? null : idx.marketWorth > 0 ? L.t("wynnlabs.bank.sellable", Prices.fmt(idx.marketWorth))
                : L.n("wynnlabs.bank.sellable_kinds", idx.junk.size());
        int linkW = sell == null ? 0 : Ui.iconChipWidth(font(), "shopping-cart", sell, false);
        int cy = y;
        int sw = sell != null ? w - linkW - Theme.SP_5 : w;
        searchHit = UiInput.search(g, font(), search, L.t("wynnlabs.bank.search_hint"), true, x, cy, sw, mx, my);
        if (sell != null) {
            link(g, sell, x + w - linkW, cy, mx, my);
        }
        cy += UiInput.H + Theme.SP_3;
        pages.refresh(idx);
        if (pages.filtered()) {
            cy = matchLine(g, x, cy, w);
        }
        cy = menus.filters(g, idx, x, cy, w, compact, mx, my);
        // the pages
        int bottom = y + h;
        listRect = new int[] {x, cy, w, bottom - cy};
        int cw = w - ScrollBar.W - Theme.SP_2;
        g.enableScissor(x - 2, cy, x + w, bottom);
        int contentH = pages.view == BankPages.VIEW_PAGES ? pages.draw(this, g, idx, x, cy - scroll, cw, cy, bottom, mx, my)
                : list.draw(this, g, idx, x, cy - scroll, cw, cy, bottom, mx, my);
        g.disableScissor();
        scrollMax = Math.max(0, contentH - (bottom - cy));
        if (jump) {
            jump = false;
            scroll = 0;                         // what passes the filter starts at the top
        }
        if (jumpPage != null) {
            int at = pages.pageY(jumpPage);
            jumpPage = null;
            if (at >= 0) {
                scroll = at;
            }
        }
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        bar.place(x + w - ScrollBar.W, cy, bottom - cy, bottom - cy, contentH, scroll);     // in pixels
        bar.draw(g, mx, my);
    }

    /** "Satılabilir: 67 LE" as a chip (it opens the drawer of what to sell). */
    void link(GuiGraphics g, String text, int x, int y, int mx, int my) {
        int lw = Ui.iconChipWidth(font(), "shopping-cart", text, false);
        Ui.iconChip(g, font(), "shopping-cart", text, false, x, y, lw, false, mx, my);
        acts.add(new Object[] {new int[] {x, y, lw, Ui.CHIP_H}, (Runnable) () -> drawers.openSell(drawer)});
    }

    /** "4 results  Champion Helmet — Account 2 · …" (the places on the pages' layout), or that nothing matches. */
    private int matchLine(GuiGraphics g, int x, int y, int w) {
        List<Object[]> m = pages.matchList;
        if (m.isEmpty()) {
            Type.META.draw(g, font(), L.t("wynnlabs.bank.no_match"), x, y, Theme.INK_3);
            return y + Type.META.lineHeight + Theme.SP_3;
        }
        Set<String> parts = new LinkedHashSet<>();
        for (Object[] o : m) {
            parts.add(((BankIndex.Item) o[0]).st().name() + " — " + ((BankIndex.Page) o[1]).name());
            if (parts.size() == 6) {
                break;
            }
        }
        int n = pages.view == BankPages.VIEW_PAGES ? m.size() : pages.shownStacks.size();
        String t = L.n("wynnlabs.bank.results", n) + (pages.view == BankPages.VIEW_PAGES && !pages.query.isEmpty()
                ? "   " + String.join("   ·   ", parts) : "");
        Type.META.draw(g, font(), Type.META.fit(font(), t, w), x, y, Theme.INK_2);
        return y + Type.META.lineHeight + Theme.SP_3;
    }

    // ---- the popups: the open menu or a drawer ----

    private final Popup popup = new Popup() {
        @Override
        public boolean open() {
            return menus.menuOpen != 0 && menus.anchors[menus.menuOpen] != null || drawer.open();
        }

        @Override
        public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
            menus.menuRows.clear();
            drawers.acts.clear();
            if (menus.menuOpen != 0) {
                List<UiMenu.Item> items = menus.menuItems(BankIndex.get());
                boxes.add(UiMenu.draw(g, font(), UiMenu.Spec.under(menus.anchors[menus.menuOpen], menu.width, menu.height).rows(12), menus.menuState, items,
                        mx, my, r -> menus.menuRows.add(new Object[] {new int[] {r.x(), r.y(), r.w(), r.h()}, r.item().id()})));
                return;
            }
            drawer.draw(g, mx, my, boxes);
        }

        @Override
        public void close() {
            menus.menuOpen = 0;
            drawer.close();
        }

        @Override
        public boolean clicked(MouseButtonEvent event, boolean doubleClick) {
            if (menus.menuOpen != 0) {
                if (menus.menuState.press(event.x(), event.y())) {
                    return true;
                }
                for (Object[] r : menus.menuRows) {
                    if (in(event.x(), event.y(), (int[]) r[0])) {
                        menus.pick((String) r[1]);
                        break;
                    }
                }
                return true;
            }
            if (drawer.clicked(event, doubleClick)) {
                return true;
            }
            for (Object[] a : List.copyOf(drawers.acts)) {
                if (drawer.body != null && in(event.x(), event.y(), drawer.body) && in(event.x(), event.y(), (int[]) a[0])) {
                    ((Runnable) a[1]).run();
                    return true;
                }
            }
            return false;                       // the drawer's shared areas: copy the name or the list
        }

        @Override
        public void scrolled(double x, double y, double v) {
            if (menus.menuOpen != 0) {
                menus.menuState.wheel(v);
            } else {
                drawer.scrolled(x, y, v);
            }
        }

        @Override
        public boolean key(int key) {
            if (menus.menuOpen == 0) {
                return false;
            }
            UiMenu.Item it = menus.menuState.key(key);
            if (it != null) {
                menus.pick(it.id());
            }
            return true;
        }
    };

    @Override
    Popup popup() {
        return popup;
    }

    // ---- the mouse ----

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (UiInput.clicked(searchHit, search, event, doubleClick, menu::setFocused)) {
            return true;
        }
        search.setFocused(false);
        if (bar.mouseClicked(event.x(), event.y())) {
            scroll = bar.offset();
            return true;
        }
        for (Object[] a : List.copyOf(acts)) {
            if (in(event.x(), event.y(), (int[]) a[0])) {
                ((Runnable) a[1]).run();
                return true;
            }
        }
        for (Object[] s : pages.slots) {
            if (in(event.x(), event.y(), (int[]) s[0])) {
                open((BankIndex.Item) s[1]);
                return true;
            }
        }
        return false;
    }

    @Override
    boolean barDragged(double x, double y) {
        if (drawer.drag(x, y) || menus.menuState.drag(y)) {
            return true;
        }
        if (bar.mouseDragged(y)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    @Override
    void barReleased() {
        drawer.release();
        menus.menuState.release();
        bar.mouseReleased();
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        if (listRect != null && in(x, y, listRect)) {
            scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.signum(v) * Theme.SP_7 * 2));
        }
    }

    // ---- drawers and jumps ----

    /** A slot clicked: its drawer, the slot marked in gold. */
    private void open(BankIndex.Item it) {
        BankIndex.Page p = pageOf(it);
        if (p == null) {
            return;
        }
        pages.picked = it;
        drawers.openItem(drawer, it, p);
    }

    private static BankIndex.Page pageOf(BankIndex.Item it) {
        for (BankIndex.Group gr : BankIndex.get().groups) {
            for (BankIndex.Page p : gr.pages()) {
                if (p.slots().containsValue(it)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Another place of the drawer's item, or a row of the list to sell: that slot's drawer, the page scrolled to it. */
    void goTo(Vault.Stored st) {
        for (BankIndex.Group gr : BankIndex.get().groups) {
            for (BankIndex.Page p : gr.pages()) {
                for (BankIndex.Item it : p.slots().values()) {
                    if (it.st() == st) {
                        pages.banks[gr.bank().ordinal()] = true;
                        jumpPage = p.key();
                        open(it);
                        return;
                    }
                }
            }
        }
    }

    void openStored(Vault.Stored st) {
        goTo(st);
    }

    /** "Open in Search": the search tab on this name. */
    void openSearch(String name) {
        drawer.close();
        menu.openTab(RoadmapScreen.TAB_SEARCH);
        menu.search.box.setValue(name);
    }

    // ---- nothing read yet ----

    private long bankerAt;
    private Area banker;

    /** The nearest banker from here (a second's answer), or null when unknown. */
    private Area banker() {
        long now = System.currentTimeMillis();
        if (now - bankerAt < 1000) {
            return banker;
        }
        bankerAt = now;
        banker = null;
        double[] pos = SearchTab.position();
        if (pos == null) {
            return null;
        }
        double best = Double.MAX_VALUE;
        for (Entry e : ItemIndex.get().entries()) {
            if (e.kind() != Kind.SERVICE || !e.type().toLowerCase(Locale.ROOT).contains("bank")) {
                continue;
            }
            for (Area a : e.places()) {
                double d = Compass.distance(a, pos);
                if (d < best) {
                    best = d;
                    banker = a;
                }
            }
        }
        return banker;
    }

    private void empty(GuiGraphics g, BankIndex idx, int x, int y, int w, int h, boolean compact, int mx, int my) {
        Area b = banker();
        double[] pos = SearchTab.position();
        UiEmpty.Action action = b == null || pos == null ? null : new UiEmpty.Action("compass",
                L.t("wynnlabs.bank.nearest_banker", RoadmapScreen.dist((int) Math.round(Compass.distance(b, pos)))),
                "/compass at " + b.x() + " " + b.y() + " " + b.z());
        int cols = idx.cols;
        int rows = idx.rows;
        int[] box = UiEmpty.draw(g, font(), x, y, w, h, compact, "building-bank", L.t("wynnlabs.bank.empty_title"),
                List.of(L.t("wynnlabs.bank.empty_step1"), L.t("wynnlabs.bank.empty_step2"), L.t("wynnlabs.bank.empty_step3")), action,
                (gg, gx, gy, gw, gh) -> {
                    int sz = BankPages.slotFor(gw - 2 * Theme.SP_7, cols);
                    int pw = BankPages.gridW(cols, sz);
                    int n = Math.max(1, Math.min(compact ? 2 : 3, (gw - 2 * Theme.SP_7 + Theme.SP_6) / (pw + Theme.SP_6)));
                    int total = n * pw + (n - 1) * Theme.SP_6;
                    gg.enableScissor(gx, gy, gx + gw, gy + gh);
                    for (int k = 0; k < n; k++) {
                        BankPages.ghost(gg, gx + (gw - total) / 2 + k * (pw + Theme.SP_6), gy, cols, rows, sz);
                    }
                    gg.disableScissor();
                }, mx, my);
        if (box != null) {
            menu.hits.add(new MenuHit(box[0], box[1], box[2], box[3], null, List.of(Component.literal("Banker")), action.id()));
        }
    }

    // ---- the rail ----

    /** The rail: how many kinds of items in the bank are worth selling. */
    @Override
    MenuRail.Badge badge() {
        int n = Vault.junk().size();
        return n == 0 ? null : new MenuRail.Badge(MenuRail.Badge.Kind.GOOD, String.valueOf(n), L.t("wynnlabs.screen.rail_badge_bank", n));
    }

    // ---- for the preview ----

    void previewKind(BankIndex.Kind k) {
        menus.setKind(k);
    }

    void previewMenu(int which) {
        menus.menuOpen = which == BankMenus.MENU_TIER && menus.anchors[which] == null ? BankMenus.MENU_FILTER : which;
        menus.menuState.reset();
    }

    void previewItem(String name) {
        for (BankIndex.Group gr : BankIndex.get().groups) {
            for (BankIndex.Page p : gr.pages()) {
                for (BankIndex.Item it : p.slots().values()) {
                    if (it.st().name().equals(name)) {
                        open(it);
                        return;
                    }
                }
            }
        }
    }

    void previewView(int view) {
        pages.view = view;
        scroll = 0;
    }

    void previewSell() {
        drawers.openSell(drawer);
    }

    void previewReset() {
        drawer.close();
        menus.menuOpen = 0;
        pages.kind = null;
        pages.tier = null;
        pages.picked = null;
        scroll = 0;
        search.setValue("");
        search.setFocused(false);
    }
}
