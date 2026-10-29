package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.model.MountRead;
import org.junit.jupiter.api.Test;

import java.util.List;

class MountBookTest {

    @Test
    void aMountForSaleInTheMerchantsShopIsNotYours() {
        assertTrue(MountBook.forSale(List.of("Wyvern Reins", "Potential: 240", "Price:", " - 1,536E (24EB)",
                "Left-Click to Buy", "SHIFT + Click to Buy 4 times")));
        assertNull(MountBook.source("ShopContainer", "Mount Merchant", false), "a shop is never a place your mounts are");
    }

    @Test
    void aMountListedOnTheTradeMarketIsNotYours() {
        // browsing, a search, a listing, the buy screen, your own sell page: none of them count
        for (String type : new String[] {"TradeMarketContainer", "TradeMarketSearchResultContainer", "TradeMarketSellContainer", null}) {
            assertNull(MountBook.source(type, "Trade Market", false));
        }
        assertTrue(MountBook.forSale(List.of("Wyvern Reins", "Price: 1,200²", "Click to Buy")));
    }

    @Test
    void theInventoryTheBankAndTheMountFeederAreYours() {
        assertEquals(MountBook.Where.BANK, MountBook.source("CharacterBankContainer", "Bank", true));
        assertEquals(MountBook.Where.ENCLOSURE, MountBook.source("MountFeederContainer", "Mount Feeder", false));
        assertEquals(MountBook.Where.ENCLOSURE, MountBook.source(null, "Mount Feeder", false));
        assertFalse(MountBook.forSale(List.of("Wyvern Reins", "Potential: 438", "Energy: 18/30")));
    }

    @Test
    void mountsSeenInAnyOtherMenuAreDroppedOnLoading() {
        MountRead r = new MountRead("WYVERN", "Wyvern", 240, 200, 200,
                List.of(new MountRead.Stat("SPEED", "Speed", 1, 10, 30)), false, "", "");
        List<MountBook.Seen> file = List.of(new MountBook.Seen(r, MountBook.Where.MENU, 1),
                new MountBook.Seen(r, MountBook.Where.INVENTORY, 2), new MountBook.Seen(r, null, 3));
        List<MountBook.Seen> kept = MountBook.owned(file);
        assertEquals(1, kept.size());
        assertEquals(MountBook.Where.INVENTORY, kept.get(0).where());
    }
}
