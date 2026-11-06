package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.model.ItemView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A powder is priced and listed under its element and tier, as the market keeps it. */
class PowderKeyTest {

    @Test
    void powderByElementAndTier() {
        ItemView fire = new ItemView(ItemView.Kind.POWDER, "PowderItem", false, "\u2739 Fire Powder V", null, null, null, null,
                null, null, null, new ItemView.Powder("FIRE", 5), null, null, null, null, null);
        assertEquals("Fire Powder T5", Prices.key(fire, null));
    }

    @Test
    void amplifierByTier() {
        ItemView amp = new ItemView(ItemView.Kind.OTHER, "AmplifierItem", false, "Corkian Amplifier II", null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertEquals("Corkian Amplifier T2", Prices.key(amp, null));
    }
}
