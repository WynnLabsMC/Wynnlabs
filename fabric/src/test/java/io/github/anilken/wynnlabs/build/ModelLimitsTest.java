package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.Rules;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model's limits: walk speed levels off; rules from before 6 lose the mana steal discount
 * and what your swaps taught, a weight set by hand staying.
 */
class ModelLimitsTest {

    @AfterEach
    void reset() {
        Rules.use(new JsonObject());
    }

    private static JsonObject model(String json) {
        JsonObject o = new JsonObject();
        o.add("model", com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        return o;
    }

    @Test
    void walkSpeedLevelsOff() {
        Rules.use(new JsonObject());
        assertTrue(Damage.speedFactor(146) < 1.35, "factor at +146%: " + Damage.speedFactor(146));
        assertTrue(Damage.speedFactor(161) / Damage.speedFactor(146) < 1.03, "+15% at +146% moves the score too much");
        double slope = (Damage.speedFactor(1) - Damage.speedFactor(0)) * 100;
        assertEquals(0.3, slope, 0.01, "slope at 0 is speedWeight");
        assertTrue(Damage.speedFactor(-20) < 1, "a penalty still costs");
    }

    @Test
    void manaStealDiscountAndTaughtWeightsGo() {
        Rules.use(model("{\"survivalModel\": 5, \"manaStealFactor\": 0.33, \"nudgeCap\": 1.5, \"capsApplied\": true,"
                + " \"speedWeight\": 0.45, \"xpWeight\": 1.15, \"survivalWeight\": 0.3}"));
        JsonObject m = Rules.get().getAsJsonObject("model");
        assertFalse(m.has("manaStealFactor"), "mana steal counts whole");
        assertFalse(m.has("nudgeCap"));
        assertFalse(m.has("capsApplied"));
        assertEquals(0.3, Rules.sub("model", "speedWeight", 0), 1e-9);
        assertEquals(1.0, Rules.sub("model", "xpWeight", 0), 1e-9);
        assertEquals(Setup.weights("DAMAGE")[0], Rules.sub("model", "survivalWeight", 0), 1e-9);
        assertEquals(Setup.SURVIVAL_MODEL, Rules.sub("model", "survivalModel", 0), 1e-9);

        Rules.use(model("{\"survivalModel\": 5, \"speedWeight\": 1.0, \"byHand\": [\"speedWeight\"]}"));
        assertEquals(1.0, Rules.sub("model", "speedWeight", 0), 1e-9, "set by hand: stays");
        Rules.use(model("{\"survivalModel\": " + Setup.SURVIVAL_MODEL + ", \"speedWeight\": 0.45}"));
        assertEquals(0.45, Rules.sub("model", "speedWeight", 0), 1e-9, "only once");
    }
}
