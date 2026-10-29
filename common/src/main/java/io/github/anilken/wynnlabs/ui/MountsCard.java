package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.MountRules;
import io.github.anilken.wynnlabs.mounts.MountStat;
import io.github.anilken.wynnlabs.mounts.MountStats;
import io.github.anilken.wynnlabs.mounts.MountType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mounts tab's Mounts section: the mounts read so far on the left (by type, best potential
 * first, a seal for one ready to breed) and the picked one's card on the right: its numbers, the
 * eight stats as bars (level, limit, max and the green the queue adds), the feed queue and what
 * the feed plan says. Small windows: the list is a row of chips over the card. No mount read yet:
 * the empty page with the compass to the nearest enclosure.
 */
final class MountsCard {

    private static final int LIST_W = 150;
    private static final int ICON = 16;
    private static final int NAME_COL = 72;      // the stat names' column (Acceleration fits)
    private static final int BAR_H = 4;
    /** The stats' bars measure against this (the highest a stat goes). */
    private static final int STAT_CAP = 115;

    private final MountsTab tab;
    private final MountsScroll listScroll = new MountsScroll();
    private final MountsScroll cardScroll = new MountsScroll();
    /** The picked mount's key ({@link io.github.anilken.wynnlabs.model.MountRead#key}); null: the first. */
    private String picked;
    private int chipFirst;

    MountsCard(MountsTab tab) {
        this.tab = tab;
    }

    private net.minecraft.client.gui.Font font() {
        return tab.font();
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<MountsModel.Owned> all = tab.model.owned();
        if (all.isEmpty()) {
            listScroll.hide();
            cardScroll.hide();
            empty(g, x, y, w, h, mx, my);
            return;
        }
        MountsModel.Owned pick = all.get(0);
        for (MountsModel.Owned o : all) {
            if (o.read().key().equals(picked)) {
                pick = o;
            }
        }
        if (tab.compact()) {
            listScroll.hide();
            int ch = chips(g, x, y, w, all, pick, mx, my);
            card(g, x, y + ch + Theme.SP_4, w, h - ch - Theme.SP_4, pick, mx, my);
            return;
        }
        list(g, x, y, LIST_W, h, all, pick, mx, my);
        int cx = x + LIST_W + Theme.SP_5;
        Kit.hair(g, cx - Theme.SP_3, y, 1, h, Theme.LINE);
        card(g, cx + Theme.SP_2, y, w - LIST_W - Theme.SP_5 - Theme.SP_2, h, pick, mx, my);
    }

    // ---- empty ----

    private void empty(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        MountsPlaces.Place near = MountsPlaces.nearest();
        List<String> steps = List.of(L.t("wynnlabs.mounts.empty_1"), L.t("wynnlabs.mounts.empty_2"), L.t("wynnlabs.mounts.empty_3"));
        UiEmpty.Action act = MountsPlaces.me() == null ? null
                : new UiEmpty.Action("compass", L.t("wynnlabs.mounts.empty_action", near.chip()), "compass");
        int[] b = UiEmpty.draw(g, font(), x, y, w, h, tab.compact(), "horse", L.t("wynnlabs.mounts.empty_title"), steps, act,
                (gg, gx, gy, gw, gh) -> ghost(gg, gx, gy, gw, gh), mx, my);
        if (b != null) {
            tab.compass(b[0], b[1], b[2], b[3], near.name(), near.at());
        }
    }

    /** The list and the stat bars to come, as faint outlines. */
    private static void ghost(GuiGraphics g, int x, int y, int w, int h) {
        int lw = Math.min(LIST_W, w / 3);
        int left = x + (w - lw - Theme.SP_6 - w / 2) / 2;
        int rowH = 2 * Type.BODY.lineHeight;
        for (int i = 0; i < 3 && (i + 1) * (rowH + Theme.SP_2) < h; i++) {
            int ry = y + i * (rowH + Theme.SP_2);
            g.fill(left, ry, left + lw, ry + rowH, 0x08000000);
            Kit.hair(g, left, ry, lw, 1, 0x289A855C);
            Kit.hair(g, left, ry + rowH - 1, lw, 1, 0x289A855C);
        }
        int bx = left + lw + Theme.SP_6;
        for (int i = 0; i < 6 && i * (Theme.SP_5 + BAR_H) < h; i++) {
            int by = y + Theme.SP_3 + i * (Theme.SP_5 + BAR_H);
            g.fill(bx, by, bx + w / 2, by + BAR_H, 0x08000000);
            Kit.hair(g, bx, by, w / 2, 1, 0x289A855C);
        }
    }

