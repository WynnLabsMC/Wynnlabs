package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.quests.GuideImages;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestGuides;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The picked quest as its guide, the quest guide's own look: the name in TITLE, its facts in one
 * META line, the stage you are on as LEAD on a soft card with "Yol ›", its places in a line, what it
 * needs and gives, Wynntils' map of the stage with the wiki's pictures, the next stages, and
 * "Rehberi aç" (the full guide), tracking and the compass at the foot.
 */
final class QuestsGuidePane extends MenuPaint {

    private final QuestsTab tab;

    QuestsGuidePane(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    /**
     * The pane's title row, shared with the places view: the name, its facts and the "Rehber |
     * Noktalar" switch on the right; returns the height used (with its SP_2 below).
     */
    int titleRow(GuiGraphics g, int x, int y, int w, String name, QuestWiki.Info info, int places, int mx, int my) {
        QuestBook.Entry q = QuestsData.entry(name);
        boolean compact = menu.layout.compact();
        // the switch first: its width comes off the name's
        String guideL = L.t("wynnlabs.quests.view_guide");
        String pointsL = L.t("wynnlabs.quests.view_points", places);
        int gw = Ui.iconChipWidth(font(), "script", guideL, false);
        int pw = Ui.iconChipWidth(font(), "map-pin", pointsL, false);
        int sx = x + w - gw - Theme.SP_1 - pw;
        int sy = Type.TITLE.onCaps(y, Ui.CHIP_H);
        Ui.iconChip(g, font(), "script", guideL, false, sx, sy, gw, tab.questDetailTab != 3, mx, my);
        menu.hits.add(new MenuHit(sx, sy, gw, Ui.CHIP_H, null, null, "#qview:0"));
        Ui.iconChip(g, font(), "map-pin", pointsL, false, sx + gw + Theme.SP_1, sy, pw, tab.questDetailTab == 3, mx, my);
        menu.hits.add(new MenuHit(sx + gw + Theme.SP_1, sy, pw, Ui.CHIP_H, null, null, "#qview:3"));
        boolean started = name.equals(QuestsData.tracked()) || q != null && "STARTED".equals(q.status());
        String title = Type.TITLE.fit(font(), QuestGuides.title(name), sx - Theme.SP_4 - x);
        Type.TITLE.draw(g, font(), title, x, y, started ? Theme.LINK : Type.TITLE.colour);
        String facts = facts(name, info, q);
        int fx = x + Type.TITLE.width(font(), title) + Theme.SP_4;
        int factsY = y + Type.TITLE.lineHeight - Type.META.lineHeight;
        if (!compact && sx - Theme.SP_4 - fx >= 60) {
            Type.META.draw(g, font(), Type.META.fit(font(), facts, sx - Theme.SP_4 - fx), fx, factsY, Theme.INK_2);
            return Type.TITLE.lineHeight + Theme.SP_3;
        }
        Type.META.draw(g, font(), Type.META.fit(font(), facts, w), x, y + Type.TITLE.lineHeight + Theme.SP_1, Theme.INK_2);
        return Type.TITLE.lineHeight + Theme.SP_1 + Type.META.lineHeight + Theme.SP_3;
    }

    /** "Lv 43 · Wynn · Orta · ~24 dk · 829 m · seviyenin %11'i" (the stage when started). */
    private String facts(String name, QuestWiki.Info info, QuestBook.Entry q) {
        List<String> f = new ArrayList<>();
        if (q != null) {
            f.add("Lv " + q.level());
        }
        if (info != null && !info.province().isEmpty()) {
            f.add(info.province());
        }
        int st = QuestsData.stage(name, info);
        if (st < 0 && q != null) {
            f.add("HARD".equals(q.difficulty()) ? L.t("wynnlabs.quests.diff_hard")
                    : "EASY".equals(q.difficulty()) ? L.t("wynnlabs.quests.diff_easy") : L.t("wynnlabs.quests.diff_medium"));
            f.add(L.t("wynnlabs.quests.about_minutes", QuestAdvisor.minutes(new QuestAdvisor.Suggestion(q, 0, -1, List.of()))));
        }
        if (q != null && q.x() != null) {
            String d = QuestsData.distance(new int[] {q.x(), 0, q.z()});
            if (d != null && st < 0) {
                f.add(d);
            }
        }
        if (q != null && q.xp() > 0 && !"COMPLETED".equals(q.status())) {
            f.add(tab.share(q.xp()));
        }
        return String.join(" · ", f);
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        String name = tab.questSelected;
        QuestWiki.Info info = QuestWiki.get(name);
        QuestBook.Entry q = QuestsData.entry(name);
        List<QuestMap.Place> places = QuestsData.mapPlaces(name, info, true);
        int bottom = y + h;
        int foot = bottom - Ui.BUTTON_H;
        int cy = y + titleRow(g, x, y, w, name, info, places.size(), mx, my);
        boolean mini = io.github.anilken.wynnlabs.quests.MiniQuests.is(name);
        if (info == null) {
            String why = !QuestWiki.on() ? L.t("wynnlabs.quests.wiki_off")
                    : mini ? L.t(io.github.anilken.wynnlabs.quests.MiniQuests.loading() ? "wynnlabs.quests.wiki_loading" : "wynnlabs.quests.mini_unknown")
                    : QuestWiki.failed(name) ? L.t("wynnlabs.quests.wiki_not_found") : L.t("wynnlabs.quests.wiki_loading");
            Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), why, w), x, cy);
            buttons(g, x, foot, w, name, q, null, mx, my);
            return;
        }
        int cur = QuestsData.stage(name, info);
        int shown = Math.max(0, Math.min(info.stages().size() - 1, cur));
        QuestWiki.Stage st = info.stages().get(shown);
        if (!mini) {                                // a mini has one step: no "Aşama 1 / 1"
            Type.LABEL.draw(g, font(), L.t("wynnlabs.quests.stage_of", shown + 1, info.stages().size()), x, cy, Theme.ACCENT);
        }
        if (QuestWiki.newer(info)) {
            String note = L.t("wynnlabs.quests.newer_than_translation");
            int nx = x + Type.LABEL.width(font(), L.t("wynnlabs.quests.stage_of", shown + 1, info.stages().size())) + Theme.SP_4;
            Type.META.draw(g, font(), Type.META.fit(font(), note, x + w - nx), nx, cy + 1, Theme.WARN);
        }
        cy += mini ? 0 : Type.LABEL.lineHeight + Theme.SP_2;
        int[] at = st.at() != null ? st.at() : !st.points().isEmpty() ? st.points().get(0) : null;
        cy = stageCard(g, x, cy, w, st, at, mx, my) + Theme.SP_3;
        // the stage's places in a line, the full list one click away
        if (!places.isEmpty()) {
            String nearest = nearest(places);
            String line = nearest == null ? L.n("wynnlabs.quests.points_count", places.size())
                    : L.n("wynnlabs.quests.points_head", places.size(), places.size(), nearest);
            Type.META.draw(g, font(), line, x, cy, Theme.INK_2);
            String link = L.t("wynnlabs.quests.points_link");
            int lx = x + Type.META.width(font(), line) + Theme.SP_3;
            Type.META.draw(g, font(), link, lx, cy, Theme.LINK);
            menu.hits.add(new MenuHit(lx, cy, Type.META.width(font(), link), Type.META.lineHeight, null, null, "#qview:3"));
            cy += Type.META.lineHeight + Theme.SP_4;
        }
        cy = needs(g, x, cy, w, info, q);
        cy = QuestsGuideFacts.rewards(this, g, x, cy, w, info, q, tab);
        cy = QuestsGuideFacts.enemies(this, g, x, cy, w, info);
        cy += Theme.SP_4;
        // Wynntils' map of the stage and the wiki's pictures, when there is room for them
        int stripH = 72;
        if (!menu.layout.compact() && cy + Type.META.lineHeight + Theme.SP_2 + stripH + Theme.SP_5 <= foot) {
            // what is left to do: the map frames the stage you are on, the rest go to its border
            List<QuestMap.Place> here = QuestsData.mapPlaces(name, info, false);
            List<GuideImages.Image> pics = QuestPictures.of(name, st.title());
            boolean mapOk = !here.isEmpty() && QuestMap.available(here);
            if (mapOk || !pics.isEmpty()) {
                Type.META.draw(g, font(), L.t("wynnlabs.quests.map_pictures"), x, cy, Theme.INK_2);
                cy += Type.META.lineHeight + Theme.SP_2;
                int px = x;
                if (mapOk && tab.map.draw(g, x, cy, 150, stripH, here, null, false, name + "|" + cur + "|g", mx, my)) {
                    px += 150 + Theme.SP_4;
                }
                pictures(g, px, cy, x + w - px, stripH, pics, name, mx, my);
                cy += stripH + Theme.SP_5;
            }
        }
        // the stages after it, dim, numbered as the guide's list
        if (shown + 1 < info.stages().size() && cy + Type.META.lineHeight + Theme.SP_2 + Type.BODY.lineHeight <= foot - Theme.SP_4) {
            Type.META.draw(g, font(), L.t("wynnlabs.quests.next_stages"), x, cy, Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_2;
            for (int i = shown + 1; i < info.stages().size() && cy + Type.BODY.lineHeight <= foot - Theme.SP_4 && i <= shown + 3; i++) {
                QuestWiki.Stage next = info.stages().get(i);
                String text = QuestWiki.placeText(next.task().isEmpty() ? QuestWiki.stageTitle(next.title()) : next.task());
                Type.NUMBER.draw(g, font(), (i + 1) + ".", x, cy, Theme.INK_3);
                Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), text.isEmpty() ? next.task() : text, w - 16), x + 16, cy);
                cy += Type.BODY.lineHeight + Theme.SP_2;
            }
        }
        buttons(g, x, foot, w, name, q, at, mx, my);
    }

    /**
     * The stage's task in LEAD on a soft card, its place under it (with its pin) and "Yol ›" with
     * the distance on the right; returns the y under the card.
     */
    private int stageCard(GuiGraphics g, int x, int y, int w, QuestWiki.Stage st, int[] at, int mx, int my) {
        int pad = menu.layout.compact() ? Theme.SP_3 : Theme.SP_4;
        String text = QuestWiki.placeText(QuestWiki.taskText(st.task()));
        if (text.isEmpty()) {
            text = st.task().isEmpty() ? QuestWiki.stageTitle(st.title()) : st.task();
        }
        int goW = GuidePane.goW(font());
        int tw = w - 2 * pad - (at != null ? goW + Theme.SP_5 : 0);
        String lead = text;
        List<FormattedCharSequence> lines = QuestsCache.kept("wrap|lead|" + tw + "|" + lead, 30_000, () -> Type.LEAD.wrap(font(), lead, tw));
        int n = Math.min(2, lines.size());
        String dist = at == null ? null : QuestsData.distance(at);
        int h = pad + n * Type.LEAD.lineHeight + (at != null ? Theme.SP_1 + Type.META.lineHeight : 0) + pad;
        Kit.softCard(g, x, y, w, h);
        int ly = y + pad;
        for (int i = 0; i < n; i++) {
            if (i == n - 1 && lines.size() > n) {
                Type.LEAD.draw(g, font(), Type.LEAD.fit(font(), rest(text, lines, i), tw), x + pad, ly);
            } else {
                g.drawString(font(), Kit.ink(lines.get(i)), x + pad, ly, Type.LEAD.colour, false);
            }
            ly += Type.LEAD.lineHeight;
        }
        if (at != null) {
            ly += Theme.SP_1;
            // every place of the stage ("here or there" alike), each with its pin; what does not fit is in Noktalar
            List<int[]> all = st.points().size() > 1 ? QuestsData.distinct(st.points()) : List.of(at);
            int px = x + pad;
            int room = x + w - pad - goW - Theme.SP_5;
            for (int[] p : all) {
                String where = p[0] + ", " + p[1] + ", " + p[2];
                int ww = Type.META.width(font(), where);
                if (px + ww + Theme.SP_1 + 8 > room && px > x + pad) {
                    break;
                }
                Type.META.draw(g, font(), where, px, ly, Theme.LINK);
                Art.icon(g, "map-pin", px + ww + Theme.SP_1, Type.META.onCaps(ly, 8), 8, Theme.GOLD_EDGE);
                menu.hits.add(new MenuHit(px, ly, ww + Theme.SP_1 + 8, Type.META.lineHeight, null,
                        List.of(L.c("wynnlabs.quests.point_click").withStyle(ChatFormatting.GRAY)), "/compass at " + p[0] + " " + p[1] + " " + p[2]));
                px += ww + Theme.SP_1 + 8 + Theme.SP_4;
            }
            String go = "/compass at " + at[0] + " " + at[1] + " " + at[2];
            int bx = x + w - pad - goW;
            int by = y + pad + (Type.LEAD.lineHeight - Ui.SMALL_H) / 2;
            Ui.small(g, font(), L.t("wynnlabs.guide.go"), bx, by, goW, mx, my);
            menu.hits.add(new MenuHit(bx, by, goW, Ui.SMALL_H, null, null, go));
            if (dist != null) {
                Type.META.draw(g, font(), dist, bx + (goW - Type.META.width(font(), dist)) / 2, by + Ui.SMALL_H + Theme.SP_1, Theme.INK_3);
            }
        }
        return y + h + 2;
    }

    /** The text from wrapped line {@code i} on (so the last shown line ends in "…"). */
    private static String rest(String text, List<FormattedCharSequence> lines, int i) {
        int skip = 0;
        for (int k = 0; k < i; k++) {
            StringBuilder b = new StringBuilder();
            lines.get(k).accept((idx, style, cp) -> {
                b.appendCodePoint(cp);
                return true;
            });
            skip += b.length();
        }
        return text.substring(Math.min(text.length(), skip)).stripLeading();
    }

    private static String nearest(List<QuestMap.Place> places) {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) {
            return null;
        }
        double best = Double.MAX_VALUE;
        for (QuestMap.Place pl : places) {
            if (pl.state() != QuestMapLayout.DONE) {
                best = Math.min(best, Math.hypot(p.getX() - pl.at()[0], p.getZ() - pl.at()[2]));
            }
        }
        return best == Double.MAX_VALUE ? null : best >= 1000 ? String.format(Locale.ROOT, "%.1f km", best / 1000) : Math.round(best) + " m";
    }

    /** What the quest needs that you lack: the quests before it, a profession's level. */
    private int needs(GuiGraphics g, int x, int y, int w, QuestWiki.Info info, QuestBook.Entry q) {
        if (q != null && !q.met() && !"COMPLETED".equals(q.status())) {
            Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.quests.prereq_missing"), w), x, y, Theme.WARN);
            y += Type.META.lineHeight + Theme.SP_1;
        }
        List<String> missing = new ArrayList<>();
        for (var e : info.professions().entrySet()) {
            int have = Professions.level(e.getKey());
            if (have >= 0 && have < e.getValue()) {
                missing.add(Professions.name(e.getKey()) + " " + have + "/" + e.getValue());
            }
        }
        if (!missing.isEmpty()) {
            String t = L.t("wynnlabs.quests.needs_profession", String.join(", ", missing));
            Type.META.draw(g, font(), Type.META.fit(font(), t, w), x, y, Theme.BAD);
            y += Type.META.lineHeight + Theme.SP_1;
        }
        return y;
    }

    /** The wiki's pictures of the stage at x, y, as many as fit in {@code w}; a click opens the guide on them. */
    private void pictures(GuiGraphics g, int x, int y, int w, int th, List<GuideImages.Image> pics, String name, int mx, int my) {
        int px = x;
        for (GuideImages.Image p : pics) {
            GuideImageCache.Tex t = GuideImageCache.get(p.file());
            int tw = t == null ? th * 4 / 3 : Math.max(24, Math.min(th * 2, t.w() * th / Math.max(1, t.h())));
            if (px + tw > x + w) {
                break;
            }
            if (t == null) {
                g.fill(px, y, px + tw, y + th, Theme.WELL);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, t.id(), px, y, 0, 0, tw, th, t.w(), t.h(), t.w(), t.h());
            }
            boolean hover = mx >= px && mx < px + tw && my >= y && my < y + th;
            int edge = hover ? Theme.GOLD_EDGE : Theme.LINE;
            Kit.hair(g, px, y - 1, tw, 1, edge);
            Kit.hair(g, px, y + th, tw, 1, edge);
            Kit.hair(g, px - 1, y, 1, th, edge);
            Kit.hair(g, px + tw, y, 1, th, edge);
            menu.hits.add(new MenuHit(px, y, tw, th, null, null, "#guide:" + name + "\n" + QuestsData.task(name)));
            px += tw + Theme.SP_3;
        }
    }

    /** "Rehberi aç", tracking and the compass, along the pane's foot. */
    private void buttons(GuiGraphics g, int x, int y, int w, String name, QuestBook.Entry q, int[] at, int mx, int my) {
        String open = L.t("wynnlabs.quests.open_guide");
        int bw = Ui.iconButtonLabelWidth(font(), open);
        Ui.iconButtonLabel(g, font(), "script", open, x, y, bw, mx, my);
        menu.hits.add(new MenuHit(x, y, bw, Ui.BUTTON_H, null, null, "#guide:" + name + "\n" + QuestsData.task(name)));
        int bx = x + bw + Theme.SP_2;
        int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        if (q != null && !"COMPLETED".equals(q.status())) {
            boolean on = name.equals(QuestsData.tracked());
            String t = L.t(on ? "wynnlabs.quests.btn_tracked" : "wynnlabs.quests.btn_track");
            int tw = Ui.iconChipWidth(font(), "bookmark", t, false);
            Ui.iconChip(g, font(), "bookmark", t, false, bx, cy, tw, on, mx, my);
            menu.hits.add(new MenuHit(bx, cy, tw, Ui.CHIP_H, null, null, "#qtrack:" + name));
            bx += tw + Theme.SP_2;
        }
        if (at != null && !menu.layout.compact()) {
            String t = L.t("wynnlabs.quests.compass");
            int tw = Ui.iconChipWidth(font(), "compass", t, false);
            if (bx + tw <= x + w) {
                Ui.iconChip(g, font(), "compass", t, false, bx, cy, tw, false, mx, my);
                menu.hits.add(new MenuHit(bx, cy, tw, Ui.CHIP_H, null, null, "/compass at " + at[0] + " " + at[1] + " " + at[2]));
            }
        }
    }

    /** For the facts helper: the font, as this pane draws it. */
    net.minecraft.client.gui.Font paneFont() {
        return font();
    }

    /** For the facts helper: a tooltip over a line. */
    void tip(int x, int y, int w, int h, List<Component> lines) {
        menu.hits.add(new MenuHit(x, y, w, h, null, lines, null));
    }
}
