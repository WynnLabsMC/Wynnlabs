package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.ui.HomeTab.Host;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The home's "First steps" card while a menu Wynn Labs reads is still unopened on this
 * character ({@link FirstSteps}): "2/4" with a bar, then each step with its tick, the open ones
 * first. It takes the place of the bank and discovery tiles, and of the bank's row on the
 * compact page, until all four are done; then it is gone for good.
 */
final class HomeSteps extends HomePart {

    /** The card: the title with the count, the bar, and the steps that fit. */
    void draw(GuiGraphics g, Host host, int x, int y, int w, int h, int mx, int my) {
        boolean[] done = FirstSteps.done();
        int n = FirstSteps.count(done);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        card(g, x, y, w, h, hover);
        Art.icon(g, "list-check", x + Theme.SP_3, Type.BODY.onCaps(y + Theme.SP_3, 8), 8, Theme.INK_2);
        String count = n + "/" + done.length;
        int cw = Type.LABEL.width(font, count);
        Type.LABEL.draw(g, font, count, x + w - Theme.SP_3 - cw, y + Theme.SP_3, Theme.INK);
        int tx = x + Theme.SP_3 + 8 + Theme.SP_2;
        Type.BODY.draw(g, font, Type.BODY.fit(font, L.t("wynnlabs.steps.title"), x + w - Theme.SP_3 - cw - Theme.SP_3 - tx), tx,
                y + Theme.SP_3, Theme.INK_2);
        HdBar.draw(g, x + Theme.SP_3, y + 18, w - 2 * Theme.SP_3, 3, n / (double) done.length, Theme.GREEN);
        int pitch = Math.max(10, Math.min(large ? 16 : 13, (h - 30) / done.length));
        int ry = y + 26;
        for (int i : order(done)) {
            if (ry + 9 > y + h - 4) {
                break;
            }
            Art.box(g, x + Theme.SP_3, ry, 8, 8, 2, done[i] ? Theme.GREEN : Theme.WELL, done[i] ? 0 : Theme.LINE);
            if (done[i]) {
                Art.icon(g, "check", x + Theme.SP_3, ry, 8, Theme.CREAM);
            }
            int sx = x + Theme.SP_3 + 8 + Theme.SP_2;
            Type.BODY.draw(g, font, Type.BODY.fit(font, L.t(FirstSteps.KEYS.get(i)), x + w - Theme.SP_3 - sx), sx, ry - 1,
                    done[i] ? Theme.INK_3 : Theme.INK);
            ry += pitch;
        }
        host.area(x, y, w, h, null, tip(done), null);
    }

    /** The compact page's row: {icon, label, "2/4 · " and the first open step, no action}. */
    String[] row() {
        boolean[] done = FirstSteps.done();
        int first = order(done)[0];
        return new String[] {"list-check", L.t("wynnlabs.steps.title"), FirstSteps.count(done) + "/" + done.length + " · "
            + L.t(FirstSteps.KEYS.get(first)), null};
    }

    /** The tooltip: every step with a tick or a ring, and why. */
    List<Component> tip(boolean[] done) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(L.t("wynnlabs.steps.title")).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < done.length; i++) {
            tip.add(Component.literal((done[i] ? "✔ " : "○ ") + L.t(FirstSteps.KEYS.get(i)))
                    .withStyle(done[i] ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        tip.add(Component.literal(L.t("wynnlabs.steps.tip")).withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    /** The steps' indexes, the open ones first, each group in the card's order. */
    static int[] order(boolean[] done) {
        int[] out = new int[done.length];
        int k = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < done.length; i++) {
                if (done[i] == (pass == 1)) {
                    out[k++] = i;
                }
            }
        }
        return out;
    }
}
