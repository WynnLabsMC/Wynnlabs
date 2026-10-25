package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.SkillKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Spell costs as the game takes them: a Lv 106 Shaman with Cheaper Totem (−10) and Cheaper
 * Totem II (−5) and 39 Intelligence, no spell cost IDs, casts Totem for 11 mana in game. The
 * model has 30 − 15 = 15 less 20.2 % for the Intelligence, 11.97, and the game rounds it down.
 */
class SpellCostTest {

    private static final List<String> SPELLS = List.of("Totem", "Haul", "Uproot", "Aura");
    private static final List<String> NODES = List.of("Totem", "Haul", "Uproot", "Aura", "Cheaper Totem",
            "Cheaper Totem II", "Cheaper Haul", "Cheaper Uproot", "Cheaper Uproot II", "Cheaper Aura", "Cheaper Aura II");

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Map<String, Double> costs(int intelligence) throws IOException {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("geardb.json")), "no shaman-44 fixture");
        List<AbilityNode> tree = TreeModelTest.tree("shaman");
        FixtureState.load(dir).withTree(tree, NODES).install("en");
        JsonObject rules = Rules.get().deepCopy();
        rules.getAsJsonObject("model").add("spells", ClassProfiles.spells());
        Rules.use(rules);
        int[] assigned = new int[SkillKind.values().length];
        assigned[SkillKind.INTELLIGENCE.ordinal()] = intelligence;
        TreeModel.Overlay o = TreeModel.overlay(106, TreeModel.read(tree, "SHAMAN", SPELLS), SPELLS, NODES, 1, 2.5);
        Damage.Detail d = TreeModel.with(o, () -> Damage.detail(List.of(), null, assigned, 106));
        Map<String, Double> out = new java.util.HashMap<>();
        d.breakdown().spells().forEach(sp -> out.put(sp.name(), sp.cost()));
        return out;
    }

    @Test
    void totemCostsWhatTheGameTakes() throws IOException {
        Map<String, Double> c = costs(39);
        assertEquals(11, c.get("Totem"), 1e-9, "Totem: 15 × (1 − 20.2 %) = 11.97, rounded down: " + c);
        assertEquals(15, costs(0).get("Totem"), 1e-9, "no Intelligence: the tree's 30 − 10 − 5");
    }
}
