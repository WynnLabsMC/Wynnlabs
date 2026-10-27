package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.AbilityNode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * The ability tree page, one page: the tree on its dark board with the game's own pictures
 * ({@link TreeBoard}) on the left; on the right the plan's next node, the plan and the archetypes
 * ({@link TreeSide}), then the damage ({@link TreeDamage}) and the deaths ({@link TreeDeaths}) as
 * blocks whose "Tümü ›" opens their full view in a drawer ({@link UiDrawer}). The live-DPS switch
 * and the search sit on the title row. A small window stacks it all in one scrolling column.
 */
public final class TreeTab extends MenuTab {

    TreeTab(RoadmapScreen menu) {
        super(menu);
        board = new TreeBoard(menu, this);
        side = new TreeSide(menu, this);
        damage = new TreeDamage(menu, this);
        deaths = new TreeDeaths(menu, this);
        drawer = new UiDrawer(menu);
    }

    final TreeBoard board;
    final TreeSide side;
    final TreeDamage damage;
    final TreeDeaths deaths;
    final UiDrawer drawer;
    /** The search over the tree (its first match opens). */
    public EditBox treeSearch;
    private UiInput.Hit searchHit;
    private boolean searchOpen;
    /** The page's scroll on a small window (logical px), its bar and box. */
    private int scroll;
    private int contentH;
    private final ScrollBar pageBar = new ScrollBar();
    private int[] pageRect;

    // ---- the tab ----

    @Override
    List<EditBox> init() {
        treeSearch = UiInput.box(font(), "wynnlabs.tree.search_hint", treeSearch, v -> board.treeJump = true);
        return List.of(treeSearch);
    }

    @Override
    Popup popup() {
        return drawer;
    }

