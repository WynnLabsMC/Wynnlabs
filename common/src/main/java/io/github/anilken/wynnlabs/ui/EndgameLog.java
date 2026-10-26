package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The Endgame page's "Kayıt ›" drawer: this session and your streak in words, the pulls since your
 * last mythic, every raid with its wins, losses and times, and the recent raids and lootruns with
 * this week's mythic camps.
 */
final class EndgameLog extends EndgamePanel {

    private final EndgameRaids raids;
    private final EndgameRecentRuns runs;

    EndgameLog(EndgameTab tab, EndgameRaids raids, EndgameRecentRuns runs) {
        super(tab);
        this.raids = raids;
        this.runs = runs;
    }

    /** The drawer's content from x, y across w; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        int y0 = y;
        // this session and the streak, said out
        if (tab.d.raidsUp()) {
            Type.LABEL.draw(g, tab.font, L.t("wynnlabs.endgame.log_session"), x, y);
            Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, EndgameTitleBar.sessionText(tab.d, true), w), x, y + Type.LABEL.lineHeight,
                    Theme.INK_2);
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, L.t("wynnlabs.endgame.tip_session_since") + " "
                    + L.t("wynnlabs.endgame.tip_streak"), w), x, y + Type.LABEL.lineHeight + Type.BODY.lineHeight, Theme.INK_3);
            y += Type.LABEL.lineHeight + Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_5;
        }
        // the pulls since the last mythic: three numbers
        List<String[]> facts = new ArrayList<>();
        if (tab.d.raidsUp() && tab.d.dry() != null && tab.d.dry().aspectPulls() >= 0) {
            facts.add(new String[] {String.valueOf(tab.d.dry().aspectPulls()), L.t("wynnlabs.endgame.log_dry_raid")});
        }
        if (tab.d.lootUp() && tab.d.dryPulls() >= 0 && !tab.d.lootruns().isEmpty()) {
            facts.add(new String[] {String.valueOf(tab.d.dryPulls()), L.t("wynnlabs.endgame.log_dry_loot")});
        }
        if (tab.d.raidsUp() && tab.d.dry() != null && tab.d.dry().tomePulls() >= 0) {
            facts.add(new String[] {String.valueOf(tab.d.dry().tomePulls()), L.t("wynnlabs.endgame.log_dry_tome")});
        }
        if (!facts.isEmpty()) {
            // side by side when each has room for its words, else one under the other
            int fw = w / facts.size();
            boolean row = fw >= 190;
            for (int i = 0; i < facts.size(); i++) {
                int fx = row ? x + i * fw : x;
                int fy = row ? y : y + i * (Type.LEAD.lineHeight + Theme.SP_1);
                int cw = row ? fw - Theme.SP_3 : w;
                Type.LEAD.draw(g, tab.font, facts.get(i)[0], fx, fy, Theme.ACCENT);
                int lx = fx + Type.LEAD.width(tab.font, facts.get(i)[0]) + Theme.SP_2;
                Type.META.draw(g, tab.font, Type.META.fit(tab.font, facts.get(i)[1], fx + cw - lx), lx, fy + Theme.SP_2, Theme.INK_3);
            }
            y += (row ? 1 : facts.size()) * (Type.LEAD.lineHeight + Theme.SP_1) + Theme.SP_4;
        }
        // every raid: wins, losses, best, average, last; then the recent runs and this week's mythic camps
        y = raids.raids(g, x, y, w, 13, true, false, mx, my) + Theme.SP_5;
        int feedH = Math.max(140, bottom - y);
        runs.feed(g, x, y, w, feedH, true, mx, my);
        y += feedH;
        return y - y0;
    }
}
