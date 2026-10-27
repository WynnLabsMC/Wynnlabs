package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.LevelRoad;
import io.github.anilken.wynnlabs.crafting.ProfessionPlan;
import io.github.anilken.wynnlabs.crafting.RoadChoices;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.quests.Waypoints;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static io.github.anilken.wynnlabs.ui.CraftFormat.thousands;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * The level road's steps, one action each, in order: done ones faded with a tick, the one to do now
 * on a soft card (its progress, the way there, and the other route), the next three, and the rest
 * folded under their level band ("+N adım", a click opens it). A material step offers gathering and
 * buying as equals; a step the mod cannot see done (a craft run) gets a "Yaptım" tick.
 */
final class CraftRoadSteps {

    private static final int NEXT = 3;
    private static final int ICON = 16;

    private final CraftingTab tab;
    private final CraftRoadPanel panel;
    private final Set<Integer> openBands = new HashSet<>();

    CraftRoadSteps(CraftingTab tab, CraftRoadPanel panel) {
        this.tab = tab;
        this.panel = panel;
    }

    /** Draws the steps from y across w; returns the y under them. */
    int draw(GuiGraphics g, int x, int y, int w, LevelRoad.Road road, int level, int top, int bottom, int mx, int my) {
        List<LevelRoad.Step> steps = road.steps();
        if (steps.isEmpty()) {
            return y;
        }
        int cur = road.current();
        if (cur < 0) {
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.road.reached"), x, y, Theme.GOOD);
            return y + Type.MUTED.lineHeight;
        }
        int curBand = steps.get(cur).fromLevel();
        int cy = y;
        int i = 0;
        while (i < steps.size()) {
            LevelRoad.Step s = steps.get(i);
            boolean shown = s.fromLevel() <= curBand || i <= cur + NEXT || openBands.contains(s.fromLevel());
            if (!shown) {
                // the rest of this band folded into one row
                int band = s.fromLevel();
                int n = 0;
                int to = s.toLevel();
                List<String> mats = new java.util.ArrayList<>();
                while (i < steps.size() && steps.get(i).fromLevel() == band) {
                    if (steps.get(i).material() != null) {
                        mats.add(steps.get(i).material());
                    }
                    n++;
                    i++;
                }
                cy = folded(g, x, cy, w, band, to, mats, n, mx, my) + Theme.SP_3;
                continue;
            }
            if (i == cur) {
                cy += Theme.SP_3;                   // the card reaches SP_3 above its row: plain page before it
            }
            cy = step(g, s, i + 1, i == cur, x, cy, w, level, mx, my) + (i == cur ? 2 * Theme.SP_3 + Theme.SP_2 : Theme.SP_3);
            i++;
        }
        return cy;
    }

