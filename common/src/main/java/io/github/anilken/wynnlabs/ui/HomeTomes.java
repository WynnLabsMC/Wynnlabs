package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.TomeAdvice;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.endgame.ItemIcons;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.PoolItem;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tomes that suit the build shown, in one popup over the Gear panel's Tomes chip: slot by
 * slot, each with its picture and what it adds, what the model cannot score at the end. Where it
 * drops and which slot it takes are in its tooltip, and copies of a tome share a row. Nothing here
 * changes the build. A class of its own made on first use (safe to hot swap in).
 */
final class HomeTomes {

    private static final int W = 270;
    private static final int ICON = 12;
    private static final int ROW_H = ICON + Theme.SP_2;
    private static final int GAIN_W = 42;
    private static final int TICK = 10;

    /** A row of the list: {@code n} copies of a tome, adding {@code gain} together. */
    private record Line(TomeAdvice.Row row, int n, double gain) {}

    private boolean open;
    private int[] at;
    private int scroll;
    private final ScrollBar bar = new ScrollBar();

    boolean open() {
        return open;
    }

    void openMenu(int[] chip) {
        at = chip;
        open = true;
        scroll = 0;
    }

    void close() {
        open = false;
        bar.mouseReleased();
    }

    boolean press(double x, double y) {
        if (bar.mouseClicked(x, y)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    boolean drag(double y) {
        if (bar.mouseDragged(y)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    void release() {
        bar.mouseReleased();
    }

    void wheel(double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v) * 3 * Type.BODY.lineHeight);   // clamped when drawn
    }

    /** "+2.4%" for a gain. */
    static String percent(double gain) {
        return String.format(Locale.ROOT, "+%.1f%%", gain * 100);
    }

    /** Slot by slot, the copies of a tome in one row (the same tome, owned or not, stays apart). */
    private static List<Line> lines(List<TomeAdvice.Row> rows) {
        Map<String, Line> out = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparing(r -> r.tome().kind())).forEach(r ->
                out.merge(r.tome().name() + "|" + r.owned() + "|" + r.counted(), new Line(r, 1, r.gain()),
                        (a, b) -> new Line(a.row(), a.n() + 1, a.gain() + b.gain())));
        return new ArrayList<>(out.values());
    }

    int[] drawList(GuiGraphics g, HomeTab.Host host, TomeAdvice.Advice advice, int mx, int my) {
        if (!open || at == null || advice == null) {
            open = false;
            return null;
        }
        Font font = UiFont.get();
        boolean known = GameState.current().tomesKnown();
        List<Line> rows = lines(advice.rows());
        List<Line> unscored = lines(advice.unscored());
        int content = Type.BODY.lineHeight + Theme.SP_2 + rows.size() * ROW_H;
        content += unscored.isEmpty() ? 0 : Theme.SP_1 + Type.LABEL.lineHeight + Theme.SP_1 + unscored.size() * ROW_H;
        content += known ? 0 : Theme.SP_1 + Type.META.lineHeight;
        var mc = net.minecraft.client.Minecraft.getInstance();
        int sw = mc.screen == null ? 10_000 : mc.screen.width;
        int sh = mc.screen == null ? 10_000 : mc.screen.height;
        int[] bounds = UiMenu.bounds(UiMenu.Spec.under(at, sw, sh));
        int room = Math.max(bounds[3] - (at[1] + at[3]), at[1] - bounds[1]) - Theme.SP_1 - 2 * Theme.SP_2;
        int viewH = Math.min(content, Math.max(4 * ROW_H, room));
        boolean scrolls = viewH < content;
        int w = Math.min(W + (scrolls ? ScrollBar.HIT : 0), bounds[2] - bounds[0] - 2 * Theme.SP_2);
        int[] b = UiMenu.place(at, w, viewH + 2 * Theme.SP_2, bounds);
        int x = b[0] + Theme.SP_3;
        int rw = w - 2 * Theme.SP_3 - (scrolls ? ScrollBar.HIT : 0);
        scroll = Math.max(0, Math.min(scroll, content - viewH));
        Kit.softCard(g, b[0], b[1], w, b[3]);
        int top = b[1] + Theme.SP_2;
        int bottom = top + viewH;
        g.enableScissor(b[0], top, x + rw, bottom);
        int y = top - scroll;
        Type.BODY.draw(g, font, L.t("wynnlabs.tomes.title"), x, y, Theme.INK);
        String total = percent(advice.total());
        Type.BODY.draw(g, font, total, x + rw - font.width(total), y, Theme.GOOD);
        y += Type.BODY.lineHeight + Theme.SP_2;
        for (Line l : rows) {
            y = row(g, font, host, l, x, y, rw, top, bottom);
        }
        if (!unscored.isEmpty()) {
            y += Theme.SP_1;
            Type.LABEL.draw(g, font, L.t("wynnlabs.tomes.unscored"), x, y, Theme.INK_2);
            y += Type.LABEL.lineHeight + Theme.SP_1;
            for (Line l : unscored) {
                y = row(g, font, host, l, x, y, rw, top, bottom);
            }
        }
        if (!known) {
            Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.tomes.unknown"), rw), x, y + Theme.SP_1, Theme.INK_2);
        }
        g.disableScissor();
        if (scrolls) {
            bar.place(x + rw, top, viewH, viewH, content, scroll);
            bar.draw(g, mx, my);
        } else {
            bar.place(0, 0, 0, 0, 0, 0);
        }
        return b;
    }

    private static int row(GuiGraphics g, Font font, HomeTab.Host host, Line l, int x, int y, int w, int top, int bottom) {
        TomeAdvice.Row r = l.row();
        ItemIcons.draw(g, new PoolItem(TomeAdvice.plain(r.tome().name()), "TomeItem", Tier.NORMAL, r.tome().kind().name() + "_TOME", 1),
                Klass.NONE, x, y, ICON, false);
        String gain = r.counted() ? L.t("wynnlabs.tomes.counted") : Double.isNaN(l.gain()) ? "" : percent(l.gain());
        int textY = y + (ICON - Type.BODY.lineHeight) / 2 + 1;
        int nameX = x + ICON + Theme.SP_2;
        int gainX = x + w - GAIN_W;
        String copies = l.n() > 1 ? " x" + l.n() : "";
        int room = gainX - Theme.SP_2 - nameX - (r.owned() ? TICK : 0) - font.width(copies);
        Type.BODY.draw(g, font, Type.BODY.fit(font, r.tome().name(), room) + copies, nameX, textY, Theme.INK);
        if (r.owned()) {
            Art.icon(g, "check", gainX - TICK, y + 2, 8, Theme.GOOD);
        }
        Type.BODY.draw(g, font, gain, x + w - font.width(gain), textY, r.counted() ? Theme.INK_2 : Theme.GOOD);
        if (y >= top && y + ROW_H <= bottom) {
            host.area(x, y, w, ROW_H, null, tip(r), null);
        }
        return y + ROW_H;
    }

    private static List<Component> tip(TomeAdvice.Row r) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(r.tome().name()).withStyle(ChatFormatting.WHITE));
        MutableComponent line = L.c("wynnlabs.tomes.level_tip", r.tome().level());
        out.add(line.withStyle(ChatFormatting.GRAY));
        out.add(L.c("wynnlabs.tomes.kind." + r.tome().kind().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        out.add((r.owned() ? L.c("wynnlabs.tomes.owned")
                : r.tome().source().isEmpty() ? L.c("wynnlabs.tomes.no_source")
                : Component.literal(r.tome().source())).withStyle(ChatFormatting.GRAY));
        return out;
    }
}
