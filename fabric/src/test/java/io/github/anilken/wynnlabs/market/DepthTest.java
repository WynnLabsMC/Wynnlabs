package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.search.BoxMarket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepthTest {

    private static BoxMarket.Listing at(int price, int amount) {
        return new BoxMarket.Listing(false, null, List.of(), 0, amount, price, 0, false);
    }

    @Test
    void aFewCheapBeforeAWallAreCounted() {
        // 9 at 17EB 7E, then 455 at 2LE 13EB: someone holds the rest high
        var steps = Depth.ladder(List.of(at(1095, 3), at(1095, 2), at(1095, 4), at(9024, 231), at(9024, 2), at(9024, 224)));
        assertEquals(List.of(new Depth.Step(1095, 9), new Depth.Step(9024, 457)), steps);
        assertEquals(9, Depth.fewCheap(steps));
    }

    @Test
    void anEvenMarketHasNoWall() {
        assertEquals(0, Depth.fewCheap(Depth.ladder(List.of(at(50, 20), at(55, 30), at(60, 40)))));
        // plenty at the lowest price: no wall however dear the rest
        assertEquals(0, Depth.fewCheap(Depth.ladder(List.of(at(100, 200), at(900, 300)))));
        assertEquals(0, Depth.fewCheap(Depth.ladder(List.of(at(100, 1)))));
    }

    @Test
    void aStrayCheapListingDoesNotSetThePrice() {
        // Koi Oil: 1 at 1EB 3E, then 29 at 3EB 30E and thousands at 5EB 5E
        var steps = Depth.ladder(List.of(at(67, 1), at(222, 29), at(325, 960), at(325, 1024), at(330, 1058)));
        assertEquals(222, Depth.price(steps));
        assertEquals(50, Depth.price(Depth.ladder(List.of(at(50, 20), at(55, 30)))), "an even market keeps its lowest");
        assertEquals(0, Depth.price(List.of()));
    }

    @Test
    void possiblySoldListingsAreLeftOut() {
        var sold = new BoxMarket.Listing(false, null, List.of(), 0, 5, 10, 0, true);
        assertEquals(List.of(new Depth.Step(20, 1)), Depth.ladder(List.of(sold, at(20, 1))));
    }
}
