package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.Nearest;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The quests tab's "Nearest" kind: the not-yet-done content nearest to you by walking distance
 * ({@link NearestList}), one mixed list. On the right, while nothing is picked: "Nearest to you"
 * with the "Higher level" chip and the result's age, a bar while the route map is not ready (the
 * distances then straight-line), then a row an entry (its kind's icon, the English name and its
 * kind, the level coloured by fit, the distance; "≈" for a straight-line estimate). In the rail the
 * same entries as short rows. Picking a row opens its kind's own pane, whose go-there action sets
 * the compass pin.
 */
final class QuestsNearest extends MenuPaint {

    /** Rows shown at most. */
    static final int SHOWN = Nearest.WANT;
    /** Content more than this many levels below you shows its level in grey. */
    private static final int LOW = 10;

    private final QuestsTab tab;

    QuestsNearest(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    // ---- the rows ----

    /** The latest ranking's first {@link #SHOWN} entries as rail rows (the search's matches when one is typed). */
    List<QuestsRail.Row> rows() {
        Nearest.Result r = NearestList.now(tab.nearOver);
        List<QuestsRail.Row> out = new ArrayList<>();
        if (r == null) {
            return out;
        }
        String q = tab.query().toLowerCase(Locale.ROOT);
        for (Nearest.Entry e : r.entries()) {
            if (out.size() >= SHOWN) {
                break;
            }
            if (q.isEmpty() || e.c().name().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new QuestsRail.Row(e.c().key(), null, null, null, e));
            }
        }
        return out;
    }

    /** Whether the entry is the one picked in the tab. */
    private boolean picked(Nearest.Entry e) {
        ContentType k = e.c().kind();
        return e.c().key().equals(tab.questSelected) && (k.quest() ? tab.pickedType == null : tab.pickedType == k);
    }

    // ---- the page ----

