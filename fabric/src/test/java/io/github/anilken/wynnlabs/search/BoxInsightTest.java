package io.github.anilken.wynnlabs.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class BoxInsightTest {

    private static BoxMarket.Listing l(double roll, int price) {
        return new BoxMarket.Listing(false, roll, List.of(), 0, 1, price, 0, false);
    }

    private static List<BoxMarket.Listing> market() {
        List<BoxMarket.Listing> out = new ArrayList<>();
        out.add(l(86, 640));
        out.add(l(80, 1280));
        out.add(l(79, 300));
        out.add(l(74, 480));
        out.add(l(64, 500));
        out.add(l(30, 450));
        return out;
    }

    @Test
    void identifyingIsWorthItWhenTheMeanAfterTaxBeatsTheFee() {
        assertEquals(BoxInsight.Answer.WORTH, BoxInsight.identify(200.0, 6, 8, 57, 57, 5).answer());
        assertEquals(BoxInsight.Answer.SELL, BoxInsight.identify(58.0, 6, 8, 57, 57, 5).answer());
    }

    @Test
    void tooFewPricesOrNoFeeIsSaidNotGuessed() {
        assertEquals(BoxInsight.Answer.FEW, BoxInsight.identify(200.0, 2, 8, 57, 57, 5).answer());
        assertEquals(BoxInsight.Answer.FEW, BoxInsight.identify(null, 0, 8, 57, 57, 5).answer());
        assertEquals(BoxInsight.Answer.NO_COST, BoxInsight.identify(200.0, 8, 8, 0, 0, 5).answer());
        assertEquals(BoxInsight.Answer.NO_COST, BoxInsight.identify(null, 0, 0, 57, 57, 5).answer());
    }

    @Test
    void theTopPercentCountsYoursAmongTheListings() {
        // 2 of 6 rolled higher than 79.5: yours is 3rd of 7, the top 43 %
        assertEquals(43, BoxInsight.topPercent(market(), 79.5));
        assertEquals(15, BoxInsight.topPercent(market(), 90));
    }

    @Test
    void noPercentOrPriceUnderFiveListings() {
        List<BoxMarket.Listing> few = market().subList(0, 4);
        assertNull(BoxInsight.topPercent(few, 72));
        assertNull(BoxInsight.suggest(few, 72));
    }

    @Test
    void possiblySoldAndUnidentifiedListingsDoNotCount() {
        List<BoxMarket.Listing> m = new ArrayList<>(market().subList(0, 4));
        m.add(new BoxMarket.Listing(true, null, List.of(), 0, 1, 200, 0, false));
        m.add(new BoxMarket.Listing(false, 50.0, List.of(), 0, 1, 200, 0, true));
        assertNull(BoxInsight.topPercent(m, 72));
    }

    @Test
    void theSuggestedPriceTakesTheThirdPlaceAmongAlikeRolls() {
        // alike to 76 (71 and up): 300, 480, 640, 1280 by price: one under the third
        BoxInsight.Suggestion s = BoxInsight.suggest(market(), 76);
        assertEquals(639, s.price());
        assertEquals(3, s.rank());
    }

    @Test
    void aRollAboveEveryListingIsPricedAgainstTheBestRolled() {
        // none within 5 of 99: the three best rolled (86, 80, 79) stand in
        BoxInsight.Suggestion s = BoxInsight.suggest(market(), 99);
        assertEquals(1279, s.price());
        assertEquals(3, s.rank());
    }
}
