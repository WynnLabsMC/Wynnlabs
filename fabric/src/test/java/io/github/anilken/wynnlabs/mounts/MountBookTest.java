package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.model.MountRead;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    void onlyTheMountFeederIsReadAsAFeeder() {
        assertTrue(MountBook.feeder("MountFeederContainer", "Mount Feeder"));
        assertTrue(MountBook.feeder(null, "Mount Feeder"));
        assertFalse(MountBook.feeder("TradeMarketContainer", "Trade Market"));
        assertFalse(MountBook.feeder(null, "Mount Merchant"));
    }

    @Test
    void aFeederRowsTimeLeftRunsDownFromWhenItWasRead() {
        MountBook.Fed fed = new MountBook.Fed(2, List.of("Copper Ingot"), 600, 1_000_000, 5);
        assertEquals(600, fed.remainingNow(1_000_000));
        assertEquals(540, fed.remainingNow(1_060_000));
        assertEquals(0, fed.remainingNow(9_000_000));
        assertEquals(-1, new MountBook.Fed(2, List.of(), -1, 0, 5).remainingNow(5_000), "not read: no time");
    }

    private static MountRead fresh(String skin) {
        return new MountRead("WYVERN", "Wyvern", 240, 200, 200, List.of(new MountRead.Stat("SPEED", "Speed", 1, 10, 30)), false,
                "", "").with(skin, "Ready to Feed/Breed");
    }

    private static MountBook.Seen at(MountRead m, MountBook.Where w, String place) {
        return new MountBook.Seen(m, w, 1, null, place);
    }

    @Test
    void aMountBoughtThenPutInTheFeederIsOneMountInTheFeeder() {
        MountRead a = fresh("Azure-Horn");
        var book = MountBook.put(Map.of(), MountBook.inventoryKey("c1"), List.of(at(a, MountBook.Where.INVENTORY, null)));
        // the next reads: the bag without it, the feeder with it
        book = MountBook.put(book, MountBook.inventoryKey("c1"), List.of());
        book = MountBook.put(book, MountBook.feederKey("Ternaves"),
                List.of(at(a, MountBook.Where.ENCLOSURE, MountBook.feederKey("Ternaves"))));
        List<MountBook.Seen> all = MountBook.union(book);
        assertEquals(1, all.size());
        assertEquals(MountBook.Where.ENCLOSURE, all.get(0).where());
    }

    @Test
    void mountsAreTheAccountsAndEachCharactersBagIsItsOwnPlace() {
        String f = MountBook.feederKey("Ternaves");
        var book = MountBook.put(Map.of(), f, List.of(at(fresh("Azure-Horn"), MountBook.Where.ENCLOSURE, f)));
        book = MountBook.put(book, MountBook.inventoryKey("shaman"), List.of(at(fresh("Golden-Tusk"), MountBook.Where.INVENTORY, null)));
        // another character logs in: the list stays, its bag adds to it
        book = MountBook.put(book, MountBook.inventoryKey("mage"), List.of(at(fresh("Pearl-Fang"), MountBook.Where.INVENTORY, null)));
        assertEquals(3, MountBook.union(book).size());
        // the mage's empty bag replaces only the mage's
        book = MountBook.put(book, MountBook.inventoryKey("mage"), List.of());
        assertEquals(2, MountBook.union(book).size(), "the shaman's carried mount and the feeder's stay");
    }

    @Test
    void theOldPerCharacterFilesComeTogetherNewestFirst() {
        String f = MountBook.feederKey("Ternaves");
        var older = Map.of(f, List.of(new MountBook.Seen(fresh("Azure-Horn"), MountBook.Where.ENCLOSURE, 1, null, f)));
        var newer = Map.of(f, List.of(new MountBook.Seen(fresh("Golden-Tusk"), MountBook.Where.ENCLOSURE, 5, null, f)),
                MountBook.inventoryKey("mage"), List.of(at(fresh("Pearl-Fang"), MountBook.Where.INVENTORY, null)));
        var merged = MountBook.mergeAll(List.of(older, newer));
        assertEquals(2, MountBook.union(merged).size());
        assertEquals("Golden-Tusk", merged.get(f).get(0).mount().skin(), "the feeder as the newest file saw it");
    }

    @Test
    void twoIdenticalFreshMountsInTwoRowsAreTwoMounts() {
        MountRead a = fresh("Azure-Horn");
        String f = MountBook.feederKey("Ternaves");
        var book = MountBook.put(Map.of(), f, List.of(at(a, MountBook.Where.ENCLOSURE, f), at(a, MountBook.Where.ENCLOSURE, f)));
        assertEquals(2, MountBook.union(book).size(), "the same skin and stats: still two mounts");
    }

    @Test
    void twoEnclosuresAreKeptApartAndEachReadReplacesOnlyItsOwn() {
        String t = MountBook.feederKey("Ternaves");
        String a = MountBook.feederKey("Aldwell");
        var book = MountBook.put(Map.of(), t, List.of(at(fresh("Azure-Horn"), MountBook.Where.ENCLOSURE, t)));
        book = MountBook.put(book, a, List.of(at(fresh("Golden-Tusk"), MountBook.Where.ENCLOSURE, a)));
        assertEquals(2, MountBook.union(book).size());
        book = MountBook.put(book, a, List.of());
        assertEquals(1, MountBook.union(book).size(), "Aldwell read empty: Ternaves' mount stays");
    }

    @Test
    void aFileFromBeforePlacesIsDroppedAndTheBookFillsAgain() {
        assertTrue(MountBook.parse("[{\"where\":\"INVENTORY\"}]").isEmpty(), "the old list: dropped");
        String f = MountBook.feederKey("Ternaves");
        String json = "{\"places\":{\"" + f + "\":[{\"mount\":{\"type\":\"WYVERN\",\"name\":\"Wyvern\",\"potential\":240,"
                + "\"stats\":[{\"id\":\"SPEED\",\"name\":\"Speed\",\"level\":1,\"limit\":10,\"max\":30}]},\"where\":\"ENCLOSURE\",\"at\":1},"
                + "{\"mount\":{\"type\":\"WYVERN\",\"name\":\"Wyvern\",\"potential\":240,\"stats\":[]},\"where\":\"MENU\",\"at\":1}]}}";
        var book = MountBook.parse(json);
        assertEquals(1, MountBook.union(book).size(), "a shop's sighting (MENU) dropped");
    }

    @Test
    void aReadWithoutMaxesKeepsTheMaxesLastSeenInTheFeeder() {
        java.util.Map<String, int[]> known = new java.util.HashMap<>();
        MountRead inFeeder = new MountRead("WYVERN", "Wyvern", 438, 200, 200, List.of(new MountRead.Stat("SPEED", "Speed", 14, 22, 58)),
                false, "", "").with("Azure-Horn", null);
        MountBook.withKnownMax(inFeeder, true, known);
        // in the bag the stat line is "14/22" only: Wynntils guesses the max
        MountRead inBag = new MountRead("WYVERN", "Wyvern", 438, 200, 200, List.of(new MountRead.Stat("SPEED", "Speed", 14, 22, 30)),
                true, "", "").with("Azure-Horn", null);
        MountRead kept = MountBook.withKnownMax(inBag, false, known);
        assertEquals(58, kept.stats().get(0).max());
        assertFalse(kept.estimated());
        MountRead other = new MountRead("WYVERN", "Wyvern", 240, 200, 200, List.of(new MountRead.Stat("SPEED", "Speed", 1, 10, 30)),
                true, "", "").with("Golden-Tusk", null);
        assertEquals(30, MountBook.withKnownMax(other, false, known).stats().get(0).max(), "never seen in a feeder: as read");
    }

    @Test
    void theMountBankPageComesFromItsButtonsNames() {
        assertEquals(2, FeederRead.bankPage("< Page 1", "Page 3 >"));
        assertEquals(1, FeederRead.bankPage(null, "§fPage 2 >"));
        assertEquals(5, FeederRead.bankPage("< Page 4", null));
        assertEquals(-1, FeederRead.bankPage(null, null));
    }

    @Test
    void thePotentialComesFromTheLoreShortFormsToo() {
        assertEquals(438, FeederRead.potential(List.of("438 Potential")));
        assertEquals(1200, FeederRead.potential(List.of("1.2k Potential")));
        assertEquals(-1, FeederRead.potential(List.of("Energy 200/200")));
    }

    private static MountRead kind(String type) {
        return new MountRead(type, type, 240, 200, 200, List.of(new MountRead.Stat("SPEED", "Speed", 1, 10, 30)), false, "", "");
    }

    @Test
    void aUniversalFeederOnAHousingIslandIsAPlaceOfItsOwn() {
        // the island sits far from every enclosure; every feeder's menu is the same, so what is in it decides
        double x = 65487, z = 65575;
        String generic = "Feed, breed, and manage mounts";
        MountRead bantisus = kind("WYVERN");
        MountRead other = new MountRead("WYVERN", "Wyvern", 500, 200, 200, List.of(new MountRead.Stat("SPEED", "Speed", 1, 10, 40)),
                false, "", "");
        Map<String, List<MountBook.Seen>> book = Map.of("feeder:Bantisu Air Temple",
                List.of(at(bantisus, MountBook.Where.ENCLOSURE, "feeder:Bantisu Air Temple")));
        // a housing Wyvern feeder shows Bantisu's own wyverns
        assertEquals("feeder:Bantisu Air Temple", MountBook.feederPlace(generic,
                List.of(at(bantisus, MountBook.Where.ENCLOSURE, null)), x, z, book));
        // wyverns Bantisu does not hold: the Universal Feeder's own slots, whatever its name says
        assertEquals("feeder:Universal Feeder", MountBook.feederPlace(null, List.of(at(other, MountBook.Where.ENCLOSURE, null)), x, z, book));
        // mixed kinds: only a Universal Feeder holds them
        assertEquals("feeder:Universal Feeder", MountBook.feederPlace(generic, List.of(at(other, MountBook.Where.ENCLOSURE, null),
                at(kind("HORSE"), MountBook.Where.ENCLOSURE, null)), x, z, book));
        // its floating name, when read, names the guild's one apart
        assertEquals("feeder:Guild Universal Feeder", MountBook.feederPlace("Guild Universal Feeder",
                List.of(at(other, MountBook.Where.ENCLOSURE, null)), x, z, book));
        assertEquals("feeder:Guild Universal Feeder", MountBook.feederPlace("Guild Universal Feeder", List.of(), x, z, book));
        // a kind whose enclosure was never read: that enclosure's
        assertEquals("feeder:Ternaves", MountBook.feederPlace(null, List.of(at(kind("HORSE"), MountBook.Where.ENCLOSURE, null)), x, z, book));
        // an empty housing feeder: whose it is cannot be told, so no enclosure is wiped
        assertNull(MountBook.feederPlace(generic, List.of(), x, z, book));
        // in an enclosure, an empty feeder is that enclosure's
        assertEquals("feeder:Aldwell", MountBook.feederPlace(null, List.of(), -1210, -735, book));
    }

    @Test
    void aMountMovedFromAnEnclosureToTheUniversalFeederIsCountedOnce() {
        String ternaves = MountBook.feederKey("Ternaves"), universal = MountBook.feederKey("Guild Universal Feeder");
        MountRead horse = kind("HORSE");
        MountRead twin = kind("HORSE");
        var book = MountBook.put(Map.of(), ternaves, List.of(at(horse, MountBook.Where.ENCLOSURE, ternaves),
                at(twin, MountBook.Where.ENCLOSURE, ternaves)));
        // one of the two moved; the enclosure was not opened since
        book = MountBook.putFeeder(book, universal, List.of(at(horse, MountBook.Where.ENCLOSURE, universal)));
        assertEquals(2, MountBook.union(book).size(), "one in each place, the twin stays in Ternaves");
        assertEquals(1, book.get(ternaves).size());
        // the Universal Feeder opened again: its horse was there already, so the twin in Ternaves stays
        book = MountBook.putFeeder(book, universal, List.of(at(horse, MountBook.Where.ENCLOSURE, universal)));
        assertEquals(1, book.get(ternaves).size());
        book = MountBook.putFeeder(book, ternaves, List.of(at(twin, MountBook.Where.ENCLOSURE, ternaves)));
        assertEquals(1, book.get(universal).size(), "and Ternaves read again keeps the Universal Feeder's twin");
    }

    @Test
    void aReadUniversalNameWinsOverAStaleEnclosureSnapshot() {
        double x = 65487, z = 65575;
        MountRead wyvern = kind("WYVERN");
        Map<String, List<MountBook.Seen>> book = Map.of("feeder:Bantisu Air Temple",
                List.of(at(wyvern, MountBook.Where.ENCLOSURE, "feeder:Bantisu Air Temple")));
        assertEquals("feeder:Guild Universal Feeder", MountBook.feederPlace("Guild Universal Feeder",
                List.of(at(wyvern, MountBook.Where.ENCLOSURE, null)), x, z, book));
        // the name read loosely is still the same place; the personal one is "Universal Feeder"
        assertEquals("feeder:Guild Universal Feeder", MountBook.feederPlace("Yozgat's Guild Universal Feeder", List.of(), x, z, book));
        assertEquals("feeder:Universal Feeder", MountBook.feederPlace("Universal Feeder", List.of(), x, z, book));
        assertEquals("Guild Universal Feeder", MountBook.universalName("feeder:Guild Universal Feeder"));
        assertNull(MountBook.universalName("feeder:Ternaves"));
    }

    @Test
    void aMountOfUnknownKindDoesNotDropItsEnclosure() {
        MountRead odd = new MountRead("", "?", 240, 200, 200, List.of(new MountRead.Stat("SPEED", "Speed", 1, 10, 30)), false, "", "");
        var out = MountBook.withoutStrays(Map.of("feeder:Ternaves", List.of(at(kind("HORSE"), MountBook.Where.ENCLOSURE, "feeder:Ternaves"),
                at(odd, MountBook.Where.ENCLOSURE, "feeder:Ternaves"))));
        assertTrue(out.containsKey("feeder:Ternaves"));
    }

    @Test
    void anEnclosureFilledWithAnotherKindByAHousingFeederIsDropped() {
        Map<String, List<MountBook.Seen>> in = Map.of(
                "feeder:Ternaves", List.of(at(kind("WYVERN"), MountBook.Where.ENCLOSURE, "feeder:Ternaves")),
                "feeder:Bantisu Air Temple", List.of(at(kind("WYVERN"), MountBook.Where.ENCLOSURE, "feeder:Bantisu Air Temple")),
                "feeder:Guild Universal Feeder", List.of(at(kind("HORSE"), MountBook.Where.ENCLOSURE, "feeder:Guild Universal Feeder")));
        Map<String, List<MountBook.Seen>> out = MountBook.withoutStrays(in);
        assertFalse(out.containsKey("feeder:Ternaves"));
        assertTrue(out.containsKey("feeder:Bantisu Air Temple"));
        assertTrue(out.containsKey("feeder:Guild Universal Feeder"));
    }

    @Test
    void theFeedersNameIsTheLineUnderClickToOpen() {
        assertEquals("Guild Universal Feeder", FeederLabel.nameLine("\u00a7fClick to Open Feeder\n\u00a77Guild Universal Feeder"));
        assertEquals("Feed, breed, and manage mounts", FeederLabel.nameLine("Click to Open Feeder\nFeed, breed, and manage mounts"));
    }
}
