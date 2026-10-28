package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import io.github.anilken.wynnlabs.ui.CraftingTab.Drop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The crafting tab's clicks, wheel and keys: an open popup (a dropdown or a drawer) owns them
 * (inside, its own areas; outside, a click only closes it); else the page's bars and click areas.
 */
final class CraftInput {

    private CraftInput() {}

    static boolean mouseClicked(CraftingTab tab, double mx, double my, int button) {
        int[] sb = tab.suggestionsPanel.searchBox;
        if (tab.suggestSearch != null && tab.suggestSearch.isFocused() && (sb == null || !CraftPanel.in(mx, my, sb[0], sb[1], sb[2], sb[3]))) {
            tab.suggestSearch.setFocused(false);
        }
        if (tab.open != Drop.NONE && tab.dropMenu.press(mx, my)) {
            return true;                            // the tab.open list's tab.scroll bar
        }
        if (tab.popupOpen()) {
            // a popup owns the mouse: inside, its own areas; outside, a click only closes it
            boolean inside = tab.overlayBox != null && CraftPanel.in(mx, my, tab.overlayBox[0], tab.overlayBox[1], tab.overlayBox[2], tab.overlayBox[3]);
            if (!inside) {
                if (tab.open != Drop.NONE) {
                    tab.open = Drop.NONE;
                } else {
                    tab.drawers.close();
                }
                tab.pickSearch.setFocused(false);
                tab.saveName.setFocused(false);
                return true;
            }
            if (tab.open == Drop.NONE && tab.drawers.clicked(mx, my, button)) {
                return true;
            }
            Runnable r = target(tab.overlay, mx, my, button);
            if (r != null) {
                r.run();
            }
            return true;
        }
        if (tab.resultPanel.idBar.mouseClicked(mx, my)) {
            tab.resultPanel.idScroll = tab.resultPanel.idBar.offset();
            return true;
        }
        if (tab.pageBar.mouseClicked(mx, my)) {
            tab.scroll = tab.pageBar.offset();
            return true;
        }
        if (tab.view == CraftingTab.VIEW_SUGGEST && tab.suggestionsPanel.barPressed(mx, my)) {
            return true;
        }
        Runnable r = target(tab.acts, mx, my, button);
        if (r != null) {
            r.run();
            return true;
        }
        return false;
    }

