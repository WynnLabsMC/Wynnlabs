package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Home's stats compare with the applied plan, also one you cannot wear yet, also after it is read back. */
class StatsSourceTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        Locks.rootOverride = tmp;
        Plans.forget();
        StatsChoice.forget();
        Build.use(Build.compute());
    }

    @AfterEach
    void reset() {
        Saver.flush();
        Locks.rootOverride = null;
        Plans.forget();
        StatsChoice.forget();
        Build.use(null);
        FixtureState.uninstall();
    }

    @Test
    void anImportedPlanYouCannotWearYetIsWhatTheStatsCompareWith() {
        GameState state = GameState.current();
        Map<String, String> items = new LinkedHashMap<>();
        for (ItemData i : state.allGear().filter(i -> i.level() > 100 && Locks.kind(i) != null).limit(200).toList()) {
            String slot = Locks.kind(i).isWeapon() ? "WEAPON" : Build.slotOf(Locks.kind(i), 0);
            if (slot != null) {
                items.putIfAbsent(slot, i.name());
            }
        }
        assertTrue(items.containsKey("WEAPON"), "the fixture has a high weapon: " + items);
        // a Lv 121 WynnBuilder link with its own points, past the 200 you earn
        Plans.Build link = new Plans.Build("p9", "Imported", List.of(), Tuning.Choice.PLAIN, items, 0, 121, 1, 1,
                List.of(75, 0, 35, 0, 0));
        Plans.apply(link);
        StatsSource src = new StatsSource();
        src.choose(StatsSource.AUTO);
        StatsSource.Shown s = src.shown();
        assertNotNull(s.now(), "your gear");
        assertNotNull(s.other(), "the plan is compared with");
        assertEquals("Imported", s.name());
        assertTrue(s.other().notWearable() != null && s.other().level() >= 121, "at its own level, not wearable yet");
        assertTrue(!s.other().stats().isEmpty() && s.other().parts() != null, "with its stats and spells");
        // choosing the Plan pill hands the choice back to Automatic, which shows the plan
        src.planPill(false);
        src.pill("at0");
        src.choose(StatsSource.NONE);
        src.planPill(true);
        src.pill("plan");
        assertEquals("Imported", src.shown().name(), "the Plan pill shows the plan");
        assertEquals(StatsSource.AUTO, src.choice(), "and Automatic is ticked");
        src.choose(StatsSource.AUTO);
        // read back from the file, as after a restart: the points stay
        Saver.flush();
        Plans.forget();
        Plans.Build back = Plans.current();
        assertNotNull(back);
        assertEquals(List.of(75, 0, 35, 0, 0), back.points());
        assertNotNull(new StatsSource().shown().other(), "and the plan read back is compared with too");
    }

    @Test
    void theMenuPickIsWhatTheChipTheTickAndTheDiffsFollow() {
        Plans.apply(new Plans.Build("p1", "Mine", List.of(), Tuning.Choice.PLAIN, Map.of(), 0, 44, 1, 1));
        StatsSource src = new StatsSource();
        src.level(null);
        src.planPill(true);                         // a pill selected, as in the report
        src.pill("plan");
        assertEquals("plan", src.target(), "Automatic: the Plan pill's plan");
        assertTrue(src.options().get(0)[1].contains("Mine"), "Automatic names what it resolved to");
        // a row picked in the menu: "No comparison" ticked, and no other build behind the rows
        src.choose(StatsSource.NONE);
        assertEquals(StatsSource.NONE, src.choice());
        assertEquals(StatsSource.NONE, src.target());
        assertEquals(null, src.shown().other(), "no diffs");
        src.pill("plan");                           // the next frames: the pick stays
        assertEquals(null, src.shown().other(), "still no diffs a frame later");
        src.choose("suggested");
        assertEquals("suggested", src.choice());
        // kept for the character: a new Home (a restart) reads it back
        Saver.flush();
        StatsChoice.forget();
        assertEquals("suggested", new StatsSource().choice());
    }

    @Test
    void aPickThatIsGoneFallsBackToAutomatic() {
        var avail = java.util.Set.of("level", "plan", "suggested");
        assertEquals("level", StatsChoice.target(StatsSource.AUTO, avail, false), "level pill first");
        assertEquals("plan", StatsChoice.target(StatsSource.AUTO, avail, true), "Plan pill: the plan");
        assertEquals(StatsSource.NONE, StatsChoice.target(StatsSource.NONE, avail, true), "none wins over the pills");
        assertEquals("suggested", StatsChoice.target("suggested", avail, false), "a pick wins over a level pill");
        assertEquals("level", StatsChoice.target("saved:gone", avail, false), "a deleted saved build: Automatic");
        assertEquals(StatsSource.AUTO, StatsChoice.ticked("saved:gone", avail), "and Automatic ticked");
        assertEquals("suggested", StatsChoice.target(StatsSource.AUTO, java.util.Set.of("suggested"), false));
    }
}
