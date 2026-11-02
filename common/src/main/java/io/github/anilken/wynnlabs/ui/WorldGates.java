package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * The region entrances and the walls the terrain does not show, for the world route. A gate is
 * an access point of a region from the Wynncraft wiki (its name, block x and z, and suggested
 * level). A corridor is a road the wiki describes as a Location under the mountains: from one of
 * its access points to its location point, walked at the cost of flat ground through the rock the
 * map shows over it ({@link WorldRoute#gates}); a gate leads anywhere only as the mouth of such a
 * corridor. The lines are ridge lines a player cannot cross (Wynncraft's invisible barrier walls);
 * a step may not cross one except within {@link #GATE} blocks of a gate. Read from
 * {@code world-gates.json}: {@code {"lines": [[[x, z], [x, z], ...], ...], "gates": [{"name", "x",
 * "z", "level"}, ...], "corridors": [{"name", "from": [x, z], "to": [x, z]}, ...]}}, every list
 * optional; anything malformed makes {@link #parse} return null, and the guide goes on without.
 */
final class WorldGates {

    /** A line may be crossed within this many blocks of a gate. */
    static final double GATE = 8;

    static final WorldGates NONE = new WorldGates(List.of(), List.of());

    /** A region's entrance: its name, block position and the level the wiki suggests for the region (0: unknown). */
    record Gate(String name, double x, double z, int level) {}

    /** Each line's points {x, z}, in order. */
    private final List<double[][]> lines;
    private final List<Gate> gates;
    private final List<Corridor> corridors;

    /** A road under the mountains: the Location's name, and the access point and location point it joins, in blocks. */
    record Corridor(String name, double fromX, double fromZ, double toX, double toZ) {}

    WorldGates(List<double[][]> lines, List<Gate> gates) {
        this(lines, gates, List.of());
    }

    WorldGates(List<double[][]> lines, List<Gate> gates, List<Corridor> corridors) {
        this.lines = lines;
        this.gates = gates;
        this.corridors = corridors;
        // each wall segment {x1, z1, x2, z2} under every square of BUCKET blocks its bounding box touches
        java.util.Map<Long, List<double[]>> b = new java.util.HashMap<>();
        for (double[][] line : lines) {
            for (int i = 1; i < line.length; i++) {
                double[] s = {line[i - 1][0], line[i - 1][1], line[i][0], line[i][1]};
                int x0 = bucket(Math.min(s[0], s[2]));
                int x1 = bucket(Math.max(s[0], s[2]));
                int z0 = bucket(Math.min(s[1], s[3]));
                int z1 = bucket(Math.max(s[1], s[3]));
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        b.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(s);
                    }
                }
            }
        }
        this.buckets = b;
    }

    /** The wall segments are looked up by squares this many blocks wide. */
    private static final double BUCKET = 64;
    private final java.util.Map<Long, List<double[]>> buckets;

    private static int bucket(double v) {
        return (int) Math.floor(v / BUCKET);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** The walls in {@code json}; null when it is not the expected shape. */
    static WorldGates parse(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            List<double[][]> lines = new ArrayList<>();
            List<Gate> gates = new ArrayList<>();
            if (o.has("lines")) {
                for (JsonElement l : o.getAsJsonArray("lines")) {
                    JsonArray pts = l.getAsJsonArray();
                    if (pts.size() < 2) {
                        return null;
                    }
                    double[][] line = new double[pts.size()][];
                    for (int i = 0; i < pts.size(); i++) {
                        line[i] = point(pts.get(i));
                    }
                    lines.add(line);
                }
            }
            if (o.has("gates")) {
                for (JsonElement g : o.getAsJsonArray("gates")) {
                    JsonObject j = g.getAsJsonObject();
                    gates.add(new Gate(j.has("name") && !j.get("name").isJsonNull() ? j.get("name").getAsString() : "",
                            j.get("x").getAsDouble(), j.get("z").getAsDouble(),
                            j.has("level") && !j.get("level").isJsonNull() ? j.get("level").getAsInt() : 0));
                }
            }
            List<Corridor> corridors = new ArrayList<>();
            if (o.has("corridors")) {
                for (JsonElement c : o.getAsJsonArray("corridors")) {
                    JsonObject j = c.getAsJsonObject();
                    double[] from = point(j.get("from"));
                    double[] to = point(j.get("to"));
                    corridors.add(new Corridor(j.has("name") && !j.get("name").isJsonNull() ? j.get("name").getAsString() : "",
                            from[0], from[1], to[0], to[1]));
                }
            }
            return new WorldGates(List.copyOf(lines), List.copyOf(gates), List.copyOf(corridors));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double[] point(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 2) {
            throw new IllegalArgumentException("a point has two numbers");
        }
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble()};
    }

    int lines() {
        return lines.size();
    }

    /** How many wall segments the lines make. */
    int segments() {
        int n = 0;
        for (double[][] line : lines) {
            n += line.length - 1;
        }
        return n;
    }

    int gates() {
        return gates.size();
    }

    List<Gate> gateList() {
        return gates;
    }

    List<Corridor> corridors() {
        return corridors;
    }

    /** Whether the step from (ax, az) to (bx, bz) crosses a line away from every gate. */
    boolean blocked(double ax, double az, double bx, double bz) {
        if (buckets.isEmpty()) {
            return false;
        }
        // only the segments under the squares the step's own box touches
        for (int x = bucket(Math.min(ax, bx)); x <= bucket(Math.max(ax, bx)); x++) {
            for (int z = bucket(Math.min(az, bz)); z <= bucket(Math.max(az, bz)); z++) {
                List<double[]> segs = buckets.get(key(x, z));
                if (segs == null) {
                    continue;
                }
                for (double[] s : segs) {
                    if (cross(ax, az, bx, bz, s[0], s[1], s[2], s[3]) && !nearGate((ax + bx) / 2, (az + bz) / 2)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean nearGate(double x, double z) {
        for (Gate g : gates) {
            if (Math.hypot(g.x() - x, g.z() - z) <= GATE) {
                return true;
            }
        }
        return false;
    }

    /** Whether the segments a-b and c-d meet (touching counts). */
    static boolean cross(double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz) {
        double d1 = side(cx, cz, dx, dz, ax, az);
        double d2 = side(cx, cz, dx, dz, bx, bz);
        double d3 = side(ax, az, bx, bz, cx, cz);
        double d4 = side(ax, az, bx, bz, dx, dz);
        if ((d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0) && d1 != 0 && d2 != 0 && d3 != 0 && d4 != 0) {
            return true;
        }
        return d1 == 0 && on(cx, cz, dx, dz, ax, az) || d2 == 0 && on(cx, cz, dx, dz, bx, bz)
                || d3 == 0 && on(ax, az, bx, bz, cx, cz) || d4 == 0 && on(ax, az, bx, bz, dx, dz);
    }

    private static double side(double ax, double az, double bx, double bz, double px, double pz) {
        return (bx - ax) * (pz - az) - (bz - az) * (px - ax);
    }

    private static boolean on(double ax, double az, double bx, double bz, double px, double pz) {
        return Math.min(ax, bx) <= px && px <= Math.max(ax, bx) && Math.min(az, bz) <= pz && pz <= Math.max(az, bz);
    }
}
