package io.github.anilken.wynnlabs.search;

import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The search tab's categories, the stat filter, "did you mean" and the marks the player keeps on the map. */
class SearchRedesignTest {

    private static final SearchQuery.Facts FACTS = new SearchQuery.Facts(Klass.MAGE, n -> false, n -> null);

    @Test
    void categoriesGroupTheKindsAndSumTheirCounts() {
        Map<Kind, Integer> counts = Map.of(Kind.GEAR, 15, Kind.INGREDIENT, 4, Kind.MOB, 9, Kind.PLACE, 7, Kind.DISCOVERY, 3,
                Kind.NPC, 2, Kind.SERVICE, 1);
        assertEquals(41, SearchCategory.ALL.count(counts));
        assertEquals(19, SearchCategory.ITEM.count(counts));
        assertEquals(9, SearchCategory.MOB.count(counts));
        assertEquals(10, SearchCategory.PLACE.count(counts), "places, content and discoveries");
        assertEquals(3, SearchCategory.SERVICE.count(counts), "services, resources and NPCs");
        assertEquals(Set.of(), SearchCategory.ALL.query(Set.of()), "all: no kind filter");
        assertEquals(Set.of(Kind.INGREDIENT), SearchCategory.ITEM.query(EnumSet.of(Kind.INGREDIENT, Kind.MOB)),
                "a narrowed group keeps only its own kinds");
        assertEquals(SearchCategory.PLACE.kinds(), SearchCategory.PLACE.query(Set.of(Kind.GEAR)), "nothing of its own: the whole group");
        assertTrue(SearchCategory.ITEM.narrows());
        assertFalse(SearchCategory.MOB.narrows());
    }

    @Test
    void aStatListsOnlyItsItemsHighestRollFirst() {
        ItemIndex idx = SearchFixtures.index();
        SearchQuery q = new SearchQuery("", SearchQuery.Sort.EASIEST, Set.of(), 0, Integer.MAX_VALUE, false, false, false, "manaSteal");
        SearchQuery.Result r = q.run(idx, FACTS);
        assertFalse(r.hits().isEmpty());
        int last = Integer.MAX_VALUE;
        for (Hit h : r.hits()) {
            Integer v = SearchQuery.statValue(h.entry(), "manaSteal");
            assertNotNull(v, h.entry().name() + " rolls mana steal");
            assertTrue(v <= last, "highest first");
            last = v;
        }
        List<ItemMatch.StatCount> stats = ItemMatch.stats(idx);
        assertTrue(stats.stream().anyMatch(s -> s.key().equals("manaSteal") && s.items() == r.hits().size()),
                "the stat list counts the same items");
        assertTrue(stats.get(0).items() >= stats.get(stats.size() - 1).items(), "most items first");
    }

    @Test
    void luckyRabFindsTheFootAndItsDropper() {
        SearchQuery.Result r = SearchQuery.of("lucky rab").run(SearchFixtures.index(), FACTS);
        assertEquals("Lucky Rabbit's Foot", r.hits().get(0).entry().name());
        Entry dropper = SearchFixtures.index().byKey("mob:Tumbleweed");
        assertNotNull(dropper, "its dropper is a mob of its own");
        assertTrue(dropper.links(Entry.ITEM).stream().anyMatch(o -> o.name().equals("Lucky Rabbit's Foot")));
    }

