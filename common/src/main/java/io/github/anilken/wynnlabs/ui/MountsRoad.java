package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.MountType;
import io.github.anilken.wynnlabs.mounts.RoadMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * The Mounts tab's Road map: the totals for the picked standard (LOW ~700, HIGH 1000+) and type,
 * the generations as columns (the mounts you have drawn, the missing ones faint), then what to do
 * now with the shopping list beside it ({@link MountsNow}). Breeding results are estimates and
 * always show as ranges; the feed plan is exact.
 */
final class MountsRoad {

    private static final int LINK_W = 56;          // between two generations: "breed ×2 ›" and its time
    private static final int SLOT = 16;

    private final MountsTab tab;
    final MountsNow now;

    MountsRoad(MountsTab tab) {
        this.tab = tab;
        this.now = new MountsNow(tab);
    }

    private net.minecraft.client.gui.Font font() {
        return tab.font();
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean compact = tab.compact();
        int cy = y;
        if (tab.chipsInPage) {
            cy += tab.chipRow(g, x, cy, w, mx, my) + Theme.SP_4;
        }
        MountsModel.Pending<RoadMap.Plan> p = tab.model.road();
        RoadMap.Plan plan = p.value();
        if (plan == null) {
            String msg = p.running() ? Ui.busy(L.t("wynnlabs.mounts.planning")) : L.t("wynnlabs.mounts.plan_failed", p.error());
            int[][] hit = new int[1][];
            cy += Ui.notice(g, font(), p.running() ? Ui.Level.INFO : Ui.Level.WARNING, List.of(msg), null, x, cy, w,
                    Ui.noticeHeight(2), mx, my, hit) + Theme.SP_4;
        } else {
            if (!plan.reached()) {
                // no road reaches it within six generations: the best road found, said plainly
                String msg = L.t("wynnlabs.mounts.road_unreachable", MountsTab.targetLabel(plan.targetPotential()),
                        MountsText.range(plan.finalPotential()));
                int[][] hit = new int[1][];
                cy += Ui.notice(g, font(), Ui.Level.WARNING, List.of(msg), null, x, cy, w, Ui.noticeHeight(2), mx, my, hit) + Theme.SP_4;
            }
            cy += (compact ? totalsLine(g, x, cy, w, plan) : totals(g, x, cy, w, plan, mx, my)) + Theme.SP_5;
            cy += generations(g, x, cy, w, plan, compact, mx, my) + Theme.SP_5;
        }
        now.draw(g, x, cy, w, y + h - cy, plan, mx, my);
    }

    // ---- the totals ----

    private int totals(GuiGraphics g, int x, int y, int w, RoadMap.Plan plan, int mx, int my) {
        int gens = plan.generations().size();
        int breeds = plan.generations().stream().mapToInt(RoadMap.Generation::breeds).sum();
        double em = cost(plan);
        String[][] cols = {
            {L.t("wynnlabs.mounts.target"), MountsText.range(plan.finalPotential()).substring(1),
                L.t("wynnlabs.mounts.target_sub", gens + 1, plan.type().display())},
            {L.t("wynnlabs.mounts.to_buy"), String.valueOf(plan.mountsToBuy()),
                L.n("wynnlabs.mounts.gens", gens) + " · " + L.n("wynnlabs.mounts.breeds", breeds)},
            {L.t("wynnlabs.mounts.time"), MountsText.dur(plan.wallClockSec()).equals("—") ? "—" : "~" + MountsText.dur(plan.wallClockSec()),
                L.t("wynnlabs.mounts.time_sub", plan.totalFeeds())},
            {L.t("wynnlabs.mounts.emeralds"), em <= 0 ? "—" : "~" + MountsText.em(em),
                L.t("wynnlabs.mounts.emeralds_sub", plan.materials().values().stream().mapToInt(Integer::intValue).sum())}};
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
        if (my >= y && my < y + ch && mx >= x && mx < x + colW + Theme.SP_4) {
            tab.tip(x, y, colW + Theme.SP_4, ch, List.of(Component.literal(L.t("wynnlabs.mounts.estimate_tip")).withStyle(ChatFormatting.GRAY)));
        }
        return ch;
    }

