package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.build.RoleGoals.Far;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The play style goals change what the road map builds (RoleGoalTest's roles, on the Shaman):
 * Boss hits harder per cast and Mobbing casts more or runs faster than the Damage goal. Loot
 * and XP are LootXpGoalTest, so the road maps build side by side.
 */
@Tag("heavy")
class PlayStyleGoalTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void playStylesBuildForTheirJob() throws Exception {
        Klass k = Klass.SHAMAN;
        Far damage = RoleGoals.far(k, "DAMAGE");
        Far boss = RoleGoals.far(k, "BOSS");
        Far mobbing = RoleGoals.far(k, "MOBBING");
        assertTrue(boss.perCast() >= damage.perCast(), k + ": boss per cast " + boss.perCast() + " vs damage " + damage.perCast());
        // packs need casting without pause, and running from one to the next
        assertTrue(mobbing.mana() > damage.mana() || mobbing.speed() > damage.speed(),
                k + ": mobbing mana " + mobbing.mana() + " speed " + mobbing.speed() + " vs damage " + damage.mana() + " " + damage.speed());
    }
}
