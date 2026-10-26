package io.github.anilken.wynnlabs.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.game.Wyn;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ContentTest {

    private static final String CAVES = """
            [{"type":"cave","name":"Wolf Den","description":"§7The home of the wolves, found around §f[175, 73, -715]",
              "length":"medium","difficulty":"easy","requirements":"43","rewards":["7000 XP","Various Items"],
              "location":{"x":175,"y":73,"z":-715}},
             {"type":"cave","name":"Breaking Ice","description":"","length":"medium","difficulty":"medium","requirements":"46",
              "rewards":[],"location":{"x":-134,"y":74,"z":-797}}]""";
    private static final String FEATURES = """
            [{"categoryId":"wynntils:content:cave","attributes":{"label":"Wolf Den","level":43},"location":{"x":175,"y":73,"z":-715}},
             {"categoryId":"wynntils:content:dungeon","attributes":{"label":"Ice Barrows","level":45},"location":{"x":134,"y":62,"z":-639}},
             {"categoryId":"wynntils:content:dungeon:corrupted","attributes":{"label":"Corrupted Ice Barrows","level":75},
              "location":{"x":1,"y":2,"z":3}},
             {"categoryId":"wynntils:content:boss-altar","attributes":{"label":"Prison of Souls","level":45},"location":{"x":50,"y":60,"z":-487}},
             {"categoryId":"wynntils:content:time-trial","attributes":{"label":"Llevigar Leg Day","level":40},"location":{"x":-2018,"y":60,"z":-4822}},
             {"categoryId":"wynntils:content:grind-spot","attributes":{"label":"Witness Church"},"location":{"x":1055,"y":117,"z":-390}}]""";

    @Test
    void cavesComeFromTheCaveListAndTheRestFromTheMapFeatures() {
        List<Content.Place> all = Content.parse(CAVES, FEATURES);
        assertEquals(2, all.stream().filter(p -> p.type() == ContentType.CAVE).count(), "the cave list, not the feature twice");
        Content.Place wolf = all.stream().filter(p -> p.name().equals("Wolf Den")).findFirst().orElseThrow();
        assertEquals(43, wolf.level());
        assertEquals("easy", wolf.difficulty());
        assertEquals(List.of("7000 XP", "Various Items"), wolf.rewards());
        assertTrue(!wolf.description().contains("§"), "formatting stripped");
        assertEquals(2, all.stream().filter(p -> p.type() == ContentType.DUNGEON).count(), "corrupted dungeons count as dungeons");
        assertEquals(1, all.stream().filter(p -> p.type() == ContentType.TIME_TRIAL).count());
        assertTrue(all.stream().noneMatch(p -> p.name().equals("Witness Church")), "grind spots are not a content kind");
    }

    @Test
    void brokenFilesGiveNoPlacesRatherThanAnError() {
        assertEquals(0, Content.parse("not json", "{}").size());
    }

    @Test
    void theBookGivesEachPlaceItsStatusAndUnreadPlacesStayUnknown() {
        List<Content.Place> all = Content.parse(CAVES, FEATURES);
        Map<String, Wyn.ContentLine> book = new LinkedHashMap<>();
        book.put("Wolf Den", new Wyn.ContentLine("Wolf Den", "COMPLETED", 43, ""));
        List<Content.Line> caves = Content.merge(ContentType.CAVE, all, book);
        assertEquals(2, caves.size());
        assertTrue(caves.get(0).done());
        assertEquals("UNKNOWN", caves.get(1).status());
    }

    @Test
    void discoveriesHaveNoPlaceAndComeFromTheBook() {
        Map<String, Wyn.ContentLine> book = new LinkedHashMap<>();
        book.put("Nesaak Village", new Wyn.ContentLine("Nesaak Village", "AVAILABLE", 35, "A snowy village"));
        List<Content.Line> lines = Content.merge(ContentType.WORLD_DISCOVERY, Content.parse(CAVES, FEATURES), book);
        assertEquals(1, lines.size());
        assertNull(lines.get(0).place());
        assertEquals(35, lines.get(0).level());
    }

    @Test
    void recommendedPlacesFitYourLevelSkipTheDoneOnesAndComeNearestFirst() {
        List<Content.Place> all = Content.parse(CAVES, FEATURES);
        Map<String, Wyn.ContentLine> book = Map.of("Breaking Ice", new Wyn.ContentLine("Breaking Ice", "COMPLETED", 46, ""));
        List<Content.Line> lines = new java.util.ArrayList<>(Content.merge(ContentType.CAVE, all, book));
        lines.addAll(Content.merge(ContentType.DUNGEON, all, Map.of()));
        lines.addAll(Content.merge(ContentType.BOSS_ALTAR, all, Map.of()));
        lines.addAll(Content.merge(ContentType.TIME_TRIAL, all, Map.of()));
        List<Content.Line> near = Content.near(lines, 44, 130, -640, 5);
        assertEquals(List.of("Ice Barrows", "Wolf Den", "Prison of Souls"), near.stream().map(Content.Line::name).toList(),
                "no done cave, no corrupted Lv 75, no time trial");
    }

    @Test
    void theCharactersCopyReadsBackWhatWasSaved() {
        Content.read("""
                {"DUNGEON":{"readAt":123,"lines":[{"name":"Ice Barrows","status":"COMPLETED","level":45}]}}""");
        assertTrue(Content.json().contains("\"Ice Barrows\""));
        assertTrue(Content.json().contains("\"readAt\": 123"));
    }
}
