package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guild Tome of Allegiance a milestone ahead may assume: +1 to every skill or +4 to one. */
class GuildTomeTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Damage.Piece piece(String name, Slot type, Map<SkillKind, Integer> reqs) {
        return new Damage.Piece(ItemStack.EMPTY, name, name, type, 1, new HashMap<>(), reqs, 0, Map.of(), 0);
    }

    private static final Tomes.Scope GUILD = new Tomes.Scope(new int[5], true);

    @Test
    void onlyMilestonesAheadFromLv100AssumeOne() {
        int[] none = new int[5];
        assertNull(Tomes.forMilestone(none, 107, 107), "now is honest");
        assertNull(Tomes.forMilestone(none, 99, 90), "below the tomes' level");
        assertTrue(Tomes.forMilestone(none, 110, 107).guild());
        Tomes.Scope have = Tomes.forMilestone(new int[] {0, 0, 4, 0, 0}, 110, 107);
        assertTrue(have != null && !have.guild(), "a real tome: no other assumed");
    }

    @Test
    void aSetFiveShortFitsWithEveryPlusOne() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        Map<String, Damage.Piece> b = new LinkedHashMap<>();
        b.put("HELMET", piece("a", Slot.HELMET, Map.of(SkillKind.STRENGTH, 41, SkillKind.DEXTERITY, 41)));
        b.put("BOOTS", piece("b", Slot.BOOTS, Map.of(SkillKind.INTELLIGENCE, 41, SkillKind.DEFENCE, 41,
                SkillKind.AGILITY, 41)));
        assertNull(Build.allocate(b, 110, 200), "205 needed, 200 earned");
        Build.Eval e = Tomes.with(GUILD, () -> Build.allocate(b, 110, 200));
        assertNotNull(e);
        assertEquals("Assimilator's Tome of Allegiance", e.tome().name());
        assertEquals(200, java.util.Arrays.stream(e.allocation()).sum(), "the tome's points are not assigned");
        assertEquals(41, e.withTome()[SkillKind.AGILITY.ordinal()]);
    }

    @Test
    void aSkillPastTheCapTakesTheFourPointTome() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        Map<String, Damage.Piece> b = new LinkedHashMap<>();
        b.put("HELMET", piece("a", Slot.HELMET, Map.of(SkillKind.INTELLIGENCE, 104)));
        Build.Eval e = Tomes.with(GUILD, () -> Build.allocate(b, 110, 200));
        assertNotNull(e, "104 Intelligence: 100 assigned and the tome's 4");
        assertEquals("Mastermind's Tome of Allegiance", e.tome().name());
        assertEquals(100, e.allocation()[SkillKind.INTELLIGENCE.ordinal()]);
    }
}
