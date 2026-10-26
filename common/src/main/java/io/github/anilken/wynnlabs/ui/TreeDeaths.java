package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.coach.DeathCoach;
import io.github.anilken.wynnlabs.core.L;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tree page's deaths: a block (the week's count in its title, the last two with their cause)
 * and the drawer's full view (the week in one line, every death, what would have helped, and
 * Wynntils' map of where). A death's row puts its place on the compass.
 */
final class TreeDeaths extends MenuPaint {

    private static final long WEEK = 7L * 24 * 3_600_000L;

    private final TreeTab tab;
    /** The deaths' map in the drawer (its own view). */
    final QuestMap map;

    /** This frame's rows {x, y, w, h, index} (a click there pans the map to that death). */
    private final List<int[]> rowRects = new ArrayList<>();
    /** The number of the row under the mouse this frame (its marker lights); null for none. */
    private String rowLit;
    /** The map pinned beside the drawer's list (the quests tab's map: pan, zoom, ⌖, markers). */
    final UiDrawer.Side side;

    TreeDeaths(RoadmapScreen menu, TreeTab tab) {
        super(menu);
        this.tab = tab;
        map = new QuestMap(menu, false);          // deaths are separate events: no route between them
        map.questsButtons = false;                  // its + − ⌖ are its own, not the quests tab's
        side = new UiDrawer.Side() {
            @Override
            public boolean shown() {
                return QuestMap.available(places());
            }

            @Override
            public void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
                map.draw(g, x, y, w, h, places(), rowLit, true, "deaths|" + recent().size(), mx, my);
            }

            @Override
            public boolean press(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
                if (map.button(e.x(), e.y())) {
                    return true;
                }
                if (doubleClick && map.press(e)) {
                    map.release();
                    map.refit();
                    return true;
                }
                return map.press(e);
            }

            @Override
            public boolean drag(double x, double y) {
                return map.drag(x, y);
            }

            @Override
            public net.minecraft.client.input.MouseButtonEvent release() {
                return map.release();
            }

            @Override
            public void wheel(double x, double y, double v) {
                map.zoomAt(x, y, (int) Math.signum(v));
            }

            @Override
            public void contentClicked(double x, double y) {
                List<DeathCoach.Death> all = recent();
                for (int[] r : rowRects) {
                    if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3] && r[4] < all.size()) {
                        DeathCoach.Death d = all.get(r[4]);
                        map.reveal(new int[] {d.x(), d.y(), d.z()});
                    }
                }
            }
        };
    }

    /** The map's places: the last nine deaths, numbered like the list, the newest lit. */
    private List<QuestMap.Place> places() {
        List<DeathCoach.Death> all = recent();
        List<QuestMap.Place> pts = new ArrayList<>();
        for (int i = 0; i < all.size() && i < 9; i++) {
            DeathCoach.Death d = all.get(i);
            pts.add(new QuestMap.Place(String.valueOf(i + 1), new int[] {d.x(), d.y(), d.z()}, L.cap(d.place()),
                    i == 0 ? QuestMapLayout.NOW : QuestMapLayout.NEXT));
        }
        return pts;
    }

    /** A death worth showing: one recorded live (the ones copied from old logs have no details). */
    private static boolean detailed(DeathCoach.Death d) {
        return d.level() > 0 && d.ehp() > 0;
    }

    /** The death's main cause, as a language key; drives its colour too. */
    static String cause(DeathCoach.Death d) {
        if (d.areaLevel() > d.level() + 2) {
            return "wynnlabs.deaths.cause_over";
        }
        if (d.secondsToZero() > 0 && d.secondsToZero() <= 3) {
            return "wynnlabs.deaths.cause_burst";
        }
        if (d.potions() == 0) {
            return "wynnlabs.deaths.cause_nopots";
        }
        if (d.secondsToZero() > 3) {
            return "wynnlabs.deaths.cause_slow";
        }
        return "wynnlabs.deaths.cause_unknown";
    }

    static int causeColour(String cause) {
        return switch (cause) {
            case "wynnlabs.deaths.cause_over" -> Theme.BAD;
            case "wynnlabs.deaths.cause_burst" -> 0xFFB06A1C;
            case "wynnlabs.deaths.cause_nopots" -> 0xFFA8406E;
            case "wynnlabs.deaths.cause_slow" -> Theme.LINK;
            default -> Theme.INK_3;
        };
    }

    /** Nameplate leftovers off a mob name ("Rai-Elisu  1s" → "Rai-Elisu"). */
    private static String mobName(String m) {
        return m.replaceAll("\\s{2,}.*$", "").replaceAll("\\s+\\d+s$", "").trim();
    }

    /** The detailed deaths, newest first, kept a second (the file is read by the coach, not here). */
    private static List<DeathCoach.Death> recent() {
        return QuestsCache.kept("deaths-recent", 1000, () -> {
            List<DeathCoach.Death> out = new ArrayList<>();
            for (DeathCoach.Death d : DeathCoach.all()) {
                if (detailed(d)) {
                    out.add(0, d);
                }
            }
            return out;
        });
    }

    private static List<DeathCoach.Death> week() {
        long now = System.currentTimeMillis();
        List<DeathCoach.Death> out = new ArrayList<>();
        for (DeathCoach.Death d : recent()) {
            if (now - d.time() < WEEK) {
                out.add(d);
            }
        }
        return out;
    }

    /** The block: the week's count in the title, the last {@code rows} deaths; returns the y under it. */
    int block(GuiGraphics g, int x, int y, int w, int rows) {
        List<DeathCoach.Death> all = recent();
        String meta = L.t("wynnlabs.tree.deaths_week", week().size());
        y += tab.side.head(g, L.t("wynnlabs.tree.deaths_head"), meta, all.isEmpty() ? null : L.t("wynnlabs.tree.all"),
                "#treedrawer:deaths", x, y, w);
        if (all.isEmpty()) {
            Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), L.t("wynnlabs.tree.deaths_none"), w), x, y);
            return y + Type.BODY.lineHeight;
        }
        for (int i = 0; i < all.size() && i < rows; i++) {
            DeathCoach.Death d = all.get(i);
            String cause = cause(d);
            Art.icon(g, "skull", x, Type.BODY.onCaps(y, 8), 8, causeColour(cause));
            String c = L.t(cause);
            int sw = Type.META.width(font(), c) + 2 * Theme.SP_2;
            Ui.seal(g, font(), c, x + w - sw, Type.BODY.onCaps(y, Ui.SEAL_H), causeColour(cause));
            String when = ago(d.time());
            int tx = x + 8 + Theme.SP_3;
            String place = Type.BODY.fit(font(), d.place().isEmpty() ? "?" : L.cap(d.place()), x + w - sw - Theme.SP_3 - tx - 40);
            Type.BODY.draw(g, font(), place, tx, y);
            Type.META.draw(g, font(), Type.META.fit(font(), when, x + w - sw - Theme.SP_3 - tx - Type.BODY.width(font(), place) - Theme.SP_3),
                    tx + Type.BODY.width(font(), place) + Theme.SP_3, y + 1, Theme.INK_3);
            menu.hits.add(new MenuHit(x, y, w, Type.BODY.lineHeight, null, tip(d), pin(d)));
            y += Type.BODY.lineHeight + Theme.SP_2 + 2;
        }
        return y;
    }

    private static String pin(DeathCoach.Death d) {
        return "#qnear:" + d.x() + " " + d.y() + " " + d.z() + " " + L.t("wynnlabs.deaths.pin", d.place());
    }

    private static List<Component> tip(DeathCoach.Death d) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(L.cap(d.place())).withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal(L.t(cause(d))).withStyle(ChatFormatting.GRAY));
        if (!d.mobs().isEmpty()) {
            List<String> mobs = new ArrayList<>();
            for (String m : d.mobs()) {
                mobs.add(mobName(m));
            }
            for (String l : Advice.wrap(L.t("wynnlabs.deaths.tip_mobs", String.join(", ", mobs)), 46)) {
                tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
            }
        }
        for (String a : d.adviceText()) {
            for (String l : Advice.wrap("→ " + a, 46)) {
                tip.add(Component.literal(l).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        tip.add(Component.literal(L.t("wynnlabs.deaths.tip_click")).withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    /** The drawer: the week in one line, every death, what would have helped, the map; returns its height. */
    int drawer(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        int y0 = y;
        List<DeathCoach.Death> all = recent();
        if (all.isEmpty()) {
            Type.MUTED.draw(g, font(), L.t("wynnlabs.tree.deaths_none"), x, y);
            return Type.BODY.lineHeight;
        }
        List<DeathCoach.Death> week = week();
        Map<String, Integer> places = new HashMap<>();
        Map<String, Integer> causes = new HashMap<>();
        for (DeathCoach.Death d : week) {
            places.merge(d.place().isEmpty() ? "?" : L.cap(d.place()), 1, Integer::sum);
            causes.merge(cause(d), 1, Integer::sum);
        }
        String worst = best(places);
        String main = best(causes);
        // the week in one line
        int cx = x;
        String[][] facts = {{L.n("wynnlabs.deaths.tile_count", week.size()), L.t("wynnlabs.deaths.tile_week")},
            {worst == null ? "—" : worst, L.t("wynnlabs.deaths.tile_place")},
            {main == null ? "—" : L.t(main), L.t("wynnlabs.deaths.tile_cause")}};
        for (int i = 0; i < facts.length; i++) {
            int vw = Type.LABEL.width(font(), facts[i][0]);
            int lw = Type.META.width(font(), facts[i][1]);
            if (cx + vw + Theme.SP_2 + lw > x + w) {
                break;
            }
            Type.LABEL.draw(g, font(), facts[i][0], cx, y, i == 2 && main != null ? causeColour(main) : Theme.INK);
            Type.META.draw(g, font(), facts[i][1], cx + vw + Theme.SP_2, y + 1, Theme.INK_3);
            cx += vw + Theme.SP_2 + lw + Theme.SP_7;
        }
        y += Type.LABEL.lineHeight + Theme.SP_5;
        int lw = w;
        int ly = y;
        rowRects.clear();
        rowLit = null;
        int rh = Theme.SP_2 + Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_2;
        for (int i = 0; i < all.size(); i++) {
            DeathCoach.Death d = all.get(i);
            String cause = cause(d);
            boolean hover = mx >= x - Theme.SP_2 && mx < x + lw + Theme.SP_2 && my >= ly && my < ly + rh && my >= top && my < bottom;
            String num = String.valueOf(i + 1);
            if (hover) {
                rowLit = num;                           // its marker lights on the map
            }
            hover |= num.equals(map.hovered);           // and the other way round
            if (i == 0) {
                Kit.rowLit(g, x - Theme.SP_2, ly, lw + 2 * Theme.SP_2, rh);
            } else if (hover) {
                g.fill(x - Theme.SP_2, ly, x + lw + Theme.SP_2, ly + rh, Theme.CARD_HOVER);
            }
            int ty = ly + Theme.SP_2;
            int numW = Type.NUMBER.width(font(), "00");            // a fixed column: rows 1 to 99 line up
            Type.NUMBER.drawRight(g, font(), num, x + numW, ty, Theme.INK_3);   // its marker's number on the map
            int nx = x + numW + Theme.SP_2;
            Art.icon(g, "skull", nx, Type.BODY.onCaps(ty, 8), 8, causeColour(cause));
            String c = L.t(cause);
            int sw = Type.META.width(font(), c) + 2 * Theme.SP_2;
            Ui.seal(g, font(), c, x + lw - sw, Type.BODY.onCaps(ty, Ui.SEAL_H), causeColour(cause));
            int tx = nx + 8 + Theme.SP_3;
            Type.BODY.draw(g, font(), Type.BODY.fit(font(), d.place().isEmpty() ? "?" : L.cap(d.place()), x + lw - sw - Theme.SP_3 - tx), tx, ty);
            List<String> m = new ArrayList<>();
            m.add(ago(d.time()));
            if (d.areaLevel() > 0) {
                m.add(L.t("wynnlabs.deaths.chip_zone", d.areaLevel(), d.level()));
            }
            if (d.secondsToZero() > 0) {
                m.add(L.t("wynnlabs.deaths.chip_seconds", dec(d.secondsToZero(), 1)));
            }
            if (d.potions() >= 0) {
                m.add(L.n("wynnlabs.deaths.chip_potions", d.potions()));
            }
            Type.META.draw(g, font(), Type.META.fit(font(), String.join(" · ", m), x + lw - tx), tx, ty + Type.BODY.lineHeight, Theme.INK_2);
            menu.hits.add(new MenuHit(x - Theme.SP_2, ly, lw + 2 * Theme.SP_2, rh, null, tip(d), pin(d)));
            rowRects.add(new int[] {x - Theme.SP_2, ly, lw + 2 * Theme.SP_2, rh, i});
            ly += rh + Theme.SP_2;
        }
        // what would have helped, for the newest
        List<String> help = all.get(0).adviceText();
        if (!help.isEmpty()) {
            ly += Theme.SP_3;
            ly += tab.side.head(g, L.t("wynnlabs.tree.deaths_help", L.cap(all.get(0).place())), null, null, null, x, ly, lw);
            for (String a : help) {
                Art.icon(g, "arrow-up-right", x, Type.BODY.onCaps(ly, 8), 8, Theme.GOOD);
                Type.BODY.draw(g, font(), Type.BODY.fit(font(), a, lw - 8 - Theme.SP_3), x + 8 + Theme.SP_3, ly);
                ly += Type.BODY.lineHeight + Theme.SP_2;
            }
        }
        return ly - y0;
    }

    private static String best(Map<String, Integer> counts) {
        String best = null;
        for (var e : counts.entrySet()) {
            if (best == null || e.getValue() > counts.get(best)) {
                best = e.getKey();
            }
        }
        return best;
    }
}
