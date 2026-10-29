package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.coach.Today;
import io.github.anilken.wynnlabs.coach.XpMeter;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Today tab, laid out by what a player opens it for each day: a card for the day (this
 * character's daily objectives with their bars, the reward, the other characters' days), then two
 * quiet columns: what to do now ({@link TodayRows#todo}, each with its one action) and what is next
 * ({@link TodayRows#next}), the professions and the last session. The reset time and this session's
 * XP sit on the title row. One column and a scroll when the window is small.
 */
final class TodayTab extends MenuTab {

    TodayTab(RoadmapScreen menu) {
        super(menu);
    }

    private TodayRows rows;
    /** The page's scroll (logical px) and its bar. */
    private int scroll;
    private final ScrollBar bar = new ScrollBar();
    private int[] pageRect;
    private int contentH;
    private List<Today.Day> days = List.of();
    private long daysAt;

    /** What the rows read, again on the next frame (a rule they follow just changed). */
    void refreshNow() {
        if (rows != null) {
            rows.stale();
        }
    }

    private TodayRows rows() {
        if (rows == null) {
            rows = new TodayRows(menu);
        }
        return rows;
    }

    /** Every character's day: files, so read every two seconds, not every frame. */
    List<Today.Day> days() {
        if (System.currentTimeMillis() - daysAt > 2000) {
            days = Today.all(Minecraft.getInstance());
            daysAt = System.currentTimeMillis();
        }
        return days;
    }

    // ---- the title row ----

    /** On the title row's right: the time to the daily reset and this session's XP. */
    @Override
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        boolean compact = menu.layout.compact();
        long m = Today.minutesToReset();
        double rate = XpMeter.sessionRate();
        String xp = "+" + QuestAdvisor.fmt(XpMeter.sessionXp()) + " XP"
                + (rate > 0 && !compact ? L.t("wynnlabs.today.session_rate", QuestAdvisor.fmt(Math.round(rate))) : "");
        int by = y - 3 + (Ui.CHIP_H - Ui.SEAL_H) / 2;
        int sw = Type.META.width(font(), xp) + 2 * Theme.SP_2;
        int r = right - sw;
        Ui.seal(g, font(), xp, r, by, Theme.SEAL_2);
        menu.hits.add(new MenuHit(r, by, sw, Ui.SEAL_H, null, List.of(L.c("wynnlabs.today.session_tip").withStyle(ChatFormatting.GRAY)), null));
        r -= Theme.SP_3;
        String reset = compact ? L.t("wynnlabs.today.reset_short", m / 60, m % 60) : L.t("wynnlabs.today.reset_chip", m / 60, m % 60);
        int rw = Type.META.width(font(), reset);
        if (right - r + rw + 8 + Theme.SP_1 <= room) {
            int ty = Type.META.yIn(y - 3, Ui.CHIP_H);
            Type.META.draw(g, font(), reset, r - rw, ty, Theme.INK_3);
            Art.icon(g, "clock", r - rw - Theme.SP_1 - 8, Type.META.onCaps(ty, 8), 8, Theme.INK_3);
            r -= rw + Theme.SP_1 + 8;
        }
        return right - r;
    }

    // ---- the page ----

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        boolean compact = menu.layout.compact();
        int barW = ScrollBar.HIT;
        int cw = contentH > h ? w - barW - Theme.SP_2 : w;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - h)));
        pageRect = new int[] {x, y, w, h};
        int hitsFrom = menu.hits.size();
        g.enableScissor(x - Theme.SP_2, y, x + w, y + h);
        int top = y - scroll;
        int cy = top;
        List<Today.Day> all = days();
        String today = Today.utcDate();
        Today.Day mine = all.isEmpty() || !today.equals(all.get(0).date()) || all.get(0).objectives().isEmpty() ? null : all.get(0);
        TodayRows r = rows();
        r.refresh();
        if (mine == null) {
            cy += r.unread(g, x, cy, cw, mx, my) + Theme.SP_6;
        } else {
            cy += r.dayCard(g, x, cy, cw, mine, all, compact, mx, my) + Theme.SP_6;
        }
        if (compact) {
            cy = r.todo(g, x, cy, cw, mine, mx, my) + Theme.SP_5;
            cy = r.next(g, x, cy, cw, mx, my) + Theme.SP_5;
            cy = r.professions(g, x, cy, cw, 3) + Theme.SP_5;
            cy = r.last(g, x, cy, cw, mx, my);
        } else {
            int lw = (cw - Theme.SP_7) / 2;
            int rx = x + lw + Theme.SP_7;
            int rw = cw - lw - Theme.SP_7;
            int col = cy;
            int left = r.todo(g, x, col, lw, mine, mx, my);
            int ry = r.next(g, rx, col, rw, mx, my) + Theme.SP_5;
            ry = r.professions(g, rx, ry, rw, 4) + Theme.SP_5;
            ry = r.last(g, rx, ry, rw, mx, my);
            int bottom = Math.max(left, ry);
            Kit.hair(g, x + lw + Theme.SP_7 / 2, col, 1, Math.min(bottom, y + h) - col, Theme.LINE);
            cy = bottom;
        }
        g.disableScissor();
        contentH = cy - top;
        // what scrolled out of the page takes no click and shows no tooltip
        menu.hits.subList(hitsFrom, menu.hits.size()).removeIf(hb -> hb.y() + hb.h() <= y || hb.y() >= y + h);
        if (contentH > h) {
            int step = Theme.SP_6;
            bar.place(x + w - barW + Theme.SP_1, y, h, Math.max(1, h / step), Math.max(1, contentH / step), scroll / step);
            bar.draw(g, mx, my);
        }
    }

    @Override
    Popup popup() {
        return rows().othersChips;
    }

    /** "+N": every other character in a list. */
    boolean action(String c) {
        if (c.equals("#qtodayothers")) {
            rows().othersChips.toggle();
            return true;
        }
        return false;
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        if (pageRect != null && in(x, y, pageRect)) {
            scroll = Math.max(0, scroll - (int) Math.signum(v) * Theme.SP_7 * 2);
        }
    }

    @Override
    boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (pageRect != null && bar.mouseClicked(event.x(), event.y())) {
            scroll = bar.offset() * Theme.SP_6;
            return true;
        }
        return false;
    }

    @Override
    boolean barDragged(double x, double y) {
        if (rows().othersChips.drag(y)) {
            return true;
        }
        if (bar.mouseDragged(y)) {
            scroll = bar.offset() * Theme.SP_6;
            return true;
        }
        return false;
    }

    @Override
    void barReleased() {
        rows().othersChips.release();
        bar.mouseReleased();
    }

    /** The rail: today's daily tasks not done yet on this character. */
    @Override
    MenuRail.Badge badge() {
        List<Today.Day> all = Today.all(Minecraft.getInstance());
        if (all.isEmpty() || !Today.utcDate().equals(all.get(0).date())) {
            return null;
        }
        long left = all.get(0).objectives().stream().filter(o -> o.max() > 0 && o.score() < o.max()).count();
        return left == 0 ? null
                : new MenuRail.Badge(MenuRail.Badge.Kind.COUNT, String.valueOf(left), L.t("wynnlabs.screen.rail_badge_today", left));
    }

    /** For the rows: a tooltip-only area. */
    static List<Component> tip(String text) {
        return List.of(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
