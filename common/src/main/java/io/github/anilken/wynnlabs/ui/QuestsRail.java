package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.Content;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestGuides;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The quests as the guide's stage rail: the order and filter as one "Önerilen ▾" chip, then one
 * row a quest (a mark, the name, the distance or the stage on the right; the picked one on a gold
 * wash), the finished ones under a "Biten" heading. Lit rows never touch: SP_2 of paper between.
 */
final class QuestsRail extends MenuPaint {

    /** A row: a body line with SP_2 inside the wash, top and bottom. */
    static final int ROW = Type.BODY.lineHeight + 2 * Theme.SP_2;
    /** A row's pitch: the row and SP_2 of plain paper before the next. */
    static final int PITCH = ROW + Theme.SP_2;
    private static final int HEAD = Theme.SP_3 + Type.META.lineHeight + Theme.SP_1;

    private final QuestsTab tab;
    /** The "Önerilen ▾" chip this frame (the menu opens under it). */
    private int[] chip;

    QuestsRail(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    /**
     * A line of the rail: a quest (its name, its suggestion when the advisor ranked it), a place or
     * discovery of another kind ({@code line}), or a heading.
     */
    record Row(String name, String head, QuestAdvisor.Suggestion s, Content.Line line) {
        Row(String name, String head, QuestAdvisor.Suggestion s) {
            this(name, head, s, null);
        }
    }

    /** The rail's rows: the search's matches, or the tracked quest, the filter's list, then the finished ones. */
    List<Row> rows() {
        ContentType t = tab.questType;
        return QuestsCache.kept("rows|" + t + "|" + tab.questFilter + "|" + tab.query() + "|" + QuestsData.tracked(), 500,
                () -> t.book() ? tab.types.rows(t, tab.query())
                        : t == ContentType.RECOMMENDED && tab.questFilter.equals("RECOMMENDED") && tab.query().isEmpty()
                        ? QuestsTypes.mix(rowsNow()) : rowsNow());
    }

    /** Whether a quest of the book belongs to the shown kind (Quest: no minis; Mini-Quest: only minis). */
    private boolean kind(String name) {
        ContentType t = tab.questType;
        if (t == ContentType.RECOMMENDED) {
            return true;
        }
        QuestBook.Entry e = QuestsData.entry(name);
        boolean mini = e != null && "MINI_QUEST".equals(e.type());
        return t == ContentType.MINI_QUEST ? mini : !mini;
    }

    /** The order's list for the kind (Mini-Quest on "Önerilen": the minis you can do). */
    private List<QuestAdvisor.Suggestion> listFor(Map<String, List<QuestAdvisor.Suggestion>> lists) {
        String f = tab.questType == ContentType.MINI_QUEST && tab.questFilter.equals("RECOMMENDED") ? "MINI" : tab.questFilter;
        return lists.getOrDefault(f, List.of());
    }

    private List<Row> rowsNow() {
        Map<String, List<QuestAdvisor.Suggestion>> lists = tab.lists();
        List<Row> out = new ArrayList<>();
        String q = tab.query().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            List<QuestBook.Entry> found = new ArrayList<>();
            for (QuestBook.Entry e : QuestBook.all()) {
                if (e.name().toLowerCase(Locale.ROOT).contains(q) && kind(e.name())) {
                    found.add(e);
                }
            }
            found.sort((a, b) -> a.level() != b.level() ? Integer.compare(a.level(), b.level()) : a.name().compareTo(b.name()));
            for (QuestBook.Entry e : found) {
                out.add(new Row(e.name(), null, suggestion(lists, e.name())));
            }
            return out;
        }
        String tracked = QuestsData.tracked();
        List<String> seen = new ArrayList<>();
        if (tracked != null && QuestsData.entry(tracked) != null && kind(tracked)) {
            out.add(new Row(tracked, null, suggestion(lists, tracked)));
            seen.add(tracked);
        }
        // the quests you started, then the rest of the filter's list
        List<QuestAdvisor.Suggestion> list = new ArrayList<>(listFor(lists));
        list.sort((a, b) -> Boolean.compare(!"STARTED".equals(a.quest().status()), !"STARTED".equals(b.quest().status())));
        for (QuestAdvisor.Suggestion s : list) {
            if (!seen.contains(s.quest().name()) && kind(s.quest().name())) {
                out.add(new Row(s.quest().name(), null, s));
                seen.add(s.quest().name());
            }
        }
        List<QuestAdvisor.Suggestion> done = lists.getOrDefault("DONE", List.of()).stream().filter(s -> kind(s.quest().name())).toList();
        if (!tab.questFilter.equals("DONE") && !done.isEmpty()) {
            out.add(new Row(null, L.t("wynnlabs.quests.done_head", done.size()), null));
            for (QuestAdvisor.Suggestion s : done) {
                out.add(new Row(s.quest().name(), null, s));
            }
        }
        return out;
    }

