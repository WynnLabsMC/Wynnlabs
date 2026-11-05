package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A guild's hourly economy: made from the territories, cost guessed from their defence ratings. */
class GuildEcoTest {

    private static GuildMapLook.Territory t(String guild, int defence, boolean hq, int crops) {
        return new GuildMapLook.Territory("T", guild, "G", 0, 0, hq, "", 0, "", defence,
                List.of(new GuildMapLook.Res("EMERALDS", 0, 0, 0, 9000), new GuildMapLook.Res("CROPS", 0, 0, 0, crops)),
                0, 0, true);
    }

    @Test
    void madeAndGuessedCost() {
        // a Medium territory (3 in every stat, aura and volley 1) and a Very High HQ (the HQ setup,
        // its crops at twice the base: Efficient Resources 100%, 12000 emeralds an hour)
        Map<String, long[]> eco = GuildEco.of("G", List.of(t("G", 3, false, 3600), t("G", 5, true, 7200), t("Other", 5, false, 3600)));
        assertEquals(18000, eco.get("EMERALDS")[0]);
        assertEquals(12000, eco.get("EMERALDS")[1]);
        assertEquals(10800, eco.get("CROPS")[0]);
        assertEquals(600 + 800 + 15600 + 3200, eco.get("CROPS")[1]);
        assertEquals(600 + 200 + 12000 + 800, eco.get("ORE")[1]);
        assertEquals(600 + 22800, eco.get("WOOD")[1]);
        assertEquals(600 + 12000 + 4800, eco.get("FISH")[1]);
    }

    @Test
    void storageFromCapacity() {
        assertEquals(2, GuildEco.storageLevel(1200, 300));        // +300%
        assertEquals(6, GuildEco.storageLevel(120000, 1500));     // an HQ maxed
        assertEquals(0, GuildEco.storageLevel(300, 300));
    }
}
