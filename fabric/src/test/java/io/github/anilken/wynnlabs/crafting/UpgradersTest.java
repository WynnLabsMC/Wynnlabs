package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradersTest {

    @Test
    void everyTownsUpgraderIsShipped() {
        assertEquals(28, Upgraders.all().size());
        assertTrue(Upgraders.all().stream().anyMatch(a -> a.x() == 493 && a.z() == -1621 && a.name().endsWith("Detlas")));
    }
}
