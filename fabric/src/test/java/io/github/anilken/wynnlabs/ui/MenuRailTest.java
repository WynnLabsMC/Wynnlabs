package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MenuRailTest {

    @Test
    void todayIsOnePageWithoutSections() {
        assertArrayEquals(new int[] {1}, MenuRail.RAIL[MenuRail.entry(1)]);
    }

    @Test
    void damageAndDeathsAreSectionsOfTheTreeInThatOrder() {
        int tree = MenuRail.entry(RoadmapScreen.TAB_TREE);
        assertArrayEquals(new int[] {RoadmapScreen.TAB_TREE, RoadmapScreen.TAB_DAMAGE, 3}, MenuRail.RAIL[tree]);
        assertEquals(tree, MenuRail.entry(RoadmapScreen.TAB_DAMAGE), "a link to Hasar lands under the tree");
        assertEquals(tree, MenuRail.entry(3), "a link to Ölümler lands under the tree");
    }

    @Test
    void theNumberKeysStillOpenTheEntriesFirstTab() {
        assertEquals(1, MenuRail.RAIL[1][0]);
        assertEquals(RoadmapScreen.TAB_TREE, MenuRail.RAIL[3][0]);
    }
}
