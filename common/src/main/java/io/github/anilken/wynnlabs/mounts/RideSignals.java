package io.github.anilken.wynnlabs.mounts;

import java.util.List;
import java.util.Objects;

/**
 * What the action bar says about the mount while riding, read from its styled runs (font, text):
 * the main line's mount part (font {@code hud/gameplay/default/bottom_middle}, after its last
 * U+CFFA; it counts up while the mount trains or eats), the "pill" (a font whose name holds {@code pill}, custom digit glyphs) that changes when a stat
 * changes, and the mount energy bar (font {@code hud/gameplay/default/center_left}, one glyph of
 * U+E000..U+E02F per frame). Pure: the game turns a Component into runs.
 */
public final class RideSignals {

    public static final String PILL_FONT = "pill";
    public static final String ENERGY_FONT = "hud/gameplay/default/center_left";
    /** The bar's main line while riding (health, mana, then the mount's part after the last {@link #MARK}). */
    public static final String BOTTOM_FONT = "hud/gameplay/default/bottom_middle";
    static final char MARK = '쿺';
    static final char ENERGY_FIRST = '';
    static final char ENERGY_LAST = '';

    private RideSignals() {}

    /** One styled piece of the bar: its font's id ("minecraft:default", null when none) and its text. */
    public record Run(String font, String text) {}

    /**
     * The bar's mount parts: the pill's raw text ("" when absent), the same text decoded, and the
     * energy frame (0-47, -1 when the bar has none).
     */
    public record Signal(String pillRaw, String pill, int energyFrame, String tail) {

        public static final Signal NONE = new Signal("", "", -1, "");

        public Signal(String pillRaw, String pill, int energyFrame) {
            this(pillRaw, pill, energyFrame, "");
        }

        public boolean empty() {
            return pillRaw.isEmpty() && energyFrame < 0 && tail.isEmpty();
        }

        /** The same without the energy frame (Wynntils reads the pool live). */
        public Signal withoutEnergy() {
            return new Signal(pillRaw, pill, -1, tail);
        }
    }

    /** The bar's runs read for the pill and the energy glyph. */
    public static Signal read(List<Run> runs) {
        StringBuilder pill = new StringBuilder();
        StringBuilder bottom = new StringBuilder();
        int frame = -1;
        for (Run r : runs) {
            if (r == null || r.font() == null || r.text() == null) {
                continue;
            }
            if (r.font().contains(PILL_FONT)) {
                pill.append(r.text());
            } else if (r.font().contains(BOTTOM_FONT)) {
                bottom.append(r.text());
            } else if (r.font().contains(ENERGY_FONT)) {
                for (int i = 0; i < r.text().length(); i++) {
                    char c = r.text().charAt(i);
                    if (c >= ENERGY_FIRST && c <= ENERGY_LAST) {
                        frame = c - ENERGY_FIRST;
                    }
                }
            }
        }
        String raw = pill.toString();
        return new Signal(raw, decodePill(raw), frame, tail(bottom.toString()));
    }

    /**
     * The pill's custom glyphs as text: U+E020..U+E029 the digits, U+E072 ".", U+E000..U+E019 the
     * letters A-Z, plain characters as they are, anything else "?". Taken from another mod's code and
     * not yet seen in game, so nothing relies on it beyond "the text changed".
     */
    public static String decodePill(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '' && c <= '') {
                out.append((char) ('0' + (c - '')));
            } else if (c == '') {
                out.append('.');
            } else if (c >= '' && c <= '') {
                out.append((char) ('A' + (c - '')));
            } else if (c < '' || c > '') {
                out.append(c);
            } else {
                out.append('?');
            }
        }
        return out.toString().trim();
    }

    /** The main line's mount part: what follows its last {@link #MARK}; "" when it has none. */
    static String tail(String bottom) {
        int at = bottom.lastIndexOf(MARK);
        return at < 0 ? "" : bottom.substring(at + 1);
    }

    /**
     * Whether the bar changed in a way that may mean new mount numbers: the pill or the main line's
     * mount part showed up or changed, or the energy frame moved (both read). A part going away is
     * not a change.
     */
    public static boolean changed(Signal before, Signal now) {
        if (now == null) {
            return false;
        }
        Signal b = before == null ? Signal.NONE : before;
        if (!now.pillRaw().isEmpty() && !Objects.equals(b.pillRaw(), now.pillRaw())) {
            return true;
        }
        if (!now.tail().isEmpty() && !Objects.equals(b.tail(), now.tail())) {
            return true;
        }
        return b.energyFrame() >= 0 && now.energyFrame() >= 0 && b.energyFrame() != now.energyFrame();
    }

    /** The raw text with its private-use glyphs as \\uXXXX, for the dev log. */
    public static String escaped(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                out.append(String.format("\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
