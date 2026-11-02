package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Terrain dumps: their run-length columns, a round trip through JSON, and the dumps from the game replayed. */
class GuideDumpTest {

    @Test
    void columnsAreRunLengthCoded() {
        assertEquals("3#2.w", GuideDump.rle("###..w".toCharArray()));
        assertArrayEquals("###..w".toCharArray(), GuideDump.unrle("3#2.w", 6));
        assertEquals("12#", GuideDump.rle("############".toCharArray()));
        // short strings are filled with "not loaded"
        assertArrayEquals("#.??".toCharArray(), GuideDump.unrle("#.", 4));
    }

    @Test
    void aDumpSurvivesARoundTripThroughJson() {
        GuideDumps.World w = new GuideDumps.World(5, 8, 4, GuideTerrain.SOLID)
                .fill(1, 3, 1, 3, 5, 2, GuideTerrain.AIR).set(2, 3, 1, GuideTerrain.WATER)
                .fill(4, 0, 3, 4, 7, 3, GuideTerrain.UNKNOWN);
        GuideDump d = w.dump(1, 3, 1, 3, 3, 2);
        String text = d.toJson().toString();
        GuideDump back = GuideDump.fromJson(JsonParser.parseString(text).getAsJsonObject());
        for (int x = -1; x <= 5; x++) {
            for (int y = -1; y <= 8; y++) {
                for (int z = -1; z <= 4; z++) {
                    assertEquals(d.at(x, y, z), back.at(x, y, z), x + " " + y + " " + z);
                }
            }
        }
        assertArrayEquals(new int[] {1, 3, 1}, back.start());
        assertEquals(3.5, back.target.get("x").getAsDouble());
        JsonObject o = d.toJson();
        assertEquals("19+-", o.get("loaded").getAsString());
        // the ground: the highest block not walked through
        assertEquals(8, back.ground().height(0, 0));
        assertEquals(GuideLine.UNKNOWN, back.ground().height(4, 3));
    }

    /**
     * Every dump under {@code fixtures/guide/} is a dead end seen in the game: the route has to
     * reach its target, or, where the target is past the dump's edge, walk out to that edge.
     */
    @Test
    void theDumpsFromTheGameAreWalkedThrough() throws IOException, URISyntaxException {
        URL dir = GuideDumpTest.class.getResource("/fixtures/guide");
        assumeTrue(dir != null, "no guide dumps yet");
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of(dir.toURI()))) {
            files = s.filter(f -> f.toString().endsWith(".json") || f.toString().endsWith(".json.gz")).sorted().toList();
        }
        assumeTrue(!files.isEmpty(), "no guide dumps yet");
        for (Path f : files) {
            String name = f.getFileName().toString();
            GuideDump d = GuideDumps.load("/fixtures/guide/" + name);
            assertNotNull(d.target, name + ": no target");
            GuideTerrain w = GuideDumps.walkable(d);
            int[] s = d.start();
            int tx = (int) Math.floor(d.target.get("x").getAsDouble());
            int tz = (int) Math.floor(d.target.get("z").getAsDouble());
            int ty = GuidePath.feet(w, tx, tz, (int) Math.floor(d.target.get("y").getAsDouble()));
            int box = d.settings.has("box") ? d.settings.get("box").getAsInt() : 192;
            int budget = d.settings.has("budget") ? d.settings.get("budget").getAsInt() : 150_000;
            GuidePath.Route r = GuidePath.find(w, s[0], s[1], s[2], tx, ty, tz, box, budget);
            assertNotNull(r, name + ": nowhere to stand at the start");
            boolean inside = tx >= d.x0 && tx < d.x0 + d.sx && tz >= d.z0 && tz < d.z0 + d.sz;
            if (inside) {
                assertTrue(r.reached(), name + ": not reached, stuck at " + r.stuck());
            } else {
                int[] last = r.nodes().get(r.nodes().size() - 1);
                boolean edge = last[0] <= d.x0 + 1 || last[0] >= d.x0 + d.sx - 2
                        || last[2] <= d.z0 + 1 || last[2] >= d.z0 + d.sz - 2;
                assertTrue(edge, name + ": closed in short of the dump's edge, stuck at " + r.stuck());
            }
        }
    }
}
