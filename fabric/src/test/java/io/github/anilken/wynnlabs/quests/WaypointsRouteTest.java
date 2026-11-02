package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A route shows only the nearby spots, or the nearest one; standing at a spot marks it. */
class WaypointsRouteTest {

    private static Waypoints.Mine at(int x, int z) {
        return new Waypoints.Mine("route", "Messenger", x, 70, z);
    }

    @Test
    void nearbySpotsOnlyNearestFirstAndCapped() {
        List<Waypoints.Mine> route = List.of(at(50, 0), at(10, 0), at(30, 0), at(500, 0), at(70, 0), at(20, 20), at(60, 10), at(75, 0));
        List<Waypoints.Mine> shown = Waypoints.routeShown(route, 0, 0, 80, 6);
        assertEquals(6, shown.size());
        assertEquals(10, shown.get(0).x());
        assertTrue(shown.stream().noneMatch(m -> m.x() == 500));
        assertTrue(shown.stream().noneMatch(m -> m.x() == 75));      // the seventh nearest: over the cap
    }

    @Test
    void nothingNearShowsTheNearestOne() {
        List<Waypoints.Mine> route = List.of(at(500, 0), at(300, 0), at(900, 0));
        assertEquals(List.of(at(300, 0)), Waypoints.routeShown(route, 0, 0, 80, 6));
    }

    @Test
    void standingAtASpotCountsFlatAndNotFromFarBelow() {
        List<Waypoints.Mine> route = List.of(at(0, 0), at(100, 0));
        Set<Long> near = Waypoints.routeNear(route, 3, 70, 3, 6);
        assertEquals(Set.of(Waypoints.key(at(0, 0))), near);
        assertTrue(Waypoints.routeNear(route, 3, 40, 3, 6).isEmpty());    // 30 blocks below: a cave under it
        assertEquals(1, Waypoints.routeNear(route, 3, 90, 3, 6).size());  // flying over it on a mount
    }
}
