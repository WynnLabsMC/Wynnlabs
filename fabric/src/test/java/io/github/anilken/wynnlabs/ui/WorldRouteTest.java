package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coarse world map: its file is read back, the way over it skirts a mountain, climbs a ramp,
 * swims a river and never crosses lava, and the corridor hands the block-level route its waypoints.
 * The maps are written here, from character rows: '.' ground at 64, '~' water at 62, 'M' a
 * mountain top at 90, 'L' lava, ' ' unknown, a digit ground 2 blocks higher a step ('1' 66, '2' 68).
 * '#' ground at 64 with an invisible wall (impassable), '=' a road at 64, 'h' ground at 64 in a
 * high-level region. A cell opens toward a neighbour that can
 * be stood or swum on and is at most 2 blocks higher or 40 lower, the way the generator decides it
 * block by block on the real map.
 */
class WorldRouteTest {

    private static final int CELL = 4;
    private static final int TILE = 16;
    private static final int BASE = 50;

    /** One tile of {@value #TILE} cells a side at the origin, from rows of z (row) by x (column). */
    static WorldRoute grid(String... rows) throws IOException {
        return WorldRoute.load(new ByteArrayInputStream(gridBytes(rows)));
    }

    /** The file bytes (gzip) of {@link #grid}. */
    static byte[] gridBytes(String... rows) throws IOException {
        byte[] heights = new byte[TILE * TILE];
        byte[] kinds = new byte[TILE * TILE / 4];
        byte[] edges = new byte[TILE * TILE / 2];
        byte[] walls = new byte[TILE * TILE / 8];
        byte[] roads = new byte[TILE * TILE / 8];
        byte[] highs = new byte[TILE * TILE / 8];
        int[] ys = new int[TILE * TILE];
        java.util.Arrays.fill(ys, WorldRoute.UNKNOWN);
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                char c = rows[z].charAt(x);
                int kind;
                int y;
                if (c == '.' || c == '#' || c == '=' || c == 'h') {
                    if (c == '=') {
                        roads[(z * TILE + x) >> 3] |= (byte) (1 << ((z * TILE + x) & 7));
                    }
                    if (c == 'h') {
                        highs[(z * TILE + x) >> 3] |= (byte) (1 << ((z * TILE + x) & 7));
                    }
                    kind = WorldRoute.GROUND;
                    y = 64;
                    if (c == '#') {
                        walls[(z * TILE + x) >> 3] |= (byte) (1 << ((z * TILE + x) & 7));
                    }
                } else if (c >= '0' && c <= '9') {
                    kind = WorldRoute.GROUND;
                    y = 64 + 2 * (c - '0');
                } else if (c == 'M') {
                    kind = WorldRoute.GROUND;
                    y = 90;
                } else if (c == '~') {
                    kind = WorldRoute.WATER;
                    y = 62;
                } else if (c == 'L') {
                    kind = WorldRoute.LAVA;
                    y = WorldRoute.UNKNOWN;
                } else {
                    continue;
                }
                int i = z * TILE + x;
                ys[i] = y;
                heights[i] = (byte) (y == WorldRoute.UNKNOWN ? 0 : y - BASE + 1);
                kinds[i >> 2] |= (byte) (kind << (2 * (i & 3)));
            }
        }
        int[][] dirs = {{1, 0, WorldRoute.EAST}, {0, 1, WorldRoute.SOUTH}, {-1, 0, WorldRoute.WEST}, {0, -1, WorldRoute.NORTH}};
        for (int z = 0; z < TILE; z++) {
            for (int x = 0; x < TILE; x++) {
                int i = z * TILE + x;
                if (ys[i] == WorldRoute.UNKNOWN) {
                    continue;
                }
                for (int[] d : dirs) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (nx < 0 || nz < 0 || nx >= TILE || nz >= TILE) {
                        continue;
                    }
                    int ny = ys[nz * TILE + nx];
                    if (ny != WorldRoute.UNKNOWN && ny - ys[i] <= 2 && ys[i] - ny <= 40) {
                        edges[i >> 1] |= (byte) (d[2] << (4 * (i & 1)));
                    }
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeBytes("WLWR");
            out.writeByte(4);
            out.writeByte(CELL);
            out.writeByte(TILE);
            out.writeInt(BASE);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(2);                    // two tiles wide, only the first present
            out.writeInt(1);
            out.writeByte(1);                   // the bitmap
            out.write(heights);
            out.write(kinds);
            out.write(edges);
            out.write(walls);
            out.write(roads);
            out.write(highs);
        }
        return bytes.toByteArray();
    }

    @Test
    void readsTheHeaderKindsAndHeights() throws IOException {
        WorldRoute g = grid(
                "..~L ",
                "M2...");
        assertEquals(CELL, g.cell);
        assertEquals(TILE, g.tile);
        assertEquals(1, g.tiles());
        assertEquals(WorldRoute.GROUND, g.kind(0, 0));
        assertEquals(64, g.height(1, 0));
        assertEquals(WorldRoute.WATER, g.kind(2, 0));
        assertEquals(62, g.height(2, 0));
        assertEquals(WorldRoute.LAVA, g.kind(3, 0));
        assertEquals(WorldRoute.UNKNOWN, g.height(3, 0));
        assertEquals(WorldRoute.NONE, g.kind(4, 0));
        assertEquals(WorldRoute.UNKNOWN, g.height(4, 0));
        assertEquals(90, g.height(0, 1));
        assertEquals(68, g.height(1, 1));
        assertTrue(g.walkable(2, 0) && g.walkable(1, 1));
        assertFalse(g.walkable(3, 0) || g.walkable(4, 0));
        // the edges: east from the first cell onto ground, not up the mountain to its south; off the map none
        assertTrue(g.open(0, 0, WorldRoute.EAST));
        assertFalse(g.open(0, 0, WorldRoute.SOUTH));
        assertTrue(g.open(0, 1, WorldRoute.NORTH), "down off the mountain");
        assertFalse(g.open(2, 0, WorldRoute.EAST), "not into the lava");
        assertFalse(g.open(0, 0, WorldRoute.WEST) || g.open(0, 0, WorldRoute.NORTH));
        assertFalse(g.open(40, 40, WorldRoute.EAST));
        // a diagonal step rounds a corner; not into the lava, not with both corners shut
        assertTrue(g.open(1, 0, 1, 1));
        assertFalse(g.open(2, 1, 1, -1));
        // off the tile that is present, and off the map
        assertEquals(WorldRoute.NONE, g.kind(TILE + 1, 0));
        assertEquals(WorldRoute.UNKNOWN, g.height(-1, -1));
        assertEquals(WorldRoute.UNKNOWN, g.height(0, 1000));
        // block to cell and back
        assertEquals(2, g.cellOf(9.7));
        assertEquals(-1, g.cellOf(-0.5));
        assertEquals(10, g.centre(2), 1e-9);
    }

    @Test
    void theWaySkirtsAMountain() throws IOException {
        // a wall of mountain down the middle, open only at the south end
        WorldRoute g = grid(
                ".....M.....",
                ".....M.....",
                ".....M.....",
                ".....M.....",
                ".....M.....",
                ".....M.....",
                "...........",
                "...........");
        WorldRoute.Plan p = g.plan(1, 1, 9, 1, 10_000, 1000);
        assertNotNull(p);
        assertTrue(p.reached());
        int[] first = p.cells().get(0);
        int[] last = p.cells().get(p.cells().size() - 1);
        assertEquals(1, first[0]);
        assertEquals(9, last[0]);
        assertEquals(1, last[1]);
        assertTrue(p.cells().stream().anyMatch(c -> c[1] >= 6), "goes round through the gap");
        assertTrue(p.cells().stream().noneMatch(c -> g.height(c[0], c[1]) == 90), "never over the mountain");
        assertTrue(p.cells().size() >= 11 && p.cells().size() <= 16, "a detour, not a wander: " + p.cells().size());
    }

    @Test
    void aRampIsClimbedButACliffIsNot() throws IOException {
        // the plateau at 70 is reached up the ramp 66, 68 in the north row; the south is a cliff
        WorldRoute g = grid(
                "..123",
                "....3");
        assertTrue(g.step(1, 0, 2, 0, 1) > 0);
        assertTrue(g.step(2, 0, 3, 0, 1) > g.step(1, 0, 1, 1, 1), "a step up costs more than a flat one");
        assertTrue(g.step(3, 1, 4, 1, 1) < 0, "64 to 70 at once is a cliff");
        assertTrue(g.step(4, 1, 3, 1, 1) > 0, "but it can be jumped down");
        WorldRoute.Plan p = g.plan(0, 1, 4, 1, 1000, 1000);
        assertTrue(p.reached());
        assertTrue(p.cells().stream().anyMatch(c -> c[1] == 0 && c[0] >= 2 && c[0] <= 3), "up the ramp");
    }

    @Test
    void theRiverIsSwumAcrossAndLavaIsNot() throws IOException {
        WorldRoute g = grid(
                "..~~..LL..",
                "..~~..LL..",
                "..~~..LL..");
        WorldRoute.Plan river = g.plan(0, 1, 5, 1, 1000, 1000);
        assertTrue(river.reached());
        assertTrue(river.cells().stream().anyMatch(c -> g.kind(c[0], c[1]) == WorldRoute.WATER));
        WorldRoute.Plan lava = g.plan(0, 1, 9, 1, 1000, 1000);
        assertFalse(lava.reached());
        assertTrue(lava.cells().stream().noneMatch(c -> c[0] >= 6), "stops at the lava's bank");
        // a target in the lava is aimed at from the ground next to it (here the far bank, not reached);
        // one on an unknown cell next to ground is reached
        WorldRoute.Plan bank = g.plan(0, 1, 7, 1, 1000, 1000);
        assertNotNull(bank);
        assertFalse(bank.reached());
        assertNull(g.plan(7, 1, 0, 1, 1000, 1000), "no start in the lava");
        assertNull(g.plan(0, 1, 15, 15, 1000, 1000), "nothing to aim at far off the map");
        WorldRoute.Plan near = g.plan(0, 1, 5, 4, 1000, 1000);
        assertNotNull(near);
        assertTrue(near.reached());
    }

    @Test
    void aBridgeIsTakenOverAShortDetourButNotALongOne() throws IOException {
        // a river three cells wide with a bridge two cells north of the straight way: cheaper than swimming it
        WorldRoute g = grid(
                "....~~~....",
                "....~~~....",
                "...........",
                "....~~~....",
                "....~~~....",
                "....~~~....");
        WorldRoute.Plan p = g.plan(0, 4, 10, 4, 1000, 1000);
        assertTrue(p.reached());
        assertTrue(p.cells().stream().noneMatch(c -> g.kind(c[0], c[1]) == WorldRoute.WATER), "over the bridge");
        // the bridge much farther off: the river is swum
        WorldRoute g2 = grid(
                "...........",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....",
                "....~~~....");
        WorldRoute.Plan p2 = g2.plan(0, 11, 10, 11, 10_000, 1000);
        assertTrue(p2.reached());
        assertTrue(p2.cells().stream().anyMatch(c -> g2.kind(c[0], c[1]) == WorldRoute.WATER), "swims");
    }

    @Test
    void theCorridorHandsOverItsFarthestLoadedWaypoint() throws IOException {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < TILE; i++) {
            row.append('.');
        }
        WorldRoute g = grid(row.toString());
        WorldRoute.Plan p = g.plan(0, 0, 15, 0, 1000, 1000);
        assertTrue(p.reached());
        assertEquals(16, p.cells().size());
        // 60 blocks of way: waypoints every 24 blocks (6 cells) and the last cell
        List<double[]> wps = g.corridor(p.cells(), 24);
        assertEquals(3, wps.size());
        assertEquals(g.centre(6), wps.get(0)[0], 1e-9);
        assertEquals(64, wps.get(0)[1], 1e-9);
        assertEquals(g.centre(12), wps.get(1)[0], 1e-9);
        assertEquals(g.centre(15), wps.get(2)[0], 1e-9);
        assertEquals(2, wps.get(2)[2], 1e-9);
        // from the start with 40 blocks loaded: the second waypoint (50 blocks out) is beyond it
        assertEquals(0, WorldRoute.handoff(wps, 2, 2, 40, (x, z) -> true));
        assertEquals(2, WorldRoute.handoff(wps, 2, 2, 100, (x, z) -> true));
        assertEquals(1, WorldRoute.handoff(wps, 2, 2, 100, (x, z) -> x < 60), "only loaded ground");
        assertEquals(-1, WorldRoute.handoff(wps, 2, 2, 10, (x, z) -> true));
        assertTrue(g.corridor(p.cells().subList(0, 1), 24).isEmpty());
        // the drawn line lies on the map's heights, and the nearest cell to a block is found
        List<double[]> line = g.line(p.cells(), 0.08);
        assertEquals(16, line.size());
        assertEquals(64.08, line.get(3)[1], 1e-9);
        assertEquals(4, g.nearestCell(p.cells(), 17, 1));
    }

    @Test
    void anInvisibleWallIsImpassableAndAHandMadeLineIsCrossedOnlyAtAGate() throws IOException {
        // a recorded wall down the middle with a hole; then a hand-made line over the hole
        WorldRoute g = grid(
                "....#....",
                "....#....",
                ".........",
                "....#....",
                "....#....");
        assertEquals(4, g.impassable());
        assertTrue(g.wall(4, 0));
        assertFalse(g.walkable(4, 0));
        WorldRoute.Plan p = g.plan(0, 0, 8, 0, 1000, 1000);
        assertTrue(p.reached());
        assertTrue(p.cells().stream().anyMatch(c -> c[0] == 4 && c[1] == 2), "through the hole");
        // the line (in blocks) closes the hole: x = 18 from z = 0 to z = 20
        WorldGates line = WorldGates.parse("{\"lines\": [[[18, 0], [18, 20]]], \"gates\": []}");
        assertEquals(1, line.lines());
        assertEquals(0, line.gates());
        g.gates(line);
        assertTrue(line.blocked(14, 10, 22, 10));
        assertFalse(line.blocked(14, 10, 14, 14), "along the line is not across it");
        assertFalse(g.plan(0, 0, 8, 0, 1000, 1000).reached());
        // a gate on the line at the hole: crossing allowed within 8 blocks of it
        g.gates(WorldGates.parse("{\"lines\": [[[18, 0], [18, 20]]], \"gates\": [{\"name\": \"Hole\", \"x\": 18, \"z\": 10, \"level\": 5}]}"));
        assertTrue(g.plan(0, 0, 8, 0, 1000, 1000).reached());
        assertEquals(1, g.gates().gates());
        assertEquals("Hole", g.gates().gateList().get(0).name());
        assertEquals(5, g.gates().gateList().get(0).level());
        // malformed files are no walls; an empty one is fine
        assertNull(WorldGates.parse("{\"lines\": [[[18, 0]]]}"));
        assertNull(WorldGates.parse("{\"lines\": [[[18, 0, 1], [18, 20, 1]]]}"));
        assertNull(WorldGates.parse("{\"gates\": [{\"name\": \"x\"}]}"));
        assertNull(WorldGates.parse("not json"));
        assertEquals(0, WorldGates.parse("{}").lines());
        assertEquals(0, WorldGates.parse("{\"lines\": [], \"gates\": []}").gates());
    }

    @Test
    void aCorridorLeadsUnderTheMountainFromItsGate() throws IOException {
        // a wall of impassable cells across the way; a gate at its west foot (cell 3, 2 = block 14, 10)
        WorldRoute g = grid(
                "....###....",
                "....###....",
                "....###....",
                "....###....",
                "....###....");
        assertFalse(g.plan(0, 2, 10, 2, 1000, 1000).reached());
        // a gate alone is no way through rock
        g.gates(WorldGates.parse("{\"gates\": [{\"name\": \"Pass\", \"x\": 14, \"z\": 10, \"level\": 1}]}"));
        assertEquals(0, g.tunnels());
        assertFalse(g.plan(0, 2, 10, 2, 1000, 1000).reached());
        // a corridor from the gate to the road's location point beyond the wall
        g.gates(WorldGates.parse("{\"gates\": [{\"name\": \"Pass\", \"x\": 14, \"z\": 10}], "
                + "\"corridors\": [{\"name\": \"Road\", \"from\": [14, 10], \"to\": [30, 10]}]}"));
        assertEquals(1, g.tunnels());
        assertEquals("Road", g.gates().corridors().get(0).name());
        WorldRoute.Plan p = g.plan(0, 2, 10, 2, 1000, 1000);
        assertTrue(p.reached());
        assertTrue(p.cells().stream().anyMatch(c -> c[0] == 3 && c[1] == 2), "in at the gate");
        assertTrue(p.cells().stream().anyMatch(c -> c[0] == 7 && c[1] == 2), "out at the road's point");
        assertTrue(p.cells().stream().noneMatch(c -> c[0] >= 4 && c[0] <= 6), "nothing walked on the rock");
        assertTrue(g.plan(10, 2, 0, 2, 1000, 1000).reached(), "both ways");
        // the drawn line keeps the mouth's height through the corridor
        assertTrue(g.line(p.cells(), 0).stream().allMatch(pt -> pt[1] == 64));
        // two corridors meeting at a location point in the rock (moved to walkable ground near it) carry the route across
        WorldRoute two = grid(
                "....###....",
                "....###....",
                "....###....",
                "....###....",
                "....###....");
        two.gates(WorldGates.parse("{\"corridors\": ["
                + "{\"name\": \"Road\", \"from\": [14, 10], \"to\": [22, 10]},"
                + "{\"name\": \"Road\", \"from\": [30, 2], \"to\": [22, 10]}]}"));
        assertEquals(2, two.tunnels());
        WorldRoute.Plan q = two.plan(0, 2, 10, 0, 1000, 1000);
        assertTrue(q.reached());
        assertTrue(q.cells().stream().noneMatch(c -> c[0] >= 4 && c[0] <= 6), "the corridors, not the rock");
        // a corridor end in the rock with nothing else there leads nowhere
        WorldRoute dead = grid(
                "....###....",
                "....###....",
                "....###....");
        dead.gates(WorldGates.parse("{\"corridors\": [{\"name\": \"Mine\", \"from\": [14, 6], \"to\": [22, 6]}]}"));
        assertFalse(dead.plan(0, 1, 10, 1, 1000, 1000).reached());
        // malformed corridors make the file unread
        assertNull(WorldGates.parse("{\"corridors\": [{\"from\": [1, 2]}]}"));
        assertNull(WorldGates.parse("{\"corridors\": [{\"from\": [1, 2, 3], \"to\": [4, 5]}]}"));
        assertEquals(0, WorldGates.parse("{\"corridors\": []}").corridors().size());
    }

    @Test
    void aWallLineIsNeverCrossedButACorridorGoesUnderIt() throws IOException {
        // open ground; a closed wall loop (in blocks) round the middle, cells 4..6 by 1..3
        WorldRoute g = grid(
                "...........",
                "...........",
                "...........",
                "...........",
                "...........");
        String loop = "[[14, 2], [30, 2], [30, 16], [14, 16], [14, 2]]";
        g.gates(WorldGates.parse("{\"lines\": [" + loop + "]}"));
        assertEquals(1, g.gates().lines());
        assertEquals(4, g.gates().segments());
        // inside cannot be reached from outside, by straight or diagonal steps
        assertFalse(g.plan(0, 2, 5, 2, 1000, 1000).reached());
        assertFalse(g.plan(5, 2, 0, 2, 1000, 1000).reached());
        assertTrue(g.plan(0, 0, 10, 4, 1000, 1000).reached(), "round the loop outside it");
        assertTrue(g.plan(0, 0, 10, 4, 1000, 1000).cells().stream().noneMatch(c -> c[0] >= 4 && c[0] <= 6 && c[1] >= 1 && c[1] <= 3));
        // a corridor from outside to inside goes under the wall
        g.gates(WorldGates.parse("{\"lines\": [" + loop + "], "
                + "\"corridors\": [{\"name\": \"Road\", \"from\": [6, 10], \"to\": [22, 10]}]}"));
        assertTrue(g.plan(0, 2, 5, 2, 1000, 1000).reached());
        // any number of walls is read
        WorldGates three = WorldGates.parse("{\"lines\": [" + loop + ", [[0, 0], [4, 4]], [[40, 0], [40, 8], [36, 8]]]}");
        assertEquals(3, three.lines());
        assertEquals(7, three.segments());
        // a gate on the wall is a door: crossing allowed within 8 blocks of it
        g.gates(WorldGates.parse("{\"lines\": [" + loop + "], \"gates\": [{\"name\": \"Door\", \"x\": 14, \"z\": 10}]}"));
        assertTrue(g.plan(0, 2, 5, 2, 1000, 1000).reached());
    }

    @Test
    void theRouteKeepsToTheRoadAndOffTheHighLevelLand() throws IOException {
        // the straight way is plain ground; a road loops round it three rows south
        WorldRoute g = grid(
                "...........",
                "...........",
                "=.........=",
                "=.........=",
                "===========");
        assertEquals(WorldRoute.ROAD_COST, g.cost(0, 4), 1e-9);
        assertEquals(WorldRoute.PLAIN_COST, g.cost(5, 0), 1e-9);
        assertTrue(g.road(0, 2) && !g.road(1, 2));
        WorldRoute.Plan p = g.plan(0, 2, 10, 2, 1000, 1000);
        assertTrue(p.reached());
        assertTrue(p.cells().stream().anyMatch(c -> c[1] == 4), "along the road");
        assertTrue(p.cells().stream().noneMatch(c -> c[1] < 2), "not across the plain");
        // a road far off: a short link over the plain wins
        WorldRoute far = grid(
                "=.........=",
                "...........",
                "...........",
                "...........",
                "...........",
                "...........",
                "...........",
                "...........",
                "...........",
                "...........",
                "===========");
        assertTrue(far.plan(0, 0, 10, 0, 1000, 1000).cells().stream().noneMatch(c -> c[1] == 10), "no long detour");
        // high-level land between two roads: the road round it, though it is longer than with plain land
        WorldRoute high = grid(
                "=hhhhhhhhh=",
                "=hhhhhhhhh=",
                "=hhhhhhhhh=",
                "=hhhhhhhhh=",
                "===========");
        assertTrue(high.highLevel(5, 0));
        assertEquals(WorldRoute.HIGH_COST, high.cost(5, 0), 1e-9);
        WorldRoute.Plan h = high.plan(0, 0, 10, 0, 1000, 1000);
        assertTrue(h.reached());
        assertTrue(h.cells().stream().anyMatch(c -> c[1] == 4), "round by the road");
        // the same loop round plain land that is too long: straight over it
        WorldRoute plain = grid(
                "=...=",
                "=...=",
                "=...=",
                "=...=",
                "=...=",
                "=...=",
                "=...=",
                "=====");
        assertTrue(plain.plan(0, 0, 4, 0, 1000, 1000).cells().stream().noneMatch(c -> c[1] == 7), "straight over plain land");
        WorldRoute highTall = grid(
                "=hhh=",
                "=hhh=",
                "=hhh=",
                "=hhh=",
                "=hhh=",
                "=hhh=",
                "=hhh=",
                "=====");
        assertTrue(highTall.plan(0, 0, 4, 0, 1000, 1000).cells().stream().anyMatch(c -> c[1] == 7), "but round high-level land");
    }

    @Test
    void theSearchStopsAtItsBudget() throws IOException {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < TILE; i++) {
            row.append('.');
        }
        String[] rows = new String[TILE];
        java.util.Arrays.fill(rows, row.toString());
        WorldRoute g = grid(rows);
        WorldRoute.Plan p = g.plan(0, 0, 15, 15, 3, 1000);
        assertFalse(p.reached());
        assertTrue(p.expanded() <= 3);
        assertTrue(p.cells().size() >= 2, "the way to the nearest cell looked at");
    }

    @Test
    void oneFloodReachesManyTargetsTheWayAroundAWall() throws IOException {
        // a wall across the map but for its east end: the cell just behind it is a long walk away
        WorldRoute g = grid(
                "................",
                "................",
                "###############.",
                "................");
        int[][] targets = {{1, 3}, {10, 1}, {1, 0}};
        WorldRoute.Reaches r = g.reach(1, 1, targets, 0, 10_000, 1000);
        assertNotNull(r.each()[0]);
        assertNotNull(r.each()[1]);
        // round the wall's end: at least 13 cells out and 13 back, against 2 cells straight
        assertTrue(r.each()[0].length() > 26 * 4, "behind the wall: " + r.each()[0].length());
        assertEquals(9 * 4, r.each()[1].length(), 1e-9);
        assertTrue(r.each()[1].length() < r.each()[0].length());
        assertEquals(4, r.each()[2].length(), 1e-9);
        // the same targets as A* sees them: the cost agrees with plan()'s way
        assertTrue(g.plan(1, 1, 1, 3, 10_000, 1000).reached());
    }

    @Test
    void theFloodTakesACorridorAndLeavesWhatItCannotReach() throws IOException {
        WorldRoute g = grid(
                "....###....",
                "....###....",
                "....###....",
                "....###....",
                "....###....");
        int[][] beyond = {{10, 2}};
        assertNull(g.reach(0, 2, beyond, 0, 10_000, 1000).each()[0], "rock and no corridor: not reached");
        g.gates(WorldGates.parse("{\"corridors\": [{\"name\": \"Road\", \"from\": [14, 10], \"to\": [30, 10]}]}"));
        WorldRoute.Reach r = g.reach(0, 2, beyond, 0, 10_000, 1000).each()[0];
        assertNotNull(r, "through the corridor");
        // 3 cells to the gate, the corridor's 4 cells, 3 cells on: 10 cells of 4 blocks
        assertEquals(40, r.length(), 1e-9);
        // a target with no cell to stand on within two is never reached
        assertNull(g.reach(0, 2, new int[][] {{30, 2}}, 0, 10_000, 1000).each()[0]);
    }

    @Test
    void theFloodStopsAtItsBudgetAndPastItsWantedTargets() throws IOException {
        WorldRoute g = grid(
                "................",
                "................",
                "................");
        int[][] targets = {{2, 1}, {15, 1}};
        // a budget of a few cells (the start, its neighbours, then the target two cells on) reaches the near target only
        WorldRoute.Reaches small = g.reach(0, 1, targets, 0, 8, 1000);
        assertNotNull(small.each()[0]);
        assertNull(small.each()[1]);
        assertTrue(small.expanded() <= 8);
        // one target wanted: the flood goes 1.5 times its cost further, not to the far end
        WorldRoute.Reaches one = g.reach(0, 1, targets, 1, 10_000, 1000);
        assertNotNull(one.each()[0]);
        assertNull(one.each()[1]);
        // none wanted: every target
        assertNotNull(g.reach(0, 1, targets, 0, 10_000, 1000).each()[1]);
    }
}
