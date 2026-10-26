package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu's rail: the tabs down the left of the window on plain wood. One inner box, SP_3 in
 * from each side, holds every surface (the character card, the open tab's sheet, the hover
 * wash); the medal-free card, the group titles and the icons share one left edge, SP_3 inside
 * it. The open tab is a sheet of parchment with a gold strip, not pulled out. Counters sit on
 * the right (a number or the tracked quest's pin); a state with no number (points to spend, an
 * update) is a dot on the icon's corner. Settings and Help are pinned to the bottom. Compact: 24
 * px cells centred in the rail, the names and counters in tooltips. Keys 1–8 open the tabs.
 */
final class MenuRail extends MenuPaint {
    /**
     * The rail's entries: each lists the tab numbers it holds; an entry with more than one shows
     * them as sections along the top of its page. Settings (the last) is pinned to the bottom.
     */
    static final int[][] RAIL = {{0}, {1, 3, RoadmapScreen.TAB_DAMAGE}, {2},
        {RoadmapScreen.TAB_TREE}, {RoadmapScreen.TAB_ENDGAME}, {RoadmapScreen.TAB_BANK}, {RoadmapScreen.TAB_CRAFT},
        {RoadmapScreen.TAB_SEARCH}, {RoadmapScreen.TAB_SETTINGS, RoadmapScreen.TAB_AI}};
    /** Each entry's name as a language key. */
    static final String[] RAIL_NAMES = {"wynnlabs.screen.rail_home", "wynnlabs.screen.tab_today",
        "wynnlabs.screen.tab_quests", "wynnlabs.screen.rail_tree", "wynnlabs.screen.rail_endgame", "wynnlabs.screen.tab_bank",
        "wynnlabs.screen.tab_craft", "wynnlabs.screen.tab_search", "wynnlabs.screen.tab_settings"};
    private static final String[] ICONS = {"home", "sun", "map-2", "hierarchy", "trophy", "building-bank", "hammer", "search",
        "settings"};
    /** The group titles, and the entry each one starts at; the entries before SETTINGS have keys 1–8. */
    private static final String[] GROUPS = {"wynnlabs.screen.rail_group_game", "wynnlabs.screen.rail_group_items"};
    private static final int[] GROUP_AT = {0, 5};
    private static final int SETTINGS = RAIL.length - 1;
    /** Help: not an entry; it opens the settings on their About page. */
    private static final int HELP = -1;

    private static final int CELL = 24;             // a compact cell's width
    private static final int CARD_H = 30;
    private static final int COUNT = 0xFF8A6A1C;    // a number waiting (Theme.GOLD_EDGE)
    private static final int GOOD = 0xFF4F7A48;     // a number worth money (items to sell)
    private static final int HOVER_WASH = 0x26FFFFFF;
    private static final int CARD_WASH = 0x1CFFFFFF;
    private static final long BADGE_MS = 2000;

    /** What a tab has waiting, shown on its rail entry (see {@link MenuTab#badge}). */
    record Badge(Kind kind, String text, String tip) {
        enum Kind { COUNT, GOOD, PIN, DOT, GOOD_DOT }
    }

    private final Badge[] badges = new Badge[RAIL.length];
    private long badgesAt;
    private int[] helpRect;

    MenuRail(RoadmapScreen menu) {
        super(menu);
    }

