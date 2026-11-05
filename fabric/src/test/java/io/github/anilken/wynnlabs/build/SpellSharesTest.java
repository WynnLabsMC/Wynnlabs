package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.AbilityNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The tree plan weighs a node for a spell by how much that spell is to you: a Summoner whose
 * damage is nearly all Aura takes Rebound (Aura's effect twice) and skips Haul's Nature's Jolt and
 * the main attack's Hand of the Shaman; with nothing known every spell weighs alike.
 */
@Tag("heavy")
class SpellSharesTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void measuredSharesNeedEnoughCasts() {
        var few = JsonParser.parseString("{\"spells\":{\"Aura\":{\"casts\":50,\"perCast\":1000}}}").getAsJsonObject();
        assertTrue(SpellShares.measured(few).isEmpty(), "too few casts: the profile's instead");
        var many = JsonParser.parseString("{\"spells\":{\"Aura\":{\"casts\":300,\"perCast\":3000},"
                + "\"Haul\":{\"casts\":100,\"perCast\":1000}}}").getAsJsonObject();
        Map<String, Double> m = SpellShares.measured(many);
        assertEquals(0.9, m.get("aura"), 1e-9);
        assertEquals(0.1, m.get("haul"), 1e-9);
        SpellShares s = new SpellShares(m, 0.2);
        assertEquals(1.8, s.weight("Aura"), 1e-9);
        assertEquals(0.25, s.weight("Haul"), 1e-9, "a spell you hardly cast: its floor");
        assertEquals(1, s.weight(null));
        assertEquals(1, SpellShares.even().weight("Aura"));
        assertEquals(0.4, s.mainAttackWeight(), 1e-9);
    }

    @Test
    void anAuraSummonerTakesReboundAndSkipsWhatItNeverUses() throws IOException {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        FixtureState.load(dir).withTree(nodes, List.of()).install("en");
        // the damage a level 119 Summoner measured: Aura most of it, Haul hardly any
        SpellShares aura = new SpellShares(Map.of("aura", 0.86, "totem", 0.08, "uproot", 0.04, "haul", 0.02), 0.2);
        List<TreePlanner.Step> steps = TreePlanner.plan(ArchetypeMix.parse("Summoner"), List.of(), aura);
        Set<String> plan = names(steps);
        System.out.println("aura summoner plan: " + steps.stream().map(TreePlanner.Step::name).toList());
        assertTrue(plan.contains("Rebound"), "Aura twice: " + plan);
        assertFalse(plan.contains("Nature's Jolt"), "Haul's damage, never cast: " + plan);
        assertFalse(plan.contains("Hand of the Shaman"), "main attack at 0.2: " + plan);

        System.out.println("even summoner plan: " + TreePlanner.plan(ArchetypeMix.parse("Summoner"), List.of(),
                SpellShares.even()).stream().map(TreePlanner.Step::name).toList());
    }

    private static Set<String> names(List<TreePlanner.Step> plan) {
        Set<String> out = new HashSet<>();
        plan.forEach(s -> out.add(s.name()));
        return out;
    }
}
