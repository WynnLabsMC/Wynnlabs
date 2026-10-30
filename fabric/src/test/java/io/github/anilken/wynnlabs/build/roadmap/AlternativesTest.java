package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.core.Perf;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The endgame milestones' other whole builds (Alternatives): from Lv 100 on, besides the pick,
 * up to ten builds the search ended on, each another weapon, another main element or way of
 * fighting, or three pieces apart from the pick and from each other; the milestone as if one of
 * them were its pick; the cards saved with the map and read back; and for a pick that needs the
 * assumed guild tome, the best build without it among them.
 */
@Tag("heavy")
class AlternativesTest {

    @TempDir
    Path tmp;

    @AfterEach
    void reset() throws Exception {
        Saver.flush();
        Roadmap.forget();
        Locks.rootOverride = null;
        FixtureState.uninstall();
    }

    private static String names(Map<String, Damage.Piece> set) {
        StringBuilder b = new StringBuilder();
        set.forEach((slot, p) -> b.append(' ').append(slot).append('=').append(p == null ? "-" : p.name()));
        return b.toString();
    }

    private static void dump(String who, List<Roadmap.Step> steps) {
        for (Roadmap.Step s : steps) {
            if (s.level() < 100) {
                continue;
            }
            Fixtures.note(who + " Lv " + s.level() + ": " + s.alternatives().size() + " cards");
            for (int k = 0; k < s.alternatives().size(); k++) {
                Roadmap.Alternative a = s.alternatives().get(k);
                Roadmap.Step as = s.as(k);
                Fixtures.note(String.format(Locale.ROOT,
                        "  %d %s | gap %.1f%% | spell %.0f, ehp %.0f, heal %.0f/s | own %d/%d, rest %.0f (%d unpriced)%s%s%s |%s",
                        k, a.label(), a.gapPct(), a.spellDps(), a.ehp(), a.healPerSecond(), a.owned(), a.pieces(), a.price(),
                        a.unpriced(), a.tome() == null ? "" : " tome " + a.tome().text(), a.needsTome() ? " NEEDS TOME" : "",
                        a.noTome() ? " NO-TOME" : "", names(as.build())));
            }
        }
    }

    /** The cards hold together: the pick first, each other build set apart, and the milestone shown as each. */
    private static void check(List<Roadmap.Step> steps) {
        for (int i = 0; i < steps.size(); i++) {
            Roadmap.Step s = steps.get(i);
            List<Roadmap.Alternative> alts = s.alternatives();
            if (s.level() < 100) {
                assertTrue(alts.isEmpty(), "no cards below Lv 100 (Lv " + s.level() + ")");
                continue;
            }
            if (alts.isEmpty()) {
                continue;
            }
            assertTrue(alts.size() >= 2 && alts.size() <= 11, "Lv " + s.level() + ": " + alts.size());
            assertNull(alts.get(0).step(), "the first card is the pick");
            assertEquals(0, alts.get(0).gapPct(), 1e-9);
            assertSame(s, s.as(0));
            java.util.Set<String> keys = new java.util.HashSet<>(List.of(SetJudge.setKey(s.build())));
            for (int k = 1; k < alts.size(); k++) {
                Roadmap.Step as = s.as(k);
                assertNotNull(as.set(), "card " + k + " has its spread");
                assertTrue(keys.add(SetJudge.setKey(as.build())), "card " + k + " is another set");
                assertEquals(s.abilities(), as.abilities());
                assertSame(alts, as.alternatives(), "the choice stays open");
                if (!alts.get(k).noTome()) {
                    // apart from the pick: another weapon, or three pieces, or another way of dealing damage
                    boolean otherWeapon = !alts.get(k).weapon().equalsIgnoreCase(s.build().get("WEAPON").name());
                    int d = BuildRanking.distance(as.build(), s.build(), java.util.Set.of());
                    assertTrue(otherWeapon || d >= Alternatives.MIN_PIECES || !alts.get(k).label().equals(alts.get(0).label()),
                            "card " + k + " is set apart from the pick");
                }
                // every row's piece is in the set it shows
                java.util.Set<String> in = new java.util.HashSet<>();
                as.build().values().forEach(p -> in.add(p.name()));
                int card = k;
                as.gear().forEach(g -> assertTrue(in.contains(g.info().name()), g.info().name() + " not in card " + card));
                List<Roadmap.Step> shown = Roadmap.withAlternative(steps, i, k);
                assertSame(as.build(), shown.get(i).build());
                int other = i == 0 ? 1 : 0;
                assertTrue(steps.get(other) == shown.get(other), "the other milestones stay as they are");
            }
        }
    }

