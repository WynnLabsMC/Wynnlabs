package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.model.SkillKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mana part of the score comes from the build's own stats: mana back every 5 s (Mana Regen,
 * Mana Steal) and the spells' cost cut (Intelligence, the raw and % cost IDs) by each spell's
 * share. The casts follow the mana (SpellCasts), so the mana part keeps a third of its weight;
 * Mana Steal counts only as far as the main attack lands it; rules.model.castModel 0 casts at the
 * old fixed rate whatever the mana.
 */
class ManaStatsTest {

    @BeforeEach
    void rules() {
        Rules.use(new JsonObject());        // the default Shaman spells: Totem 1st, Aura 3rd, Uproot 4th
    }

    @AfterEach
    void reset() {
        Rules.use(new JsonObject());
    }

    private static Damage.Piece piece(Map<String, Double> stats) {
        return new Damage.Piece(null, "test", "test", null, 0, new HashMap<>(stats), Map.of(), 0,
                Map.of("neutral", 1.0), 0);
    }

    private static final Damage.Piece WEAPON = new Damage.Piece(null, "weapon", "weapon", null, 0, new HashMap<>(),
            Map.of(), 300, Map.of("neutral", 1.0), 0);

    private static Damage.Result score(int intelligence, Map<String, Double> stats) {
        int[] assigned = new int[SkillKind.values().length];
        assigned[SkillKind.INTELLIGENCE.ordinal()] = intelligence;
        List<Damage.Piece> pieces = new ArrayList<>();
        if (!stats.isEmpty()) {
            pieces.add(piece(stats));
        }
        return Damage.evaluate(pieces, WEAPON, assigned, 100);
    }

    private static double mana(Damage.Result r) {
        return r.factors()[2];
    }

    @Test
    void castsFollowTheMana() {
        // the casts follow the mana, and the mana part keeps a third of its weight
        Damage.Result base = score(0, Map.of());
        Damage.Result more = score(0, Map.of("manaRegen", 30.0));
        assertTrue(base.casts() < Rules.sub("model", "maxCastsPer5s", 4), "the base mana pays for fewer: " + base.casts());
        assertTrue(more.casts() > base.casts());
        assertTrue(more.spellDps() > base.spellDps(), "more mana, more casts, more spell damage");
        assertTrue(score(0, Map.of("manaRegen", -40.0)).spellDps() <= base.spellDps());
        assertEquals(1 + 0.3 / 3 * Math.log(2), mana(score(0, Map.of("manaRegen", 25.0))), 1e-9);
    }

    @Test
    void manaStealCountsAsFarAsTheMainAttackLandsIt() {
        // a melee archetype hits all fight: its value every 3 s, whole
        assertEquals(25 + 10, withMainAttack(1.0, () -> score(0, Map.of("manaSteal", 6.0))).manaPer5(), 1e-9);
        // a Summoner casts and hits a fifth of the time: a fifth of it
        assertEquals(25 + 2, withMainAttack(0.2, () -> score(0, Map.of("manaSteal", 6.0))).manaPer5(), 1e-9);
        // and the casts see the same mana: the caster gets fewer of them from it
        assertTrue(withMainAttack(0.2, () -> score(0, Map.of("manaSteal", 20.0))).casts()
                < withMainAttack(1.0, () -> score(0, Map.of("manaSteal", 20.0))).casts());
    }

    private static Damage.Result withMainAttack(double weight, java.util.function.Supplier<Damage.Result> f) {
        JsonObject model = new JsonObject();
        model.addProperty("mainAttackWeight", weight);
        JsonObject rules = new JsonObject();
        rules.add("model", model);
        Rules.use(rules);
        try {
            return f.get();
        } finally {
            Rules.use(new JsonObject());
        }
    }

    @Test
    void noManaStatsIsOne() {
        Damage.Result r = score(0, Map.of());
        assertEquals(1.0, mana(r), 1e-9, "the game's base 25 and full costs");
        assertEquals(25, r.manaPer5(), 1e-9);
        assertEquals(0, r.costCut(), 1e-9);
    }

    @Test
    void manaRegenAndStealRaiseTheManaPart() {
        double base = mana(score(0, Map.of()));
        Damage.Result regen = score(0, Map.of("manaRegen", 25.0));
        assertEquals(50, regen.manaPer5(), 1e-9);
        assertEquals(1 + 0.3 / 3 * Math.log(2), mana(regen), 1e-9, "twice the base: a third of manaWeight × ln 2");
        Damage.Result steal = score(0, Map.of("manaSteal", 6.0));
        assertEquals(30, steal.manaPer5(), 1e-9, "Mana Steal's value every 3 s, half of it: the main attack's half of the fight");
        assertTrue(mana(steal) > base);
    }

