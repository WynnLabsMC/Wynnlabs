package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExactPriceTest {

    @Test
    void pricesReadToTheEmerald() {
        assertEquals("2EB 49E", BoxListingsPanel.exact(177));
        assertEquals("27E", BoxListingsPanel.exact(27));
        assertEquals("1LE", BoxListingsPanel.exact(4096));
        assertEquals("1LE 12EB 3E", BoxListingsPanel.exact(4096 + 12 * 64 + 3));
        assertEquals("3stx 12LE", BoxListingsPanel.exact(3 * 262_144 + 12 * 4096));
        assertEquals("0E", BoxListingsPanel.exact(0));
        // tooltips draw coins as the game's glyphs; words with an E stay as they are
        assertEquals("~2¼² 52²½ · LEGENDARY Lv 75", io.github.anilken.wynnlabs.market.Prices.glyphs("~2LE 52EB · LEGENDARY Lv 75"));
        assertEquals("3stx 12.5¼², 30²", io.github.anilken.wynnlabs.market.Prices.glyphs("3stx 12.5LE, 30E"));
        // an item slot's corner: one coin
        assertEquals("40E", io.github.anilken.wynnlabs.market.Prices.slot(40));
        assertEquals("11EB", io.github.anilken.wynnlabs.market.Prices.slot(10 * 64 + 55));
        assertEquals("2.3LE", io.github.anilken.wynnlabs.market.Prices.slot(2.3 * 4096));
        assertEquals("1.2stx", io.github.anilken.wynnlabs.market.Prices.slot(1.2 * 262_144));
        // short: from a stack up, liquid emeralds to a tenth
        assertEquals("3stx 12.5LE", io.github.anilken.wynnlabs.market.Prices.fmt(3 * 262_144 + 12.5 * 4096));
        assertEquals("12LE 34EB", io.github.anilken.wynnlabs.market.Prices.fmt(12 * 4096 + 34 * 64 + 1));
        assertEquals("2LE 52EB", io.github.anilken.wynnlabs.market.Prices.fmt(2 * 4096 + 52 * 64 + 30));
    }
}