    /** The entry that holds tab {@code t}. */
    static int entry(int t) {
        for (int i = 0; i < RAIL.length; i++) {
            for (int s : RAIL[i]) {
                if (s == t) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** The tab a number key opens (1–8, no modifier), or -1. */
    static int shortcut(int key, int modifiers) {
        int n = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
        return modifiers == 0 && n >= 0 && n < SETTINGS ? RAIL[n][0] : -1;
    }

    /** A click on Help: the settings' About page; true when taken. */
    boolean helpClicked(double x, double y) {
        if (helpRect == null || !in(x, y, helpRect)) {
            return false;
        }
        menu.openTab(RoadmapScreen.TAB_SETTINGS);
        menu.settings.showAbout();
        return true;
    }

    /** The rail at x, y, {@code h} tall (the wood is drawn by the screen). */
    void draw(GuiGraphics g, int x, int y, int h, int mx, int my) {
        if (System.currentTimeMillis() - badgesAt > BADGE_MS) {
            for (int i = 0; i < RAIL.length; i++) {
                MenuTab page = menu.page(RAIL[i][0]);
                try {
                    badges[i] = page == null ? null : page.badge();
                } catch (Throwable t) {
                    badges[i] = null;               // a counter never breaks the menu
                }
            }
            badgesAt = System.currentTimeMillis();
        }
        helpRect = null;
        if (menu.layout.compact()) {
            drawCompact(g, x, y, h, mx, my);
        } else {
            drawLarge(g, x, y, h, mx, my);
        }
    }

    // ---- large and standard: names, counters on the right ----

    private void drawLarge(GuiGraphics g, int x, int y, int h, int mx, int my) {
        int railW = menu.railW();
        int left = x + Theme.SP_3;
        int right = x + railW - Theme.SP_3;
        int edge = left + Theme.SP_3;               // the one content edge
        int head = Type.META.lineHeight + Theme.SP_1;
        int rows = SETTINGS;
        // the tallest layout that fits: the card and 20px rows, no card, then 16px rows
        int rowH = 20;
        boolean card = true;
        for (int pass = 0; pass < 3; pass++) {
            card = pass == 0;
            rowH = pass < 2 ? 20 : 16;
            int need = Theme.SP_4 + (card ? CARD_H + Theme.SP_4 : 0) + GROUPS.length * head + rows * (rowH + Theme.SP_1)
                    + Theme.SP_4 + Theme.SP_4 + Theme.SP_3 + 2 * (rowH + Theme.SP_1) + Theme.SP_4;
            if (need <= h) {
                break;
            }
        }
        int step = rowH + Theme.SP_1;
        int ry = y + Theme.SP_4;
        if (card && characterCard(g, left, ry, right - left, edge, false, mx, my)) {
            ry += CARD_H + Theme.SP_4;
        }
        for (int i = 0; i < SETTINGS; i++) {
            for (int gi = 0; gi < GROUP_AT.length; gi++) {
                if (GROUP_AT[gi] == i) {
                    if (i > 0) {
                        ry += Theme.SP_4 - Theme.SP_1;
                    }
                    Type.META.draw(g, font(), L.t(GROUPS[gi]), edge, ry, Theme.CREAM_DIM);
                    ry += head;
                }
            }
            row(g, i, left, right, edge, ry, rowH, mx, my);
            ry += step;
        }
        // Settings and Help at the bottom, under an engraved rule
        int fy = Math.max(ry + Theme.SP_4 + Theme.SP_3, y + h - Theme.SP_4 - 2 * step + Theme.SP_1);
        Kit.railRule(g, edge, fy - Theme.SP_3 - 1, right - Theme.SP_3 - edge);
        row(g, SETTINGS, left, right, edge, fy, rowH, mx, my);
        row(g, HELP, left, right, edge, fy + step, rowH, mx, my);
    }

    /** One named row: the open one a parchment sheet with a gold strip; returns nothing. */
    private void row(GuiGraphics g, int i, int left, int right, int edge, int ry, int rowH, int mx, int my) {
        boolean on = i != HELP && entry(menu.tab()) == i;
        boolean hover = mx >= left && mx < right && my >= ry && my < ry + rowH;
        if (on) {
            sheet(g, left, ry, right - left, rowH, -1);
            g.fill(left, ry + 4, left + 2, ry + rowH - 4, Theme.GOLD_DEEP);
        } else if (hover) {
            sheet(g, left, ry, right - left, rowH, HOVER_WASH);
        }
        int ink = on ? Theme.INK : Theme.CREAM;
        Type role = on ? Type.LABEL : Type.BODY;
        int ty = role.yIn(ry, rowH);
        int iy = role.onCaps(ty, 9);
        Art.icon(g, icon(i), edge, iy, 9, ink);
        Badge b = i == HELP ? null : badges[i];
        int end = right - Theme.SP_3;
        int used = 0;
        String key = i >= 0 && i < SETTINGS ? String.valueOf(i + 1) : null;
        if (hover && !on && key != null) {
            // the shortcut shows on the row under the mouse only, in the counter's place
            Type.META.drawRight(g, font(), key, end, Type.META.yIn(ry, rowH), Theme.CREAM_DIM);
            used = Type.META.width(font(), key);
        } else if (b != null) {
            used = counter(g, b, end, ry, rowH, iy);
        }
        if (b != null && (b.kind() == Badge.Kind.DOT || b.kind() == Badge.Kind.GOOD_DOT)) {
            dot(g, edge + 9 - 2, iy - 2, b.kind() == Badge.Kind.DOT ? Theme.GOLD_DEEP : Theme.SEAL_2);
        }
        int lx = edge + 9 + Theme.SP_3;
        String name = name(i);
        role.draw(g, font(), role.fit(font(), name, end - (used > 0 ? used + Theme.SP_2 : 0) - lx), lx, ty, ink);
        if (hover && b != null && b.tip() != null) {
            menu.hits.add(new MenuHit(left, ry, right - left, rowH, null, List.of(Component.literal(b.tip())), null));
        }
        click(i, left, ry, right - left, rowH);
    }

    /** A counter right-aligned at {@code end}: a seal with the number, or the pin; returns its width. */
    private int counter(GuiGraphics g, Badge b, int end, int ry, int rowH, int iconY) {
        return switch (b.kind()) {
            case COUNT, GOOD -> {
                int w = Type.META.width(font(), b.text()) + 2 * Theme.SP_2;
                Ui.seal(g, font(), b.text(), end - w, ry + (rowH - Ui.SEAL_H) / 2, b.kind() == Badge.Kind.GOOD ? GOOD : COUNT);
                yield w;
            }
            case PIN -> {
                Art.icon(g, "map-pin", end - 8, iconY, 8, Theme.GOLD);
                yield 8;
            }
            default -> 0;
        };
    }

    // ---- compact: icon cells, everything else in tooltips ----

    private void drawCompact(GuiGraphics g, int x, int y, int h, int mx, int my) {
        int railW = menu.railW();
        int cx = x + (railW - CELL) / 2;
        int rowH = 20;
        boolean card = true;
        for (int pass = 0; pass < 3; pass++) {
            card = pass == 0;
            rowH = pass < 2 ? 20 : 16;
            int need = Theme.SP_3 + (card ? CARD_H + Theme.SP_3 : 0) + (SETTINGS + 2) * (rowH + Theme.SP_1)
                    + Theme.SP_4 + Theme.SP_4 + Theme.SP_3;
            if (need <= h) {
                break;
            }
        }
        int step = rowH + Theme.SP_1;
        int ry = y + Theme.SP_3;
        if (card && characterCard(g, cx, ry, CELL, cx, true, mx, my)) {
            ry += CARD_H + Theme.SP_3;
        }
        for (int i = 0; i < SETTINGS; i++) {
            if (i == GROUP_AT[1]) {
                Kit.railRule(g, cx + Theme.SP_1, ry + Theme.SP_1 - 1, CELL - 2 * Theme.SP_1);
                ry += Theme.SP_4;
            }
            cell(g, i, cx, ry, rowH, mx, my);
            ry += step;
        }
        int fy = Math.max(ry + Theme.SP_4, y + h - Theme.SP_3 - 2 * step + Theme.SP_1);
        Kit.railRule(g, cx + Theme.SP_1, fy - Theme.SP_3 - 1, CELL - 2 * Theme.SP_1);
        cell(g, SETTINGS, cx, fy, rowH, mx, my);
        cell(g, HELP, cx, fy + step, rowH, mx, my);
    }

    private void cell(GuiGraphics g, int i, int cx, int ry, int rowH, int mx, int my) {
        boolean on = i != HELP && entry(menu.tab()) == i;
        boolean hover = mx >= cx && mx < cx + CELL && my >= ry && my < ry + rowH;
        if (on) {
            sheet(g, cx, ry, CELL, rowH, -1);
            g.fill(cx, ry + 4, cx + 2, ry + rowH - 4, Theme.GOLD_DEEP);
        } else if (hover) {
            sheet(g, cx, ry, CELL, rowH, HOVER_WASH);
        }
        int mid = cx + CELL / 2;
        int iy = ry + (rowH - 10) / 2;
        Art.icon(g, icon(i), mid - 5, iy, 10, on ? Theme.INK : Theme.CREAM);
        Badge b = i == HELP ? null : badges[i];
        if (b != null) {
            if (b.kind() == Badge.Kind.PIN) {
                Art.icon(g, "map-pin", mid + 4, iy - 2, 6, Theme.GOLD);
            } else {
                boolean gold = b.kind() == Badge.Kind.COUNT || b.kind() == Badge.Kind.DOT;
                dot(g, mid + 4, iy - 1, gold ? Theme.GOLD_DEEP : Theme.SEAL_2);
            }
        }
        if (hover) {
            List<Component> tip = new ArrayList<>();
            tip.add(Type.LABEL.of(name(i)));
            if (b != null && b.tip() != null) {
                tip.add(Component.literal(b.tip()));
            }
            if (i >= 0 && i < SETTINGS) {
                tip.add(Component.literal(L.t("wynnlabs.screen.rail_key", i + 1)));
            }
            menu.hits.add(new MenuHit(cx, ry, CELL, rowH, null, tip, null));
        }
        click(i, cx, ry, CELL, rowH);
    }

    // ---- shared ----

    /**
     * The character on top: the class (bold) over the level, on a faint sheet; a click opens the
     * home. No emblem. Returns false (and draws nothing) when the class is not known yet.
     */
    private boolean characterCard(GuiGraphics g, int x, int y, int w, int edge, boolean compact, int mx, int my) {
        Klass k;
        int level;
        try {
            k = GameState.current().classType();
            level = GameState.current().level();
        } catch (Throwable t) {
            return false;
        }
        if (k == null || k == Klass.NONE || level <= 0) {
            return false;
        }
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + CARD_H;
        sheet(g, x, y, w, CARD_H, hover ? HOVER_WASH : CARD_WASH);
        String cls = BuildText.nice(k.name());
        String lv = String.valueOf(level);
        if (compact) {
            int mid = x + w / 2;
            String cap = L.t("wynnlabs.screen.rail_level", "").trim();
            Type.META.draw(g, font(), cap, mid - Type.META.width(font(), cap) / 2, y + Theme.SP_2, Theme.CREAM_DIM);
            Type.LABEL.draw(g, font(), lv, mid - Type.LABEL.width(font(), lv) / 2, y + Theme.SP_2 + Type.META.lineHeight + 1,
                    Theme.CREAM);
            if (hover) {
                menu.hits.add(new MenuHit(x, y, w, CARD_H, null, List.of(Type.LABEL.of(cls),
                        Component.literal(L.t("wynnlabs.screen.rail_level", lv))), null));
            }
        } else {
            int ty = y + (CARD_H - Type.LABEL.lineHeight - 1 - Type.META.lineHeight) / 2;     // the two lines centred
            Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), cls, x + w - Theme.SP_3 - edge), edge, ty, Theme.CREAM);
            Type.META.draw(g, font(), L.t("wynnlabs.screen.rail_level", lv), edge, ty + Type.LABEL.lineHeight + 1, Theme.CREAM_DIM);
        }
        menu.tabs.add(new int[] {x, y, w, CARD_H, 0});
        return true;
    }

    /** The rail sheet (kit/rail_sheet): parchment for the open tab, or tinted faint for a wash. */
    private static void sheet(GuiGraphics g, int x, int y, int w, int h, int tint) {
        NineSlice.draw(g, net.minecraft.resources.Identifier.fromNamespaceAndPath("wynnlabs", "kit/rail_sheet"), x, y, w, h, tint);
    }

    /** A 4px round dot (corners cut). */
    private static void dot(GuiGraphics g, int x, int y, int colour) {
        g.fill(x + 1, y, x + 3, y + 4, colour);
        g.fill(x, y + 1, x + 4, y + 3, colour);
    }

    private void click(int i, int x, int y, int w, int h) {
        if (i == HELP) {
            helpRect = new int[] {x, y, w, h};
            return;
        }
        // a click opens the entry's first section, or stays on the section already open
        boolean on = entry(menu.tab()) == i;
        menu.tabs.add(new int[] {x, y, w, h, on ? menu.tab() : RAIL[i][0]});
    }

    private static String icon(int i) {
        return i == HELP ? "question-mark" : ICONS[i];
    }

    private static String name(int i) {
        return L.t(i == HELP ? "wynnlabs.screen.rail_help" : RAIL_NAMES[i]);
    }
}
