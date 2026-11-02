package io.github.anilken.wynnlabs.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * A terrain sample from Wynncraft for the guide route's tests: per column the runs of blocks the
 * player cannot walk through (solid ground, water, lava), top down, so the paths under arches,
 * bridges and overhangs are there too. Its blocks are read as {@link GuideTerrain} codes and walked
 * with the game's own rules ({@link #walkable()}); a road or stair block on top is a road.
 * Coordinates are relative to the patch's north-west corner.
 */
final class TerrainGrid implements GuideTerrain.Blocks {

    private static final int SOLID = 0;
    private static final int ROAD = 1;
    private static final int WATER = 2;
    private static final int LAVA = 3;

    final int width;
    final int depth;
    /** The world position of the patch's north-west corner. */
    final int x0;
    final int z0;
    /** Per column its runs, top down: bottom, top (exclusive), kind; null with no data. */
    private final int[][][] runs;

    private TerrainGrid(int x0, int z0, int width, int depth) {
        this.x0 = x0;
        this.z0 = z0;
        this.width = width;
        this.depth = depth;
        runs = new int[width][depth][];
    }

    /** The fixture {@code fixtures/guide-terrain/<name>.txt}. */
    static TerrainGrid load(String name) {
        String path = "/fixtures/guide-terrain/" + name + ".txt";
        try (InputStream in = TerrainGrid.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("no fixture " + path);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String[] head = r.readLine().trim().split(" ");
            TerrainGrid g = new TerrainGrid(Integer.parseInt(head[0]), Integer.parseInt(head[1]),
                    Integer.parseInt(head[2]), Integer.parseInt(head[3]));
            for (int z = 0; z < g.depth; z++) {
                String[] tokens = r.readLine().trim().split(" ");
                for (int x = 0; x < g.width; x++) {
                    if (tokens[x].equals("-")) {
                        continue;
                    }
                    String[] parts = tokens[x].split("/");
                    int[] col = new int[parts.length * 3];
                    for (int i = 0; i < parts.length; i++) {
                        String p = parts[i];
                        char last = p.charAt(p.length() - 1);
                        int kind = last == 'w' ? WATER : last == 'l' ? LAVA : last == 'r' ? ROAD : SOLID;
                        String[] bt = (kind == SOLID ? p : p.substring(0, p.length() - 1)).split(":");
                        col[i * 3] = Integer.parseInt(bt[0]);
                        col[i * 3 + 1] = Integer.parseInt(bt[1]);
                        col[i * 3 + 2] = kind;
                    }
                    g.runs[x][z] = col;
                }
            }
            return g;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < depth;
    }

    /** The block code at (x, y, z): unknown outside the patch or where it has no data. */
    @Override
    public char at(int x, int y, int z) {
        int[] col = inside(x, z) ? runs[x][z] : null;
        if (col == null) {
            return GuideTerrain.UNKNOWN;
        }
        for (int i = 0; i < col.length; i += 3) {
            if (y >= col[i] && y < col[i + 1]) {
                return switch (col[i + 2]) {
                    case WATER -> GuideTerrain.WATER;
                    case LAVA -> GuideTerrain.LAVA;
                    case ROAD -> y == col[i + 1] - 1 ? GuideTerrain.ROAD : GuideTerrain.SOLID;
                    default -> GuideTerrain.SOLID;
                };
            }
        }
        return GuideTerrain.AIR;
    }

    /** The patch walked with the game's rules. */
    GuideTerrain walkable() {
        return new GuideTerrain(this);
    }

    private boolean solid(int kind) {
        return kind == SOLID || kind == ROAD;
    }

    private boolean standable(int x, int y, int z) {
        return walkable().standable(x, y, z) && GuideTerrain.ground(at(x, y - 1, z));
    }

    /** The highest feet height a player can stand at in a column, NONE with none. */
    int top(int x, int z) {
        int[] col = runs[x][z];
        if (col == null) {
            return GuidePath.NONE;
        }
        for (int i = 0; i < col.length; i += 3) {
            if (solid(col[i + 2]) && standable(x, col[i + 1], z)) {
                return col[i + 1];
            }
        }
        return GuidePath.NONE;
    }

    /** The lowest feet height a player can stand at in a column (under the arches), NONE with none. */
    int floor(int x, int z) {
        int[] col = runs[x][z];
        int out = GuidePath.NONE;
        if (col != null) {
            for (int i = 0; i < col.length; i += 3) {
                if (solid(col[i + 2]) && standable(x, col[i + 1], z)) {
                    out = col[i + 1];
                }
            }
        }
        return out;
    }

    /** Whether the column's surface (its highest run) is water. */
    boolean wet(int x, int z) {
        int[] col = runs[x][z];
        return col != null && col[2] == WATER;
    }

    /** Whether the block under feet height {@code y} is a road or stair block. */
    boolean road(int x, int y, int z) {
        return at(x, y - 1, z) == GuideTerrain.ROAD;
    }
}
