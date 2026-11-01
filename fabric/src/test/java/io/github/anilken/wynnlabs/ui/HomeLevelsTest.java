package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which milestones get a tab on the Home. */
class HomeLevelsTest {

    private static Roadmap.Step step(int level, int wearAt, int builds) {
        Damage.Piece p = new Damage.Piece(null, "Piece", "Piece", Slot.RING, wearAt, Map.of(), Map.of(), 0, Map.of(), 0, false);
        Roadmap.Alternative a = new Roadmap.Alternative("", "", 0, 0, 0, 0, 0, 9, 0, 0, null, false, false, null);
        return new Roadmap.Step(level, List.of(), List.of(), List.of(), 0, Map.of(), Map.of(), Map.of("RING1", p), null,
                java.util.Collections.nCopies(builds, a));
    }

    @Test
    void aSetWearableNowHasNoTab() {
        assertEquals(List.of(), HomeLevels.shown(List.of(step(113, 113, 0), step(120, 113, 0))));
    }

    @Test
    void aMilestoneWithOtherBuildsKeepsItsTab() {
        List<Roadmap.Step> steps = List.of(step(113, 113, 0), step(120, 113, 11));
        assertEquals(List.of(1), HomeLevels.shown(steps));
        assertEquals(120, HomeLevels.tabLevel(steps, 1));
    }

    @Test
    void aLaterSetReadsItsOwnLevel() {
        List<Roadmap.Step> steps = List.of(step(113, 113, 0), step(120, 118, 11));
        assertEquals(List.of(1), HomeLevels.shown(steps));
        assertEquals(118, HomeLevels.tabLevel(steps, 1));
    }
}
