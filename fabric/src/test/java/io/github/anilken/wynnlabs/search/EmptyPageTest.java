package io.github.anilken.wynnlabs.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmptyPageTest {

    @Test
    void closedTheLastFiveSearchesAndMoreOnly() {
        EmptyPage.Plan p = EmptyPage.plan(true, true, 8, 3, false, true);
        assertEquals(EmptyPage.Group.RECENT, p.first());
        assertEquals(5, p.firstCount());
        assertEquals(EmptyPage.Button.MORE, p.button());
        assertFalse(p.missingAfter() || p.catalogue(), "nothing else until More");
    }

    @Test
    void openedTheMissingPiecesAndTheCatalogueUnderLess() {
        EmptyPage.Plan p = EmptyPage.plan(true, true, 2, 3, true, true);
        assertEquals(2, p.firstCount());
        assertEquals(EmptyPage.Button.LESS, p.button());
        assertTrue(p.missingAfter() && p.catalogue());
    }

    @Test
    void noSearchesYetTheMissingPiecesStartThePage() {
        EmptyPage.Plan closed = EmptyPage.plan(true, true, 0, 9, false, true);
        assertEquals(EmptyPage.Group.MISSING, closed.first());
        assertEquals(5, closed.firstCount());
        assertEquals(EmptyPage.Button.MORE, closed.button());
        EmptyPage.Plan open = EmptyPage.plan(true, true, 0, 9, true, true);
        assertEquals(9, open.firstCount(), "opened: every missing piece");
        assertFalse(open.missingAfter(), "not twice");
        assertTrue(open.catalogue());
    }

    @Test
    void anotherCategoryOrNothingToShowListsAsBefore() {
        EmptyPage.Plan items = EmptyPage.plan(false, true, 4, 0, false, true);
        assertEquals(EmptyPage.Button.NONE, items.button(), "Items: the groups, no More button");
        assertTrue(items.catalogue());
        EmptyPage.Plan bare = EmptyPage.plan(true, true, 0, 0, false, true);
        assertEquals(EmptyPage.Group.NONE, bare.first());
        assertTrue(bare.catalogue(), "nothing to head the page: the catalogue, never an empty page");
        EmptyPage.Plan typed = EmptyPage.plan(false, false, 4, 4, false, true);
        assertEquals(EmptyPage.Group.NONE, typed.first());
    }
}
