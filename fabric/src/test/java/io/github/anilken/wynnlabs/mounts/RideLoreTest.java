package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.model.MountRead;
import org.junit.jupiter.api.Test;

import java.util.List;

class RideLoreTest {

    @Test
    void aSummonItemsLoreReadsAsTheMountWithItsPoolApart() {
        MountRead m = RideLore.parse("Wyvern Reins", List.of("", "438 Potential", " Azure-Horn", "Energy 18/30", "",
                "Level/Limit", "Speed 12/21", "Acceleration 21/21", "Altitude 9/18", "Energy 15/19", "Handling 6/14",
                "Toughness 18/18", "Boost 4/12", "Training 30/30", "", " Right-Click to Summon"));
        assertEquals("WYVERN", m.type());
        assertEquals(18, m.energy());
        assertEquals(30, m.energyMax());
        assertEquals(8, m.stats().size());
        assertEquals(new MountRead.Stat("ENERGY", "Energy", 15, 19, 19), m.stats().get(3));
        assertEquals(438, m.potential());
        assertEquals("Azure-Horn", m.skin());
        assertTrue(m.estimated(), "no maxes outside an enclosure");
    }

    @Test
    void maxesInsideAnEnclosureAndJumpHeightAreRead() {
        MountRead m = RideLore.parse("§6Horse", List.of("Energy 9/24", "Speed 8/15 (30)", "Acceleration 5/15 (30)",
                "Jump Height 3/10 (28)", "Energy 10/10 (30)", "Handling 7/12 (30)"));
        assertEquals("HORSE", m.type());
        assertEquals(new MountRead.Stat("JUMP_HEIGHT", "Jump Height", 3, 10, 28), m.stats().get(2));
        assertFalse(m.estimated());
        assertEquals(9, m.energy());
    }

    @Test
    void anItemWithoutStatLinesIsNoMount() {
        assertNull(RideLore.parse("Diamond Sword", List.of("Damage 12/20", "Speed 3/4")));
        assertNull(RideLore.parse("Wyvern Reins", List.of()));
    }

    /** The Wyvern Flute's lore as the game sent it, its glyphs as U+E000 (one between each stat's name and number). */
    @Test
    void theWyvernFluteFromTheGameReadsAsTheMount() {
        String g = "\uE000";
        List<String> lore = List.of(g + "240 Potential", " Golden-Tusk", g + "Energy 196/204", g + "Combat Level" + g + "84",
                g + "Quest The Canyon Guides", "Speed" + g + "1/10", "Acceleration" + g + "1/10", "Altitude" + g + "1/18",
                "Energy" + g + "2/10", "Handling" + g + "1/10", "Toughness" + g + "1/10", "Boost" + g + "1/14",
                "Training" + g + "1/10", "", "Right-Click to Summon", "Drop to Destroy", "Bantisu Quarters");
        MountRead m = RideLore.parse("Wyvern Flute", lore);
        assertEquals("WYVERN", m.type());
        assertEquals(196, m.energy(), "the first Energy line, before the stats, is the pool");
        assertEquals(204, m.energyMax());
        assertEquals(8, m.stats().size());
        assertEquals(new MountRead.Stat("SPEED", "Speed", 1, 10, 10), m.stats().get(0));
        assertEquals(new MountRead.Stat("ALTITUDE", "Altitude", 1, 18, 18), m.stats().get(2));
        assertEquals(new MountRead.Stat("ENERGY", "Energy", 2, 10, 10), m.stats().get(3), "the second Energy line is the stat");
        assertEquals(new MountRead.Stat("BOOST", "Boost", 1, 14, 14), m.stats().get(6));
        assertTrue(m.estimated(), "no maxes on a flute: the feeder's are used when known");
        assertEquals(240, m.potential());
        assertEquals("Golden-Tusk", m.skin());
        assertEquals("Bantisu Quarters", RideLore.home(lore));
        assertEquals(9, RideLore.statLines(lore), "eight stats and the pool");
    }

