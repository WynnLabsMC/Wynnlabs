package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.TomeAdvice;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tomes that suit the build shown, in one popup over the Gear panel's Tomes chip: grouped by
 * slot, each with what it adds and where it drops, what the model cannot score at the end. Nothing
 * here changes the build. A class of its own made on first use (safe to hot swap in).
 */
final class HomeTomes {

    private static final int W = 380;
    private static final int ROW_H = Type.BODY.lineHeight + Theme.SP_2;
    private static final int GAIN_W = 42;
    private static final int SOURCE_W = 100;

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

    private static int block(int rows) {
        return Type.LABEL.lineHeight + Theme.SP_1 + rows * ROW_H + Theme.SP_2;
    }

    int[] drawList(GuiGraphics g, HomeTab.Host host, TomeAdvice.Advice advice, int mx, int my) {
        if (!open || at == null || advice == null) {
            open = false;
            return null;
        }
        Font font = UiFont.get();
        boolean known = GameState.current().tomesKnown();
        int content = Type.BODY.lineHeight + Theme.SP_3;
        for (TomeAdvice.Kind k : TomeAdvice.Kind.values()) {
            int n = count(advice.rows(), k);
            content += n > 0 ? block(n) : 0;
        }
        content += advice.unscored().isEmpty() ? 0 : block(advice.unscored().size());
        content += known ? 0 : Type.META.lineHeight + Theme.SP_1;
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
        y += Type.BODY.lineHeight + Theme.SP_3;
        for (TomeAdvice.Kind k : TomeAdvice.Kind.values()) {
            if (count(advice.rows(), k) > 0) {
                y = title(g, font, L.t("wynnlabs.tomes.kind." + k.name().toLowerCase(Locale.ROOT)), x, y);
                for (TomeAdvice.Row r : advice.rows()) {
                    if (r.tome().kind() == k) {
                        y = row(g, font, host, r, x, y, rw, top, bottom);
                    }
                }
                y += Theme.SP_2;
            }
        }
        if (!advice.unscored().isEmpty()) {
            y = title(g, font, L.t("wynnlabs.tomes.unscored"), x, y);
            for (TomeAdvice.Row r : advice.unscored()) {
                y = row(g, font, host, r, x, y, rw, top, bottom);
            }
            y += Theme.SP_2;
        }
        if (!known) {
            Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.tomes.unknown"), rw), x, y, Theme.INK_2);
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

    private static int count(List<TomeAdvice.Row> rows, TomeAdvice.Kind k) {
        return (int) rows.stream().filter(r -> r.tome().kind() == k).count();
    }

    private static int title(GuiGraphics g, Font font, String text, int x, int y) {
        Type.LABEL.draw(g, font, text, x, y, Theme.INK_2);
        return y + Type.LABEL.lineHeight + Theme.SP_1;
    }

    private static int row(GuiGraphics g, Font font, HomeTab.Host host, TomeAdvice.Row r, int x, int y, int w, int top, int bottom) {
        int tick = 10;
        if (r.owned()) {
            Art.icon(g, "check", x, y + 1, 8, Theme.GOOD);
        }
        String gain = r.counted() ? L.t("wynnlabs.tomes.counted") : Double.isNaN(r.gain()) ? "" : percent(r.gain());
        String source = r.tome().source().isEmpty() ? L.t("wynnlabs.tomes.no_source") : r.tome().source();
        if (r.owned()) {
            source = L.t("wynnlabs.tomes.owned");
        }
        int gainX = x + w - GAIN_W;
        int sourceX = gainX - SOURCE_W - Theme.SP_2;
        Type.BODY.draw(g, font, Type.BODY.fit(font, r.tome().name(), sourceX - Theme.SP_2 - x - tick), x + tick, y, Theme.INK);
        Type.META.draw(g, font, Type.META.fit(font, source, SOURCE_W), sourceX, y + 1, Theme.INK_2);
        Type.BODY.draw(g, font, gain, x + w - font.width(gain), y, r.counted() ? Theme.INK_2 : Theme.GOOD);
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
        if (!r.tome().source().isEmpty()) {
            out.add(Component.literal(r.tome().source()).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }
}
