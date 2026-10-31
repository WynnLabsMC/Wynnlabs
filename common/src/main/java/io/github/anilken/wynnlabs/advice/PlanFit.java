package io.github.anilken.wynnlabs.advice;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;

/**
 * Whether the build plan would take a piece you have not identified yet (a box's outcome, or
 * a revealed piece with stats "?"): every roll at its best, swapped into the plan's build, it
 * must clear the gain the plan asks of any swap ({@link Build#gainThreshold}). The shopping
 * list's own picks count as wanted too. The same source the identified piece is judged by, so
 * a box is not identified for a piece the plan then tells you to sell.
 */
final class PlanFit {

    private PlanFit() {}

    /** {@code wanted}: the plan would take it. {@code rival}: the plan's piece in that slot, null when empty. */
    record Fit(boolean wanted, String rival) {}

    /**
     * Null without a plan to ask, or when the plan cannot take the piece at all (its skill
     * requirements are out of reach at that level): nothing to judge it by, not "unwanted".
     */
    static Fit of(ItemData info, int level) {
        Build.Plan plan = Build.plan();
        if (plan == null || plan.result() == null || plan.result().objective() <= 0) {
            return null;
        }
        Slot t = info.slot();
        int lv = Math.max(level, info.requirements().level());
        double base = plan.objectiveAt(lv);
        if (base <= 0) {
            return null;
        }
        int earned = Math.max(plan.earned(), (Math.min(lv, 101) - 1) * 2);
        Damage.Piece cand = Damage.bestRoll(info);
        String rival = null;
        boolean wanted = false;
        boolean judged = false;
        for (String slot : t == Slot.RING ? new String[] {"RING1", "RING2"} : new String[] {Build.slotOf(t, 0)}) {
            if (slot == null) {
                continue;
            }
            Damage.Piece there = plan.pieces().get(slot);
            if (rival == null && there != null) {
                rival = there.name();
            }
            double v = Build.trySlot(plan.pieces(), plan.allocation(), slot, cand, lv, earned);
            // the bar at the level you could wear it: a piece meant for later is weighed against
            // the plan's piece as old as it will be by then
            double need = there == null ? 0.2 : Build.gainThreshold(there, lv);
            if (!Double.isNaN(v)) {
                judged = true;
                wanted |= (v / base - 1) * 100 >= need;
            }
        }
        if (Shopping.find(info.name()) != null) {
            return new Fit(true, rival);
        }
        return judged ? new Fit(wanted, rival) : null;
    }
}