    /** The Wyvern Flute exactly as the game sent it (ride.txt): spacer glyphs outside the private-use areas, as surrogate pairs. */
    @Test
    void theWyvernFluteAsTheGameSentItReadsAsTheMount() {
        String name = "\uDAFC\uDC00Wyvern Flute\uDAFC\uDC00";
        List<String> lore = List.of(
                "\uDAFF\uDFF0\uDAFF\uDFCF\uDB00\uDC05Wyvern Flute",
                "\uDB00\uDC26\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFE0\uDB00\uDC02\uDB00\uDC01\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFFF\uDAFF\uDFDA\uDB00\uDC02",
                "",
                "\uDB00\uDC01240 Potential",
                "\uDB00\uDC01 Golden-Tusk",
                "\uDAFF\uDFF7\uDB00\uDC05Energy 203/204",
                "\uDB00\uDC06",
                "\uDAFF\uDFFF Combat Level\uDAFF\uDFBB\uDB00\uDC7284",
                "\uDAFF\uDFFF Quest\uDAFF\uDFDC\uDB00\uDC2CThe Canyon Guides",
                "\uDB00\uDC06",
                "Speed\uDAFF\uDFE7\uDB00\uDC5F1/10 \uDAFF\uDFF7\uDB00\uDC09\uDAFF\uDFF7",
                "Acceleration\uDAFF\uDFCA\uDB00\uDC5F1/10 \uDAFF\uDFF7\uDB00\uDC09\uDAFF\uDFF7",
                "Altitude\uDAFF\uDFDF\uDB00\uDC5F1/18 \uDAFF\uDFF7\uDB00\uDC09\uDAFF\uDFF7",
                "Energy\uDAFF\uDFE2\uDB00\uDC5E2/10 \uDAFF\uDFF7\uDAFF\uDFF7",
                "Handling\uDAFF\uDFDD\uDB00\uDC5F1/10 \uDAFF\uDFF7\uDB00\uDC09\uDAFF\uDFF7",
                "Toughness\uDAFF\uDFD2\uDB00\uDC5F1/10 \uDAFF\uDFF7\uDB00\uDC09\uDAFF\uDFF7",
                "Boost\uDAFF\uDFE8\uDB00\uDC5D2/14 \uDAFF\uDFF7\uDAFF\uDFF7",
                "Training\uDAFF\uDFDD\uDB00\uDC5F1/10 \uDAFF\uDFF7\uDB00\uDC09\uDAFF\uDFF7",
                "",
                "\uDB00\uDC09 Right-Click to Summon",
                "\uDB00\uDC14 Drop to Destroy",
                "",
                "\uDB00\uDC19Bantisu Quarters");
        MountRead m = RideLore.parse(name, lore);
        assertEquals("WYVERN", m.type());
        assertEquals("Wyvern Flute", m.name());
        assertEquals(203, m.energy());
        assertEquals(204, m.energyMax());
        assertEquals(8, m.stats().size());
        assertEquals(new MountRead.Stat("SPEED", "Speed", 1, 10, 10), m.stats().get(0));
        assertEquals(new MountRead.Stat("ALTITUDE", "Altitude", 1, 18, 18), m.stats().get(2));
        assertEquals(new MountRead.Stat("ENERGY", "Energy", 2, 10, 10), m.stats().get(3));
        assertEquals(new MountRead.Stat("BOOST", "Boost", 2, 14, 14), m.stats().get(6));
        assertEquals(240, m.potential());
        assertEquals("Golden-Tusk", m.skin());
        assertEquals("Bantisu Quarters", RideLore.home(lore));
    }

    @Test
    void glyphsAreTakenOutByCodePointInEveryPlane() {
        String s = "Speed" + new String(Character.toChars(0xFFFE7)) + new String(Character.toChars(0x10045))
                + new String(Character.toChars(0xCFFE7)) + "\uE000" + "1/10";
        assertEquals("Speed1/10", FeederRead.glyphless(s));
        assertEquals("Çay ş", FeederRead.glyphless("Çay ş"), "real letters stay");
    }
}
