package io.github.anilken.wynnlabs.market;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Turkish;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mount prices by potential: a band of listings near the mount's potential, widened when too few,
 * and nothing (so "depends on potential") when none are known, never one price for every mount.
 */
class MountPricesTest {

    private static final String WYVERN = "Wyvern Reins";
    private static final long NOW = 1_800_000_000_000L;
    private static final int LE = 4096;

    private static MountPrices.Seen seen(int potential, int price) {
        return new MountPrices.Seen(WYVERN, potential, price, NOW - 60_000L);
    }

    private static void language(int pick) {
        JsonObject s = new JsonObject();
        s.addProperty("langOverride", pick);
        Settings.use(s);
    }

    @AfterEach
    void reset() {
        Settings.use(new JsonObject());
    }

    @Test
    void aFreshMountAndAHighPotentialOneGetTheirOwnPrice() {
        List<MountPrices.Seen> all = List.of(seen(240, 13 * 64), seen(250, 15 * 64), seen(230, 12 * 64),
                seen(1000, 22 * LE), seen(980, 20 * LE), seen(1040, 25 * LE));
        MountPrices.Estimate low = MountPrices.estimate(all, WYVERN, 240, NOW);
        MountPrices.Estimate high = MountPrices.estimate(all, WYVERN, 1000, NOW);
        assertEquals(13 * 64, low.price(), "the lower middle of the 230-250 band");
        assertEquals(240, low.potential());
        assertEquals(3, low.count());
        assertEquals(22 * LE, high.price(), "the 1k band, nothing from the fresh mounts");
        assertEquals(3, high.count());
    }

    @Test
    void theBandWidensWhenTooFewAreNear() {
        // ±10 % of 500 is 450-550: one listing; ±20 % (400-600) has two
        List<MountPrices.Seen> all = List.of(seen(520, 3 * LE), seen(590, 4 * LE), seen(1000, 22 * LE));
        MountPrices.Estimate e = MountPrices.estimate(all, WYVERN, 500, NOW);
        assertEquals(2, e.count());
        assertEquals(3 * LE, e.price());
    }

    @Test
    void theWidestBandTakesEvenOneListing() {
        MountPrices.Estimate e = MountPrices.estimate(List.of(seen(1100, 22 * LE)), WYVERN, 1000, NOW);
        assertEquals(1, e.count());
        assertEquals(1100, e.potential());
    }

    @Test
    void nothingNearOldOrAnotherMountGivesNoPrice() {
        List<MountPrices.Seen> all = List.of(seen(240, 13 * 64),
                new MountPrices.Seen(WYVERN, 1000, 22 * LE, NOW - MountPrices.FRESH_MS - 1),
                new MountPrices.Seen("Horse Saddle", 1000, 2 * LE, NOW - 60_000L));
        assertNull(MountPrices.estimate(all, WYVERN, 1000, NOW), "no name-only fallback for a mount");
        assertNull(MountPrices.estimate(List.of(), WYVERN, 240, NOW));
    }

    @Test
    void theSameListingHoveredAgainIsKeptOnce() {
        List<MountPrices.Seen> list = new ArrayList<>();
        assertTrue(MountPrices.add(list, new MountPrices.Seen(WYVERN, 1000, 22 * LE, NOW)));
        assertFalse(MountPrices.add(list, new MountPrices.Seen(WYVERN, 1000, 22 * LE, NOW + 1000)), "every frame: nothing new");
        assertTrue(MountPrices.add(list, new MountPrices.Seen(WYVERN, 1000, 22 * LE, NOW + 120_000)));
        assertEquals(1, list.size());
        assertEquals(NOW + 120_000, list.get(0).at());
        assertTrue(MountPrices.add(list, new MountPrices.Seen(WYVERN, 990, 22 * LE, NOW)));
        assertEquals(2, list.size());
    }

    @Test
    void theStoreKeepsTheNewest() {
        List<MountPrices.Seen> list = new ArrayList<>();
        for (int i = 0; i <= MountPrices.MAX; i++) {
            MountPrices.add(list, new MountPrices.Seen(WYVERN, i, 100, NOW + i));
        }
        assertEquals(MountPrices.MAX, list.size());
        assertEquals(1, list.get(0).potential(), "the oldest went");
    }

    @Test
    void englishText() {
        language(1);
        assertEquals("~5.5 LE for ~1,000 potential (3 listings)",
                MountPrices.text(new MountPrices.Estimate(5.5 * LE, 1000, 3)));
        assertEquals("~14 EB for ~240 potential (1 listing)", MountPrices.text(new MountPrices.Estimate(14 * 64, 240, 1)));
        assertEquals("Depends on potential", MountPrices.text((io.github.anilken.wynnlabs.model.Mount) null));
    }

    @Test
    @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")
    void turkishText() {
        language(2);
        assertEquals("~1.000 Potential için ~5.5 LE (3 ilan)", MountPrices.text(new MountPrices.Estimate(5.5 * LE, 1000, 3)));
    }
}
