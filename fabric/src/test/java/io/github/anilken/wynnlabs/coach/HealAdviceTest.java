package io.github.anilken.wynnlabs.coach;

import io.github.anilken.wynnlabs.core.Words;
import io.github.anilken.wynnlabs.model.Klass;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Healers are told to heal, players who stay in the fight are not told to back off, and their
 * health is judged by the net loss; the death coach says the same after a death.
 */
class HealAdviceTest {

    private static final HealAdvice.Healer MAGE = new HealAdvice.Healer("R-L-R", 35);

    private static HealAdvice.Stance stance(HealAdvice.Healer h, boolean holds, boolean boss, int mana) {
        return new HealAdvice.Stance(h, holds, boss, mana);
    }

    private static String key(Words w) {
        return w.key();
    }

    // ---- who heals ----

    @Test
    void everyMageHealsUnlessTheTreeSaysOtherwise() {
        assertEquals(MAGE, HealAdvice.selfHeal(Klass.MAGE, "DAMAGE", List.of(), null), "tree not read yet");
        assertEquals(MAGE, HealAdvice.selfHeal(Klass.MAGE, "DAMAGE", List.of("Meteor", "Heal"), null));
        assertNull(HealAdvice.selfHeal(Klass.MAGE, "DAMAGE", List.of("Meteor", "Teleport"), null), "Heal not taken");
    }

    @Test
    void healerGoalHealsWithoutAKnownCombo() {
        assertEquals(new HealAdvice.Healer("", 0), HealAdvice.selfHeal(Klass.SHAMAN, "HEALER", List.of(), null));
        assertNull(HealAdvice.selfHeal(Klass.SHAMAN, "TANK", List.of(), null));
        assertNull(HealAdvice.selfHeal(Klass.WARRIOR, "DAMAGE", List.of(), null));
    }

    @Test
    void tanksHealersAndBossFightsHold() {
        assertTrue(HealAdvice.holds("TANK", false));
        assertTrue(HealAdvice.holds("HEALER", false));
        assertTrue(HealAdvice.holds("DAMAGE", true));
        assertFalse(HealAdvice.holds("DAMAGE", false));
    }

    @Test
    void aBossHasManyTimesYourHealth() {
        assertTrue(HealAdvice.bossFight(3_000_000, 20_000));
        assertFalse(HealAdvice.bossFight(200_000, 20_000), "a tough pack mob");
        assertFalse(HealAdvice.bossFight(0, 20_000), "nothing hit");
        assertFalse(HealAdvice.bossFight(3_000_000, 0), "health unknown");
    }

    // ---- the warning ----

    @Test
    void healerIsToldToHealWithTheCombo() {
        Words w = HealAdvice.warning("2.0", stance(MAGE, false, false, 200), 5, 3);
        assertEquals("wynnlabs.coach.warn_heal_combo", key(w));
        assertEquals(List.of("2.0", "R-L-R"), w.args());
        assertEquals("wynnlabs.coach.warn_heal_combo", key(HealAdvice.warning("2.0", stance(MAGE, false, false, -1), 0, -1)),
                "mana unknown: heal");
        assertEquals("wynnlabs.coach.warn_heal_now",
                key(HealAdvice.warning("2.0", stance(new HealAdvice.Healer("", 0), true, false, 10), 0, -1)));
    }

    @Test
    void healerWithoutManaGetsThePots() {
        assertEquals("wynnlabs.coach.warn_potion_slot", key(HealAdvice.warning("2.0", stance(MAGE, false, false, 20), 5, 3)));
        assertEquals("wynnlabs.coach.warn_no_potion", key(HealAdvice.warning("2.0", stance(MAGE, false, false, 20), 0, -1)));
        // a healer by goal holds: the pots without "back off"
        assertEquals("wynnlabs.coach.warn_fast_potion_bag", key(HealAdvice.warning("2.0", stance(MAGE, true, false, 20), 5, -1)));
    }

