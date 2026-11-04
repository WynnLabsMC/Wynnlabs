package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.quests.Content;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.Discoveries;
import io.github.anilken.wynnlabs.quests.Nearest;
import io.github.anilken.wynnlabs.quests.QuestBook;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nearest list: one flood over a hand-made route grid ranks a place behind a wall after a
 * farther one in the open, done and over-level content is left out, the list falls back to
 * straight lines without the map, a result is reused for small moves and worked out again after
 * a longer one or newly done content, /nearest's JSON, a row's click, and one flood's cost on the
 * shipped world route map.
 */
class NearestTest {

    /** A cell's centre in blocks on the test grids (cells of 4 blocks). */
    private static int b(int cell) {
        return cell * 4 + 2;
    }

    private static Nearest.Candidate cave(String name, int level, int cx, int cz) {
        return new Nearest.Candidate(ContentType.CAVE, name, name, level, b(cx), 64, b(cz));
    }

    private static Content.Line line(ContentType t, String name, int level, String status, Integer x, int z) {
        Content.Place p = x == null ? null : new Content.Place(t, name, level, x, 64, z, "", "", "", List.of());
        return new Content.Line(t, name, level, status, "", p);
    }

    private static QuestBook.Entry quest(String name, String status, int level, Integer x, String type) {
        return new QuestBook.Entry(name, status, level, "Short", 0, true, "", x, x == null ? null : 64, x == null ? null : 10, null, 0,
                type, "Easy");
    }

    private static Discoveries.Entry discovery(String id, String name, int level) {
        return new Discoveries.Entry(id, name, "", 30, 70, 40, level, "Wynn", List.of(), "", List.of(), "");
    }

    @Test
    void aPlaceBehindAWallRanksAfterAFartherOneInTheOpen() throws IOException {
        WorldRoute g = WorldRouteTest.grid(
                "................",
                "................",
                "###############.",
                "................");
        // A: two cells away in a straight line, behind the wall; B: nine cells away in the open
        List<Nearest.Candidate> cands = List.of(cave("Behind", 10, 1, 3), cave("Open", 10, 10, 1));
        List<Nearest.Entry> ranked = Nearest.rank(cands, b(1), b(1), RouteReach.over(g));
        assertEquals("Open", ranked.get(0).c().name());
        assertEquals("Behind", ranked.get(1).c().name());
        assertFalse(ranked.get(1).estimate());
        assertTrue(ranked.get(1).straight() < ranked.get(0).straight(), "nearer in a straight line");
        assertTrue(ranked.get(1).walk() > 100, "the way round the wall: " + ranked.get(1).walk());
        assertEquals(36, ranked.get(0).walk(), 1e-9);
    }

    @Test
    void whatTheSearchDidNotReachFollowsAsAnEstimate() throws IOException {
        WorldRoute g = WorldRouteTest.grid(
                "................",
                "................");
        // off the mapped tile: no way on the map, straight-line nearer than the reached one
        List<Nearest.Candidate> cands = List.of(cave("Far", 1, 12, 0), cave("Off", 1, -3, 1));
        List<Nearest.Entry> ranked = Nearest.rank(cands, b(1), b(1), RouteReach.over(g));
        assertEquals("Far", ranked.get(0).c().name());
        assertEquals("Off", ranked.get(1).c().name());
        assertTrue(ranked.get(1).estimate());
        assertEquals(-1, ranked.get(1).walk());
        assertEquals(ranked.get(1).straight(), ranked.get(1).shown());
    }

    @Test
    void doneOverLevelAndPlacelessContentIsLeftOut() {
        List<QuestBook.Entry> quests = List.of(
                quest("Done Quest", "COMPLETED", 5, 100, "QUEST"),
                quest("Open Quest", "AVAILABLE", 30, 100, "QUEST"),
                quest("No Coords", "AVAILABLE", 30, null, "QUEST"),
                quest("Mini Post", "AVAILABLE", 20, null, "MINI_QUEST"),
                quest("Mini Unknown", "AVAILABLE", 20, null, "MINI_QUEST"));
        List<Content.Line> places = List.of(
                line(ContentType.CAVE, "Done Cave", 30, "COMPLETED", 10, 10),
                line(ContentType.CAVE, "Open Cave", 30, "UNKNOWN", 10, 10),
                line(ContentType.CAVE, "Bookonly Cave", 30, "AVAILABLE", null, 0),
                line(ContentType.DUNGEON, "High Dungeon", 60, "AVAILABLE", 50, 50),
                line(ContentType.WORLD_DISCOVERY, "A World Discovery", 0, "AVAILABLE", 20, 20));
        List<Discoveries.Entry> discs = List.of(discovery("ticked", "Ticked", 10), discovery("open-id", "Open Secret", 0));
        List<Nearest.Candidate> all = Nearest.gather(quests, n -> n.equals("Mini Post") ? new int[] {7, 64, 8} : null, places, discs,
                e -> e.id().equals("ticked"));
        List<String> names = all.stream().map(Nearest.Candidate::name).toList();
        assertEquals(List.of("Open Quest", "Mini Post", "Open Cave", "High Dungeon", "Open Secret"), names);
        assertEquals(ContentType.MINI_QUEST, all.get(1).kind());
        assertEquals(7, all.get(1).x());
        assertEquals("open-id", all.get(4).key(), "a discovery is opened by its id");
        // level 40: the level-60 dungeon is hidden unless asked for; no level (0) always shown
        List<String> fit = Nearest.fit(all, 40, false).stream().map(Nearest.Candidate::name).toList();
        assertEquals(List.of("Open Quest", "Mini Post", "Open Cave", "Open Secret"), fit);
        assertEquals(5, Nearest.fit(all, 40, true).size());
        assertEquals(5, Nearest.fit(all, 55, false).size(), "55 + 5 reaches 60");
    }

