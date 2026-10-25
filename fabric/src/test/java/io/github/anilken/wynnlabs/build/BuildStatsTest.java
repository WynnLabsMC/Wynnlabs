package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Home's stats view: the totals, the derived numbers and the warnings of a build. */
class BuildStatsTest {

    @org.junit.jupiter.api.BeforeAll
    static void boot() {
        io.github.anilken.wynnlabs.game.TestGame.boot();
    }

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Damage.Piece ring(Map<String, Double> stats) {
        return new Damage.Piece(ItemStack.EMPTY, "r", "r", Slot.RING, 1, stats, Map.of(), 0, Map.of(), 0);
    }

    @Test
    void sumsTheGearAndFlagsWhatGetsYouKilled() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        Damage.Piece weapon = Damage.piece(GameState.current().allGear()
                .filter(i -> i.name().equals("Heavensent")).findFirst().orElseThrow());
        Map<String, Double> bad = new HashMap<>();
        bad.put("healthRegenRaw", -20.0);
        bad.put("healthRegen", 50.0);
        bad.put("lifeSteal", -5.0);
        bad.put("fireDefence", -90.0);
        bad.put("elementalDefence", -20.0);
        bad.put("rawMaxMana", 30.0);
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        set.put("RING1", ring(bad));
        set.put("WEAPON", weapon);
        int[] points = new int[SkillKind.values().length];
        points[SkillKind.AGILITY.ordinal()] = 40;
        Damage.Result r = Damage.evaluate(List.of(set.get("RING1")), weapon, points, 50);
        BuildStats.Side s = BuildStats.of(set, points, r, null, 50);

