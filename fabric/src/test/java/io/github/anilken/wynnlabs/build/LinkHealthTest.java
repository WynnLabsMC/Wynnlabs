package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A decoded link's armour keeps the health its item data has (with the link's powders), and a
 * link whose armour is crafted is not judged as a whole build: the model cannot read crafted
 * pieces, so with a Cataclysm's −6000 health the set it keeps sits at 1 health (the guide's
 * Cataclysm Trickster, entry 109, scored about 0.002 of the pick that way).
 */
class LinkHealthTest {

    /** The guide's entry 109: crafted helmet, chestplate, rings and necklace, Funambulist, Electro Mage's Boots, Cataclysm. */
    private static final String CATACLYSM = "https://wynnbuilder-beta.github.io/builder/#CUG48gGa8QW1GpGpid81RqF24L8I4Dm0ePeP2KaWDwpglDEhLsuH0"
            + "J2J2+YWYWYW2SY441C9C9uB2A2A2Am9I0OOY0c4c4y515151bu49WDDRui60uVkdKkynhK";
    /** The guide's entry 12: Hero, every piece a plain item. */
    private static final String HERO = "https://wynnbuilder.github.io/builder/#CY0W90xG8t2uQWW64qmoHmlHhcYuOF0ez-ulx6Hu";

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void decodedArmourKeepsItsHealth() throws Exception {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(CATACLYSM);
        assertNotNull(b);
        ReferenceBuilds.installPlain();
        ReferenceBuilds.install(Klass.ASSASSIN, b.level(), "Trickster");
        ReferenceBuilds.LinkSet ls = ReferenceBuilds.set(b);
        Map<String, Damage.Piece> set = ls.set();
        // two T7 powders add 75 health each to the item's own
        assertEquals("Funambulist", set.get("LEGGINGS").name());
        assertEquals(ReferenceBuilds.byName("Funambulist").base().health() + 150, set.get("LEGGINGS").health(), 1e-9);
        assertEquals("Electro Mage's Boots", set.get("BOOTS").name());
        assertEquals(ReferenceBuilds.byName("Electro Mage's Boots").base().health() + 150, set.get("BOOTS").health(), 1e-9);
        assertEquals(-6000, set.get("WEAPON").stats().get("rawHealth"), 1e-9);
        // the crafted helmet and chestplate are left out, so the link is not a whole build
        assertTrue(ls.dropped().contains("helmet:crafted"), ls.dropped().toString());
        assertTrue(ls.dropped().contains("chestplate:crafted"), ls.dropped().toString());
        assertTrue(ls.armourLeftOut());
    }

    @Test
    void aPlainLinkIsWhole() throws Exception {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(HERO);
        assertNotNull(b);
        ReferenceBuilds.installPlain();
        Klass k = ReferenceBuilds.classOf(b);
        assertNotNull(k);
        ReferenceBuilds.install(k, b.level(), "");
        ReferenceBuilds.LinkSet ls = ReferenceBuilds.set(b);
        assertFalse(ls.armourLeftOut(), ls.dropped().toString());
        for (String slot : new String[] {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"}) {
            Damage.Piece p = ls.set().get(slot);
            assertNotNull(p, slot);
            assertTrue(p.health() >= GameState.current().allGear().filter(i -> i.name().equals(p.name())).findFirst()
                    .orElseThrow().base().health(), slot + " " + p.name());
        }
    }
}
