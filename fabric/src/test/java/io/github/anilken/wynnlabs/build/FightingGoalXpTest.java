package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.anilken.wynnlabs.core.Rules;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XP weighs little on the fighting goals: Damage and Boss scale the XP weight by 0.3,
 * rules saved before that get it once (a value set by hand stays), and an XP weight feedback
 * raised before goes.
 */
class FightingGoalXpTest {

    private static final int GOAL_XP = 6;

    @AfterEach
    void reset() {
        Rules.use(new JsonObject());
    }

    private static JsonObject rules(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void fightingGoalsWeighXpLittle() {
        assertEquals(Setup.DAMAGE_XP, Setup.weights("DAMAGE")[GOAL_XP], 1e-9, "the Damage goal counts XP a little");
        assertTrue(Setup.DAMAGE_XP > 0 && Setup.DAMAGE_XP < Setup.weights("SAFE")[GOAL_XP]);
        assertEquals(0.3, Setup.weights("BOSS")[GOAL_XP], 1e-9);
        // Safe is a survival goal that still levels; the levelling goals keep theirs
        assertEquals(1, Setup.weights("SAFE")[GOAL_XP], 1e-9);
        assertEquals(3, Setup.weights("XP")[GOAL_XP], 1e-9);
    }

    @Test
    void oldRulesGetTheGoalXpOnceAndHandValuesStay() {
        JsonObject old = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 2, \"goalXp\": 1, \"xpWeight\": 1.5}}");
        Setup.upgrade(old);
        JsonObject m = old.getAsJsonObject("model");
        assertEquals(Setup.DAMAGE_XP, m.get("goalXp").getAsDouble(), 1e-9);
        assertEquals(1, m.get("xpWeight").getAsDouble(), 1e-9, "an old XP nudge goes with the step to 4");
        assertEquals(Setup.SURVIVAL_MODEL, m.get("survivalModel").getAsInt());
        // once: a goal factor changed after the upgrade stays
        m.addProperty("goalXp", 0.8);
        Setup.upgrade(old);
        assertEquals(0.8, m.get("goalXp").getAsDouble(), 1e-9);

        JsonObject boss = rules("{\"goalPreset\": \"BOSS\", \"model\": {\"survivalModel\": 2, \"goalXp\": 0.5}}");
        Setup.upgrade(boss);
        assertEquals(0.3, boss.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);

        JsonObject hand = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 2, \"goalXp\": 1, \"byHand\": [\"goalXp\"]}}");
        Setup.upgrade(hand);
        assertEquals(1, hand.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);
    }

    @Test
    void theStepFromTwoKeepsItsSurvivalModel() {
        // rules at 2 already have the survival model of 2: only the goal XP factor is new, and
        // the summon calibration was fitted against summons that never died
        JsonObject two = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 2, \"goalXp\": 1,"
                + " \"survivalWeight\": 0.33, \"survivalPower\": 0.4, \"ehpFloorScale\": 1.2, \"xpLog\": 0,"
                + " \"summonCalibration\": 3.5}}");
        Setup.upgrade(two);
        JsonObject m = two.getAsJsonObject("model");
        assertEquals(Setup.DAMAGE_XP, m.get("goalXp").getAsDouble(), 1e-9);
        // a survival weight your swaps raised comes from the goal again (6)
        assertEquals(Setup.weights("DAMAGE")[0], m.get("survivalWeight").getAsDouble(), 1e-9);
        assertEquals(0.4, m.get("survivalPower").getAsDouble(), 1e-9);
        assertEquals(1.2, m.get("ehpFloorScale").getAsDouble(), 1e-9);
        assertEquals(0, m.get("xpLog").getAsDouble(), 1e-9);

        // below 2 the whole list is reset as before
        JsonObject one = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 1, \"survivalWeight\": 0.33}}");
        Setup.upgrade(one);
        assertEquals(Setup.weights("DAMAGE")[0], one.getAsJsonObject("model").get("survivalWeight").getAsDouble(), 1e-9);
    }

    @Test
    void anOldXpNudgeGoesOnTheFightingGoals() {
        // feedback raised the XP weight before the fighting goals stopped it: from 3 on it is reset
        for (String goal : List.of("DAMAGE", "BOSS", "SAFE")) {
            JsonObject r = rules("{\"goalPreset\": \"" + goal + "\", \"model\": {\"survivalModel\": 3,"
                    + " \"goalXp\": 0.3, \"xpWeight\": 1.5, \"speedWeight\": 0.45, \"summonCalibration\": 0.15}}");
            Setup.upgrade(r);
            JsonObject m = r.getAsJsonObject("model");
            assertEquals(1, m.get("xpWeight").getAsDouble(), 1e-9, goal);
            assertEquals(Setup.SURVIVAL_MODEL, m.get("survivalModel").getAsInt());
            // the wave-1 Damage factor 0.3 becomes the goal's own (DAMAGE_XP); Boss and Safe keep
            // theirs; the walk speed nudge goes too (6) and the summon calibration stays
            assertEquals(goal.equals("DAMAGE") ? Setup.DAMAGE_XP : 0.3, m.get("goalXp").getAsDouble(), 1e-9, goal);
            assertEquals(0.3, m.get("speedWeight").getAsDouble(), 1e-9, goal);
            assertEquals(0.15, m.get("summonCalibration").getAsDouble(), 1e-9, goal);
            // once: a weight raised again later stays
            m.addProperty("xpWeight", 1.2);
            Setup.upgrade(r);
            assertEquals(1.2, m.get("xpWeight").getAsDouble(), 1e-9, goal);
        }
        // a levelling goal too: nothing is learned from your swaps any more (6)
        JsonObject xp = rules("{\"goalPreset\": \"XP\", \"model\": {\"survivalModel\": 3, \"goalXp\": 3, \"xpWeight\": 1.5}}");
        Setup.upgrade(xp);
        assertEquals(1, xp.getAsJsonObject("model").get("xpWeight").getAsDouble(), 1e-9);
        // a weight set by hand stays
        JsonObject hand = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 3, \"xpWeight\": 1.5,"
                + " \"byHand\": [\"xpWeight\"]}}");
        Setup.upgrade(hand);
        assertEquals(1.5, hand.getAsJsonObject("model").get("xpWeight").getAsDouble(), 1e-9);
        // a Damage goal factor other than the wave-1 0.3, or one set by hand, stays
        JsonObject other = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 3, \"goalXp\": 0.5}}");
        Setup.upgrade(other);
        assertEquals(0.5, other.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);
        JsonObject handXp = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 3, \"goalXp\": 0.3,"
                + " \"byHand\": [\"goalXp\"]}}");
        Setup.upgrade(handXp);
        assertEquals(0.3, handXp.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);
    }

    @Test
    void theDamageGoalCountsXpWhileLeveling() {
        // 10: the Damage goal's 0 becomes its small XP factor; an XP switch turned off by hand stays off
        JsonObject nine = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 9, \"goalXp\": 0}}");
        Setup.upgrade(nine);
        assertEquals(Setup.DAMAGE_XP, nine.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);
        JsonObject off = rules("{\"goalPreset\": \"DAMAGE\", \"model\": {\"survivalModel\": 9, \"goalXp\": 0,"
                + " \"byHand\": [\"goalXp\"]}}");
        Setup.upgrade(off);
        assertEquals(0, off.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);
        JsonObject tank = rules("{\"goalPreset\": \"TANK\", \"model\": {\"survivalModel\": 9, \"goalXp\": 0}}");
        Setup.upgrade(tank);
        assertEquals(0, tank.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);
    }
}