    /**
     * What a click runs: of the areas under it that act on this button, the smallest, so a row's
     * buttons win over the row however they were added; a tip-only area lets the click through.
     */
    private static Runnable target(List<Act> list, double mx, double my, int button) {
        Act best = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Act a = list.get(i);
            Runnable r = button == 1 ? a.right() : a.left();
            if (r != null && a.in(mx, my) && (best == null || a.w() * a.h() < best.w() * best.h())) {
                best = a;
            }
        }
        return best == null ? null : button == 1 ? best.right() : best.left();
    }

    static boolean mouseScrolled(CraftingTab tab, double mx, double my, double v) {
        int step = (int) Math.signum(v);
        if (tab.open != Drop.NONE) {
            tab.dropMenu.wheel(v);
            return true;
        }
        if (tab.drawers.showing()) {
            tab.drawers.scrolled(mx, my, v);
            return true;
        }
        if (tab.view == CraftingTab.VIEW_SUGGEST) {
            tab.suggestionsPanel.scrolled(mx, my, v);
            return true;
        }
        if (tab.resultPanel.cardRect != null && CraftPanel.in(mx, my, tab.resultPanel.cardRect[0], tab.resultPanel.cardRect[1], tab.resultPanel.cardRect[2], tab.resultPanel.cardRect[3])) {
            int next = Math.max(0, Math.min(tab.resultPanel.idMax, tab.resultPanel.idScroll - step));
            if (next != tab.resultPanel.idScroll) {
                tab.resultPanel.idScroll = next;                    // the card's own list scrolls first
                return true;
            }
        }
        tab.scroll = Math.max(0, Math.min(tab.scrollMax, tab.scroll - Theme.SP_7 * step));
        return true;
    }

    /** Enter in the name box saves; Esc closes an tab.open popup first. */
    static boolean keyPressed(CraftingTab tab, int key) {
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && tab.saveName.isFocused()) {
            tab.savedPanel.saveCurrent();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && tab.savedPanel.renaming != null) {
            tab.savedPanel.stopRenaming();                             // Esc leaves renaming, not the menu
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && tab.popupOpen()) {
            tab.open = Drop.NONE;
            tab.drawers.close();
            tab.pickSearch.setFocused(false);
            return true;
        }
        if (tab.open != Drop.NONE) {
            UiMenu.Item it = tab.dropMenu.key(key);
            if (it != null) {
                tab.dropdowns.pickByKey(it);
            }
            return it != null || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
        }
        return false;
    }

    /** True while a text box of the tab takes the keys (so key binds such as J do not fire). */

    /** A drag in the tab: a held tab.scroll bar; true when it did. */
    static boolean mouseDragged(CraftingTab tab, double mx, double my) {
        if (tab.open != Drop.NONE && tab.dropMenu.drag(my)) {
            return true;
        }
        if (tab.drawers.drag(mx, my)) {
            return true;
        }
        if (tab.resultPanel.idBar.mouseDragged(my)) {
            tab.resultPanel.idScroll = tab.resultPanel.idBar.offset();
            return true;
        }
        if (tab.pageBar.mouseDragged(my)) {
            tab.scroll = tab.pageBar.offset();
            return true;
        }
        return tab.view == CraftingTab.VIEW_SUGGEST && tab.suggestionsPanel.barDragged(my);
    }

    static void mouseReleased(CraftingTab tab) {
        tab.dropMenu.release();
        tab.drawers.release();
        tab.pageBar.mouseReleased();
        tab.resultPanel.idBar.mouseReleased();
        tab.suggestionsPanel.barReleased();
    }

    /**
     * For the preview's layout check: the click areas of this frame. With a popup tab.open only its
     * own (it takes every click); a tip-only area is not one.
     */
    static List<LayoutCheck.Area> clickAreas(CraftingTab tab) {
        List<LayoutCheck.Area> out = new ArrayList<>();
        for (Act a : tab.overlayBox != null ? tab.overlay : tab.acts) {
            if (a.left() != null || a.right() != null) {
                out.add(new LayoutCheck.Area(a.x(), a.y(), a.w(), a.h(),
                        a.tip() != null && !a.tip().isEmpty() ? a.tip().get(0).getString() : "act@" + a.x() + "," + a.y()));
            }
        }
        return out;
    }

    static void showTooltip(CraftingTab tab, GuiGraphics g, int mx, int my) {
        List<Component> tip = null;
        if (tab.overlayBox != null) {
            for (int i = tab.overlay.size() - 1; i >= 0 && tip == null; i--) {
                if (tab.overlay.get(i).in(mx, my)) {
                    tip = tab.overlay.get(i).tip();
                    if (tip == null) {
                        return;
                    }
                }
            }
            if (tip == null) {
                return;                             // the page under a popup shows no tooltip
            }
        }
        if (tip == null) {
            for (int i = tab.acts.size() - 1; i >= 0; i--) {
                if (tab.acts.get(i).in(mx, my) && tab.acts.get(i).tip() != null) {
                    tip = tab.acts.get(i).tip();
                    break;
                }
            }
        }
        if (tip == null || tip.isEmpty()) {
            return;
        }
        int maxW = Math.max(160, Math.min(260, tab.screen.width / 2));
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component c : tip) {
            List<FormattedCharSequence> part = tab.font.split(c, maxW);
            lines.addAll(part.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : part);
        }
        g.setTooltipForNextFrame(tab.font, lines, mx, my);
    }
}
