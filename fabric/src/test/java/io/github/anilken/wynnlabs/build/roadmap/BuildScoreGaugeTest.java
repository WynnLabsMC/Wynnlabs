package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Home's build score gauge fills toward one goal, the road map's last milestone: a Lv 5
 * character's 32 filled the whole arc (no goal read as full) while 26.0k on another was a sliver.
 */
class BuildScoreGaugeTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void fillIsTheScoreOverTheGoalClamped() {
        assertEquals(0.5, Roadmap.scoreFill(320_000, 640_000), 1e-9);
        assertEquals(1, Roadmap.scoreFill(700_000, 640_000), 1e-9);       // past the goal: full, never more
        assertEquals(32 / 640_000.0, Roadmap.scoreFill(32, 640_000), 1e-12); // a tiny score: a sliver, not full
        assertTrue(Roadmap.scoreFill(32, 640_000) < 0.001);
    }

    @Test
    void noGoalOrNoScoreIsAnEmptyGauge() {
        assertEquals(0, Roadmap.scoreFill(32, Double.NaN));
        assertEquals(0, Roadmap.scoreFill(32, 0));
        assertEquals(0, Roadmap.scoreFill(32, -5));
        assertEquals(0, Roadmap.scoreFill(0, 640_000));
        assertEquals(0, Roadmap.scoreFill(Double.NaN, 640_000));
        assertEquals(0, Roadmap.scoreFill(-1, 640_000));
        assertNull(Roadmap.scoreTarget(List.of()));
    }

    @Test
    void theGoalIsTheLastMilestoneWithASet() throws Exception {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        FixtureState.load(dir).install("en");
        List<Roadmap.Step> steps = Roadmap.build();
        Roadmap.Step goal = Roadmap.scoreTarget(steps);
        assertNotNull(goal, "the road map has a goal");
        int last = 0;
        for (Roadmap.Step s : steps) {
            if (s.set() != null && s.set().result().objective() > 0) {
                last = Math.max(last, s.level());
            }
        }
        assertEquals(last, goal.level(), "the goal is the highest milestone with a set");
        double fill = Roadmap.scoreFill(goal.set().result().objective() / 4, goal.set().result().objective());
        assertEquals(0.25, fill, 1e-9);
        Fixtures.note("goal Lv " + goal.level() + ": " + Math.round(goal.set().result().objective()));
    }
}
