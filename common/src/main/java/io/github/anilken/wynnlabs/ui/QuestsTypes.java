package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.Content;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The content book's kinds in the quests tab, as the game's filter: a type chip beside "Önerilen ▾"
 * with a menu in the game's order and names, each with how many are left; the rows of a non-quest
 * kind (a cave, a dungeon, a discovery) and how one is drawn in the rail.
 */
final class QuestsTypes extends MenuPaint {

    /** The menu's groups start after these: Recommended | quests | discoveries | places. */
    private static final List<ContentType> BREAK_AFTER = List.of(ContentType.RECOMMENDED, ContentType.MINI_QUEST,
            ContentType.TERRITORIAL_DISCOVERY);

    private final QuestsTab tab;
    /** The type chip this frame (the menu opens under it). */
    private int[] chip;

    QuestsTypes(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    // ---- the rows ----

    /** A kind's lines, kept a second (the map data and the book do not change while you look). */
    static List<Content.Line> lines(ContentType type) {
        return QuestsCache.kept("lines|" + type, 1000, () -> Content.lines(type));
    }

    /** A kind's rows: what you can do now nearest first, then the higher ones by level, then "Biten". */
    List<QuestsRail.Row> rows(ContentType type, String query) {
        if (type == ContentType.SECRET_DISCOVERY) {
            return tab.secrets.rows(query);
        }
        List<Content.Line> all = lines(type);
        String q = query.toLowerCase(Locale.ROOT);
        int level = Wyn.level();
        double[] at = here();
        List<Content.Line> open = new ArrayList<>();
        List<Content.Line> done = new ArrayList<>();
        for (Content.Line l : all) {
            if (!q.isEmpty() && !l.name().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            (l.done() ? done : open).add(l);
        }
        // what your level allows, nearest first; then the higher ones, lowest first
        open.sort(Comparator.<Content.Line>comparingInt(l -> l.level() > level ? 1 : 0)
                .thenComparingDouble(l -> l.level() > level || l.place() == null ? l.level() : far(l, at)));
        done.sort(Comparator.comparing(Content.Line::name));
        List<QuestsRail.Row> out = new ArrayList<>();
        for (Content.Line l : open) {
            out.add(new QuestsRail.Row(l.name(), null, null, l));
        }
        if (!done.isEmpty()) {
            out.add(new QuestsRail.Row(null, L.t("wynnlabs.quests.done_head", done.size()), null, null));
            for (Content.Line l : done) {
                out.add(new QuestsRail.Row(l.name(), null, null, l));
            }
        }
        return out;
    }

    /** Recommended: the advisor's quests with the places for your level among them (two quests, a place). */
    static List<QuestsRail.Row> mix(List<QuestsRail.Row> quests) {
        List<Content.Line> near = near();
        List<QuestsRail.Row> out = new ArrayList<>();
        int p = 0;
        int n = 0;
        for (QuestsRail.Row r : quests) {
            if (r.head() != null && p < near.size()) {
                while (p < near.size()) {
                    Content.Line l = near.get(p++);
                    out.add(new QuestsRail.Row(l.name(), null, null, l));
                }
            }
            out.add(r);
            if (r.head() == null && ++n % 2 == 0 && p < near.size()) {
                Content.Line l = near.get(p++);
                out.add(new QuestsRail.Row(l.name(), null, null, l));
            }
        }
        while (p < near.size()) {
            Content.Line l = near.get(p++);
            out.add(new QuestsRail.Row(l.name(), null, null, l));
        }
        return out;
    }

    /** The places the advisor adds for your level: caves, dungeons and boss altars near you. */
    static List<Content.Line> near() {
        return QuestsCache.kept("near", 2000, () -> {
            List<Content.Line> places = new ArrayList<>();
            for (ContentType t : List.of(ContentType.CAVE, ContentType.DUNGEON, ContentType.BOSS_ALTAR)) {
                places.addAll(lines(t));
            }
            double[] at = here();
            return Content.near(places, Wyn.level(), at[0], at[1], 6);
        });
    }

    private static double[] here() {
        var p = Minecraft.getInstance().player;
        return p == null ? new double[] {0, 0} : new double[] {p.getX(), p.getZ()};
    }

    private static double far(Content.Line l, double[] at) {
        return Math.hypot(l.place().x() - at[0], l.place().z() - at[1]);
    }

    /** How many are left of a kind: not done and within your level (quests: the order's list). */
    int count(ContentType type) {
        return QuestsCache.kept("count|" + type + "|" + tab.questFilter, 2000, () -> {
            Map<String, List<QuestAdvisor.Suggestion>> lists = tab.lists();
            return switch (type) {
                case RECOMMENDED -> lists.getOrDefault("RECOMMENDED", List.of()).size() + near().size();
                case QUEST -> (int) lists.getOrDefault("RECOMMENDED", List.of()).stream()
                        .filter(s -> !"MINI_QUEST".equals(s.quest().type())).count();
                case MINI_QUEST -> lists.getOrDefault("MINI", List.of()).size();
                case SECRET_DISCOVERY -> QuestsSecrets.left();
                default -> {
                    int level = Wyn.level();
                    yield (int) lines(type).stream().filter(l -> !l.done() && l.level() <= level).count();
                }
            };
        });
    }


    // ---- the chip and its menu ----

    /** The type chip at x, y; returns its width. */
    int chip(GuiGraphics g, int x, int y, int mx, int my) {
        ContentType t = tab.questType;
        int cw = UiMenu.chip(g, font(), t.icon, t.shown, x, y, tab.typeOpen, mx, my);
        menu.hits.add(new MenuHit(x, y, cw, Ui.CHIP_H, null, null, "#qtypes"));
        chip = new int[] {x, y, cw, Ui.CHIP_H};
        return cw;
    }

    /** The menu under the chip: the kinds in the game's order, an icon, how many are left. */
    int[] menu(GuiGraphics g, int mx, int my) {
        if (chip == null) {
            return null;
        }
        UiMenu.Items items = new UiMenu.Items();
        for (ContentType t : ContentType.values()) {
            items.add(UiMenu.Item.of("#qtype:" + t.name(), t.icon, t.shown, String.valueOf(count(t)), t == tab.questType));
            if (BREAK_AFTER.contains(t)) {
                items.divide();
            }
        }
        return UiMenu.draw(g, font(), UiMenu.Spec.under(chip, menu.width, menu.height).rows(ContentType.values().length), tab.typeMenu,
                items.list, mx, my, r -> menu.hits.add(new MenuHit(r.x(), r.y(), r.w(), r.h(), null, r.item().tip(), r.item().id())));
    }

    // ---- a row of a non-quest kind ----

    /** A place or discovery in the rail: its kind's icon (a tick when done), its name, where it is. */
    void row(GuiGraphics g, QuestsRail.Row r, int x, int y, int w, int mx, int my) {
        Content.Line l = r.line();
        int rowH = QuestsRail.ROW;
        boolean picked = l.name().equals(tab.questSelected) && l.type() == tab.pickedType;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + rowH;
        if (picked) {
            Kit.rowLit(g, x, y, w, rowH);
            g.fill(x, y + Theme.SP_2, x + 2, y + rowH - Theme.SP_2, Theme.GOLD_DEEP);
        } else if (hover) {
            g.fill(x, y, x + w, y + rowH, Theme.CARD_HOVER);
        }
        int ty = y + Theme.SP_2;
        int mark = x + Theme.SP_3;
        if (l.done()) {
            Art.icon(g, "check", mark, Type.BODY.onCaps(ty, 8), 8, Theme.GOOD);
        } else {
            Art.icon(g, l.type().icon, mark, Type.BODY.onCaps(ty, 8), 8, picked ? Theme.INK : Theme.INK_3);
        }
        String right = "";
        int level = Wyn.level();
        if (!l.done() && l.level() > level) {
            right = L.t("wynnlabs.quests.tag_levels_later", l.level() - level);
        } else if (!l.done() && l.place() != null) {
            String d = QuestsData.distance(new int[] {l.place().x(), l.place().y(), l.place().z()});
            right = d == null ? "Lv " + l.level() : d;
        } else if (!l.done() && l.level() > 0) {
            right = "Lv " + l.level();
        }
        // a secret discovery's row, hovered: a pin for the way to its first place (one click, no guide)
        boolean pin = hover && !l.done() && l.type() == ContentType.SECRET_DISCOVERY;
        if (pin) {
            int sq = Ui.CHIP_H + 2;
            int bx = x + w - Theme.SP_2 - sq;
            int by = y + (rowH - Ui.CHIP_H) / 2;
            Ui.iconButton(g, "map-pin", bx, by, false, mx, my);
            menu.hits.add(new MenuHit(bx, by, sq, Ui.CHIP_H, null,
                    List.of(L.c("wynnlabs.discoveries.pin_tip", l.name()).withStyle(ChatFormatting.LIGHT_PURPLE)), "#discpin:0:" + l.name()));
            right = "";
        }
        int rw = right.isEmpty() ? (pin ? Ui.CHIP_H + 2 + Theme.SP_3 : 0) : Type.META.width(font(), right) + Theme.SP_3;
        if (!right.isEmpty()) {
            Type.META.drawRight(g, font(), right, x + w - Theme.SP_3, ty + 1, Theme.INK_3);
        }
        int tx = mark + 8 + Theme.SP_2;
        Type role = picked ? Type.LABEL : l.done() ? Type.MUTED : Type.BODY;
        int ink = picked ? Theme.GOOD : role.colour;
        role.draw(g, font(), role.fit(font(), l.name(), x + w - Theme.SP_3 - rw - tx), tx, ty, ink);
        var e = l.type() == ContentType.SECRET_DISCOVERY ? QuestsSecrets.entry(l.name()) : null;
        menu.hits.add(new MenuHit(x, y, w, rowH, null, e != null ? QuestsSecrets.tip(e) : tip(l), "#qcsel:" + l.type().name() + "\n" + l.name()));
    }

    private static List<Component> tip(Content.Line l) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(l.name()).withStyle(ChatFormatting.AQUA).withStyle(UiFont::bold));
        tip.add(Component.literal(l.type().shown + (l.level() > 0 ? " · Lv " + l.level() : "")).withStyle(ChatFormatting.GRAY));
        if ("UNKNOWN".equals(l.status())) {
            tip.add(L.c("wynnlabs.content.not_read").withStyle(ChatFormatting.DARK_GRAY));
        }
        return tip;
    }

