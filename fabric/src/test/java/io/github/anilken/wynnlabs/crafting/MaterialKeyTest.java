package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialKeyTest {

    @Test
    void aMaterialIsPricedAsTheMarketNamesIt() {
        assertEquals("Mahseer Oil T3", Shopping.materialKey("Mahseer Oil", 3));
        assertEquals("Barley Grains T1", Shopping.materialKey("Barley Grains", 1));
        // recipes say "Planks", the market one "Plank"
        assertEquals("Redwood Plank T2", Shopping.materialKey("Redwood Planks", 2));
    }
}