    // ---- the list ----

    private void list(GuiGraphics g, int x, int y, int w, int h, List<MountsModel.Owned> all, MountsModel.Owned pick,
                      int mx, int my) {
        int footH = Type.META.lineHeight + Type.LINK.lineHeight + Theme.SP_3 + 1;
        int lh = h - footH - Theme.SP_3;
        int cy = listScroll.begin(g, x, y, w, lh);
        int top = cy;
        int cw = listScroll.width(w);
        int rowH = Theme.SP_2 + Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_2;
        // the types you have, best mount first; then the locked ones
        List<MountType> types = new ArrayList<>();
        for (MountsModel.Owned o : all) {
            if (!types.contains(o.mount().type())) {
                types.add(o.mount().type());
            }
        }
        for (MountType t : MountType.values()) {
            if (!types.contains(t)) {
                types.add(t);
            }
        }
        for (MountType t : types) {
            List<MountsModel.Owned> mine = all.stream().filter(o -> o.mount().type() == t).toList();
            MountsPlaces.Unlock u = MountsPlaces.UNLOCKS.stream().filter(un -> un.type() == t).findFirst().orElse(null);
            boolean locked = mine.isEmpty() && u != null && !tab.model.unlocked(t, u.quest(), u.level());
            if (mine.isEmpty() && !locked) {
                continue;
            }
            Type.META.draw(g, font(), t.display(), x + Theme.SP_2, cy, Theme.INK_3);
            if (!mine.isEmpty()) {
                Type.META.drawRight(g, font(), String.valueOf(mine.size()), x + cw - Theme.SP_2, cy, Theme.INK_3);
            }
            cy += Type.META.lineHeight + Theme.SP_2;
            if (locked) {
                int ty = cy + Theme.SP_2;
                int nx = x + Theme.SP_2 + ICON + Theme.SP_3;
                Art.icon(g, "lock", x + Theme.SP_2 + (ICON - 8) / 2, Type.BODY.onCaps(ty, 8), 8, Theme.INK_3);
                Type.BODY.draw(g, font(), L.t("wynnlabs.mounts.locked"), nx, ty, Theme.INK_3);
                String q = Type.META.fit(font(), L.t("wynnlabs.mounts.unlock_line", u.level(), u.quest()), x + cw - nx);
                Type.META.draw(g, font(), q, nx, ty + Type.BODY.lineHeight, Theme.INK_3);
                cy += rowH + Theme.SP_3;
                continue;
            }
            for (MountsModel.Owned o : mine) {
                boolean on = o == pick;
                boolean hover = mx >= x && mx < x + cw && my >= cy && my < cy + rowH && listScroll.shows(my);
                if (on) {
                    Kit.rowLit(g, x, cy, cw, rowH);
                } else if (hover) {
                    g.fill(x, cy, x + cw, cy + rowH, Theme.CARD_HOVER);
                }
                int ty = cy + Theme.SP_2;
                MountsText.icon(g, t, x + Theme.SP_2, Type.BODY.onCaps(ty, ICON) + Theme.SP_2, ICON);
                int nx = x + Theme.SP_2 + ICON + Theme.SP_3;
                String seal = o.mount().breedReady() ? L.t("wynnlabs.mounts.ready") : fresh(o.mount()) ? L.t("wynnlabs.mounts.fresh") : null;
                int sw = seal == null ? 0 : Type.META.width(font(), seal) + 2 * Theme.SP_2;
                int right = x + cw - Theme.SP_2;
                if (seal != null) {
                    Ui.seal(g, font(), seal, right - sw, Type.BODY.onCaps(ty, Ui.SEAL_H),
                            o.mount().breedReady() ? Theme.SEAL_2 : Theme.INK_3);
                }
                Type.BODY.draw(g, font(), Type.BODY.fit(font(), o.mount().name(), right - sw - Theme.SP_2 - nx), nx, ty,
                        on ? Theme.INK : Theme.INK_2);
                Type.META.draw(g, font(), L.t("wynnlabs.mounts.potential_n", o.mount().potential()), nx, ty + Type.BODY.lineHeight,
                        Theme.INK_2);
                if (listScroll.shows(cy) || listScroll.shows(cy + rowH - 1)) {
                    String key = o.read().key();
                    tab.click(x, Math.max(cy, y), cw, Math.min(cy + rowH, y + lh) - Math.max(cy, y), () -> {
                        picked = key;
                        cardScroll.top();
                    });
                }
                cy += rowH + Theme.SP_2;
            }
            cy += Theme.SP_2;
        }
        listScroll.end(g, cy - top, mx, my);
        // the foot: a summary, the nearest enclosure as a link (the compass)
        int fy = y + h - footH;
        Kit.hair(g, x, fy, w, 1, Theme.LINE);
        fy += 1 + Theme.SP_3;
        long ready = all.stream().filter(o -> o.mount().breedReady()).count();
        long fresh = all.stream().filter(o -> fresh(o.mount())).count();
        Type.META.draw(g, font(), Type.META.fit(font(), L.n("wynnlabs.mounts.summary", all.size(), all.size(), ready, fresh), w), x, fy, Theme.INK_2);
        MountsPlaces.Place near = MountsPlaces.nearest();
        String link = Type.LINK.fit(font(), L.t("wynnlabs.mounts.nearest", near.chip()) + " ›", w);
        Type.LINK.draw(g, font(), link, x, fy + Type.META.lineHeight, Theme.LINK);
        tab.compass(x, fy + Type.META.lineHeight, Type.LINK.width(font(), link), Type.LINK.lineHeight, near.name(), near.at());
    }

