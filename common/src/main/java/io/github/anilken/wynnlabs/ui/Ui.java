package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The mod's small components, one look and one set of rules everywhere: a label is centred in its
 * box on its role's capitals (from the font's real metrics, not its line height), an icon on the
 * capitals of the text beside it, and sizes and padding come from Theme's SP_* steps.
 *
 * <pre>
 *   Component  Height  Padding x  Min width  Label         States
 *   button     16      SP_4       48         LABEL          gold; lighter on hover; pressed sits 1px lower
 *   small      12      SP_3       fixed      BODY           the "Go ›" button: one width for a column
 *   chip       14      SP_4       24         BODY           picked gold, else a thin outline; hover lighter
 *   seal       11      SP_2       —          META           a coloured pill, its text light
 *   icon+text  —       gap SP_1   —          any            the icon centred on the text's capitals
 *   iconChip   14      SP_4       24         BODY           a chip with an icon (and a menu's chevron)
 *   iconButton 14      —          16 square  —              an icon alone, as a chip
 *   tabBar     19      SP_4       24         BODY           primary tabs (icon + label), picked gold; gap SP_2;
 *                                                           SP_2 padding, then a full-width hairline as its last row;
 *                                                           too narrow: the tabs not picked keep their icon only
 *   filterRow  12      SP_3       —          BODY           secondary chips in groups, one picked per group; gap SP_2,
 *                                                           SP_4 with a short hairline between groups; no divider;
 *                                                           too narrow: never cut, a › (and ‹) scrolls the row
 *   notice     lines   SP_3       —          BODY           info / warning / error: a light wash, a one-pixel edge in
 *                                                           the level's colour, the icon on the capitals; height
 *                                                           2·SP_3 + lines × BODY's line height; an optional action
 *                                                           as a small gold button (12) inside its right end
 * </pre>
 *
 * Padding is the component's, margin the parent's: a component returns the height it used and
 * the caller adds the gap to what follows (after a tab bar SP_3 before a filter row, SP_4 before
 * content; after a filter row or a notice SP_4 before what follows).
 *
 * Every method draws only; the caller keeps its click areas (they differ per screen).
 */
final class Ui {

    static final int BUTTON_H = 16;
    static final int SMALL_H = 12;
    static final int CHIP_H = 14;
    static final int SEAL_H = 11;
    static final int BUTTON_MIN = 48;
    static final int CHIP_MIN = 24;

    /** Noto Sans's capital height per em. */
    static final float CAP = 0.714f;
    /** The game's text baseline below the y it is drawn at (7 for every font, TTF ones moved down by their shift). */
    static final float BASELINE = 7f;

    private Ui() {}

    // ---- the centring math (pure, tested) ----

    /** Where a text line's capitals start below the y it is drawn at. */
    static float capTop(float px, int shift, boolean pixelFont) {
        return pixelFont ? 0 : BASELINE + shift - CAP * px;
    }

    /** The capitals' height. */
    static float capHeight(float px, boolean pixelFont) {
        return pixelFont ? 7 : CAP * px;
    }

    /** The y to draw a text at so its capitals sit centred in the box {@code boxY}..{@code boxY + boxH}. */
    static int centreY(int boxY, int boxH, float px, int shift, boolean pixelFont) {
        return Math.round(boxY + boxH / 2f - capTop(px, shift, pixelFont) - capHeight(px, pixelFont) / 2f);
    }

    /** The top of a {@code h} tall box (an icon, a button) centred on the capitals of a line drawn at {@code lineY}. */
    static int onCaps(int lineY, int h, float px, int shift, boolean pixelFont) {
        return Math.round(lineY + capTop(px, shift, pixelFont) + capHeight(px, pixelFont) / 2f - h / 2f);
    }

    // ---- components ----

    static int buttonWidth(Font font, String label) {
        return Math.max(BUTTON_MIN, Type.LABEL.width(font, label) + 2 * Theme.SP_4);
    }

    static int chipWidth(Font font, String label) {
        return Math.max(CHIP_MIN, Type.BODY.width(font, label) + 2 * Theme.SP_4);
    }

    /** A button: gold, lighter on hover, its label 1px lower while pressed. Returns whether the mouse is on it. */
    static boolean button(GuiGraphics g, Font font, String label, int x, int y, int w, int mx, int my) {
        return pill(g, font, Type.LABEL, label, x, y, w, BUTTON_H, true, mx, my, Theme.INK);
    }

    /** The small button of a column ("Go ›"), {@code w} wide for every row. */
    static boolean small(GuiGraphics g, Font font, String label, int x, int y, int w, int mx, int my) {
        return pill(g, font, Type.BODY, label, x, y, w, SMALL_H, true, mx, my, Theme.INK);
    }

    /** A chip: gold when picked, a thin outline otherwise; {@code twin} underlines it, {@code dot} marks it. */
    static boolean chip(GuiGraphics g, Font font, String label, int x, int y, int w, boolean on, boolean twin, boolean dot,
                        int mx, int my) {
        int textW = w - (dot ? Theme.SP_3 : 0);
        boolean hover = pill(g, font, Type.BODY, label, x, y, textW, CHIP_H, on, mx, my, on ? Theme.INK : Theme.INK_2, w);
        if (dot) {
            int dy = y + CHIP_H / 2 - 1;
            g.fill(x + w - Theme.SP_4 - 3, dy, x + w - Theme.SP_4, dy + 3, Theme.GOLD_DEEP);
        }
        if (twin) {
            Kit.hair(g, x + Theme.SP_4, y + CHIP_H - Theme.SP_1, w - 2 * Theme.SP_4, 1, Theme.GOLD_DEEP);
        }
        return hover;
    }

    /** A seal: a pill in {@code colour}, its text in META, light. Returns its width. */
    static int seal(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        int w = Type.META.width(font, text) + 2 * Theme.SP_2;
        Kit.seal(g, x, y, w, SEAL_H, colour);
        Type.META.draw(g, font, text, x + Theme.SP_2, Type.META.yIn(y, SEAL_H), Theme.SEAL_TEXT);
        return w;
    }

    /** An icon then a text, the icon centred on the text's capitals, SP_1 apart; returns the x after the text. */
    static int iconText(GuiGraphics g, Font font, Type role, String icon, int iw, int ih, String text, int x, int y, int iconInk, int ink) {
        Kit.icon(g, icon, x, role.onCaps(y, ih), iw, ih, iconInk);
        int tx = x + iw + Theme.SP_1;
        role.draw(g, font, text, tx, y, ink);
        return tx + role.width(font, text);
    }

    /** An 8px icon in front of a label and, optionally, a chevron after it: the width of {@link #iconChip}. */
    static int iconChipWidth(Font font, String icon, String label, boolean chevron) {
        int w = 2 * Theme.SP_4 + (label == null || label.isEmpty() ? 0 : Type.BODY.width(font, label));
        if (icon != null) {
            w += 8 + (label == null || label.isEmpty() ? 0 : Theme.SP_1);
        }
        if (chevron) {
            w += Theme.SP_1 + 6;
        }
        return Math.max(label == null || label.isEmpty() ? CHIP_H + 2 : CHIP_MIN, w);
    }

    /**
     * A chip with an icon before its label (and a chevron after it for a menu): gold when on, a thin
     * outline otherwise; the icon centred on the label's capitals. Returns whether the mouse is on it.
     */
    static boolean iconChip(GuiGraphics g, Font font, String icon, String label, boolean chevron, int x, int y, int w, boolean on,
                            int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + CHIP_H;
        Kit.pill(g, x, y, w, CHIP_H, on, hover);
        iconChipFace(g, font, icon, label, chevron, x, y, w, on ? Theme.INK : Theme.INK_2);
        return hover;
    }

    /** The primary action as an icon chip: purple, its icon and label cream ("+ 3" for the chosen items, Save). */
    static boolean primaryIconChip(GuiGraphics g, Font font, String icon, String label, int x, int y, int w, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + CHIP_H;
        Kit.pillPrimary(g, x, y, w, CHIP_H, hover);
        iconChipFace(g, font, icon, label, false, x, y, w, Theme.CREAM);
        return hover;
    }

    private static void iconChipFace(GuiGraphics g, Font font, String icon, String label, boolean chevron, int x, int y, int w,
                                     int ink) {
        int ty = Type.BODY.yIn(y, CHIP_H);
        boolean text = label != null && !label.isEmpty();
        int inner = (icon == null ? 0 : 8) + (text ? (icon == null ? 0 : Theme.SP_1) + Type.BODY.width(font, label) : 0)
                + (chevron ? Theme.SP_1 + 6 : 0);
        int cx = x + (w - inner) / 2;
        if (icon != null) {
            Art.icon(g, icon, cx, Type.BODY.onCaps(ty, 8), 8, ink);
            cx += 8 + (text ? Theme.SP_1 : 0);
        }
        if (text) {
            Type.BODY.draw(g, font, label, cx, ty, ink);
            cx += Type.BODY.width(font, label);
        }
        if (chevron) {
            Art.icon(g, "chevron-down", cx + Theme.SP_1, Type.BODY.onCaps(ty, 6), 6, Theme.INK_3);
        }
    }

    /** Where an icon {@code size} long starts in a box {@code box} long: centred, both even so it lands on a whole pixel. */
    static int iconIn(int box, int size) {
        return (box - size) / 2;
    }

    /** A square icon button the height of a chip (16×14, an 8 px icon): gold when on, a thin outline otherwise. */
    static boolean iconButton(GuiGraphics g, String icon, int x, int y, boolean on, int mx, int my) {
        int w = CHIP_H + 2;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + CHIP_H;
        Kit.pill(g, x, y, w, CHIP_H, on, hover);
        Art.icon(g, icon, x + iconIn(w, 8), y + iconIn(CHIP_H, 8), 8, on ? Theme.INK : Theme.INK_2);
        return hover;
    }

    /** A button with an icon before its label (the compass on "Compass ›"). */
    static boolean iconButtonLabel(GuiGraphics g, Font font, String icon, String label, int x, int y, int w, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + BUTTON_H;
        boolean pressed = hover && Minecraft.getInstance().mouseHandler.isLeftPressed();
        Kit.pill(g, x, y, w, BUTTON_H, true, hover && !pressed);
        int ty = Type.LABEL.yIn(y, BUTTON_H) + (pressed ? 1 : 0);
        int inner = 8 + Theme.SP_1 + Type.LABEL.width(font, label);
        int cx = x + (w - inner) / 2;
        Art.icon(g, icon, cx, Type.LABEL.onCaps(ty, 8), 8, Theme.INK);
        Type.LABEL.draw(g, font, label, cx + 8 + Theme.SP_1, ty, Theme.INK);
        return hover;
    }

    static int iconButtonLabelWidth(Font font, String label) {
        return Math.max(BUTTON_MIN, 8 + Theme.SP_1 + Type.LABEL.width(font, label) + 2 * Theme.SP_4);
    }

    // ---- notices ----

    /** A notice's level: its wash, its hairline edge, its ink and its icon. */
    enum Level {
        INFO(0xFFE2E6DA, 0xFF2F6FB0, Theme.INK, 0xFF2F6FB0, "info-circle"),
        WARNING(0xFFF1DDB0, Theme.WARN, Theme.WARN, Theme.WARN, "alert-triangle"),
        ERROR(0xFFF2D2C8, Theme.BAD, Theme.BAD, Theme.BAD, "alert-triangle");

        final int fill;
        final int edge;
        final int ink;
        final int iconInk;
        final String icon;

        Level(int fill, int edge, int ink, int iconInk, String icon) {
            this.fill = fill;
            this.edge = edge;
            this.ink = ink;
            this.iconInk = iconInk;
            this.icon = icon;
        }
    }

    /** A notice's action: a small gold button at its right end (an icon, and a label when it fits). */
    record Action(String icon, String label) {}

    /** A notice's padding on every side. */
    static final int NOTICE_PAD = Theme.SP_3;

    /** A notice's height for {@code lines} lines of text: its padding and the BODY line height. */
    /** A running text ("Calculating...") with its dots coming and going by the clock; as it is in the previews. */
    static String busy(String text) {
        if (!text.endsWith("…") || MountsModel.previewView != null || MountsModel.previewRoad != null) {
            return text;
        }
        int n = (int) (System.currentTimeMillis() / 350 % 4);
        return text.substring(0, text.length() - 1) + ".".repeat(n);
    }

    static int noticeHeight(int lines) {
        return 2 * NOTICE_PAD + Math.max(1, lines) * Type.BODY.lineHeight;
    }

    /** An action button's width: the icon and its label with SP_3 each side, or the icon alone. */
    static int noticeActionWidth(Font font, Action a, boolean named) {
        return named ? 2 * Theme.SP_3 + 8 + Theme.SP_1 + Type.BODY.width(font, a.label()) : SMALL_H + 2;
    }

    /** Whether a notice {@code w} wide shows its action's label (the text keeps at least half the row). */
    static boolean noticeNamed(Font font, Action a, int w) {
        return a != null && a.label() != null && noticeActionWidth(font, a, true) <= (w - 2 * NOTICE_PAD) / 3;
    }

    /** The width a notice leaves its text: inside the padding, after the icon, before the action. */
    static int noticeTextRoom(Font font, Action a, int w) {
        int room = w - 2 * NOTICE_PAD - 9 - Theme.SP_2;
        if (a != null) {
            room -= noticeActionWidth(font, a, noticeNamed(font, a, w)) + Theme.SP_3;
        }
        return room;
    }

    /**
     * A notice: a light wash of its level with a one-pixel edge in the level's colour, SP_3 of
     * padding, the level's icon on each message's first line (centred on its capitals) and the text
     * wrapped in BODY; an optional action as a small gold button at the right end, always inside.
     * At most {@code maxH} tall (whole lines). Puts the action's box in {@code hit[0]} (null without
     * one) and returns its height; the gap after it is the caller's (SP_4).
     */
    static int notice(GuiGraphics g, Font font, Level level, java.util.List<String> messages, Action action, int x, int y, int w,
                      int maxH, int mx, int my, int[][] hit) {
        int room = noticeTextRoom(font, action, w);
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        int lines = 0;
        for (String m : messages) {
            int n = Type.BODY.wrap(font, m, room).size();
            counts.add(n);
            lines += n;
        }
        int fit = Math.max(1, (maxH - 2 * NOTICE_PAD) / Type.BODY.lineHeight);
        int h = noticeHeight(Math.min(lines, fit));
        g.fill(x, y, x + w, y + h, level.fill);
        Kit.hair(g, x, y, w, 1, level.edge);
        Kit.hair(g, x, y + h - 1, w, 1, level.edge);
        Kit.hair(g, x, y, 1, h, level.edge);
        Kit.hair(g, x + w - 1, y, 1, h, level.edge);
        int tx = x + NOTICE_PAD + 9 + Theme.SP_2;
        int ly = y + NOTICE_PAD;
        int left = Math.min(lines, fit);
        for (int i = 0; i < messages.size() && left > 0; i++) {
            Art.icon(g, level.icon, x + NOTICE_PAD, Type.BODY.onCaps(ly, 9), 9, level.iconInk);
            int n = Math.min(counts.get(i), left);
            ly = Type.BODY.drawWrapped(g, font, messages.get(i), tx, ly, room, n, level.ink);
            left -= n;
        }
        hit[0] = null;
        if (action != null) {
            boolean named = noticeNamed(font, action, w);
            int aw = noticeActionWidth(font, action, named);
            int ax = x + w - NOTICE_PAD - aw;
            // on the first line: its capitals and the button's middle level
            int ay = Type.BODY.onCaps(y + NOTICE_PAD, SMALL_H);
            boolean hover = mx >= ax && mx < ax + aw && my >= ay && my < ay + SMALL_H;
            Kit.pill(g, ax, ay, aw, SMALL_H, true, hover);
            int ty = Type.BODY.yIn(ay, SMALL_H);
            if (named) {
                Art.icon(g, action.icon(), ax + Theme.SP_3, Type.BODY.onCaps(ty, 8), 8, Theme.INK);
                Type.BODY.draw(g, font, action.label(), ax + Theme.SP_3 + 8 + Theme.SP_1, ty, Theme.INK);
            } else {
                Art.icon(g, action.icon(), ax + (aw - 8) / 2, ay + (SMALL_H - 8) / 2, 8, Theme.INK);
            }
            hit[0] = new int[] {ax, ay, aw, SMALL_H};
        }
        return h;
    }

    // ---- tab bars and filter rows ----

    /** A tab of a tab bar, or a chip of a filter row: an optional 8px icon (null for none) and its label. */
    record Tab(String icon, String label) {}

    /** A tab bar's height: its pills, SP_2 of padding under them, then its divider as its last pixel row. */
    static final int TAB_BAR_H = CHIP_H + Theme.SP_2 + 1;
    /** A filter row's height: its small chips; no divider. */
    static final int FILTER_H = SMALL_H;
    /** Between two tabs or two chips of a group. */
    static final int TAB_GAP = Theme.SP_2;
    /** Between two groups of a filter row, a short vertical hairline in its middle. */
    static final int GROUP_GAP = Theme.SP_4;
    /** The ‹ and › of a filter row that does not fit. */
    static final int ARROW_W = SMALL_H + 2;

    /**
     * A primary tab bar: a pill per tab (gold when picked), the divider under them as the bar's last
     * row, full width. Too narrow for every label, the tabs not picked keep their icon only (their
     * name then in the caller's tooltip). Fills {@code hits[i]} with each tab's box and returns
     * {@link #TAB_BAR_H}; the space after it is the caller's (SP_3 before a filter row, SP_4 before content).
     */
    static int tabBar(GuiGraphics g, Font font, int x, int y, int w, java.util.List<Tab> tabs, int picked, int mx, int my,
                      int[][] hits) {
        int n = tabs.size();
        int[] full = new int[n];
        int sum = TAB_GAP * Math.max(0, n - 1);
        for (int i = 0; i < n; i++) {
            full[i] = iconChipWidth(font, tabs.get(i).icon(), tabs.get(i).label(), false);
            sum += full[i];
        }
        boolean folded = sum > w;
        int tx = x;
        for (int i = 0; i < n; i++) {
            Tab t = tabs.get(i);
            boolean on = i == picked;
            boolean named = !folded || on || t.icon() == null;
            int tw = named ? full[i] : CHIP_H + 2;
            iconChip(g, font, t.icon(), named ? t.label() : null, false, tx, y, tw, on, mx, my);
            hits[i] = new int[] {tx, y, tw, CHIP_H};
            tx += tw + TAB_GAP;
        }
        Kit.hair(g, x, y + TAB_BAR_H - 1, w, 1, Theme.LINE);
        return TAB_BAR_H;
    }

    /** A filter chip's width: its label (and icon) and SP_3 each side. */
    static int filterChipWidth(Font font, Tab t) {
        return Type.BODY.width(font, t.label()) + 2 * Theme.SP_3 + (t.icon() == null ? 0 : 8 + Theme.SP_1);
    }

    /**
     * A secondary filter row: small chips in groups (one picked per group, gold), {@link #GROUP_GAP}
     * and a short hairline between groups, no divider. Chips that do not fit are never cut: the row
     * starts at chip {@code first} and ends with a › when more follow (a ‹ in front when it does not
     * start at the first). {@code hits} gets each chip's box (null when hidden), then the ‹ and the ›
     * (null when not shown); the caller moves {@code first} on their clicks. Returns {@link #FILTER_H}.
     */
    static int filterRow(GuiGraphics g, Font font, int x, int y, int w, java.util.List<java.util.List<Tab>> groups, int[] picked,
                         int first, int mx, int my, int[][] hits) {
        java.util.List<Tab> all = new java.util.ArrayList<>();
        java.util.List<Integer> groupOf = new java.util.ArrayList<>();
        java.util.List<Boolean> on = new java.util.ArrayList<>();
        for (int gi = 0; gi < groups.size(); gi++) {
            for (int i = 0; i < groups.get(gi).size(); i++) {
                all.add(groups.get(gi).get(i));
                groupOf.add(gi);
                on.add(picked[gi] == i);
            }
        }
        int n = all.size();
        int[] widths = new int[n];
        int[] group = new int[n];
        for (int i = 0; i < n; i++) {
            widths[i] = filterChipWidth(font, all.get(i));
            group[i] = groupOf.get(i);
        }
        int[] at = filterLayout(widths, group, w, first);
        int ty = Type.BODY.yIn(y, FILTER_H);
        for (int i = 0; i < n; i++) {
            if (at[i] < 0) {
                hits[i] = null;
                continue;
            }
            int cx = x + at[i];
            boolean lit = on.get(i);
            boolean hover = mx >= cx && mx < cx + widths[i] && my >= y && my < y + FILTER_H;
            Kit.pill(g, cx, y, widths[i], FILTER_H, lit, hover);
            int ink = lit ? Theme.INK : Theme.INK_2;
            int lx = cx + Theme.SP_3;
            Tab t = all.get(i);
            if (t.icon() != null) {
                Art.icon(g, t.icon(), lx, Type.BODY.onCaps(ty, 8), 8, ink);
                lx += 8 + Theme.SP_1;
            }
            Type.BODY.draw(g, font, t.label(), lx, ty, ink);
            hits[i] = new int[] {cx, y, widths[i], FILTER_H};
            // a group's start after another's: a short hairline in the middle of the gap
            if (i > 0 && at[i - 1] >= 0 && group[i] != group[i - 1]) {
                Kit.hair(g, cx - GROUP_GAP / 2, y + Theme.SP_1, 1, FILTER_H - 2 * Theme.SP_1, Theme.LINE);
            }
        }
        hits[n] = arrow(g, "chevron-left", at[n], x, y, mx, my);
        hits[n + 1] = arrow(g, "chevron-right", at[n + 1], x, y, mx, my);
        return FILTER_H;
    }

    private static int[] arrow(GuiGraphics g, String icon, int at, int x, int y, int mx, int my) {
        if (at < 0) {
            return null;
        }
        int ax = x + at;
        boolean hover = mx >= ax && mx < ax + ARROW_W && my >= y && my < y + FILTER_H;
        Kit.pill(g, ax, y, ARROW_W, FILTER_H, false, hover);
        Art.icon(g, icon, ax + (ARROW_W - 6) / 2, y + (FILTER_H - 6) / 2, 6, Theme.INK_2);
        return new int[] {ax, y, ARROW_W, FILTER_H};
    }

    /**
     * A filter row's layout (pure, tested): each chip's x from the row's left, or -1 when it is not
     * shown, then the ‹'s and the ›'s x (-1 for none). Chips go from {@code first} (0 when they all
     * fit) and never past {@code w}; a › takes the row's end when more chips follow.
     */
    static int[] filterLayout(int[] widths, int[] group, int w, int first) {
        int n = widths.length;
        int[] at = new int[n + 2];
        java.util.Arrays.fill(at, -1);
        if (n == 0) {
            return at;
        }
        if (span(widths, group, 0, n) <= w) {
            first = 0;
        }
        first = Math.max(0, Math.min(first, n - 1));
        int start = 0;
        if (first > 0) {
            at[n] = 0;
            start = ARROW_W + TAB_GAP;
        }
        boolean rest = start + span(widths, group, first, n) <= w;
        int limit = rest ? w : w - TAB_GAP - ARROW_W;
        int cx = start;
        int last = first;
        for (int i = first; i < n; i++) {
            int gap = i == first ? 0 : group[i] != group[i - 1] ? GROUP_GAP : TAB_GAP;
            if (cx + gap + widths[i] > limit && i > first) {
                break;
            }
            at[i] = cx + gap;
            cx = at[i] + widths[i];
            last = i;
        }
        if (last < n - 1) {
            at[n + 1] = w - ARROW_W;
        }
        return at;
    }

    /** The width of chips {@code from} to {@code to} (exclusive) with their gaps. */
    static int span(int[] widths, int[] group, int from, int to) {
        int s = 0;
        for (int i = from; i < to; i++) {
            s += widths[i] + (i == from ? 0 : group[i] != group[i - 1] ? GROUP_GAP : TAB_GAP);
        }
        return s;
    }

    private static boolean pill(GuiGraphics g, Font font, Type role, String label, int x, int y, int w, int h, boolean on,
                                int mx, int my, int ink) {
        return pill(g, font, role, label, x, y, w, h, on, mx, my, ink, w);
    }

    /** A pill {@code boxW} wide with its label centred in its first {@code w} px. */
    private static boolean pill(GuiGraphics g, Font font, Type role, String label, int x, int y, int w, int h, boolean on,
                                int mx, int my, int ink, int boxW) {
        boolean hover = mx >= x && mx < x + boxW && my >= y && my < y + h;
        boolean pressed = hover && on && Minecraft.getInstance().mouseHandler.isLeftPressed();
        Kit.pill(g, x, y, boxW, h, on, hover && !pressed);
        int tw = role.width(font, label);
        role.draw(g, font, label, x + (w - tw) / 2, role.yIn(y, h) + (pressed ? 1 : 0), ink);
        return hover;
    }
}
