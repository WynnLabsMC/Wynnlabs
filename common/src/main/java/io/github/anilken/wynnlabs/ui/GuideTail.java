package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * What follows a stage's rows: on a long stage one quiet "Sonra" line to the next stage; on a
 * short one (or one with no steps) the next three stages and the quest's facts, so the pane reads
 * as a page rather than an empty sheet.
 */
final class GuideTail {

    private GuideTail() {}

    static void add(GuideScreen s, Font font, List<GuideRows.Block> blocks, int x, int w, boolean short_) {
        int n = s.stages.size();
        if (!short_) {
            if (s.sel + 1 < n) {
                int next = s.sel + 1;
                blocks.add(new GuideRows.Block(Theme.SP_6, Type.BODY.lineHeight, (g, y, mx, my) -> {
                    Kit.hair(g, x, y - Theme.SP_3, w, 1, Theme.LINE);
                    String k = L.t("wynnlabs.guide.next");
                    Type.LABEL.draw(g, font, k, x, y, Theme.INK_2);
                    int cx = x + Type.LABEL.width(font, k) + Theme.SP_3;
                    String t = QuestWiki.stageTitle(s.stages.get(next).title()) + " · " + s.labels.get(next);
                    line(s, g, font, mx, my, x, cx, y, w, t, next);
                }));
            }
            return;
        }
        if (s.sel + 1 < n) {
            blocks.add(section(font, L.t("wynnlabs.guide.upcoming"), x));
            for (int i = s.sel + 1; i < n && i <= s.sel + 3; i++) {
                int stage = i;
                blocks.add(new GuideRows.Block(stage == s.sel + 1 ? Theme.SP_2 : Theme.SP_3, Type.BODY.lineHeight, (g, y, mx, my) -> {
                    Type.NUMBER.drawRight(g, font, (stage + 1) + ".", x + GuidePane.MARK_W - Theme.SP_1, y, Theme.INK_2);
                    line(s, g, font, mx, my, x, x + GuidePane.MARK_W, y, w, s.labels.get(stage), stage);
                }));
            }
        }
        QuestWiki.Info info = s.info;
        if (info != null && info.level() > 0) {
            blocks.add(section(font, L.t("wynnlabs.guide.quest"), x));
            List<String> facts = new ArrayList<>();
            facts.add(L.t("wynnlabs.guide.level", info.level()));
            for (String f : new String[] {info.length(), info.difficulty(), info.province()}) {
                if (f != null && !f.isBlank()) {
                    facts.add(f);
                }
            }
            if (info.xp() > 0) {
                facts.add(GuidePane.compact(info.xp()) + " XP");
            }
            if (info.emeralds() > 0) {
                facts.add(info.emeralds() + " E");
            }
            String line = String.join("  ·  ", facts);
            blocks.add(new GuideRows.Block(Theme.SP_2, Type.META.lineHeight, (g, y, mx, my) -> Type.META.draw(g, font, Type.META.fit(font, line, w), x, y)));
            if (info.rewards() != null && !info.rewards().isEmpty()) {
                String r = L.t("wynnlabs.guide.reward", String.join(" · ", info.rewards()));
                blocks.add(new GuideRows.Block(Theme.SP_1, Type.META.lineHeight, (g, y, mx, my) -> Type.META.draw(g, font, Type.META.fit(font, r, w), x, y)));
            }
        }
    }

    private static GuideRows.Block section(Font font, String title, int x) {
        return new GuideRows.Block(Theme.SP_6, Type.LABEL.lineHeight, (g, y, mx, my) -> Type.LABEL.draw(g, font, title, x, y, Theme.INK_2));
    }

    /** A stage's label from {@code tx} with a "›" at the row's end; the row opens that stage. */
    private static void line(GuideScreen s, net.minecraft.client.gui.GuiGraphics g, Font font, int mx, int my,
                             int x, int tx, int y, int w, String text, int stage) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + Type.BODY.lineHeight;
        int arrow = Type.LINK.width(font, "›");
        Type.MUTED.draw(g, font, Type.MUTED.fit(font, text, x + w - tx - arrow - Theme.SP_5), tx, y, hover ? Theme.INK : Theme.INK_2);
        Type.LINK.draw(g, font, "›", x + w - arrow, y, hover ? Theme.GOLD_EDGE : Theme.LINK);
        s.tap(x, y, w, Type.BODY.lineHeight, () -> {
            s.pick(stage);
            s.follow = true;
        });
    }
}
