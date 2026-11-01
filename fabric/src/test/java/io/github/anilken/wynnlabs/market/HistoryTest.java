package io.github.anilken.wynnlabs.market;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.game.Fixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The box window's price history on a canned Wynnventory answer: what is kept (this item only, no
 * shinies, a missing median as none), the four numbers and the week's change, the one-request-a-day
 * rule, and the local API's shape. Requests go to a recorder instead of the network.
 */
class HistoryTest {

    /** Records what would be sent. */
    static final class Recorder implements History.Sender {
        boolean live = true;
        final List<String> sent = new ArrayList<>();

        @Override
        public boolean live() {
            return live;
        }

        @Override
        public boolean queue(String name) {
            if (!live) {
                return false;
            }
            sent.add(name);
            return true;
        }
    }

    /** The answer was read on Oct 31, 2026 at noon UTC. */
    private static final long READ_AT = Instant.parse("2026-10-31T12:00:00Z").toEpochMilli();

    private History.Sender before;
    private Recorder rec;

    @BeforeEach
    void setUp() {
        before = History.sender;
        rec = new Recorder();
        History.sender = rec;
        History.clear();
    }

    @AfterEach
    void tearDown() {
        History.sender = before;
        History.clear();
    }

    static JsonArray answer() throws IOException {
        String text = Files.readString(Fixtures.root().resolve("wynnventory").resolve("history-idol.json"), StandardCharsets.UTF_8);
        return new Gson().fromJson(text, JsonArray.class);
    }

    private static History.Series idol() throws IOException {
        return new History.Series(History.parse("Idol", answer()).days(), READ_AT);
    }

    @Test
    void anAnswerKeepsThisItemOnlyWithoutShinies() throws IOException {
        List<History.Day> days = idol().days();
        assertEquals(4, days.size());                   // the shiny and "Idolatry" left out
        assertEquals(LocalDate.of(2026, 10, 18), days.get(0).date());
        assertEquals(LocalDate.of(2026, 10, 28), days.get(3).date());
        History.Day oct24 = days.get(2);
        assertEquals(1620049, oct24.p50());
        assertEquals(540016, oct24.lowest());
        assertEquals(17550540, oct24.highest());
        assertEquals(181, oct24.count());
        History.Day empty = days.get(1);
        assertNull(empty.p50());                        // null in the answer: no median, not 0
        assertNull(empty.lowest());
        assertEquals(0, empty.count());
    }

    @Test
    void theFourNumbersAndTheWeek() throws IOException {
        History.Series s = idol();
        assertEquals(LocalDate.of(2026, 10, 28), s.lastSold().date());
        assertEquals(1800000, s.lastSold().p50());
        assertEquals(500000, s.lowest());
        assertEquals(1620049, s.typical());             // the median of 1.5M, 1.62M and 1.8M
        assertEquals(331, s.sold());
        // the last 7 days (Oct 28) against the 7 before (Oct 18 and 24, median 1 560 024.5)
        double before = (1500000 + 1620049) / 2.0;
        assertEquals((1800000 - before) / before * 100, s.weekChange(), 1e-9);
        assertTrue(s.drawable());
    }

    @Test
    void noWeekChangeWithoutBothWeeks() throws IOException {
        List<History.Day> recentOnly = idol().days().stream().filter(d -> d.date().isAfter(LocalDate.of(2026, 10, 24))).toList();
        assertNull(new History.Series(recentOnly, READ_AT).weekChange());
        History.Series none = new History.Series(History.parse("Idol", new JsonArray()).days(), READ_AT);
        assertNull(none.weekChange());
        assertNull(none.lastSold());
        assertNull(none.lowest());
        assertNull(none.typical());
        assertEquals(0, none.sold());
        assertFalse(none.drawable());
    }

    @Test
    void aSeriesIsAskedForOnceADay() throws IOException {
        long now = System.currentTimeMillis();
        History.put("Idol", new History.Series(idol().days(), now - 2 * 60 * 60_000L));
        History.put("Atoll", new History.Series(idol().days(), now - 25 * 60 * 60_000L));
        History.request("Idol");
        History.request("Atoll");
        History.request("Canopy");
        assertEquals(List.of("Atoll", "Canopy"), rec.sent);
    }

    @Test
    void noHistoryIsAnAnswerAndAFailureWaits() {
        History.put("Nothing", History.parse("Nothing", new JsonArray()));    // a 404
        History.failed("Busy", System.currentTimeMillis());                   // a 429
        History.request("Nothing");
        History.request("Busy");
        assertEquals(List.of(), rec.sent);
        assertNotNull(History.of("Nothing"));
        assertTrue(History.failed("Busy"));
        assertNull(History.of("Busy"));
        History.failed("Old", System.currentTimeMillis() - History.RETRY_MS - 1);
        History.request("Old");
        assertEquals(List.of("Old"), rec.sent);
    }

    @Test
    void lookupsOffSendNothing() {
        rec.live = false;
        History.request("Idol");
        assertEquals(List.of(), rec.sent);
        assertEquals("off", History.json("Idol").get("status").getAsString());
    }

    @Test
    void theRouteShape() throws IOException {
        JsonObject pending = History.json("Idol");
        assertEquals("pending", pending.get("status").getAsString());
        assertEquals(List.of("Idol"), rec.sent);
        History.put("Idol", idol());
        JsonObject o = History.json("Idol");
        assertEquals("Idol", o.get("item").getAsString());
        assertEquals(READ_AT, o.get("fetchedAt").getAsLong());
        JsonArray days = o.getAsJsonArray("days");
        assertEquals(4, days.size());
        JsonObject d = days.get(2).getAsJsonObject();
        assertEquals("2026-10-24", d.get("date").getAsString());
        assertEquals(1620049, d.get("p50").getAsInt());
        assertEquals(540016, d.get("lowest").getAsInt());
        assertEquals(17550540, d.get("highest").getAsInt());
        assertEquals(181, d.get("count").getAsInt());
        assertFalse(days.get(1).getAsJsonObject().has("p50"));
        JsonObject sum = o.getAsJsonObject("summary");
        assertEquals("2026-10-28", sum.getAsJsonObject("lastSold").get("date").getAsString());
        assertEquals(1800000, sum.getAsJsonObject("lastSold").get("p50").getAsInt());
        assertEquals(500000, sum.get("lowest").getAsInt());
        assertEquals(1620049, sum.get("typical").getAsInt());
        assertEquals(331, sum.get("sold").getAsInt());
        assertEquals(15.4, sum.get("weekChange").getAsDouble(), 1e-9);
        assertEquals(1, rec.sent.size());               // held: not asked again
    }
}