    private int folded(GuiGraphics g, int x, int y, int w, int from, int to, List<String> mats, int n, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + Type.LABEL.lineHeight;
        Art.icon(g, "chevron-right", x + (ICON - 8) / 2, Type.LABEL.onCaps(y, 8), 8, Theme.INK_3);
        int tx = x + ICON + Theme.SP_3;
        String band = "Lv " + from + " → " + to;
        Type.LABEL.draw(g, tab.font, band, tx, y, Theme.INK_2);
        String more = L.n("wynnlabs.road.more_steps", n);
        int mw = Type.META.width(tab.font, more);
        Type.META.drawRight(g, tab.font, more, x + w, y + 1, hover ? Theme.INK : Theme.INK_2);
        int mx2 = tx + Type.LABEL.width(tab.font, band) + Theme.SP_3;
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, String.join(", ", mats), x + w - mw - Theme.SP_4 - mx2), mx2, y + 1, Theme.INK_3);
        tab.acts.add(new Act(x, y, w, Type.LABEL.lineHeight, () -> openBands.add(from), null, null));
        return y + Type.LABEL.lineHeight;
    }

    private int step(GuiGraphics g, LevelRoad.Step s, int num, boolean now, int x, int y, int w, int level, int mx, int my) {
        int rowH = Type.LABEL.lineHeight + Type.META.lineHeight;
        int extra = now ? Theme.SP_3 + Ui.BUTTON_H : 0;
        if (now) {
            Kit.softCard(g, x - Theme.SP_3, y - Theme.SP_3, w + 2 * Theme.SP_3, rowH + extra + 2 * Theme.SP_3);
        }
        boolean done = s.done();
        LevelRoad.Kind kind = s.kind();
        String icon = done ? "check" : kind == LevelRoad.Kind.GATHER ? "pick" : kind == LevelRoad.Kind.BUY ? "shopping-cart" : "sparkles";
        int ic = done ? Theme.INK_3 : kind == LevelRoad.Kind.GATHER ? Theme.GOOD : kind == LevelRoad.Kind.BUY ? Theme.LINK : Theme.ACCENT;
        int iy = y + (rowH - ICON) / 2;
        if (done) {
            Kit.seal(g, x, iy, ICON, ICON, 0xFFB3A27A);
            Art.icon(g, "check", x + (ICON - 8) / 2, iy + (ICON - 8) / 2, 8, Theme.CREAM);
        } else {
            Kit.pill(g, x, iy, ICON, ICON, false, false);
            Art.icon(g, icon, x + (ICON - 8) / 2, iy + (ICON - 8) / 2, 8, ic);
        }
        int tx = x + ICON + Theme.SP_3;
        int r = x + w;
        int ink = done ? Theme.INK_3 : Theme.INK;
        String word = L.t("wynnlabs.road.kind." + kind.name().toLowerCase(Locale.ROOT));
        String what;
        if (kind == LevelRoad.Kind.CRAFT) {
            String n = s.crafts() < 0 ? "?" : "~" + thousands((int) Math.min(Integer.MAX_VALUE, s.crafts()));
            what = n + "× " + typeName(s.recipe().type()) + " " + s.recipe().bracket().replace('-', '–');
        } else {
            what = (s.need() < 0 ? "?" : thousands((int) Math.min(Integer.MAX_VALUE, s.need()))) + " " + s.material();
        }
        // the right side: the routes or the tick, then the count or the gain
        if (!done && s.material() != null) {
            r -= routes(g, s, r, y + (rowH - Ui.CHIP_H) / 2, mx, my) + Theme.SP_4;
        } else if (kind == LevelRoad.Kind.CRAFT && !(done && level >= s.toLevel())) {
            String tick = L.t(done ? "wynnlabs.road.undo" : "wynnlabs.road.ticked");
            int cw = Ui.iconChipWidth(tab.font, done ? null : "check", tick, false);
            int cy2 = y + (rowH - Ui.CHIP_H) / 2;
            Ui.iconChip(g, tab.font, done ? null : "check", tick, false, r - cw, cy2, cw, false, mx, my);
            String key = s.key();
            tab.acts.add(new Act(r - cw, cy2, cw, Ui.CHIP_H, () -> tick(key), null, List.of(L.c("wynnlabs.road.ticked_tip"))));
            r -= cw + Theme.SP_4;
        }
        String head = num + ". " + word + ": ";
        Type.LABEL.draw(g, tab.font, head, tx, y, ink);
        int wx = tx + Type.LABEL.width(tab.font, head);
        Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, what, r - wx), wx, y, ink);
        // the second line: where, and what it gives
        int ly = y + Type.LABEL.lineHeight;
        String right = null;
        int rc = Theme.INK_2;
        if (kind == LevelRoad.Kind.CRAFT) {
            right = "Lv " + s.fromLevel() + " → " + s.toLevel();
            rc = done ? Theme.INK_3 : Theme.ACCENT;
        } else if (s.need() >= 0) {
            right = L.t("wynnlabs.road.have", s.have(), s.need());
            rc = done ? Theme.INK_3 : s.have() >= s.need() ? Theme.GOOD : Theme.WARN;
        }
        // where, then the count or the gain in its own colour, on one line left of the control
        String where = where(s, kind);
        String sep = right == null ? "" : " · ";
        int rw = right == null ? 0 : Type.META.width(tab.font, sep + right);
        String shown = Type.META.fit(tab.font, where, r - rw - tx);
        Type.META.draw(g, tab.font, shown, tx, ly, done ? Theme.INK_3 : Theme.INK_2);
        if (right != null) {
            int ax = tx + Type.META.width(tab.font, shown);
            Type.META.draw(g, tab.font, sep, ax, ly, Theme.INK_3);
            Type.META.draw(g, tab.font, right, ax + Type.META.width(tab.font, sep), ly, rc);
        }
        if (now) {
            current(g, s, tx, ly + Type.META.lineHeight + Theme.SP_3, x + w - tx, mx, my);
        }
        return y + rowH + extra;
    }

    /** Topla / Al as two chips; returns their width. */
    private int routes(GuiGraphics g, LevelRoad.Step s, int right, int y, int mx, int my) {
        String[] labels = {L.t("wynnlabs.road.kind.gather"), L.t("wynnlabs.road.kind.buy")};
        int w0 = Ui.chipWidth(tab.font, labels[0]);
        int w1 = Ui.chipWidth(tab.font, labels[1]);
        int x0 = right - w1 - Theme.SP_1 - w0;
        LevelRoad.Route[] rs = {LevelRoad.Route.GATHER, LevelRoad.Route.BUY};
        int cx = x0;
        for (int i = 0; i < 2; i++) {
            int cw = i == 0 ? w0 : w1;
            Ui.chip(g, tab.font, labels[i], cx, y, cw, s.route() == rs[i], false, false, mx, my);
            LevelRoad.Route to = rs[i];
            String m = s.material();
            tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> {
                RoadChoices ch = RoadChoices.get();
                ch.routes.put(m, to);
                ch.save();
            }, null, i == 0 && !s.gatherable() && s.source() != null
                    ? List.of(L.c("wynnlabs.road.gather_level", Professions.name(s.source().profession().toUpperCase(Locale.ROOT)), s.source().level()))
                    : null));
            cx += cw + Theme.SP_1;
        }
        return right - x0;
    }

    /** Where the step is done: the resource and its distance, the Trade Market and its price, or the station. */
    private String where(LevelRoad.Step s, LevelRoad.Kind kind) {
        if (kind == LevelRoad.Kind.CRAFT) {
            return L.t("wynnlabs.road.at_station", Professions.name(panel.profession));
        }
        if (kind == LevelRoad.Kind.BUY) {
            long missing = s.need() < 0 ? -1 : Math.max(0, s.need() - s.have());
            String unit = s.unitPrice() == null ? L.t("wynnlabs.road.no_price") : "~" + Prices.fmt(s.unitPrice()) + L.t("wynnlabs.road.each");
            String rest = missing > 0 && s.cost() != null ? " · " + L.t("wynnlabs.road.rest_cost", missing, Prices.fmt(s.cost())) : "";
            return L.t("wynnlabs.road.at_market") + " · " + unit + rest;
        }
        LevelRoad.Source src = s.source();
        if (src == null) {
            return L.t("wynnlabs.road.no_source");
        }
        String base = L.t("wynnlabs.road.gather_line", Professions.name(src.profession().toUpperCase(Locale.ROOT)), src.level(), src.resource());
        ProfessionPlan.Spot sp = panel.data.nearest(s.material());
        if (sp != null && !Double.isNaN(sp.distance())) {
            base += " · " + RoadmapScreen.dist((int) Math.round(sp.distance()));
        }
        if (!s.gatherable()) {
            int have = tab.knownLevel(src.profession());
            base += " · " + L.t("wynnlabs.road.gather_need", Professions.name(src.profession().toUpperCase(Locale.ROOT)), src.level(), have);
        }
        return base;
    }

    /** The step to do now: its progress, the way there, and the other route for a material. */
    private void current(GuiGraphics g, LevelRoad.Step s, int x, int y, int w, int mx, int my) {
        String go;
        Runnable pin;
        if (s.kind() == LevelRoad.Kind.CRAFT) {
            go = L.t("wynnlabs.road.go_station");
            String p = panel.profession;
            pin = () -> {
                int d = Crafting.pinStation(p, L.t("wynnlabs.craft.station_label", Professions.name(p)));
                panel.note = d < 0 ? L.t("wynnlabs.craft.station_none") : L.t("wynnlabs.craft.station_pinned", d);
            };
        } else if (s.kind() == LevelRoad.Kind.BUY) {
            go = L.t("wynnlabs.road.go_market");
            pin = () -> pinArea(panel.data.service("trade-market"), "Trade Market");
        } else {
            go = L.t("wynnlabs.road.go_compass");
            String m = s.material();
            pin = () -> {
                ProfessionPlan.Spot sp = panel.data.nearest(m);
                pinArea(sp == null ? null : sp.node(), sp == null ? m : sp.resource().name());
            };
        }
        int bw = Ui.iconButtonLabelWidth(tab.font, go);
        int barW = Math.max(40, Math.min(160, w - bw - Theme.SP_4));
        if (s.need() > 0) {
            CraftRoadPanel.progress(g, x, y + (Ui.BUTTON_H - 6) / 2, barW, 6, s.have() / (double) s.need());
        } else if (s.kind() == LevelRoad.Kind.CRAFT) {
            Type.META.draw(g, tab.font, L.t("wynnlabs.road.craft_note"), x, Type.META.yIn(y, Ui.BUTTON_H), Theme.INK_3);
        }
        int bx = x + barW + Theme.SP_4;
        Ui.iconButtonLabel(g, tab.font, "compass", go, bx, y, bw, mx, my);
        tab.acts.add(new Act(bx, y, bw, Ui.BUTTON_H, pin, null, null));
    }

    private void pinArea(Area a, String label) {
        if (a == null) {
            panel.note = L.t("wynnlabs.road.no_place");
            return;
        }
        panel.note = Waypoints.go(a.x(), a.y(), a.z(), label) ? L.t("wynnlabs.plan.compass_set", label) : L.t("wynnlabs.plan.compass_failed");
    }

    private void tick(String key) {
        RoadChoices ch = RoadChoices.get();
        Set<String> t = ch.ticked(panel.profession);
        if (!t.remove(key)) {
            t.add(key);
        }
        ch.save();
    }

    /** A gathering spot: the resource, its level and materials, how far, and "Pusula ›". */
    int spotRow(GuiGraphics g, ProfessionPlan.Spot s, String profession, int x, int y, int w, int rowH, int mx, int my) {
        int iy = y + (rowH - ICON) / 2;
        Kit.pill(g, x, iy, ICON, ICON, false, false);
        Art.icon(g, CraftRoadPanel.profIcon(profession), x + (ICON - 8) / 2, iy + (ICON - 8) / 2, 8, Theme.INK_2);
        int tx = x + ICON + Theme.SP_3;
        String go = L.t("wynnlabs.road.go_compass");
        int bw = Ui.iconButtonLabelWidth(tab.font, go);
        int bx = x + w - bw;
        Ui.iconButtonLabel(g, tab.font, "compass", go, bx, y + (rowH - Ui.BUTTON_H) / 2, bw, mx, my);
        Area a = s.node();
        String label = s.resource().name();
        tab.acts.add(new Act(bx, y + (rowH - Ui.BUTTON_H) / 2, bw, Ui.BUTTON_H, () -> pinArea(a, label), null, null));
        String d = Double.isNaN(s.distance()) ? "" : " · " + RoadmapScreen.dist((int) Math.round(s.distance()))
                + dirName(Compass.direction(a, CraftRoadData.position()));
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, s.resource().name() + " · Lv " + s.resource().level(), bx - Theme.SP_4 - tx), tx, y);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, String.join(", ", s.resource().materials()) + d, bx - Theme.SP_4 - tx), tx,
                y + Type.LABEL.lineHeight, Theme.INK_3);
        return y + rowH;
    }

    private static String dirName(String d) {
        return d == null ? "" : " " + SearchCardRows.dirName(d);
    }
}