    /** The list on the right: the title row, the route map's bar, the rows, the hint. */
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Nearest.Result r = NearestList.now(tab.nearOver);
        int level = GameState.current().level();
        int top = y;
        // the title row: "Nearest to you · walking distance · Lv 44" … "12s ago" [Higher level]
        String over = L.t("wynnlabs.nearest.over");
        int cw = Ui.iconChipWidth(font(), "arrow-up", over, false);
        int cx = x + w - cw;
        Ui.iconChip(g, font(), "arrow-up", over, false, cx, top, cw, tab.nearOver, mx, my);
        menu.hits.add(new MenuHit(cx, top, cw, Ui.CHIP_H, null, List.of(L.c("wynnlabs.nearest.over_tip").withStyle(ChatFormatting.GRAY)),
                "#qnearover"));
        int rx = cx - Theme.SP_3;
        if (r != null) {
            String age = age(r.computedAt());
            rx -= Type.META.width(font(), age);
            Type.META.draw(g, font(), age, rx, Type.META.yIn(top, Ui.CHIP_H), Theme.INK_3);
            rx -= Theme.SP_3;
        }
        String title = L.t("wynnlabs.nearest.title");
        Type.HEADING.draw(g, font(), Type.HEADING.fit(font(), title, rx - x), x, Type.HEADING.yIn(top, Ui.CHIP_H));
        int sx = x + Type.HEADING.width(font(), title) + Theme.SP_2;
        boolean route = r != null && r.route();
        String sub = L.t(route ? "wynnlabs.nearest.sub_walk" : "wynnlabs.nearest.sub_straight", level);
        if (rx - sx > 24) {
            Type.META.draw(g, font(), Type.META.fit(font(), "· " + sub, rx - sx), sx, Type.META.yIn(top, Ui.CHIP_H), Theme.INK_3);
        }
        top += Ui.CHIP_H + Theme.SP_4;
        // the route map not ready: the distances are straight-line until it is
        String state = RouteReach.state();
        if (!"READY".equals(state) && (r == null || !r.route())) {
            top += bar(g, x, top, w, "FAILED".equals(state)) + Theme.SP_4;
        }
        // the hint at the foot
        int foot = y + h - Type.META.lineHeight;
        Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.nearest.hint"), w), x, foot, Theme.INK_3);
        int bottom = foot - Theme.SP_3;
        tab.nearRect = new int[] {x, top, w, bottom - top};
        List<QuestsRail.Row> rows = rows();
        if (r == null) {
            Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), Ui.busy(L.t("wynnlabs.nearest.computing")), w), x + Theme.SP_2, top);
            return;
        }
        if (rows.isEmpty()) {
            Type.MUTED.drawWrapped(g, font(), L.t("wynnlabs.nearest.empty"), x + Theme.SP_2, top, w - Theme.SP_2, 3, Type.MUTED.colour);
            return;
        }
        // the columns: the distance at the right, the level before it, the name and kind in the rest
        int distW = 0;
        int lvW = 0;
        for (QuestsRail.Row row : rows) {
            distW = Math.max(distW, Type.BODY.width(font(), distance(row.near())));
            lvW = Math.max(lvW, Type.BODY.width(font(), levelTag(row.near().c().level())));
        }
        int fit = Math.max(1, (bottom - top + Theme.SP_2) / QuestsRail.PITCH);
        tab.nearScroll = Math.max(0, Math.min(tab.nearScroll, rows.size() - fit));
        int ry = top;
        for (int i = tab.nearScroll; i < rows.size() && ry + QuestsRail.ROW <= bottom; i++) {
            row(g, rows.get(i).near(), x, ry, w, distW, lvW, level, mx, my);
            ry += QuestsRail.PITCH;
        }
    }

    /** The route map's bar: an arrow, "Route map downloading" (or unavailable) and why the distances are straight-line; its height. */
    private int bar(GuiGraphics g, int x, int y, int w, boolean failed) {
        int bh = Type.BODY.lineHeight + 2 * Theme.SP_3;
        Kit.softCard(g, x, y, w, bh);
        int ty = y + Theme.SP_3;
        int ix = x + Theme.SP_4;
        Art.icon(g, "arrow-down", ix, Type.LABEL.onCaps(ty, 8), 8, Theme.LINK);
        int tx = ix + 8 + Theme.SP_2;
        String head = L.t(failed ? "wynnlabs.nearest.map_failed" : "wynnlabs.nearest.map_downloading");
        String head2 = Type.LABEL.fit(font(), head, x + w - Theme.SP_4 - tx);
        Type.LABEL.draw(g, font(), head2, tx, ty);
        int mx2 = tx + Type.LABEL.width(font(), head2) + Theme.SP_2;
        int room = x + w - Theme.SP_4 - mx2;
        if (room > 24) {
            Type.META.draw(g, font(), Type.META.fit(font(), "· " + L.t("wynnlabs.nearest.map_straight"), room), mx2, ty + 1, Theme.INK_3);
        }
        return bh;
    }

    /** One entry: its kind's icon, name · kind, the level by fit, the distance; picked on the gold wash. */
    private void row(GuiGraphics g, Nearest.Entry e, int x, int y, int w, int distW, int lvW, int level, int mx, int my) {
        int rowH = QuestsRail.ROW;
        boolean picked = picked(e);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
        if (picked) {
            Kit.rowLit(g, x, y, w, rowH);
        } else if (hover) {
            g.fill(x, y, x + w, y + rowH, Theme.CARD_HOVER);
        }
        int ty = y + Theme.SP_2;
        int mark = x + Theme.SP_3;
        ContentType kind = e.c().kind();
        Art.icon(g, kind.icon, mark, Type.BODY.onCaps(ty, 8), 8, picked ? Theme.INK : Theme.INK_3);
        int right = x + w - Theme.SP_3;
        Type.BODY.drawRight(g, font(), distance(e), right, ty, e.estimate() ? Theme.INK_3 : Theme.INK);
        int lvRight = right - distW - Theme.SP_6;
        Type.BODY.drawRight(g, font(), levelTag(e.c().level()), lvRight, ty, levelInk(e.c().level(), level));
        int tx = mark + 8 + Theme.SP_2;
        int room = lvRight - lvW - Theme.SP_6 - tx;
        String name = Type.BODY.fit(font(), e.c().name(), room);
        Type.BODY.draw(g, font(), name, tx, ty, picked ? Theme.GOOD : Theme.INK);
        int kx = tx + Type.BODY.width(font(), name) + Theme.SP_2;
        String k = "· " + kind.label();
        if (name.equals(e.c().name()) && kx + Type.META.width(font(), k) <= tx + room) {
            Type.META.draw(g, font(), k, kx, ty + 1, Theme.INK_3);
        }
        menu.hits.add(new MenuHit(x, y, w, rowH, null, tip(e), pick(e.c())));
    }

    /** One entry in the rail: its kind's icon, its name, the distance short ("≈ 2.3 km"). */
    void railRow(GuiGraphics g, QuestsRail.Row r, int x, int y, int w, int mx, int my) {
        Nearest.Entry e = r.near();
        int rowH = QuestsRail.ROW;
        boolean picked = picked(e);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
        if (picked) {
            Kit.rowLit(g, x, y, w, rowH);
            g.fill(x, y + Theme.SP_2, x + 2, y + rowH - Theme.SP_2, Theme.GOLD_DEEP);
        } else if (hover) {
            g.fill(x, y, x + w, y + rowH, Theme.CARD_HOVER);
        }
        int ty = y + Theme.SP_2;
        int mark = x + Theme.SP_3;
        Art.icon(g, e.c().kind().icon, mark, Type.BODY.onCaps(ty, 8), 8, picked ? Theme.INK : Theme.INK_3);
        String right = (e.estimate() ? "≈ " : "") + RoadmapScreen.dist((int) Math.round(e.shown()));
        int rw = Type.META.width(font(), right) + Theme.SP_3;
        Type.META.drawRight(g, font(), right, x + w - Theme.SP_3, ty + 1, Theme.INK_3);
        int tx = mark + 8 + Theme.SP_2;
        Type role = picked ? Type.LABEL : Type.BODY;
        role.draw(g, font(), role.fit(font(), e.c().name(), x + w - Theme.SP_3 - rw - tx), tx, ty, picked ? Theme.GOOD : role.colour);
        menu.hits.add(new MenuHit(x, y, w, rowH, null, tip(e), pick(e.c())));
    }

    private static List<Component> tip(Nearest.Entry e) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(e.c().name()).withStyle(ChatFormatting.AQUA).withStyle(UiFont::bold));
        tip.add(Component.literal(e.c().kind().label() + (e.c().level() > 0 ? " · Lv " + e.c().level() : "")).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(distance(e)).withStyle(ChatFormatting.GRAY));
        return tip;
    }

    // ---- the words ----

    /** "184 blocks", or "≈ 2,260 blocks" for a straight-line estimate. */
    static String distance(Nearest.Entry e) {
        String n = L.t("wynnlabs.nearest.blocks", String.format(Locale.ROOT, "%,d", Math.round(e.shown())));
        return e.estimate() ? "≈ " + n : n;
    }

    /** "Lv 34", or "—" with no known level. */
    static String levelTag(int lv) {
        return lv > 0 ? "Lv " + lv : "—";
    }

    /** Green when it fits you, amber above you, grey far below you or with no level. */
    static int levelInk(int lv, int player) {
        if (lv <= 0 || lv < player - LOW) {
            return Theme.INK_3;
        }
        return lv > player ? Theme.WARN : Theme.GOOD;
    }

    /** "12s ago" under a minute, else as the tab's other times. */
    private static String age(long at) {
        long s = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        return s < 60 ? L.t("wynnlabs.nearest.age_s", s) : ago(at);
    }

    // ---- the actions ----

    /** A row's click: its kind and the name its pane knows it by (a discovery's id), as {@link #action} reads it. */
    static String pick(Nearest.Candidate c) {
        return "#qnear:" + c.kind().name() + "\n" + c.key();
    }

    /** The list's clicks: a row picked, back to the list, the over-level chip; false when {@code c} is none. */
    boolean action(String c) {
        if (c.equals("#qtype:NEAREST")) {
            tab.typeOpen = false;
            tab.nearest = true;
            tab.questSelected = null;
            tab.pickedType = null;
            tab.railScroll = 0;
            tab.nearScroll = 0;
            tab.questDetailTab = 0;
        } else if (c.startsWith("#qnear:")) {
            String[] parts = c.substring(7).split("\n", 2);
            ContentType k = ContentType.of(parts[0]);
            tab.pickedType = k.quest() ? null : k;
            tab.questSelected = parts.length > 1 ? parts[1] : null;
            tab.questDetailTab = 0;
            tab.pointScrollKey = "";
            tab.listOpen = false;
            tab.map.zoom = 1;
        } else if (c.equals("#qnearlist")) {
            tab.questSelected = null;
            tab.pickedType = null;
            tab.listOpen = false;
        } else if (c.equals("#qnearover")) {
            tab.nearOver = !tab.nearOver;
            tab.nearScroll = 0;
        } else {
            return false;
        }
        return true;
    }
}
