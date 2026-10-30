package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The feed planner against the reference planner's verified outputs, plus its memory/time budget. */
@Tag("heavy")
class FeedPlannerTest {

    @Test
    void all72ReferenceCasesMatchStepForStep() {
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (FeedCases.Case c : FeedCases.load()) {
            n++;
            FeedPlan plan = FeedPlanner.plan(c.stats(), c.goal(), c.options());
            if (!plan.proven()) {
                bad.add(c.name() + ": not proven");
            }
            for (String d : FeedCases.diff(c, plan)) {
                bad.add(c.name() + ": " + d);
            }
        }
        assertEquals(72, n);
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void freshMountFitsTheBudget() {
        // a fresh 240 mount to full limits, any stat trainable: the reference needed ~1.8M states / 20+ GB
        for (FeedOptions o : List.of(FeedOptions.defaults(), FeedOptions.defaults().withTrainSecPerLevel(0),
                FeedOptions.defaults().withTrainSecPerLevel(120).withQueueSwaps(true))) {
            FeedPlanner.Stats st = new FeedPlanner.Stats();
            long t0 = System.nanoTime();
            FeedPlan plan = FeedPlanner.plan(MountStats.fresh(), FeedGoal.full(), o, () -> false, st);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(plan.proven() && plan.goalReached(), "fresh plan proven");
            assertTrue(st.stored < 100_000, "stored states " + st.stored);
            assertTrue(st.bytes < 32L << 20, "search memory " + (st.bytes >> 20) + " MB");
            assertTrue(ms < 15_000, "took " + ms + " ms");
        }
        FeedPlan p = FeedPlanner.plan(MountStats.fresh(), FeedGoal.full(), FeedOptions.defaults());
        assertEquals(116_700, p.totalTimeSec());
        assertEquals(10, p.feeds());
    }

    @Test
    void jobRunsOnlyWhenStartedAndIsCachedOnDisk(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path file = dir.resolve("mount-plans.json");
        FeedCache.attach(file);
        FeedGoal goal = FeedGoal.full();
        FeedOptions o = FeedOptions.defaults();
        assertEquals(null, FeedPlanner.cached("m1", MountStats.fresh(), goal, o), "nothing runs by itself");
        FeedJob job = FeedPlanner.start(new FeedPlanner.Request("m1", MountStats.fresh(), goal, o));
        FeedPlan done = job.result().get(60, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(job.done() && job.proven() && done.proven());
        assertEquals(116_700, done.totalTimeSec());
        assertTrue(job.stats().explored() > 0);
        assertTrue(FeedWorker.maxSliceCpuNs < 200_000_000L, "a slice used " + FeedWorker.maxSliceCpuNs / 1e6 + " ms");

        FeedCache.Cached same = FeedPlanner.cached("m1", MountStats.fresh(), goal, o);
        assertNotNull(same);
        assertFalse(same.stale());
        assertTrue(same.plan().proven());
        assertEquals(done.steps(), same.plan().steps());
        MountStats fed = MountStats.fresh().fed(MountMaterials.of(MaterialKind.PAPER, 1));
        assertTrue(FeedPlanner.cached("m1", fed, goal, o).stale(), "the mount changed: shown, marked stale");

        io.github.anilken.wynnlabs.core.Saver.settle(file);
        FeedCache.attach(file);
        FeedCache.Cached back = FeedPlanner.cached("m1", MountStats.fresh(), goal, o);
        assertNotNull(back, "read back from disk");
        assertEquals(done.steps(), back.plan().steps());

        FeedJob cut = FeedPlanner.start(new FeedPlanner.Request("m2", MountStats.fresh(), goal,
                o.withTrainSecPerLevel(90)));
        cut.cancel();
        FeedPlan best = cut.result().get(60, java.util.concurrent.TimeUnit.SECONDS);
        if (best != null) {
            assertFalse(best.proven(), "a cancelled search is never called optimal");
        }
        FeedCache.attach(null);
    }

    @Test
    void estimateIsQuickAndHonest() {
        FeedPlan e = FeedPlanner.estimate(MountStats.fresh(), FeedGoal.full(), FeedOptions.defaults());
        assertTrue(e.goalReached());
        assertFalse(e.proven());
        assertEquals("estimate", e.source());
        assertTrue(e.totalTimeSec() >= 116_700);
    }

    @Test
    void cutSearchIsNeverCalledOptimal() {
        FeedPlan plan = FeedPlanner.plan(MountStats.fresh(), FeedGoal.full(),
                FeedOptions.defaults().withBudget(50, 10_000));
        assertFalse(plan.proven());
        assertTrue(plan.goalReached(), "falls back to the quick plan");
        assertTrue(plan.totalTimeSec() >= 116_700, "never better than the optimum");
    }

    @Test
    void breedReadyFromFresh() {
        FeedPlan plan = FeedPlanner.plan(MountStats.fresh(), FeedGoal.breedReady(), FeedOptions.defaults());
        assertTrue(plan.proven() && plan.goalReached());
        assertTrue(plan.end().breedReady());
        assertTrue(FeedGoal.breedReady().reachedBy(plan.end()));
    }

    @Test
    void alreadyAtGoalIsAnEmptyProvenPlan() {
        MountStats full = MountStats.uniform(1, 30, 30);
        FeedPlan plan = FeedPlanner.plan(full, FeedGoal.full(), FeedOptions.defaults());
        assertTrue(plan.proven() && plan.goalReached());
        assertEquals(0, plan.feeds());
        assertEquals("none", plan.source());
    }

    @Test
    void replayGivesTheSamePlan() {
        FeedPlan plan = FeedPlanner.plan(MountStats.fresh(), FeedGoal.full(), FeedOptions.defaults());
        List<FeedPlanner.Feed> feeds = new ArrayList<>();
        MountStat trained = null;
        for (FeedStep s : plan.steps()) {
            if (s.isFeed()) {
                feeds.add(new FeedPlanner.Feed(s.material(), trained));
                trained = null;
            } else if (trained == null) {
                trained = s.stat();
            }
        }
        // a swap's training follows its feed; this plan has none
        FeedPlan again = FeedPlanner.replay(MountStats.fresh(), feeds, FeedOptions.defaults());
        assertNotNull(again);
        assertEquals(plan.steps(), again.steps());
        assertFalse(again.proven());
    }

    @Test
    void materialsAreTheRealItems() {
        assertEquals(112, MountMaterials.all().size());
        Material m = MountMaterials.of(MaterialKind.INGOT, 1);
        assertEquals("Copper Ingot", m.name());
        int[] g = MountStats.fresh().gainsFrom(m);
        assertEquals(8, g[MountStat.TOUGHNESS.ordinal()]);
        assertEquals(4, g[MountStat.ENERGY.ordinal()]);
        assertEquals(MaterialKind.WOOD, MountMaterials.byName("Redwood Plank").kind());
        assertEquals(115, MountMaterials.byName("Redwood Plank").level());
    }

    @Test
    void timerTable() {
        assertEquals(60, MountRules.timerSec(88));
        assertEquals(300, MountRules.timerSec(89));
        assertEquals(18000, MountRules.timerSec(152));
        assertEquals(21600, MountRules.timerSec(153));
    }
}
