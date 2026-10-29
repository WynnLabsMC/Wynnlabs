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

    /** A ride to Altitude 10 (10 min), then paper. */
    private FeedPlan rideFirst() {
        List<FeedStep> steps = List.of(FeedStep.train(MountStat.ALTITUDE, 10, 600, 0), FeedStep.feed(paper, 900, false));
        return new FeedPlan(true, true, steps, 1500, 900, 600, 0, start, 0, "test");
    }

    private MountStats altitude(int level) {
        return FeedProgress.apply(start, FeedStep.train(MountStat.ALTITUDE, level, 0, 0));
    }

    @Test
    void aRideAlreadyDoneIsPlanProgressNotAStalePlan() {
        assertEquals(1, FeedProgress.of(rideFirst(), start, altitude(10), List.of()), "ridden to 10: past the ride");
        assertEquals(0, FeedProgress.of(rideFirst(), start, altitude(7), List.of()), "still riding");
    }

    @Test
    void aDoneRideIsLeftOutOfThePlan() {
        FeedPlan left = FeedProgress.pending(rideFirst(), altitude(10), 60);
        assertEquals(1, left.steps().size());
        assertEquals(paper, left.steps().get(0).material());
        assertEquals(900, left.totalTimeSec());
        assertEquals(0, left.trainingTimeSec());
    }

    @Test
    void aRidePartlyDoneTakesOnlyTheLevelsLeft() {
        FeedPlan left = FeedProgress.pending(rideFirst(), altitude(7), 60);
        FeedStep ride = left.steps().get(0);
        assertEquals(MountStat.ALTITUDE, ride.stat());
        assertEquals(10, ride.toLevel());
        assertEquals(180, ride.durationSec() - ride.coveredSec(), "three levels at a minute each");
        assertEquals(1080, left.totalTimeSec());
    }

    @Test
    void nothingDoneLeavesThePlanAsItIs() {
        FeedPlan p = rideFirst();
        assertEquals(p, FeedProgress.pending(p, start, 0));
    }
}