    @Test
    void theShamansEndgameOffersOtherBuilds() throws Exception {
        FixtureState.load(SharedRoadmaps.shamanDir()).install("en");
        Locks.rootOverride = tmp;
        Roadmap.forget();
        Perf.reset();
        Perf.on = true;
        long t0 = System.nanoTime();
        List<Roadmap.Step> steps = Roadmap.build();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Perf.on = false;
        Fixtures.note("shaman-44 road map " + ms + " ms; perf " + Perf.json());
        dump("shaman-44", steps);
        check(steps);
        Roadmap.Step top = steps.get(steps.size() - 1);
        assertTrue(top.level() >= 110);
        assertFalse(top.alternatives().isEmpty(), "the Lv " + top.level() + " milestone offers other builds");
        // saved with the map and read back
        Saver.flush();
        RoadmapStore.Loaded l = RoadmapStore.load(GameState.current().characterId());
        assertNotNull(l);
        Roadmap.Step back = l.steps().get(l.steps().size() - 1);
        assertEquals(top.alternatives().size(), back.alternatives().size());
        for (int k = 0; k < top.alternatives().size(); k++) {
            assertEquals(top.alternatives().get(k).label(), back.alternatives().get(k).label());
            assertEquals(SetJudge.setKey(top.as(k).build()).replaceAll("#[^,;|]*", ""),
                    SetJudge.setKey(back.as(k).build()).replaceAll("#[^,;|]*", ""));
        }
        // the local API's /roadmap lists them
        var json = RoadmapJson.live(steps, 0, false);
        var last = json.getAsJsonArray("milestones").get(steps.size() - 1).getAsJsonObject();
        assertEquals(top.alternatives().size(), last.getAsJsonArray("alternatives").size());
    }

    /** Debug only (-PdebugDump): the Shaman's map built cold without the other builds (rules.roadmap.alternatives 0), for the time they add. */
    @Test
    void timeWithoutThem() throws Exception {
        assumeTrue(Boolean.getBoolean("wynnlabs.debugDump"), "debug dump off");
        FixtureState.load(SharedRoadmaps.shamanDir()).install("en");
        com.google.gson.JsonObject change = new com.google.gson.JsonObject();
        com.google.gson.JsonObject road = new com.google.gson.JsonObject();
        road.addProperty("alternatives", 0);
        change.add("roadmap", road);
        io.github.anilken.wynnlabs.core.Rules.update(change);
        Locks.rootOverride = tmp;
        Roadmap.forget();
        long t0 = System.nanoTime();
        List<Roadmap.Step> steps = Roadmap.build();
        Fixtures.note("shaman-44 road map without alternatives " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        assertTrue(steps.stream().allMatch(s -> s.alternatives().isEmpty()));
    }

    /** A Lv 107 Light Bender healer (HealerRespecTest): Lv 110 and 120 offer other builds, the one without the tome among them when the pick needs it. */
    @Test
    void theHealersEndgameOffersOtherBuilds() throws Exception {
        io.github.anilken.wynnlabs.build.HealerRespecTest.installHealer();
        Locks.rootOverride = tmp;
        Roadmap.forget();
        long t0 = System.nanoTime();
        List<Roadmap.Step> steps = Roadmap.build();
        Fixtures.note("healer road map " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        dump("healer", steps);
        check(steps);
        for (Roadmap.Step s : steps) {
            if (s.level() < 100 || s.alternatives().isEmpty()) {
                continue;
            }
            Roadmap.Alternative pick = s.alternatives().get(0);
            if (pick.needsTome()) {
                assertTrue(s.alternatives().stream().anyMatch(Roadmap.Alternative::noTome),
                        "Lv " + s.level() + ": the pick needs the tome, so the best build without it is offered");
            }
            for (Roadmap.Alternative a : s.alternatives()) {
                if (a.noTome()) {
                    Build.Eval e = s.as(s.alternatives().indexOf(a)).set();
                    assertTrue(e.tome() == null || !e.tome().assumed(), "the build without the tome assumes none");
                }
            }
        }
    }
}
