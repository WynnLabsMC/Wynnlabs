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

    private static MarkSet card(KeptMarks kept) {
        MarkSet m = new MarkSet(kept);
        m.reset(ROWS, Source::name);
        return m;
    }

    @Test
    void thePickedRowShowsAndOnlyAskedMarksAreKept() {
        KeptMarks kept = KeptMarks.inMemory();
        MarkSet m = card(kept);
        assertEquals(2, m.marks(0).size(), "the picked row's two areas");
        assertFalse(m.kept(), "the picked row alone is not kept");
        assertEquals(0, m.closed().size(), "the menu closed: the picked row's marks go");
        m.toggle(2);
        assertEquals(5, m.marks(0).size(), "the chests added");
        assertTrue(m.marked(2) && m.kept());
        assertEquals(3, m.closed().size(), "the menu closed: the chests stay");
        m.toggle(2);
        assertFalse(m.kept());
        assertEquals(6, m.toggleAll(), "every row's places kept");
        assertTrue(m.all());
        m.toggle(1);
        assertFalse(m.all());
        assertTrue(m.marked(0) && !m.marked(1) && m.marked(2), "one row off of all");
        m.toggleAll();
        m.toggleAll();
        assertFalse(m.all() || m.kept(), "all again, then off: nothing kept");
        assertEquals(2, m.marks(0).size(), "the picked row again");
    }

    @Test
    void keptMarksOutliveTheCardAndRemoveAllCanBeUndone() {
        KeptMarks kept = KeptMarks.inMemory();
        MarkSet m = card(kept);
        m.toggleAll();
        // another card (or the menu opened again): what was kept is still there
        MarkSet next = new MarkSet(kept);
        next.reset(List.of(SHOP), Source::name);
        assertEquals(6, next.closed().size());
        assertTrue(next.marked(0), "the shop is already marked from the first card");
        List<MapMark> on = next.marks(0);
        next.remove(on.get(3));
        assertEquals(5, kept.all().size(), "one kept mark taken off for good");
        List<MapMark> was = next.clear();
        assertEquals(0, next.marks(0).size(), "everything off, the picked row too");
        next.picked();
        assertEquals(1, next.marks(0).size(), "a pick shows its row again");
        kept.addAll(was);
        assertEquals(5, kept.all().size(), "undo: the marks back");
        next.keep(0);
        assertTrue(next.marked(0), "open on map keeps the picked row");
    }

    @Test
    void keptMarksSurviveARestart(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path f = dir.resolve("map-marks.json");
        KeptMarks kept = KeptMarks.at(f);
        MarkSet m = card(kept);
        m.toggle(2);
        io.github.anilken.wynnlabs.core.Saver.flush();
        KeptMarks again = KeptMarks.at(f);
        assertEquals(3, again.all().size(), "read back after a restart");
        assertEquals(kept.all().get(0).spot(), again.all().get(0).spot());
        assertFalse(again.all().get(0).focus());
    }
}