    /** Opens the damage's ("damage") or the deaths' ("deaths") full view: links and tab 7 / 3 land here. */
    void openDrawer(String which) {
        if (which.equals("damage")) {
            drawer.show(L.t("wynnlabs.tree.damage_head"), (g, x, y, w, top, bottom, mx, my) -> damage.drawer(g, x, y, w, mx, my));
        } else {
            drawer.show(L.t("wynnlabs.tree.deaths_head"), deaths::drawer, deaths.side);
        }
    }

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.settings.toggleClicked(event, doubleClick)) {
            return true;                            // the live-DPS switch
        }
        if (UiInput.clicked(searchHit, treeSearch, event, doubleClick, menu::setFocused)) {
            return true;
        }
        treeSearch.setFocused(false);
        if (board.rect != null && board.bar.mouseClicked(event.x(), event.y())) {
            board.treeScroll = board.bar.offset() * board.treeCell;
            return true;
        }
        if (pageRect != null && pageBar.mouseClicked(event.x(), event.y())) {
            scroll = pageBar.offset() * Theme.SP_6;
            return true;
        }
        for (MenuHit h : menu.hits) {
            if (h.copy() != null && h.copy().startsWith("#tree") && in(event.x(), event.y(), new int[] {h.x(), h.y(), h.w(), h.h()})) {
                treeClick(h.copy());
                return true;
            }
        }
        return false;
    }

    @Override
    boolean barDragged(double x, double y) {
        if (drawer.drag(x, y)) {
            return true;
        }
        if (board.bar.mouseDragged(y)) {
            board.treeScroll = board.bar.offset() * board.treeCell;
            return true;
        }
        if (pageBar.mouseDragged(y)) {
            scroll = pageBar.offset() * Theme.SP_6;
            return true;
        }
        return false;
    }

    @Override
    void barReleased() {
        MouseButtonEvent click = drawer.release();
        if (click != null) {
            menu.replayClick(click);                // a press on the deaths' map that did not pan: a marker's click
        }
        board.bar.mouseReleased();
        pageBar.mouseReleased();
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        if (board.rect != null && in(x, y, board.rect)) {
            board.scroll(v);
        } else if (pageRect != null && in(x, y, pageRect)) {
            scroll = Math.max(0, scroll - (int) Math.signum(v) * Theme.SP_7 * 2);
        }
    }

    /** A click of the page: the archetype chip, a drawer's "Tümü ›". */
    private void treeClick(String action) {
        if (action.equals("#tree:arch")) {
            menu.archPicker.openAt(action);         // the list opens; a row picks the archetype
        } else if (action.startsWith("#treedrawer:")) {
            openDrawer(action.substring(12));
        } else if (action.equals("#tree:search")) {
            searchOpen = !searchOpen;
            if (searchOpen) {
                UiInput.focus(treeSearch, menu::setFocused);
            } else {
                treeSearch.setValue("");
            }
        }
    }

    @Override
    boolean slashTyped() {
        searchOpen = true;
        return UiInput.focus(treeSearch, menu::setFocused);
    }

    // ---- the title row: the live-DPS switch and the search ----

    @Override
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        int by = y - 3;
        int r = right;
        searchHit = null;
        int sw = Math.min(150, room / 3);
        if (menu.layout.compact() || sw < UiInput.MIN_W) {
            r -= UiInput.icon(g, treeSearch, searchOpen, r - UiInput.H - 2, by, mx, my);
            menu.hits.add(new MenuHit(r, by, UiInput.H + 2, UiInput.H, null, null, "#tree:search"));
            r -= Theme.SP_4;
        } else {
            searchHit = UiInput.search(g, font(), treeSearch, L.t("wynnlabs.tree.search_hint"), true, r - sw, by, sw, mx, my);
            r -= sw + Theme.SP_4;
        }
        // the same meter on the game screen: its switch, here
        boolean on = Settings.on("dpsHud");
        int tw = UiToggle.W;
        int ty = by + (Ui.CHIP_H - UiToggle.H) / 2;
        UiToggle.draw(g, r - tw, ty, on, "dpsHud", mx, my);
        String label = L.t("wynnlabs.tree.dps_hud");
        int lw = Type.META.width(font(), label);
        Type.META.draw(g, font(), label, r - tw - Theme.SP_2 - lw, Type.META.yIn(by, Ui.CHIP_H), Theme.INK_3);
        int x0 = r - tw - Theme.SP_2 - lw;
        menu.toggles.add(new Object[] {new int[] {x0, by, r - x0, Ui.CHIP_H}, "dpsHud"});
        menu.hits.add(new MenuHit(x0, by, r - x0, Ui.CHIP_H, null, List.of(L.c("wynnlabs.setting.dpsHud").withStyle(ChatFormatting.WHITE),
                L.c("wynnlabs.setting_desc.dpsHud").withStyle(ChatFormatting.GRAY)), null));
        return right - x0;
    }

    // ---- the page ----

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        drawer.page(x, y, w, h);
        board.rect = null;
        boolean compact = menu.layout.compact();
        List<AbilityNode> tree = GameState.current().abilityTree();
        boolean known = tree != null && !tree.isEmpty();
        TreeView.View v = known ? board.treeView(tree) : null;
        int cy = y;
        // the archetype and the points
        String chosen = TreePlanner.chosen();
        String arch = chosen.isEmpty() ? L.t("wynnlabs.tree.arch_off") : chosen;
        int cw = UiMenu.chip(g, font(), "sparkles", arch, x, cy, menu.archPicker.isOpen(), mx, my);
        menu.hits.add(new MenuHit(x, cy, cw, Ui.CHIP_H, null, null, "#tree:arch"));
        if (v != null) {
            String pts = v.afterPlan() > v.spent() ? L.t("wynnlabs.tree.points_plan", v.spent(), v.total(), v.afterPlan())
                    : L.t("wynnlabs.tree.points", v.spent(), v.total());
            Type.META.draw(g, font(), Type.META.fit(font(), pts, w - cw - Theme.SP_3), x + cw + Theme.SP_3, Type.META.yIn(cy, Ui.CHIP_H),
                    Theme.INK_2);
        }
        cy += Ui.CHIP_H + Theme.SP_4;
        if (searchOpen && (compact || Math.min(150, w / 3) < UiInput.MIN_W)) {
            searchHit = UiInput.search(g, font(), treeSearch, L.t("wynnlabs.tree.search_hint"), true, x, cy, w, mx, my);
            cy += UiInput.H + Theme.SP_4;
        }
        if (compact) {
            drawCompact(g, x, cy, w, y + h - cy, tree, v, mx, my);
            return;
        }
        pageRect = null;
        int bw = Math.max(9 * 18 + 8, Math.min(9 * 40 + 8, w * 46 / 100));
        if (known) {
            board.draw(g, tree, v, x, cy, bw, y + h - cy, mx, my);
        } else {
            Kit.slate(g, x, cy, bw, y + h - cy);
        }
        int rx = x + bw + Theme.SP_7;
        int rw = x + w - rx;
        int ry = y;
        if (!known) {
            ry += unknown(g, rx, ry, rw, mx, my) + Theme.SP_6;
        } else {
            int nh = side.next(g, rx, ry, rw, v);
            ry += nh > 0 ? nh + Theme.SP_5 : 0;
            int half = (rw - Theme.SP_6) / 2;
            int py = side.plan(g, rx, ry, half, v, 4);
            int ay = side.archetypes(g, rx + half + Theme.SP_6, ry, rw - half - Theme.SP_6, v);
            ry = Math.max(py, ay) + Theme.SP_4;
        }
        ry = damage.block(g, rx, ry, rw, 3, mx, my) + Theme.SP_4;
        deaths.block(g, rx, ry, rw, 2);
    }

    /** "The tree is not read": an info notice with the way to it; returns its height. */
    private int unknown(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<String> lines = List.of(L.t("wynnlabs.tree.unknown_title") + ". " + L.t("wynnlabs.tree.unknown_1"));
        return Ui.notice(g, font(), Ui.Level.INFO, lines, null, x, y, w, Ui.noticeHeight(2), mx, my, new int[1][]);
    }

    /** A small window: the tree first, then the next node, the damage, the plan, the archetypes, the deaths; it scrolls. */
    private void drawCompact(GuiGraphics g, int x, int y, int w, int h, List<AbilityNode> tree, TreeView.View v, int mx, int my) {
        int cw = contentH > h ? w - ScrollBar.HIT - Theme.SP_2 : w;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - h)));
        pageRect = new int[] {x, y, w, h};
        int hitsFrom = menu.hits.size();
        g.enableScissor(x - Theme.SP_2, y, x + w, y + h);
        int top = y - scroll;
        int cy = top;
        int bh = Math.max(110, h * 55 / 100);
        if (v != null) {
            board.draw(g, tree, v, x, cy, cw, bh, mx, my);
            cy += bh + Theme.SP_4;
            int nh = side.next(g, x, cy, cw, v);
            cy += nh > 0 ? nh + Theme.SP_4 : 0;
        } else {
            cy += unknown(g, x, cy, cw, mx, my) + Theme.SP_5;
        }
        cy = damage.block(g, x, cy, cw, 2, mx, my) + Theme.SP_4;
        if (v != null) {
            cy = side.plan(g, x, cy, cw, v, 4) + Theme.SP_4;
            cy = side.archetypes(g, x, cy, cw, v) + Theme.SP_4;
        }
        cy = deaths.block(g, x, cy, cw, 2);
        g.disableScissor();
        contentH = cy - top;
        menu.hits.subList(hitsFrom, menu.hits.size()).removeIf(hb -> hb.y() + hb.h() <= y || hb.y() >= y + h);
        if (board.rect != null && (board.rect[1] + board.rect[3] <= y || board.rect[1] >= y + h)) {
            board.rect = null;                      // scrolled out: the wheel scrolls the page
        }
        if (contentH > h) {
            int step = Theme.SP_6;
            pageBar.place(x + w - ScrollBar.HIT + Theme.SP_1, y, h, Math.max(1, h / step), Math.max(1, contentH / step), scroll / step);
            pageBar.draw(g, mx, my);
        }
    }

    /** The rail: a dot while ability points wait to be spent (the tree read at least once). */
    @Override
    MenuRail.Badge badge() {
        GameState gs = GameState.current();
        List<AbilityNode> tree = gs.abilityTree();
        java.util.Set<String> have = new java.util.HashSet<>(gs.unlockedAbilities());
        if (tree == null || tree.isEmpty() || have.isEmpty()) {
            return null;
        }
        int spent = 0;
        for (AbilityNode n : tree) {
            if (have.contains(n.name())) {
                spent += n.cost();
            }
        }
        int free = Math.max(Wyn.abilityPointsAt(gs.level()), TreePlanner.basePoints(gs.level())) - spent;
        return free <= 0 ? null : new MenuRail.Badge(MenuRail.Badge.Kind.DOT, String.valueOf(free), L.t("wynnlabs.screen.rail_badge_tree", free));
    }
}