    @Test
    void didYouMeanNamesTheNearestEntryOrWhatTheFiltersHid() {
        ItemIndex idx = SearchFixtures.index();
        SearchQuery.Result typo = SearchQuery.of("luckyrabit").run(idx, FACTS);
        assertTrue(typo.hits().isEmpty(), "no word matches it");
        assertNotNull(typo.suggestion());
        assertEquals("Lucky Rabbit's Foot", typo.suggestion().entry().name());
        assertFalse(typo.filtered());
        SearchQuery.Result far = SearchQuery.of("qqqqzzzz").run(idx, FACTS);
        assertTrue(far.hits().isEmpty());
        assertNull(far.suggestion(), "nothing close: no suggestion");
        SearchQuery hidden = new SearchQuery("lucky rab", SearchQuery.Sort.EASIEST, Set.of(), 1, 20, false, false, false);
        SearchQuery.Result r = hidden.run(idx, FACTS);
        assertTrue(r.hits().isEmpty(), "level 37 is outside 1–20");
        assertEquals("Lucky Rabbit's Foot", r.suggestion().entry().name());
        assertTrue(r.filtered(), "the filters hid it");
        assertNull(SearchQuery.of("").run(idx, FACTS).suggestion(), "an empty box suggests nothing");
    }

    @Test
    void closestNeedsThreeLettersAndAFewEditsAtMost() {
        ItemIndex idx = SearchFixtures.index();
        assertNull(ItemMatch.closest(idx, "pa"));
        Hit h = ItemMatch.closest(idx, "papyrsu");
        assertNotNull(h);
        assertEquals("Papyrus", h.entry().name());
    }

    private static final Source MOB = new Source(Source.Type.MOB, "Reggie",
            List.of(new Area(10, 60, 20, 25, 70, 0, null), new Area(-300, 60, 40, 15, 70, 0, "Nemract Plains")),
            1, "sword", 0, null, 0, 0, null);
    private static final Source SHOP = new Source(Source.Type.MERCHANT, "Detlas", List.of(Area.point(400, 70, -1500)),
            1, "shopping-cart", 0, null, 0, 0, null);
    private static final Source CHESTS = new Source(Source.Type.LOOT_CHEST, "T3",
            List.of(Area.point(1, 60, 1), Area.point(2, 60, 2), Area.point(3, 60, 3)), 1, "box", 0, null, 0, 0, null);
    private static final List<Source> ROWS = List.of(MOB, SHOP, CHESTS);

    private static int count(MarkSet m, int picked) {
        return m.marks(ROWS, picked, Source::name).size();
    }

    @Test
    void thePickedRowShowsAndOnlyAskedMarksAreKept() {
        MarkSet m = new MarkSet();
        assertEquals(2, count(m, 0), "the picked row's two areas");
        assertFalse(m.kept(), "the picked row alone leaves with the menu");
        m.toggle(2, ROWS.size());
        assertEquals(5, count(m, 0), "the chests added");
        assertTrue(m.marked(2));
        assertTrue(m.kept(), "asked for: kept");
        m.toggle(2, ROWS.size());
        assertFalse(m.kept());
        m.toggleAll();
        assertEquals(6, count(m, 0), "every row");
        assertTrue(m.all() && m.kept());
        m.toggle(1, ROWS.size());
        assertFalse(m.all());
        assertTrue(m.marked(0) && !m.marked(1) && m.marked(2), "one row off of all");
        m.toggleAll();
        m.toggleAll();
        assertFalse(m.all());
        assertEquals(2, count(m, 0), "all off: the picked row again");
    }

    @Test
    void singleMarksComeOffAndRemoveAllClearsUntilAPick() {
        MarkSet m = new MarkSet();
        m.toggleAll();
        List<MapMark> on = m.marks(ROWS, 0, Source::name);
        m.remove(on.get(3));
        List<MapMark> after = m.marks(ROWS, 0, Source::name);
        assertEquals(on.size() - 1, after.size());
        assertFalse(after.contains(on.get(3)));
        m.clear();
        assertEquals(0, count(m, 0), "everything off, the picked row too");
        assertFalse(m.kept());
        m.picked();
        assertEquals(2, count(m, 0), "a pick shows its row again");
        m.toggleAll();
        m.reset();
        assertFalse(m.all() || m.kept());
        assertEquals(1, count(m, 1), "a new card: its picked row only");
        m.keep();
        assertTrue(m.kept(), "open on map keeps them");
    }
}