    @Test
    void tankHealerOrBossIsNotToldToBackOff() {
        for (HealAdvice.Stance s : List.of(stance(null, true, false, -1), stance(null, true, true, -1))) {
            assertEquals("wynnlabs.coach.warn_fast_no_potion", key(HealAdvice.warning("1.5", s, 0, -1)));
            assertEquals("wynnlabs.coach.warn_fast_potion_slot", key(HealAdvice.warning("1.5", s, 4, 0)));
            assertEquals("wynnlabs.coach.warn_fast_potion_bag", key(HealAdvice.warning("1.5", s, 4, -1)));
        }
    }

    @Test
    void everyoneElseAsBefore() {
        HealAdvice.Stance s = HealAdvice.Stance.NONE;
        assertEquals("wynnlabs.coach.warn_no_potion", key(HealAdvice.warning("1.5", s, 0, -1)));
        assertEquals(List.of("1.5", 1), HealAdvice.warning("1.5", s, 4, 0).args());
        assertEquals("wynnlabs.coach.warn_potion_bag", key(HealAdvice.warning("1.5", s, 4, -1)));
        assertFalse(s.net());
    }

    @Test
    void bossFightIsQuietLikeARaid() {
        assertFalse(SurvivalPace.sound(true));
        assertFalse(SurvivalPace.due(10_000, 0, true));
    }

    // ---- damage only or net ----

    @Test
    void healingItOutIsNoLoss() {
        List<Integer> hp = List.of(10_000, 8_000, 9_500, 7_500, 10_000);
        assertEquals(4_000, HealAdvice.lost(hp, false), 0.01, "damage only: the drops");
        assertEquals(0, HealAdvice.lost(hp, true), 0.01, "net: healed back to full");
        assertEquals(1_000, HealAdvice.lost(List.of(10_000, 8_000, 9_500, 7_500, 9_000), true), 0.01);
        assertTrue(stance(MAGE, false, false, -1).net());
        assertTrue(stance(null, true, false, -1).net());
    }

    // ---- the death coach ----

    private static DeathCoach.Death death(int potions, double seconds) {
        return new DeathCoach.Death(1_000_000L, 100, 70, -200, 44, "Somewhere", false, 0, 2900, 1900, seconds, potions,
                List.of(), new ArrayList<>());
    }

    private static List<String> keys(List<Words> ws) {
        return ws.stream().map(Words::key).toList();
    }

    @Test
    void deathAdviceUnchangedForOthers() {
        assertEquals(List.of("wynnlabs.death.no_potions"), keys(DeathCoach.advise(death(0, 8), List.of(), 20)));
        assertEquals(List.of("wynnlabs.death.one_off"), keys(DeathCoach.advise(death(5, 8), List.of(), 20)));
    }

    @Test
    void healerIsToldToHealSooner() {
        assertEquals(List.of("wynnlabs.death.heal_sooner_combo"),
                keys(DeathCoach.advise(death(0, 8), List.of(), 20, stance(MAGE, false, false, 200))));
        assertEquals(List.of("wynnlabs.death.heal_sooner"),
                keys(DeathCoach.advise(death(0, 8), List.of(), 20, stance(new HealAdvice.Healer("", 0), true, false, -1))));
        assertEquals(List.of("wynnlabs.death.out_of_mana"),
                keys(DeathCoach.advise(death(5, 8), List.of(), 20, stance(MAGE, false, false, 10))));
        assertEquals(List.of("wynnlabs.death.one_off_heal"),
                keys(DeathCoach.advise(death(5, 8), List.of(), 20, stance(MAGE, false, false, 200))));
    }

    @Test
    void tankOrBossIsNotToldToBackOff() {
        assertEquals(List.of("wynnlabs.death.one_off_tank"),
                keys(DeathCoach.advise(death(5, 8), List.of(), 20, stance(null, true, false, -1))));
        DeathCoach.Death before = death(5, 8);
        assertEquals("wynnlabs.death.repeat_boss",
                keys(DeathCoach.advise(death(5, 8), List.of(before), 20, stance(null, true, true, -1))).get(0));
        assertEquals("wynnlabs.death.repeat_here",
                keys(DeathCoach.advise(death(5, 8), List.of(before), 20)).get(0));
    }
}
