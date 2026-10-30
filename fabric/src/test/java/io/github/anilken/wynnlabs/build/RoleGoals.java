package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.Klass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The road maps the goal tests compare (RoleGoalTest, RoleGoalWarriorTest, PlayStyleGoalTest, LootXpGoalTest):
 * the recorded Shaman played as a class on a goal, one map per class and goal, built once per
 * test JVM. A test that reads two milestones of one map (Lv 120 and Lv 90 on the Damage goal)
 * reads the same build, as the J menu would show it.
 */
final class RoleGoals {

    record Far(double ehp, double heal, double damage, double perCast, double xp, double speed, double loot, double mana) {}

    private static final Map<String, List<Roadmap.Step>> MAPS = new HashMap<>();

    private RoleGoals() {}

    /** The road map of the recorded Shaman played as {@code k} on {@code goal}. */
    private static synchronized List<Roadmap.Step> map(Klass k, String goal) throws Exception {
        String key = k + " " + goal;
        List<Roadmap.Step> steps = MAPS.get(key);
        if (steps == null) {
            FixtureState.load(Fixtures.root().resolve("shaman-44")).as(k).install("en");
            try {
                Setup.setGoal(goal);
                steps = List.copyOf(Roadmap.build());
            } finally {
                FixtureState.uninstall();
            }
            MAPS.put(key, steps);
        }
        return steps;
    }

    static Far far(Klass k, String goal) throws Exception {
        return far(k, goal, 120);
    }

    /** The set of the milestone at {@code level} (or the last before it) for class {@code k} and the goal. */
    static Far far(Klass k, String goal, int level) throws Exception {
        List<Roadmap.Step> steps = map(k, goal);
        Roadmap.Step last = steps.get(0);
        for (Roadmap.Step st : steps) {
            if (st.level() <= level) {
                last = st;
            }
        }
        Build.Eval e = last.set();
        assertTrue(e != null, goal + ": no set at Lv " + last.level());
        Map<String, Damage.Piece> b = last.build();
        double health = 0;
        double eff = 0;
        double loot = 0;
        for (Damage.Piece p : b.values()) {
            if (p != null) {
                health += p.health();
                eff += p.stats().getOrDefault("healingEfficiency", 0.0);
                loot += p.stats().getOrDefault("lootBonus", 0.0) + p.stats().getOrDefault("lootQuality", 0.0);
            }
        }
        Damage.Result r = e.result();
        Far f = new Far(r.ehp(), health * (1 + eff / 100) * r.casts(), r.spellDps() + r.mainDps(), r.dmgPerCast(),
                r.xp(), r.speed(), loot, r.manaPer5() / Math.max(0.05, 1 - r.costCut()));
        Fixtures.note(String.format("%s Lv %d: ehp %.0f, heal %.0f, damage %.0f, per cast %.0f, xp %.0f, speed %.0f, loot %.0f, mana %.2f, points %s",
                goal, last.level(), f.ehp(), f.heal(), f.damage(), f.perCast(), f.xp(), f.speed(), f.loot(), f.mana(),
                java.util.Arrays.toString(e.allocation())).replaceFirst("^", k + " "));
        return f;
    }

    /** The Tank's far set has the most effective health, the Healer's heals more than the Damage one, and the Damage one hits hardest. */
    static void rolesBuildForTheirJob(Klass k) throws Exception {
        Far damage = far(k, "DAMAGE");
        Far tank = far(k, "TANK");
        Far healer = far(k, "HEALER");
        assertTrue(tank.ehp() > damage.ehp(), k + ": tank ehp " + tank.ehp() + " vs damage " + damage.ehp());
        assertTrue(tank.ehp() >= healer.ehp(), k + ": tank ehp " + tank.ehp() + " vs healer " + healer.ehp());
        assertTrue(healer.heal() > damage.heal(), k + ": healer heal " + healer.heal() + " vs damage " + damage.heal());
        assertTrue(damage.damage() > tank.damage(), k + ": damage " + damage.damage() + " vs tank " + tank.damage());
    }
}
