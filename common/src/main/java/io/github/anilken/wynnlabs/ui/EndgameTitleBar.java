package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The Endgame tab's title line: one chip that says this session in words ("Bu oturum: 3 raid ·
 * seri 5", "· 1 başarısız" only when there was one); a click opens the Kayıt drawer with the rest.
 */
final class EndgameTitleBar extends EndgamePanel {

    EndgameTitleBar(EndgameTab tab) {
        super(tab);
    }

    /** "Bu oturum: 3 raid · 1 başarısız · seri 5" (short: "3 raid · seri 5"). */
    static String sessionText(EndgameData d, boolean shortForm) {
        return sessionText(d.sessionDone(), d.sessionFailed(), d.streak(), shortForm);
    }

    /** The words of the chip: the raids done, the failed ones only when there were any, the streak when there is one. */
    static String sessionText(int done, int failed, int streak, boolean shortForm) {
        List<String> f = new ArrayList<>();
        f.add(L.t(shortForm ? "wynnlabs.endgame.session_short" : "wynnlabs.endgame.session", done));
        if (failed > 0) {
            f.add(L.t("wynnlabs.endgame.session_failed", failed));
        }
        if (streak > 0) {
            f.add(L.t("wynnlabs.endgame.session_streak", streak));
        }
        return String.join(" · ", f);
    }

    /** The chip at the title line's right edge (nothing while raids are not tracked). */
    void chip(GuiGraphics g, int x, int titleY, int w, boolean compact, int mx, int my) {
        if (!tab.d.raidsUp() || tab.d.sessionDone() + tab.d.sessionFailed() == 0 && tab.d.streak() == 0) {
            return;
        }
        int py = Type.TITLE.onCaps(titleY, Ui.CHIP_H);
        String text = sessionText(tab.d, compact);
        int cw = Ui.iconChipWidth(tab.font, "clock", text, false);
        int titleEnd = x + Type.TITLE.width(tab.font, L.t("wynnlabs.screen.tab_endgame")) + Theme.SP_5;
        if (x + w - cw < titleEnd) {
            text = sessionText(tab.d, true);
            cw = Ui.iconChipWidth(tab.font, "clock", text, false);
        }
        int cx = x + w - cw;
        Ui.iconChip(g, tab.font, "clock", text, false, cx, py, cw, false, mx, my);
        tab.acts.add(new Act(cx, py, cw, Ui.CHIP_H, tab::openLog, "endgame:session"));
        tab.tips.add(new Tip(cx, py, cw, Ui.CHIP_H, List.of(title(L.t("wynnlabs.endgame.tip_session_head"), ChatFormatting.GREEN),
                grey(L.t("wynnlabs.endgame.tip_session_since")), grey(L.t("wynnlabs.endgame.tip_streak")),
                dark(L.t("wynnlabs.endgame.session_click")))));
    }
}
