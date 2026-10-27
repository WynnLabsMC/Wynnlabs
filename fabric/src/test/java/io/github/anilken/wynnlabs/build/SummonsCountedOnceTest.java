package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.coach.Coach;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.AbilityNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The ability tree's summons (TreeModel) and the damage meter's summon calibration (Coach) on
 * one model: the shaman-44 recording as a Lv 44 Summoner with the game's Shaman tree. The
 * Totem's ticks are counted once (a lasting spell, never a tree summon too), the damage meter
 * compares the background with the very summons the build is scored with, and the calibration
 * it writes corrects them once: measured again, it stays where it is.
 */
class SummonsCountedOnceTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Path shaman44() {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        return dir;
    }

    /** The recording as a Lv 44 Summoner with the class profile read from the tree, its plan made. */
    static Build.Plan summoner() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        List<String> have = TreeModelTest.summonerAt44(nodes);
        FixtureState.load(shaman44()).withTree(nodes, have).install("en");
        JsonObject tree = new JsonObject();
        tree.addProperty("archetype", "Summoner");
        JsonObject pick = new JsonObject();
        pick.add("tree", tree);
        Rules.update(pick);
        Rules.update(ClassProfiles.rulesFor("Summoner", false));
        Build.Plan plan = Build.compute();
        Build.use(plan);
        assertNotNull(plan.tree(), "the plan is scored with the tree you have");
        assertTrue(plan.tree().passiveTotal() > 0, "Puppet Master at 44");
        return plan;
    }

    /** A build's pieces but the weapon, in the order the plan sums them. */
    static List<Damage.Piece> armour(java.util.Map<String, Damage.Piece> build) {
        List<Damage.Piece> out = new java.util.ArrayList<>();
        build.forEach((k, v) -> {
            if (!k.equals("WEAPON") && v != null) {
                out.add(v);
            }
        });
        return out;
    }

    static Damage.SpellPart totem(Damage.Breakdown b) {
        Damage.SpellPart found = null;
        int n = 0;
        for (Damage.SpellPart sp : b.spells()) {
            if (sp.name().equals("Totem")) {
                found = sp;
                n++;
            }
        }
        assertEquals(1, n, "one Totem in the spells");
        return found;
    }

    @Test
    void theTotemIsCountedOnce() throws IOException {
        Build.Plan plan = summoner();
        // the tree reads no summon out of the Totem's own node: its ticks are the spell's
        List<TreeModel.Read> reads = TreeModel.run().reads;
        for (TreeModel.Read r : reads) {
            if (r.name().equals("Totem")) {
                assertNull(r.conv(), "the Totem is no tree summon");
            }
        }
        Damage.Breakdown with = plan.currentDetail(null, null).breakdown();
        Damage.SpellPart totem = totem(with);
        assertTrue(totem.lasting(), "the Totem lasts its 30 s alongside the casts");
        assertTrue(totem.duration() > 0);

        // the same build with the Totem left out of the spells: the summons lose exactly its ticks
        JsonObject model = Rules.get().getAsJsonObject("model");
        JsonArray spells = model.getAsJsonArray("spells");
        JsonArray noTotem = new JsonArray();
        spells.forEach(e -> {
            if (!e.getAsJsonObject().get("name").getAsString().equals("Totem")) {
                noTotem.add(e.deepCopy());
            }
        });
        JsonObject m2 = new JsonObject();
        m2.add("spells", noTotem);
        JsonObject change = new JsonObject();
        change.add("model", m2);
        Rules.update(change);
        Damage.Breakdown without = plan.currentDetail(null, null).breakdown();
        double puppets = without.summonTotal();
        assertTrue(puppets > 0, "the puppets are still there");
        assertEquals(puppets + totem.perCast() / totem.duration(), with.summonTotal(), 1e-6 * with.summonTotal(),
                "summons = the tree's puppets + the Totem's ticks, each once");
    }
}
