package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.BaseStats;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Requirement;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.StatRange;
import io.github.anilken.wynnlabs.model.Tier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A milestone's step gains are what each pick adds to the finished set, not what it added in the
 * order the picks went in: a helmet that carries the Dexterity the new weapon needs reads a gain,
 * not the loss it showed before the weapon was in; a pick the set is better without goes.
 */
class StepGainsTest {

    private static final int M = 100;
    private static final int EARNED = 200;

    @BeforeEach
    void load() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
    }

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static ItemData relik(String name, SkillKind skill, int need, String element, int low, int high) {
        List<Requirement.SkillReq> reqs = new ArrayList<>();
        if (skill != null) {
            reqs.add(new Requirement.SkillReq(skill, need));
        }
        return new ItemData(name, Slot.RELIK, Tier.RARE, 0,
                new Requirement(90, Optional.of(Klass.SHAMAN), reqs, Optional.empty()),
                new BaseStats((low + high) / 2, 0, Optional.of("NORMAL"), Optional.empty(),
                        List.of(new BaseStats.Damage(element, low, high)), List.of()),
                List.of(), Optional.empty(), "NONE");
    }

    private static ItemData piece(String name, Slot slot, int health, List<StatRange> stats) {
        return new ItemData(name, slot, Tier.RARE, 0, new Requirement(90, Optional.empty(), List.of(), Optional.empty()),
                new BaseStats(0, health, Optional.empty(), Optional.empty(), List.of(), List.of()), stats,
                Optional.empty(), "NONE");
    }

    private static StatRange raw(String id, int v) {
        return new StatRange(id, id, "RAW", false, v, v);
    }

    private static StatRange pct(String id, int v) {
        return new StatRange(id, id, "PERCENT", false, v, v);
    }

    private static Roadmap.Gear row(String slot, ItemData info, double gain) {
        return new Roadmap.Gear(slot, info, gain, "?", null, null, null, List.of(), List.of(), List.of());
    }

    private static double judged(Map<String, Damage.Piece> set) {
        Build.Eval e = Build.allocate(set, M, EARNED);
        assertNotNull(e, "wearable: " + set.keySet());
        return e.result().objective();
    }

    @Test
    void aHelmetTheNewWeaponNeedsShowsWhatItAddsInTheSet() {
        // the new weapon needs 120 Dexterity: only the new helmet's +30 lets the set wear it
        ItemData oldWeapon = relik("Old Relik", null, 0, "NEUTRAL", 300, 340);
        ItemData newWeapon = relik("Quick Relik", SkillKind.DEXTERITY, 120, "THUNDER", 900, 1000);
        ItemData oldHelmet = piece("Old Helmet", Slot.HELMET, 3000, List.of(pct("spellDamage", 25)));
        ItemData newHelmet = piece("Dex Helmet", Slot.HELMET, 2500, List.of(raw("rawDexterity", 30)));
        Map<String, Damage.Piece> start = new LinkedHashMap<>();
        start.put("WEAPON", Roadmap.pieceOf(oldWeapon));
        start.put("HELMET", Roadmap.pieceOf(oldHelmet));
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        set.put("WEAPON", Roadmap.pieceOf(newWeapon));
        set.put("HELMET", Roadmap.pieceOf(newHelmet));
        // slot by slot the helmet went in first, beside the old weapon, and lost there
        Map<String, Damage.Piece> helmetFirst = new LinkedHashMap<>(start);
        helmetFirst.put("HELMET", Roadmap.pieceOf(newHelmet));
        double seq = (judged(helmetFirst) / judged(start) - 1) * 100;
        assertTrue(seq < 0, "the helmet alone beside the old weapon is a loss: " + seq);
        assertNull(Build.allocate(Map.of("WEAPON", Roadmap.pieceOf(newWeapon), "HELMET", Roadmap.pieceOf(oldHelmet)),
                M, EARNED), "the new weapon needs the new helmet's Dexterity");

        List<Roadmap.Gear> gear = new ArrayList<>(List.of(row("HELMET", newHelmet, seq), row("RELIK", newWeapon, 76)));
        Map<String, String> placed = new LinkedHashMap<>(Map.of("HELMET", "HELMET", "RELIK", "WEAPON"));
        StepGains.apply(gear, set, start, placed, M, EARNED, null);

        double total = (judged(set) / judged(start) - 1) * 100;
        assertEquals(2, gear.size(), "both picks stay");
        Roadmap.Gear helmet = gear.get(0);
        Roadmap.Gear weapon = gear.get(1);
        assertTrue(helmet.gain() > 0, "the helmet adds to the set it belongs to: " + helmet.gain());
        // the helmet cannot go back alone (the weapon needs it): it goes back with the weapon and
        // shows what the two add; the weapon goes back alone, beside the helmet that stays
        assertEquals(total, helmet.gain(), 1e-6);
        assertEquals((judged(set) / judged(helmetFirst) - 1) * 100, weapon.gain(), 1e-6);
        assertEquals("Dex Helmet", set.get("HELMET").name());
    }

    @Test
    void aPickTheSetIsBetterWithoutGoes() {
        ItemData weapon = relik("Old Relik", null, 0, "NEUTRAL", 300, 340);
        ItemData goodRing = piece("Good Ring", Slot.RING, 0, List.of(pct("spellDamage", 20)));
        ItemData weakRing = piece("Weak Ring", Slot.RING, 0, List.of(pct("spellDamage", 1)));
        ItemData oldNeck = piece("Old Necklace", Slot.NECKLACE, 0, List.of());
        ItemData newNeck = piece("New Necklace", Slot.NECKLACE, 0, List.of(pct("spellDamage", 15)));
        Map<String, Damage.Piece> start = new LinkedHashMap<>();
        start.put("WEAPON", Roadmap.pieceOf(weapon));
        start.put("RING1", Roadmap.pieceOf(goodRing));
        start.put("NECKLACE", Roadmap.pieceOf(oldNeck));
        Map<String, Damage.Piece> set = new LinkedHashMap<>(start);
        set.put("RING1", Roadmap.pieceOf(weakRing));
        set.put("NECKLACE", Roadmap.pieceOf(newNeck));

        List<Roadmap.Gear> gear = new ArrayList<>(List.of(row("RING", weakRing, 0.3), row("NECKLACE", newNeck, 5)));
        Map<String, String> placed = new LinkedHashMap<>(Map.of("RING", "RING1", "NECKLACE", "NECKLACE"));
        StepGains.apply(gear, set, start, placed, M, EARNED, null);

        assertEquals("Good Ring", set.get("RING1").name(), "the ring you had stays");
        assertEquals(1, gear.size());
        assertEquals("New Necklace", gear.get(0).info().name());
        Map<String, Damage.Piece> without = new LinkedHashMap<>(set);
        without.put("NECKLACE", Roadmap.pieceOf(oldNeck));
        assertEquals((judged(set) / judged(without) - 1) * 100, gear.get(0).gain(), 1e-6);
        assertFalse(gear.get(0).gain() <= 0);
    }
}
