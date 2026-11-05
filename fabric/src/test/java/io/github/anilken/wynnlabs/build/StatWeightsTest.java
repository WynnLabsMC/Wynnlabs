package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.model.StatRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A roll's place in its range: the higher, better end 1, a cost cut kept positive. */
class StatWeightsTest {

    @Test
    void aRollSitsInItsRangeWithTheBetterEndAtOne() {
        assertEquals(0.5, StatWeights.roll(25, new StatRange("airDamage", "Air Damage", "%", false, 10, 40)), 1e-9);
        assertEquals(1.0, StatWeights.roll(40, new StatRange("airDamage", "Air Damage", "%", false, 10, 40)), 1e-9);
        // a cost cut is kept positive ("-25% Aura Cost" is 25): the biggest cut is the best roll
        assertEquals(1.0, StatWeights.roll(25, new StatRange("3rdSpellCost", "Aura Cost", "%", true, 8, 25)), 1e-9);
        assertEquals(0.0, StatWeights.roll(8, new StatRange("3rdSpellCost", "Aura Cost", "%", true, 8, 25)), 1e-9);
        // a negative stat rolled from its worst to its least bad
        assertEquals(0.75, StatWeights.roll(-30, new StatRange("healthRegen", "Health Regen", "%", false, -90, -10)), 1e-9);
    }
}
