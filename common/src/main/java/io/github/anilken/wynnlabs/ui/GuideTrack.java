package io.github.anilken.wynnlabs.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The guide line's drawn track ({@link GuideLine}): the ground path from the player's feet laid on
 * the block tops and eased, and the ribbon, flowing chevrons, disc, arrow and faint tail drawn
 * along it. Drawing only.
 */
final class GuideTrack {

    /** Dashes (chevrons) this long and wide with gaps this long, in blocks; they flow this many blocks a second. */
    static final double DASH = 1.3;
    static final float DASH_WIDTH = 0.6f;
    static final double GAP = 0.5;
    static final double FLOW = 2.0;
    /** At most this many dashes, the nearest the player. */
    static final int DASHES = 64;
    /** The walkable path is walked in steps this long, laid on the block tops under it, ... */
    static final double SAMPLE = 0.25;
    /** A turn of the drawn line is rounded over this many blocks to either side (the route's own corners are block-sharp). */
    static final double ROUND = 2.5;
    /** ... with a step's edge eased into a slope this steep (a block up a block along: a stair's run). */
    static final double EASE = 1.0;
    /** The base ribbon lies this far above the ground and the chevrons this far, riding on it. */
    static final float RIBBON_LIFT = 0.04f;
    static final float DASH_LIFT = 0.07f;
    /** The base ribbon: its width and opacity. */
    private static final float RIBBON_WIDTH = 0.5f;
    private static final float RIBBON_ALPHA = 0.28f;
    /** The brightness pulse: this much up and down, over this many seconds. */
    static final double PULSE = 0.2;
    static final double PULSE_SECONDS = 1.2;
    /** The glowing disc under the pillar: its radius in blocks. */
    private static final double DISC = 0.8;
    /** The first blocks from the player fade in over this length. */
    static final double FADE = 1.5;
    /** Height of the pillar at the end of the path. */
    static final float PILLAR = 6f;
    /** Light cyan for the glow, pillar and disc; the chevrons go from {@link #TAIL} to white at the tip. */
    static final int BODY = 0x8FDFFF;
    private static final int TAIL = 0x7FD8FF;
    private static final int TIP = 0xFFFFFF;
    private static final int CORE = 0xEAFBFF;
    /** The base ribbon under the chevrons. */
    private static final int RIBBON = 0x5FC8FF;
    /** The arrow toward a target that cannot be walked to: its length, and its height over the last spot. */
    private static final double ARROW = 3;
    private static final double ARROW_UP = 1.2;
    /** The straight guess past the route is drawn at this share of the opacity, still. */
    static final float TAIL_ALPHA = 1 / 3f;

    private GuideTrack() {}

    /** Opacity at {@code d} blocks along the line from the player: none at the feet, full from {@link #FADE} on. */
    static float nearFade(double d) {
        return (float) Math.max(0, Math.min(1, d / FADE));
    }

    /**
     * Where a dash covers the spot {@code left} blocks before the line's end at {@code seconds}:
     * -1 in a gap, else how far along the dash from its back (0) to its front (1). The pattern is
     * fixed to the end, so a new route (which mostly changes the player end) does not make the
     * dashes jump, and it moves toward the end by {@link #FLOW} blocks a second.
     */
    static double dashAt(double left, double seconds) {
        double period = DASH + GAP;
        double u = ((left + seconds * FLOW) % period + period) % period;
        return u < DASH ? 1 - u / DASH : -1;
    }

