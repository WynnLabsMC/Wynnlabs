package io.github.anilken.wynnlabs.ui;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Wynncraft's roads at block resolution, made offline from the world's terrain and fetched from
 * our repository with the world route map ({@link WorldRouteData}): for a column, whether it is
 * not a road, the edge of a road, or inside one (every orthogonal neighbour a road too). The
 * block-level route ({@link GuideTerrain}) takes its road costs from here where the file knows
 * the ground, so a cobble on its own among the dirt is not a road and the middle of a real one
 * is, instead of guessing from the block under each step. Pure: no Minecraft types.
 *
 * <p>File: gzip over "WLRD", a version byte (1), the tile size (64), then big-endian ints: the
 * origin tile x and z, the tiles wide and high; a bitmap of the tiles present (row-major, z
 * outer, bit i of byte i / 8; a tile is present where the ground there is known, roads or not);
 * then each present tile: tile² / 4 bytes, two bits a column, column i = (x - tx · 64) · 64 +
 * (z - tz · 64) in bits 2 (i mod 4).
 */
final class WorldRoads {

    /** What {@link #at} says of a column. */
    static final int UNKNOWN = -1;
    static final int NONE = 0;
    static final int EDGE = 1;
    static final int INNER = 2;

    private static final int VERSION = 1;
    /** The file is at most this big; more is not the file we asked for. */
    static final int MAX_TILES = 1 << 16;

    private final int tile;
    private final int ox;
    private final int oz;
    private final int wide;
    private final int high;
    /** Per tile (row-major, z outer; null: not known): the packed columns. */
    private final byte[][] tiles;
    private final int present;

    private WorldRoads(int tile, int ox, int oz, int wide, int high, byte[][] tiles, int present) {
        this.tile = tile;
        this.ox = ox;
        this.oz = oz;
        this.wide = wide;
        this.high = high;
        this.tiles = tiles;
        this.present = present;
    }

    /** Reads a file; {@link IOException} when it is not one. */
    static WorldRoads load(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'W' || magic[1] != 'L' || magic[2] != 'R' || magic[3] != 'D') {
                throw new IOException("not a world roads file");
            }
            int version = in.readUnsignedByte();
            if (version != VERSION) {
                throw new IOException("world roads file version " + version + ", expected " + VERSION);
            }
            int tile = in.readUnsignedByte();
            int ox = in.readInt();
            int oz = in.readInt();
            int wide = in.readInt();
            int high = in.readInt();
            if (tile < 4 || tile > 128 || (tile & (tile - 1)) != 0 || wide <= 0 || high <= 0 || (long) wide * high > MAX_TILES) {
                throw new IOException("world roads file of " + wide + " x " + high + " tiles of " + tile);
            }
            int n = wide * high;
            byte[] present = new byte[(n + 7) / 8];
            in.readFully(present);
            byte[][] tiles = new byte[n][];
            int count = 0;
            for (int i = 0; i < n; i++) {
                if ((present[i >> 3] >> (i & 7) & 1) != 0) {
                    tiles[i] = new byte[tile * tile / 4];
                    in.readFully(tiles[i]);
                    count++;
                }
            }
            return new WorldRoads(tile, ox, oz, wide, high, tiles, count);
        }
    }

    /** The column's road state: {@link #NONE}, {@link #EDGE}, {@link #INNER}, or {@link #UNKNOWN} where the file does not know the ground. */
    int at(int x, int z) {
        int tx = Math.floorDiv(x, tile) - ox;
        int tz = Math.floorDiv(z, tile) - oz;
        if (tx < 0 || tz < 0 || tx >= wide || tz >= high) {
            return UNKNOWN;
        }
        byte[] t = tiles[tz * wide + tx];
        if (t == null) {
            return UNKNOWN;
        }
        int i = Math.floorMod(x, tile) * tile + Math.floorMod(z, tile);
        return t[i >> 2] >> (2 * (i & 3)) & 3;
    }

    /** How many tiles know their ground. */
    int tiles() {
        return present;
    }

    int tileSize() {
        return tile;
    }
}
