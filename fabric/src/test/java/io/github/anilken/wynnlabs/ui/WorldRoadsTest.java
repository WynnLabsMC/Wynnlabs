package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The roads file: tiles of two-bit columns, read back as none, edge, inner or unknown. */
class WorldRoadsTest {

    private static final int TILE = 64;

    /** A file of {@code wide} x {@code high} tiles from origin tile ({@code ox}, {@code oz}); {@code at} gives each present column's value, or -1 for a tile left out. */
    private static byte[] file(int ox, int oz, int wide, int high, Column at) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.write(new byte[] {'W', 'L', 'R', 'D', 1, (byte) TILE});
            out.writeInt(ox);
            out.writeInt(oz);
            out.writeInt(wide);
            out.writeInt(high);
            int n = wide * high;
            byte[] present = new byte[(n + 7) / 8];
            byte[][] tiles = new byte[n][];
            for (int tz = 0; tz < high; tz++) {
                for (int tx = 0; tx < wide; tx++) {
                    int i = tz * wide + tx;
                    if (at.value((ox + tx) * TILE, (oz + tz) * TILE) < 0) {
                        continue;
                    }
                    present[i >> 3] |= (byte) (1 << (i & 7));
                    byte[] t = new byte[TILE * TILE / 4];
                    for (int rx = 0; rx < TILE; rx++) {
                        for (int rz = 0; rz < TILE; rz++) {
                            int c = rx * TILE + rz;
                            int v = at.value((ox + tx) * TILE + rx, (oz + tz) * TILE + rz) & 3;
                            t[c >> 2] |= (byte) (v << (2 * (c & 3)));
                        }
                    }
                    tiles[i] = t;
                }
            }
            out.write(present);
            for (byte[] t : tiles) {
                if (t != null) {
                    out.write(t);
                }
            }
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface Column {
        int value(int x, int z);
    }

    @Test
    void columnsReadBackAsWritten() throws IOException {
        // two tiles wide from tile x -2 (blocks -128..-1), one high from tile z 3 (blocks 192..255):
        // a road three wide along x at z 200..202, its middle inner, the rest nothing
        byte[] data = file(-2, 3, 2, 1, (x, z) -> z == 201 ? WorldRoads.INNER : z == 200 || z == 202 ? WorldRoads.EDGE : WorldRoads.NONE);
        WorldRoads r = WorldRoads.load(new ByteArrayInputStream(data));
        assertEquals(2, r.tiles());
        assertEquals(WorldRoads.INNER, r.at(-100, 201));
        assertEquals(WorldRoads.EDGE, r.at(-100, 200));
        assertEquals(WorldRoads.EDGE, r.at(-1, 202));
        assertEquals(WorldRoads.NONE, r.at(-128, 199));
        assertEquals(WorldRoads.NONE, r.at(-1, 255));
        // outside the file: not known
        assertEquals(WorldRoads.UNKNOWN, r.at(0, 201));
        assertEquals(WorldRoads.UNKNOWN, r.at(-129, 201));
        assertEquals(WorldRoads.UNKNOWN, r.at(-100, 191));
        assertEquals(WorldRoads.UNKNOWN, r.at(-100, 256));
    }

    @Test
    void aTileLeftOutIsUnknownGround() throws IOException {
        byte[] data = file(0, 0, 2, 2, (x, z) -> x >= 64 && z >= 64 ? -1 : WorldRoads.NONE);
        WorldRoads r = WorldRoads.load(new ByteArrayInputStream(data));
        assertEquals(3, r.tiles());
        assertEquals(WorldRoads.NONE, r.at(10, 10));
        assertEquals(WorldRoads.NONE, r.at(100, 10));
        assertEquals(WorldRoads.UNKNOWN, r.at(100, 100));
    }

    @Test
    void anotherFileIsRefused() {
        assertThrows(IOException.class, () -> WorldRoads.load(new ByteArrayInputStream(new byte[] {1, 2, 3})));
    }
}
