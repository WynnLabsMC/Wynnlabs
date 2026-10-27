package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceText;
import io.github.anilken.wynnlabs.search.Sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The one-item window's stats: for gear, "Yours 72%" with your own rolls as the listings' chips
 * and every stat's lowest … highest on the right (so the listings' percentages read against
 * something); for an item without rolls (an ingredient, a material), what it needs and where it is
 * used, then its stats. Under them one line on where it comes from besides the market. Lines past
 * the column's height are left out.
 */
final class BoxStatsPanel {

    /** The easiest source, read once per item and then every half minute, never per frame. */
    private static String readFor;
    private static long readAt;
    private static Source from;

    private BoxStatsPanel() {}

    static void draw(BoxScreen s, GuiGraphics g, String name, int x, int y, int w, int h) {
        Entry e = ItemIndex.get().byName(name);
        BoxChips.use(name);
        int bottom = y + h;
        if (e == null) {
            if (s.single != null && s.single.ownRoll() != null && !s.own.isEmpty()) {
                // no item data yet: yours as chips all the same, without the ranges
                int cy = head(s, g, L.t("wynnlabs.box.own_seal", BoxScreen.pct(Math.round(s.single.ownRoll()))), null, x, y, w);
                for (io.github.anilken.wynnlabs.search.BoxMarket.StatRoll st : s.single.ownStats()) {
                    if (cy + BoxChips.H > bottom) {
                        return;
                    }
                    BoxChips.draw(g, s.font(), st, null, false, false, x, cy);
                    cy += BoxChips.H + Theme.SP_2;
                }
                return;
            }
            Type.MUTED.draw(g, s.font(), Type.MUTED.fit(s.font(), L.t("wynnlabs.box.stats_unknown"), w), x, y, Theme.INK_3);
            return;
        }
        int cy = y;
        if (!s.rolled()) {
            List<String[]> needs = new ArrayList<>();
            needs.add(new String[] {L.t("wynnlabs.box.stats_level"), String.valueOf(e.level())});
            if (e.klass() != null && e.klass() != Klass.NONE) {
                needs.add(new String[] {L.t("wynnlabs.box.stats_class"), SearchResultsPanel.words(e.klass().name())});
            }
            for (Map.Entry<String, Integer> sk : e.skills().entrySet()) {
                if (sk.getValue() != null && sk.getValue() > 0) {
                    needs.add(new String[] {SearchResultsPanel.words(sk.getKey()), String.valueOf(sk.getValue())});
                }
            }
            if (e.kind() == Kind.INGREDIENT && !e.professions().isEmpty()) {
                List<String> prof = new ArrayList<>();
                e.professions().forEach(p -> prof.add(SearchResultsPanel.words(p)));
                needs.add(new String[] {L.t("wynnlabs.box.stats_used_in"), String.join(", ", prof)});
            }
            cy = head(s, g, L.t("wynnlabs.box.stats_needs"), null, x, cy, w);
            for (String[] r : needs) {
                if (cy + Type.BODY.lineHeight > bottom) {
                    return;
                }
                pair(s, g, r[0], r[1], Theme.INK, x, cy, w);
                cy += Type.BODY.lineHeight + Theme.SP_1;
            }
            cy += Theme.SP_3;
        }
        Map<String, Double> own = s.own;
        boolean yours = s.rolled() && s.single != null && s.single.ownRoll() != null;
        if (!e.stats().isEmpty() || !e.modifiers().isEmpty()) {
            if (cy + Type.LABEL.lineHeight > bottom) {
                return;
            }
            String title = yours ? L.t("wynnlabs.box.own_seal", BoxScreen.pct(Math.round(s.single.ownRoll()))) : L.t("wynnlabs.box.stats_title");
            cy = head(s, g, title, L.t("wynnlabs.box.stats_range"), x, cy, w);
        }
        for (Entry.Stat st : e.stats()) {
            Double p = own.get(st.key());
            int rowH = BoxChips.H + Theme.SP_2;
            if (cy + rowH > bottom) {
                return;
            }
            String range = st.min() == st.max() ? BoxChips.signed(st.min()) : BoxChips.signed(st.min()) + " … " + BoxChips.signed(st.max());
            if (p != null) {
                // yours as the listings' chip, the range on the right
                Type.META.drawRight(g, s.font(), range, x + w, Type.META.yIn(cy, BoxChips.H), Theme.INK_3);
                BoxChips.draw(g, s.font(), s.ownRoll(st.key()), null, false, false, x, cy);
            } else {
                // no roll of yours: the same pill with its value (a fixed stat) or its range
                if (st.min() != st.max()) {
                    Type.META.drawRight(g, s.font(), range, x + w, Type.META.yIn(cy, BoxChips.H), Theme.INK_3);
                }
                String v = st.min() == st.max() ? " " + range : "";
                BoxChips.plain(g, s.font(), BoxChips.name(st.key()) + v, st.max() < 0 && st.min() < 0 ? Theme.BAD : Theme.INK_2, x, cy);
            }
            cy += rowH;
        }
        for (Map.Entry<String, Integer> m : e.modifiers().entrySet()) {
            if (cy + Type.BODY.lineHeight > bottom) {
                return;
            }
            pair(s, g, SearchResultsPanel.statName(m.getKey()), BoxChips.signed(m.getValue()), m.getValue() < 0 ? Theme.BAD : Theme.GOOD, x, cy, w);
            cy += Type.BODY.lineHeight + Theme.SP_1;
        }
        source(s, g, e, x, cy, w, bottom);
    }

