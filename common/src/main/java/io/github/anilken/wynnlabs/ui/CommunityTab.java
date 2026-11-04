package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.CommunityList;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu's Community tab: the community build list ({@link CommunityList}, fetched in the
 * background the first time the tab shows) drawn by {@link CommunityPage}. This class is the
 * tab's plumbing: the search box, the click areas of the frame, the open menu (level range,
 * budget, owned share, the stat column) as the tab's popup, the wheel and the scroll bar. Its
 * hovers and "Set as goal" go through the Home's build window host, so an applied build becomes
 * the plan exactly as there. Nothing here acts in the game.
 */
final class CommunityTab extends MenuTab {

    final BuildControls ui = new BuildControls();
    final CommunityPage page = new CommunityPage(this, ui);
    EditBox search;
    UiInput.Hit searchHit;
    /** The small menu layout this frame. */
    boolean compact;
    /** The open menu (a CommunityPage.MENU_*; 0 none) and the box it opens under. */
    int menuOpen;
    int[] menuAt;
    final UiMenu.State menuState = new UiMenu.State();
    private final List<Object[]> menuRows = new ArrayList<>();

    CommunityTab(RoadmapScreen menu) {
        super(menu);
    }

    @Override
    List<EditBox> init() {
        search = UiInput.box(font(), "wynnlabs.community.search_hint", search, v -> page.scroll = 0);
        return List.of(search);
    }

    @Override
    void beginFrame() {
        ui.btns.clear();
    }

    /** The Home's host: hovers into this frame's hits, item icons, the flash line, the plan applied. */
    BuildWindow.Host host() {
        return menu.home.windowHost;
    }

    void flash(String text) {
        menu.flash(text);
    }

    /** A community build's crafted piece: the Crafting tab with its recipe. */
    void craft(RecipeCode.Recipe r) {
        CraftingTab.INSTANCE.load(r);
        menu.openTab(RoadmapScreen.TAB_CRAFT);
    }

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        ui.font = font();
        compact = w < 520;
        page.draw(g, host(), x, y, w, h, mx, my);
    }

    // ---- the open menu ----

    private final Popup popup = new Popup() {
        @Override
        public boolean open() {
            return menuOpen != 0 && menuAt != null;
        }

        @Override
        public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
            menuRows.clear();
            boxes.add(UiMenu.draw(g, font(), UiMenu.Spec.under(menuAt, menu.width, menu.height).rows(10), menuState,
                    page.menuItems(menuOpen), mx, my,
                    r -> menuRows.add(new Object[] {new int[] {r.x(), r.y(), r.w(), r.h()}, r.item().id()})));
        }

        @Override
        public void close() {
            menuOpen = 0;
        }

        @Override
        public boolean clicked(MouseButtonEvent event, boolean doubleClick) {
            if (menuState.press(event.x(), event.y())) {
                return true;
            }
            for (Object[] r : menuRows) {
                if (in(event.x(), event.y(), (int[]) r[0])) {
                    page.pickMenu(menuOpen, Integer.parseInt((String) r[1]));
                    menuOpen = 0;
                    break;
                }
            }
            return true;
        }

        @Override
        public void scrolled(double x, double y, double v) {
            menuState.wheel(v);
        }

        @Override
        public boolean key(int key) {
            UiMenu.Item it = menuState.key(key);
            if (it != null) {
                page.pickMenu(menuOpen, Integer.parseInt(it.id()));
                menuOpen = 0;
            }
            return true;
        }
    };

    @Override
    Popup popup() {
        return popup;
    }

    // ---- the mouse and keys ----

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (searchHit != null && UiInput.clicked(searchHit, search, event, doubleClick, menu::setFocused)) {
            page.scroll = 0;
            return true;
        }
        search.setFocused(false);
        if (page.bar.mouseClicked(event.x(), event.y())) {
            page.scroll = page.bar.offset();
            return true;
        }
        for (int i = ui.btns.size() - 1; i >= 0; i--) {
            BuildControls.Btn b = ui.btns.get(i);
            if (BuildControls.in(event.x(), event.y(), b.x(), b.y(), b.w(), b.h())) {
                if (b.act().equals("menu")) {
                    int m = (int) b.arg();
                    menuOpen = menuOpen == m ? 0 : m;
                    menuState.reset();
                } else {
                    page.click(b.act(), b.arg(), System.currentTimeMillis());
                }
                return true;
            }
        }
        return false;
    }

    @Override
    boolean barDragged(double x, double y) {
        if (menuState.drag(y)) {
            return true;
        }
        if (page.bar.mouseDragged(y)) {
            page.scroll = page.bar.offset();
            return true;
        }
        return false;
    }

    @Override
    void barReleased() {
        menuState.release();
        page.bar.mouseReleased();
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        page.scroll(v);
    }

    @Override
    boolean slashTyped() {
        if (search == null) {
            return false;
        }
        menu.setFocused(search);
        search.setFocused(true);
        return true;
    }
}
