package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** One copy fills one slot of a build. */
class OwnedSlotsTest {

    private static final Damage.Piece RING = new Damage.Piece(null, "Messenger", "Messenger", Slot.RING, 118, Map.of(),
            Map.of(), 0, Map.of(), 0);
    private static final Map<String, Damage.Piece> TWICE = Map.of("RING1", RING, "RING2", RING);

    @Test
    void oneCopyFillsOneHand() {
        assertEquals(Set.of("RING1"), BuildSources.ownedSlots(TWICE, Map.of(), Map.of("Messenger", 1)));
    }

    @Test
    void twoCopiesFillBoth() {
        assertEquals(Set.of("RING1", "RING2"), BuildSources.ownedSlots(TWICE, Map.of(), Map.of("Messenger", 2)));
    }

    @Test
    void theWornRingIsTheCopyInTheBag() {
        assertEquals(Set.of("RING2"), BuildSources.ownedSlots(TWICE, Map.of("RING2", "Messenger"), Map.of("Messenger", 1)));
    }
}