    /** A section's LABEL title, a META note on the right, a hairline; returns the first line's y. */
    private static int head(BoxScreen s, GuiGraphics g, String title, String note, int x, int y, int w) {
        Type.LABEL.draw(g, s.font(), title, x, y, Theme.INK);
        if (note != null) {
            Type.META.drawRight(g, s.font(), Type.META.fit(s.font(), note, w - Type.LABEL.width(s.font(), title) - Theme.SP_3), x + w, y + 1,
                    Theme.INK_3);
        }
        int hy = y + Type.LABEL.lineHeight + Theme.SP_1;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    private static void pair(BoxScreen s, GuiGraphics g, String name, String value, int ink, int x, int y, int w) {
        int vw = Type.BODY.width(s.font(), value);
        Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), name, w - vw - Theme.SP_3), x, y, Theme.INK_2);
        Type.BODY.drawRight(g, s.font(), value, x + w, y, ink);
    }

    /**
     * The easiest way to get the item besides the market, as one line under the stats: its badge,
     * a short text and how far away it is, from {@code cy} down to {@code bottom}.
     */
    private static void source(BoxScreen s, GuiGraphics g, Entry e, int x, int cy, int w, int bottom) {
        long now = System.currentTimeMillis();
        if (!e.name().equals(readFor) || now - readAt > 30_000) {
            readFor = e.name();
            readAt = now;
            from = null;
            for (Source src : Sources.of(e, ItemIndex.get().places(), Sources.Owned.NONE, null, SearchTab.position())) {
                if (src.type() != Source.Type.MARKET) {
                    from = src;                 // the market is this whole window
                    break;
                }
            }
        }
        Source src = from;
        cy += Theme.SP_3;
        if (src == null || cy + 1 + Theme.SP_3 + KindBadge.MIN > bottom) {
            return;
        }
        Kit.hair(g, x, cy, w, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        int bs = Kit.kindBadge(g, x, Type.BODY.onCaps(cy, KindBadge.MIN), KindBadge.MIN, KindBadge.of(src));
        double[] pos = SearchTab.position();
        Area a = src.main();
        double d = Compass.distance(a, pos);
        String dist = a == null || Double.isNaN(d) ? "" : RoadmapScreen.dist((int) Math.round(d));
        int dw = dist.isEmpty() ? 0 : Type.META.width(s.font(), dist) + Theme.SP_3;
        int tx = x + bs + Theme.SP_3;
        Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), SourceText.row(src, null), x + w - dw - tx), tx, cy, Theme.INK);
        if (!dist.isEmpty()) {
            Type.META.drawRight(g, s.font(), dist, x + w, cy + 1, Theme.INK_3);
        }
    }
}
