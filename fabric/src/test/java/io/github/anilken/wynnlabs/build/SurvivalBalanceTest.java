package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Rules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Damage builds are not buried under defence: health far above the floor stops paying for the
 * goals that fight for damage, a health drain costs a share of your own health, and a negative
 * regen is applied the way WynnBuilder does.
 */
class SurvivalBalanceTest {

    @AfterEach
    void reset() {
        Rules.use(new JsonObject());
    }

    @Test
    void negativeRegenShrinksWithAPositivePercent() {
        assertEquals(150, Damage.healthRegen(100, 50), 1e-9);
        assertEquals(-50, Damage.healthRegen(-100, 50), 1e-9, "a positive % shrinks a drain");
        assertEquals(0, Damage.healthRegen(-100, 150), 1e-9, "and never turns it into a heal");
        assertEquals(-150, Damage.healthRegen(-100, -50), 1e-9, "a negative % deepens it");
        // the bug this replaces: -1050 raw with -146 % read as +483 regen
        assertTrue(Damage.healthRegen(-1050, -146) < -1050);
    }

    @Test
    void healthPastTheKneeLevelsOff() {
        double knee = 30000;
        assertEquals(20000, Damage.softCap(20000, knee), 1e-9);
        assertEquals(knee, Damage.softCap(knee, knee), 1e-9);
        // no kink at the knee: the slope leaving it is 1
        assertEquals(1, (Damage.softCap(knee + 1, knee) - knee), 1e-3);
        // and it never passes 1.5 × the knee
        assertTrue(Damage.softCap(10 * knee, knee) < 1.5 * knee);
        assertTrue(Damage.softCap(3 * knee, knee) > Damage.softCap(2 * knee, knee), "still rising");
        assertEquals(1e6, Damage.softCap(1e6, 0), 1e-9, "0 turns it off");
    }

    @Test
    void onlyTheGoalsThatFightForDamageCapHealth() {
        int cap = List.of(keysOf()).indexOf("ehpSoftCap");
        for (String g : List.of("SAFE", "TANK", "HEALER")) {
            assertEquals(0, Setup.weights(g)[cap], 1e-9, g);
        }
        for (String g : List.of("DAMAGE", "BALANCED", "BOSS", "MOBBING", "XP", "LOOT")) {
            assertTrue(Setup.weights(g)[cap] >= 1.5, g);
        }
        assertTrue(Setup.weights("BALANCED")[cap] > Setup.weights("DAMAGE")[cap]);
    }

    @Test
    void aDrainCostsAShareOfYourOwnHealth() {
        Rules.use(new JsonObject());
        assertEquals(1, Damage.drainFactor(0), 1e-9);
        double small = Damage.drainFactor(250 / 20000.0);
        double big = Damage.drainFactor(2500 / 13000.0);
        assertTrue(small > 0.9 && small < 1, "a -250 drain on 20k health is a few %: " + small);
        assertTrue(big < small && big > 0.6, "Nether's Scar's drain is a real but bounded cost: " + big);
        assertTrue(Damage.drainFactor(100) >= 0.5, "never under the floor");
    }

    @Test
    void oldRulesGetTheSoftCapOnce() {
        JsonObject old = JsonParser.parseString("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 8,"
                + " \"negativeRegenWeight\": 2.0}}").getAsJsonObject();
        Setup.upgrade(old);
        JsonObject m = old.getAsJsonObject("model");
        assertEquals(Setup.weights("DAMAGE")[List.of(keysOf()).indexOf("ehpSoftCap")], m.get("ehpSoftCap").getAsDouble(), 1e-9);
        assertFalse(m.has("negativeRegenWeight"));
        JsonObject tank = JsonParser.parseString("{\"goalPreset\": \"TANK\", \"model\": {\"survivalModel\": 8}}").getAsJsonObject();
        Setup.upgrade(tank);
        assertEquals(0, tank.getAsJsonObject("model").get("ehpSoftCap").getAsDouble(), 1e-9);
    }

    private static String[] keysOf() {
        return new String[] {"survivalWeight", "regenWeight", "manaWeight", "healWeight", "damageWeight",
            "survivalPower", "goalXp", "goalSpeed", "goalSpeedRoom", "burstWeight", "lootWeight", "ehpFloorScale",
            "xpLog", "ehpSoftCap"};
    }
}
