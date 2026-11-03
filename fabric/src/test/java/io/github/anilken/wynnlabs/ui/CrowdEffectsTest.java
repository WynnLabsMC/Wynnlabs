package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CrowdEffectsTest {

    @Test
    void theShippedTableNamesTheEffects() {
        assertEquals("totem ring on the ground", CrowdEffects.effect("911e742de86f65a408f2139db7a72a73eb224f65728a10d05ee3b0a45423a660"));
        assertEquals("flames", CrowdEffects.effect("e86922374529a215525cc6d85415dcd45452fdb5ea39f1d3fccf9f4eaa4d9f33"));
        assertNull(CrowdEffects.effect("0".repeat(64)));
        assertNull(CrowdEffects.effect(null));
    }
}
