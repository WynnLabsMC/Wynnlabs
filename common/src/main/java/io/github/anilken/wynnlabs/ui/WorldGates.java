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
 * level): the mouth of a passage under or through a mountain wall, which the coarse A* enters to
 * come out on walkable ground beyond ({@link WorldRoute#gates}). The lines are ridge lines a
 * player cannot cross (Wynncraft's invisible barrier walls); a step may not cross one except
 * within {@link #GATE} blocks of a gate. The passages under the mountains from each gate were
 * worked out with the map, offline, and come in the same file. Read from {@code world-gates.json}:
 * {@code {"lines": [[[x, z], [x, z], ...], ...], "gates": [{"name", "x", "z", "level"}, ...],
 * "passages": [[x1, z1, x2, z2], ...]}}, every list optional; anything malformed makes
 * {@link #parse} return null, and the guide goes on without.
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
    /** The passages {x1, z1, x2, z2} in blocks, both ways; null when the file has none (the map works them out). */
    private final List<double[]> passages;

    WorldGates(List<double[][]> lines, List<Gate> gates) {
        this(lines, gates, null);
    }

    WorldGates(List<double[][]> lines, List<Gate> gates, List<double[]> passages) {
        this.lines = lines;
        this.gates = gates;
        this.passages = passages;
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
            List<double[]> passages = null;
            if (o.has("passages")) {
                passages = new ArrayList<>();
                for (JsonElement p : o.getAsJsonArray("passages")) {
                    JsonArray a = p.getAsJsonArray();
                    if (a.size() != 4) {
                        return null;
                    }
                    passages.add(new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(),
                            a.get(2).getAsDouble(), a.get(3).getAsDouble()});
                }
                passages = List.copyOf(passages);
            }
            return new WorldGates(List.copyOf(lines), List.copyOf(gates), passages);
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

    int gates() {
        return gates.size();
    }

    List<Gate> gateList() {
        return gates;
    }

    /** The passages worked out offline, null when the file has none. */
    List<double[]> passages() {
        return passages;
    }

    /** Whether the step from (ax, az) to (bx, bz) crosses a line away from every gate. */
    boolean blocked(double ax, double az, double bx, double bz) {
        if (lines.isEmpty()) {
            return false;
        }
        for (double[][] line : lines) {
            for (int i = 1; i < line.length; i++) {
                if (cross(ax, az, bx, bz, line[i - 1][0], line[i - 1][1], line[i][0], line[i][1])
                        && !nearGate((ax + bx) / 2, (az + bz) / 2)) {
                    return true;
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
