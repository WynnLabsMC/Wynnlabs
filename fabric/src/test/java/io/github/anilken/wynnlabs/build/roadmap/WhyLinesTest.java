package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.game.Fixture;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A row's pill, its hover's headline, its "Why this?" lines and its comparison panel all measure
 * the pick against what you wear now. A Lv 97 spear read "+22 %" (over the milestone before)
 * above "Main Attack: 22 → 2295 (+10107 %)" (over the Lv 4 spear worn), and its panel, labelled
 * "Now", read −17 % against the set before the milestone's armour.
 */
class WhyLinesTest {

    /** "Main Attack: 22 → 2295 (+10107%)": from, to, the per cent. */
    private static final Pattern PCT = Pattern.compile("(\\d+) → (\\d+) \\(([+−-])(\\d+)%\\)");

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
    void shamanRowsTellOneNumber() throws Exception {
        // the shared plain map (SharedRoadmaps): other classes in this JVM read it too
        check(SharedRoadmaps.shaman());
    }

    @Test
    void warriorRowsTellOneNumber(@TempDir Path tmp) throws Exception {
        // the same character as a Warrior: the relik it wears is no weapon for it, so its far
        // milestones' spears are thousands of per cent over what it has, as in the report
        Fixture.Data d = FixtureState.load(shaman()).data;
        Fixture.State s = d.state();
        Fixture.State w = new Fixture.State(s.format(), s.recordedAt(), s.inGame(), Klass.WARRIOR, s.level(),
                s.characterId(), s.characters(), s.characterIds(), s.today(), s.skills(), s.skillsKnown(), s.unspent(),
                s.professions(), s.quests(), s.abilityPlan(), s.position(), s.spots(), s.xpRates(), s.pricesInstalled(),
                s.selectedSlot());
        Fixture.write(new Fixture.Data(w, d.items(), d.gearDb(), d.prices(), d.rules(), d.settings()), tmp, null);
        FixtureState.load(tmp).install("en");
        check(Roadmap.build());
    }

    @Test
    void anEmptySlotShowsAmountsNotAPerCent(@TempDir Path tmp) throws Exception {
        // nothing in the leggings slot: a per cent of nothing explodes (+407120 %), so the row has
        // no number and its reasons are plain amounts
        Fixture.Data d = FixtureState.load(shaman()).data;
        Fixture.Items it = d.items();
        Map<String, Integer> armour = new LinkedHashMap<>(it.armour());
        assumeTrue(armour.remove("LEGS") != null, "the fixture wears leggings");
        Fixture.Items bare = new Fixture.Items(it.stacks(), it.inventory(), armour, it.accessories(), it.mainHand(), it.bank());
        Fixture.write(new Fixture.Data(d.state(), bare, d.gearDb(), d.prices(), d.rules(), d.settings()), tmp, null);
        FixtureState.load(tmp).install("en");
        List<Roadmap.Step> steps = Roadmap.build();
        int legs = 0;
        for (int i = 0; i < steps.size(); i++) {
            for (Roadmap.Gear g : steps.get(i).gear()) {
                if (!g.slot().equals("LEGGINGS") || RoadmapRows.fromBag(g)) {
                    continue;
                }
                String where = "Lv " + steps.get(i).level() + " " + g.info().name();
                String pill = Roadmap.pill(Roadmap.shownGain(steps, i, g), g);
                List<String> why = Roadmap.shownWhy(steps, i, g);
                Fixtures.note(where + ": " + pill + " " + why);
                assertEquals("New", pill, where + ": no per cent against nothing");
                assertTrue(!why.isEmpty(), where + " says why");
                for (String line : why) {
                    assertTrue(!PCT.matcher(line).find(), where + ": a per cent of nothing in " + line);
                }
                legs++;
            }
        }
        assertTrue(legs > 0, "leggings rows");
    }