    // ---- the refresh ----

    /** The refresh's tooltip on a non-quest kind: what it reads, or why it cannot. */
    List<Component> rescanTip(ContentType t) {
        if (!Wyn.canRescanQuests() || !Wyn.knowsKind(t.name())) {
            return List.of(L.c("wynnlabs.content.rescan_off", t.shown).withStyle(ChatFormatting.GOLD));
        }
        return List.of(L.c(Content.scanning(t) ? "wynnlabs.content.rescanning" : "wynnlabs.content.rescan", t.shown).withStyle(ChatFormatting.GOLD),
                L.c("wynnlabs.quests.rescan_only_press").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** When the book was read for the shown kind (0: never). */
    long readAt() {
        return tab.questType.book() ? Content.readAt(tab.questType) : QuestBook.readAt();
    }

    // ---- the actions ----

    /** The kinds' clicks: the menu, a kind, a place picked, a place tracked; false when {@code c} is none. */
    boolean action(String c) {
        if (c.equals("#qtypes")) {
            tab.typeOpen = !tab.typeOpen;
            tab.typeMenu.reset();
            tab.filterOpen = false;
        } else if (c.startsWith("#qtype:")) {
            tab.typeOpen = false;
            ContentType t = ContentType.of(c.substring(7));
            if (t != tab.questType) {
                tab.questType = t;
                tab.questSelected = null;
                tab.pickedType = null;
                tab.railScroll = 0;
                tab.questDetailTab = 0;
            }
        } else if (c.startsWith("#qcsel:")) {
            String[] parts = c.substring(7).split("\n", 2);
            tab.pickedType = ContentType.of(parts[0]);
            tab.questSelected = parts.length > 1 ? parts[1] : null;
            tab.listOpen = false;
            tab.map.zoom = 1;
        } else if (c.startsWith("#qctrack:")) {
            String[] parts = c.substring(9).split("\n", 2);
            if (parts.length > 1) {
                menu.onClose();
                if (Wyn.startTracking(parts[1], parts[0])) {
                    Minecraft.getInstance().gui.setOverlayMessage(
                            L.c("wynnlabs.quests.now_tracking", parts[1]).withStyle(ChatFormatting.GREEN), false);
                }
            }
        } else {
            return false;
        }
        return true;
    }
}
