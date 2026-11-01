package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The replaced maps of a character: kept, capped, and brought back without a search. */
class RoadmapHistoryTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        FixtureState.load(RoadmapLocksTest.shaman()).install("en");
        Locks.rootOverride = tmp;
        Roadmap.forget();
        RoadmapHistory.forget();
    }

    @AfterEach
    void reset() {
        Saver.flush();
        Roadmap.forget();
        RoadmapHistory.forget();
        Locks.rootOverride = null;
        FixtureState.uninstall();
    }

    private static List<Roadmap.Step> map(String weapon) {
        Damage.Piece p = new Damage.Piece(null, weapon, weapon, Slot.RELIK, 40, Map.of(), Map.of(), 0, Map.of(), 0, false);
        return List.of(new Roadmap.Step(44, List.of(), List.of(), List.of(), 0, Map.of(), Map.of(), Map.of("WEAPON", p), null, List.of()));
    }

    @Test
    void onlyTheLastFiveAreKept() {
        String id = GameState.current().characterId();
        for (int i = 1; i <= 7; i++) {
            RoadmapHistory.push(id, map("Covetous Thunder"), "k", 44, i);
        }
        List<RoadmapHistory.Entry> list = RoadmapHistory.entries();
        assertEquals(List.of(7L, 6L, 5L, 4L, 3L), list.stream().map(RoadmapHistory.Entry::builtAt).toList());
        assertEquals("Covetous Thunder", list.get(0).weapon());
        assertEquals(null, RoadmapHistory.read(id, 1));
        Saver.flush();
        RoadmapHistory.forget();
        assertEquals(5, RoadmapHistory.entries().size());
    }

    @Test
    void bringingAMapBackSwapsItWithTheCurrentOne() {
        String id = GameState.current().characterId();
        RoadmapStore.save(id, map("Covetous Thunder"), "now:v" + Roadmap.mapVersion(), 44, 20);
        Saver.flush();
        assertFalse(Roadmap.cached().isEmpty());
        RoadmapHistory.push(id, map("Psychopomp's Pileus"), "old:v" + Roadmap.mapVersion(), 44, 10);

        assertTrue(Roadmap.restore(10));

        assertEquals("Psychopomp's Pileus", Roadmap.cached().get(0).build().get("WEAPON").name());
        assertEquals(List.of(20L), RoadmapHistory.entries().stream().map(RoadmapHistory.Entry::builtAt).toList());
        assertFalse(Roadmap.restore(10));
        Saver.flush();
        Roadmap.forget();
        assertEquals("Psychopomp's Pileus", Roadmap.cached().get(0).build().get("WEAPON").name());
    }

    @Test
    void theSamePicksAreNoNewMap() {
        assertTrue(Roadmap.samePicks(map("A"), map("A")));
        assertFalse(Roadmap.samePicks(map("A"), map("B")));
    }
}