    static boolean fresh(Mount m) {
        return m.stats().equals(MountStats.fresh());
    }

    /** Small windows: the mounts as a row of chips ("Azure 438"); returns its height. */
    private int chips(GuiGraphics g, int x, int y, int w, List<MountsModel.Owned> all, MountsModel.Owned pick, int mx, int my) {
        List<Ui.Tab> row = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < all.size(); i++) {
            Mount m = all.get(i).mount();
            String first = m.name().contains(" ") ? m.name().substring(0, m.name().indexOf(' ')) : m.name();
            row.add(new Ui.Tab(null, first + " " + m.potential()));
            if (all.get(i) == pick) {
                sel = i;
            }
        }
        int[][] hits = new int[all.size() + 2][];
        Ui.filterRow(g, font(), x, y, w, List.of(row), new int[] {sel}, chipFirst, mx, my, hits);
        for (int i = 0; i < all.size(); i++) {
            if (hits[i] != null) {
                String key = all.get(i).read().key();
                tab.click(hits[i][0], hits[i][1], hits[i][2], hits[i][3], () -> {
                    picked = key;
                    cardScroll.top();
                });
            }
        }
        if (hits[all.size()] != null) {
            int[] a = hits[all.size()];
            tab.click(a[0], a[1], a[2], a[3], () -> chipFirst = Math.max(0, chipFirst - 1));
        }
        if (hits[all.size() + 1] != null) {
            int[] a = hits[all.size() + 1];
            tab.click(a[0], a[1], a[2], a[3], () -> chipFirst = Math.min(all.size() - 1, chipFirst + 1));
        }
        return Ui.FILTER_H;
    }

    // ---- the card ----

    private void card(GuiGraphics g, int x, int y, int w, int h, MountsModel.Owned o, int mx, int my) {
        Mount m = o.mount();
        boolean compact = tab.compact();
        int cy = cardScroll.begin(g, x, y, w, h);
        int top = cy;
        int cw = cardScroll.width(w);
        // the title row's button (16 high) sits on the heading's capitals and reaches above its line:
        // SP_2 first, so it stays clear of the page's top edge and its clip
        cy += Theme.SP_2;
        // the title: the item, the name, where it was read; the state and the compass on the right
        int big = compact ? ICON : 24;
        MountsText.icon(g, m.type(), x, cy, big);
        int nx = x + big + Theme.SP_4;
        MountsPlaces.Place near = MountsPlaces.nearest();
        int right = x + cw;
        if (compact) {
            int bx = right - (Ui.CHIP_H + 2);
            Ui.iconButton(g, "compass", bx, Type.HEADING.onCaps(cy, Ui.CHIP_H), false, mx, my);
            if (cardScroll.shows(cy)) {
                tab.compass(bx, Type.HEADING.onCaps(cy, Ui.CHIP_H), Ui.CHIP_H + 2, Ui.CHIP_H, near.name(), near.at());
            }
            right = bx - Theme.SP_3;
        } else {
            String label = L.t("wynnlabs.mounts.compass_enclosure");
            int bw = Ui.iconButtonLabelWidth(font(), label);
            int by = Type.HEADING.onCaps(cy, Ui.BUTTON_H);
            Ui.iconButtonLabel(g, font(), "compass", label, right - bw, by, bw, mx, my);
            if (cardScroll.shows(by)) {
                tab.compass(right - bw, by, bw, Ui.BUTTON_H, near.name(), near.at());
            }
            right -= bw + Theme.SP_3;
        }
        if (m.breedReady()) {
            String s = L.t(compact ? "wynnlabs.mounts.ready" : "wynnlabs.mounts.ready_long");
            int sw = Type.META.width(font(), s) + 2 * Theme.SP_2;
            Ui.seal(g, font(), s, right - sw, Type.HEADING.onCaps(cy, Ui.SEAL_H), Theme.SEAL_2);
            right -= sw + Theme.SP_3;
        }
        Type.HEADING.draw(g, font(), Type.HEADING.fit(font(), m.name(), right - nx), nx, cy, Theme.INK);
        cy += Type.HEADING.lineHeight;
        if (!compact) {
            String where = m.type().display() + " · " + L.t("wynnlabs.mounts.where." + o.where().name().toLowerCase(java.util.Locale.ROOT),
                    MenuPaint.ago(o.at()));
            Type.META.draw(g, font(), Type.META.fit(font(), where, x + cw - nx), nx, cy + Theme.SP_1, Theme.INK_2);
            cy += Theme.SP_1 + Type.META.lineHeight;
        }
        cy += Theme.SP_4;
        cy += numbers(g, x, cy, cw, m, compact) + Theme.SP_5;
        cy += stats(g, x, cy, cw, m, mx, my) + Theme.SP_5;
        cy += queue(g, x, cy, cw, m) + Theme.SP_4;
        cy += plan(g, x, cy, cw, o, mx, my);
        cardScroll.end(g, cy - top, mx, my);
    }

    /** Potential, energy, average limit, feed timer, queue: a soft card of columns (one line when small). */
    private int numbers(GuiGraphics g, int x, int y, int w, Mount m, boolean compact) {
        MountsModel.Options opt = MountsModel.options();
        int queued = m.queue().pending().size();
        String[][] cols = {
            {L.t("wynnlabs.mounts.potential"), String.valueOf(m.potential()), L.t("wynnlabs.mounts.potential_sub")},
            {L.t("wynnlabs.mounts.energy"), o(m) == null ? "—" : o(m), L.t("wynnlabs.mounts.energy_sub")},
            {L.t("wynnlabs.mounts.avg_limit"), MountsText.one(m.averageLimit()), L.t("wynnlabs.mounts.avg_limit_sub")},
            {L.t("wynnlabs.mounts.feed_timer"), MountsText.dur(m.stats().feedTimerSec()), L.t("wynnlabs.mounts.feed_timer_sub")},
            {L.t("wynnlabs.mounts.queue"), queued + " / " + opt.queueSlots(), ""}};
        if (compact) {
            int cx = x;
            for (String[] c : cols) {
                int lw = Type.MUTED.width(font(), c[0] + " ");
                int vw = Type.LABEL.width(font(), c[1]);
                if (cx + lw + vw > x + w) {
                    break;
                }
                Type.MUTED.draw(g, font(), c[0] + " ", cx, y, Theme.INK_2);
                Type.LABEL.draw(g, font(), c[1], cx + lw, y, Theme.INK);
                cx += lw + vw + Theme.SP_4;
            }
            return Type.BODY.lineHeight;
        }
        int ch = 2 * Theme.SP_4 + Type.META.lineHeight + Type.HEADING.lineHeight + Type.META.lineHeight;
        Kit.softCard(g, x, y, w, ch);
        int colW = (w - 2 * Theme.SP_4) / cols.length;
        for (int i = 0; i < cols.length; i++) {
            int cx = x + Theme.SP_4 + i * colW;
            if (i > 0) {
                Kit.hair(g, cx - Theme.SP_3, y + Theme.SP_3, 1, ch - 2 * Theme.SP_3, Theme.LINE);
            }
            int cy = y + Theme.SP_4;
            int room = colW - Theme.SP_4;
            Type.META.draw(g, font(), Type.META.fit(font(), cols[i][0], room), cx, cy, Theme.INK_2);
            Type.HEADING.draw(g, font(), Type.HEADING.fit(font(), cols[i][1], room), cx, cy + Type.META.lineHeight, Theme.INK);
            Type.META.draw(g, font(), Type.META.fit(font(), cols[i][2], room), cx, cy + Type.META.lineHeight + Type.HEADING.lineHeight,
                    Theme.INK_2);
        }
        return ch;
    }

    /** "18 / 30" energy from the read, or null. */
    private String o(Mount m) {
        for (MountsModel.Owned own : tab.model.owned()) {
            if (own.mount() == m) {
                return own.read().energyMax() > 0 ? own.read().energy() + " / " + own.read().energyMax() : null;
            }
        }
        return null;
    }

    /** The eight stats: name, a bar as long as the stat's max (level dark, limit tan, the queue's gain green), the numbers. */
    private int stats(GuiGraphics g, int x, int y, int w, Mount m, int mx, int my) {
        int cy = head(g, x, y, w, L.t("wynnlabs.mounts.stats"), null);
        MountStats now = m.stats();
        MountStats after = m.queue().applyTo(now);
        int numW = Type.NUMBER.width(font(), "115 / 115 / 115");
        int bx = x + NAME_COL + Theme.SP_3;
        int bw = w - NAME_COL - Theme.SP_3 - numW - Theme.SP_5;
        for (MountStat s : MountStat.values()) {
            int i = s.ordinal();
            Type.BODY.draw(g, font(), s.display(), x, cy, Theme.INK);
            int by = Type.BODY.onCaps(cy, BAR_H);
            int track = Math.max(1, bw * now.max(i) / STAT_CAP);
            g.fill(bx, by, bx + track, by + BAR_H, 0x229A855C);
            Kit.hair(g, bx, by, track, 1, 0x809A855C);
            Kit.hair(g, bx, by + BAR_H - 1, track, 1, 0x809A855C);
            int lim = bw * now.limit(i) / STAT_CAP;
            int gain = bw * after.limit(i) / STAT_CAP;
            int lvl = bw * now.level(i) / STAT_CAP;
            g.fill(bx + lim, by, bx + gain, by + BAR_H, 0xFF6BAE5C);
            g.fill(bx, by, bx + lim, by + BAR_H, 0xFFB89A64);
            g.fill(bx, by, bx + lvl, by + BAR_H, Theme.INK_2);
            String nums = now.level(i) + " / " + now.limit(i) + " / " + now.max(i);
            Type.NUMBER.drawRight(g, font(), nums, x + w, cy, Theme.PRICE);
            if (my >= cy && my < cy + Type.BODY.lineHeight && mx >= x && mx < x + w) {
                List<Component> tip = new ArrayList<>(List.of(Component.literal(s.display()).withStyle(ChatFormatting.GOLD),
                        Component.literal(L.t("wynnlabs.mounts.stat_tip", now.level(i), now.limit(i), now.max(i))).withStyle(ChatFormatting.GRAY)));
                if (after.limit(i) > now.limit(i)) {
                    tip.add(Component.literal(L.t("wynnlabs.mounts.stat_queued", after.limit(i) - now.limit(i))).withStyle(ChatFormatting.GREEN));
                }
                tab.tip(x, cy, w, Type.BODY.lineHeight, tip);
            }
            cy += Type.BODY.lineHeight + Theme.SP_2;
        }
        return cy - Theme.SP_2 - y;
    }

    /** The feed queue's slots and when it ends; "not read" in its header while the queue is unknown. */
    private int queue(GuiGraphics g, int x, int y, int w, Mount m) {
        MountsModel.Options opt = MountsModel.options();
        List<Material> q = m.queue().pending();
        boolean known = !q.isEmpty() || m.queue().secondsLeft() > 0;
        int cy = head(g, x, y, w, L.t("wynnlabs.mounts.feed_queue"),
                known ? q.size() + " / " + opt.queueSlots() : L.t("wynnlabs.mounts.not_read"));
        int slot = 18;
        for (int i = 0; i < opt.queueSlots(); i++) {
            int sx = x + i * (slot + Theme.SP_2);
            if (sx + slot > x + w) {
                break;
            }
            Kit.slot(g, sx, cy, slot, 0);
            if (i < q.size()) {
                MountsText.item(g, q.get(i).name(), sx + 1, cy + 1, 16);
            }
        }
        cy += slot + Theme.SP_2;
        int gate = m.stats().gate();
        int open = 1;
        int nextLevel = -1;
        for (int l : MountRules.LEVELS) {
            if (l <= gate) {
                open = l;
            } else if (nextLevel < 0) {
                nextLevel = l;
            }
        }
        String line = L.t("wynnlabs.mounts.open_level", open)
                + (nextLevel > 0 ? " · " + L.t("wynnlabs.mounts.next_level", nextLevel, nextLevel) : "");
        Type.META.draw(g, font(), Type.META.fit(font(), line, w), x, cy, Theme.INK_2);
        return cy + Type.META.lineHeight - y;
    }

    /** What the feed plan says for this mount, as a notice with the road map one click away. */
    private int plan(GuiGraphics g, int x, int y, int w, MountsModel.Owned o, int mx, int my) {
        MountsModel.FeedView v = tab.model.feed(o);
        FeedPlan f = v.plan();
        boolean full = tab.model.goalFor(o).fullLimits();
        String goal = L.t(full ? "wynnlabs.mounts.goal_full" : "wynnlabs.mounts.goal_ready");
        String text;
        Ui.Level level = Ui.Level.INFO;
        Ui.Action action;
        Runnable run;
        if (f != null && f.steps().isEmpty()) {
            text = L.t("wynnlabs.mounts.plan_done", goal);
            action = new Ui.Action("route", L.t("wynnlabs.mounts.plan_open"));
            run = () -> tab.menu.openTab(RoadmapScreen.TAB_MOUNT_ROAD);
        } else {
            String time = f == null ? "—" : MountsText.dur(f.totalTimeSec());
            int feeds = f == null ? 0 : f.feeds();
            switch (v.state()) {
                case RUNNING -> {
                    text = L.t("wynnlabs.mounts.notice_running", goal, String.format(java.util.Locale.ROOT, "%,d", v.explored()));
                    action = new Ui.Action("x", L.t("wynnlabs.mounts.calc_cancel"));
                    run = MountsModel::cancelFeed;
                }
                case STALE -> {
                    text = L.t("wynnlabs.mounts.notice_stale", MenuPaint.ago(v.savedAt()));
                    level = Ui.Level.WARNING;
                    action = new Ui.Action("refresh", L.t("wynnlabs.mounts.calc_again"));
                    run = () -> tab.model.startFeed(o);
                }
                case CACHED -> {
                    text = L.t(f != null && f.proven() ? "wynnlabs.mounts.notice_done" : "wynnlabs.mounts.notice_partial", goal, time, feeds,
                            MenuPaint.ago(v.savedAt()));
                    action = new Ui.Action("route", L.t("wynnlabs.mounts.plan_open"));
                    run = () -> tab.menu.openTab(RoadmapScreen.TAB_MOUNT_ROAD);
                }
                default -> {
                    text = L.t("wynnlabs.mounts.notice_estimate", goal, time, feeds);
                    action = new Ui.Action("player-play", L.t("wynnlabs.mounts.calc_start"));
                    run = () -> tab.model.startFeed(o);
                }
            }
        }
        int[][] hit = new int[1][];
        int nh = Ui.notice(g, font(), level, List.of(text), action, x, y, w, Ui.noticeHeight(2), mx, my, hit);
        if (hit[0] != null && cardScroll.shows(hit[0][1])) {
            tab.click(hit[0][0], hit[0][1], hit[0][2], hit[0][3], run);
        }
        return nh;
    }

    /** A section title: LABEL, an optional note on the right, the hairline; returns the y for its rows. */
    private int head(GuiGraphics g, int x, int y, int w, String title, String note) {
        Type.LABEL.draw(g, font(), title, x, y, Theme.INK);
        if (note != null) {
            Type.META.drawRight(g, font(), note, x + w, y, Theme.INK_3);
        }
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    // ---- input ----

    boolean barPress(double x, double y) {
        return listScroll.press(x, y) || cardScroll.press(x, y);
    }

    boolean barDrag(double y) {
        return listScroll.drag(y) || cardScroll.drag(y);
    }

    void barRelease() {
        listScroll.release();
        cardScroll.release();
    }

    void scrolled(double x, double y, double v) {
        if (!listScroll.wheel(x, y, v)) {
            cardScroll.wheel(x, y, v);
        }
    }
}
