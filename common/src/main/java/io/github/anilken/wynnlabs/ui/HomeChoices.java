package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.MapChoices;
import io.github.anilken.wynnlabs.build.roadmap.RoadmapHistory;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * What you told the road map, in one popup under the Gear header's filter chip: its lowest item
 * level, the pieces it keeps or never picks, and the maps a new one replaced, each with its
 * click. It stays open while you change things, the map rebuilds behind it. A class of its own
 * made on first use (safe to hot swap in).
 */
final class HomeChoices {

    private static final int W = 220;
    private static final int PILL_H = 14;
    private static final int ROW_H = PILL_H + Theme.SP_1;
    /** The level pills: Any, then the item levels that split the endgame's gear. */
    private static final int[] LEVELS = {0, 80, 100, 110};

    private boolean open;
    private int[] at;
    private int scroll;
    private final ScrollBar bar = new ScrollBar();

    boolean open() {
        return open;
    }

    void openMenu(int[] chip) {
        at = chip;
        open = true;
        scroll = 0;
    }

    void close() {
        open = false;
        bar.mouseReleased();
    }

    boolean press(double x, double y) {
        if (bar.mouseClicked(x, y)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

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

    void wheel(double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v) * 3 * Type.BODY.lineHeight);   // clamped when drawn
    }

    /** A title with its rows under it, or one line saying how to fill them. */
    private static int block(int rows) {
        return Type.LABEL.lineHeight + Theme.SP_1 + Math.max(1, rows) * ROW_H + Theme.SP_2;
    }

    int[] drawList(GuiGraphics g, HomeTab.Host host, int mx, int my) {
        if (!open || at == null) {
            open = false;
            return null;
        }
        Font font = UiFont.get();
        MapChoices.Choice c = MapChoices.current();
        List<RoadmapHistory.Entry> history = RoadmapHistory.entries();
        int content = block(1) + block(c.kept().size()) + block(c.banned().size()) + block(history.size()) - Theme.SP_2;
        var mc = net.minecraft.client.Minecraft.getInstance();
        int sw = mc.screen == null ? 10_000 : mc.screen.width;
        int sh = mc.screen == null ? 10_000 : mc.screen.height;
        int[] bounds = UiMenu.bounds(UiMenu.Spec.under(at, sw, sh));
        int room = Math.max(bounds[3] - (at[1] + at[3]), at[1] - bounds[1]) - Theme.SP_1 - 2 * Theme.SP_2;
        int viewH = Math.min(content, Math.max(4 * ROW_H, room));
        boolean scrolls = viewH < content;
        int w = Math.min(W + (scrolls ? ScrollBar.HIT : 0), bounds[2] - bounds[0] - 2 * Theme.SP_2);
        int[] b = UiMenu.place(at, w, viewH + 2 * Theme.SP_2, bounds);
        int x = b[0] + Theme.SP_3;
        int rw = w - 2 * Theme.SP_3 - (scrolls ? ScrollBar.HIT : 0);
        scroll = Math.max(0, Math.min(scroll, content - viewH));
        Kit.softCard(g, b[0], b[1], w, b[3]);
        int top = b[1] + Theme.SP_2;
        int bottom = top + viewH;
        g.enableScissor(b[0], top, x + rw, bottom);
        int y = title(g, font, L.t("wynnlabs.choices.level"), x, top - scroll);
        pills(g, font, host, c, x, y, rw, top, bottom, mx, my);
        y += ROW_H + Theme.SP_2;
        y = title(g, font, L.t("wynnlabs.choices.kept"), x, y);
        y = names(g, font, host, c.kept(), "#qkeep:", "wynnlabs.choices.none_kept", x, y, rw, top, bottom, mx, my);
        y = title(g, font, L.t("wynnlabs.choices.banned"), x, y);
        y = names(g, font, host, c.banned(), "#qban:", "wynnlabs.choices.none_banned", x, y, rw, top, bottom, mx, my);
        y = title(g, font, L.t("wynnlabs.choices.history"), x, y);
        history(g, font, host, history, x, y, rw, top, bottom, mx, my);
        g.disableScissor();
        if (scrolls) {
            bar.place(x + rw, top, viewH, viewH, content, scroll);
            bar.draw(g, mx, my);
        } else {
            bar.place(0, 0, 0, 0, 0, 0);
        }
        return b;
    }

