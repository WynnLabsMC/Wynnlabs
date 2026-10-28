package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The player's own combined goals: what is kept and read back. */
class CraftCombosTest {

    @Test
    void combinationsReadBackAndOnlyValidOnes() {
        List<String> keys = List.of("combo:rawspell+loot", "combo:melee+attackspeed+lifesteal+speed");
        assertEquals(keys, CraftCombos.fromJson(CraftCombos.toJson(keys)));
        // a single goal, five goals or junk are not combinations
        assertEquals(List.of("combo:mana+xp"),
                CraftCombos.fromJson("[\"mana\",\"combo:a+b+c+d+e\",\"combo:mana+xp\"]"));
        assertEquals(List.of(), CraftCombos.fromJson("not json"));
    }
}
