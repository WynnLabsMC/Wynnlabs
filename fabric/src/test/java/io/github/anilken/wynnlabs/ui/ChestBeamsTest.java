package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.search.Area;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which loot chests get a beam: tier filter, range, nearest first and the cap. */
class ChestBeamsTest {

    private static Area chest(int x, int stars) {
        return new Area(x, 64, 0, 0, -1, stars, null);
    }

    @Test
    void allIncludesUnknownTierInGrey() {
        List<ChestBeams.Beam> beams = ChestBeams.pick(List.of(chest(10, 0), chest(5, 3)), 0, 64, 0, 0, 60);
        assertEquals(2, beams.size());
        assertEquals(5, beams.get(0).x(), "nearest first");
        assertEquals(0xFF55FF, beams.get(0).colour(), "three stars are purple");
        assertEquals(0xAAAAAA, beams.get(1).colour(), "unknown tier is grey");
    }

    @Test
    void minimumTierDropsLowerAndUnknownChests() {
        List<Area> all = List.of(chest(1, 0), chest(2, 1), chest(3, 2), chest(4, 3), chest(5, 4));
        assertEquals(4, ChestBeams.pick(all, 0, 64, 0, 1, 60).size());
        List<ChestBeams.Beam> two = ChestBeams.pick(all, 0, 64, 0, 2, 60);
        assertEquals(3, two.size());
        assertEquals(0xFFFF55, two.get(0).colour(), "two stars are yellow");
        assertEquals(2, ChestBeams.pick(all, 0, 64, 0, 3, 60).size());
    }

    @Test
    void onlyTheNearestWithinRangeUpToTheCap() {
        List<Area> all = new ArrayList<>();
        for (int i = 100; i > 0; i--) {
            all.add(chest(i, 2));
        }
        all.add(chest((int) ChestBeams.RANGE + 50, 4));      // out of range
        List<ChestBeams.Beam> beams = ChestBeams.pick(all, 0, 64, 0, 0, ChestBeams.CAP);
        assertEquals(ChestBeams.CAP, beams.size());
        assertEquals(1, beams.get(0).x());
        assertEquals(ChestBeams.CAP, beams.get(beams.size() - 1).x());
        assertTrue(ChestBeams.pick(List.of(chest((int) ChestBeams.RANGE + 50, 4)), 0, 64, 0, 0, 60).isEmpty());
    }

    @Test
    void fadesOnlyNearTheEdgeOfTheRange() {
        assertEquals(1f, ChestBeams.fade(50), 1e-6);
        assertEquals(0f, ChestBeams.fade(ChestBeams.RANGE), 1e-6);
        assertTrue(ChestBeams.fade(ChestBeams.RANGE * 0.95) > 0);
    }
}
