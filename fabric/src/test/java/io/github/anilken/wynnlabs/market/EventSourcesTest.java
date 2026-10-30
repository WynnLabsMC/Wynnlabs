package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seasonal event gear is event gear however else it is listed: Prologue also sits at the Isles of
 * Fiction merchant, which only opens for the events, and the Ornaments are sold there alone; the
 * default "EVENT" rule leaves them all out. A merchant named after a world event (Golem's Arm) is
 * that world event, which the rule never hides.
 */
class EventSourcesTest {

    private static final List<String> DEFAULT = List.of("UNAVAILABLE", "RAID", "EVENT");

    @BeforeEach
    void install() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
    }

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static ItemData gear(String name) {
        return GameState.current().allGear().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void eventGearSoldAtTheEventMerchantIsEventGear() {
        for (String name : List.of("Prologue", "Never-Ending Nightmare", "Yellow Ornament", "Red Ornament")) {
            ItemData info = gear(name);
            assertTrue(ShoppingSources.origin(info).startsWith("EVENT"), name + ": " + ShoppingSources.origin(info));
            assertTrue(ShoppingSources.excluded(info, DEFAULT), name);
        }
        assertEquals("EVENT: Festival of the Heroes", ShoppingSources.origin(gear("Prologue")));
    }

    @Test
    void aWorldEventMerchantIsTheWorldEvent() {
        ItemData arm = gear("Golem's Arm");
        assertTrue(ShoppingSources.origin(arm).startsWith("WORLD_EVENT"), ShoppingSources.origin(arm));
        assertFalse(ShoppingSources.excluded(arm, DEFAULT), "world events are not seasonal events");
    }

    @Test
    void townMerchantsAndUnknownSourcesStayAsTheyWere() {
        assertEquals("MERCHANT: Entamis Village", ShoppingSources.origin(gear("Fuunyet")));
        // the Tribal set: nothing says where it comes from, so it stays hidden
        ItemData tribal = gear("Tribal Cap");
        assertTrue(ShoppingSources.unknownSource(tribal));
        assertTrue(ShoppingSources.excluded(tribal, DEFAULT));
    }
}
