package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The hits the tree's nodes give a spell or the main attack (HitCounts), with the per-hit
 * lowerings that come with them (TreeBonuses), against WynnBuilder's tree data: Fallen's Bash
 * hits four times at 150 %, Uppercut takes Fireworks and Comet, Arrow Storm with Windstorm and
 * Nimble String is two streams of 16 arrows at 20 %, Multihit 11 hits, three Smoke Bombs, a
 * Flaming Tongue Uproot six hits at 110 %; Double and Triple Shots make the main attack 3 × 50 %,
 * Double Slice 2 × 60 %. And a link's powders: each tier as written, a second element converting
 * from the weapon's own neutral.
 */
class HitCountsTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    /** The class's spells (rules.model.spells from its tree) with the overlay for {@code nodes}. */
    private static TreeModel.Overlay setUp(Klass k, List<String> nodes) throws IOException {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("geardb.json")), "no shaman-44 fixture");
        List<AbilityNode> tree = TreeModelTest.tree(k.name().toLowerCase(Locale.ROOT));
        FixtureState.load(dir).as(k).withTree(tree, nodes).install("en");
        JsonObject rules = Rules.get().deepCopy();
        JsonObject model = rules.has("model") ? rules.getAsJsonObject("model") : new JsonObject();
        JsonArray spells = ClassProfiles.spells();
        model.add("spells", spells);
        rules.add("model", model);
        Rules.use(rules);
        List<String> names = TreeModel.spellNames(tree, k.name());
        return TreeReading.overlay(106, TreeModel.read(tree, k.name(), names), names, nodes, 1, 2.5,
                TreeBonuses.read(tree, k.name(), names, List.of()), SourceText.read(tree, k.name(), names));
    }

    private static JsonObject spell(TreeModel.Overlay o, String name) {
        JsonArray rs = Rules.get().getAsJsonObject("model").getAsJsonArray("spells");
        for (JsonElement e : TreeModel.with(o, () -> TreeModel.spells(rs))) {
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) {
                return e.getAsJsonObject();
            }
        }
        throw new AssertionError("no " + name);
    }

    private static double conv(JsonObject s, String el) {
        JsonObject c = s.getAsJsonObject("conv");
        return c.has(el) ? c.get(el).getAsDouble() : 0;
    }

    private static double total(JsonObject s) {
        double t = 0;
        for (String el : Damage.ELEMENTS) {
            t += conv(s, el);
        }
        return t;
    }

    @Test
    void fallenBashHitsFourTimesAndUppercutTakesItsParts() throws IOException {
        TreeModel.Overlay o = setUp(Klass.WARRIOR, List.of("Bash", "Charge", "Uppercut", "War Scream", "Double Bash",
                "Quadruple Bash", "Fireworks", "Comet"));
        JsonObject bash = spell(o, "Bash");
        assertEquals(4, bash.get("hits").getAsDouble(), 1e-9);
        assertEquals(4 * 1.2, conv(bash, "neutral"), 1e-6);    // 170 - 30 - 20 % a hit
        assertEquals(4 * 0.3, conv(bash, "earth"), 1e-6);
        // 340 % and Fireworks' 100 %, and Comet's (it names Fireworks, not Uppercut)
        assertEquals(5.4, total(spell(o, "Uppercut")), 1e-6);
        // without the nodes: one bash at 200 %
        TreeModel.Overlay plain = setUp(Klass.WARRIOR, List.of("Bash", "Charge", "Uppercut", "War Scream"));
        assertEquals(2.0, total(spell(plain, "Bash")), 1e-6);
    }

    @Test
    void arrowStormStreamsAndTheMainAttacksArrows() throws IOException {
        TreeModel.Overlay o = setUp(Klass.ARCHER, List.of("Arrow Bomb", "Escape", "Arrow Storm", "Arrow Shield",
                "Windstorm", "Nimble String", "Double Shots", "Triple Shots"));
        JsonObject storm = spell(o, "Arrow Storm");
        assertEquals(32, storm.get("hits").getAsDouble(), 1e-9);
        assertEquals(32 * 0.2, total(storm), 1e-6);              // 40 - 10 - 10 % an arrow
        double main = TreeModel.with(o, () -> TreeModel.adjust("mainAttackClassMult", 1.0));
        assertEquals(1.5, main, 1e-9);                            // 3 arrows at 50 %
        TreeModel.Overlay two = setUp(Klass.ARCHER, List.of("Arrow Storm", "Double Shots"));
        assertEquals(1.2, TreeModel.with(two, () -> TreeModel.adjust("mainAttackClassMult", 1.0)), 1e-9);
    }

    @Test
    void assassinHitsBombsAndDoubleSlice() throws IOException {
        TreeModel.Overlay o = setUp(Klass.ASSASSIN, List.of("Spin Attack", "Dash", "Multihit", "Smoke Bomb", "Mutilate",
                "Wall of Smoke", "Double Spin", "Double Slice"));
        assertEquals(11, spell(o, "Multihit").get("hits").getAsDouble(), 1e-9);
        JsonObject smoke = spell(o, "Smoke Bomb");
        assertEquals(30, smoke.get("hits").getAsDouble(), 1e-9);
        assertEquals(30 * 0.25, total(smoke), 1e-6);             // 35 - 10 % a tick, three bombs
        assertEquals(2 * 1.1, total(spell(o, "Spin Attack")), 1e-6);
        // the profile's Acrobat guess (1.2) gives way to the tree: Double Slice taken or not
        assertEquals(1.2, TreeModel.with(o, () -> TreeModel.adjust("mainAttackClassMult", 1.2)), 1e-9);
        TreeModel.Overlay none = setUp(Klass.ASSASSIN, List.of("Spin Attack", "Multihit"));
        assertEquals(1.0, TreeModel.with(none, () -> TreeModel.adjust("mainAttackClassMult", 1.2)), 1e-9);
    }

    @Test
    void flamingTongueUprootHitsSixTimes() throws IOException {
        TreeModel.Overlay o = setUp(Klass.SHAMAN, List.of("Totem", "Haul", "Uproot", "Aura", "Flaming Tongue",
                "Lashing Lance", "Scourge", "Hand of the Shaman"));
        JsonObject up = spell(o, "Uproot");
        assertEquals(6, up.get("hits").getAsDouble(), 1e-9);
        assertEquals(6 * 1.1, total(up), 1e-6);                  // 130 - 35 + 15 % a hit
        // five beams where the relik has three
        assertEquals(1.05 * 5 / 3, TreeModel.with(o, () -> TreeModel.adjust("mainAttackClassMult", 1.05)), 1e-9);
    }

    @Test
    void aLinksPowdersKeepTheirTiersAndConvertFromTheWeaponsNeutral() throws IOException {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("geardb.json")), "no shaman-44 fixture");
        FixtureState.load(dir).install("en");
        ItemData longinus = GameState.current().allGear().filter(i -> i.name().equals("Longinus")).findFirst().orElse(null);
        assertNotNull(longinus);
        assertEquals(List.of(new Pieces.Powder("thunder", 6), new Pieces.Powder("earth", 7)), Pieces.linkPowders("T6E7"));
        // Earth I (17 %) and Thunder I (9 %) both from the neutral the weapon has
        Damage.Piece p = Damage.piece(longinus, "E1T1");
        double perHit = 484 / 4.3;
        double neutral = perHit * (1 - 0.17 - 0.09);
        double earth = perHit * 0.17 + 4.5;
        double thunder = perHit * 0.09 + 4.5;
        double sum = neutral + earth + thunder;
        assertEquals(neutral / sum, p.weaponShares().get("neutral"), 1e-6);
        assertEquals(sum * 4.3, p.baseDps(), 1e-6);
        // Tier VII as the link has it, where the assumed powders stop at VI
        Damage.Piece seven = Damage.piece(longinus, "E7E7E7");
        Damage.Piece six = Damage.piece(longinus, "E6E6E6");
        // (both convert all the neutral: the flat damage tells them apart, 13 a VII and 11.5 a VI)
        assertEquals(3 * (13 - 11.5) * 4.3, seven.baseDps() - six.baseDps(), 1e-6);
    }
}
