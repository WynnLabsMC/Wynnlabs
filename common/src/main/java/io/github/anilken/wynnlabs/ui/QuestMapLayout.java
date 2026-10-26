package io.github.anilken.wynnlabs.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Where things go on the quest map, without drawing (pure, tested): the world box fitted to the
 * places, close places merged into one marker, and each marker's chip set down in free space
 * inside the frame. Logical pixels; the map's frame is {@code 0, 0, w, h}.
 */
final class QuestMapLayout {

    /** The closest the map comes: one block a logical pixel, so Wynntils' tiles never blur. */
    static final double MIN_SPAN = 1.0;
    /**
     * The furthest it goes: three blocks a logical pixel (one block a screen pixel at GUI 3);
     * further out the tiles turn to grain, so the map shows less and the rest go to edge chips.
     */
    static final double MAX_SPAN = 3.0;
    /** By hand (the wheel, +) the map goes closer than a fit does: two logical px a block. */
    static final double HAND_MIN_SPAN = 0.5;

    /** By hand (the wheel, −) the map goes out until the whole of Wynntils' map fits a {@code w} by {@code h} frame. */
    static double handMaxSpan(float[] coverage, int w, int h) {
        if (coverage == null || w <= 0 || h <= 0) {
            return MAX_SPAN;
        }
        return Math.max(MAX_SPAN, Math.max((coverage[2] - coverage[0]) / w, (coverage[3] - coverage[1]) / h));
    }
    /** The player shows on the map within this many blocks of a place; further off, an arrow on the border. */
    static final double NEAR = 1500;
    /** Markers closer than this (px) merge into one. */
    static final double MERGE = 14;

    static final int NOW = 0;
    static final int NEXT = 1;
    static final int DONE = 2;
    static final int START = 3;

    private QuestMapLayout() {}

    /** The world box shown: its top left (x0, z0) and blocks a pixel; {@code near} when the player is in it. */
    record Fit(double x0, double z0, double span, boolean near) {

        double px(double wx) {
            return (wx - x0) / span;
        }

        double py(double wz) {
            return (wz - z0) / span;
        }
    }