    /** Small windows: the totals as one line ("Target 950–1110 · 3 gen · ~5 d 6 h · ~2.1 LE"). */
    private int totalsLine(GuiGraphics g, int x, int y, int w, RoadMap.Plan plan) {
        String head = L.t("wynnlabs.mounts.target") + " ";
        double em = cost(plan);
        String rest = MountsText.range(plan.finalPotential()).substring(1) + " · " + L.n("wynnlabs.mounts.mounts_n", plan.mountsToBuy())
                + " · ~" + MountsText.dur(plan.wallClockSec()) + (em > 0 ? " · ~" + MountsText.em(em) : "");
        Type.MUTED.draw(g, font(), head, x, y, Theme.INK_2);
        int hw = Type.MUTED.width(font(), head);
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), rest, w - hw), x + hw, y, Theme.INK);
        return Type.LABEL.lineHeight;
    }

    /** The road's emeralds: the mounts to buy at the merchant's price, and the food with a known price. */
    private static double cost(RoadMap.Plan plan) {
        double each = MountsModel.mountPrice(plan.type());
        return MountsNow.cost(plan.materials()) + (each > 0 ? each * plan.mountsToBuy() : 0);
    }

    // ---- the generations ----

    private int generations(GuiGraphics g, int x, int y, int w, RoadMap.Plan plan, boolean compact, int mx, int my) {
        Type.LABEL.draw(g, font(), L.t("wynnlabs.mounts.generations"), x, y, Theme.INK);
        String note = L.t("wynnlabs.mounts.estimates");
        int nw = Type.META.width(font(), note);
        Type.META.drawRight(g, font(), note, x + w, y, Theme.INK_3);
        tab.tip(x + w - nw, y, nw, Type.META.lineHeight, List.of(Component.literal(L.t("wynnlabs.mounts.estimate_tip")).withStyle(ChatFormatting.GRAY)));
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        int cy = y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
        List<RoadMap.Generation> gens = plan.generations();
        int cards = gens.size() + 1;
        int link = compact || w < 560 ? Theme.SP_6 : LINK_W;
        int cw = (w - (cards - 1) * link) / cards;
        int ch = 2 * Theme.SP_3 + Type.LABEL.lineHeight + Type.META.lineHeight + Theme.SP_3 + SLOT + Theme.SP_3
                + (compact ? 0 : Type.META.lineHeight + Theme.SP_2) + 1 + Theme.SP_3 + Type.META.lineHeight;
        MountType type = plan.type();
        List<MountsModel.Owned> owned = tab.model.owned(type);
        int nowGen = currentGeneration(gens, owned);
        int cx = x;
        for (int i = 0; i < gens.size(); i++) {
            RoadMap.Generation gen = gens.get(i);
            List<MountsModel.Owned> mine = inGeneration(gens, i, owned);
            boolean on = i == nowGen;
            String title = gen.bought() > 0 && i == 0 ? L.t("wynnlabs.mounts.gen_fresh") : L.t("wynnlabs.mounts.gen_n", gen.index());
            String range = gen.bought() > 0 && i == 0 ? String.valueOf(gen.parentStart().potential()) : MountsText.range(gen.parentPotential());
            // the goal and the feeding time; small cards keep the time alone, no time when nothing is fed
            long feed = gen.wallClockSec() - gen.breedTimeSec();
            String goalText = gen.goal().fullLimits() ? L.t("wynnlabs.mounts.full_limits")
                    : L.t("wynnlabs.mounts.avg_limit_n", gen.goal().minAvgLimit());
            String time = feed > 0 ? "~" + MountsText.dur(feed) : null;
            String foot = compact && time != null ? time : time == null ? goalText : goalText + " · " + time;
            card(g, cx, cy, cw, ch, title, range, on, false, type, mine.size(), gen.parents(), foot, compact);
            cx += cw;
            // the link to the next: how many breedings and how long
            int lx = cx;
            int mid = cy + ch / 2;
            if (link >= LINK_W) {
                String b = L.t("wynnlabs.mounts.breed_x", gen.breeds());
                Type.META.draw(g, font(), b, lx + (link - Type.META.width(font(), b)) / 2, mid - Type.META.lineHeight - Theme.SP_1, Theme.INK_2);
                Art.icon(g, "chevron-right", lx + (link - 6) / 2, mid - 3, 6, Theme.INK_3);
                String t = MountsText.dur(gen.breedTimeSec());
                Type.META.draw(g, font(), t, lx + (link - Type.META.width(font(), t)) / 2, mid + Theme.SP_2, Theme.INK_2);
            } else {
                Art.icon(g, "chevron-right", lx + (link - 6) / 2, mid - 3, 6, Theme.INK_3);
            }
            if (my >= cy && my < cy + ch && mx >= lx && mx < lx + link) {
                tab.tip(lx, cy, link, ch, List.of(Component.literal(L.t("wynnlabs.mounts.breed_tip", gen.breeds(),
                        MountsText.range(gen.childPotential()), MountsText.range(gen.childAvgLimit()))).withStyle(ChatFormatting.GRAY)));
            }
            cx += link;
        }
        // the goal: the last child, a range, never one number
        String last = gens.isEmpty() ? L.t("wynnlabs.mounts.gen_n", 1) : L.t("wynnlabs.mounts.gen_n", gens.get(gens.size() - 1).index() + 1);
        card(g, cx, cy, x + w - cx, ch, last, MountsText.range(plan.finalPotential()), false, true, type, 0, 0,
                L.t("wynnlabs.mounts.goal"), compact);
        return cy + ch - y;
    }

    /** One generation's card: title, range, the mounts drawn and faint + for the missing, then its foot. */
    private void card(GuiGraphics g, int x, int y, int w, int h, String title, String range, boolean on, boolean goal, MountType type,
                      int have, int need, String foot, boolean compact) {
        if (on) {
            Kit.softCard(g, x, y, w, h);
        } else {
            g.fill(x, y, x + w, y + h, 0x10000000);
            Kit.hair(g, x, y, w, 1, goal ? Theme.GOLD_EDGE : 0x809A855C);
            Kit.hair(g, x, y + h - 1, w, 1, goal ? Theme.GOLD_EDGE : 0x809A855C);
            Kit.hair(g, x, y, 1, h, goal ? Theme.GOLD_EDGE : 0x809A855C);
            Kit.hair(g, x + w - 1, y, 1, h, goal ? Theme.GOLD_EDGE : 0x809A855C);
        }
        int in = x + Theme.SP_3;
        int room = w - 2 * Theme.SP_3;
        int cy = y + Theme.SP_3;
        int sealW = 0;
        if (on && !compact) {
            String s = L.t("wynnlabs.mounts.now");
            sealW = Type.META.width(font(), s) + 2 * Theme.SP_2;
            Ui.seal(g, font(), s, x + w - Theme.SP_3 - sealW, Type.LABEL.onCaps(cy, Ui.SEAL_H), Theme.GOLD_DEEP);
            sealW += Theme.SP_2;
        } else if (need > 0 && !compact) {
            String s = Math.min(have, need) + "/" + need;
            Type.META.drawRight(g, font(), s, x + w - Theme.SP_3, cy, Theme.INK_3);
            sealW = Type.META.width(font(), s) + Theme.SP_2;
        }
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), title, room - sealW), in, cy, Theme.INK);
        cy += Type.LABEL.lineHeight;
        Type.META.draw(g, font(), Type.META.fit(font(), range, room), in, cy, goal ? Theme.WARN : Theme.INK_2);
        cy += Type.META.lineHeight + Theme.SP_3;
        int sx = in;
        if (goal) {
            Kit.pill(g, sx, cy, SLOT, SLOT, true, false);
            Art.icon(g, "star", sx + (SLOT - 8) / 2, cy + (SLOT - 8) / 2, 8, Theme.GOLD_EDGE);
        }
        for (int i = 0; i < Math.max(have, need) && sx + SLOT <= x + w - Theme.SP_3; i++) {
            if (i < have) {
                MountsText.icon(g, type, sx, cy, SLOT);
            } else {
                Kit.pill(g, sx, cy, SLOT, SLOT, false, false);
                Art.icon(g, "plus", sx + (SLOT - 6) / 2, cy + (SLOT - 6) / 2, 6, Theme.INK_3);
            }
            sx += SLOT + Theme.SP_2;
        }
        cy += SLOT + Theme.SP_3;
        if (!compact && !goal) {
            String counts = L.t("wynnlabs.mounts.have_need", have, Math.max(0, need - have));
            Type.META.draw(g, font(), Type.META.fit(font(), counts, room), in, cy, Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_2;
        } else if (!compact) {
            cy += Type.META.lineHeight + Theme.SP_2;
        }
        Kit.hair(g, in, cy, room, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        Type.META.draw(g, font(), Type.META.fit(font(), foot, room), in, cy, goal ? Theme.WARN : Theme.INK_2);
    }

    /** The generation your best mount belongs to (0 with none). */
    static int currentGeneration(List<RoadMap.Generation> gens, List<MountsModel.Owned> owned) {
        int best = 0;
        for (int i = 0; i < gens.size(); i++) {
            if (!inGeneration(gens, i, owned).isEmpty()) {
                best = i;
            }
        }
        return best;
    }

    /** The mounts that fit generation {@code i}: potential at least its parents' low end, under the next one's. */
    static List<MountsModel.Owned> inGeneration(List<RoadMap.Generation> gens, int i, List<MountsModel.Owned> owned) {
        int lo = i == 0 ? 0 : gens.get(i).parentPotential().low();
        int hi = i + 1 < gens.size() ? gens.get(i + 1).parentPotential().low() : Integer.MAX_VALUE;
        return owned.stream().filter(o -> o.mount().potential() >= lo && o.mount().potential() < hi).toList();
    }

    /** The mounts still short of their goal, whatever their generation: a fresh one bought later still eats. */
    static List<MountsModel.Owned> unfinished(List<MountsModel.Owned> owned, Predicate<MountsModel.Owned> done) {
        return owned.stream().filter(done.negate()).toList();
    }

    // ---- input ----

    boolean barPress(double x, double y) {
        return now.barPress(x, y);
    }

    boolean barDrag(double y) {
        return now.barDrag(y);
    }

    void barRelease() {
        now.barRelease();
    }

    void scrolled(double x, double y, double v) {
        now.scrolled(x, y, v);
    }
}
