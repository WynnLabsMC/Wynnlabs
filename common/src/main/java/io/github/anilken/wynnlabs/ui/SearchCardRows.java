package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.MobName;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.TrackedMobs;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The card's "How do I get it" (an item) or "Where" (a thing): every way, easiest first. A row:
 * its source's badge, its words (the mob, town or dungeon it names opens that card: ↗), and under
 * them how far and which way, the place's name and how many more places it has. On the right: a
 * named mob's "Track" (the target, gold while tracked), "Mark on the map" (gold while its places
 * are on Wynntils' map) and "Compass ›" (a row of several places, such as loot chests or spawn
 * areas, pins them all). A click on the row makes it the map's focus. The title's "All on the
 * map" puts every row there and keeps them after the menu closes.
 */
final class SearchCardRows extends MenuPaint {

    /** The badge of every row: Wynntils' 16–18 px icons at 1x with a pixel of tile around them. */
    static final int BADGE = 20;
    private final SearchCardPanel card;

    SearchCardRows(SearchCardPanel card) {
        super(card.tab.menu);
        this.card = card;
    }

    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<Source> rows = card.rows;
        Entry entry = card.entry;
        String title = L.t(entry.item() ? "wynnlabs.search.how" : "wynnlabs.search.where");
        Type.LABEL.draw(g, font(), title, x, y, Theme.INK);
        int places = 0;
        for (Source s : rows) {
            places += s.hasPlace() ? 1 : 0;
        }
        String count = L.n("wynnlabs.search.ways", rows.size());
        Type.META.draw(g, font(), count, x + Type.LABEL.width(font(), title) + Theme.SP_3, y + 1, Theme.INK_3);
        if (places > 1 && GameState.current().running()) {
            boolean on = card.marks.all();
            String all = L.t("wynnlabs.search.mark_all");
            int aw = Ui.iconChipWidth(font(), on ? "check" : "map-2", all, false);
            int ay = Type.LABEL.onCaps(y, Ui.CHIP_H);
            Ui.iconChip(g, font(), on ? "check" : "map-2", all, false, x + w - aw, ay, aw, on, card.over(mx, my) ? mx : -1, my);
            card.click(x + w - aw, ay, aw, Ui.CHIP_H, card::toggleAll);
            card.tip(x + w - aw, ay, aw, Ui.CHIP_H, mx, my, L.t("wynnlabs.search.mark_all_tip"));
        }
        int cy = y + Type.LABEL.lineHeight + Theme.SP_3;
        Kit.hair(g, x, cy, w, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        double[] pos = SearchTab.position();
        for (int i = 0; i < rows.size(); i++) {
            cy = row(g, rows.get(i), i, x, cy, w, pos, mx, my);
        }
        return cy;
    }

