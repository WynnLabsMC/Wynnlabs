package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.search.EmptyPage;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;
import io.github.anilken.wynnlabs.search.RecentSearches;
import io.github.anilken.wynnlabs.search.SearchQuery;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The search tab's list. Before anything is typed it is the whole catalogue: the road map's
 * missing pieces first (and the recent searches on a small window), then everything, in the
 * sort's order; typing, the category strip and the filters narrow it. A row: the item's own icon
 * in an HD slot (a thing's badge), its name in its rarity's colour, a META line (kind, level, why
 * it matched), and on the right the easiest way to get it, how far a thing is, "Yours", or the
 * chosen stat's roll. A category with a list of its own shows that instead ({@link SearchViews#ownRows}:
 * the Stats category with no stat chosen lists the stats). Nothing found: the
 * closest name ("did you mean") and a way out of the filters. Rows are built once per answer.
 */
final class SearchResultsPanel extends MenuPaint {

    /** A row's pitch: a name and a META line, SP_2 inside the wash above and below, SP_2 of page between washes. */
    static final int ROW = Type.BODY.lineHeight + Type.META.lineHeight + 2 * Theme.SP_2 + Theme.SP_2;
    /** A group's title: SP_3 above and below a LABEL line with its hairline. */
    static final int HEADER = Type.LABEL.lineHeight + 2 * Theme.SP_3;

    final SearchTab tab;
    int scroll;
    private int scrollMax;
    private List<SearchRow> rows = List.of();
    private int[] tops = new int[0];
    private int total;
    private int rightW;
    private Object builtFor;
    private boolean reveal;
    private final ScrollBar bar = new ScrollBar();
    private List<SearchRow> selRows;
    private String selFor;
    private int selAt = -1;
    /** Whether the empty page headed with the recent searches when it was last built. */
    private boolean recent;

    SearchResultsPanel(SearchTab tab) {
        super(tab.menu);
        this.tab = tab;
    }

    // ---- the rows, once per answer ----

    private void build(SearchQuery.Result result, boolean compact) {
        ItemIndex index = ItemIndex.get();
        List<String> missing = tab.missingPieces();
        List<String> last = RecentSearches.get().newest(EmptyPage.FIRST_ROWS);
        boolean blank = tab.box.getValue().isBlank();
        Object key = List.of(System.identityHashCode(result), compact, missing, last, tab.category, String.valueOf(tab.stat),
                SearchTab.expanded);
        if (key.equals(builtFor)) {
            return;
        }
        builtFor = key;
        List<SearchRow> out = SearchViews.ownRows(tab, index, blank);
        if (out != null) {
            recent = false;                         // a category's own list has no recent group, so no "Clear" either
        } else {
            SearchRowSources.Page page = SearchRowSources.emptyPage(tab, index, blank, missing, last, !result.hits().isEmpty());
            recent = page.recent();
            out = page.rows();
            if (page.catalogue()) {
                out.addAll(SearchRowSources.hits(tab, result, blank));
            }
        }
        rows = out;
        layoutRows(out);
    }

    /** Every row's top (headers, rows and the More button) and the right column's width. */
    private void layoutRows(List<SearchRow> out) {
        tops = new int[out.size() + 1];
        int y = 0;
        rightW = 0;
        for (int i = 0; i < out.size(); i++) {
            tops[i] = y;
            SearchRow r = out.get(i);
            y += r.height();
            if (r.right() != null) {
                rightW = Math.max(rightW, Type.BODY.width(font(), r.right()));
            }
        }
        tops[out.size()] = y;
        total = y;
    }

    /** Whether the list heads with the recent searches now. */
    boolean showsRecent() {
        return recent;
    }

    /** The right column's width: the widest value of the rows listed (0 with none). */
    int rightW() {
        return rightW;
    }

    // ---- drawing ----

