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
                case SECRET_DISCOVERY -> secretsLeft();
                default -> {
                    int level = Wyn.level();
                    yield (int) lines(type).stream().filter(l -> !l.done() && l.level() <= level).count();
                }
            };
        });
    }

    /** The secret discoveries left within your level, as the discoveries page counts them. */
    private static int secretsLeft() {
        var gs = io.github.anilken.wynnlabs.game.GameState.current();
        java.util.Set<String> found = io.github.anilken.wynnlabs.quests.Discoveries.foundInGame();
        int n = 0;
        for (var e : io.github.anilken.wynnlabs.quests.Discoveries.all()) {
            if (e.minLevel() <= gs.level() && !io.github.anilken.wynnlabs.quests.Discoveries.done(e, found, gs.characterId())) {
                n++;
            }
        }
        return n;
    }

    // ---- the chip and its menu ----

    /** The type chip at x, y; returns its width. */
    int chip(GuiGraphics g, int x, int y, int mx, int my) {
        ContentType t = tab.questType;
        int cw = Ui.iconChipWidth(font(), t.icon, t.shown, true);
        Ui.iconChip(g, font(), t.icon, t.shown, true, x, y, cw, tab.typeOpen, mx, my);
        menu.hits.add(new MenuHit(x, y, cw, Ui.CHIP_H, null, null, "#qtypes"));
        chip = new int[] {x, y, cw, Ui.CHIP_H};
        return cw;
    }

    /** The menu under the chip: the kinds in the game's order, an icon, how many are left. */
    int[] menu(GuiGraphics g, int mx, int my) {
        if (chip == null) {
            return null;
        }
        // rows as tall as the window lets them be (a small window: down to a body line), never past its foot
        int fixed = 2 * Theme.SP_2 + BREAK_AFTER.size() * (2 * Theme.SP_1 + 1);
        int room = menu.height - Theme.SP_5 - (chip[1] + chip[3] + Theme.SP_1) - fixed;
        int row = Math.max(Type.BODY.lineHeight, Math.min(QuestsRail.ROW, room / ContentType.values().length));
        int w = 0;
        for (ContentType t : ContentType.values()) {
            w = Math.max(w, Type.LABEL.width(font(), t.shown));
        }
        w += 2 * Theme.SP_3 + 8 + Theme.SP_2 + Theme.SP_5 + Type.META.width(font(), "000");
        int gap = 2 * Theme.SP_1 + 1;
        int h = 2 * Theme.SP_2 + ContentType.values().length * row + BREAK_AFTER.size() * gap;
        int x = chip[0];
        int y = chip[1] + chip[3] + Theme.SP_1;
        int[] r = {x, y, w, h};
        Kit.softCard(g, x, y, w, h);
        int ry = y + Theme.SP_2;
        for (ContentType t : ContentType.values()) {
            boolean on = t == tab.questType;
            boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + row;
            if (on) {
                Kit.rowLit(g, x + Theme.SP_1, ry, w - 2 * Theme.SP_1, row);
            } else if (hover) {
                g.fill(x + 1, ry, x + w - 1, ry + row, Theme.CARD_HOVER);
            }
            int ty = ry + (row - Type.BODY.lineHeight) / 2;
            Art.icon(g, t.icon, x + Theme.SP_3, Type.BODY.onCaps(ty, 8), 8, on ? Theme.INK : Theme.INK_3);
            (on ? Type.LABEL : Type.BODY).draw(g, font(), t.shown, x + Theme.SP_3 + 8 + Theme.SP_2, ty);
            List<Component> tip = t == ContentType.SECRET_DISCOVERY ? List.of(L.c("wynnlabs.content.secret_tab").withStyle(ChatFormatting.GRAY)) : null;
            Type.META.drawRight(g, font(), String.valueOf(count(t)), x + w - Theme.SP_3, ty + 1, Theme.INK_3);
            menu.hits.add(new MenuHit(x, ry, w, row, null, tip, "#qtype:" + t.name()));
            ry += row;
            if (BREAK_AFTER.contains(t)) {
                Kit.hair(g, x + Theme.SP_3, ry + Theme.SP_1, w - 2 * Theme.SP_3, 1, Theme.LINE);
                ry += gap;
            }
        }
        return r;
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
            Art.icon(g, l.type().icon, mark, Type.BODY.onCaps(ty, 8), 8, Theme.INK_3);
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
        int rw = right.isEmpty() ? 0 : Type.META.width(font(), right) + Theme.SP_3;
        if (!right.isEmpty()) {
            Type.META.drawRight(g, font(), right, x + w - Theme.SP_3, ty + 1, Theme.INK_3);
        }
        int tx = mark + 8 + Theme.SP_2;
        Type role = picked ? Type.LABEL : l.done() ? Type.MUTED : Type.BODY;
        int ink = picked ? Theme.GOOD : role.colour;
        role.draw(g, font(), role.fit(font(), l.name(), x + w - Theme.SP_3 - rw - tx), tx, ty, ink);
        menu.hits.add(new MenuHit(x, y, w, rowH, null, tip(l), "#qcsel:" + l.type().name() + "\n" + l.name()));
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
            tab.filterOpen = false;
        } else if (c.startsWith("#qtype:")) {
            tab.typeOpen = false;
            ContentType t = ContentType.of(c.substring(7));
            if (t == ContentType.SECRET_DISCOVERY) {
                menu.openTab(RoadmapScreen.TAB_DISCOVERIES);   // its own page, with the guides
            } else if (t != tab.questType) {
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