    @Test
    void withoutTheMapEveryEntryIsAStraightLineEstimate() {
        List<Nearest.Candidate> cands = List.of(cave("B", 1, 9, 0), cave("A", 1, 3, 0), cave("C", 1, 3, 0));
        Nearest.Request q = new Nearest.Request(b(0), b(0), 10, false, cands, null, 1000);
        Nearest.Result r = Nearest.compute(q, null, "MISSING");
        assertFalse(r.route());
        assertEquals("MISSING", r.routeState());
        assertEquals(List.of("A", "C", "B"), r.entries().stream().map(e -> e.c().name()).toList(), "straight line, then name");
        assertTrue(r.entries().stream().allMatch(Nearest.Entry::estimate));
        JsonObject o = Nearest.json(r, 25, false, "MISSING");
        assertEquals("straight", o.get("mode").getAsString());
        assertFalse(o.getAsJsonArray("entries").get(0).getAsJsonObject().has("walk"));
    }

    @Test
    void aResultIsReusedNearbyAndWorkedOutAgainAfterAMoveOrNewlyDoneContent() throws IOException {
        // the recorded character's level, quests and place; two caves of our own
        FixtureState fs = FixtureState.load(Fixtures.root().resolve("shaman-44"));
        double[] at = fs.position();
        List<Content.Line> places = new ArrayList<>(List.of(
                line(ContentType.CAVE, "First Cave", fs.level(), "AVAILABLE", (int) at[0] + 30, (int) at[1]),
                line(ContentType.CAVE, "Second Cave", fs.level(), "AVAILABLE", (int) at[0] + 90, (int) at[1])));
        List<Nearest.Candidate> before = Nearest.gather(fs.quests(), n -> null, places, List.of(), e -> false);
        Nearest.Request q = new Nearest.Request(at[0], at[1], fs.level(), false, before, "abcd1234", 1000);
        Nearest.Result r = Nearest.compute(q, null, "READY");
        assertTrue(Nearest.fresh(r, q));
        // 20 blocks on: the same result; 60 blocks on: a new one
        assertTrue(Nearest.fresh(r, new Nearest.Request(at[0] + 20, at[1], fs.level(), false, before, "abcd1234", 2000)));
        assertFalse(Nearest.fresh(r, new Nearest.Request(at[0] + 60, at[1], fs.level(), false, before, "abcd1234", 2000)));
        // the first cave done: a new one without it
        places.set(0, line(ContentType.CAVE, "First Cave", fs.level(), "COMPLETED", (int) at[0] + 30, (int) at[1]));
        List<Nearest.Candidate> after = Nearest.gather(fs.quests(), n -> null, places, List.of(), e -> false);
        Nearest.Request done = new Nearest.Request(at[0], at[1], fs.level(), false, after, "abcd1234", 2000);
        assertFalse(Nearest.fresh(r, done));
        assertTrue(Nearest.compute(done, null, "READY").entries().stream().noneMatch(e -> e.c().name().equals("First Cave")));
        // a level up, the over-level choice, a new route map: new ones too
        assertFalse(Nearest.fresh(r, new Nearest.Request(at[0], at[1], fs.level() + 1, false, before, "abcd1234", 2000)));
        assertFalse(Nearest.fresh(r, new Nearest.Request(at[0], at[1], fs.level(), true, before, "abcd1234", 2000)));
        assertFalse(Nearest.fresh(r, new Nearest.Request(at[0], at[1], fs.level(), false, before, "ffff0000", 2000)));
    }

