package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.search.EmptyPage;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.Hit;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemMatch;
import io.github.anilken.wynnlabs.search.SearchCategory;
import io.github.anilken.wynnlabs.search.SearchQuery;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceRank;
import io.github.anilken.wynnlabs.search.SourceText;
import io.github.anilken.wynnlabs.search.UseList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the search list's rows come from, each a list of {@link SearchRow}: the Stats category's
 * list of stats, the uses of an item, the empty page's groups (the recent searches, the road
 * map's missing pieces, "More ↓") and the query's hits. The list stacks what they return.
 */
final class SearchRowSources {

    private SearchRowSources() {}

    /** The empty page's rows; {@code recent}: it heads with the recent searches; {@code catalogue}: the hits go under it. */
    record Page(List<SearchRow> rows, boolean recent, boolean catalogue) {}

    /** The Stats category with no stat chosen: every stat and how many items roll it. */
    static List<SearchRow> stats(List<ItemMatch.StatCount> stats) {
        List<SearchRow> out = new ArrayList<>();
        out.add(head(L.t("wynnlabs.search.cat.stat"), String.valueOf(stats.size())));
        for (ItemMatch.StatCount s : stats) {
            out.add(new SearchRow.StatRow(s.key(), SearchResultsPanel.statName(s.key()), L.n("wynnlabs.search.stat_items", s.items())));
        }
        return out;
    }

    /**
     * The uses of an item: "Crafted into" with a row a craft, an ingredient's professions, then
     * "Other uses" (feeding a mount), each group under its title with its count.
     */
    static List<SearchRow> uses(String item) {
        List<UseList.Line> lines = UseStock.lines(item);
        List<SearchRow> out = new ArrayList<>();
        UseList.Kind group = null;
        for (UseList.Line l : lines) {
            if (l.kind() != group) {
                group = l.kind();
                String title = switch (group) {
                    case CRAFT -> "wynnlabs.search.uses_crafted";
                    case PROFESSION -> "wynnlabs.search.uses_ingredient";
                    case FEED -> "wynnlabs.search.uses_other";
                };
                out.add(head(L.t(title), String.valueOf(UseList.count(lines, group))));
            }
            out.add(UseCard.row(item, l));
        }
        return out;
    }

    /**
     * The empty query on "All": the last searches (or, before any, the road map's missing pieces)
     * and "More ↓"; opened, the missing pieces and the whole catalogue under them. Any other query
     * has none of these groups: only the catalogue.
     */
    static Page emptyPage(SearchTab tab, ItemIndex index, boolean blank, List<String> missing, List<String> recent, boolean hits) {
        boolean home = blank && tab.filtersOn() == 0 && tab.stat == null && tab.category == SearchCategory.ALL;
        boolean catalogue = blank && tab.filtersOn() == 0 && tab.stat == null
                && (tab.category == SearchCategory.ALL || tab.category == SearchCategory.ITEM);
        List<SearchRow> mine = new ArrayList<>();
        List<SearchRow> rs = new ArrayList<>();
        if (catalogue) {
            for (String n : missing) {
                Entry e = index.byName(n);
                if (e != null) {
                    mine.add(entry(tab, e, null, false));
                }
            }
            for (String n : recent) {
                // a recent search is listed only while its key is an entry's: one of another kind of row
                // (no entry behind it) is left out here until it has a row to show
                Entry e = index.byKey(n);
                if (e != null) {
                    rs.add(entry(tab, e, null, true));
                }
            }
        }
        EmptyPage.Plan plan = EmptyPage.plan(home, catalogue, rs.size(), mine.size(), SearchTab.expanded, hits);
        List<SearchRow> out = new ArrayList<>();
        if (plan.first() == EmptyPage.Group.RECENT) {
            out.add(new SearchRow.Header(L.t("wynnlabs.search.recent"), String.valueOf(plan.firstCount()), true));
            out.addAll(rs.subList(0, plan.firstCount()));
        } else if (plan.first() == EmptyPage.Group.MISSING) {
            // a new player: the road map's missing pieces start the page instead
            out.add(head(L.t("wynnlabs.search.roadmap_missing"), String.valueOf(mine.size())));
            out.addAll(mine.subList(0, plan.firstCount()));
        }
        if (plan.button() != EmptyPage.Button.NONE) {
            out.add(new SearchRow.MoreRow(plan.button() == EmptyPage.Button.MORE));
        }
        if (plan.missingAfter()) {
            out.add(head(L.t("wynnlabs.search.roadmap_missing"), String.valueOf(mine.size())));
            out.addAll(mine);
        }
        return new Page(out, plan.first() == EmptyPage.Group.RECENT, plan.catalogue());
    }

    /** The query's hits under their title: "Everything", "With Mana steal" or "Results", the count and the sort. */
    static List<SearchRow> hits(SearchTab tab, SearchQuery.Result result, boolean blank) {
        List<SearchRow> out = new ArrayList<>();
        if (!result.hits().isEmpty()) {
            String title = blank ? L.t(tab.stat != null ? "wynnlabs.search.with_stat" : "wynnlabs.search.everything",
                    SearchResultsPanel.statName(tab.stat)) : L.t("wynnlabs.search.results");
            out.add(head(title, result.hits().size() + " · " + L.t(SearchMenus.SORTS[tab.sort.ordinal()]).toLowerCase(Locale.ROOT)));
        }
        for (Hit h : result.hits()) {
            String note = switch (h.match()) {
                case MOB -> L.t("wynnlabs.search.match_mob", h.detail());
                case STAT -> L.t("wynnlabs.search.match_stat", SearchResultsPanel.statName(h.detail()));
                default -> null;
            };
            out.add(entry(tab, h.entry(), note, false));
        }
        return out;
    }

    private static SearchRow head(String title, String meta) {
        return new SearchRow.Header(title, meta, false);
    }

    /** A row of an entry: its META line and its right column, worked out here once. */
    static SearchRow.EntryRow entry(SearchTab tab, Entry e, String note, boolean recent) {
        String meta = note != null ? note : e.item() ? SearchResultsPanel.kindText(e) + (e.level() > 0 ? " · Lv " + e.level() : "")
                : ThingCard.sub(e);
        String right;
        int ink;
        Integer v = SearchQuery.statValue(e, tab.stat);
        if (v != null) {
            right = (v > 0 ? "+" : "") + v;
            ink = v < 0 ? Theme.BAD : Theme.GOOD;
        } else if (!e.item()) {
            right = ThingCard.hint(e, SearchTab.position());
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
        return new SearchRow.EntryRow(e, meta, right, ink, recent);
    }
}
