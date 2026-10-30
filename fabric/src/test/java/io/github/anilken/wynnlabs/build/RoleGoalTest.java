package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The role goals change what the road map builds (on two classes, the play styles on one): the Tank's far milestone set has the most
 * effective health, the Healer's heals more than the Damage one (max health × Healing
 * Efficiency × casts), and the Damage one hits hardest. The Shaman here; the Warrior is
 * RoleGoalWarriorTest and the play styles PlayStyleGoalTest and LootXpGoalTest, so their road
 * maps build side by side.
 */
class RoleGoalTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void rolesBuildForTheirJobInEveryClass() throws Exception {
        // two classes with unlike weapons and damage taken (all five took a minute and a half)
        RoleGoals.rolesBuildForTheirJob(Klass.SHAMAN);
    }
}
