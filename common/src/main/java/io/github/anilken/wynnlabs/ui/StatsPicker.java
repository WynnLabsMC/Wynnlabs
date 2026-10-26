package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The stats card's header controls: a small wood button naming what the list compares with (a
 * click opens the list of builds to pick from, {@link StatsSource#options}) and an icon toggle
 * between your numbers with the other build's change ("Now") and the other build's numbers with
 * the change from your gear ("New build"). The spell panel follows both.
 */
final class StatsPicker extends HomePart {

    private static final int H = 12;
    private static final int ROW_H = 12;

    private final StatsSource source;
    /** The list is open; it closed on this press (so the button's own click does not open it again). */
    private boolean open;
    private boolean closedNow;
    /** The button's box this frame, for the list under it. */
    private int[] at;

    StatsPicker(StatsSource source) {
        this.source = source;
    }

    boolean open() {
        return open;
    }

    /** A press anywhere: an open list closes (a row's own click still picks). */
    void press() {
        closedNow = open;
        open = false;
    }

    /** The button's click: the list opens, unless this press just closed it. */
    void toggle() {
        open = !closedNow;
        closedNow = false;
    }

    /**
     * The toggle at {@code right} and the button left of it (no further left than {@code left}),
     * at {@code y}.
     */
    /**
     * The header's controls ending at {@code right}, none left of {@code left}: the view toggle as an
     * icon button, and the compare menu as a chip with its chevron, or (no room for its name) an
     * icon button whose tooltip names it. Centred on a chip's height from {@code y}.
     */
    void draw(GuiGraphics g, HomeTab.Host host, StatsSource.Shown shown, int right, int y, int left, int mx, int my) {
        frame(UiFont.get(), large);
        boolean none = shown.other() == null;
        int bw = Ui.CHIP_H + 2;
        int tx = right - bw;
        Ui.iconButton(g, "arrows-horizontal", tx, y, source.flipped() && !none, mx, my);
        host.area(tx, y, bw, Ui.CHIP_H, null, List.of(
                L.c(source.flipped() ? "wynnlabs.stats.view_new_title" : "wynnlabs.stats.view_now_title").withStyle(ChatFormatting.WHITE),
                L.c(none ? "wynnlabs.stats.view_none_tip" : "wynnlabs.stats.view_tip").withStyle(ChatFormatting.GRAY)), "#qstatsflip:");
        String label = none ? L.t("wynnlabs.stats.src_none")
                : shown.flipped() ? L.t("wynnlabs.stats.view_new", shown.name()) : L.t("wynnlabs.stats.vs", shown.name());
        int room = tx - Theme.SP_2 - left;
        String shownLabel = !none && Ui.iconChipWidth(font, null, label, true) > room ? shown.name() : label;
        int cw = Ui.iconChipWidth(font, null, shownLabel, true);
        int bx;
        int w;
        if (cw <= room) {
            w = cw;
            bx = tx - Theme.SP_2 - w;
            Ui.iconChip(g, font, null, shownLabel, true, bx, y, w, open, mx, my);
        } else {
            // a narrow column: the menu as an icon, its name in the tooltip
            w = bw;
            bx = tx - Theme.SP_2 - w;
            Ui.iconButton(g, "scale", bx, y, open, mx, my);
        }
        host.area(bx, y, w, Ui.CHIP_H, null, open ? List.of() : List.of(Component.literal(label).withStyle(ChatFormatting.LIGHT_PURPLE),
                L.c("wynnlabs.stats.src_tip").withStyle(ChatFormatting.GRAY)), "#qstatssrc:");
        at = new int[] {Math.min(bx, right - 120), y + Ui.CHIP_H, right - Math.min(bx, right - 120)};
    }

    /** The left edge the controls took last frame (for the heading's room). */
    int left() {
        return at == null ? Integer.MAX_VALUE : at[0];
    }

    /** The open list under the button, over the card; drawn last. */
    void drawList(GuiGraphics g, HomeTab.Host host, int mx, int my) {
        if (!open || at == null) {
            return;
        }
        List<String[]> opts = source.options();
        int w = at[2];
        for (String[] o : opts) {
            w = Math.max(w, width(o[1], false) + 18);
        }
        int x = at[0] + at[2] - w;
        int y = at[1] + 1;
        int h = opts.size() * ROW_H + 4;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.LINE);
        g.fill(x, y, x + w, y + h, Theme.CARD);
        String chosen = source.choice();
        for (int i = 0; i < opts.size(); i++) {
            String[] o = opts.get(i);
            int ry = y + 2 + i * ROW_H;
            boolean hov = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
            boolean on = o[0].equals(chosen);
            Kit.row(g, x + 1, ry, w - 2, ROW_H, i, hov || on);
            if (on) {
                Art.icon(g, "check", x + 4, ry + 2, 8, Theme.GOOD);
            }
            ink(g, fit(o[1], w - 18), x + 14, ry + 2, Theme.INK);
            host.area(x, ry, w, ROW_H, null, null, "#qstatsset:" + o[0]);
        }
    }
}
