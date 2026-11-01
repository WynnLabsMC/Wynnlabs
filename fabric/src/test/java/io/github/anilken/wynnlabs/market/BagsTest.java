package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.core.Profile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The other characters' bags: counted and shown, this character's own never twice. */
class BagsTest {

    private static Vault.Stored stack(String key, String name, int count) {
        return new Vault.Stored(name, 1, key, 1L, count, false, "material", null, null, null, null, "", 0, null, null);
    }

    @BeforeEach
    void empty() {
        Vault.preview(Map.of());
    }

    @AfterEach
    void reset() {
        Vault.preview(Map.of());
    }

    @Test
    void anotherCharactersBagCountsAndIsNotABankPage() {
        String key = Vault.bagKey("alt");
        Vault.putBag(key, List.of(stack(key, "Copper Ingot", 16)));
        assertEquals(16, Vault.find("Copper Ingot").total());
        assertFalse(Vault.find("Copper Ingot").reachable(), "it cannot be taken out from here");
        assertEquals(List.of(key), Vault.bagKeys());
        assertTrue(Vault.pageKeys().isEmpty());
        assertEquals(0, Vault.count(), "a bag does not make the bank read");
    }

    @Test
    void thisCharactersBagIsTheLiveInventory() {
        String key = Vault.bagKey(Profile.id());
        Vault.putBag(key, List.of(stack(key, "Copper Ingot", 16)));
        assertNull(Vault.find("Copper Ingot"));
        assertTrue(Vault.bagKeys().isEmpty());
    }

    @Test
    void savedBagsReadBack() {
        String key = Vault.bagKey("alt");
        var back = Vault.pageOf(key, Vault.savedPage(List.of(stack(key, "Copper Ingot", 16))));
        assertEquals("Copper Ingot", back.get(0).name());
        assertEquals(16, back.get(0).count());
        assertEquals(key, back.get(0).page());
    }

    @Test
    void everySlotFitsABankPageAndTheHotbarIsLast() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 36; i++) {
            int s = Bags.slot(i);
            assertTrue(s % 9 < 8 && s / 9 < 5, "slot " + s);
            assertTrue(seen.add(s));
        }
        assertEquals(0, Bags.slot(9));
        assertTrue(Bags.slot(8) > Bags.slot(35));
    }
}
