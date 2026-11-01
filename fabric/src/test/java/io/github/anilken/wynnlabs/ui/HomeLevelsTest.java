package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The level a milestone's tab on the Home reads. */
class HomeLevelsTest {

    private static Roadmap.Step step(int level, int wearAt) {
        Damage.Piece p = new Damage.Piece(null, "Piece", "Piece", Slot.RING, wearAt, Map.of(), Map.of(), 0, Map.of(), 0, false);
        return new Roadmap.Step(level, List.of(), List.of(), List.of(), 0, Map.of(), Map.of(), Map.of("RING1", p), null,
                List.of());
    }

    @Test
    void aSetWearableNowReadsItsMilestone() {
        assertEquals(120, HomeLevels.tabLevel(List.of(step(113, 113), step(120, 113)), 1));
    }

    @Test
    void aLaterSetReadsItsOwnLevel() {
        assertEquals(118, HomeLevels.tabLevel(List.of(step(113, 113), step(120, 118)), 1));
    }

    @Test
    void twoTabsNeverReadTheSame() {
        List<Roadmap.Step> steps = List.of(step(95, 95), step(100, 98), step(110, 98));
        assertEquals(98, HomeLevels.tabLevel(steps, 1));
        assertEquals(110, HomeLevels.tabLevel(steps, 2));
    }
}
