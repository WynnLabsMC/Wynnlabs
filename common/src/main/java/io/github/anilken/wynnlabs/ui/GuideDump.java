package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A box of terrain around the player as block codes ({@link GuideTerrain}), with the player, the
 * guide line's target and the route settings: written by {@code /guide?dump=1}, read back by a
 * unit test to replay a dead end seen in the game. Each column is one run-length string, bottom
 * up ({@code "12#3.w"}: 12 solid, 3 air, 1 water); columns go x fastest, then z; {@code loaded}
 * holds a {@code +} (loaded) or {@code -} per column, run-length too. Pure: no
 * Minecraft types.
 */
final class GuideDump {

    static final int VERSION = 1;

    /** The box's lowest corner and its size. */
    final int x0, y0, z0, sx, sy, sz;
    /** Every column's codes, bottom up, at {@code (z - z0) * sx + (x - x0)}. */
    private final char[][] cols;
    /** The player's position, the target (null: none) and everything else as written. */
    final double[] player;
    final JsonObject target;
    final JsonObject settings;

    GuideDump(int x0, int y0, int z0, int sx, int sy, int sz, char[][] cols, double[] player,
              JsonObject target, JsonObject settings) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.cols = cols;
        this.player = player;
        this.target = target;
        this.settings = settings;
    }

    /** The code at a block; {@link GuideTerrain#UNKNOWN} outside the box. */
    char at(int x, int y, int z) {
        int i = x - x0;
        int j = y - y0;
        int k = z - z0;
        if (i < 0 || j < 0 || k < 0 || i >= sx || j >= sy || k >= sz) {
            return GuideTerrain.UNKNOWN;
        }
        return cols[k * sx + i][j];
    }

    GuideTerrain.Blocks blocks() {
        return this::at;
    }

    /** The highest block of a column that is not walked through, {@link GuideLine#UNKNOWN} with none or outside the box. */
    GuideLine.Ground ground() {
        return (x, z) -> {
            for (int y = y0 + sy - 1; y >= y0; y--) {
                char c = at(x, y, z);
                if (c == GuideTerrain.UNKNOWN) {
                    return GuideLine.UNKNOWN;
                }
                if (!GuideTerrain.passable(c)) {
                    return y + 1;
                }
            }
            return GuideLine.UNKNOWN;
        };
    }

    /** The player's feet block {x, y, z}, as the guide line takes it. */
    int[] start() {
        return new int[] {(int) Math.floor(player[0]), (int) Math.floor(player[1] + 0.01), (int) Math.floor(player[2])};
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("version", VERSION);
        o.add("origin", ints(x0, y0, z0));
        o.add("size", ints(sx, sy, sz));
        JsonArray p = new JsonArray();
        for (double v : player) {
            p.add(v);
        }
        o.add("player", p);
        o.add("target", target);
        o.add("settings", settings);
        JsonObject legend = new JsonObject();
        legend.addProperty(String.valueOf(GuideTerrain.AIR), "air");
        legend.addProperty(String.valueOf(GuideTerrain.SOLID), "solid");
        legend.addProperty(String.valueOf(GuideTerrain.ROAD), "solid road");
        legend.addProperty(String.valueOf(GuideTerrain.WATER), "water");
        legend.addProperty(String.valueOf(GuideTerrain.LAVA), "lava");
        legend.addProperty(String.valueOf(GuideTerrain.CLIMB), "climbable");
        legend.addProperty(String.valueOf(GuideTerrain.DOOR), "door, gate or trapdoor");
        legend.addProperty(String.valueOf(GuideTerrain.HALF), "slab or stair");
        legend.addProperty(String.valueOf(GuideTerrain.TALL), "fence or wall");
        legend.addProperty(String.valueOf(GuideTerrain.THIN), "other non-solid");
        legend.addProperty(String.valueOf(GuideTerrain.UNKNOWN), "not loaded");
        o.add("codes", legend);
        StringBuilder loaded = new StringBuilder();
        JsonArray c = new JsonArray();
        for (char[] col : cols) {
            loaded.append(col.length > 0 && col[0] == GuideTerrain.UNKNOWN ? '-' : '+');
            c.add(rle(col));
        }
        o.addProperty("loaded", rle(loaded.toString().toCharArray()));
        o.add("columns", c);
        return o;
    }

    static GuideDump fromJson(JsonObject o) {
        JsonArray origin = o.getAsJsonArray("origin");
        JsonArray size = o.getAsJsonArray("size");
        int sx = size.get(0).getAsInt();
        int sy = size.get(1).getAsInt();
        int sz = size.get(2).getAsInt();
        JsonArray c = o.getAsJsonArray("columns");
        char[][] cols = new char[sx * sz][];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = unrle(c.get(i).getAsString(), sy);
        }
        JsonArray p = o.getAsJsonArray("player");
        JsonElement t = o.get("target");
        return new GuideDump(origin.get(0).getAsInt(), origin.get(1).getAsInt(), origin.get(2).getAsInt(),
                sx, sy, sz, cols, new double[] {p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()},
                t == null || t.isJsonNull() ? null : t.getAsJsonObject(),
                o.has("settings") ? o.getAsJsonObject("settings") : new JsonObject());
    }

    /** Runs of the same code as count and code, the count left out when 1. */
    static String rle(char[] s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length) {
            int j = i;
            while (j < s.length && s[j] == s[i]) {
                j++;
            }
            if (j - i > 1) {
                out.append(j - i);
            }
            out.append(s[i]);
            i = j;
        }
        return out.toString();
    }

    /** {@link #rle} undone, to exactly {@code len} codes (the rest {@link GuideTerrain#UNKNOWN}). */
    static char[] unrle(String s, int len) {
        char[] out = new char[len];
        java.util.Arrays.fill(out, GuideTerrain.UNKNOWN);
        int at = 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                n = n * 10 + (ch - '0');
                continue;
            }
            for (int k = Math.max(1, n); k > 0 && at < len; k--) {
                out[at++] = ch;
            }
            n = 0;
        }
        return out;
    }

    private static JsonArray ints(int... v) {
        JsonArray a = new JsonArray();
        for (int i : v) {
            a.add(i);
        }
        return a;
    }
}