    @Test
    void theApiServesTheDocumentedFields() throws IOException {
        WorldRoute g = WorldRouteTest.grid(
                "................",
                "................");
        List<Nearest.Candidate> cands = List.of(cave("Near", 3, 4, 1), cave("Off", 3, -5, 0));
        Nearest.Request q = new Nearest.Request(b(1), b(1), 10, true, cands, "abcd1234", 1234);
        JsonObject o = Nearest.json(Nearest.compute(q, RouteReach.over(g), "READY"), 1, false, "READY");
        assertEquals("route", o.get("mode").getAsString());
        assertEquals("READY", o.get("routeState").getAsString());
        assertEquals(1234, o.get("computedAt").getAsLong());
        assertEquals(b(1), o.getAsJsonObject("origin").get("x").getAsInt());
        assertEquals(b(1), o.getAsJsonObject("origin").get("z").getAsInt());
        assertEquals(1, o.getAsJsonArray("entries").size(), "the limit");
        JsonObject e = o.getAsJsonArray("entries").get(0).getAsJsonObject();
        for (String k : List.of("kind", "name", "level", "x", "y", "z", "walk", "straight", "estimate")) {
            assertTrue(e.has(k), k);
        }
        assertEquals("CAVE", e.get("kind").getAsString());
        assertEquals(12, e.get("walk").getAsInt());
        assertFalse(e.get("estimate").getAsBoolean());
        JsonObject none = Nearest.json(null, 25, true, "DOWNLOADING");
        assertTrue(none.get("computing").getAsBoolean());
        assertEquals("straight", none.get("mode").getAsString());
        assertEquals("DOWNLOADING", none.get("routeState").getAsString());
    }

    @Test
    void aRowOpensItsKindsPane() {
        assertEquals("#qnear:SECRET_DISCOVERY\nopen-id",
                QuestsNearest.pick(new Nearest.Candidate(ContentType.SECRET_DISCOVERY, "Open Secret", "open-id", 0, 1, 2, 3)));
        assertEquals("#qnear:MINI_QUEST\nMini Post",
                QuestsNearest.pick(new Nearest.Candidate(ContentType.MINI_QUEST, "Mini Post", "Mini Post", 20, 1, 2, 3)));
        assertEquals("#qnear:DUNGEON\nIce Barrows",
                QuestsNearest.pick(new Nearest.Candidate(ContentType.DUNGEON, "Ice Barrows", "Ice Barrows", 45, 1, 2, 3)));
    }

    @Test
    void oneFloodOnTheShippedRouteMapKeepsItsDeadline() throws IOException {
        // the repository's data/, five folders above the fixtures (fabric/src/test/resources/fixtures)
        Path file = Fixtures.root().toAbsolutePath().getParent().getParent().getParent().getParent().getParent()
                .resolve("data/world-route.bin");
        Assumptions.assumeTrue(Files.exists(file), "no data/world-route.bin here");
        WorldRoute map;
        try (InputStream in = Files.newInputStream(file)) {
            map = WorldRoute.load(in);
        }
        Path gates = file.resolveSibling("world-gates.json");
        if (Files.exists(gates)) {
            map.gates(WorldGates.parse(Files.readString(gates, StandardCharsets.UTF_8)));
        }
        // from Nesaak (near the Wolf Den), to 400 points on rings out to 4 km
        double px = 175;
        double pz = -715;
        int[][] at = new int[400][];
        for (int i = 0; i < at.length; i++) {
            double r = 100 + 10.0 * i;
            double a = i * 2.399963;
            at[i] = new int[] {(int) (px + r * Math.cos(a)), (int) (pz + r * Math.sin(a))};
        }
        for (int want : new int[] {Nearest.WANT, 0}) {
            int[][] cells = new int[at.length][];
            for (int i = 0; i < at.length; i++) {
                cells[i] = new int[] {map.cellOf(at[i][0]), map.cellOf(at[i][1])};
            }
            WorldRoute.Reaches r = map.reach(map.cellOf(px), map.cellOf(pz), cells, want, RouteReach.BUDGET, RouteReach.MAX_MILLIS);
            long reached = java.util.Arrays.stream(r.each()).filter(java.util.Objects::nonNull).count();
            System.out.printf("[nearest] want=%d: %d cells looked at in %d ms, %d of %d reached%n", want, r.expanded(), r.millis(), reached,
                    at.length);
            assertTrue(r.millis() <= RouteReach.MAX_MILLIS + 250, "the deadline: " + r.millis() + " ms");
            assertTrue(r.expanded() <= RouteReach.BUDGET);
            if (want > 0) {
                assertTrue(reached >= want, "the nearest " + want + " reached from Nesaak: " + reached);
            }
        }
    }
}
