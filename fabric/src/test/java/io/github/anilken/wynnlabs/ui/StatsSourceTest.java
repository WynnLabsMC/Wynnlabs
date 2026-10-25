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
        Build.use(Build.compute());
    }

    @AfterEach
    void reset() {
        Saver.flush();
        Locks.rootOverride = null;
        Plans.forget();
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
        // the Plan pill steers it, whatever the header's choice kept from earlier in the session
        src.choose(StatsSource.NONE);
        src.planPill(true);
        assertEquals("Imported", src.shown().name(), "the Plan pill shows the plan");
        src.planPill(false);
        assertEquals(null, src.shown().other(), "\"now\" leaves it to the header's choice");
        src.choose(StatsSource.AUTO);
        // read back from the file, as after a restart: the points stay
        Saver.flush();
        Plans.forget();
        Plans.Build back = Plans.current();
        assertNotNull(back);
        assertEquals(List.of(75, 0, 35, 0, 0), back.points());
        assertNotNull(new StatsSource().shown().other(), "and the plan read back is compared with too");
    }
}
