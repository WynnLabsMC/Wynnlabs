package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Home's level row: the Plan pill, "Now" and the road map's milestones as pills, a window of
 * them that follows the selected one, and which milestones get a pill at all.
 */
final class HomeLevels extends MenuPaint {

    /** First milestone shown after "now" in the level buttons, and how many fit. */
    private int levelOff;                        // the first shown milestone tab, as a position in shown()
    private static final int LEVEL_WINDOW = 5;

    HomeLevels(RoadmapScreen menu) {
        super(menu);
        levelWindow = LEVEL_WINDOW;
    }

    /**
     * The level a milestone's whole set is wearable at: its highest piece. A milestone at 120
     * often brings Lv 105 pieces, so its tab reads 105 or so, not 120.
     */
    private static int wearAt(Roadmap.Step s) {
        int hi = 0;
        for (Damage.Piece p : s.build().values()) {
            if (p != null) {
                hi = Math.max(hi, p.level());
            }
        }
        for (Roadmap.Gear g : s.gear()) {
            hi = Math.max(hi, g.info().requirements().level());
        }
        return hi == 0 ? s.level() : hi;
    }

    /**
     * The milestones with a tab: one is left out when its set is wearable already (the now tab
     * has it) or the next one's is wearable as early (its picks were there already, only better
     * chosen), so the tabs climb one level each.
     */
    List<Integer> shown() {
        List<Integer> out = new ArrayList<>();
        int now = menu.steps.isEmpty() ? 0 : wearAt(menu.steps.get(0));
        for (int i = 1; i < menu.steps.size(); i++) {
            int at = wearAt(menu.steps.get(i));
            // wearable already: the now tab has it; a later set as early: that one is better
            if (at <= now || i + 1 < menu.steps.size() && wearAt(menu.steps.get(i + 1)) <= at) {
                continue;
            }
            out.add(i);
        }
        return out;
    }

    /** How many milestone buttons the level row shows this frame (LEVEL_WINDOW where they fit). */
    private int levelWindow;

    /**
     * The level buttons (şimdi · ‹ · Lv 60 · 70 … · ›) ending at {@code right}, their text on
     * line {@code y}; each names the level its whole set is wearable at, the window follows the
     * selected one and shows as many milestones as {@code room} takes. Returns their left edge.
     */
    int draw(GuiGraphics g, int y, int right, int room, List<int[]> levelRows) {
        List<Integer> shown = shown();
        int pos = shown.indexOf(menu.selected);
        if (menu.selected > 0 && pos < 0 && !shown.isEmpty()) {
            // a hidden milestone: the next shown one has a set as early and better
            int to = shown.get(shown.size() - 1);
            for (int i : shown) {
                if (i >= menu.selected) {
                    to = i;
                    break;
                }
            }
            menu.selected = to;
            pos = shown.indexOf(menu.selected);
        }
        levelWindow = LEVEL_WINDOW;
        // with a plan, its pill first: "Plan · Now · Lv 68 · 80 …"
        Plans.Build plan = Plans.current();
        boolean planOn = planPills.planSelected(plan);
        String planText = L.t("wynnlabs.homev.plan_pill");
        int planW = plan == null ? 0 : Type.BODY.width(font(), planText) + 14 + PLAN_X;
        room -= plan == null ? 0 : planW + 3;
        List<int[]> order = pillOrder(shown, pos, levelWindow);
        while (levelWindow > 1 && pillsWidth(order, shown) > room) {
            order = pillOrder(shown, pos, --levelWindow);
        }
        int firstShown = levelOff < shown.size() ? shown.get(levelOff) : -9;
        int bx = right;
        for (int k = order.size() - 1; k >= 0; k--) {
            int i = order.get(k)[0];
            String t = pillText(i, firstShown);
            int tw = i < 0 ? 16 : font().width(t) + 12;
            bx -= tw;
            boolean on = !planOn && i == menu.selected;
            if (i < 0) {
                Ui.iconButton(g, i == -1 ? "chevron-left" : "chevron-right", bx, y - 3, false, menu.frameMx, menu.frameMy);
            } else {
                Ui.chip(g, font(), t, bx, y - 3, tw, on, false, false, menu.frameMx, menu.frameMy);
            }
            levelRows.add(new int[] {bx, y - 3, tw, 14, i});
            if (i > 0) {
                menu.hits.add(new MenuHit(bx, y - 3, tw, 14, null, List.of(
                        L.c("wynnlabs.home.levels_tip", wearAt(menu.steps.get(i)), menu.steps.get(i).level())
                                .withStyle(ChatFormatting.GRAY)), null));
            }
            bx -= 3;
        }
        if (plan != null) {
            bx -= planW;
            // purple when selected, like the chosen items on the panel; a thin chip otherwise
            boolean ph = menu.frameMx >= bx && menu.frameMx < bx + planW && menu.frameMy >= y - 3 && menu.frameMy < y + 11;
            if (planOn) {
                Kit.pillPrimary(g, bx, y - 3, planW, Ui.CHIP_H, ph);
            } else {
                Kit.pill(g, bx, y - 3, planW, Ui.CHIP_H, false, ph);
            }
            Type.BODY.draw(g, font(), planText, bx + Theme.SP_4, Type.BODY.yIn(y - 3, Ui.CHIP_H), planOn ? Theme.CREAM : Theme.INK_2);
            levelRows.add(new int[] {bx, y - 3, planW - PLAN_X, 14, PLAN_PILL});
            menu.hits.add(new MenuHit(bx, y - 3, planW - PLAN_X, 14, null, List.of(Component.literal(plan.name()).withStyle(ChatFormatting.LIGHT_PURPLE),
                    L.c("wynnlabs.homev.plan_pill_tip").withStyle(ChatFormatting.GRAY)), null));
            // its ✕ at the right end: the plan removed (Undo on the build line for a few seconds)
            int xx = bx + planW - PLAN_X - 1;
            boolean xh = menu.frameMx >= xx && menu.frameMx < xx + 12 && menu.frameMy >= y - 2 && menu.frameMy < y + 10;
            int xink = planOn ? Theme.CREAM : Theme.INK_2;
            Art.icon(g, "x", xx + 2, y, 8, xh ? (planOn ? Theme.GOLD_EDGE : Theme.INK) : xink);
            List<Component> xtip = new ArrayList<>();
            xtip.add(L.c("wynnlabs.homev.plan_remove").withStyle(ChatFormatting.WHITE));
            xtip.add(L.c("wynnlabs.homev.plan_remove_tip").withStyle(ChatFormatting.GRAY));
            if (!Plans.isSaved(plan)) {
                xtip.add(L.c("wynnlabs.homev.plan_unsaved").withStyle(ChatFormatting.GOLD));
            }
            menu.hits.add(new MenuHit(xx, y - 2, 12, 12, null, xtip, "#qplanremove:"));
            bx -= 3;
        }
        return bx + 3;
    }

