package io.github.anilken.wynnlabs.build;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ability nodes' own damage (AbilityText) read from the game's trees (resources/trees,
 * Wynntils' data), the steady-state table on top (AbilityMechanics), and the cast rate that
 * follows the mana (Casting).
 */
class AbilityMechanicsTest {

    private static final List<String> WARRIOR = List.of("Bash", "Charge", "Uppercut", "War Scream");
    private static final List<String> MAGE = List.of("Heal", "Teleport", "Meteor", "Ice Snake");
    private static final List<String> ASSASSIN = List.of("Spin Attack", "Dash", "Multihit", "Smoke Bomb");
    private static final List<String> ARCHER = List.of("Arrow Bomb", "Escape", "Arrow Storm", "Arrow Shield");

    private static Map<String, AbilityText.Extra> read(String cls, List<String> spells) throws IOException {
        return AbilityText.read(TreeModelTest.tree(cls.toLowerCase(java.util.Locale.ROOT)), cls, spells);
    }

    @Test
    void nodesThatComeWithASpellAddToIt() throws IOException {
        Map<String, AbilityText.Extra> w = read("WARRIOR", WARRIOR);
        AbilityText.Extra kick = w.get("Flying Kick");
        assertEquals("Charge", kick.spell());
        assertEquals(4.0, sum(kick.conv()), 1e-9, "Flying Kick: 400% on each Charge");
        // Collide names Flying Kick, not a spell: it comes with Charge through it
        assertEquals("Charge", w.get("Collide").spell());
        // a burn: every 0.6 seconds for 3 s is 5 ticks of 50%
        assertEquals(2.5, sum(w.get("Flaming Uppercut").conv()), 1e-9);
        assertEquals("War Scream", w.get("Air Shout").spell());
        assertEquals(1, w.get("Air Shout").hits(), 1e-9, "Air Shout's 3 hits come from the table");
        assertNull(w.get("Cyclone"), "a vortex on its own clock is a summon or area's (TreeReading)");
    }

    @Test
    void replacementsAndMainAttackExtras() throws IOException {
        Map<String, AbilityText.Extra> a = read("ASSASSIN", ASSASSIN);
        assertTrue(a.get("Backstab").replace(), "Backstab makes Multihit a single hit");
        assertEquals("Multihit", a.get("Backstab").spell());
        Map<String, AbilityText.Extra> m = read("MAGE", MAGE);
        assertTrue(m.get("Pyrokinesis").main(), "Pyrokinesis is on the main attack");
        assertEquals("Teleport", m.get("Etheric Slash").spell());
        Map<String, AbilityText.Extra> r = read("ARCHER", ARCHER);
        assertEquals(3, r.get("Shrapnel Bomb").hits(), 1e-9, "each enemy is hit by three shrapnel");
        assertTrue(r.get("Phantom Ray").replace());
        assertEquals(12, r.get("Phantom Ray").hits(), 1e-9, "10 times a second for 1.2 s");
    }

    @Test
    void theTableAddsTheSteadyState() throws IOException {
        Map<String, AbilityText.Extra> w = read("WARRIOR", WARRIOR);
        AbilityMechanics.Sum none = AbilityMechanics.of(w, List.of("Bash"));
        assertTrue(none.isEmpty());
        AbilityMechanics.Sum monk = AbilityMechanics.of(w, List.of("Charge", "Flying Kick", "Collide", "Acceleration"));
        // Flying Kick 400% x 1.5 (Acceleration) and Collide 200% x 0.5 (half the kicks reach a wall)
        assertEquals(7.0, monk.of("Charge").stream().mapToDouble(e -> sum(e.conv())).sum(), 1e-9);
        AbilityMechanics.Sum fallen = AbilityMechanics.of(w, List.of("Bak'al's Grasp", "Enraged Blow", "Bloodlust"));
        assertEquals(80, fallen.raw(), 1e-9);
        assertEquals(1.55, fallen.damage(), 1e-9);
        assertEquals(1, fallen.passives().size(), "Bloodlust once every 20 s");
        Map<String, AbilityText.Extra> m = read("MAGE", MAGE);
        AbilityMechanics.Sum light = AbilityMechanics.of(m, List.of("Meteor", "Ophanim", "Augury"));
        assertTrue(light.replaced().contains("Meteor"));
        assertEquals(1, light.passives().size(), "Ophanim's orbs hit as summons do");
        assertEquals(3, light.passives().get(0).count(), "2 orbs and Augury's one more");
        assertEquals(1.2, sum(light.passives().get(0).conv()), 1e-9, "120% a hit");
        assertNotNull(AbilityMechanics.table().get("Marked"));
        assertFalse(AbilityMechanics.of(m, List.of("Lightweaver")).passives().isEmpty());
    }

    @Test
    void castsFollowTheMana() {
        double[] dmg = {100, 400};
        double[] cost = {5, 50};
        double[] each = {2, 2};
        // plenty of mana: the clicks are short, the biggest hit first
        assertArrayEquals(new double[] {0, 1.5}, Casting.allocate(dmg, cost, 1.5, 1000, each), 1e-9);
        // 10 mana a second: the 5-mana spell is the better buy, spammed up to the cap
        double[] x = Casting.allocate(dmg, cost, 1.5, 10, each);
        assertEquals(10, x[0] * 5 + x[1] * 50, 1e-9, "the mana is spent exactly");
        assertEquals(1.5, x[0] + x[1], 1e-9);
        assertTrue(x[0] > x[1]);
        // a cooldown caps one spell
        double[] y = Casting.allocate(dmg, cost, 1.5, 1000, new double[] {2, 0.2});
        assertEquals(0.2, y[1], 1e-9);
        assertEquals(1.3, y[0], 1e-9);
    }

    private static double sum(double[] c) {
        double t = 0;
        for (double v : c) {
            t += v;
        }
        return t;
    }
}
