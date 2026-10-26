package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The one dropdown of the mod (the quests tab's kind menu made shared): opened by a chip with a
 * caret, a soft card under it (above or to the left when there is no room), one row an option: an
 * optional icon, the label (bold on the gold wash when it is the chosen one), an optional second
 * line, a count or note on the right; hairlines between groups; a hover wash; a draggable scroll
 * bar past {@link Spec#maxRows}; up, down, Enter and Esc. While open it owns the mouse
 * ({@link MenuTab#popupOpen}): the page under it neither hovers nor clicks.
 *
 * <p>The caller keeps a {@link State} per menu, draws it with {@link #draw} each frame from its
 * popup hook, and registers each visible row's click through the {@code onRow} callback (its own
 * click system: the menu's {@link MenuHit} actions, the crafting tab's acts).
 */
final class UiMenu {

    /** A row's height without a second line: a body line with SP_2 above and below. */
    static final int ROW = Type.BODY.lineHeight + 2 * Theme.SP_2;
    private static final int ICON = 8;
    private static final int GAP = 2 * Theme.SP_1 + 1;

    private UiMenu() {}

    /**
     * One option. {@code icon} null for none; {@code sub} a second META line (null: none);
     * {@code meta} the count or note on the right (null: none); {@code chosen} the current one;
     * {@code link} drawn as an action (LINK ink) rather than a choice; {@code groupStart} a
     * hairline above it; {@code tip} its tooltip.
     */
    record Item(String id, String icon, String label, String sub, String meta, boolean chosen, boolean link, boolean dim,
                boolean groupStart, List<Component> tip) {

        static Item of(String id, String icon, String label, String meta, boolean chosen) {
            return new Item(id, icon, label, null, meta, chosen, false, false, false, null);
        }

        Item sub(String s) {
            return new Item(id, icon, label, s, meta, chosen, link, dim, groupStart, tip);
        }

        Item group() {
            return new Item(id, icon, label, sub, meta, chosen, link, dim, true, tip);
        }

        Item asLink() {
            return new Item(id, icon, label, sub, meta, chosen, true, dim, groupStart, tip);
        }

        Item asDim() {
            return new Item(id, icon, label, sub, meta, chosen, link, true, groupStart, tip);
        }

        Item tip(List<Component> t) {
            return new Item(id, icon, label, sub, meta, chosen, link, dim, groupStart, t);
        }
    }

    /** How a menu opens: its anchor (the chip: x, y, w, h), the screen it stays in, its rows at most. */
    record Spec(int[] anchor, int screenW, int screenH, int maxRows, int minW) {

        static Spec under(int[] anchor, int screenW, int screenH) {
            return new Spec(anchor, screenW, screenH, 12, anchor == null ? 0 : anchor[2]);
        }

        Spec rows(int n) {
            return new Spec(anchor, screenW, screenH, n, minW);
        }

        Spec width(int w) {
            return new Spec(anchor, screenW, screenH, maxRows, w);
        }
    }

    /** A drawn row: its item and box, for the caller's click areas. */
    record Row(Item item, int x, int y, int w, int h) {}

    /** A menu's own state between frames: the first row in view, the keyboard's row, the bar. */
    static final class State {
        int scroll;
        /** The row the keys move (-1: none yet, the chosen one starts it). */
        int cursor = -1;
        final ScrollBar bar = new ScrollBar();
        private List<Item> items = List.of();
        private int visible;
        private int[] box;

        /** Opened again: from the top, the keys on the chosen row. */
        void reset() {
            scroll = 0;
            cursor = -1;
        }

        /** The panel's box last frame (null before the first). */
        int[] box() {
            return box;
        }

        /** A press: the scroll bar takes it when it is on it. */
        boolean press(double x, double y) {
            if (bar.mouseClicked(x, y)) {
                scroll = bar.offset();
                return true;
            }
            return false;
        }

        /** A drag with the bar's thumb held. */
        boolean drag(double y) {
            if (bar.mouseDragged(y)) {
                scroll = bar.offset();
                return true;
            }
            return false;
        }

        void release() {
            bar.mouseReleased();
        }

        /** The wheel over the menu. */
        void wheel(double v) {
            scroll = Math.max(0, Math.min(Math.max(0, items.size() - visible), scroll - (int) Math.signum(v)));
        }

        /**
         * A key: up and down move the cursor (the list follows), Enter returns the cursor's item;
         * null for anything else (the caller closes on Esc).
         */
        Item key(int key) {
            if (items.isEmpty()) {
                return null;
            }
            if (cursor < 0) {
                cursor = Math.max(0, chosen(items));
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                cursor = Math.min(items.size() - 1, cursor + 1);
            } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                cursor = Math.max(0, cursor - 1);
            } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                return items.get(cursor);
            } else {
                return null;
            }
            if (cursor < scroll) {
                scroll = cursor;
            } else if (cursor >= scroll + visible) {
                scroll = cursor - visible + 1;
            }
            return null;
        }
    }

    private static int chosen(List<Item> items) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).chosen()) {
                return i;
            }
        }
        return -1;
    }

    /** A row's height: one line, or two with its second line. */
    static int rowH(Item it) {
        return it.sub() == null ? ROW : ROW + Type.META.lineHeight;
    }

    /** The panel's width for these items: the widest label or second line, the icon and the right note. */
    static int width(Font font, List<Item> items, int min) {
        int w = min;
        boolean icons = items.stream().anyMatch(i -> i.icon() != null);
        for (Item it : items) {
            int lw = Type.LABEL.width(font, it.label());
            if (it.sub() != null) {
                lw = Math.max(lw, Type.META.width(font, it.sub()));
            }
            int mw = it.meta() == null ? 0 : Type.META.width(font, it.meta()) + Theme.SP_5;
            w = Math.max(w, 2 * Theme.SP_3 + (icons ? ICON + Theme.SP_2 : 0) + lw + mw);
        }
        return w;
    }

    /**
     * Where the panel goes: under the anchor, left edges together; above it when there is no room
     * below and more above; its right edge on the anchor's when it would leave the screen. Pure
     * (tested): x, y, w, h.
     */
    static int[] place(int[] anchor, int w, int h, int[] bounds) {
        int margin = Theme.SP_2;
        int left = bounds[0] + margin;
        int top = bounds[1] + margin;
        int right = bounds[2] - margin;
        int bottom = bounds[3] - margin;
        int x = anchor[0];
        if (x + w > right) {
            x = anchor[0] + anchor[2] - w;
        }
        x = Math.max(left, Math.min(x, right - w));
        int below = anchor[1] + anchor[3] + Theme.SP_1;
        int above = anchor[1] - Theme.SP_1 - h;
        int y = below;
        if (below + h > bottom && above >= top) {
            y = above;
        } else if (below + h > bottom) {
            y = Math.max(top, bottom - h);
        }
        return new int[] {x, y, w, h};
    }

    /**
     * Where a menu may go: the menu window's frame when the J menu is open (a dropdown never
     * crosses the window's wood), else the whole screen.
     */
    static int[] bounds(Spec spec) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof RoadmapScreen rs && rs.layout != null) {
            MenuLayout l = rs.layout;
            return new int[] {l.x(), l.y(), l.x() + l.w(), l.y() + l.h()};
        }
        return new int[] {0, 0, spec.screenW(), spec.screenH()};
    }

    /** How many rows fit: at most {@code maxRows} and what the screen has room for. */
    private static int fit(List<Item> items, int from, int maxRows, int room) {
        int n = 0;
        int used = 2 * Theme.SP_2;
        for (int i = from; i < items.size() && n < maxRows; i++) {
            int add = rowH(items.get(i)) + (items.get(i).groupStart() && i > from ? GAP : 0);
            if (used + add > room && n > 0) {
                break;
            }
            used += add;
            n++;
        }
        return Math.max(1, n);
    }

    /**
     * Draws the menu for {@code items} with the real mouse; calls {@code onRow} for each row in
     * view (the caller adds its click area and tooltip). Returns the panel's box.
     */
    static int[] draw(GuiGraphics g, Font font, Spec spec, State st, List<Item> items, int mx, int my,
                      java.util.function.Consumer<Row> onRow) {
        st.items = items;
        int[] bounds = bounds(spec);
        int screenRoom = Math.max(ROW * 3, Math.max(spec.anchor()[1] - bounds[1], bounds[3] - spec.anchor()[1] - spec.anchor()[3]) - 2 * Theme.SP_2);
        int visible = fit(items, 0, spec.maxRows(), screenRoom);
        boolean scrolls = visible < items.size();
        st.visible = visible;
        st.scroll = Math.max(0, Math.min(st.scroll, items.size() - visible));
        if (scrolls) {
            visible = fit(items, st.scroll, spec.maxRows(), screenRoom);
            st.visible = visible;
        }
        int w = width(font, items, spec.minW()) + (scrolls ? ScrollBar.HIT : 0);
        int h = 2 * Theme.SP_2;
        for (int i = st.scroll; i < st.scroll + visible && i < items.size(); i++) {
            h += rowH(items.get(i)) + (items.get(i).groupStart() && i > st.scroll ? GAP : 0);
        }
        int[] b = place(spec.anchor(), w, h, bounds);
        st.box = b;
        int x = b[0];
        int y = b[1];
        Kit.softCard(g, x, y, w, h);
        boolean icons = items.stream().anyMatch(i -> i.icon() != null);
        int rw = w - (scrolls ? ScrollBar.HIT : 0);
        int ry = y + Theme.SP_2;
        for (int i = st.scroll; i < st.scroll + visible && i < items.size(); i++) {
            Item it = items.get(i);
            if (it.groupStart() && i > st.scroll) {
                Kit.hair(g, x + Theme.SP_3, ry + Theme.SP_1, rw - 2 * Theme.SP_3, 1, Theme.LINE);
                ry += GAP;
            }
            int rh = rowH(it);
            boolean hover = mx >= x && mx < x + rw && my >= ry && my < ry + rh;
            if (it.chosen()) {
                Kit.rowLit(g, x + Theme.SP_1, ry, rw - 2 * Theme.SP_1, rh);
            } else if (hover || i == st.cursor) {
                g.fill(x + 1, ry, x + rw - 1, ry + rh, Theme.CARD_HOVER);
            }
            int ty = ry + Theme.SP_2;
            int tx = x + Theme.SP_3;
            if (it.icon() != null) {
                Art.icon(g, it.icon(), tx, Type.BODY.onCaps(ty, ICON), ICON, it.link() ? Theme.LINK : it.chosen() ? Theme.INK : Theme.INK_3);
            }
            if (icons) {
                tx += ICON + Theme.SP_2;
            }
            int metaW = it.meta() == null ? 0 : Type.META.width(font, it.meta()) + Theme.SP_3;
            Type role = it.link() ? Type.LINK : it.chosen() ? Type.LABEL : it.dim() ? Type.MUTED : Type.BODY;
            role.draw(g, font, role.fit(font, it.label(), x + rw - Theme.SP_3 - metaW - tx), tx, ty);
            if (it.meta() != null) {
                Type.META.drawRight(g, font, it.meta(), x + rw - Theme.SP_3, ty + 1, Theme.INK_3);
            }
            if (it.sub() != null) {
                Type.META.draw(g, font, Type.META.fit(font, it.sub(), x + rw - Theme.SP_3 - tx), tx, ty + Type.BODY.lineHeight, Theme.INK_3);
            }
            onRow.accept(new Row(it, x, ry, rw, rh));
            ry += rh;
        }
        if (scrolls) {
            st.bar.place(x + rw, y + Theme.SP_2, h - 2 * Theme.SP_2, visible, items.size(), st.scroll);
            st.bar.drawThin(g, mx, my);
        }
        return b;
    }

    /** The trigger: a chip with a caret, washed while its menu is open; returns its width. */
    static int chip(GuiGraphics g, Font font, String icon, String label, int x, int y, boolean open, int mx, int my) {
        int w = Ui.iconChipWidth(font, icon, label, true);
        Ui.iconChip(g, font, icon, label, true, x, y, w, open, mx, my);
        return w;
    }

    /** For callers that build items in a loop: a list that marks the next one added as a group's start. */
    static final class Items {
        final List<Item> list = new ArrayList<>();
        private boolean group;

        Items add(Item it) {
            list.add(group && !list.isEmpty() ? it.group() : it);
            group = false;
            return this;
        }

        Items divide() {
            group = true;
            return this;
        }
    }
}
