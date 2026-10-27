package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Fixture;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.model.ItemData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A piece nothing says how to get (Ornate Shadow Cloak in the recorded item data) is never
 * offered as a buy: only the Trade Market's price makes one buyable, and one you have is kept
 * and said to be of unknown source.
 */
class UnobtainableTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
        BuildSources.forgetReads();
    }

    private static FixtureState shaman() throws Exception {
        FixtureState s = FixtureState.load(Fixtures.root().resolve("shaman-44"));
        s.install("en");
        BuildSources.forgetReads();
        return s;
    }

    private static ItemData gear(String name) {
        return GameState.current().allGear().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aPieceWithNoKnownSourceIsLeftOut() throws Exception {
        shaman();
        ItemData cloak = gear("Ornate Shadow Cloak");
        assertTrue(Shopping.unknownSource(cloak));
        assertFalse(Shopping.tradable(cloak));
        // left out with no rule asking for it
        assertTrue(Shopping.excluded(cloak, List.of()), "an untradable piece with no source cannot be had");
        // a piece with a known source is not
        ItemData known = GameState.current().allGear()
                .filter(i -> !Shopping.unknownSource(i) && i.restriction().equals("NONE")
                        && GameState.current().obtain(i).stream().anyMatch(o -> o.type().equals("MERCHANT")))
                .findFirst().orElseThrow();
        assertFalse(Shopping.excluded(known, List.of()), known.name());
    }

    @Test
    void aTradablePieceWithAPriceCanBeBought() throws Exception {
        FixtureState s = shaman();
        ItemData masterwork = gear("Masterwork Olympic");
        assertTrue(Shopping.unknownSource(masterwork));
        assertTrue(Shopping.tradable(masterwork));
        assertTrue(Shopping.excluded(masterwork, List.of()), "no source and no price: nothing says it can be had");
        s.data.prices().put(masterwork.name(), new Prices.Price(200_000, 250_000.0, 250_000.0, null, null,
                GameState.current().now(), GameState.current().now(), 3));
        assertFalse(Shopping.excluded(masterwork, List.of()), "the Trade Market sells it");
        // the rule list still has the last word
        assertTrue(Shopping.excluded(masterwork, List.of("UNKNOWN")));
    }

    @Test
    void aPieceYouHaveIsKeptAndSaysItsSourceIsUnknown(@TempDir Path tmp) throws Exception {
        // Diverted Loyalty: in the recorded bank, no known source, no price; carried here
        Fixture.Data d = Fixture.read(Fixtures.root().resolve("shaman-44"));
        Fixture.Items it = d.items();
        Fixture.BankSlot banked = it.bank().stream().filter(b -> b.name().equals("Diverted Loyalty")).findFirst().orElseThrow();
        List<Fixture.Held> stacks = new ArrayList<>(it.stacks());
        stacks.add(new Fixture.Held(banked.name(), banked.count(), banked.view()));
        List<Integer> bag = new ArrayList<>(it.inventory());
        bag.set(bag.indexOf(null), stacks.size() - 1);
        Fixture.write(new Fixture.Data(d.state(), new Fixture.Items(stacks, bag, it.armour(), it.accessories(),
                it.mainHand(), it.bank()), d.gearDb(), d.prices(), d.rules(), d.settings(), d.tree()), tmp, null);
        FixtureState.load(tmp).install("en");
        BuildSources.forgetReads();

        ItemData mine = gear("Diverted Loyalty");
        assertTrue(Shopping.unknownSource(mine));
        assertTrue(Shopping.excluded(mine, List.of()), "not a buy");
        assertTrue(Roadmap.ownedCounts(GameState.current()).containsKey(mine.name()), "but yours: the road map keeps it");
        assertEquals(L.t("wynnlabs.results.unknown_have"), BuildSources.sourceText(mine.name()));
        // one you do not have reads as before
        assertEquals(L.t("wynnlabs.results.untradable"), BuildSources.sourceText("Ornate Shadow Cloak"));
    }
}