    /** Draws the list (or the empty state); returns the chosen row's key, null when none is listed. */
    String draw(GuiGraphics g, int x, int y, int w, int h, SearchQuery.Result result, boolean compact, int mx, int my) {
        build(result, compact);
        if (rows.isEmpty()) {
            SearchNone.draw(this, tab, g, x, y, w, h, result, compact, mx, my);
            return null;
        }
        if (rows != selRows || !Objects.equals(tab.selName, selFor)) {
            selAt = indexOf(tab.selName);           // once per new list or choice, not every frame
            selRows = rows;
            selFor = tab.selName;
        }
        int sel = selAt;
        if (sel < 0 && !compact) {
            sel = next(-1, 1);                      // a stat row is never chosen by itself
        }
        String chosen = sel < 0 ? null : rows.get(sel).key();
        scrollMax = Math.max(0, total - h);
        if (reveal && sel >= 0) {
            // a key moved the choice: keep it in sight
            scroll = Math.min(scroll, tops[sel]);
            scroll = Math.max(scroll, tops[sel] + rows.get(sel).height() - h);
            reveal = false;
        }
        scroll = Math.max(0, Math.min(scrollMax, scroll));
        int cw = scrollMax > 0 ? w - ScrollBar.W - Theme.SP_2 : w;
        g.enableScissor(x - Theme.SP_2, y, x + cw + Theme.SP_2, y + h);
        SearchRow.Frame frame = new SearchRow.Frame(this, compact, y, h, mx, my);
        for (int i = firstAt(scroll); i < rows.size() && tops[i] - scroll < h; i++) {
            rows.get(i).draw(g, frame, x, y + tops[i] - scroll, cw, i == sel);
        }
        g.disableScissor();
        bar.place(x + w - ScrollBar.W, y, h, h, total, scroll);
        bar.draw(g, mx, my);
        return chosen;
    }

    /** The index still loading or missing: the empty page with what to do. */
    void loading(GuiGraphics g, int x, int y, int w, int h, boolean loading, boolean compact, int mx, int my) {
        UiEmpty.draw(g, font(), x, y, w, h, compact, "search", L.t(loading ? "wynnlabs.search.loading" : "wynnlabs.search.no_data_title"),
                loading ? List.of() : List.of(L.t("wynnlabs.search.no_data_1"), L.t("wynnlabs.search.no_data_2")), null, null, mx, my);
    }

    /** "Ingredient · Woodworking", "Chestplate", "Material"; the data's words with a capital. */
    static String kindText(Entry e) {
        String kind = L.t("wynnlabs.search.kind." + e.kind().name().toLowerCase(Locale.ROOT));
        if (e.kind() == Kind.GEAR || e.kind() == Kind.TOME) {
            return e.type() == null ? kind : words(e.type());
        }
        if (e.stars() > 0) {
            kind += " " + "✫".repeat(e.stars());
        }
        return e.professions().isEmpty() ? kind : kind + " · " + words(e.professions().get(0));
    }

    /** An item's name colour on paper: its rarity's, ink for the rest. */
    static int colour(Entry e) {
        if (e.tier() == null) {
            return Theme.INK;
        }
        return HomePart.tierColour(Tier.of(e.tier().toUpperCase(Locale.ROOT)));
    }

    /** "manaSteal" → "Mana steal", "WEAPON_TOME" → "Weapon tome". */
    static String statName(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            char prev = i > 0 ? key.charAt(i - 1) : ' ';
            if (i > 0 && (Character.isUpperCase(c) && !Character.isUpperCase(prev)
                    || Character.isDigit(c) && Character.isLetter(prev))) {
                b.append(' ');              // "raw1stSpellCost" → "Raw 1st spell cost"
            }
            b.append(Character.toLowerCase(c));
        }
        return words(b.toString());
    }

    static String words(String s) {
        String t = s.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    // ---- keys and wheel ----

    /** Moves the choice by {@code delta} pickable rows (titles and stats skipped). */
    void move(int delta) {
        int at = indexOf(tab.selName);
        int step = delta < 0 ? -1 : 1;
        int to = at;
        for (int n = 0; n < Math.abs(delta); n++) {
            int nx = next(to, step);
            if (nx < 0) {
                break;
            }
            to = nx;
        }
        if (to >= 0 && to != at) {
            tab.selName = rows.get(to).key();
            reveal = true;
        }
    }

    /** A press on the list's bar: the thumb starts a drag, the track pages; true when it took it. */
    boolean barPress(double mx, double my) {
        if (bar.mouseClicked(mx, my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    /** The bar's thumb dragged; true while it is. */
    boolean barDrag(double my) {
        if (bar.mouseDragged(my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    void barRelease() {
        bar.mouseReleased();
    }

    void scrolled(double v) {
        scroll = Math.max(0, Math.min(scrollMax, scroll - ROW * (int) Math.signum(v)));
    }

    private int firstAt(int offset) {
        int lo = 0;
        int hi = rows.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (tops[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return Math.max(0, lo);
    }

    /** The pickable row with this key, or -1. */
    private int indexOf(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).pickable() && key.equals(rows.get(i).key())) {
                return i;
            }
        }
        return -1;
    }

    /** The next pickable row after {@code from} in direction {@code step}, or -1. */
    private int next(int from, int step) {
        for (int i = from + step; i >= 0 && i < rows.size(); i += step) {
            if (rows.get(i).pickable()) {
                return i;
            }
        }
        return -1;
    }
}