    /** The Plan pill's place in levelRows (the milestones are 0 and up, ‹ and › -1 and -2). */
    static final int PLAN_PILL = -3;
    /** The room the Plan pill's ✕ takes at its right end. */
    private static final int PLAN_X = 13;
    /** Whether the Plan pill or a level pill is selected (kept across menu openings). */
    static final Plans.Pills planPills = new Plans.Pills();

    /** The level row's buttons for a window of {@code window} milestones: {step index, or -1 ‹ / -2 ›}. */
    private List<int[]> pillOrder(List<Integer> shown, int pos, int window) {
        if (pos >= 0 && pos < levelOff) {
            levelOff = pos;
        } else if (pos >= levelOff + window) {
            levelOff = pos - window + 1;
        }
        levelOff = Math.max(0, Math.min(levelOff, Math.max(0, shown.size() - window)));
        List<int[]> order = new ArrayList<>();
        order.add(new int[] {0});
        if (levelOff > 0) {
            order.add(new int[] {-1});
        }
        for (int k = levelOff; k < shown.size() && k < levelOff + window; k++) {
            order.add(new int[] {shown.get(k)});
        }
        if (levelOff + window < shown.size()) {
            order.add(new int[] {-2});
        }
        return order;
    }

    private String pillText(int i, int firstShown) {
        return i < 0 ? "" : i == 0 ? L.t("wynnlabs.home.now_short") : (i == firstShown ? "Lv " : "") + wearAt(menu.steps.get(i));
    }

    private int pillsWidth(List<int[]> order, List<Integer> shown) {
        int firstShown = levelOff < shown.size() ? shown.get(levelOff) : -9;
        int n = 0;
        for (int[] o : order) {
            n += (o[0] < 0 ? 16 : font().width(pillText(o[0], firstShown)) + 12) + 3;
        }
        return n - 3;
    }

    /** A click on level pill {@code r} (not the Plan pill): it is selected, ‹ and › step the window. */
    void clicked(int[] r) {
        planPills.pickLevel();
        List<Integer> shown = shown();
        int pos = shown.indexOf(menu.selected);
        menu.selected = r[4] == -1 ? shown.get(Math.max(0, Math.min(pos, levelOff) - 1))
                : r[4] == -2 ? shown.get(Math.min(shown.size() - 1, Math.max(pos, levelOff + levelWindow - 1) + 1))
                : r[4];
    }

    /** The wheel walks the pills: Now, then the shown milestones. */
    void wheel(double v) {
        planPills.pickLevel();
        List<Integer> order = new ArrayList<>();
        order.add(0);
        order.addAll(shown());
        int pos = Math.max(0, order.indexOf(menu.selected));
        menu.selected = order.get(Math.max(0, Math.min(order.size() - 1, pos - (int) Math.signum(v))));
    }
}
