package io.github.anilken.wynnlabs.quests;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The coordinates a guide line writes inline ("biri -1655, -2937, diğeri -1650, -2949",
 * "(-1639, 68, -2908)"), each one a place the guide can point the compass at. Three numbers are
 * a place as they are; two (x and z) take the height of a stage point nearby, or the player's
 * when there is none (the wiki's own x/z maps are read the same way, see {@link QuestWiki}).
 */
public final class GuideCoords {

    /**
     * A group in a line: where it is ({@code start} to {@code end}) and the place; {@code knowsY}
     * false when the line gave x and z only and no stage point was near ({@code at[1]} is then 0).
     */
    public record Spot(int start, int end, int[] at, boolean knowsY) {}

    private static final Pattern XYZ = Pattern.compile("(?<![\\d.,-])[\\[(]?(-?\\d{1,5}),\\s*(-?\\d{1,3}),\\s*(-?\\d{1,5})[\\])]?(?![\\d.])");
    /** "(-1656 75 -2939)": three numbers in brackets without commas. */
    private static final Pattern XYZ_SPACED = Pattern.compile("[\\[(](-?\\d{1,5}) (-?\\d{1,3}) (-?\\d{1,5})[\\])]");
    /** "-1657, -2940" or "(-1797 -2120)": two numbers of two digits or more. */
    private static final Pattern XZ = Pattern.compile("(?<![\\d.,-])[\\[(]?(-?\\d{2,5})(?:,\\s*|\\s+)(-?\\d{2,5})[\\])]?(?![\\d.]|,\\s*-?\\d)");

    /** How far (x and z, in blocks) a stage point may be to lend its height to an x/z pair. */
    static final int NEAR = 32;

    private GuideCoords() {}

    /** The groups in {@code line}, in order; {@code points} are the stage's places, for x/z pairs' height. */
    public static List<Spot> find(String line, List<int[]> points) {
        List<Spot> out = new ArrayList<>();
        boolean[] taken = new boolean[line.length()];
        for (Pattern p : new Pattern[] {XYZ, XYZ_SPACED}) {
            Matcher m = p.matcher(line);
            while (m.find()) {
                int[] at = {num(m.group(1)), num(m.group(2)), num(m.group(3))};
                if (at[1] >= -64 && at[1] <= 320 && Math.abs(at[0]) + Math.abs(at[2]) >= 100 && free(taken, m.start(), m.end())) {
                    take(taken, m.start(), m.end());
                    out.add(new Spot(m.start(), m.end(), at, true));
                }
            }
        }
        Matcher m = XZ.matcher(line);
        while (m.find()) {
            int x = num(m.group(1));
            int z = num(m.group(2));
            if (Math.abs(x) >= 100 && Math.abs(z) >= 100 && free(taken, m.start(), m.end())) {
                take(taken, m.start(), m.end());
                int[] near = near(points, x, z);
                out.add(new Spot(m.start(), m.end(), new int[] {x, near == null ? 0 : near[1], z}, near != null));
            }
        }
        out.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return out;
    }

    /** The stage point closest to x/z within {@link #NEAR} blocks, or null. */
    static int[] near(List<int[]> points, int x, int z) {
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int[] p : points == null ? List.<int[]>of() : points) {
            int d = Math.max(Math.abs(p[0] - x), Math.abs(p[2] - z));
            if (d <= NEAR && d < bestD) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    private static boolean free(boolean[] taken, int from, int to) {
        for (int i = from; i < to; i++) {
            if (taken[i]) {
                return false;
            }
        }
        return true;
    }

    private static void take(boolean[] taken, int from, int to) {
        for (int i = from; i < to; i++) {
            taken[i] = true;
        }
    }
}
