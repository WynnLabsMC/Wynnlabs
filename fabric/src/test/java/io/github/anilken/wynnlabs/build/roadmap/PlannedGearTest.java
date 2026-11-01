package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a later level's build wears counts as planned, wherever that build is shown. */
class PlannedGearTest {

    private static final Map<String, Damage.Piece> SET = Map.of("RING1", new Damage.Piece(null, "Messenger", "Messenger",
            Slot.RING, 118, Map.of(), Map.of(), 0, Map.of(), 0, false));

    @Test
    void aRoadMapStepsBuildPlansItsPieces() {
        Roadmap.Step step = new Roadmap.Step(120, List.of(), List.of(), List.of(), 0, Map.of(), Map.of(), SET, null, List.of());
        assertTrue(Roadmap.wears(List.of(step), List.of(), "Messenger"));
        assertFalse(Roadmap.wears(List.of(step), List.of(), "Symbiont"));
    }

    @Test
    void aBuildTheWindowShowsPlansItsPieces() {
        Roadmap.Candidate shown = new Roadmap.Candidate(SET, null, 0, 0, List.of(), Set.of());
        assertTrue(Roadmap.wears(List.of(), List.of(shown), "Messenger"));
        assertFalse(Roadmap.wears(List.of(), List.of(), "Messenger"));
    }
}
