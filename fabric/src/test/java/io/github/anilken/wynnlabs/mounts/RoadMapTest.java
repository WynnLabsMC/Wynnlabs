package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Breeding estimates (ranges from one set of constants) and the road maps to a target potential. */
@Tag("heavy")
class RoadMapTest {
    private static final RoadMap.Options WYVERN = RoadMap.Options.defaults(MountType.WYVERN);

    @Test
    void breedingGivesRangesFromLimitsOnly() {
        MountStats full = MountStats.uniform(1, 30, 30);
        BreedEstimate e = Breeding.estimate(full, full);
        assertTrue(e.eligible());
        BreedEstimate.StatRange s = e.stats().get(0);
        assertEquals(new Range(38, 42), s.limit());
        assertEquals(new Range(53, 67), s.max());
        assertEquals(new Range(424, 536), e.potential());
        assertEquals(1.0, e.closeness());
        // only limits matter: a bigger max on one parent changes nothing
        BreedEstimate same = Breeding.estimate(MountStats.uniform(1, 30, 60), full);
        assertEquals(e.potential(), same.potential());
        assertFalse(Breeding.estimate(MountStats.fresh(), full).eligible());
        assertTrue(Breeding.estimate(MountStats.uniform(1, 20, 30), full).closeness() < 1);
    }

    @Test
    void constantsCanBeRefitted() {
        BreedConstants k = new BreedConstants(20, 0, 0, 0, 0, 0, 3600, 255);
        MountStats full = MountStats.uniform(1, 30, 30);
        BreedEstimate e = Breeding.estimate(full, full, k);
        assertEquals(new Range(240, 240), e.potential());
        assertEquals(3600, e.durationSec());
    }

    @Test
    void target500() {
        assertEquals(4, check(500).mountsToBuy());
    }

    @Test
    void target700() {
        assertEquals(4, check(700).mountsToBuy());
    }

    @Test
    void target850() {
        assertEquals(8, check(850).mountsToBuy());
    }

    @Test
    void target1000() {
        assertEquals(16, check(1000).mountsToBuy());
    }

    @Test
    void target1200() {
        assertEquals(16, check(1200).mountsToBuy());
    }

    /** Reached on the middle estimate, a real range, consistent counts and totals, estimates never "proven". */
    private static RoadMap.Plan check(int target) {
        RoadMap.Plan p = RoadMap.plan(target, WYVERN);
        assertNotNull(p);
        assertTrue(p.reached(), target + " reachable within " + RoadMap.MAX_GENERATIONS + " generations");
        assertEquals(target, p.targetPotential());
        assertTrue(p.finalPotential().low() < p.finalPotential().high(), "a range, not one number");
        assertTrue(p.finalPotential().high() >= target);
        int g = p.generations().size();
        assertTrue(g >= 1 && g <= RoadMap.MAX_GENERATIONS);
        assertEquals(1 << g, p.mountsToBuy());
        assertEquals(1 << g, p.generations().get(0).bought());
        int sum = p.materials().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(p.totalFeeds(), sum);
        long work = 0, wall = 0;
        for (RoadMap.Generation gen : p.generations()) {
            assertEquals(gen.parents() / 2, gen.breeds());
            assertTrue(gen.feedPlan().goalReached(), "generation " + gen.index() + " reaches its feed goal");
            assertFalse(gen.feedPlan().proven() && "estimate".equals(gen.feedPlan().source()));
            FeedPlan f = gen.feedPlan();
            work += (f.feedTimeSec() + f.fullTrainingSec()) * gen.parents() + gen.breedTimeSec() * gen.breeds();
            // one feeder: parents 5 at a time, then the breedings 5 at a time (both parents share one row)
            long gw = (gen.parents() + 4) / 5 * f.totalTimeSec() + (gen.breeds() + 4) / 5 * gen.breedTimeSec();
            assertEquals(gw, gen.wallClockSec());
            wall += gw;
        }
        assertEquals(work, p.workTimeSec());
        assertEquals(wall, p.wallClockSec());
        assertTrue(p.wallClockSec() <= p.workTimeSec());
        return p;
    }

    @Test
    void oneFeederRowIsTheOldSequentialClock() {
        for (int t : RoadMap.TARGETS) {
            RoadMap.Plan one = RoadMap.plan(t, WYVERN.withFeederRows(1));
            long seq = 0;
            for (RoadMap.Generation gen : one.generations()) {
                seq += gen.feedPlan().totalTimeSec() * gen.parents() + gen.breedTimeSec() * gen.breeds();
            }
            assertEquals(seq, one.wallClockSec(), "target " + t);
            RoadMap.Plan five = RoadMap.plan(t, WYVERN);
            assertEquals(one.mountsToBuy(), five.mountsToBuy(), "rows never change the mount count");
            assertTrue(five.wallClockSec() <= one.wallClockSec());
        }
        assertEquals(5, WYVERN.feederRows());
        assertEquals(1, WYVERN.withFeederRows(0).feederRows());
    }

    @Test
    void higherTargetsNeverNeedFewerMounts() {
        int prev = 0;
        for (int t : RoadMap.TARGETS) {
            RoadMap.Plan p = RoadMap.plan(t, WYVERN);
            assertTrue(p.mountsToBuy() >= prev, "target " + t);
            prev = p.mountsToBuy();
        }
    }

    @Test
    void timeObjectiveTradesMountsForTime() {
        for (int t : RoadMap.TARGETS) {
            RoadMap.Plan mounts = RoadMap.plan(t, WYVERN);
            RoadMap.Plan time = RoadMap.plan(t, WYVERN.withObjective(RoadMap.Objective.TIME));
            assertTrue(time.reached());
            assertTrue(time.workTimeSec() <= mounts.workTimeSec(), "target " + t);
            assertTrue(time.mountsToBuy() >= mounts.mountsToBuy(), "target " + t);
        }
        assertEquals(RoadMap.Objective.MOUNTS, WYVERN.objective());
    }

    @Test
    void unreachableTargetSaysSo() {
        RoadMap.Plan p = RoadMap.plan(5000, WYVERN);
        assertFalse(p.reached());
        assertTrue(p.finalPotential().mid() < 5000);
    }

    @Test
    void aliasesStillWork() {
        assertEquals(700, RoadMap.plan(RoadMap.Standard.LOW, WYVERN).targetPotential());
        assertEquals(1000, RoadMap.plan(RoadMap.Standard.HIGH, WYVERN).targetPotential());
    }

    @Test
    void trainingTimeIsAParameter() {
        FeedPlan slow = FeedPlanner.plan(MountStats.fresh(), FeedGoal.full(),
                new RoadMap.Options(MountType.HORSE, 300, Integer.MAX_VALUE, false).feedOptions());
        FeedPlan none = FeedPlanner.plan(MountStats.fresh(), FeedGoal.full(),
                new RoadMap.Options(MountType.HORSE, 0, Integer.MAX_VALUE, false).feedOptions());
        assertTrue(slow.fullTrainingSec() > none.fullTrainingSec());
    }
}
