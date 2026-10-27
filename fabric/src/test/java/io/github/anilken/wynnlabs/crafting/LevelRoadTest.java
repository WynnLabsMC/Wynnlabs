package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelRoadTest {

    private static CraftData.Recipe recipe(int min, int xp, String m1, String m2) {
        return new CraftData.Recipe("ring" + min, "ring", "jeweling", min, min + 3,
                List.of(new CraftData.Material(m1, m1, 2), new CraftData.Material(m2, m2, 2)), null, null, null, null, xp);
    }

    /** Ring 10–19 on Granite Gem and Trout Oil (35 XP), 20+ on Gold Gem and Salmon Oil (70 XP). */
    private static final List<CraftData.Recipe> RECIPES = List.of(
            recipe(10, 35, "Granite Gem", "Trout Oil"),
            recipe(20, 70, "Gold Gem", "Salmon Oil"));

    private static final Map<String, LevelRoad.Source> SOURCES = Map.of(
            "Granite Gem", new LevelRoad.Source("mining", 10, "Granite"),
            "Trout Oil", new LevelRoad.Source("fishing", 10, "Trout"),
            "Gold Gem", new LevelRoad.Source("mining", 20, "Gold"),
            "Salmon Oil", new LevelRoad.Source("fishing", 20, "Salmon"));

    private static LevelRoad.Road road(LevelRoad.Pref pref, Map<String, LevelRoad.Route> routes, Set<String> ticked, double mult) {
        // XP 200 of 1000 at 18: 800, 1000, then 2000 for level 20 (ProfessionPlan's estimate: the ring XP doubles)
        return LevelRoad.build(new LevelRoad.Inputs(RECIPES, "JEWELING", "ring", 18, 21, 200, 1000,
                n -> n.equals("Granite Gem") ? 30 : 0, SOURCES::get,
                p -> p.equals("mining") ? 5 : 40,                       // mining too low for Granite
                n -> n.equals("Trout Oil") ? Long.valueOf(1) : n.equals("Salmon Oil") ? Long.valueOf(2) : null, pref, routes, ticked, mult));
    }

    @Test
    void bandsFollowTheMaterialsAndStepsComeInOrder() {
        LevelRoad.Road r = road(LevelRoad.Pref.GATHER, Map.of(), Set.of(), 1);
        assertEquals(2, r.bands().size());
        assertEquals(18, r.bands().get(0).from());
        assertEquals(20, r.bands().get(0).to());
        assertEquals(52, r.bands().get(0).crafts(), "1800 XP at 35 a craft, rounded up");
        assertEquals(29, r.bands().get(1).crafts(), "2000 XP at 70");
        assertEquals(81, r.crafts());
        assertEquals(3800, r.xp());
        List<LevelRoad.Step> s = r.steps();
        assertEquals(6, s.size(), "two materials then the craft, per band");
        assertEquals("Granite Gem", s.get(0).material());
        assertEquals(LevelRoad.Kind.CRAFT, s.get(2).kind());
        assertEquals(104, s.get(0).need(), "52 crafts × 2");
        assertEquals(30, s.get(0).have(), "the bag and bank count, mixed with what is bought");
        assertEquals(0, r.current(), "nothing done yet: the first step is the one to do");
    }

    @Test
    void aMaterialTooHighToGatherIsBoughtButCanStillBeGathered() {
        LevelRoad.Road r = road(LevelRoad.Pref.GATHER, Map.of(), Set.of(), 1);
        LevelRoad.Step granite = r.steps().get(0);
        assertFalse(granite.gatherable(), "mining 5 under Granite's 10");
        assertEquals(LevelRoad.Route.BUY, granite.route());
        assertNull(granite.cost(), "no price for it: the cost is unknown");
        assertNull(r.cost());
        LevelRoad.Road chosen = road(LevelRoad.Pref.GATHER, Map.of("Granite Gem", LevelRoad.Route.GATHER), Set.of(), 1);
        assertEquals(LevelRoad.Kind.GATHER, chosen.steps().get(0).kind(), "the player's choice wins");
        assertEquals(LevelRoad.Route.GATHER, chosen.steps().get(1).route(), "Trout Oil: fishing allows it");
    }

    @Test
    void mixedBuysWhatIsCheapAndGathersTheRest() {
        assertEquals(LevelRoad.Route.BUY, LevelRoad.defaultRoute(LevelRoad.Pref.MIXED, true, 1L, 40));
        assertEquals(LevelRoad.Route.GATHER, LevelRoad.defaultRoute(LevelRoad.Pref.MIXED, true, 1L, 104), "104 E is over the limit");
        assertEquals(LevelRoad.Route.GATHER, LevelRoad.defaultRoute(LevelRoad.Pref.MIXED, true, null, 10), "no price: gather");
        assertEquals(LevelRoad.Route.BUY, LevelRoad.defaultRoute(LevelRoad.Pref.BUY, true, null, 10));
        LevelRoad.Road all = road(LevelRoad.Pref.BUY, Map.of("Granite Gem", LevelRoad.Route.GATHER), Set.of(), 1);
        // buying Trout Oil 104 × 1, Gold 58 (no price), Salmon 58 × 2: one unpriced makes the total unknown
        assertNull(all.cost());
        LevelRoad.Road some = road(LevelRoad.Pref.GATHER, Map.of("Granite Gem", LevelRoad.Route.GATHER,
                "Trout Oil", LevelRoad.Route.BUY, "Gold Gem", LevelRoad.Route.GATHER), Set.of(), 1);
        assertEquals(104L, some.cost(), "only what is bought is counted");
        assertEquals(74 + 58 + 58, some.toGather(), "the rest of Granite (104 - 30), Gold and Salmon");
    }

    @Test
    void ticksAndOwnedMaterialsFinishSteps() {
        LevelRoad.Road r = road(LevelRoad.Pref.GATHER, Map.of(), Set.of("18:Granite Gem", "18:Trout Oil"), 1);
        assertTrue(r.steps().get(0).done(), "ticked by hand");
        assertEquals(2, r.current(), "the band's craft is next");
        assertTrue(r.steps().get(0).have() < r.steps().get(0).need(), "a tick does not invent the count");
    }

    @Test
    void aProfessionXpBombHalvesTheCrafts() {
        LevelRoad.Road r = road(LevelRoad.Pref.GATHER, Map.of(), Set.of(), 2);
        assertEquals(26, r.bands().get(0).crafts(), "1800 XP at 70 a craft");
        assertEquals(15, r.bands().get(1).crafts(), "2000 at 140, rounded up");
        assertEquals(52, r.steps().get(0).need());
        assertEquals(3800, r.xp(), "the XP itself does not change");
    }

    @Test
    void anUnknownLevelXpKeepsTheStepsWithoutCounts() {
        LevelRoad.Road r = LevelRoad.build(new LevelRoad.Inputs(RECIPES, "jeweling", "ring", 18, 21, 0, 0, n -> 0, SOURCES::get,
                p -> 40, n -> null, LevelRoad.Pref.GATHER, Map.of(), Set.of(), 1));
        assertEquals(-1, r.crafts());
        assertEquals(-1, r.steps().get(0).need());
        assertEquals(2, r.bands().size(), "the bands are still known from the recipes");
    }
}