    @Test
    void costCutsCountByTheSpellsShare() {
        double base = mana(score(0, Map.of()));
        // the 3rd spell (Aura) is cast: its cut counts by its share
        Damage.Result aura = score(0, Map.of("3rdSpellCost", 20.0));
        double share = 0.55 / (0.55 + 0.25 + 0.2);
        assertEquals(0.2 * share, aura.costCut(), 1e-9);
        assertTrue(mana(aura) > base);
        // a raw cut too, as a share of the spell's base cost
        Damage.Result raw = score(0, Map.of("raw4thSpellCost", 6.0));
        assertEquals(6.0 / 30 * 0.25, raw.costCut(), 1e-9);
        // the 2nd spell is not cast here: a cut on it counts nothing, a rise costs nothing
        assertEquals(base, mana(score(0, Map.of("2ndSpellCost", 30.0))), 1e-12);
        assertEquals(base, mana(score(0, Map.of("2ndSpellCost", -33.0))), 1e-12);
        // a cost rise on a spell that is cast counts against it
        assertTrue(mana(score(0, Map.of("3rdSpellCost", -33.0))) < base);
    }

    @Test
    void intelligenceCutsEveryCostAsAPercentage() {
        Damage.Result r = score(40, Map.of());
        double cut = 0.5 * Damage.curve(40) / Damage.curve(150);
        assertEquals(cut, r.costCut(), 1e-9, "Intelligence's cut, the same on each spell");
        assertTrue(mana(r) > mana(score(0, Map.of())));
    }

    @Test
    void theManaChipRaisesTheWeight() {
        Damage.Result plain = score(0, Map.of("manaRegen", 25.0));
        Damage.Result chip = Tuning.with(Tuning.Choice.PLAIN.withMana(true), () -> score(0, Map.of("manaRegen", 25.0)));
        assertEquals(3 * (mana(plain) - 1), mana(chip) - 1, 1e-9);
    }

    /** Spell cost and Mana lean on their own part of the mana score; both together are the old Mana chip. */
    @Test
    void theLeaningsWeighTheirOwnPart() {
        Map<String, Double> regen = Map.of("manaRegen", 25.0);
        Map<String, Double> cut = Map.of("3rdSpellCost", 50.0, "1stSpellCost", 50.0, "4thSpellCost", 50.0, "2ndSpellCost", 50.0);
        Tuning.Choice cost = Tuning.Choice.PLAIN.withLean(Tuning.LEAN_COST, true);
        Tuning.Choice mana = Tuning.Choice.PLAIN.withLean(Tuning.LEAN_MANA, true);
        // Spell cost: the cost cut counts three times, mana back every 5 s as before
        assertEquals(mana(score(0, regen)), mana(Tuning.with(cost, () -> score(0, regen))), 1e-9);
        assertTrue(mana(Tuning.with(cost, () -> score(0, cut))) - 1 > 2.5 * (mana(score(0, cut)) - 1));
        // Mana: the other way round
        assertEquals(mana(score(0, cut)), mana(Tuning.with(mana, () -> score(0, cut))), 1e-9);
        assertEquals(3 * (mana(score(0, regen)) - 1), mana(Tuning.with(mana, () -> score(0, regen))) - 1, 1e-9);
    }

    /** Sustain counts health regen and life steal more; nothing else in the score moves. */
    @Test
    void sustainWeighsRegen() {
        Tuning.Choice sustain = Tuning.Choice.PLAIN.withLean(Tuning.LEAN_SUSTAIN, true);
        Map<String, Double> regen = Map.of("healthRegenRaw", 200.0);
        double plain = score(0, regen).factors()[1] / score(0, Map.of()).factors()[1];
        double leant = Tuning.with(sustain, () -> score(0, regen).factors()[1] / score(0, Map.of()).factors()[1]);
        assertTrue(leant > plain, "regen counts more: " + plain + " → " + leant);
        assertEquals(mana(score(0, Map.of("manaRegen", 25.0))),
                mana(Tuning.with(sustain, () -> score(0, Map.of("manaRegen", 25.0)))), 1e-9);
    }

    @Test
    void noFightIsSimulatedWithTheFixedRate() {
        // rules.model.castModel 0: the old fixed rate, spell damage whatever the mana
        JsonObject fixed = new JsonObject();
        fixed.addProperty("castModel", 0);
        JsonObject withFixed = new JsonObject();
        withFixed.add("model", fixed);
        Rules.use(withFixed);
        Damage.Result base = score(0, Map.of());
        double rate = Rules.sub("model", "maxCastsPer5s", 4);
        assertEquals(rate, base.casts(), 1e-12, "the fixed cast rate");
        assertTrue(base.spellDps() > 0);
        for (Map<String, Double> m : List.of(Map.of("manaRegen", -40.0), Map.of("manaRegen", 30.0),
                Map.of("manaSteal", 20.0), Map.of("3rdSpellCost", 50.0), Map.of("rawMaxMana", 80.0))) {
            Damage.Result r = score(0, m);
            assertEquals(base.spellDps(), r.spellDps(), 1e-9, "spell damage does not hang on mana: " + m);
            assertEquals(rate, r.casts(), 1e-12, m.toString());
        }
        // the old fight's keys change nothing, and the rules no longer carry them
        JsonObject model = new JsonObject();
        model.addProperty("fightSeconds", 5);
        model.addProperty("baseMana", 400);
        JsonObject rules = new JsonObject();
        model.addProperty("castModel", 0);
        rules.add("model", model);
        Rules.use(rules);
        assertEquals(base.objective(), score(0, Map.of()).objective(), 1e-9 * base.objective());
        assertFalse(Rules.get().getAsJsonObject("model").has("fightSeconds"), "dropped on load");
    }
}
