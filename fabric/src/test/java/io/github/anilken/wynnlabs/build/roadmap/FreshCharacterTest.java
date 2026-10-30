package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A character that just started (Lv 1, nothing worn or carried) gets a road map: with no weapon
 * the build scored nothing and every candidate was dropped, so a new player was told nothing.
 * Its pills read sensibly too: its Lv 1 gear once made a Lv 120 relik "+64092 %". Both tests
 * read the same map, built once.
 */
@Tag("heavy")
class FreshCharacterTest {

    private static List<Roadmap.Step> fresh;

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    /** Installs the recorded Shaman as a new character and returns its road map (built on the first call). */
    private static List<Roadmap.Step> freshMap() throws Exception {
        FixtureState.load(SharedRoadmaps.shamanDir()).fresh().install("en");
        if (fresh == null) {
            fresh = List.copyOf(Roadmap.build());
        }
        return fresh;
    }

    @Test
    void aNewCharacterGetsAWeaponAndASetAtEveryMilestone() throws Exception {
        List<Roadmap.Step> steps = freshMap();
        assertTrue(!steps.isEmpty(), "no road map");
        Slot weapon = Slot.weaponOf(GameState.current().classType());
        assertTrue(steps.get(0).gear().stream().anyMatch(g -> g.slot().equals(weapon.name())),
                "no weapon at Lv " + steps.get(0).level() + ": " + steps.get(0).gear().stream().map(g -> g.info().name()).toList());
        for (Roadmap.Step s : steps) {
            assertTrue(s.set() != null && s.set().result().objective() > 0, "Lv " + s.level() + ": the set scores nothing");
        }
    }

    @Test
    void aNewCharactersPillsReadSensibly() throws Exception {
        List<Roadmap.Step> steps = freshMap();
        assumeTrue(!steps.isEmpty(), "no road map");
        List<String> bad = new ArrayList<>();
        Map<String, Roadmap.Gear> latest = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            Roadmap.Step s = steps.get(i);
            for (Roadmap.Gear g : s.gear()) {
                latest.put(g.slot(), g);
            }
            Roadmap.keepWorn(latest, s);
            for (Roadmap.Gear g : latest.values()) {
                double shown = Roadmap.shownGain(steps, i, g);
                String pill = Roadmap.pill(shown, g);
                Fixtures.note("Lv " + s.level() + " " + g.slot() + " " + g.info().name() + ": " + pill);
                // nothing is worn: a row reads against what you have (nothing), the whole per cent, from 100000 % in thousands
                if (!pill.equals("New") && !pill.equals("Set") && !pill.matches("[+−]\\d{1,5}k?%")) {
                    bad.add("Lv " + s.level() + " " + g.slot() + " " + g.info().name() + ": " + pill);
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
        // the first weapon of an empty build has nothing to compare with
        Roadmap.Gear weapon = steps.get(0).gear().stream()
                .filter(g -> g.info().slot().isWeapon()).findFirst().orElse(null);
        assertTrue(weapon != null, "a weapon at the start");
        assertEquals("New", Roadmap.pill(Roadmap.shownGain(steps, 0, weapon), weapon));
    }
}
