package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.build.roadmap.RoadmapLocksTest;
import io.github.anilken.wynnlabs.build.roadmap.SharedRoadmaps;
import io.github.anilken.wynnlabs.game.Expected;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tuning strip on the recorded Shaman: Balanced with nothing on and no budget is the road map
 * as before, Damage and Tanky lean the sets their way, and a budget keeps a piece that costs more
 * out of every milestone.
 */
class TuningTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Roadmap.Computed run(Tuning.Choice t) {
        return Roadmap.compute(new Roadmap.Inputs(List.of(), t, false, 0), null);
    }

    /** {@code run(Tuning.Choice.PLAIN)} on the recorded Shaman, which it installs: worked out once (SharedRoadmaps). */
    private static Roadmap.Computed plain() throws Exception {
        return SharedRoadmaps.shamanComputed();
    }

    @Test
    void balancedWithNoBudgetIsTodaysMap() throws Exception {
        // the J menu's map (Roadmap.build, as Roadmap.json serves it) against the engine's with
        // Balanced and no budget, each built once (SharedRoadmaps)
        java.nio.file.Path dir = RoadmapLocksTest.shaman();
        List<String> diff = Expected.check(dir, "roadmap.en.json", SharedRoadmaps.shamanJson(), Expected::json);
        assertTrue(diff.isEmpty(), String.join("\n", diff));
        assertEquals(RoadmapLocksTest.sets(SharedRoadmaps.shaman()), RoadmapLocksTest.sets(plain().steps()));
        assertTrue(Tuning.Choice.PLAIN.plain() && Tuning.Choice.PLAIN.key().isEmpty());
        // outside a tuned run every weight is as the rules say
        assertEquals(1.0, Tuning.weight("survivalWeight"));
        assertEquals(1.0, Tuning.with(Tuning.Choice.PLAIN, () -> Tuning.weight("damageWeight")));
    }

    @Test
    void damageAndTankyLeanTheSets() throws Exception {
        FixtureState.load(RoadmapLocksTest.shaman()).install("en");
        List<Roadmap.Step> dmg = run(Tuning.Choice.PLAIN.withFocus(Tuning.DAMAGE)).steps();
        List<Roadmap.Step> tank = run(Tuning.Choice.PLAIN.withFocus(Tuning.TANKY)).steps();
        assertNotEquals(RoadmapLocksTest.sets(dmg), RoadmapLocksTest.sets(tank), "the two foci pick other sets");
        double dmgEhp = 0;
        double tankEhp = 0;
        double dmgDps = 0;
        double tankDps = 0;
        for (int i = 0; i < Math.min(dmg.size(), tank.size()); i++) {
            Damage.Result a = dmg.get(i).set().result();
            Damage.Result b = tank.get(i).set().result();
            dmgEhp += a.ehp();
            tankEhp += b.ehp();
            dmgDps += a.factors()[0];
            tankDps += b.factors()[0];
        }
        Fixtures.note(String.format(java.util.Locale.ROOT, "damage focus: dps %.0f ehp %.0f; tanky: dps %.0f ehp %.0f",
                dmgDps, dmgEhp, tankDps, tankEhp));
        assertTrue(tankEhp > dmgEhp, "Tanky wears more effective health");
        assertTrue(dmgDps > tankDps, "Damage deals more");
        // Mana and Speed weigh their stats more inside a run only
        Tuning.Choice ms = Tuning.Choice.PLAIN.withMana(true).withSpeed(true);
        assertTrue(Tuning.with(ms, () -> Tuning.weight("costCutLean")) > 1.5);
        assertTrue(Tuning.with(ms, () -> Tuning.weight("manaRegenLean")) > 1.5);
        assertTrue(Tuning.with(ms, () -> Tuning.weight("speedWeight")) > 1.5);
    }

    @Test
    void aBudgetLeavesOutWhatCostsMore() throws Exception {
        List<Roadmap.Step> plain = plain().steps();
        GameState state = GameState.current();
        // the dearest priced pick of the plain map; a powder row is the weapon you have, and only
        // its powders are bought (its piece costs nothing under a budget)
        String dear = null;
        double price = 0;
        for (Roadmap.Step s : plain) {
            for (Roadmap.Gear g : s.gear()) {
                if (g.slot().startsWith("POWDER:")) {
                    continue;
                }
                double c = Roadmap.cost(state, g.info());
                if (c > price) {
                    price = c;
                    dear = g.info().name();
                }
            }
        }
        assertTrue(dear != null && price > 2, "a priced pick to leave out");
        long budget = (long) Math.floor(price / 2);
        Roadmap.Computed c = run(new Tuning.Choice(Tuning.BALANCED, false, false, budget));
        for (Roadmap.Step s : c.steps()) {
            for (Roadmap.Gear g : s.gear()) {
                if (g.slot().startsWith("POWDER:")) {
                    continue;
                }
                assertNotEquals(dear, g.info().name(), "Lv " + s.level() + ": " + dear + " costs " + price + ", over " + budget);
                double cost = Roadmap.cost(state, g.info());
                assertTrue(cost >= 0 && cost <= budget, g.info().name() + " costs " + cost);
            }
        }
        assertFalse(c.budgetCut().isEmpty(), "the map says where the budget left something out");
    }

    @Test
    void theBudgetMenuSetsAnyStepEitherWay() {
        Tuning.Choice c = new Tuning.Choice(Tuning.BALANCED, true, false, 0);
        for (long b : Tuning.BUDGETS) {
            Tuning.Choice set = c.withBudget(b);
            assertEquals(b, set.budget());
            assertEquals(c.focus(), set.focus(), "the rest of the strip stays");
            assertTrue(set.mana());
        }
        assertEquals(640, c.withBudget(4096).withBudget(640).budget(), "down as well as up");
    }

    /** The Priority menu's leanings are saved by name; an older file's Mana switch reads as spell cost and mana. */
    @Test
    void leaningsSaveAndOldFilesStillRead() {
        com.google.gson.JsonObject old = com.google.gson.JsonParser.parseString(
                "{\"focus\":2,\"mana\":true,\"speed\":false,\"budget\":640}").getAsJsonObject();
        Tuning.Choice c = Tuning.Choice.read(old);
        assertEquals(Tuning.TANKY, c.focus());
        assertTrue(c.leans(Tuning.LEAN_COST) && c.leans(Tuning.LEAN_MANA), "the old Mana: spell cost and mana");
        assertFalse(c.leans(Tuning.LEAN_SUSTAIN) || c.leans(Tuning.LEAN_SPEED));
        assertEquals(640, c.budget());

        Tuning.Choice picked = Tuning.Choice.PLAIN.withLean(Tuning.LEAN_COST, true).withLean(Tuning.LEAN_SUSTAIN, true);
        com.google.gson.JsonObject j = new com.google.gson.JsonObject();
        picked.write(j);
        assertEquals("[\"cost\",\"sustain\"]", j.get("lean").toString());
        assertFalse(j.get("mana").getAsBoolean(), "an older version reads Mana off: only the cost is on");
        assertEquals(picked, Tuning.Choice.read(j), "read back as written");
        assertEquals(new Tuning.Choice(Tuning.BALANCED, true, true, 0),
                Tuning.Choice.read(com.google.gson.JsonParser.parseString("{\"mana\":true,\"speed\":true}").getAsJsonObject()));
        assertTrue(Tuning.Choice.read(new com.google.gson.JsonObject()).lean() == 0, "nothing saved: nothing on");
    }
}