    private static QuestAdvisor.Suggestion suggestion(Map<String, List<QuestAdvisor.Suggestion>> lists, String name) {
        for (List<QuestAdvisor.Suggestion> l : lists.values()) {
            for (QuestAdvisor.Suggestion s : l) {
                if (s.quest().name().equals(name)) {
                    return s;
                }
            }
        }
        return null;
    }

    /** The rail in x, y, w, h: the order chip, then the rows from {@link QuestsTab#railScroll}. */
    void draw(GuiGraphics g, int x, int y, int w, int h, List<Row> rows, int mx, int my) {
        int top = y;
        if (tab.query().isEmpty()) {
            top += orderChip(g, x, y, w, rows, mx, my) + Theme.SP_4;
        } else {
            String n = rows.isEmpty() ? L.t("wynnlabs.quests.search_none", tab.query()) : L.n("wynnlabs.quests.count", rows.size());
            Type.META.draw(g, font(), Type.META.fit(font(), n, w), x + Theme.SP_2, top, Theme.INK_3);
            top += Type.META.lineHeight + Theme.SP_3;
        }
        if (tab.questType == ContentType.SECRET_DISCOVERY && tab.query().isEmpty() && QuestsSecrets.unread()) {
            top += unread(g, x, top, w, mx, my) + Theme.SP_4;
        }
        int bottom = y + h;
        tab.railRect = new int[] {x, top, w, bottom - top};
        if (tab.query().isEmpty() && rows.stream().noneMatch(r -> r.name() != null)) {
            Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), L.t("wynnlabs.quests.none_in_filter"), w), x + Theme.SP_2, top);
            return;
        }
        int fit = 0;
        int used = 0;
        for (int i = tab.railScroll; i < rows.size(); i++) {
            used += rows.get(i).head() != null ? HEAD : PITCH;
            if (used > bottom - top) {
                break;
            }
            fit++;
        }
        int total = 0;
        for (Row r : rows) {
            total += r.head() != null ? HEAD : PITCH;
        }
        if (total <= bottom - top) {
            tab.railScroll = 0;
        } else {
            tab.railScroll = Math.max(0, Math.min(tab.railScroll, rows.size() - Math.max(1, fit)));
        }
        int ry = top;
        for (int i = tab.railScroll; i < rows.size(); i++) {
            Row r = rows.get(i);
            int rh = r.head() != null ? HEAD : PITCH;
            if (ry + rh - (r.head() != null ? 0 : Theme.SP_2) > bottom) {
                break;
            }
            if (r.head() != null) {
                Type.META.draw(g, font(), r.head(), x + Theme.SP_2, ry + Theme.SP_3, Theme.INK_3);
            } else {
                row(g, r, x, ry, w, mx, my);
            }
            ry += rh;
        }
        // where in the list you are: a thin bar at the rail's right, dragged or clicked like any list's
        tab.railBar.place(x + w + Theme.SP_1, top, bottom - top, Math.max(1, fit), total <= bottom - top ? fit : rows.size(), tab.railScroll);
        tab.railBar.draw(g, mx, my);
    }

    /** The secret discoveries never read: an info notice with the content book's read in it (the why in its tooltip). */
    private int unread(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Ui.Action act = new Ui.Action("refresh", L.t("wynnlabs.content.read_book"));
        List<String> lines = List.of(L.t("wynnlabs.discoveries.book_unread"));
        int nh = Ui.noticeHeight(2);
        int[][] hit = new int[1][];
        nh = Ui.notice(g, font(), Ui.Level.INFO, lines, act, x, y, w, nh, mx, my, hit);
        List<Component> why = List.of(Component.literal(L.t("wynnlabs.discoveries.book_tip")).withStyle(ChatFormatting.GRAY));
        if (hit[0] != null) {
            menu.hits.add(new MenuHit(hit[0][0], hit[0][1], hit[0][2], hit[0][3], null, tab.types.rescanTip(ContentType.SECRET_DISCOVERY), "#qrescan:"));
        }
        menu.hits.add(new MenuHit(x, y, w, nh, null, why, null));
        return nh;
    }

    /** "Önerilen ▾" (the quest kinds only), the kind's chip, and how many are listed; returns its height. */
    private int orderChip(GuiGraphics g, int x, int y, int w, List<Row> rows, int mx, int my) {
        int cx = x;
        chip = null;
        boolean secret = tab.questType == ContentType.SECRET_DISCOVERY;
        if (tab.questType.quest() || secret) {
            String name = secret ? QuestsSecrets.sortName() : L.t(QuestAdvisor.FILTER_NAMES[filterIndex()]);
            // the order has the sort arrow, so it never reads like the kind's chip beside it
            int cw = UiMenu.chip(g, font(), "arrow-down", name, cx, y, tab.filterOpen, mx, my);
            menu.hits.add(new MenuHit(cx, y, cw, Ui.CHIP_H, null, null, "#qmenu"));
            chip = new int[] {cx, y, cw, Ui.CHIP_H};
            cx += cw + Theme.SP_2;
        }
        cx += tab.types.chip(g, cx, y, mx, my) + Theme.SP_3;
        // what is left: the rows before the "Biten" heading
        long n = 0;
        for (Row r : rows) {
            if (r.head() != null) {
                break;
            }
            n++;
        }
        String count = secret ? QuestsSecrets.progress() : L.n("wynnlabs.content.count", n);
        if (cx + Type.META.width(font(), count) <= x + w) {
            Type.META.draw(g, font(), count, cx, Type.META.yIn(y, Ui.CHIP_H), Theme.INK_3);
        }
        return Ui.CHIP_H;
    }

    private int filterIndex() {
        for (int i = 0; i < QuestAdvisor.FILTERS.length; i++) {
            if (QuestAdvisor.FILTERS[i].equals(tab.questFilter)) {
                return i;
            }
        }
        return 0;
    }

    /** One quest: its mark, its name, and on the right where it is (or the stage you are on). */
    private void row(GuiGraphics g, Row r, int x, int y, int w, int mx, int my) {
        if (r.line() != null) {
            tab.types.row(g, r, x, y, w, mx, my);
            return;
        }
        QuestBook.Entry q = QuestsData.entry(r.name());
        boolean picked = r.name().equals(tab.questSelected) && tab.pickedType == null;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + ROW;
        if (picked) {
            Kit.rowLit(g, x, y, w, ROW);
            g.fill(x, y + Theme.SP_2, x + 2, y + ROW - Theme.SP_2, Theme.GOLD_DEEP);
        } else if (hover) {
            g.fill(x, y, x + w, y + ROW, Theme.CARD_HOVER);
        }
        boolean done = q != null && "COMPLETED".equals(q.status());
        boolean started = r.name().equals(QuestsData.tracked()) || q != null && "STARTED".equals(q.status());
        int ty = y + Theme.SP_2;
        int mark = x + Theme.SP_3;
        if (done) {
            Art.icon(g, "check", mark, Type.BODY.onCaps(ty, 8), 8, Theme.GOOD);
        } else if (started) {
            Art.icon(g, "player-play", mark, Type.BODY.onCaps(ty, 8), 8, Theme.LINK);
        } else {
            // every row its kind's icon, as in the kind menu (a tick or ▷ in its place when done or started)
            ContentType kind = q != null && "MINI_QUEST".equals(q.type()) ? ContentType.MINI_QUEST : ContentType.QUEST;
            Art.icon(g, kind.icon, mark, Type.BODY.onCaps(ty, 8), 8, picked ? Theme.INK : Theme.INK_3);
        }
        // the right column: the stage you are on, the distance, or the levels still to go
        String right = "";
        int rightInk = Theme.INK_3;
        if (started && !done) {
            QuestWiki.Info info = QuestWiki.get(r.name());
            int st = QuestsData.stage(r.name(), info);
            if (info != null && st >= 0) {
                right = (st + 1) + "/" + info.stages().size();
                rightInk = Theme.LINK;
            }
        } else if (!done && q != null && Wyn.level() < q.level()) {
            right = L.t("wynnlabs.quests.tag_levels_later", q.level() - Wyn.level());
        } else if (!done && r.s() != null && r.s().distance() >= 0) {
            right = RoadmapScreen.dist(r.s().distance());
        }
        int rw = right.isEmpty() ? 0 : Type.META.width(font(), right) + Theme.SP_3;
        if (!right.isEmpty()) {
            Type.META.drawRight(g, font(), right, x + w - Theme.SP_3, ty + 1, rightInk);
        }
        int tx = mark + 8 + Theme.SP_2;
        Type role = picked ? Type.LABEL : done ? Type.MUTED : Type.BODY;
        int ink = started ? Theme.LINK : picked ? Theme.GOOD : role.colour;
        String name = QuestGuides.title(r.name());
        role.draw(g, font(), role.fit(font(), name, x + w - Theme.SP_3 - rw - tx), tx, ty, ink);
        menu.hits.add(new MenuHit(x, y, w, ROW, null, tip(r, q), "#qsel:" + r.name()));
    }

    /** A row's tooltip: the name, why it is suggested, its share of a level, missing professions. */
    private List<Component> tip(Row r, QuestBook.Entry q) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(QuestGuides.title(r.name())).withStyle(ChatFormatting.AQUA).withStyle(UiFont::bold));
        if (q != null) {
            String diff = "HARD".equals(q.difficulty()) ? L.t("wynnlabs.quests.diff_hard")
                    : "EASY".equals(q.difficulty()) ? L.t("wynnlabs.quests.diff_easy") : L.t("wynnlabs.quests.diff_medium");
            String mins = r.s() == null ? "" : " · " + L.t("wynnlabs.quests.about_minutes", QuestAdvisor.minutes(r.s()));
            tip.add(Component.literal("Lv " + q.level() + " · " + QuestAdvisor.lengthName(q) + " · " + diff + mins).withStyle(ChatFormatting.GRAY));
        }
        if (r.s() != null) {
            for (String reason : r.s().reasons()) {
                tip.add(Component.literal("• " + L.cap(reason)).withStyle(ChatFormatting.GRAY));
            }
        }
        if (q != null && q.xp() > 0) {
            tip.add(Component.literal(QuestAdvisor.fmt(q.xp()) + " XP · " + tab.share(q.xp())).withStyle(ChatFormatting.GOLD));
        }
        QuestWiki.Info info = q == null || "COMPLETED".equals(q.status()) ? null : QuestWiki.get(r.name());
        if (info != null) {
            Professions.missing(info.professions()).forEach((p, hn) -> tip.add(Component.literal("✖ " + Professions.name(p) + " " + hn[0]
                    + "/" + hn[1]).withStyle(ChatFormatting.RED)));
        }
        return tip;
    }

    // ---- the compact window ----

    /** Compact: the list as a menu chip, the quest you are on as a link (or the search box); returns its height. */
    int header(GuiGraphics g, int x, int y, int w, List<Row> rows, int mx, int my) {
        if (tab.searchOpen) {
            tab.searchField(g, x, y, w, mx, my);
            return Ui.CHIP_H;
        }
        String label = tab.questSelected == null ? L.t("wynnlabs.content.list") : tab.questSelected;
        int cw = Math.min(w / 2, Ui.iconChipWidth(font(), "list-check", label, true));
        Ui.iconChip(g, font(), "list-check", Type.BODY.fit(font(), label, cw - Ui.iconChipWidth(font(), "list-check", "", true)), true,
                x, y, cw, tab.listOpen, mx, my);
        menu.hits.add(new MenuHit(x, y, cw, Ui.CHIP_H, null, null, "#qlist"));
        int tcx = x + cw + Theme.SP_2;
        tcx += tab.types.chip(g, tcx, y, mx, my);
        String tracked = QuestsData.tracked();
        if (tracked != null && tab.questType.quest()) {
            // the quest you are on, one click back to it
            String on = L.t("wynnlabs.quests.started_link", QuestGuides.title(tracked));
            int lx = tcx + Theme.SP_2;
            int bare = Ui.iconChipWidth(font(), "player-play", "", false) + Theme.SP_1;
            int tw = Math.min(x + w - lx, Ui.iconChipWidth(font(), "player-play", on, false));
            if (tw >= bare + 24) {
                Ui.iconChip(g, font(), "player-play", Type.BODY.fit(font(), on, tw - bare), false,
                        lx, y, tw, false, mx, my);
                menu.hits.add(new MenuHit(lx, y, tw, Ui.CHIP_H, null, null, "#qsel:" + tracked));
            }
        }
        return Ui.CHIP_H;
    }

    /** Compact: the list over the pane, on a soft card. */
    int[] overlay(GuiGraphics g, int x, int y, int w, int h, List<Row> rows, int mx, int my) {
        int cw = Math.min(w, 260);
        int[] r = {x, y, cw, h};
        Kit.softCard(g, x, y, cw, h - 2);
        draw(g, x + Theme.SP_3, y + Theme.SP_3, cw - 2 * Theme.SP_3, h - 2 * Theme.SP_3 - 2, rows, mx, my);
        return r;
    }

    /** The "Önerilen ▾" menu under its chip: the orders and filters with their counts, then the session plan. */
    int[] menu(GuiGraphics g, int mx, int my) {
        if (chip == null) {
            return null;
        }
        if (tab.questType == ContentType.SECRET_DISCOVERY) {
            return UiMenu.draw(g, font(), UiMenu.Spec.under(chip, menu.width, menu.height), tab.orderMenu, QuestsSecrets.menuItems(), mx, my,
                    r -> menu.hits.add(new MenuHit(r.x(), r.y(), r.w(), r.h(), null, r.item().tip(), r.item().id())));
        }
        Map<String, List<QuestAdvisor.Suggestion>> lists = tab.lists();
        UiMenu.Items items = new UiMenu.Items();
        for (int i = 0; i < QuestAdvisor.FILTERS.length; i++) {
            String f = QuestAdvisor.FILTERS[i];
            items.add(UiMenu.Item.of("#qfilter:" + f, null, L.t(QuestAdvisor.FILTER_NAMES[i]),
                    String.valueOf(lists.getOrDefault(f, List.of()).size()), f.equals(tab.questFilter)));
        }
        List<QuestAdvisor.Suggestion> plan = plan(lists);
        if (!plan.isEmpty()) {
            List<Component> tip = new ArrayList<>();
            tip.add(L.c("wynnlabs.quests.plan_tip").withStyle(ChatFormatting.GRAY));
            int minutes = 0;
            long xp = 0;
            StringBuilder cmd = new StringBuilder("#qplan:");
            for (QuestAdvisor.Suggestion s : plan) {
                tip.add(Component.literal("• " + QuestGuides.title(s.quest().name()) + "  ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(QuestAdvisor.fmt(s.quest().xp()) + " XP").withStyle(ChatFormatting.GOLD)));
                minutes += QuestAdvisor.minutes(s);
                xp += s.quest().xp();
                cmd.append(s.quest().name()).append('\n');
            }
            tip.add(L.c("wynnlabs.quests.plan_sum", minutes, tab.share(xp)).withStyle(ChatFormatting.GREEN));
            items.divide().add(UiMenu.Item.of(cmd.toString(), null, L.t("wynnlabs.quests.plan_action", plan.size()), null, false)
                    .asLink().tip(tip));
        }
        return UiMenu.draw(g, font(), UiMenu.Spec.under(chip, menu.width, menu.height), tab.orderMenu, items.list, mx, my,
                r -> menu.hits.add(new MenuHit(r.x(), r.y(), r.w(), r.h(), null, r.item().tip(), r.item().id())));
    }

    /** This session's plan: the best safe quests near each other, nearest first, for about an hour. */
    static List<QuestAdvisor.Suggestion> plan(Map<String, List<QuestAdvisor.Suggestion>> lists) {
        List<QuestAdvisor.Suggestion> pool = new ArrayList<>();
        for (QuestAdvisor.Suggestion s : lists.getOrDefault("RECOMMENDED", List.of())) {
            if (s.quest().x() != null && !"HARD".equals(s.quest().difficulty()) && pool.size() < 8) {
                pool.add(s);
            }
        }
        List<QuestAdvisor.Suggestion> plan = new ArrayList<>();
        var p = Minecraft.getInstance().player;
        double sx = p == null ? 0 : p.getX();
        double sz = p == null ? 0 : p.getZ();
        int minutes = 0;
        while (!pool.isEmpty() && minutes < 60 && plan.size() < 3) {
            QuestAdvisor.Suggestion nearest = pool.get(0);
            double best = Double.MAX_VALUE;
            for (QuestAdvisor.Suggestion s : pool) {
                double d = Math.hypot(s.quest().x() - sx, s.quest().z() - sz);
                if (d < best) {
                    best = d;
                    nearest = s;
                }
            }
            pool.remove(nearest);
            plan.add(nearest);
            minutes += QuestAdvisor.minutes(new QuestAdvisor.Suggestion(nearest.quest(), 0, (int) best, List.of()));
            sx = nearest.quest().x();
            sz = nearest.quest().z();
        }
        return plan;
    }
}
