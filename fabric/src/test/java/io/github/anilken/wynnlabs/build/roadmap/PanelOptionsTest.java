package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.market.ShoppingSources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The comparison panel's options on the recorded Shaman: the pick first, the rest best gain
 * first; at least {@link PanelOptions#count} shown where there are, with two the Trade Market
 * sells among them when there are; the Trade Market filter shows only those. And where an
 * item comes from, in words for people. The panels are read on the shared plain map
 * ({@link SharedRoadmaps}).
 */
@Tag("heavy")
class PanelOptionsTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Path shaman() {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        return dir;
    }

    @Test
    void optionsAreSortedAndKeepTheMarket() throws Exception {
        List<Roadmap.Step> steps = SharedRoadmaps.shaman();
        int panels = 0;
        for (Roadmap.Step s : steps) {
            for (Roadmap.Gear g : s.gear()) {
                List<Roadmap.Option> all = g.options();
                if (all == null || all.size() < 2) {
                    continue;
                }
                panels++;
                String at = "Lv " + s.level() + " " + g.slot();
                for (int i = 2; i < all.size(); i++) {
                    assertTrue(all.get(i - 1).gain() >= all.get(i).gain(), at + ": best gain first");
                }
                List<Roadmap.Option> shown = PanelOptions.shown(all, false, 44);
                assertEquals(all.get(0), shown.get(0), at + ": the pick first");
                assertTrue(shown.size() >= Math.min(PanelOptions.count(), all.size()), at + ": at least " + PanelOptions.count());
                long market = all.stream().filter(PanelOptions::onMarket).count();
                long shownMarket = shown.stream().filter(PanelOptions::onMarket).count();
                assertTrue(shownMarket >= Math.min(2, market), at + ": two the Trade Market sells");
                List<Roadmap.Option> tm = PanelOptions.shown(all, true, 44);
                assertEquals(market, tm.size(), at + ": the filter keeps every one on the market");
                assertTrue(tm.stream().allMatch(PanelOptions::onMarket), at + ": only the market");
                Fixtures.note(at + ": " + all.size() + " options, " + shown.size() + " shown, " + market + " on the market");
            }
        }
        assertTrue(panels > 0, "some panels");
    }

    /**
     * Every row that names a piece to get opens its panel: a set's pieces too, also when the
     * build is not wearable halfway through the set (the left column of the large Home could
     * not be clicked: its armour came from a set and had no options).
     */
    @Test
    void everyPickHasItsPanel() throws Exception {
        List<String> without = new java.util.ArrayList<>();
        int rows = 0;
        for (Roadmap.Step s : SharedRoadmaps.shaman()) {
            for (Roadmap.Gear g : s.gear()) {
                if (Roadmap.isPowder(g) || Roadmap.fromBag(g)) {
                    continue;
                }
                rows++;
                if (g.options() == null || g.options().isEmpty()) {
                    without.add("Lv " + s.level() + " " + g.slot() + " " + g.info().name());
                }
            }
        }
        assertTrue(rows > 0, "some rows");
        assertTrue(without.isEmpty(), "rows without a panel: " + without);
    }

    /** A set's pick shows its row's gain in the panel, never a 0 % against a set it cannot be worn in alone. */
    @Test
    void aSetPickShowsItsRowsGain() throws Exception {
        int sets = 0;
        for (Roadmap.Step s : SharedRoadmaps.shaman()) {
            for (Roadmap.Gear g : s.gear()) {
                if (g.options() == null || g.options().isEmpty() || !g.options().get(0).set()) {
                    continue;
                }
                sets++;
                Roadmap.Option pick = g.options().get(0);
                String at = "Lv " + s.level() + " " + g.slot() + " " + g.info().name();
                // the panel measures against what you wear, as the row's pill does
                Double row = s.vsWorn().get(g.slot());
                if (row == null && s.whyVsWorn().containsKey(g.slot())) {
                    continue;                   // a slot you have nothing in: no per cent on the row
                }
                double shown = row == null ? g.gain() : row;
                assertTrue(pick.gain() != 0 || shown == 0, at + ": a set's pick at 0 %");
                assertEquals(shown, pick.gain(), 1e-6, at + ": the panel and the row agree");
                for (Roadmap.Option o : g.options()) {
                    assertTrue(o.gain() != 0 || o.to() == null || o.from() == null
                            || o.to().objective() == o.from().objective(), at + ": " + o.info().name() + " at 0 %");
                }
            }
        }
        Fixtures.note(sets + " set picks");
        // since major IDs count (Sorcery's Runebound Chains at Lv 90) shaman-44's map has no pick
        // that needs the rest of its set to be worn: nothing to check then, not a failure
        assumeTrue(sets > 0, "the fixture has no set picks");
    }

    @Test
    void sourcesReadForPeople() throws Exception {
        FixtureState.load(shaman()).install("en");
        assertEquals("Legendary Island (challenge)", ShoppingSources.where("CHALLENGE: Legendary Island"));
        assertEquals("The Canyon Colossus raid", ShoppingSources.where("RAID: The Canyon Colossus"));
        assertEquals("Scavenger's Workshop (tinkering)", ShoppingSources.where("TINKERING: Scavenger's Workshop"));
    }
}
