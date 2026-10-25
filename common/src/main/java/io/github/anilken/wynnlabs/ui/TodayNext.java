package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.quests.QuestBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Today tab's "Next" section: the tiles that were on the Home (the next quest, where to level,
 * the bank, the nearest secret discovery, this week's raid pool, the aspects and the professions),
 * drawn by {@link HomeTiles} as they were, with the same clicks and tooltips. Up to four to a row,
 * none narrower than they were on the Home, under the to-do list; the rows that do not fit the
 * page are left out.
 */
final class TodayNext {

    private final RoadmapScreen menu;
    private final HomeTiles tiles = new HomeTiles();

    /** The tiles' way back into the screen: only their hover and click areas, and a quest's. */
    private final HomeTab.Host host = new HomeTab.Host() {
        @Override
        public void area(int x, int y, int w, int h, ItemStack stack, List<Component> tip, String action) {
            menu.hits.add(new MenuHit(x, y, w, h, stack, tip, action));
        }

        @Override
        public void quest(int x, int y, int w, int h, List<Component> tip, QuestBook.Entry q) {
            menu.hits.add(new MenuHit(x, y, w, h, null, tip, null, q));
        }

        @Override
        public void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> extra) {
        }

        @Override
        public void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> lead, List<Component> extra) {
        }

        @Override
        public void compare(int[] rect, HomeTab.SlotView s) {
        }

        @Override
        public int levelPills(GuiGraphics g, int y, int right, int room) {
            return right;
        }

        @Override
        public int refresh(GuiGraphics g, int x, int y, int mx, int my, String staleTip) {
            return 0;
        }
    };

    TodayNext(RoadmapScreen menu) {
        this.menu = menu;
    }

    /** The tile height for this menu. */
    static int tileH(boolean compact, boolean large) {
        return compact ? 40 : large ? 58 : 52;
    }

    /** How many tiles go in a row across {@code w}: none narrower than the Home's side column was (112). */
    static int cols(int w) {
        return Math.max(1, Math.min(4, (w + 6) / (112 + 6)));
    }

    /**
     * The tiles from {@code y} across {@code w}, as many rows as fit in {@code room} (the first
     * tiles first); returns the y under them, {@code y} when none fit.
     */
    int draw(GuiGraphics g, int x, int y, int w, int room, boolean compact, boolean large, int mx, int my) {
        tiles.frame(UiFont.get(), large);
        tiles.refresh(System.currentTimeMillis());
        HomeTab.In in = new HomeTab.In(List.of(), Double.NaN, Double.NaN, menu.quests.isEmpty() ? null : menu.quests.get(0),
                menu.steps.isEmpty() ? List.of() : menu.steps.get(0).places());
        int gap = 6;
        int cols = cols(w);
        int th = tileH(compact, large);
        int fit = Math.max(0, (room + gap) / (th + gap));
        int n = Math.min(7, fit * cols);
        if (n == 0) {
            return y;
        }
        int tw = (w - (cols - 1) * gap) / cols;
        for (int i = 0; i < n; i++) {
            int tx = x + (i % cols) * (tw + gap);
            int ty = y + (i / cols) * (th + gap);
            switch (i) {
                case 0 -> tiles.questTile(g, in, host, tx, ty, tw, th, mx, my);
                case 1 -> tiles.grindTile(g, in, host, tx, ty, tw, th, mx, my);
                case 2 -> tiles.bankTile(g, host, tx, ty, tw, th, mx, my);
                case 3 -> tiles.discoveryTile(g, host, tx, ty, tw, th, mx, my);
                case 4 -> tiles.weekTile(g, host, tx, ty, tw, th, mx, my);
                case 5 -> tiles.aspectTile(g, host, tx, ty, tw, th, mx, my);
                default -> tiles.professionTile(g, host, tx, ty, tw, th, mx, my);
            }
        }
        int rows = (n + cols - 1) / cols;
        return y + rows * (th + gap) - gap;
    }
}
