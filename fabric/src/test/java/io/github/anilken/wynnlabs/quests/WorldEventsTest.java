package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.game.Wyn;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** World events on the main map: the list's places and schedule, and the content book's word on what is on. */
class WorldEventsTest {

    private static final String JSON = """
            [{"name":"Tainted Shoreline","level":24,"difficulty":"EASY",
              "location":[{"event":{"x":603,"y":35,"z":-2283},"radius":20}],"schedule":"2026-11-06T04:43:00+00:00"},
             {"name":"Mole Meet-Up","level":10,"location":[{"event":{"x":-1043,"y":96,"z":-1226},"radius":12}],"schedule":null},
             {"name":"Nowhere","location":[],"schedule":null}]
            """;
    private static final long START = OffsetDateTime.parse("2026-11-06T04:43:00+00:00").toInstant().toEpochMilli();

    @Test
    void parsesPlacesAndSchedule() {
        List<WorldEvents.Event> all = WorldEvents.parse(JSON);
        assertEquals(2, all.size());
        WorldEvents.Event t = all.get(0);
        assertEquals("Tainted Shoreline", t.name());
        assertEquals(603, t.x());
        assertEquals(-2283, t.z());
        assertEquals(20, t.radius());
        assertEquals(START, t.start());
        assertEquals(0, all.get(1).start());
    }

    @Test
    void scheduleAloneShowsEventsAroundTheirStart() {
        List<WorldEvents.Event> all = WorldEvents.parse(JSON);
        List<WorldEvents.Shown> soon = WorldEvents.shown(all, null, START - 4 * 60_000L);
        assertEquals(1, soon.size());
        assertFalse(soon.get(0).active());
        assertTrue(WorldEvents.shown(all, null, START + 60_000L).get(0).active());
        assertTrue(WorldEvents.shown(all, null, START - 60 * 60_000L).isEmpty());
        assertTrue(WorldEvents.shown(all, null, START + 60 * 60_000L).isEmpty());
    }

    @Test
    void theBookDecidesWhatIsOnAndWhen() {
        long now = START - 10 * 60_000L;
        Map<String, Long> book = WorldEvents.fromBook(List.of(
                new Wyn.ContentLine("Tainted Shoreline", "AVAILABLE", 0, "Pirate Bay Foreshore\nEvent starting in 3m 54s"),
                new Wyn.ContentLine("Mole Meet-Up", "UNAVAILABLE", 0, "")), now);
        assertEquals(Map.of("tainted shoreline", now + 234_000L), book);
        List<WorldEvents.Shown> shown = WorldEvents.shown(WorldEvents.parse(JSON), book, now);
        assertEquals(1, shown.size());
        assertEquals(now + 234_000L, shown.get(0).start());
        assertFalse(shown.get(0).active());
    }

    @Test
    void aWaitingBookEventWithNoTimeIsSoonNotActive() {
        long now = START + 60 * 60_000L;            // the schedule's time has passed
        Map<String, Long> book = WorldEvents.fromBook(List.of(
                new Wyn.ContentLine("Tainted Shoreline", "AVAILABLE", 0, "Pirate Bay Foreshore")), now);
        List<WorldEvents.Shown> shown = WorldEvents.shown(WorldEvents.parse(JSON), book, now);
        assertEquals(0, shown.get(0).start());
        assertFalse(shown.get(0).active());
        long early = START - 5 * 60_000L;          // the schedule gives the time
        shown = WorldEvents.shown(WorldEvents.parse(JSON), WorldEvents.fromBook(List.of(
                new Wyn.ContentLine("Tainted Shoreline", "AVAILABLE", 0, "Pirate Bay Foreshore")), early), early);
        assertEquals(START, shown.get(0).start());
    }

    @Test
    void aStartHeardInChatShowsEvenWithoutSchedule() {
        long now = 5_000_000L;
        Map<String, Long> chat = Map.of("mole meet-up", now + 239_000L);
        List<WorldEvents.Shown> shown = WorldEvents.shown(WorldEvents.parse(JSON), null, chat, now);
        assertEquals(1, shown.size());
        assertEquals("Mole Meet-Up", shown.get(0).event().name());
        assertEquals(now + 239_000L, shown.get(0).start());
        assertTrue(WorldEvents.shown(WorldEvents.parse(JSON), null, chat, now + 300_000L).get(0).active());
        assertTrue(WorldEvents.shown(WorldEvents.parse(JSON), null, chat, now + 60 * 60_000L).isEmpty());
    }

    @Test
    void theBookEntrysCountdownGivesTheStart() {
        long now = 7_000_000L;
        WorldEvents.fromLore("§7Mole Meet-Up [World Event]", List.of("Pirate Bay Foreshore", "Event starting in 3m 54s", "text"), now);
        assertEquals(now + 234_000L, WorldEvents.heardFor("Mole Meet-Up"));
    }

    @Test
    void chatNamesWithTheMatchTheList() {
        assertEquals(WorldEvents.key("Slimy Schism"), WorldEvents.key("The Slimy Schism"));
        assertEquals(244, WorldEvents.seconds(null, "4", "4"));
    }

    @Test
    void aCountdownLabelBelongsToTheNearestEvent() {
        List<WorldEvents.Event> all = WorldEvents.parse(JSON);
        assertEquals("Tainted Shoreline", WorldEvents.nearest(all, 610, -2290).name());
        assertEquals(null, WorldEvents.nearest(all, 0, 0));
    }

    @Test
    void aBookEventWithNoTimeIsOnNow() {
        Map<String, Long> book = WorldEvents.fromBook(List.of(new Wyn.ContentLine("Mole Meet-Up", "STARTED", 0, "")), 1000L);
        List<WorldEvents.Shown> shown = WorldEvents.shown(WorldEvents.parse(JSON), book, 1000L);
        assertEquals(1, shown.size());
        assertTrue(shown.get(0).active());
    }
}
