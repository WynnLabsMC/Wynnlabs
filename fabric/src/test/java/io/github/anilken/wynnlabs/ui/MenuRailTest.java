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

    @Test
    void theMountsEntryHasThreeSectionsAndOpensOnTheRoadMap() {
        int mounts = MenuRail.entry(RoadmapScreen.TAB_MOUNTS);
        assertArrayEquals(new int[] {RoadmapScreen.TAB_MOUNTS, RoadmapScreen.TAB_MOUNT_ROAD, RoadmapScreen.TAB_MOUNT_PLACES},
                MenuRail.RAIL[mounts]);
        assertEquals(mounts, MenuRail.entry(RoadmapScreen.TAB_MOUNT_PLACES));
        assertEquals(RoadmapScreen.TAB_MOUNT_ROAD, MenuRail.opens(mounts));
        assertEquals(RoadmapScreen.TAB_MOUNT_ROAD, MenuRail.shortcut(org.lwjgl.glfw.GLFW.GLFW_KEY_1 + mounts, 0));
    }
}