    private int row(GuiGraphics g, Source s, int i, int x, int y, int w, double[] pos, int mx, int my) {
        boolean place = s.hasPlace();
        int textH = place ? Type.BODY.lineHeight + Type.META.lineHeight : Type.BODY.lineHeight;
        int ty = y + Theme.SP_2;
        if (i == card.picked && place) {
            Kit.rowLit(g, x - Theme.SP_2, y, w + 2 * Theme.SP_2, textH + 2 * Theme.SP_2);
        }
        if (place) {
            // the row itself: the map's focus (its controls, added after, take their own clicks)
            int row = i;
            card.click(x, y, w, textH + 2 * Theme.SP_2, () -> card.pickRow(row));
        }
        // one badge size for the whole list (the text starts at one x): centred on the row's two
        // lines, or on a single line's capitals
        int badge = BADGE;
        int by = place ? ty + (textH - badge) / 2 : Type.BODY.onCaps(ty, badge);
        Kit.kindBadge(g, x, by, badge, KindBadge.of(s));
        // the controls on the right, right to left: compass, map mark, track
        int r = x + w;
        if (place) {
            String pin = L.t("wynnlabs.search.pin");
            int pw = Ui.chipWidth(font(), pin);
            int py = Type.BODY.onCaps(ty, Ui.SMALL_H);
            Ui.small(g, font(), pin, r - pw, py, pw, card.over(mx, my) ? mx : -1, my);
            Area a = s.main();
            List<Area> all = s.areas();
            String label = card.rowText(s);
            card.click(r - pw, py, pw, Ui.SMALL_H, () -> {
                if (all.size() > 1) {
                    card.pinAll(all);
                } else {
                    card.pin(a, label);
                }
            });
            r -= pw + Theme.SP_2;
            int bw = Ui.CHIP_H + 2;
            int iy = Type.BODY.onCaps(ty, Ui.CHIP_H);
            boolean marked = card.marks.marked(i);
            Ui.iconButton(g, "map-2", r - bw, iy, marked, card.over(mx, my) ? mx : -1, my);
            int row = i;
            card.click(r - bw, iy, bw, Ui.CHIP_H, () -> card.toggleRow(row));
            card.tip(r - bw, iy, bw, Ui.CHIP_H, mx, my, L.t(marked ? "wynnlabs.search.unmark_row" : "wynnlabs.search.mark_row"));
            r -= bw + Theme.SP_2;
        }
        if (trackable(s)) {
            int bw = Ui.CHIP_H + 2;
            int iy = Type.BODY.onCaps(ty, Ui.CHIP_H);
            String mob = s.name();
            boolean on = TrackedMobs.get().has(mob);
            Ui.iconButton(g, "target", r - bw, iy, on, card.over(mx, my) ? mx : -1, my);
            card.click(r - bw, iy, bw, Ui.CHIP_H, () -> {
                boolean now = TrackedMobs.get().toggle(mob);
                menu.flash(L.t(now ? "wynnlabs.search.track_on" : "wynnlabs.search.track_off", mob));
            });
            card.tip(r - bw, iy, bw, Ui.CHIP_H, mx, my, L.t(on ? "wynnlabs.search.tracking_tip" : "wynnlabs.search.track_tip"));
            r -= bw + Theme.SP_2;
        }
        // the words, and the card the row names
        int tx = x + badge + Theme.SP_3;
        Entry link = i < card.rowLinks.size() ? card.rowLinks.get(i) : null;
        int linkW = link == null ? 0 : 8 + Theme.SP_2;
        String text = card.rowText(s);
        String shownText = Type.BODY.fit(font(), text, r - Theme.SP_3 - tx - linkW);
        boolean best = i == 0;
        (best ? Type.LABEL : Type.BODY).draw(g, font(), shownText, tx, ty, Theme.INK);
        int ex = tx + (best ? Type.LABEL : Type.BODY).width(font(), shownText) + Theme.SP_2;
        if (!shownText.equals(text)) {
            card.tip(tx, ty, ex - tx, Type.BODY.lineHeight, mx, my, text);
        }
        if (link != null) {
            int ly = Type.BODY.onCaps(ty, 8);
            Art.icon(g, "arrow-up-right", ex, ly, 8, Theme.LINK);
            card.click(ex - 1, ty, 10, Type.BODY.lineHeight, () -> card.tab.show(link.key()));
            card.tip(ex - 1, ty, 10, Type.BODY.lineHeight, mx, my, L.t("wynnlabs.search.open_card", link.name()));
        }
        if (place) {
            Area a = s.main();
            double d = Compass.distance(a, pos);
            String dir = Compass.direction(a, pos);
            String where = Double.isNaN(d) ? "" : RoadmapScreen.dist((int) Math.round(d)) + (dir == null ? "" : " " + dirName(dir));
            if (a.name() != null && !a.name().equals(s.name())) {
                where = where.isEmpty() ? a.name() : where + " · " + a.name();
            }
            if (s.areas().size() > 1) {
                where += (where.isEmpty() ? "" : " · ") + L.t("wynnlabs.search.more_places", s.areas().size() - 1);
            }
            Type.META.draw(g, font(), Type.META.fit(font(), where, r - Theme.SP_3 - tx), tx, ty + Type.BODY.lineHeight, Theme.INK_3);
        }
        return y + textH + 2 * Theme.SP_2 + Theme.SP_2;
    }

    /** A section's title: LABEL, its META count, a hairline under it; returns the y of its first line. */
    int title(GuiGraphics g, String text, String meta, int x, int y, int w) {
        Type.LABEL.draw(g, font(), text, x, y, Theme.INK);
        if (meta != null) {
            Type.META.draw(g, font(), meta, x + Type.LABEL.width(font(), text) + Theme.SP_3, y + 1, Theme.INK_3);
        }
        int hy = y + Type.LABEL.lineHeight + Theme.SP_3;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    /** A line of words in {@code ink}; returns the y under it. */
    int line(GuiGraphics g, String text, int x, int y, int w, int ink) {
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), text, w), x, y, ink);
        return y + Type.BODY.lineHeight + Theme.SP_1;
    }

    /** A line that opens something (another card, a tab): LINK ink, gold on hover; returns the y under it. */
    int link(GuiGraphics g, String text, int x, int y, int w, int mx, int my, Runnable run) {
        String t = Type.BODY.fit(font(), text, w);
        int tw = Type.BODY.width(font(), t);
        boolean hover = card.over(mx, my) && mx >= x && mx < x + tw && my >= y && my < y + Type.BODY.lineHeight;
        Type.BODY.draw(g, font(), t, x, y, hover ? Theme.GOLD_DEEP : Theme.LINK);
        card.click(x, y, tw, Type.BODY.lineHeight, run);
        return y + Type.BODY.lineHeight + Theme.SP_1;
    }

    /** A compass point of {@link io.github.anilken.wynnlabs.search.Compass#direction} in the player's language. */
    static String dirName(String dir) {
        return switch (dir) {
            case "east" -> L.t("wynnlabs.grind.dir_east");
            case "southeast" -> L.t("wynnlabs.grind.dir_southeast");
            case "south" -> L.t("wynnlabs.grind.dir_south");
            case "southwest" -> L.t("wynnlabs.grind.dir_southwest");
            case "west" -> L.t("wynnlabs.grind.dir_west");
            case "northwest" -> L.t("wynnlabs.grind.dir_northwest");
            case "north" -> L.t("wynnlabs.grind.dir_north");
            default -> L.t("wynnlabs.grind.dir_northeast");
        };
    }

    /** Whether a row names a mob the tracker can look for. */
    private static boolean trackable(Source s) {
        return (s.type() == Source.Type.MOB || s.type() == Source.Type.MINIBOSS)
                && s.name() != null && !MobName.key(s.name()).isEmpty();
    }
}
