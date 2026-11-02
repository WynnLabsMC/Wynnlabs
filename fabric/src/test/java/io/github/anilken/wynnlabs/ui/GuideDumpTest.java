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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                .fill(1, 3, 1, 3, 5, 2, GuideTerrain.AIR).set(2, 3, 1, GuideTerrain.WATER).set(3, 6, 2, GuideTerrain.BARRIER)
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
        assertEquals(GuideDump.VERSION, o.get("version").getAsInt());
        assertEquals(GuideTerrain.BARRIER, back.at(3, 6, 2));
        assertEquals("19+-", o.get("loaded").getAsString());
        // the ground: the highest block not walked through
        assertEquals(8, back.ground().height(0, 0));
        assertEquals(GuideLine.UNKNOWN, back.ground().height(4, 3));
    }

    /**
     * Every dump under {@code fixtures/guide/} whose target lies inside it is walked to. (A dump
     * whose target is past its edge has a test of its own.)
     */
    @Test
    void theDumpsFromTheGameAreWalkedThrough() throws IOException, URISyntaxException {
        URL dir = GuideDumpTest.class.getResource("/fixtures/guide");
        assumeTrue(dir != null, "no guide dumps yet");
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of(dir.toURI()))) {
            files = s.filter(f -> f.toString().endsWith(".json") || f.toString().endsWith(".json.gz")).sorted().toList();
        }
        for (Path f : files) {
            String name = f.getFileName().toString();
            GuideDump d = GuideDumps.load("/fixtures/guide/" + name);
            assertNotNull(d.target, name + ": no target");
            int tx = (int) Math.floor(d.target.get("x").getAsDouble());
            int tz = (int) Math.floor(d.target.get("z").getAsDouble());
            if (tx >= d.x0 && tx < d.x0 + d.sx && tz >= d.z0 && tz < d.z0 + d.sz) {
                GuidePath.Route r = route(d);
                assertTrue(r.reached(), name + ": not reached, " + report(r));
            }
        }
    }

    /**
     * The canyon west of Silent Expanse (a dump 128 blocks each way and 64 up and down): the
     * guide line said "dead end" there, and it is one. Its open air, followed block by block
     * from where the player can walk, never gets east of x 832 and leaves the dump only to the
     * sky; the expedition's pin, 182 blocks east and under the mountain, is not reached on foot
     * from here. So the route is closed in, says why, and leads out where the canyon's ground goes on.
     */
    @Test
    void theSilentExpanseCanyonIsClosedInAndSaysWhy() {
        GuideDump d = GuideDumps.load("/fixtures/guide/silent-expanse-cave.json.gz");
        GuidePath.Route r = route(d);
        assertNotNull(r, "nowhere to stand at the start");
        assertFalse(r.reached());
        assertTrue(r.enclosed(), report(r));
        assertTrue(r.columns() > 10_000, report(r));
        assertEquals(GuidePath.STUCK, r.stuck().size());
        for (GuidePath.Stuck st : r.stuck()) {
            assertFalse(st.rejected().isEmpty(), report(r));
        }
        // no rule about what is walked on would open it: no open air leads east
        assertEquals(832, eastmostAir(d, r.nodes().get(0)), report(r));
        // so the line leads out of the canyon where its ground goes on past the dump (unloaded
        // here), not to the eastern wall nearest the target: south, the way players go (round
        // through Eyeball Forest and north again to Lutho)
        assertNotNull(r.exit(), report(r));
        assertEquals(GuidePath.EXIT_UNLOADED, r.exitKind());
        int[] e = r.exit();
        assertTrue(e[2] >= d.z0 + d.sz - 4, "not on the south edge: " + report(r));
        assertTrue(e[0] >= 740 && e[0] <= 780, report(r));
        assertArrayEquals(e, r.nodes().get(r.nodes().size() - 1));
    }

    /** The route over a dump as the game searches it: the player's feet to the target's, the dump's box and budget. */
    private static GuidePath.Route route(GuideDump d) {
        GuideTerrain w = GuideDumps.walkable(d);
        int[] s = d.start();
        int tx = (int) Math.floor(d.target.get("x").getAsDouble());
        int tz = (int) Math.floor(d.target.get("z").getAsDouble());
        int ty = GuidePath.feet(w, tx, tz, (int) Math.floor(d.target.get("y").getAsDouble()));
        int box = d.settings.has("box") ? d.settings.get("box").getAsInt() : 192;
        int budget = d.settings.has("budget") ? d.settings.get("budget").getAsInt() : 150_000;
        return GuidePath.find(w, s[0], s[1], s[2], tx, ty, tz, box, budget);
    }

    /** The last spot, the counts, and the stuck spots with why each neighbour was refused, one a line. */
    private static String report(GuidePath.Route r) {
        int[] last = r.nodes().get(r.nodes().size() - 1);
        StringBuilder b = new StringBuilder("last spot " + last[0] + ", " + last[1] + ", " + last[2]
                + "; expanded " + r.expanded() + ", enclosed " + r.enclosed() + ", columns " + r.columns()
                + ", exit " + java.util.Arrays.toString(r.exit()) + " (" + r.exitKind() + ")");
        for (GuidePath.Stuck st : r.stuck()) {
            b.append("\n  ").append(st.x()).append(", ").append(st.y()).append(", ").append(st.z())
                    .append(" ").append(st.rejected());
        }
        return b.toString();
    }

    /** The highest x of the open blocks joined face to face with {@code from}. */
    private static int eastmostAir(GuideDump d, int[] from) {
        java.util.BitSet seen = new java.util.BitSet(d.sx * d.sy * d.sz);
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(from);
        seen.set(index(d, from[0], from[1], from[2]));
        int east = from[0];
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            east = Math.max(east, c[0]);
            for (int[] f : faces) {
                int x = c[0] + f[0], y = c[1] + f[1], z = c[2] + f[2];
                if (GuideTerrain.passable(d.at(x, y, z)) && !seen.get(index(d, x, y, z))) {
                    seen.set(index(d, x, y, z));
                    q.add(new int[] {x, y, z});
                }
            }
        }
        return east;
    }

    private static int index(GuideDump d, int x, int y, int z) {
        return ((z - d.z0) * d.sx + (x - d.x0)) * d.sy + (y - d.y0);
    }
}
