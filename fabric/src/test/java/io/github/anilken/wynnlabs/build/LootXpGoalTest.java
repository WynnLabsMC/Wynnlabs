package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.build.RoleGoals.Far;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** PlayStyleGoalTest's other two play styles on the Shaman: Loot finds more than the Damage goal, and XP gains more before the cap. */
class LootXpGoalTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void lootAndXpBuildForTheirJob() throws Exception {
        Klass k = Klass.SHAMAN;
        Far damage = RoleGoals.far(k, "DAMAGE");
        Far loot = RoleGoals.far(k, "LOOT");
        assertTrue(loot.loot() > damage.loot(), k + ": loot " + loot.loot() + " vs damage " + damage.loot());
        // the levelling goals only matter before the cap, so they are judged at the Lv 90 milestone
        Far xpDamage = RoleGoals.far(k, "DAMAGE", 90);
        Far xp = RoleGoals.far(k, "XP", 90);
        assertTrue(xp.xp() > xpDamage.xp(), k + ": xp " + xp.xp() + " vs damage " + xpDamage.xp());
    }
}
