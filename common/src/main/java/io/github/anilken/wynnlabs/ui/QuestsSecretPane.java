package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.quests.ContentType;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.Discoveries;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestWiki;
import io.github.anilken.wynnlabs.quests.StepPlaces;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A secret discovery's guide on the right of the quests tab: the name with its kind, level, region
 * and distance; one line of its story; what it needs; its numbered steps, each place with its pin
 * and "Yol ›"; Wynntils' map of the places beside them (a strip above them when small); then
 * Pusula (every place queued), Takip et, Buldum and Wiki.
 */
final class QuestsSecretPane extends MenuPaint {

    private final QuestsTab tab;
    /** The steps' scroll (a step a notch), their bar and box. */
    int stepScroll;
    final ScrollBar stepBar = new ScrollBar();
    int[] stepRect;
    private String scrollFor = "";

    QuestsSecretPane(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        stepRect = null;
        Discoveries.Entry e = QuestsSecrets.entry(tab.questSelected);
        if (e == null) {
            return;
        }
        if (!e.id().equals(scrollFor)) {
            scrollFor = e.id();
            stepScroll = 0;
        }
        boolean small = menu.layout.compact();
        boolean done = QuestsSecrets.done(e);
        int foot = y + h - Ui.BUTTON_H;
        // the title row
        Art.icon(g, "crystal-ball", x, Type.TITLE.onCaps(y, 10), 10, Theme.INK_2);
        int tx = x + 10 + Theme.SP_2;
        String name = Type.TITLE.fit(font(), e.name(), w - (tx - x));
        Type.TITLE.draw(g, font(), name, tx, y, Theme.LINK);
        List<String> facts = new ArrayList<>(List.of(ContentType.SECRET_DISCOVERY.label(), "Lv " + e.minLevel(), e.region()));
        String dist = QuestsData.distance(new int[] {e.x(), e.y(), e.z()});
        if (!small && dist != null) {
            facts.add(dist);
        }
        if (done) {
            facts.add(L.t("wynnlabs.content.done"));
        }
        int fx = tx + Type.TITLE.width(font(), name) + Theme.SP_4;
        if (fx < x + w) {
            Type.META.draw(g, font(), Type.META.fit(font(), String.join(" · ", facts), x + w - fx), fx,
                    y + Type.TITLE.lineHeight - Type.META.lineHeight - 1, e.minLevel() > GameState.current().level() ? Theme.WARN : Theme.INK_2);
        }
        int cy = y + Type.TITLE.lineHeight + Theme.SP_2;
        // the story in one line, all of it in the tooltip
        String lore = Type.META.fit(font(), e.lore(), w);
        Type.META.draw(g, font(), lore, x, cy, Theme.INK_2);
        tip(x, cy, Type.META.width(font(), lore), Type.META.lineHeight, QuestsSecrets.tip(e));
        cy += Type.META.lineHeight + Theme.SP_1;
        Discoveries.Guide guide = QuestsCache.kept("disc-guide|" + e.id(), 5000, () -> Discoveries.guide(e));
        cy = needs(g, x, cy, w, e, guide) + Theme.SP_1;
        if (guide.newer() || !QuestWiki.on()) {
            String note = guide.newer() ? L.t("wynnlabs.discoveries.newer") : L.t("wynnlabs.discoveries.wiki_off");
            Type.META.draw(g, font(), Type.META.fit(font(), note, w), x, cy, Theme.WARN);
            tip(x, cy, w, Type.META.lineHeight, List.of(Component.literal(note).withStyle(ChatFormatting.GOLD)));
            cy += Type.META.lineHeight + Theme.SP_1;
        }
        cy += Theme.SP_3;
        // the places: the first "Başla", the rest gold, in step order
        List<QuestMap.Place> places = places(e, guide);
        int bottom = foot - Theme.SP_5;
        if (small) {
            int stripH = Math.min(60, bottom - cy - 40);
            if (!places.isEmpty() && stripH >= 40 && QuestMap.available(places)
                    && tab.map.draw(g, x, cy, w, stripH, places, tab.litPlace, false, "d|" + e.id(), mx, my)) {
                cy += stripH + Theme.SP_3;
            }
            steps(g, x, cy, w - ScrollBar.HIT, bottom - cy, e, guide, mx, my);
        } else {
            int mapW = Math.min(300, w * 2 / 5);
            int listW = w;
            if (!places.isEmpty() && QuestMap.available(places)
                    && tab.map.draw(g, x + w - mapW, cy, mapW, bottom - cy, places, tab.litPlace, true, "d|" + e.id(), mx, my)) {
                listW = w - mapW - Theme.SP_6;
            }
            steps(g, x + Theme.SP_2, cy, listW - Theme.SP_2 - ScrollBar.HIT, bottom - cy, e, guide, mx, my);
        }
        buttons(g, x, foot, w, e, done, mx, my);
    }

