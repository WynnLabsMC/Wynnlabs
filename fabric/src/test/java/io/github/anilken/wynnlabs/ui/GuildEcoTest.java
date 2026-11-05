package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        // a medium territory (each stat ~6: 4800 an hour) and a very high HQ shown one tier up (high: 8, aura 1, volley 1)
        Map<String, long[]> eco = GuildEco.of("G", List.of(t("G", 3, false, 3600), t("G", 5, true, 7200), t("Other", 5, false, 3600)));
        assertArrayEquals(new long[] {18000, 0, 0}, eco.get("EMERALDS"));
        assertEquals(10800, eco.get("CROPS")[0]);
        assertEquals(4800 + 12000 + 800, eco.get("CROPS")[1]);
        assertEquals(4800 + 12000 + 200, eco.get("ORE")[1]);
        assertEquals(4800 + 12000, eco.get("WOOD")[1]);
    }
}
