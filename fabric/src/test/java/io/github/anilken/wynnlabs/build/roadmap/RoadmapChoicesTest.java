package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.MapChoices;
import io.github.anilken.wynnlabs.game.FixtureState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What you tell the map on the recorded Shaman: a ban, a kept piece and the item level filter. */
@Tag("heavy")
class RoadmapChoicesTest {

    private static final String KEPT = RoadmapLocksTest.DEX_RELIK;

    @AfterEach
    void reset() {
        MapChoices.use(null);
        FixtureState.uninstall();
    }

    private static Roadmap.Computed map(MapChoices.Choice c) throws Exception {
        FixtureState.load(RoadmapLocksTest.shaman()).install("en");
        MapChoices.use(c);
        return Roadmap.compute(Roadmap.Inputs.PLAIN, null);
    }

    private static boolean wears(Roadmap.Step s, String name) {
        return s.build().values().stream().anyMatch(p -> p != null && p.name().equals(name));
    }

    @Test
    void aBannedPieceIsNeverWorn() throws Exception {
        Roadmap.Computed plain = map(MapChoices.Choice.NONE);
        String name = plain.steps().get(plain.steps().size() - 1).build().get("HELMET").name();
        Roadmap.Computed out = map(new MapChoices.Choice(List.of(), List.of(name), 0));
        assertFalse(out.steps().stream().anyMatch(s -> wears(s, name)), name);
    }

    @Test
    void aKeptPieceStaysFromTheLevelItCanBeWorn() throws Exception {
        Roadmap.Computed out = map(new MapChoices.Choice(List.of(KEPT), List.of(), 0));
        boolean held = false;
        for (Roadmap.Step s : out.steps()) {
            held |= wears(s, KEPT);
            assertTrue(!held || wears(s, KEPT), "dropped at " + s.level());
        }
        assertTrue(held, "never worn");
    }

    @Test
    void theLevelFilterOnlyActsFromItsLevel() throws Exception {
        Roadmap.Computed out = map(new MapChoices.Choice(List.of(), List.of(), 80));
        for (Roadmap.Step s : out.steps()) {
            if (s.level() >= 80) {
                for (Damage.Piece p : s.build().values()) {
                    assertTrue(p == null || p.level() >= 80 || p.crafted(), p + " at " + s.level());
                }
            }
        }
    }

    @Test
    void choicesChangeTheKeyOnlyWhenSet() {
        assertTrue(MapChoices.Choice.NONE.key().isEmpty());
        assertFalse(new MapChoices.Choice(List.of(), List.of("A"), 0).key().isEmpty());
    }
}