    /** "Gerekli: …" with each need met (GOOD), not met (BAD) or unknown, or none; then how many steps and places. */
    private int needs(GuiGraphics g, int x, int y, int w, Discoveries.Entry e, Discoveries.Guide guide) {
        int places = StepPlaces.count(QuestsSecrets.marks(e));
        String count = L.t("wynnlabs.content.steps_places", guide.steps().size(), places);
        int cx = x;
        if (e.requires().isEmpty()) {
            String t = L.t("wynnlabs.content.needs_none") + " · " + count;
            Type.META.draw(g, font(), Type.META.fit(font(), t, w), x, y, Theme.INK_2);
            return y + Type.META.lineHeight;
        }
        Discoveries.Entry en = Discoveries.englishOf(e.id());
        List<QuestBook.Entry> quests = GameState.current().quests();
        java.util.Set<String> found = QuestsSecrets.found();
        String character = GameState.current().characterId();
        String head = L.t("wynnlabs.discoveries.needs") + " ";
        Type.META.draw(g, font(), head, cx, y, Theme.INK_2);
        cx += Type.META.width(font(), head);
        List<Component> tip = new ArrayList<>();
        tip.add(L.c("wynnlabs.discoveries.needs").withStyle(ChatFormatting.WHITE));
        for (int i = 0; i < e.requires().size(); i++) {
            Boolean met = en == null || i >= en.requires().size() ? null : Discoveries.met(en, en.requires().get(i), quests, found, character);
            int ink = met == null ? Theme.INK_3 : met ? Theme.GOOD : Theme.BAD;
            String part = (i > 0 ? " · " : "") + (met == null ? "" : met ? "✔ " : "✘ ") + e.requires().get(i);
            String cut = Type.META.fit(font(), part, Math.max(0, x + w - cx));
            Type.META.draw(g, font(), cut, cx, y, ink);
            cx += Type.META.width(font(), cut);
            tip.add(Component.literal(part.replace(" · ", "")).withStyle(met == null ? ChatFormatting.GRAY : met ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        tip(x, y, w, Type.META.lineHeight, tip);
        return y + Type.META.lineHeight;
    }

    /** The map's places: every place of the steps ("1a", "1b" for alternatives); the first is where you start. */
    private static List<QuestMap.Place> places(Discoveries.Entry e, Discoveries.Guide guide) {
        return QuestsCache.kept("disc-places|" + e.id(), 5000, () -> {
            List<QuestMap.Place> out = new ArrayList<>();
            List<List<StepPlaces.Mark>> marks = QuestsSecrets.marks(e);
            for (List<StepPlaces.Mark> step : marks) {
                for (StepPlaces.Mark m : step) {
                    String task = guide.steps().get(m.step()).text();
                    out.add(new QuestMap.Place(m.label(), m.at(), task, out.isEmpty() ? QuestMapLayout.START : QuestMapLayout.NOW));
                }
            }
            return out;
        });
    }

    /**
     * One step a row: its number; the task with every coordinate group in it a link with its pin
     * (as the quest guide draws them); a place the text does not show on a line under it; the
     * nearest place's distance; "Yol ›" (this step's places and the ones after, queued).
     */
    private void steps(GuiGraphics g, int x, int y, int w, int h, Discoveries.Entry e, Discoveries.Guide guide, int mx, int my) {
        List<Discoveries.Step> steps = guide.steps();
        List<List<StepPlaces.Mark>> marks = QuestsSecrets.marks(e);
        stepRect = new int[] {x, y, w + ScrollBar.HIT, h};
        int goW = GuidePane.goW(font());
        int textX = x + 16;
        int textW = w - 16 - goW - Theme.SP_3;
        stepScroll = Math.max(0, Math.min(stepScroll, Math.max(0, steps.size() - 1)));
        int ry = y;
        int shown = 0;
        tab.litPlace = null;
        for (int i = stepScroll; i < steps.size(); i++) {
            String text = steps.get(i).text();
            List<StepPlaces.Mark> here = marks.get(i);
            List<int[]> data = new ArrayList<>();
            for (StepPlaces.Mark m : here) {
                data.add(m.at());
            }
            List<List<GuideText.Tok>> lines = QuestsCache.kept("gtext|" + textW + "|" + text, 30_000,
                    () -> GuideText.wrap(font(), Type.BODY, text, data, textW));
            int n = Math.min(3, lines.size());
            StepPlaces.Mark own = here.stream().filter(m -> !m.inText()).findFirst().orElse(null);
            boolean metaLine = !here.isEmpty();
            int rh = Theme.SP_2 + n * Type.BODY.lineHeight + (metaLine ? Type.META.lineHeight : 0) + Theme.SP_2;
            if (ry + rh > y + h && shown > 0) {
                break;
            }
            boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + rh;
            if (hover) {
                g.fill(x - Theme.SP_2, ry, x + w + Theme.SP_2, ry + rh, Theme.CARD_HOVER);
                tab.litPlace = here.isEmpty() ? null : here.get(0).label();
            }
            int ty = ry + Theme.SP_2;
            Type.NUMBER.draw(g, font(), (i + 1) + ".", x, ty, Theme.PRICE);
            int step = i;
            GuideText.draw(g, font(), Type.BODY, lines.subList(0, n), textX, ty, Type.BODY.colour, mx, my, (tx, tyy, tw, th, sp) -> {
                int py = sp.knowsY() ? sp.at()[1] : here.isEmpty() ? e.y() : here.get(0).at()[1];
                menu.hits.add(new MenuHit(tx, tyy, tw, th, null, List.of(L.c("wynnlabs.discoveries.pin", e.name(), step + 1)),
                        "/compass at " + sp.at()[0] + " " + py + " " + sp.at()[2]));
            });
            ty += n * Type.BODY.lineHeight;
            if (lines.size() > n) {
                tip(textX, ry, textW, rh, List.of(Component.literal(text).withStyle(ChatFormatting.GRAY)));
            }
            if (metaLine) {
                int mx0 = textX;
                if (own != null) {
                    // a place the step's text does not show: its block and pin on this line
                    String where = own.at()[0] + ", " + own.at()[1] + ", " + own.at()[2];
                    Type.META.draw(g, font(), where, mx0, ty, Theme.LINK);
                    int ww = Type.META.width(font(), where);
                    Art.icon(g, "map-pin", mx0 + ww + Theme.SP_1, Type.META.onCaps(ty, 8), 8, Theme.GOLD_EDGE);
                    menu.hits.add(new MenuHit(mx0, ty, ww + Theme.SP_1 + 8, Type.META.lineHeight, null,
                            List.of(L.c("wynnlabs.discoveries.pin", e.name(), i + 1)), "/compass at " + own.at()[0] + " " + own.at()[1] + " " + own.at()[2]));
                    mx0 += ww + Theme.SP_1 + 8 + Theme.SP_3;
                }
                String d = nearest(here);
                if (d != null) {
                    Type.META.draw(g, font(), d, mx0, ty, Theme.INK_3);
                }
                int bx = x + w - goW;
                int by = ry + Theme.SP_2 + (Type.BODY.lineHeight - Ui.SMALL_H) / 2;
                Ui.small(g, font(), L.t("wynnlabs.guide.go"), bx, by, goW, mx, my);
                menu.hits.add(new MenuHit(bx, by, goW, Ui.SMALL_H, null,
                        List.of(L.c("wynnlabs.discoveries.pin", e.name(), i + 1).withStyle(ChatFormatting.LIGHT_PURPLE)),
                        "#discpin:" + i + ":" + e.id()));
            }
            ry += rh + Theme.SP_2;
            shown++;
        }
        // the licence of the wiki's steps: in the list's tooltip, no line of its own
        tip(x, y, 14, Math.min(h, ry - y), List.of(L.c("wynnlabs.discoveries.licence").withStyle(ChatFormatting.DARK_GRAY)));
        if (shown < steps.size()) {
            stepBar.place(x + w + Theme.SP_1, y, h, shown, steps.size(), stepScroll);
            stepBar.draw(g, mx, my);
        }
    }

    /** The nearest of a step's places from you ("320 m"), or null. */
    private static String nearest(List<StepPlaces.Mark> here) {
        String best = null;
        double far = Double.MAX_VALUE;
        var p = net.minecraft.client.Minecraft.getInstance().player;
        for (StepPlaces.Mark m : here) {
            double d = p == null ? 0 : Math.hypot(p.getX() - m.at()[0], p.getZ() - m.at()[2]);
            if (d < far) {
                far = d;
                best = QuestsData.distance(m.at());
            }
        }
        return best;
    }

    /** Pusula (every place, queued), Takip et, Buldum (locked when the book found it), Wiki. */
    private void buttons(GuiGraphics g, int x, int y, int w, Discoveries.Entry e, boolean done, int mx, int my) {
        String compass = L.t("wynnlabs.content.compass");
        int bw = Ui.iconButtonLabelWidth(font(), compass);
        Ui.iconButtonLabel(g, font(), "compass", compass, x, y, bw, mx, my);
        menu.hits.add(new MenuHit(x, y, bw, Ui.BUTTON_H, null, List.of(L.c("wynnlabs.discoveries.btn_pin_all")), "#discpin:0:" + e.id()));
        int bx = x + bw + Theme.SP_2;
        int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        if (!done && Wyn.knowsKind("SECRET_DISCOVERY")) {
            boolean on = e.name().equals(QuestsData.tracked());
            String t = L.t(on ? "wynnlabs.quests.btn_tracked" : "wynnlabs.quests.btn_track");
            int tw = Ui.iconChipWidth(font(), "bookmark", t, false);
            Ui.iconChip(g, font(), "bookmark", t, false, bx, cy, tw, on, mx, my);
            menu.hits.add(new MenuHit(bx, cy, tw, Ui.CHIP_H, null, null, "#qctrack:SECRET_DISCOVERY\n" + e.name()));
            bx += tw + Theme.SP_2;
        }
        boolean byBook = Discoveries.foundByWynntils(e, QuestsSecrets.found());
        String t = L.t("wynnlabs.discoveries.btn_done");
        int tw = Ui.iconChipWidth(font(), "check", t, false);
        if (bx + tw <= x + w) {
            Ui.iconChip(g, font(), "check", t, false, bx, cy, tw, done, mx, my);
            List<Component> tip = List.of(L.c(byBook ? "wynnlabs.discoveries.found_by_book" : done ? "wynnlabs.discoveries.btn_undo"
                    : "wynnlabs.discoveries.ticked").withStyle(ChatFormatting.GRAY));
            menu.hits.add(new MenuHit(bx, cy, tw, Ui.CHIP_H, null, tip, byBook ? "#discfound" : "#disctick:" + e.id()));
            bx += tw + Theme.SP_2;
        }
        String wiki = L.t("wynnlabs.content.wiki");
        int ww = Ui.iconChipWidth(font(), "arrow-up-right", wiki, false);
        if (bx + ww <= x + w) {
            Ui.iconChip(g, font(), "arrow-up-right", wiki, false, bx, cy, ww, false, mx, my);
            menu.hits.add(new MenuHit(bx, cy, ww, Ui.CHIP_H, null, List.of(L.c("wynnlabs.discoveries.btn_wiki"),
                    L.c("wynnlabs.discoveries.licence").withStyle(ChatFormatting.DARK_GRAY)), e.wiki()));
        }
    }

    private void tip(int x, int y, int w, int h, List<Component> lines) {
        menu.hits.add(new MenuHit(x, y, w, h, null, lines, null));
    }

    /** The wheel over the steps: a step a notch. */
    boolean scrolled(double x, double y, double v) {
        if (stepRect == null || !in(x, y, stepRect)) {
            return false;
        }
        stepScroll = Math.max(0, stepScroll - (int) Math.signum(v));
        return true;
    }
}
