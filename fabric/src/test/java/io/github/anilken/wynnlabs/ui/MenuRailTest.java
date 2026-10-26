package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MenuRailTest {

    @Test
    void todayIsOnePageWithoutSections() {
        assertArrayEquals(new int[] {1}, MenuRail.RAIL[MenuRail.entry(1)]);
    }

    @Test
    void theTreeIsOnePageAndTheDamageAndDeathsAreNotRailEntries() {
        int tree = MenuRail.entry(RoadmapScreen.TAB_TREE);
        assertArrayEquals(new int[] {RoadmapScreen.TAB_TREE}, MenuRail.RAIL[tree]);
        for (int[] e : MenuRail.RAIL) {
            for (int t : e) {
                assertNotEquals(RoadmapScreen.TAB_DAMAGE, t, "the damage is a drawer of the tree page");
                assertNotEquals(3, t, "the deaths are a drawer of the tree page");
            }
        }
    }

    @Test
    void theNumberKeysStillOpenTheEntriesFirstTab() {
        assertEquals(1, MenuRail.RAIL[1][0]);
        assertEquals(RoadmapScreen.TAB_TREE, MenuRail.RAIL[3][0]);
    }
}
