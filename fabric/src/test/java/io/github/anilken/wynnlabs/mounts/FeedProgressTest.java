package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

class FeedProgressTest {

    private final Material copper = MountMaterials.of(MaterialKind.INGOT, 1);
    private final Material paper = MountMaterials.of(MaterialKind.PAPER, 1);
    private final Material grain = MountMaterials.of(MaterialKind.GRAIN, 1);
    private final MountStats start = MountStats.fresh();

    /** Copper, then a ride to Altitude 5, then paper. */
    private FeedPlan plan() {
        List<FeedStep> steps = List.of(FeedStep.feed(copper, 60, false), FeedStep.train(MountStat.ALTITUDE, 5, 240, 0),
                FeedStep.feed(paper, 60, false));
        return new FeedPlan(true, true, steps, 360, 120, 240, 0, start, 0, "test");
    }

    @Test
    void nothingDoneYetIsStepZero() {
        assertEquals(0, FeedProgress.of(plan(), start, start, List.of()));
    }

    @Test
    void queuingThePlannedFoodIsOnTrackAndAdvances() {
        assertEquals(1, FeedProgress.of(plan(), start, start, List.of(copper)));
    }

    @Test
    void queuingAnotherFoodIsStale() {
        assertEquals(-1, FeedProgress.of(plan(), start, start, List.of(grain)));
    }

    @Test
    void aFeedFinishedAsPlannedAdvances() {
        assertEquals(1, FeedProgress.of(plan(), start, start.fed(copper), List.of()));
    }

    @Test
    void theRideAndTheNextFoodCountTooAndRidingAheadIsFine() {
        MountStats rode = FeedProgress.apply(start.fed(copper), FeedStep.train(MountStat.ALTITUDE, 7, 0, 0));
        assertEquals(3, FeedProgress.of(plan(), start, rode, List.of(paper)), "copper eaten, ridden past 5, paper queued");
    }

    @Test
    void otherStatsThanAnyStepAreStale() {
        assertEquals(-1, FeedProgress.of(plan(), start, MountStats.uniform(5, 20, 40), List.of()));
    }
}
