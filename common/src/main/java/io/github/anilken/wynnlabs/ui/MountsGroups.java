package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.RoadMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The mounts the road map's "Now" works on: every mount of the current generation still being fed,
 * those in the same state with the same plan grouped into one ("×5 same plan": the steps apply to
 * each). The page shows one group's plan at a time; by default the first one that can act now (no
 * food being eaten), else the one whose food is done soonest.
 */
final class MountsGroups {

    /** One mount: its key, name, feeder row (0: not known), the time left on what it eats (0: none), its plan. */
    record Member(String key, String name, int row, long timerSec, FeedPlan plan, MountsModel.Owned owned) {
        boolean ready() {
            return timerSec <= 0;
        }
    }

    /** Mounts with the same state and the same plan, in feeder-row order. */
    record Group(List<Member> members) {
        Member first() {
            return members.get(0);
        }

        int size() {
            return members.size();
        }

        boolean ready() {
            return first().ready();
        }

        /** The soonest a food in the group is eaten (0 when ready). */
        long timer() {
            long t = Long.MAX_VALUE;
            for (Member m : members) {
                t = Math.min(t, m.timerSec());
            }
            return Math.max(0, t);
        }

        boolean has(String key) {
            for (Member m : members) {
                if (m.key().equals(key)) {
                    return true;
                }
            }
            return false;
        }
    }

    private MountsGroups() {}

    /** The members grouped by state (ready or eating) and plan steps, in the order the first of each comes. */
    static List<Group> group(List<Member> members) {
        List<Member> sorted = new ArrayList<>(members);
        sorted.sort((a, b) -> Integer.compare(a.row() <= 0 ? Integer.MAX_VALUE : a.row(), b.row() <= 0 ? Integer.MAX_VALUE : b.row()));
        Map<String, List<Member>> by = new LinkedHashMap<>();
        for (Member m : sorted) {
            String k = m.ready() + "|" + (m.plan() == null ? "none" : m.plan().steps().toString());
            by.computeIfAbsent(k, x -> new ArrayList<>()).add(m);
        }
        List<Group> out = new ArrayList<>();
        by.values().forEach(l -> out.add(new Group(List.copyOf(l))));
        return out;
    }

    /** The group to show first: the first that can act now and has steps, else the soonest done; -1 for none. */
    static int pick(List<Group> groups) {
        for (int i = 0; i < groups.size(); i++) {
            Group gr = groups.get(i);
            if (gr.ready() && gr.first().plan() != null && !gr.first().plan().steps().isEmpty()) {
                return i;
            }
        }
        int best = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (best < 0 || groups.get(i).timer() < groups.get(best).timer()) {
                best = i;
            }
        }
        return best;
    }

    /** The group holding {@code key}, else the default pick; -1 for none. */
    static int find(List<Group> groups, String key) {
        for (int i = 0; key != null && i < groups.size(); i++) {
            if (groups.get(i).has(key)) {
                return i;
            }
        }
        return pick(groups);
    }

    /** The mounts of the generation the road map works on now that are still short of their goal. */
    static List<Member> members(MountsModel model, RoadMap.Plan plan) {
        List<RoadMap.Generation> gens = plan == null ? List.of() : plan.generations();
        if (gens.isEmpty()) {
            return List.of();
        }
        List<MountsModel.Owned> owned = model.owned(model.type());
        int i = MountsRoad.currentGeneration(gens, owned);
        List<Member> out = new ArrayList<>();
        for (MountsModel.Owned o : MountsRoad.inGeneration(gens, i, owned)) {
            var m = o.mount();
            if (model.goalFor(o).reachedBy(m.queue().applyTo(m.stats()))) {
                continue;
            }
            long timer = m.queue().pending().isEmpty() ? 0 : Math.max(0, m.queue().secondsLeft());
            out.add(new Member(o.read().key() + "#" + o.row() + "#" + out.size(), m.name(), o.row(), timer, model.feed(o).plan(), o));
        }
        return out;
    }
}
