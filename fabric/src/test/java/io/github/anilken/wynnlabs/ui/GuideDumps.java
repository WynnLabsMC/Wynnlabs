package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Terrain dumps for guide route tests: one written by {@code /guide?dump=1} loaded from the test
 * resources ({@code .json} or {@code .json.gz}), or a small one built block by block.
 */
final class GuideDumps {

    private GuideDumps() {}

    /** A dump from the test resources, e.g. {@code /fixtures/guide/cave.json.gz}. */
    static GuideDump load(String resource) {
        try (InputStream raw = GuideDumps.class.getResourceAsStream(resource)) {
            if (raw == null) {
                throw new IllegalArgumentException("no such resource: " + resource);
            }
            InputStream in = resource.endsWith(".gz") ? new GZIPInputStream(raw) : raw;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return GuideDump.fromJson(o);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The route rules over a dump's blocks. */
    static GuideTerrain walkable(GuideDump d) {
        return new GuideTerrain(d.blocks());
    }

    static GuideTerrain walkable(GuideDump d, GuideTerrain.Roads roads) {
        return new GuideTerrain(d.blocks(), roads);
    }

    /** A box of blocks built by hand, all of one code to start with; corner (0, 0, 0). */
    static final class World {
        final int sx, sy, sz;
        final char[][] cols;

        World(int sx, int sy, int sz, char fill) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            cols = new char[sx * sz][sy];
            for (char[] c : cols) {
                Arrays.fill(c, fill);
            }
        }

        World set(int x, int y, int z, char c) {
            cols[z * sx + x][y] = c;
            return this;
        }

        /** Every block from (x0, y0, z0) to (x1, y1, z1), both included. */
        World fill(int x0, int y0, int z0, int x1, int y1, int z1, char c) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, c);
                    }
                }
            }
            return this;
        }

        /** A dump of this box with the player's feet at (px, py, pz) and the target's block at (tx, ty, tz). */
        GuideDump dump(int px, int py, int pz, int tx, int ty, int tz) {
            JsonObject t = new JsonObject();
            t.addProperty("x", tx + 0.5);
            t.addProperty("y", ty);
            t.addProperty("z", tz + 0.5);
            t.addProperty("label", "test");
            t.addProperty("ours", false);
            JsonObject set = new JsonObject();
            set.addProperty("box", 64);
            set.addProperty("budget", 150_000);
            return new GuideDump(0, 0, 0, sx, sy, sz, cols, new double[] {px + 0.5, py, pz + 0.5}, t, set);
        }
    }
}
