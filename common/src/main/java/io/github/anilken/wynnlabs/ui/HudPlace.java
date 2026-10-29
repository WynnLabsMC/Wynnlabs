package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;

/**
 * Where a movable HUD element sits: the screen corner nearest to it and its distance from that
 * corner's two edges, so it stays put in that corner when the window changes size and never
 * draws off screen. Saved per element in settings.json as numbers ({@code hud_<id>_corner},
 * {@code _dx}, {@code _dy}); none saved means the element's own default place. The math is pure
 * for the tests; {@link HudEditScreen} moves the elements.
 */
public final class HudPlace {

    /** How near (GUI px) an edge, the margin or the middle pulls an element onto it. */
    static final int SNAP = 4;
    /** The gap kept from the screen's edges when snapped. */
    static final int MARGIN = Theme.SP_2;

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** A place: a corner and the distance (≥ 0) from its side and its top or bottom. */
    public record Spot(Corner corner, int dx, int dy) {}

    private HudPlace() {}

    /** The element's top-left for a spot on this screen, kept fully on it. */
    static int[] resolve(Spot s, int screenW, int screenH, int w, int h) {
        boolean right = s.corner() == Corner.TOP_RIGHT || s.corner() == Corner.BOTTOM_RIGHT;
        boolean bottom = s.corner() == Corner.BOTTOM_LEFT || s.corner() == Corner.BOTTOM_RIGHT;
        int x = right ? screenW - w - s.dx() : s.dx();
        int y = bottom ? screenH - h - s.dy() : s.dy();
        return clamp(x, y, screenW, screenH, w, h);
    }

    /** A top-left kept on the screen (the top-left wins when the element is larger than the screen). */
    static int[] clamp(int x, int y, int screenW, int screenH, int w, int h) {
        return new int[] {Math.max(0, Math.min(x, screenW - w)), Math.max(0, Math.min(y, screenH - h))};
    }

    /** The spot for an element at x, y: the corner of the quarter its centre is in. */
    static Spot of(int x, int y, int w, int h, int screenW, int screenH) {
        int[] c = clamp(x, y, screenW, screenH, w, h);
        boolean right = c[0] + w / 2 >= screenW / 2;
        boolean bottom = c[1] + h / 2 >= screenH / 2;
        Corner corner = bottom ? right ? Corner.BOTTOM_RIGHT : Corner.BOTTOM_LEFT : right ? Corner.TOP_RIGHT : Corner.TOP_LEFT;
        return new Spot(corner, right ? screenW - w - c[0] : c[0], bottom ? screenH - h - c[1] : c[1]);
    }

    /**
     * One axis snapped: an element at {@code pos}, {@code size} long, on a screen {@code screen}
     * long, pulled to the margin at either end or to the middle when within {@link #SNAP}.
     */
    static int snap(int pos, int size, int screen) {
        int[] targets = {MARGIN, screen - size - MARGIN, (screen - size) / 2, 0, screen - size};
        for (int t : targets) {
            if (Math.abs(pos - t) <= SNAP) {
                return t;
            }
        }
        return pos;
    }

    /** Whether an axis sits on its middle (the edit screen lights that guide). */
    static boolean centred(int pos, int size, int screen) {
        return pos == (screen - size) / 2;
    }

    /** The saved spot of an element; null for its default place. */
    public static Spot load(String id) {
        return fromNumbers(Settings.number(key(id, "corner"), -1), Settings.number(key(id, "dx"), 0),
                Settings.number(key(id, "dy"), 0));
    }

    public static void save(String id, Spot s) {
        int[] n = numbers(s);
        Settings.setNumber(key(id, "corner"), n[0]);
        Settings.setNumber(key(id, "dx"), n[1]);
        Settings.setNumber(key(id, "dy"), n[2]);
    }

    /** A spot as settings.json keeps it: corner, dx, dy. */
    static int[] numbers(Spot s) {
        return new int[] {s.corner().ordinal(), Math.max(0, s.dx()), Math.max(0, s.dy())};
    }

    /** A spot from settings.json's numbers; null (the default place) when the corner is unset or unknown. */
    static Spot fromNumbers(int corner, int dx, int dy) {
        if (corner < 0 || corner >= Corner.values().length) {
            return null;
        }
        return new Spot(Corner.values()[corner], Math.max(0, dx), Math.max(0, dy));
    }

    /** Back to the element's default place. */
    public static void reset(String id) {
        Settings.resetNumber(key(id, "corner"));
        Settings.resetNumber(key(id, "dx"));
        Settings.resetNumber(key(id, "dy"));
    }

    static String key(String id, String part) {
        return "hud_" + id + "_" + part;
    }
}
