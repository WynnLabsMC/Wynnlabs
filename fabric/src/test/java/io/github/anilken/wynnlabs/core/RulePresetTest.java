package io.github.anilken.wynnlabs.core;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.advice.MarketSlots;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules as the regrouped settings show them: the Relaxed / Normal / Strict preset over nine
 * numbers, the three roll rules merged into one, and the Trade Market slot limit's own switch.
 */
class RulePresetTest {

    @AfterEach
    void reset() {
        Rules.use(new JsonObject());
    }

    @Test
    void normalIsEveryDefault() {
        Rules.use(new JsonObject());
        assertEquals(RuleEditor.NORMAL, RuleEditor.preset(), "no one's advice changes");
        for (String id : RuleEditor.PRESET_RULES) {
            RuleEditor.Rule r = RuleEditor.byId(id);
            assertNotNull(r, id);
            assertEquals(r.def(), RuleEditor.presetValue(RuleEditor.NORMAL, id), 1e-9, id);
            double[] range = RuleEditor.range(r);
            for (int p = 0; p < 3; p++) {
                double v = RuleEditor.presetValue(p, id);
                assertTrue(v >= range[0] && v <= range[1], id + " preset " + p + " is within the rule's range");
            }
        }
    }

    @Test
    void aPresetSetsAllNineAndAHandChangeMakesItCustom() {
        Rules.use(new JsonObject());
        RuleEditor.applyPreset(2);
        assertEquals(2, RuleEditor.preset());
        assertEquals(15, RuleEditor.value(RuleEditor.byId("min_gain")), 1e-9);
        assertEquals(2, Rules.num("tooLowLevels", 0), 1e-9);
        RuleEditor.step(RuleEditor.byId("min_gain"), 1);
        assertEquals(-1, RuleEditor.preset(), "Custom");
        RuleEditor.applyPreset(RuleEditor.NORMAL);
        assertEquals(RuleEditor.NORMAL, RuleEditor.preset());
        for (RuleEditor.Rule r : RuleEditor.RULES) {
            assertFalse(RuleEditor.changed(r), RuleEditor.id(r) + " back at its default");
        }
    }

    @Test
    void theRollRulesBecomeOne() {
        JsonObject saved = new JsonObject();
        JsonObject trade = new JsonObject();
        trade.addProperty("legendaryMinPercent", 70);
        trade.addProperty("anyMinPercent", 90);
        trade.addProperty("keepRollPercent", 85);
        saved.add("trade", trade);
        Rules.use(saved);
        JsonObject t = Rules.get().getAsJsonObject("trade");
        assertEquals(85, t.get("keepRollPercent").getAsInt(), "the threshold most players had is kept");
        assertFalse(t.has("legendaryMinPercent"));
        assertFalse(t.has("anyMinPercent"));
    }

    @Test
    void aRollRuleThatWasOffTakesTheAnyItemThreshold() {
        JsonObject saved = new JsonObject();
        JsonObject trade = new JsonObject();
        trade.addProperty("legendaryMinPercent", 80);
        trade.addProperty("anyMinPercent", 92);
        trade.addProperty("keepRollPercent", 0);
        saved.add("trade", trade);
        Rules.use(saved);
        assertEquals(92, Rules.sub("trade", "keepRollPercent", -1), 1e-9);
        // a file without the old keys is left alone (the API may still turn it off with 0)
        JsonObject now = new JsonObject();
        JsonObject t2 = new JsonObject();
        t2.addProperty("keepRollPercent", 0);
        now.add("trade", t2);
        Rules.use(now);
        assertEquals(0, Rules.sub("trade", "keepRollPercent", -1), 1e-9);
    }

    @Test
    void theSlotLimitHasItsOwnSwitchAndKeepsItsNumber() {
        JsonObject saved = new JsonObject();
        JsonObject trade = new JsonObject();
        trade.addProperty("marketSlots", 7);
        saved.add("trade", trade);
        Rules.use(saved);
        assertTrue(MarketSlots.limited(), "on by default");
        assertEquals(7, MarketSlots.slots());
        MarketSlots.setLimited(false);
        assertFalse(MarketSlots.limited());
        assertTrue(MarketSlots.slots() > 10_000, "no limit: every item fits");
        assertEquals(7, MarketSlots.limit(), "the number is kept while off");
        RuleEditor.reset();
        assertTrue(MarketSlots.limited(), "reset turns the limit back on");
    }
}
