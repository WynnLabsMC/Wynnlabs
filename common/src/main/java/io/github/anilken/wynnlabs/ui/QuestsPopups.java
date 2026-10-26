package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The quests tab's popups, drawn over the page with the real mouse while the page draws with the
 * mouse away ({@link MenuTab#popupOpen}): the list over the pane in a compact window, the
 * "Önerilen ▾" menu and the kind menu ({@link UiMenu}); their clicks, wheel and keys.
 */
final class QuestsPopups extends MenuPaint implements MenuTab.Popup {

    private final QuestsTab tab;
    /** Where the compact list goes and its rows, from this frame's page. */
    int[] overlayAt;
    List<QuestsRail.Row> overlayRows = List.of();

    QuestsPopups(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    @Override
    public boolean open() {
        return tab.filterOpen || tab.typeOpen || tab.listOpen && menu.layout.compact();
    }

    @Override
    public void close() {
        tab.closeMenus();
    }

    @Override
    public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
        if (tab.listOpen && menu.layout.compact() && overlayAt != null) {
            boxes.add(tab.rail.overlay(g, overlayAt[0], overlayAt[1], overlayAt[2], overlayAt[3], overlayRows, mx, my));
        }
        int[] b = tab.filterOpen ? tab.rail.menu(g, mx, my) : null;
        if (b != null) {
            boxes.add(b);
        }
        b = tab.typeOpen ? tab.types.menu(g, mx, my) : null;
        if (b != null) {
            boxes.add(b);
        }
        if (boxes.isEmpty()) {
            close();                       // nothing to show it under (no chip this frame)
        }
    }

    /** Up, down and Enter in the open menu. */
    @Override
    public boolean key(int key) {
        UiMenu.State st = tab.typeOpen ? tab.typeMenu : tab.filterOpen ? tab.orderMenu : null;
        if (st == null) {
            return false;
        }
        UiMenu.Item it = st.key(key);
        if (it != null) {
            menu.runAction(it.id());
        }
        return true;
    }

    /** A press inside a popup before its rows: a menu's or the list's scroll bar. */
    @Override
    public boolean clicked(net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        if (tab.typeOpen && tab.typeMenu.press(x, y) || tab.filterOpen && tab.orderMenu.press(x, y)) {
            return true;
        }
        if (tab.listOpen && tab.railRect != null && in(x, y, tab.railRect) && tab.railBar.mouseClicked(x, y)) {
            tab.railScroll = tab.railBar.offset();
            return true;
        }
        return false;
    }

    /** The wheel over a popup: the menu under it, or the compact list. */
    @Override
    public void scrolled(double x, double y, double v) {
        if (tab.typeOpen && tab.typeMenu.box() != null && in(x, y, tab.typeMenu.box())) {
            tab.typeMenu.wheel(v);
        } else if (tab.filterOpen && tab.orderMenu.box() != null && in(x, y, tab.orderMenu.box())) {
            tab.orderMenu.wheel(v);
        } else if (tab.listOpen && tab.railRect != null && in(x, y, tab.railRect)) {
            tab.railScroll = Math.max(0, tab.railScroll - (int) Math.signum(v));
        }
    }
}