        assertEquals(-30, s.regen(), 1e-9, "raw regen raised by its %, a negative one too");
        assertEquals(-110, s.defence("fire"), 1e-9, "an element's own defence plus the all-element one");
        assertEquals(-30 * r.ehp() / r.health(), s.effectiveRegen(), 1e-9, "regen counted as effective health");
        assertTrue(s.parts() != null, "the spell panel's breakdown comes with the side");
        assertEquals(r.mainDps() / Damage.HITS[s.attackTier()], s.perAttack(), 1e-9, "one main attack: its DPS over hits per second");
        s.parts().spells().forEach(sp -> assertTrue(sp.cost() >= 1, "each spell has its mana cost: " + sp.name()));
        assertTrue(s.mana() >= 130, "the base pool and the gear's max mana: " + s.mana());
        assertTrue(s.ehpNoAgility() < r.ehp(), "Agility's dodge left out lowers effective health");
        List<String> kinds = s.warnings().stream().map(w -> w.kind() + (w.element() == null ? "" : ":" + w.element())).toList();
        assertEquals(List.of("regen", "steal", "defence:fire"), kinds);
    }

    @Test
    void aBuildYouCannotWearYetIsWorkedOutAtItsOwnLevelAndPoints() {
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        // 211 points of requirements, as a Lv 121 WynnBuilder link can have: past the 200 you earn
        set.put("HELMET", new Damage.Piece(ItemStack.EMPTY, "big", "big", Slot.HELMET, 121, new HashMap<>(),
                Map.of(SkillKind.STRENGTH, 100, SkillKind.DEFENCE, 100, SkillKind.AGILITY, 11), 0, Map.of(), 0));
        String why = BuildStats.whyNot(set, 106, 200);
        assertTrue(why != null && why.contains("121"), "a piece above your level is named first: " + why);
        assertTrue(BuildStats.whyNot(set, 121, 200) != null, "and the points it needs past what you earn");
        List<Integer> link = List.of(75, 0, 35, 0, 0);
        BuildStats.Side s = BuildStats.asBuilt(set, link, 106, null);
        assertEquals(121, s.level(), "worked out at the piece's level, not yours");
        assertEquals(5 * 121 + 5, s.result().health(), 1e-9, "the health of that level");
        BuildStats.Side least = BuildStats.asBuilt(set, null, 121, null);
        assertTrue(least.ehpNoAgility() > 0, "without points of its own: what its requirements take, past the cap");
    }

    @Test
    void anImportedPlanYouCannotWearYetStillHasItsStats() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        GameState state = GameState.current();
        // a Lv 121 link: high pieces whose requirements together pass the 200 points you earn
        List<io.github.anilken.wynnlabs.model.ItemData> high = state.allGear()
                .filter(i -> i.level() > 106 && Locks.kind(i) != null && !Locks.kind(i).isWeapon()).limit(40).toList();
        Map<String, String> items = new LinkedHashMap<>();
        for (io.github.anilken.wynnlabs.model.ItemData i : high) {
            String slot = Build.slotOf(Locks.kind(i), 0);
            if (slot != null) {
                items.putIfAbsent(slot, i.name());
            }
        }
        state.allGear().filter(i -> Locks.kind(i) != null && Locks.kind(i).isWeapon() && i.level() > 100).findFirst()
                .ifPresent(i -> items.put("WEAPON", i.name()));
        assertTrue(items.size() >= 3, "the fixture has high gear to build from: " + items);
        Plans.Build plan = new Plans.Build("p1", "Imported", List.of(), Tuning.Choice.PLAIN, items, 0, 121, 0, 0,
                List.of(75, 0, 35, 0, 0));
        Build.Plan now = Build.compute();
        BuildStats.Side s = BuildStats.planned(plan, now);
        assertTrue(s != null, "a plan you cannot wear yet still has its numbers");
        assertTrue(s.notWearable() != null && s.level() >= 121, "worked out at its own level, and says it is not wearable: " + s.level());
        assertTrue(s.result().health() > 5 * now.level() + 5, "its health is the build's, not yours");
        assertTrue(!s.stats().isEmpty(), "and its stat totals are there");
        // a search's plan for a level above yours, with the mod's spread (no points of its own)
        Map<String, String> found = new LinkedHashMap<>();
        found.put("HELMET", "Boreal-Patterned Crown");
        found.put("CHESTPLATE", "Aquarius");
        found.put("LEGGINGS", "Ornate Shadow Cover");
        found.put("BOOTS", "Moontower");
        found.put("WEAPON", "Warp");
        found.put("RING1", "Moon Pool Circlet");
        found.put("RING2", "Byte");
        found.put("BRACELET", "Megabyte");
        found.put("NECKLACE", "Necklace of a Thousand Storms");
        Plans.Build search = new Plans.Build("p2", "Build 1", List.of(), Tuning.Choice.PLAIN, found, 1, 107, 0, 0);
        BuildStats.Side t = BuildStats.planned(search, now);
        assertTrue(t != null && !t.stats().isEmpty(), "a found build for a higher level has its numbers too");
    }

    @Test
    void theSuggestionCountsWhenOnlyItsSkillPointsDiffer() {
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        set.put("RING1", ring(new HashMap<>()));
        int[] worn = new int[SkillKind.values().length];
        int[] better = worn.clone();
        better[SkillKind.DEFENCE.ordinal()] = 30;
        Damage.Result r = new Damage.Result(1, 0, 0, 0, 100, 0, 0, 0, new double[0], 100, 100);
        Build.Plan same = new Build.Plan(set, worn, r, set, worn, r, 0, java.util.Set.of(), worn, set, 50, null);
        assertEquals(null, BuildStats.suggested(same), "what you wear with your own points: nothing to compare");
        Build.Plan points = new Build.Plan(set, better, r, set, worn, r, 0, java.util.Set.of(), worn, set, 50, null);
        BuildStats.Side s = BuildStats.suggested(points);
        assertTrue(s != null && s.ehpNoAgility() > BuildStats.worn(points).ehpNoAgility(),
                "Defence from the suggested spread raises the numbers");
    }
}
