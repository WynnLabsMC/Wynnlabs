package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** RoleGoalTest's roles on the second class, the Warrior (a spear, and more damage taken up close). */
class RoleGoalWarriorTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void rolesBuildForTheirJobInEveryClass() throws Exception {
        RoleGoals.rolesBuildForTheirJob(Klass.WARRIOR);
    }
}
