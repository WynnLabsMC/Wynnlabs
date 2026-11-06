package io.github.anilken.wynnlabs.market;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** An item never seen listed takes the newest day of its market history, as a past price. */
class HistoryPriceTest {

    private static JsonArray days(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @Test
    void theNewestDayIsThePastPrice() {
        JsonArray d = days("""
                [{"name":"Fake Tooth","tier":0,"shiny":false,"lowest_price":20,"average_mid_80_percent_price":25.0,
                  "timestamp":"Wed, 28 Oct 2026 00:00:00 GMT"},
                 {"name":"Fake Tooth","tier":0,"shiny":false,"lowest_price":27,"average_mid_80_percent_price":27.0,
                  "timestamp":"Thu, 29 Oct 2026 00:00:00 GMT"},
                 {"name":"Fake Tooth","tier":0,"shiny":true,"lowest_price":900,"average_mid_80_percent_price":900.0,
                  "timestamp":"Fri, 30 Oct 2026 00:00:00 GMT"}]""");
        Prices.Price p = Prices.fromHistory("Fake Tooth", null, d, 1L);
        assertEquals(27.0, p.typical());
        assertEquals(Prices.Confidence.HISTORY, p.confidence(), "shown as a past price");
    }

    @Test
    void aTierOrNothing() {
        JsonArray d = days("""
                [{"name":"Copper Ingot","tier":1,"average_price":5.0,"timestamp":"Thu, 29 Oct 2026 00:00:00 GMT"}]""");
        assertNull(Prices.fromHistory("Copper Ingot", 3, d, 1L));
        assertEquals(5.0, Prices.fromHistory("Copper Ingot", 1, d, 1L).typical());
        assertNull(Prices.fromHistory("Fake Tooth", null, days("[]"), 1L));
    }
}