    /**
     * Index of the segment of {@code pts} closest to (x, z), seen from above, and how far along it
     * (0..1): where the path is cut so it starts at the player's feet.
     */
    static double[] nearest(List<double[]> pts, double x, double z) {
        double best = Double.MAX_VALUE;
        double[] out = {0, 0};
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            double dx = b[0] - a[0];
            double dz = b[2] - a[2];
            double l2 = dx * dx + dz * dz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[2]) * dz) / l2));
            double d = sq(a[0] + dx * t - x) + sq(a[2] + dz * t - z);
            if (d < best) {
                best = d;
                out[0] = i - 1;
                out[1] = t;
            }
        }
        return out;
    }

    /**
     * A stretch of path walked by arc length: {@code s[i]} blocks from its start lies the point
     * ({@code x[i]}, {@code y[i]}, {@code z[i]}). {@code toEnd} is how far its start is from the
     * end of the walkable route, which the dashes are fixed to, so a new route (which mostly
     * changes the player end) does not make them jump.
     */
    record Track(double[] s, double[] x, double[] y, double[] z, double toEnd) {
        double length() {
            return s.length == 0 ? 0 : s[s.length - 1];
        }
    }

    /**
     * {@code pts} walked from its first point, a point every {@code step} blocks of arc length up
     * to {@code max} blocks (the last point where the path ends sooner); {@code toEnd} is the
     * whole path's length.
     */
    static Track resample(List<double[]> pts, double step, double max) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += dist(pts.get(i - 1), pts.get(i));
        }
        double len = Math.min(total, max);
        int n = Math.max(1, (int) Math.ceil(len / step)) + 1;
        double[] s = new double[n];
        double[] x = new double[n];
        double[] y = new double[n];
        double[] z = new double[n];
        int seg = 1;
        double segStart = 0;
        for (int i = 0; i < n; i++) {
            double at = Math.min(len, i * step);
            while (seg < pts.size() - 1 && segStart + dist(pts.get(seg - 1), pts.get(seg)) < at) {
                segStart += dist(pts.get(seg - 1), pts.get(seg));
                seg++;
            }
            double[] a = pts.get(Math.min(seg - 1, pts.size() - 1));
            double[] b = pts.get(Math.min(seg, pts.size() - 1));
            double l = dist(a, b);
            double t = l < 1e-9 ? 0 : Math.max(0, Math.min(1, (at - segStart) / l));
            s[i] = at;
            x[i] = a[0] + (b[0] - a[0]) * t;
            y[i] = a[1] + (b[1] - a[1]) * t;
            z[i] = a[2] + (b[2] - a[2]) * t;
        }
        return new Track(s, x, y, z, total);
    }

    /** The point {x, y, z} {@code at} blocks along the track (clamped to its ends). */
    static double[] at(Track t, double at) {
        double[] s = t.s();
        int n = s.length;
        if (n == 1 || at <= 0) {
            return new double[] {t.x()[0], t.y()[0], t.z()[0]};
        }
        if (at >= s[n - 1]) {
            return new double[] {t.x()[n - 1], t.y()[n - 1], t.z()[n - 1]};
        }
        int i = java.util.Arrays.binarySearch(s, at);
        if (i >= 0) {
            return new double[] {t.x()[i], t.y()[i], t.z()[i]};
        }
        int b = -i - 1;
        int a = b - 1;
        double k = (at - s[a]) / (s[b] - s[a]);
        return new double[] {t.x()[a] + (t.x()[b] - t.x()[a]) * k, t.y()[a] + (t.y()[b] - t.y()[a]) * k,
                t.z()[a] + (t.z()[b] - t.z()[a]) * k};
    }

    /**
     * Where the dashes' centres lie along a track of {@code length} blocks whose start is
     * {@code toEnd} from the route's end, at {@code seconds}: one every {@link #DASH} + {@link #GAP}
     * blocks counted back from the end, moving toward it by {@link #FLOW} blocks a second, at most
     * {@link #DASHES}, nearest the player first; only those that fit whole on the track.
     */
    static double[] centres(double toEnd, double length, double seconds) {
        double period = DASH + GAP;
        double shift = ((seconds * FLOW) % period + period) % period;
        // the first centre past the dash's half length from the start
        double first = toEnd - period * Math.floor((toEnd + shift - DASH / 2) / period) + shift;
        double[] out = new double[DASHES];
        int n = 0;
        for (double c = first; c + DASH / 2 <= length + 1e-9 && n < DASHES; c += period) {
            if (c - DASH / 2 >= -1e-9) {
                out[n++] = c;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** The gentle brightness pulse: 1 ± {@link #PULSE} over {@link #PULSE_SECONDS}. */
    static float pulse(double seconds) {
        return (float) (1 + PULSE * Math.sin(2 * Math.PI * seconds / PULSE_SECONDS));
    }

    /**
     * Heights {@code y} of points {@code step} blocks apart, with every rise and drop eased into a
     * slope of {@link #EASE}: each point is at least as high as any point near it less the slope
     * times the distance. A staircase becomes the line over its step edges, so a chevron lies on
     * the stair's run instead of standing up at a step; nothing goes below the real ground.
     */
    static double[] ease(double[] y, double step) {
        int reach = (int) Math.ceil(1.5 / step);
        double[] out = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            double h = y[i];
            for (int j = Math.max(0, i - reach); j <= Math.min(y.length - 1, i + reach); j++) {
                h = Math.max(h, y[j] - EASE * step * Math.abs(i - j));
            }
            out[i] = h;
        }
        return out;
    }

    /**
     * Every 2 ticks: the walkable part of the ground path from the player's feet, a point every
     * {@link #SAMPLE} blocks, each on the top of the block it is over (a slab, a stair step), the
     * step edges eased ({@link #ease}).
     */
    static Track buildTrack(Minecraft mc, GuideLine.Path g) {
        Player p = mc.player;
        List<double[]> pts = g.points();
        if (p == null || mc.level == null || pts.size() < 2) {
            return null;
        }
        int tail = Math.max(1, Math.min(pts.size(), g.tail()));
        if (tail < 2) {
            return null;
        }
        Track raw = resample(fromFeet(pts.subList(0, tail), p.getX(), p.getZ()), SAMPLE, GuideLine.CAP);
        // the turns rounded: each point the mean of the points within ROUND of it along the line
        // (a shrinking window toward the ends, so they stay put); a rounded point that would stand
        // in a block (a corner cut into a gate post) goes back where it was
        int n = raw.s().length;
        double[] x = raw.x().clone();
        double[] z = raw.z().clone();
        int r = (int) Math.round(ROUND / SAMPLE);
        if (n > 2 && r > 0) {
            double[] px = new double[n + 1];
            double[] pz = new double[n + 1];
            for (int i = 0; i < n; i++) {
                px[i + 1] = px[i] + raw.x()[i];
                pz[i + 1] = pz[i] + raw.z()[i];
            }
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int i = 1; i < n - 1; i++) {
                int k = Math.min(r, Math.min(i, n - 1 - i));
                double mx = (px[i + k + 1] - px[i - k]) / (2 * k + 1);
                double mz = (pz[i + k + 1] - pz[i - k]) / (2 * k + 1);
                int by = (int) Math.floor(raw.y()[i] - GuideLine.LIFT + 0.5);
                boolean free = mc.level.getBlockState(at.set(Math.floor(mx), by, Math.floor(mz))).getCollisionShape(mc.level, at).isEmpty()
                        && mc.level.getBlockState(at.set(Math.floor(mx), by + 1, Math.floor(mz))).getCollisionShape(mc.level, at).isEmpty();
                if (free) {
                    x[i] = mx;
                    z[i] = mz;
                }
            }
        }
        double[] y = raw.y().clone();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < y.length; i++) {
            y[i] = surface(mc.level, pos, x[i], z[i], y[i] - GuideLine.LIFT);
        }
        return new Track(raw.s(), x, ease(y, SAMPLE), z, raw.toEnd());
    }

    /**
     * The walking surface at (x, z) near height {@code near}: the top of the highest block there
     * that is not more than a little above it (a slab or stair step counts at its own height).
     */
    private static double surface(ClientLevel level, BlockPos.MutableBlockPos pos, double x, double z, double near) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(near);
        for (int y = by + 1; y >= by - 2; y--) {
            BlockState s = level.getBlockState(pos.set(bx, y, bz));
            VoxelShape shape = s.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                if (top <= near + 0.75) {
                    return top;
                }
            }
        }
        return near;
    }

    /** The path cut where it passes the player, so it starts at their feet (it is up to a second old). */
    private static List<double[]> fromFeet(List<double[]> pts, double x, double z) {
        double[] at = nearest(pts, x, z);
        int i = (int) at[0];
        double[] a = pts.get(i);
        double[] b = pts.get(i + 1);
        double t = at[1];
        List<double[]> out = new ArrayList<>(pts.size() - i);
        out.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
        out.addAll(pts.subList(i + 1, pts.size()));
        return out;
    }

    /**
     * The base ribbon along the whole track, under the chevrons: one strip through every point,
     * each point's edge vertices shared with the next quad so the strip runs on unbroken round the
     * turns, every point at its own ground height.
     */
    static void ribbon(PoseStack pose, VertexConsumer v, Vec3 c, Track t) {
        double[] s = t.s();
        int n = s.length;
        if (n < 2) {
            return;
        }
        int r = RIBBON >> 16 & 0xFF;
        int g = RIBBON >> 8 & 0xFF;
        int b = RIBBON & 0xFF;
        var e = pose.last();
        float[] prev = null;
        float prevX = 0, prevY = 0, prevZ = 0;
        int prevA = 0;
        for (int i = 0; i < n; i++) {
            double[] pt = {t.x()[i], t.y()[i], t.z()[i]};
            float[] o = side(t, s[i], pt, c, true, RIBBON_WIDTH / 2);
            float x = (float) (pt[0] - c.x), y = (float) (pt[1] - c.y) + RIBBON_LIFT, z = (float) (pt[2] - c.z);
            int a = Math.round(255 * RIBBON_ALPHA * nearFade(s[i]));
            if (prev != null && (a > 0 || prevA > 0)) {
                v.addVertex(e, prevX - prev[0], prevY - prev[1], prevZ - prev[2]).setColor(r, g, b, prevA);
                v.addVertex(e, prevX + prev[0], prevY + prev[1], prevZ + prev[2]).setColor(r, g, b, prevA);
                v.addVertex(e, x + o[0], y + o[1], z + o[2]).setColor(r, g, b, a);
                v.addVertex(e, x - o[0], y - o[1], z - o[2]).setColor(r, g, b, a);
            }
            prev = o;
            prevX = x;
            prevY = y;
            prevZ = z;
            prevA = a;
        }
    }

    /**
     * The flowing chevrons along a track: each a narrow tail widening to its broadest point and
     * closing to a tip toward the target, its three stations placed by arc length and at the
     * track's (eased) height there, so a dash lies flat on the ground, tilts with a stair's run
     * and bends with the path at a turn. Three layers: a wide soft glow, the body (light cyan at
     * the tail to white at the tip) and a bright thin core. Flat on the ground with {@code flat},
     * else turned to face the camera; {@code lift} above the track.
     */
    static void chevrons(PoseStack pose, VertexConsumer v, Vec3 c, Track t, double seconds,
                                 boolean flat, float glow, float lift) {
        double[] centres = centres(t.toEnd(), t.length(), seconds);
        for (int layer = 0; layer < 3; layer++) {
            float width = layer == 0 ? 1.1f : layer == 1 ? DASH_WIDTH : 0.12f;
            float alpha = (layer == 0 ? 0.3f : layer == 1 ? 0.7f : 1f) * glow;
            int back = layer == 0 ? TAIL : layer == 1 ? TAIL : CORE;
            int front = layer == 0 ? TAIL : TIP;
            float up = lift + layer * 0.003f;
            for (double s : centres) {
                float fade = nearFade(s - DASH / 2) * alpha;
                if (fade < 0.01f) {
                    continue;
                }
                double[] tail = at(t, s - DASH / 2);
                double[] mid = at(t, s + DASH * 0.1);
                double[] tip = at(t, s + DASH / 2);
                float[] ot = side(t, s - DASH / 2, tail, c, flat, width * 0.15f);
                float[] om = side(t, s + DASH * 0.1, mid, c, flat, width * 0.5f);
                int a = Math.round(255 * Math.min(1, fade));
                int cb = back;
                int cm = mix(back, front, 0.6f);
                int cf = front;
                var e = pose.last();
                float tx = (float) (tail[0] - c.x), ty = (float) (tail[1] - c.y) + up, tz = (float) (tail[2] - c.z);
                float mx = (float) (mid[0] - c.x), my = (float) (mid[1] - c.y) + up, mz = (float) (mid[2] - c.z);
                float px = (float) (tip[0] - c.x), py = (float) (tip[1] - c.y) + up, pz = (float) (tip[2] - c.z);
                // tail to broadest point, then broadest point to the tip (the last two vertices meet)
                vertex(v, e, tx - ot[0], ty - ot[1], tz - ot[2], cb, a);
                vertex(v, e, tx + ot[0], ty + ot[1], tz + ot[2], cb, a);
                vertex(v, e, mx + om[0], my + om[1], mz + om[2], cm, a);
                vertex(v, e, mx - om[0], my - om[1], mz - om[2], cm, a);
                vertex(v, e, mx - om[0], my - om[1], mz - om[2], cm, a);
                vertex(v, e, mx + om[0], my + om[1], mz + om[2], cm, a);
                vertex(v, e, px, py, pz, cf, a);
                vertex(v, e, px, py, pz, cf, a);
            }
        }
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose e, float x, float y, float z, int rgb, int a) {
        v.addVertex(e, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, a);
    }

    /** The colour {@code k} of the way from {@code a} to {@code b}. */
    static int mix(int a, int b, float k) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * k);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return r << 16 | g << 8 | bl;
    }

    /**
     * The half-width offset across the track at arc length {@code s} (point {@code p}): level and
     * to the left of the way with {@code flat} (with the vertex order used the face looks up),
     * else square to the line of sight.
     */
    private static float[] side(Track t, double s, double[] p, Vec3 c, boolean flat, float half) {
        double[] a = at(t, s - 0.15);
        double[] b = at(t, s + 0.15);
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double ox, oy, oz;
        if (flat && dx * dx + dz * dz > 1e-6) {
            ox = -dz;
            oy = 0;
            oz = dx;
        } else {
            // (b - a) × (camera - point)
            double cx = c.x - p[0], cy = c.y - p[1], cz = c.z - p[2];
            ox = dy * cz - dz * cy;
            oy = dz * cx - dx * cz;
            oz = dx * cy - dy * cx;
        }
        double len = Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (len < 1e-9) {
            return new float[] {0, 0, 0};
        }
        return new float[] {(float) (ox / len * half), (float) (oy / len * half), (float) (oz / len * half)};
    }

    /** A soft glowing disc on the ground at the end of the path, brightest in the middle. */
    static void disc(PoseStack pose, VertexConsumer v, Vec3 c, double[] e, float glow) {
        float cx = (float) (e[0] - c.x);
        float cy = (float) (e[1] - GuideLine.LIFT + RIBBON_LIFT - c.y);
        float cz = (float) (e[2] - c.z);
        int r = BODY >> 16 & 0xFF;
        int g = BODY >> 8 & 0xFF;
        int b = BODY & 0xFF;
        int mid = Math.round(255 * Math.min(1, 0.45f * glow));
        var p = pose.last();
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n;
            double a1 = 2 * Math.PI * (i + 1) / n;
            // centre, next rim point, this rim point, centre: the face looks up
            v.addVertex(p, cx, cy, cz).setColor(r, g, b, mid);
            v.addVertex(p, cx + (float) (DISC * Math.cos(a1)), cy, cz + (float) (DISC * Math.sin(a1))).setColor(r, g, b, 0);
            v.addVertex(p, cx + (float) (DISC * Math.cos(a0)), cy, cz + (float) (DISC * Math.sin(a0))).setColor(r, g, b, 0);
            v.addVertex(p, cx, cy, cz).setColor(r, g, b, mid);
        }
    }

    /**
     * A short faint arrow from over the last reachable spot {@code e} toward the target {@code to}
     * (the target cannot be walked to from here): a shaft and two barbs, turned to the camera.
     */
    static void arrow(PoseStack pose, VertexConsumer v, Vec3 c, double[] e, double[] to) {
        double ax = e[0], ay = e[1] + ARROW_UP, az = e[2];
        double dx = to[0] - ax, dy = to[1] - ay, dz = to[2] - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return;
        }
        double k = Math.min(ARROW, len) / len;
        double bx = ax + dx * k, by = ay + dy * k, bz = az + dz * k;
        // across the arrow, square to the line of sight at its tip
        double vx = c.x - bx, vy = c.y - by, vz = c.z - bz;
        double ox = dy * vz - dz * vy, oy = dz * vx - dx * vz, oz = dx * vy - dy * vx;
        double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
        float a = 0.55f * TAIL_ALPHA * 2;
        strip(pose, v, (float) (ax - c.x), (float) (ay - c.y), (float) (az - c.z),
                (float) (bx - c.x), (float) (by - c.y), (float) (bz - c.z), 0.06f, 0.06f, a, a, false);
        if (ol < 1e-9) {
            return;
        }
        double back = 0.7 / len;
        for (int side = -1; side <= 1; side += 2) {
            double hx = bx - dx * back + ox / ol * 0.45 * side;
            double hy = by - dy * back + oy / ol * 0.45 * side;
            double hz = bz - dz * back + oz / ol * 0.45 * side;
            strip(pose, v, (float) (hx - c.x), (float) (hy - c.y), (float) (hz - c.z),
                    (float) (bx - c.x), (float) (by - c.y), (float) (bz - c.z), 0.06f, 0.06f, a, a, false);
        }
    }

    /** The faint still dashes of the straight guess past the last reachable spot. */
    static void plain(PoseStack pose, VertexConsumer v, Vec3 c, List<double[]> pts, float alpha) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) {
            total += dist(pts.get(i - 1), pts.get(i));
        }
        double along = 0;
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            double len = dist(a, b);
            if (len < 1e-6) {
                continue;
            }
            int pieces = Math.max(1, (int) Math.ceil(len / 0.3));
            for (int s = 0; s < pieces; s++) {
                double s0 = (double) s / pieces;
                double s1 = (double) (s + 1) / pieces;
                if (dashAt(total - along - len * (s0 + s1) / 2, 0) < 0) {
                    continue;
                }
                float ax = (float) (a[0] + (b[0] - a[0]) * s0 - c.x);
                float ay = (float) (a[1] + (b[1] - a[1]) * s0 - c.y);
                float az = (float) (a[2] + (b[2] - a[2]) * s0 - c.z);
                float bx = (float) (a[0] + (b[0] - a[0]) * s1 - c.x);
                float by = (float) (a[1] + (b[1] - a[1]) * s1 - c.y);
                float bz = (float) (a[2] + (b[2] - a[2]) * s1 - c.z);
                strip(pose, v, ax, ay, az, bx, by, bz, 0.1f, 0.1f, 0.55f * alpha, 0.55f * alpha, true);
            }
            along += len;
        }
    }

    /**
     * One piece from a to b (relative to the camera), {@code halfA} / {@code halfB} wide each side:
     * lying flat with {@code flat} (facing up), else (or going straight up a ladder) square to the
     * line of sight.
     */
    private static void strip(PoseStack pose, VertexConsumer v, float ax, float ay, float az,
                              float bx, float by, float bz, float halfA, float halfB,
                              float alphaA, float alphaB, boolean flat) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float ox, oy, oz;
        if (flat && dx * dx + dz * dz > 1e-4f) {
            // left of the way, level: (-dz, 0, dx); with the order below the face looks up
            ox = -dz;
            oy = 0;
            oz = dx;
        } else {
            // (b - a) × (camera - midpoint): across the piece, square to the line of sight
            float cx = -(ax + bx) / 2, cy = -(ay + by) / 2, cz = -(az + bz) / 2;
            ox = dy * cz - dz * cy;
            oy = dz * cx - dx * cz;
            oz = dx * cy - dy * cx;
        }
        float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (len < 1e-6f) {
            return;
        }
        ox /= len;
        oy /= len;
        oz /= len;
        int r = BODY >> 16 & 0xFF;
        int g = BODY >> 8 & 0xFF;
        int b = BODY & 0xFF;
        int a1 = Math.round(255 * Math.max(0, Math.min(1, alphaA)));
        int a2 = Math.round(255 * Math.max(0, Math.min(1, alphaB)));
        if (a1 == 0 && a2 == 0) {
            return;
        }
        var p = pose.last();
        v.addVertex(p, ax - ox * halfA, ay - oy * halfA, az - oz * halfA).setColor(r, g, b, a1);
        v.addVertex(p, ax + ox * halfA, ay + oy * halfA, az + oz * halfA).setColor(r, g, b, a1);
        v.addVertex(p, bx + ox * halfB, by + oy * halfB, bz + oz * halfB).setColor(r, g, b, a2);
        v.addVertex(p, bx - ox * halfB, by - oy * halfB, bz - oz * halfB).setColor(r, g, b, a2);
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
    }

    private static double sq(double v) {
        return v * v;
    }
}
