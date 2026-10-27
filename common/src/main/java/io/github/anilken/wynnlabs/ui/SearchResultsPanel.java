package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.Hit;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemMatch;
import io.github.anilken.wynnlabs.search.RecentSearches;
import io.github.anilken.wynnlabs.search.SearchCategory;
import io.github.anilken.wynnlabs.search.SearchQuery;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceRank;
import io.github.anilken.wynnlabs.search.SourceText;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The search tab's list. Before anything is typed it is the whole catalogue: the road map's
 * missing pieces first (and the recent searches on a small window), then everything, in the
 * sort's order; typing, the category strip and the filters narrow it. A row: the item's own icon
 * in an HD slot (a thing's badge), its name in its rarity's colour, a META line (kind, level, why
 * it matched), and on the right the easiest way to get it, how far a thing is, "Yours", or the
 * chosen stat's roll. The Stats category with no stat chosen lists the stats. Nothing found: the
 * closest name ("did you mean") and a way out of the filters. Rows are built once per answer.
 */
final class SearchResultsPanel extends MenuPaint {

    /** A row's pitch: a name and a META line, SP_2 inside the wash above and below, SP_2 of page between washes. */
    static final int ROW = Type.BODY.lineHeight + Type.META.lineHeight + 2 * Theme.SP_2 + Theme.SP_2;
    /** A group's title: SP_3 above and below a LABEL line with its hairline. */
    static final int HEADER = Type.LABEL.lineHeight + 2 * Theme.SP_3;
    /** The slot and the badge of every row, one size: an item at 1x in it, Wynntils' icons at 1x with room. */
    private static final int SLOT = SearchCardRows.BADGE;

    /** A row: an entry (or a stat), or a group title ({@code e} and {@code stat} null) with its META. */
    record Row(Entry e, String header, String meta, String stat, String right, int rightInk) {
        boolean pickable() {
            return e != null || stat != null;
        }
    }

    private final SearchTab tab;
    int scroll;
    private int scrollMax;
    private List<Row> rows = List.of();
    private int[] tops = new int[0];
    private int total;
    private int rightW;
    private Object builtFor;
    private boolean reveal;
    private final ScrollBar bar = new ScrollBar();
    private List<ItemMatch.StatCount> stats;
    private List<Row> selRows;
    private String selFor;
    private int selAt = -1;
    private ItemIndex statsFor;

    SearchResultsPanel(SearchTab tab) {
        super(tab.menu);
        this.tab = tab;
    }

    // ---- the rows, once per answer ----

    private void build(SearchQuery.Result result, boolean compact) {
        ItemIndex index = ItemIndex.get();
        List<String> missing = tab.missingPieces();
        List<String> recent = compact ? RecentSearches.get().names() : List.of();
        boolean blank = tab.box.getValue().isBlank();
        Object key = List.of(System.identityHashCode(result), compact, missing, recent, tab.category, String.valueOf(tab.stat));
        if (key.equals(builtFor)) {
            return;
        }
        builtFor = key;
        List<Row> out = new ArrayList<>();
        if (blank && tab.category == SearchCategory.STAT && tab.stat == null) {
            if (stats == null || statsFor != index) {
                stats = ItemMatch.stats(index);
                statsFor = index;
            }
            out.add(head(L.t("wynnlabs.search.cat.stat"), String.valueOf(stats.size())));
            for (ItemMatch.StatCount s : stats) {
                out.add(new Row(null, statName(s.key()), L.n("wynnlabs.search.stat_items", s.items()), s.key(), null, 0));
            }
        } else {
            boolean catalogue = blank && tab.filtersOn() == 0 && tab.stat == null
                    && (tab.category == SearchCategory.ALL || tab.category == SearchCategory.ITEM);
            if (catalogue && !recent.isEmpty()) {
                out.add(head(L.t("wynnlabs.search.recent"), String.valueOf(recent.size())));
                for (String n : recent) {
                    Entry e = index.byKey(n);
                    if (e != null) {
                        out.add(row(e, null));
                    }
                }
            }
            if (catalogue && !missing.isEmpty()) {
                List<Row> mine = new ArrayList<>();
                for (String n : missing) {
                    Entry e = index.byName(n);
                    if (e != null) {
                        mine.add(row(e, null));
                    }
                }
                if (!mine.isEmpty()) {
                    out.add(head(L.t("wynnlabs.search.roadmap_missing"), String.valueOf(mine.size())));
                    out.addAll(mine);
                }
            }
            if (!result.hits().isEmpty()) {
                String title = blank ? L.t(tab.stat != null ? "wynnlabs.search.with_stat" : "wynnlabs.search.everything",
                        statName(tab.stat)) : L.t("wynnlabs.search.results");
                out.add(head(title, result.hits().size() + " · " + L.t(SearchMenus.SORTS[tab.sort.ordinal()]).toLowerCase(Locale.ROOT)));
            }
            for (Hit h : result.hits()) {
                String note = switch (h.match()) {
                    case MOB -> L.t("wynnlabs.search.match_mob", h.detail());
                    case STAT -> L.t("wynnlabs.search.match_stat", statName(h.detail()));
                    default -> null;
                };
                out.add(row(h.entry(), note));
            }
        }
        rows = out;
        tops = new int[out.size() + 1];
        int y = 0;
        rightW = 0;
        for (int i = 0; i < out.size(); i++) {
            tops[i] = y;
            Row r = out.get(i);
            y += r.pickable() ? ROW : HEADER;
            if (r.right() != null) {
                rightW = Math.max(rightW, Type.BODY.width(font(), r.right()));
            }
        }
        tops[out.size()] = y;
        total = y;
    }

    private static Row head(String title, String meta) {
        return new Row(null, title, meta, null, null, 0);
    }

    /** A row of an entry: its META line and its right column, worked out here once. */
    private Row row(Entry e, String note) {
        String meta = note != null ? note : e.item() ? kindText(e) + (e.level() > 0 ? " · Lv " + e.level() : "")
                : SearchThingCard.sub(e);
        String right;
        int ink;
        Integer v = SearchQuery.statValue(e, tab.stat);
        if (v != null) {
            right = (v > 0 ? "+" : "") + v;
            ink = v < 0 ? Theme.BAD : Theme.GOOD;
        } else if (!e.item()) {
            right = SearchThingCard.hint(e, SearchTab.position());
            ink = Theme.INK_2;
        } else if (tab.ownedNames.contains(e.name().toLowerCase(Locale.ROOT))) {
            right = L.t("wynnlabs.search.owned");
            ink = Theme.GOOD;
        } else {
            Prices.Price price = e.tradable() ? Prices.of(e.name()) : null;
            Source.Type easy = SourceRank.easiest(e, price != null && price.typical() != null);
            right = SourceText.brief(easy, price, e.level());
            ink = easy == Source.Type.MARKET ? Theme.PRICE : Theme.INK_2;
        }
        return new Row(e, null, meta, null, right, ink);
    }

    // ---- drawing ----

    /** Draws the list (or the empty state); returns the chosen entry's key, null when none is listed. */
    String draw(GuiGraphics g, int x, int y, int w, int h, SearchQuery.Result result, boolean compact, int mx, int my) {
        build(result, compact);
        if (rows.isEmpty()) {
            SearchNone.draw(this, tab, g, x, y, w, h, result, compact, mx, my);
            return null;
        }
        if (rows != selRows || !java.util.Objects.equals(tab.selName, selFor)) {
            selAt = indexOf(tab.selName);           // once per new list or choice, not every frame
            selRows = rows;
            selFor = tab.selName;
        }
        int sel = selAt;
        if (sel < 0 && !compact) {
            sel = next(-1, 1);
            while (sel >= 0 && rows.get(sel).e() == null) {
                sel = next(sel, 1);                 // a stat row is never chosen by itself
            }
        }
        String chosen = sel < 0 ? null : rows.get(sel).e().key();
        scrollMax = Math.max(0, total - h);
        if (reveal && sel >= 0) {
            // a key moved the choice: keep it in sight
            scroll = Math.min(scroll, tops[sel]);
            scroll = Math.max(scroll, tops[sel] + ROW - h);
            reveal = false;
        }
        scroll = Math.max(0, Math.min(scrollMax, scroll));
        int cw = scrollMax > 0 ? w - ScrollBar.W - Theme.SP_2 : w;
        g.enableScissor(x - Theme.SP_2, y, x + cw + Theme.SP_2, y + h);
        int first = firstAt(scroll);
        for (int i = first; i < rows.size() && tops[i] - scroll < h; i++) {
            Row r = rows.get(i);
            int ry = y + tops[i] - scroll;
            if (!r.pickable()) {
                header(g, r, x, ry, cw);
            } else {
                drawRow(g, r, x, ry, cw, i == sel, compact, y, h, mx, my);
            }
        }
        g.disableScissor();
        bar.place(x + w - ScrollBar.W, y, h, h, total, scroll);
        bar.draw(g, mx, my);
        return chosen;
    }

    private void header(GuiGraphics g, Row r, int x, int y, int w) {
        int ty = y + Theme.SP_3;
        Type.LABEL.draw(g, font(), r.header(), x, ty, Theme.INK_2);
        int mx = x + Type.LABEL.width(font(), r.header()) + Theme.SP_3;
        String meta = Type.META.fit(font(), r.meta(), Math.max(0, x + w - mx - Theme.SP_6));
        Type.META.draw(g, font(), meta, mx, ty + 1, Theme.INK_3);
        int lx = mx + Type.META.width(font(), meta) + Theme.SP_3;
        if (lx < x + w) {
            Kit.hair(g, lx, Type.LABEL.onCaps(ty, 1) + 2, x + w - lx, 1, Theme.LINE);
        }
    }

    /** One row at y (its pitch's top): the wash SP_2 around the text, the slot on the text block. */
    void drawRow(GuiGraphics g, Row r, int x, int y, int w, boolean chosen, boolean compact, int top, int h, int mx, int my) {
        int ty = y + Theme.SP_2;
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        boolean hover = mx >= x && mx < x + w && my >= Math.max(y, top) && my < Math.min(y + ROW, top + h);
        if (chosen) {
            Kit.rowLit(g, x - Theme.SP_2, y, w + 2 * Theme.SP_2, textH + 2 * Theme.SP_2);
        }
        int sy = ty + (textH - SLOT) / 2;
        Entry e = r.e();
        if (e == null) {
            Kit.slot(g, x, sy, SLOT, 0);
            Art.icon(g, "bolt", x + Ui.iconIn(SLOT, 8), sy + Ui.iconIn(SLOT, 8), 8, Theme.INK_2);
        } else if (e.item()) {
            Kit.slot(g, x, sy, SLOT, 0);
            g.renderItem(tab.icons.of(e), x + (SLOT - 16) / 2, sy + (SLOT - 16) / 2);
            if (hover && mx < x + SLOT) {
                menu.hits.add(new MenuHit(x, sy, SLOT, SLOT, tab.icons.of(e), null, null));
            }
        } else {
            SearchThingCard.picture(g, e, x, sy, SLOT);
        }
        int tx = x + SLOT + Theme.SP_4;
        int right = x + w - (rightW > 0 ? rightW + Theme.SP_5 : 0);
        String name = e == null ? r.header() : e.name();
        int ink = hover && !chosen ? Theme.LINK : e == null ? Theme.INK : colour(e);
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), name, right - tx), tx, ty, ink);
        Type.META.draw(g, font(), Type.META.fit(font(), r.meta(), right - tx), tx, ty + Type.BODY.lineHeight, Theme.INK_3);
        if (r.right() != null) {
            Type.BODY.drawRight(g, font(), r.right(), x + w, ty, r.rightInk());
        }
        int cy = Math.max(y, top);
        int ch = Math.min(y + ROW, top + h) - cy;
        if (ch > 0) {
            tab.click(x, cy, w, ch, () -> pick(r, compact));
        }
    }

    /** A row clicked: a stat lists its items; an entry is chosen (and, small, its card opens). */
    void pick(Row r, boolean compact) {
        if (r.e() == null) {
            tab.pickStat(r.stat());
            return;
        }
        tab.selName = r.e().key();
        RecentSearches.get().add(r.e().key());
        if (compact) {
            tab.openDrawer(r.e().item() ? kindText(r.e()) : SearchThingCard.typeText(r.e()));
        }
    }

    /** The index still loading or missing: the empty page with what to do. */
    void loading(GuiGraphics g, int x, int y, int w, int h, boolean loading, boolean compact, int mx, int my) {
        UiEmpty.draw(g, font(), x, y, w, h, compact, "search", L.t(loading ? "wynnlabs.search.loading" : "wynnlabs.search.no_data_title"),
                loading ? List.of() : List.of(L.t("wynnlabs.search.no_data_1"), L.t("wynnlabs.search.no_data_2")), null, null, mx, my);
    }

    /** "Ingredient · Woodworking", "Chestplate", "Material"; the data's words with a capital. */
    static String kindText(Entry e) {
        String kind = L.t("wynnlabs.search.kind." + e.kind().name().toLowerCase(Locale.ROOT));
        if (e.kind() == io.github.anilken.wynnlabs.search.Kind.GEAR || e.kind() == io.github.anilken.wynnlabs.search.Kind.TOME) {
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

    /** Moves the choice by {@code delta} entry rows (titles and stats skipped). */
    void move(int delta) {
        int at = indexOf(tab.selName);
        int step = delta < 0 ? -1 : 1;
        int to = at;
        for (int n = 0; n < Math.abs(delta); n++) {
            int nx = next(to, step);
            while (nx >= 0 && rows.get(nx).e() == null) {
                nx = next(nx, step);
            }
            if (nx < 0) {
                break;
            }
            to = nx;
        }
        if (to >= 0 && to != at) {
            tab.selName = rows.get(to).e().key();
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

    private int indexOf(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).e() != null && rows.get(i).e().key().equals(key)) {
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
