package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.quests.Waypoints;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Kind;
import io.github.anilken.wynnlabs.search.MapMark;
import io.github.anilken.wynnlabs.search.SearchQuery;
import io.github.anilken.wynnlabs.search.TrackedMobs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * The search tab's dropdowns, all through {@link UiMenu}: the sort, the filters (the category's
 * own kinds, the level range, my class, mine, on the Trade Market), the map marks the player kept
 * (a row puts the compass there, its ✕ takes the mark off, "Remove all" clears the map) and the
 * tracked mobs (a row opens the mob's card, its ✕ stops tracking it, "Stop all"). One is open at
 * a time; the filter menu stays open while its toggles are clicked.
 */
final class SearchMenus {

    static final int NONE = 0;
    static final int SORT = 1;
    static final int FILTER = 2;
    static final int MARKS = 3;
    static final int TRACKED = 4;
    static final String[] SORTS = {"wynnlabs.search.sort_easiest", "wynnlabs.search.sort_level", "wynnlabs.search.sort_name",
        "wynnlabs.search.sort_price"};

    private final SearchTab tab;
    int open = NONE;

    /** What "Remove all" / "Stop all" took, for "Undo" a few seconds (or until the list changes again). */
    record Undo(List<MapMark> marks, List<String> mobs, long until, int version) {}

    private static final long UNDO_MS = 6000;
    Undo undo;

    /** The undo still on offer, or null. */
    Undo undo() {
        Undo u = undo;
        if (u == null || System.currentTimeMillis() > u.until()
                || u.marks() != null && io.github.anilken.wynnlabs.search.KeptMarks.get().version() != u.version()
                || u.mobs() != null && !TrackedMobs.get().names().isEmpty()) {
            undo = null;
            return null;
        }
        return u;
    }

    /** "Undo": the marks or the tracked mobs back. */
    void undoNow() {
        Undo u = undo();
        if (u == null) {
            return;
        }
        if (u.marks() != null) {
            tab.card.restoreMarks(u.marks());
        } else {
            for (String m : u.mobs()) {
                TrackedMobs.get().toggle(m);
            }
        }
        undo = null;
        tab.menu.flash(L.t("wynnlabs.search.undone"));
    }
    final UiMenu.State state = new UiMenu.State();

    SearchMenus(SearchTab tab) {
        this.tab = tab;
    }

    void toggle(int which) {
        open = open == which ? NONE : which;
        state.reset();
    }

    private int[] anchor() {
        return switch (open) {
            case SORT -> tab.sortAt;
            case FILTER -> tab.strip.filterAt;
            case MARKS -> tab.strip.marksAt;
            case TRACKED -> tab.strip.trackedAt;
            default -> null;
        };
    }

    boolean showing() {
        return open != NONE && anchor() != null;
    }

    /** Draws the open menu over the page; returns its box (null when none). */
    int[] draw(GuiGraphics g, int mx, int my) {
        int[] at = anchor();
        if (at == null) {
            open = NONE;
            return null;
        }
        List<UiMenu.Item> items = items();
        if (items.isEmpty()) {
            open = NONE;
            return null;
        }
        boolean removable = open == MARKS || open == TRACKED;
        // the marked and tracked lists keep "Remove all" in a header that never scrolls away
        int head = removable ? HEAD : 0;
        int[] under = removable ? new int[] {at[0], at[1], at[2], at[3] + head} : at;
        int[] b = UiMenu.draw(g, tab.font(), UiMenu.Spec.under(under, tab.menu.width, tab.menu.height).rows(12), state, items, mx, my, r -> {
            String id = r.item().id();
            tab.click(r.x(), r.y(), r.w(), r.h(), () -> pick(id, false));
            if (removable && (id.startsWith("m:") || id.startsWith("t:"))) {
                // the ✕ at the row's end: added last, so it wins over the row
                int xw = Type.BODY.width(tab.font(), "✕") + 2 * Theme.SP_3;
                tab.click(r.x() + r.w() - xw, r.y(), xw, r.h(), () -> pick(id, true));
            }
        });
        if (!removable || b == null) {
            return b;
        }
        int hy = b[1] >= at[1] + at[3] ? b[1] - head : b[1] + b[3];
        header(g, b[0], hy, b[2], mx, my);
        return new int[] {b[0], Math.min(b[1], hy), b[2], b[3] + head};
    }

    /** The header's height: a small button with SP_2 above and below. */
    private static final int HEAD = Ui.SMALL_H + 2 * Theme.SP_2;

    /** "229 marks" and the red "Remove all" (or "Stop all"), pinned over the list. */
    private void header(GuiGraphics g, int x, int y, int w, int mx, int my) {
        boolean marks = open == MARKS;
        int n = marks ? tab.card.keptMarks().size() : TrackedMobs.get().names().size();
        Kit.softCard(g, x, y, w, HEAD + 2);
        String count = marks ? L.n("wynnlabs.search.marks_count", n) : n + " / " + TrackedMobs.MAX;
        Type.META.draw(g, tab.font(), count, x + Theme.SP_3, Type.META.yIn(y, HEAD), Theme.INK_3);
        String label = L.t(marks ? "wynnlabs.search.marks_clear" : "wynnlabs.search.track_clear");
        int bw = 2 * Theme.SP_3 + 8 + Theme.SP_1 + Type.BODY.width(tab.font(), label);
        int bx = x + w - Theme.SP_3 - bw;
        int by = y + Theme.SP_2;
        boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + Ui.SMALL_H;
        Kit.pill(g, bx, by, bw, Ui.SMALL_H, false, hover);
        Art.icon(g, "trash", bx + Theme.SP_3, by + Ui.iconIn(Ui.SMALL_H, 8), 8, Theme.BAD);
        Type.BODY.draw(g, tab.font(), label, bx + Theme.SP_3 + 8 + Theme.SP_1, Type.BODY.yIn(by, Ui.SMALL_H), Theme.BAD);
        tab.click(bx, by, bw, Ui.SMALL_H, () -> pick(marks ? "m:clear" : "t:clear", false));
    }

    private List<UiMenu.Item> items() {
        UiMenu.Items out = new UiMenu.Items();
        switch (open) {
            case SORT -> {
                for (int i = 0; i < SORTS.length; i++) {
                    out.add(UiMenu.Item.of("s:" + i, null, L.t(SORTS[i]), null, i == tab.sort.ordinal()));
                }
            }
            case FILTER -> filterItems(out);
            case MARKS -> {
                double[] pos = SearchTab.position();
                List<MapMark> marks = tab.card.keptMarks();
                for (int i = 0; i < marks.size(); i++) {
                    MapMark m = marks.get(i);
                    double d = pos == null ? Double.NaN : Compass.distance(Area.point(m.x(), m.y(), m.z()), pos);
                    String sub = m.x() + ", " + m.y() + ", " + m.z() + (Double.isNaN(d) ? "" : " · " + RoadmapScreen.dist((int) Math.round(d)));
                    out.add(UiMenu.Item.of("m:" + i, "map-pin", m.label(), "✕", false).sub(sub)
                            .tip(List.of(Component.literal(L.t("wynnlabs.search.mark_row_tip")))));
                }
            }
            case TRACKED -> {
                List<String> names = TrackedMobs.get().names();
                for (int i = 0; i < names.size(); i++) {
                    out.add(UiMenu.Item.of("t:" + i, "skull", names.get(i), "✕", false)
                            .tip(List.of(Component.literal(L.t("wynnlabs.search.tracked_row_tip")))));
                }
            }
            default -> {
                // nothing open
            }
        }
        return out.list;
    }

    private void filterItems(UiMenu.Items out) {
        if (tab.category.narrows()) {
            SearchQuery.Result r = tab.lastResult;
            for (Kind k : tab.category.kinds()) {
                Integer n = r == null ? null : r.counts().get(k);
                out.add(UiMenu.Item.of("k:" + k.name(), null, L.t("wynnlabs.search.kind." + k.name().toLowerCase(Locale.ROOT)),
                        n == null ? "0" : String.valueOf(n), tab.kinds.contains(k)));
            }
            out.divide();
        }
        int lv = GameState.current().level();
        out.add(UiMenu.Item.of("l:0", null, L.t("wynnlabs.search.level_all"), null, tab.levelMode == 0));
        if (lv > 0) {
            out.add(UiMenu.Item.of("l:1", null, L.t("wynnlabs.search.level_upto", lv), null, tab.levelMode == 1));
            out.add(UiMenu.Item.of("l:2", null, L.t("wynnlabs.search.level_near", Math.max(1, lv - 10), lv), null, tab.levelMode == 2));
        }
        out.divide();
        out.add(UiMenu.Item.of("f:class", null, L.t("wynnlabs.search.my_class"), null, tab.myClass));
        out.add(UiMenu.Item.of("f:owned", null, L.t("wynnlabs.search.owned"), null, tab.owned));
        out.add(UiMenu.Item.of("f:market", null, L.t("wynnlabs.search.on_market"), null, tab.onMarket));
        if (tab.filtersOn() > 0) {
            out.divide().add(UiMenu.Item.of("f:clear", "eraser", L.t("wynnlabs.search.clear_filters"), null, false).asLink());
        }
    }

    /** A row picked (by click or Enter); {@code remove}: its ✕. */
    void pick(String id, boolean remove) {
        String v = id.substring(2);
        switch (id.charAt(0)) {
            case 's' -> {
                tab.sort = SearchQuery.Sort.values()[Integer.parseInt(v)];
                open = NONE;
            }
            case 'k' -> {
                Kind k = Kind.valueOf(v);
                if (!tab.kinds.remove(k)) {
                    tab.kinds.add(k);
                }
            }
            case 'l' -> tab.levelMode = Integer.parseInt(v);
            case 'f' -> {
                switch (v) {
                    case "class" -> tab.myClass = !tab.myClass;
                    case "owned" -> tab.owned = !tab.owned;
                    case "market" -> tab.onMarket = !tab.onMarket;
                    default -> {
                        tab.clearFilters();
                        open = NONE;
                    }
                }
            }
            case 'm' -> marks(v, remove);
            case 't' -> tracked(v, remove);
            default -> {
                // not a row of ours
            }
        }
        tab.results.scroll = 0;
    }

    private void marks(String v, boolean remove) {
        if (v.equals("clear")) {
            List<MapMark> was = tab.card.clearMarks();
            undo = new Undo(was, null, System.currentTimeMillis() + UNDO_MS, io.github.anilken.wynnlabs.search.KeptMarks.get().version());
            tab.menu.flash(L.n("wynnlabs.search.marks_removed", was.size()));
            open = NONE;
            return;
        }
        List<MapMark> marks = tab.card.keptMarks();
        int i = Integer.parseInt(v);
        if (i >= marks.size()) {
            return;
        }
        MapMark m = marks.get(i);
        if (remove) {
            tab.card.removeMark(m);
            if (tab.card.keptMarks().isEmpty()) {
                open = NONE;
            }
            return;
        }
        String what = m.label().length() > 32 ? m.label().substring(0, 32) : m.label();
        if (Waypoints.go(m.x(), m.y(), m.z(), what)) {
            tab.menu.flash(L.t("wynnlabs.screen.guiding", what));
        }
        open = NONE;
    }

    private void tracked(String v, boolean remove) {
        TrackedMobs t = TrackedMobs.get();
        if (v.equals("clear")) {
            List<String> was = t.names();
            t.clear();
            undo = new Undo(null, was, System.currentTimeMillis() + UNDO_MS, 0);
            tab.menu.flash(L.n("wynnlabs.search.track_removed", was.size()));
            open = NONE;
            return;
        }
        List<String> names = t.names();
        int i = Integer.parseInt(v);
        if (i >= names.size()) {
            return;
        }
        String mob = names.get(i);
        if (remove) {
            t.toggle(mob);
            tab.menu.flash(L.t("wynnlabs.search.track_off", mob));
            if (t.names().isEmpty()) {
                open = NONE;
            }
            return;
        }
        tab.show("mob:" + mob);
        open = NONE;
    }

    /** Up, down and Enter while a menu is open. */
    boolean key(int key) {
        UiMenu.Item it = state.key(key);
        if (it != null) {
            pick(it.id(), false);
        }
        return true;
    }
}