    private static int title(GuiGraphics g, Font font, String text, int x, int y) {
        Type.LABEL.draw(g, font, text, x, y, Theme.INK_2);
        return y + Type.LABEL.lineHeight + Theme.SP_1;
    }

    private static boolean shown(int y, int top, int bottom) {
        return y >= top && y + PILL_H <= bottom;
    }

    private static boolean hover(int x, int y, int w, int mx, int my, int top, int bottom) {
        return shown(y, top, bottom) && mx >= x && mx < x + w && my >= y && my < y + PILL_H;
    }

    private void pills(GuiGraphics g, Font font, HomeTab.Host host, MapChoices.Choice c, int x, int y, int w,
            int top, int bottom, int mx, int my) {
        int pw = (w - (LEVELS.length - 1) * Theme.SP_1) / LEVELS.length;
        for (int i = 0; i < LEVELS.length; i++) {
            int level = LEVELS[i];
            int px = x + i * (pw + Theme.SP_1);
            String text = level == 0 ? L.t("wynnlabs.choices.any") : level + "+";
            boolean on = c.minLevel() == level;
            Kit.pill(g, px, y, pw, PILL_H, on, hover(px, y, pw, mx, my, top, bottom));
            Type.LABEL.draw(g, font, text, px + (pw - font.width(text)) / 2, y + (PILL_H - Type.LABEL.lineHeight) / 2 + 1, Theme.INK);
            if (shown(y, top, bottom)) {
                host.area(px, y, pw, PILL_H, null, List.of(L.c("wynnlabs.choices.level_tip").withStyle(ChatFormatting.GRAY)),
                        "#qminlv:" + level);
            }
        }
    }

    /** Names with their ✕, which takes the choice back; or the line saying how to add one. */
    private int names(GuiGraphics g, Font font, HomeTab.Host host, List<String> names, String action, String none,
            int x, int y, int w, int top, int bottom, int mx, int my) {
        if (names.isEmpty()) {
            Type.META.draw(g, font, Type.META.fit(font, L.t(none), w), x, y + 3, Theme.INK_2);
            return y + ROW_H + Theme.SP_2;
        }
        for (String name : names) {
            int bx = x + w - PILL_H;
            boolean hot = hover(bx, y, PILL_H, mx, my, top, bottom);
            Type.BODY.draw(g, font, Type.BODY.fit(font, name, w - PILL_H - Theme.SP_2), x, y + 2, Theme.INK);
            Art.icon(g, "x", bx + 3, y + 3, 8, hot ? Theme.INK : Theme.INK_2);
            if (shown(y, top, bottom)) {
                host.area(bx, y, PILL_H, PILL_H, null, List.of(L.c("wynnlabs.choices.remove").withStyle(ChatFormatting.GRAY)),
                        action + name);
            }
            y += ROW_H;
        }
        return y + Theme.SP_2;
    }

    private void history(GuiGraphics g, Font font, HomeTab.Host host, List<RoadmapHistory.Entry> list, int x, int y, int w,
            int top, int bottom, int mx, int my) {
        if (list.isEmpty()) {
            Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.choices.none_history"), w), x, y + 3, Theme.INK_2);
            return;
        }
        long now = GameState.current().now();
        String back = L.t("wynnlabs.choices.back");
        int bw = font.width(back) + 2 * Theme.SP_2;
        for (RoadmapHistory.Entry e : list) {
            int bx = x + w - bw;
            String age = HomeTab.age(Math.max(0, now - e.builtAt()));
            String line = L.t("wynnlabs.choices.history_row", e.level(), e.weapon()) + (age.isEmpty() ? "" : " · " + age);
            Type.BODY.draw(g, font, Type.BODY.fit(font, line, w - bw - Theme.SP_2), x, y + 2, Theme.INK);
            Kit.pill(g, bx, y, bw, PILL_H, false, hover(bx, y, bw, mx, my, top, bottom));
            Type.LABEL.draw(g, font, back, bx + Theme.SP_2, y + (PILL_H - Type.LABEL.lineHeight) / 2 + 1, Theme.INK);
            if (shown(y, top, bottom)) {
                host.area(bx, y, bw, PILL_H, null, List.of(Component.literal(L.t("wynnlabs.choices.back_tip")).withStyle(ChatFormatting.GRAY)),
                        "#qhist:" + e.builtAt());
            }
            y += ROW_H;
        }
    }
}
