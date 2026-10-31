package io.github.anilken.wynnlabs.advice;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.Fixture;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The advice stays one story from box to identified piece: the recorded Shaman at Lv 44 runs
 * Generator Amulet, and Dogfight (Lv 42) helps its build a little at a middle roll, but not by
 * the gain the plan asks of a swap even at its best roll. So neither the box nor the revealed
 * piece is identified to wear it, and the box goes by its value. Identifying only to sell must
 * also reach the Trade Market minimum (2 LE here, as the player has it).
 */
@Tag("heavy")
class PlanFitTest {

    private static final ItemView.Box NECKLACE_BOX = new ItemView.Box(Slot.NECKLACE, Tier.LEGENDARY, 41, 45);
    private static final double MIN_PRICE = 8192;

    @AfterEach
    void tearDown() {
        FixtureState.uninstall();
    }

    /** shaman-44 with the necklace box holding Dogfight, and these prices added. */
    private static void install(Path tmp, Map<String, Prices.Price> extra) throws Exception {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        Fixture.Data d = Fixture.read(dir);
        Map<String, List<String>> boxes = new LinkedHashMap<>(d.gearDb().boxes());
        boxes.put("NECKLACE:LEGENDARY:41-45", List.of("Dogfight"));
        Map<String, Prices.Price> prices = new LinkedHashMap<>(d.prices());
        prices.putAll(extra);
        Fixture.GearDb db = new Fixture.GearDb(d.gearDb().format(), d.gearDb().gear(), d.gearDb().obtain(), boxes);
        Path out = tmp.resolve("fx");
        Fixture.write(new Fixture.Data(d.state(), d.items(), db, prices, d.rules(), d.settings(), d.tree()), out, null);
        FixtureState.load(out).install("en");
        JsonObject trade = new JsonObject();
        trade.addProperty("minTradeMarketPrice", MIN_PRICE);
        trade.addProperty("revealMinProfit", 640);
        JsonObject change = new JsonObject();
        change.add("trade", trade);
        Rules.update(change);
    }

    private static ItemData dogfight() {
        return GameState.current().allGear().filter(i -> i.name().equals("Dogfight")).findFirst().orElseThrow();
    }

    /** A revealed Dogfight: named, stats "?". */
    private static ItemView unidentified(ItemData d) {
        return new ItemView(ItemView.Kind.GEAR, "GearItem", false, null, null, null, d, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static long recordedAt() throws Exception {
        return Fixture.read(Fixtures.root().resolve("shaman-44")).state().recordedAt();
    }

    @Test
    void thePlanDoesNotWantDogfightEvenAtItsBestRoll(@TempDir Path tmp) throws Exception {
        install(tmp, Map.of());
        PlanFit.Fit fit = PlanFit.of(dogfight(), 44);
        assertNotNull(fit, "a plan to ask");
        assertFalse(fit.wanted(), "under the plan's swap gain at its best roll");
        assertEquals("Generator Amulet", fit.rival());
    }

    @Test
    void aBoxOnlyThePlanWouldSkipIsSoldByItsValue(@TempDir Path tmp) throws Exception {
        long at = recordedAt();
        // cheap: under the 2 LE minimum, to the Blacksmith
        install(tmp, Map.of("Dogfight", new Prices.Price(1500, 1500.0, 1500.0, 1000, 1000.0, at, at, 10)));
        BoxAdvice.Decision cheap = BoxAdvice.decide(NECKLACE_BOX, Klass.SHAMAN, 44);
        assertNotNull(cheap.choice());
        assertEquals(BoxRoutes.Route.BLACKSMITH, cheap.choice().route(), cheap.entry().note());
        assertTrue(cheap.entry().note().contains("Your build runs Generator Amulet here"), cheap.entry().note());
        FixtureState.uninstall();
        // worth listing: the Trade Market's reveal, not the Item Identifier
        install(tmp.resolve("b"), Map.of("Dogfight", new Prices.Price(30000, 30000.0, 30000.0, 20000, 20000.0, at, at, 10)));
        BoxAdvice.Decision dear = BoxAdvice.decide(NECKLACE_BOX, Klass.SHAMAN, 44);
        assertNotNull(dear.choice());
        assertEquals(BoxRoutes.Route.REVEAL_MARKET, dear.choice().route(), dear.entry().note());
    }

    @Test
    void anUnidentifiedPieceThePlanBeatsIsNotIdentified(@TempDir Path tmp) throws Exception {
        long at = recordedAt();
        install(tmp, Map.of("Dogfight", new Prices.Price(1500, 1500.0, 1500.0, 1000, 1000.0, at, at, 10)));
        ItemView gear = unidentified(dogfight());
        Advice.Entry e = AutoAdvice.decide(gear, new AutoAdvice.StackFacts(null, "Dogfight", 1));
        assertNotNull(e);
        assertNotEquals(Verdict.IDENTIFY, e.verdict(), e.note());
        assertEquals(Verdict.SELL, e.verdict(), e.note());
        assertTrue(e.note().startsWith("Your build runs Generator Amulet here: not worth the ID fee"), e.note());
    }

    @Test
    void identifyingToSellMustReachTheTradeMarketMinimum(@TempDir Path tmp) throws Exception {
        long at = recordedAt();
        // ID'd copies fetch far more than unID'd ones, but still under 2 LE: no fee, the Blacksmith
        install(tmp, Map.of("Dogfight", new Prices.Price(6000, 6000.0, 6000.0, 100, 100.0, at, at, 10)));
        int cost = Tier.LEGENDARY.identificationCost(42);
        assertTrue(SellAdvice.identifyPays(100.0, 6000.0, cost, SellAdvice.marketFee(), 640), "the fee alone would pay");
        Advice.Entry low = SellAdvice.sell(unidentified(dogfight()), "why");
        assertEquals(Verdict.SELL, low.verdict(), low.note());
        assertTrue(low.note().contains("minimum"), low.note());
        FixtureState.uninstall();
        // over the minimum identified: identify, then sell on the Trade Market
        install(tmp.resolve("b"), Map.of("Dogfight", new Prices.Price(20000, 20000.0, 20000.0, 100, 100.0, at, at, 10)));
        Advice.Entry high = SellAdvice.sell(unidentified(dogfight()), "why");
        assertEquals(Verdict.IDENTIFY, high.verdict(), high.note());
    }
}
