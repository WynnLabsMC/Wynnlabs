package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.Content;
import io.github.anilken.wynnlabs.quests.ContentType;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The right side for a kind with no stages (a cave, a dungeon, a raid, a discovery): the name with
 * its kind, level and difficulty, the description, "Go to" on a soft card with the place and
 * "Yol ›", what it needs and gives, Wynntils' map around it, then Pusula and Takip et.
 */
final class QuestsPlacePane extends MenuPaint {

    private final QuestsTab tab;

    QuestsPlacePane(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    /** The picked line of the picked kind, or null when it is gone from the list. */
    private Content.Line picked() {
        ContentType t = tab.pickedType;
        if (t == null) {
            return null;
        }
        for (Content.Line l : QuestsTypes.lines(t)) {
            if (l.name().equals(tab.questSelected)) {
                return l;
            }
        }
        return null;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        Content.Line l = picked();
        if (l == null) {
            return;
        }
        int bottom = y + h;
        int foot = bottom - Ui.BUTTON_H;
        Content.Place p = l.place();
        int[] at = p == null ? null : new int[] {p.x(), p.y(), p.z()};
        // the title row: the kind's icon, the name, then kind · level · difficulty (· done)
        Art.icon(g, l.type().icon, x, Type.TITLE.onCaps(y, 10), 10, Theme.INK_2);
        int tx = x + 10 + Theme.SP_2;
        String name = Type.TITLE.fit(font(), l.name(), w - (tx - x));
        Type.TITLE.draw(g, font(), name, tx, y, Theme.LINK);
        List<String> facts = new ArrayList<>();
        facts.add(l.type().label());
        if (l.level() > 0) {
            facts.add("Lv " + l.level());
        }
        if (p != null && !p.difficulty().isEmpty()) {
            facts.add(L.t(switch (p.difficulty()) {
                case "hard" -> "wynnlabs.quests.diff_hard";
                case "easy" -> "wynnlabs.quests.diff_easy";
                default -> "wynnlabs.quests.diff_medium";
            }));
        }
        if (l.done()) {
            facts.add(L.t("wynnlabs.content.done"));
        }
        int fx = tx + Type.TITLE.width(font(), name) + Theme.SP_4;
        if (fx < x + w) {
            Type.META.draw(g, font(), Type.META.fit(font(), String.join(" · ", facts), x + w - fx), fx,
                    y + Type.TITLE.lineHeight - Type.META.lineHeight - 1, Theme.INK_2);
        }
        int cy = y + Type.TITLE.lineHeight + Theme.SP_2;
        String desc = describe(l);
        if (!desc.isEmpty()) {
            int n = QuestsGuideFacts.wrapped(g, font(), desc, x, cy, w, Theme.INK_2);
            cy += n * Type.META.lineHeight + Theme.SP_4;
        }
        if (at != null) {
            cy = card(g, x, cy, w, l, at, mx, my) + Theme.SP_4;
        }
        // what it asks and gives
        int level = Wyn.level();
        if (l.level() > 0 && !l.done()) {
            String need = L.t("wynnlabs.content.needs_level", l.level(), level);
            Type.META.draw(g, font(), Type.META.fit(font(), need, w), x, cy, l.level() > level ? Theme.WARN : Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_1;
        }
        if (p != null && !p.rewards().isEmpty()) {
            String rw = L.t("wynnlabs.content.rewards", String.join(" · ", p.rewards()));
            Type.META.draw(g, font(), Type.META.fit(font(), rw, w), x, cy, Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_1;
        }
        cy += Theme.SP_4;
        // the map: this place and the same kind's open ones around it
        if (at != null) {
            List<QuestMap.Place> places = around(l);
            int mh = foot - Theme.SP_5 - cy;
            int minH = menu.layout.compact() ? 48 : 72;
            if (mh >= minH && QuestMap.available(places)) {
                tab.map.draw(g, x, cy, w, mh, places, l.name(), !menu.layout.compact(), "c|" + l.type() + "|" + l.name(), mx, my);
            }
        }
        buttons(g, x, foot, w, l, at, mx, my);
    }

    /** The description without the coordinates the card already shows ("… at [x, y, z]"). */
    private static String describe(Content.Line l) {
        String d = l.description() == null ? "" : l.description().strip();
        d = d.replaceAll(",?\\s*((found|located)\\s+)?((at|around|near)\\s+)?\\[-?\\d+, ?-?\\d+, ?-?\\d+\\]", "");
        return d.replaceAll("\\s+", " ").replaceAll("\\s+([.,;])", "$1").strip();
    }

    /** "Go to …" in LEAD on a soft card, the block under it with its pin, "Yol ›" and the distance. */
    private int card(GuiGraphics g, int x, int y, int w, Content.Line l, int[] at, int mx, int my) {
        int pad = menu.layout.compact() ? Theme.SP_3 : Theme.SP_4;
        int goW = GuidePane.goW(font());
        int h = pad + Type.LEAD.lineHeight + Theme.SP_1 + Type.META.lineHeight + pad;
        Kit.softCard(g, x, y, w, h);
        String go = "/compass at " + at[0] + " " + at[1] + " " + at[2];
        String lead = L.t("wynnlabs.content.go_to", l.name());
        Type.LEAD.draw(g, font(), Type.LEAD.fit(font(), lead, w - 2 * pad - goW - Theme.SP_5), x + pad, y + pad);
        int ly = y + pad + Type.LEAD.lineHeight + Theme.SP_1;
        String where = at[0] + ", " + at[1] + ", " + at[2];
        Type.META.draw(g, font(), where, x + pad, ly, Theme.LINK);
        int ww = Type.META.width(font(), where);
        Art.icon(g, "map-pin", x + pad + ww + Theme.SP_1, Type.META.onCaps(ly, 8), 8, Theme.GOLD_EDGE);
        menu.hits.add(new MenuHit(x + pad, ly, ww + Theme.SP_1 + 8, Type.META.lineHeight, null,
                List.of(L.c("wynnlabs.quests.point_click")), go));
        int bx = x + w - pad - goW;
        int by = y + pad + (Type.LEAD.lineHeight - Ui.SMALL_H) / 2;
        Ui.small(g, font(), L.t("wynnlabs.guide.go"), bx, by, goW, mx, my);
        menu.hits.add(new MenuHit(bx, by, goW, Ui.SMALL_H, null, null, go));
        String dist = QuestsData.distance(at);
        if (dist != null) {
            Type.META.draw(g, font(), dist, bx + (goW - Type.META.width(font(), dist)) / 2, by + Ui.SMALL_H + Theme.SP_1, Theme.INK_3);
        }
        return y + h + 2;
    }

    /** The map's places: this one (current) and up to four open ones of its kind within 600 blocks. */
    private static List<QuestMap.Place> around(Content.Line l) {
        return QuestsCache.kept("around|" + l.type() + "|" + l.name(), 2000, () -> {
            Content.Place p = l.place();
            List<QuestMap.Place> out = new ArrayList<>();
            out.add(new QuestMap.Place(l.name(), new int[] {p.x(), p.y(), p.z()}, l.name(), QuestMapLayout.NOW));
            QuestsTypes.lines(l.type()).stream()
                    .filter(o -> o.place() != null && !o.done() && !o.name().equals(l.name()))
                    .filter(o -> Math.hypot(o.place().x() - p.x(), o.place().z() - p.z()) < 600)
                    .sorted(Comparator.comparingDouble(o -> Math.hypot(o.place().x() - p.x(), o.place().z() - p.z())))
                    .limit(4)
                    .forEach(o -> out.add(new QuestMap.Place(o.name(), new int[] {o.place().x(), o.place().y(), o.place().z()}, o.name(),
                            QuestMapLayout.NEXT)));
            return out;
        });
    }

    /** "Pusula ›" (the main action) and "Takip et" when Wynntils can track this kind. */
    private void buttons(GuiGraphics g, int x, int y, int w, Content.Line l, int[] at, int mx, int my) {
        int bx = x;
        if (at != null) {
            String t = L.t("wynnlabs.content.compass");
            int bw = Ui.iconButtonLabelWidth(font(), t);
            Ui.iconButtonLabel(g, font(), "compass", t, bx, y, bw, mx, my);
            menu.hits.add(new MenuHit(bx, y, bw, Ui.BUTTON_H, null, null, "/compass at " + at[0] + " " + at[1] + " " + at[2]));
            bx += bw + Theme.SP_2;
        }
        if (!l.done() && Wyn.knowsKind(l.type().name())) {
            int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
            boolean on = l.name().equals(QuestsData.tracked());
            String t = L.t(on ? "wynnlabs.quests.btn_tracked" : "wynnlabs.quests.btn_track");
            int tw = Ui.iconChipWidth(font(), "bookmark", t, false);
            if (bx + tw <= x + w) {
                Ui.iconChip(g, font(), "bookmark", t, false, bx, cy, tw, on, mx, my);
                menu.hits.add(new MenuHit(bx, cy, tw, Ui.CHIP_H, null, null, "#qctrack:" + l.type().name() + "\n" + l.name()));
            }
        }
    }
}
