package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListedPriceTest {

    @Test
    void aListingsPriceIsReadFromTheLineAfterPrice() {
        assertEquals(17719L, BoxMadePanel.listedPrice(List.of("3/3 Charges", "9m 15s Duration", "", "Price",
                "17,719 (4LE 20EB 55E)", "Left-Click to Buy")));
        // a glyph before the heading does not matter
        assertEquals(64L, BoxMadePanel.listedPrice(List.of(" Price", "64 (1EB)")));
    }

    @Test
    void noPriceBlockGivesNothing() {
        assertNull(BoxMadePanel.listedPrice(List.of("3/3 Charges", "Crafted by someone")));
        assertNull(BoxMadePanel.listedPrice(List.of()));
    }
}