    /**
     * The box around {@code places} ({x, z} each) for a {@code w × h} map, the player in it only
     * when within {@link #NEAR}; margins keep room for a chip above and beside a marker.
     * {@code zoom} above 1 shows less (the + button), below 1 more.
     */
    static Fit fit(List<double[]> places, double[] player, int w, int h, double zoom) {
        boolean near = false;
        if (player != null) {
            for (double[] p : places) {
                near |= Math.hypot(p[0] - player[0], p[1] - player[1]) <= NEAR;
            }
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        List<double[]> all = new ArrayList<>(places);
        if (near) {
            all.add(player);
        }
        for (double[] p : all) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        int side = 40;
        int tall = 44;
        double span = Math.max(Math.max((maxX - minX) / Math.max(1, w - side), (maxZ - minZ) / Math.max(1, h - tall)), MIN_SPAN);
        double cx = (minX + maxX) / 2;
        double cz = (minZ + maxZ) / 2;
        if (span > MAX_SPAN && near) {
            // you and the places do not fit readably: the places only, you on the border
            Fit f = fit(places, null, w, h, zoom);
            return new Fit(f.x0(), f.z0(), f.span(), false);
        }
        if (span > MAX_SPAN) {
            // still too wide: around the first place, the rest on the border
            cx = places.get(0)[0];
            cz = places.get(0)[1];
        }
        span = Math.min(MAX_SPAN, Math.max(MIN_SPAN, span / Math.max(0.05, zoom)));
        return new Fit(cx - span * w / 2, cz - span * h / 2, span, near);
    }

    /** A place on the map: its number ("5.2"), where (px), and its state ({@link #NOW} …). */
    record Marker(String num, double x, double y, int state) {}

    /** One drawn marker: its places, where its dot is, its state (the most current of them). */
    record Group(List<String> nums, double x, double y, int state) {

        /** "5.2", or "5.1–5.3" for merged places (in number order: 5.9 before 5.10). */
        String label() {
            List<String> n = new ArrayList<>(nums);
            n.sort(java.util.Comparator.comparingInt(QuestMapLayout::stageOf).thenComparingInt(QuestMapLayout::stepOf));
            return n.size() == 1 ? n.get(0) : n.get(0) + "–" + n.get(n.size() - 1);
        }
    }

    /** A place number's stage: 5 for "5.2", 3 for "3"; -1 when it has none (a death, a cave). */
    static int stageOf(String num) {
        String s = num == null ? "" : num.contains(".") ? num.substring(0, num.indexOf('.')) : num;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** A place number's step in its stage: 2 for "5.2", 0 for "5". */
    static int stepOf(String num) {
        if (num == null || !num.contains(".")) {
            return 0;
        }
        try {
            return Integer.parseInt(num.substring(num.indexOf('.') + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The places a stage-focused map shows: the focused stage's (with chips), the next stage's (outlined dots only). */
    record Focus<T>(List<T> shown, List<T> ghosts) {}

    /**
     * Focus on {@code stage}: its places, plus {@code peek} (a list row of another stage under the
     * mouse) on its own; the next stage's places as ghosts; every other stage left out.
     */
    static <T> Focus<T> focus(List<T> places, java.util.function.Function<T, String> num, int stage, String peek) {
        List<T> shown = new ArrayList<>();
        List<T> ghosts = new ArrayList<>();
        for (T p : places) {
            String n = num.apply(p);
            int s = stageOf(n);
            if (s == stage || n.equals(peek)) {
                shown.add(p);
            } else if (s == stage + 1) {
                ghosts.add(p);
            }
        }
        return new Focus<>(shown, ghosts);
    }

    /**
     * Places closer than {@link #MERGE} become one marker; a passed place never merges with a live
     * one and the start never merges, so a done tick never hides what is left to do.
     */
    static List<Group> merge(List<Marker> markers) {
        return merge(markers, true);
    }

    /**
     * As above; {@code staged}: the places are numbered by stage, and a place only merges with the
     * one just before it of the same stage (never across stages, "14.6–5.11"). Unstaged places
     * (deaths, caves) merge with any close one.
     */
    static List<Group> merge(List<Marker> markers, boolean staged) {
        List<Group> out = new ArrayList<>();
        Marker last = null;
        for (Marker m : markers) {
            int found = -1;
            if (staged) {
                if (last != null && !out.isEmpty() && stageOf(last.num()) == stageOf(m.num())
                        && kin(out.get(out.size() - 1), m) && Math.hypot(last.x() - m.x(), last.y() - m.y()) < MERGE) {
                    found = out.size() - 1;
                }
            } else {
                for (int i = 0; i < out.size(); i++) {
                    Group g = out.get(i);
                    if (kin(g, m) && Math.hypot(g.x() - m.x(), g.y() - m.y()) < MERGE) {
                        found = i;
                        break;
                    }
                }
            }
            last = m;
            if (found < 0) {
                List<String> nums = new ArrayList<>();
                nums.add(m.num());
                out.add(new Group(nums, m.x(), m.y(), m.state()));
            } else {
                Group g = out.get(found);
                g.nums().add(m.num());
                out.set(found, new Group(g.nums(), g.x(), g.y(), Math.min(g.state(), m.state())));
            }
        }
        return out;
    }

    /** A passed place never merges with a live one, and the start never merges. */
    private static boolean kin(Group g, Marker m) {
        return (g.state() == DONE) == (m.state() == DONE) && g.state() != START && m.state() != START;
    }

    /** The directions a chip tries around its dot, most natural first (degrees, 0 = east, y down). */
    private static final int[] ANGLES = {-60, -120, -20, -160, 20, 160, 60, 120};
    private static final int[] REACH = {16, 24, 32};

    /**
     * A chip's top left for the dot at (dx, dy): the first spot around it, inside the frame by
     * {@code inset}, clear of every dot, every chip already placed and the {@code avoid} boxes;
     * else beside the dot, clamped into the frame. Boxes are {x, y, w, h}.
     */
    static double[] chip(double dx, double dy, double cw, double ch, int w, int h, int inset,
                         List<double[]> dots, List<double[]> placed, List<double[]> avoid) {
        for (int reach : REACH) {
            for (int a : ANGLES) {
                double cx = dx + reach * Math.cos(Math.toRadians(a));
                double cy = dy + reach * Math.sin(Math.toRadians(a));
                double bx = cx - cw / 2;
                double by = cy - ch / 2;
                if (bx < inset || by < inset || bx + cw > w - inset || by + ch > h - inset) {
                    continue;
                }
                if (clear(bx, by, cw, ch, dots, placed, avoid)) {
                    return new double[] {bx, by};
                }
            }
        }
        double bx = Math.max(inset, Math.min(w - inset - cw, dx + 6));
        double by = Math.max(inset, Math.min(h - inset - ch, dy - ch - 6));
        return new double[] {bx, by};
    }

    private static boolean clear(double bx, double by, double cw, double ch, List<double[]> dots, List<double[]> placed,
                                 List<double[]> avoid) {
        for (double[] d : dots) {
            if (d[0] > bx - 3 && d[0] < bx + cw + 3 && d[1] > by - 3 && d[1] < by + ch + 3) {
                return false;
            }
        }
        for (List<double[]> boxes : List.of(placed, avoid)) {
            for (double[] b : boxes) {
                if (bx < b[0] + b[2] + 2 && b[0] < bx + cw + 2 && by < b[1] + b[3] + 2 && b[1] < by + ch + 2) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a dot at (px, py) is inside the frame with room for its chip; else it goes to an edge chip. */
    static boolean onMap(double px, double py, int w, int h) {
        return px >= 8 && py >= 8 && px <= w - 8 && py <= h - 8;
    }

    /**
     * Where a chip for something outside the frame sits: on the border toward it from the middle,
     * clear of the corner by {@code inset}; {x, y} of the chip's top left.
     */
    static double[] edge(double px, double py, double cw, double ch, int w, int h, int inset) {
        double ang = Math.atan2(py - h / 2.0, px - w / 2.0);
        double rx = (w / 2.0 - cw / 2.0 - inset) / Math.max(1e-6, Math.abs(Math.cos(ang)));
        double ry = (h / 2.0 - ch / 2.0 - inset) / Math.max(1e-6, Math.abs(Math.sin(ang)));
        double r = Math.min(rx, ry);
        double bx = w / 2.0 + r * Math.cos(ang) - cw / 2.0;
        double by = h / 2.0 + r * Math.sin(ang) - ch / 2.0;
        // flush against the border it reached (rounding never leaves it floating inside)
        if (rx <= ry) {
            bx = Math.cos(ang) > 0 ? w - inset - cw : inset;
        } else {
            by = Math.sin(ang) > 0 ? h - inset - ch : inset;
        }
        return new double[] {bx, by};
    }

    /**
     * The route's segments for the view {@code f}: the places in their stage order (as given),
     * each projected where it really is now, every leg clipped to the frame; legs wholly outside
     * are left out, a leg toward a place out of view runs to the border on its way there.
     */
    static List<double[]> route(boolean ordered, Fit f, List<double[]> places, int w, int h) {
        return ordered ? route(f, places, w, h) : List.of();         // separate events (deaths): no path between them
    }

    static List<double[]> route(Fit f, List<double[]> places, int w, int h) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < places.size(); i++) {
            double[] c = clip(f.px(places.get(i)[0]), f.py(places.get(i)[1]), f.px(places.get(i + 1)[0]), f.py(places.get(i + 1)[1]), w, h);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    /** The part of the segment a → b inside 0..w, 0..h (Liang–Barsky), or null when none is. */
    static double[] clip(double ax, double ay, double bx, double by, double w, double h) {
        double dx = bx - ax;
        double dy = by - ay;
        double t0 = 0;
        double t1 = 1;
        double[][] edges = {{-dx, ax}, {dx, w - ax}, {-dy, ay}, {dy, h - ay}};
        for (double[] e : edges) {
            if (e[0] == 0) {
                if (e[1] < 0) {
                    return null;
                }
                continue;
            }
            double t = e[1] / e[0];
            if (e[0] < 0) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
            if (t0 > t1) {
                return null;
            }
        }
        return new double[] {ax + t0 * dx, ay + t0 * dy, ax + t1 * dx, ay + t1 * dy};
    }

    /**
     * What the map shows, in stage order: the places not yet done. Passed places never reach the
     * map (no dot, chip, edge chip or route leg), so the route starts at the stage you are on.
     */
    static <T> List<T> remaining(List<T> places, java.util.function.ToIntFunction<T> state) {
        List<T> out = new ArrayList<>();
        for (T p : places) {
            if (state.applyAsInt(p) != DONE) {
                out.add(p);
            }
        }
        return out;
    }

    /** How far (blocks) the player at {@code me} is from the world area the view {@code f} shows; 0 when in it. */
    static double offView(Fit f, int w, int h, double[] me) {
        double cx = Math.max(f.x0(), Math.min(f.x0() + f.span() * w, me[0]));
        double cz = Math.max(f.z0(), Math.min(f.z0() + f.span() * h, me[1]));
        return Math.hypot(me[0] - cx, me[1] - cz);
    }

    /**
     * A chip on the border ({@link #edge}) moved along that border, never into the map, until it
     * clears {@code placed}: left and right borders slide up and down, top and bottom left and right.
     */
    static double[] onBorder(double[] at, double cw, double ch, int w, int h, int inset, List<double[]> placed) {
        boolean side = at[0] <= inset + 0.5 || at[0] + cw >= w - inset - 0.5;
        double[] best = at;
        for (int k = 0; k <= 8; k++) {
            double step = (k % 2 == 1 ? 1 : -1) * ((k + 1) / 2) * ((side ? ch : cw) + 3);
            double[] c = side ? new double[] {at[0], Math.max(inset, Math.min(h - inset - ch, at[1] + step))}
                    : new double[] {Math.max(inset, Math.min(w - inset - cw, at[0] + step)), at[1]};
            boolean free = true;
            for (double[] b : placed) {
                free &= !(c[0] < b[0] + b[2] + 2 && b[0] < c[0] + cw + 2 && c[1] < b[1] + b[3] + 2 && b[1] < c[1] + ch + 2);
            }
            if (free) {
                return c;
            }
            best = k == 0 ? c : best;
        }
        return best;
    }

    /** Whether a box lies inside the frame by {@code inset} (the clipping rule). */
    static boolean inside(double bx, double by, double bw, double bh, int w, int h, int inset) {
        return bx >= inset && by >= inset && bx + bw <= w - inset && by + bh <= h - inset;
    }
}
