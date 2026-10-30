package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A finished quest takes only its own points off the map; the pins inbox and other quests' points stay. */
class WaypointsForgetTest {

    @Test
    void onlyTheFinishedQuestsPointsGo() {
        List<Waypoints.Mine> mine = new ArrayList<>(List.of(
                new Waypoints.Mine("quest", "1 Talk to Tol", 10, 60, 10, "The Olmic Rune"),
                new Waypoints.Mine("next", "2 The rune altar", 20, 60, 20, "The Olmic Rune"),
                new Waypoints.Mine("next", "3 Lost Royalty start", 30, 60, 30, "Lost Royalty"),
                new Waypoints.Mine("next", "Checkpoint", 40, 60, 40),             // the pins inbox: no quest
                new Waypoints.Mine("keep", "Home", 50, 60, 50),
                new Waypoints.Mine("go", "The Olmic Rune", 60, 60, 60)));         // "go there": no quest
        List<Waypoints.Mine> gone = Waypoints.forget(mine, "the olmic rune");
        assertEquals(2, gone.size());
        assertEquals(List.of("3 Lost Royalty start", "Checkpoint", "Home", "The Olmic Rune"),
                mine.stream().map(Waypoints.Mine::name).toList());
    }

    @Test
    void namesWithApostrophesMatchAndPinsWithoutAQuestNeverGo() {
        List<Waypoints.Mine> mine = new ArrayList<>(List.of(
                new Waypoints.Mine("quest", "Start", 1, 2, 3, "Pirate's Trove"),
                new Waypoints.Mine("quest", "Inbox point", 4, 5, 6)));
        assertEquals(1, Waypoints.forget(mine, "Pirate’s Trove").size());
        assertEquals(0, Waypoints.forget(mine, "").size());
        assertEquals(List.of("Inbox point"), mine.stream().map(Waypoints.Mine::name).toList());
    }
}