    private static void check(List<Roadmap.Step> steps) {
        assertTrue(!steps.isEmpty(), "steps");
        int checked = 0;
        int panels = 0;
        Map<String, Roadmap.Gear> latest = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            Roadmap.Step s = steps.get(i);
            s.gear().forEach(g -> latest.put(g.slot(), g));
            Roadmap.keepWorn(latest, s);
            for (Roadmap.Gear g : latest.values()) {
                double shown = Roadmap.shownGain(steps, i, g);
                if (Double.isNaN(shown) || RoadmapRows.fromBag(g)) {
                    continue;
                }
                String where = "Lv " + s.level() + " " + g.slot() + " " + g.info().name();
                List<String> why = Roadmap.shownWhy(steps, i, g);
                Fixtures.note(where + ": " + Math.round(shown * 10) / 10.0 + " % " + why);
                // the pill and the headline are the number against what you wear
                assertEquals(s.vsWorn().get(g.slot()), shown, 1e-9, where + ": the pill is against what you wear");
                // the reasons come from the same builds: a stat can move more than the whole
                // build, not by orders of magnitude more (five times, at least 100 points: a minor
                // part of the score can double), unless it starts from next to nothing (a Lv 4
                // spear's 22 main attack: ten times or more)
                double cap = Math.max(100, 5 * Math.abs(shown));
                for (String line : why) {
                    Matcher mt = PCT.matcher(line);
                    if (mt.find() && Double.parseDouble(mt.group(2)) < 10 * Double.parseDouble(mt.group(1))) {
                        assertTrue(Double.parseDouble(mt.group(4)) <= cap, where + " shows "
                                + Math.round(shown * 10) / 10.0 + " % but says " + line);
                    }
                }
                // the panel's first option is the pick, at the row's number, against the same build
                if (g.options() != null && !g.options().isEmpty()
                        && g.options().get(0).info().name().equals(g.info().name())) {
                    assertEquals(Math.round(shown * 10) / 10.0, Math.round(g.options().get(0).gain() * 10) / 10.0,
                            where + ": the panel agrees with the row");
                    panels++;
                }
                // a row never recommends a loss against what you wear
                assertTrue(shown > -0.1, where + " loses " + shown + " % against what you wear");
                checked++;
            }
        }
        // one piece's pill never carries the whole set's gain: two different pieces of one
        // milestone never share a number to the last digit (the whole set's +407120 % did)
        for (Roadmap.Step st : steps) {
            Map<Double, String> byGain = new LinkedHashMap<>();
            for (Roadmap.Gear g : st.gear()) {
                Double v = st.vsWorn().get(g.slot());
                if (v == null || v < 1) {
                    continue;
                }
                String other = byGain.putIfAbsent(v, g.info().name());
                assertTrue(other == null || other.equals(g.info().name()), "Lv " + st.level() + ": " + other + " and "
                        + g.info().name() + " both show " + v + " %");
            }
        }
        Fixtures.note(checked + " rows, " + panels + " panels checked");
        assertTrue(checked > 10 && panels > 5, checked + " rows, " + panels + " panels");
        assertTrue(oneEquippedBaseline(steps) > 0, "rows that pair with their milestone were checked");
    }

    /**
     * Every row a milestone first picks starts its reasons from the same build: what you have
     * equipped, at that milestone's level. A row that pairs with its milestone's other upgrades
     * once started from the milestone's set without it (Windborne: "Main Attack: 13257 → 19976"
     * under "+62.9 % over what you have equipped" while 22 was equipped). Returns how many such
     * pairing rows were seen.
     */
    private static int oneEquippedBaseline(List<Roadmap.Step> steps) {
        Pattern from = Pattern.compile("^(Main Attack|Effective HP): (\\d+) →");
        java.util.Set<String> seen = new java.util.HashSet<>();
        int pairs = 0;
        for (int i = 0; i < steps.size(); i++) {
            Roadmap.Step s = steps.get(i);
            String together = io.github.anilken.wynnlabs.core.L.t("wynnlabs.road.why_together", s.level());
            String better = io.github.anilken.wynnlabs.core.L.t("wynnlabs.road.why_better_together", s.level());
            Map<String, String> base = new LinkedHashMap<>();
            for (Roadmap.Gear g : s.gear()) {
                if (!seen.add(g.info().name()) || RoadmapRows.fromBag(g) || Double.isNaN(Roadmap.shownGain(steps, i, g))) {
                    continue;                   // a piece keeps the reasons of the milestone that first picked it
                }
                List<String> why = Roadmap.shownWhy(steps, i, g);
                if (!why.isEmpty() && (why.get(0).equals(together) || why.get(0).equals(better))) {
                    pairs++;
                }
                for (String line : why) {
                    Matcher mt = from.matcher(line);
                    if (mt.find()) {
                        String was = base.putIfAbsent(mt.group(1), mt.group(2));
                        assertTrue(was == null || was.equals(mt.group(2)), "Lv " + s.level() + " " + g.info().name()
                                + ": " + line + ", other rows start from " + was);
                    }
                }
            }
        }
        Fixtures.note(pairs + " pairing rows");
        return pairs;
    }
}
