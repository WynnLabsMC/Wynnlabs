package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Exclusive;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Gear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each of a milestone's picks adds to the milestone's finished set (its step gain): the set
 * judged with the pick and with its slot as the milestone started, never in the order the picks
 * went in. In that order a helmet that needs the new weapon's skill points was judged before the
 * weapon and read −13 % on a set it belongs to.
 */
final class StepGains {

    private StepGains() {}

    /** A pick of the milestone: its row, the build slot it holds, and what that slot held when the milestone started. */
    private record Pick(int row, String slot, Damage.Piece was) {}

    /** What the set scores with some of its picks back as the milestone started; {@code slots} those picks. */
    private record Back(double judged, List<String> slots) {}

    /**
     * Each pick's step gain as what it adds to the finished {@code set} (leave one out):
     * <ol>
     * <li>its slot back as the milestone started ({@code start}: the last milestone's piece, what you
     *     wear, or empty where nothing was), never empty where something was (a set without its
     *     weapon says nothing about the weapon);</li>
     * <li>when the set cannot wear that (the pick carries a skill point another change needs), the
     *     pick and one other pick back together, the best such: the row shows what the two add
     *     (The Modulator read −21 % at Lv 90 slot by slot);</li>
     * <li>when not even that can be worn, against the best other piece of its panel the set can
     *     wear there and that is behind it (what you would put in instead);</li>
     * <li>else the picks still unsettled go back together and share what they add, each the same
     *     factor; when even that cannot be worn, they share what the milestone adds.</li>
     * </ol>
     * A pick the set is as good without (back as it started, alone or with a partner) is no pick: it
     * goes back into the set and its row goes, the worst first, until every pick adds something. The
     * search ends on sets no single swap of its pools beats, but the piece you had is not always in
     * those pools (Gambler's Ruin read −2.5 % on the Lv 120 set where Dune Storm did better), and it
     * keeps a pick that is within its 0.2 % of nothing. Targets and chosen items (NaN) and rows of
     * pieces not in the set are left as they are. Runs on the finished set, after the search's.
     */
    static void apply(List<Gear> gear, Map<String, Damage.Piece> set, Map<String, Damage.Piece> start,
            Map<String, String> placed, int m, int earned, int[] prev) {
        for (int round = 0; round <= gear.size(); round++) {
            Build.Eval full = Build.allocate(set, m, earned);
            if (full == null) {
                return;
            }
            double fullJ = SetJudge.judged(full, prev, m);
            List<Pick> picks = picks(gear, set, start, placed);
            Map<Integer, Back> back = new LinkedHashMap<>();
            for (Pick p : picks) {
                Back b = alone(set, p, m, earned, prev);
                if (b == null) {
                    b = withPartner(set, picks, p, m, earned, prev);
                }
                if (b != null) {
                    back.put(p.row(), b);
                }
            }
            List<Pick> open = picks.stream().filter(p -> !back.containsKey(p.row())).toList();
            Back group = together(set, open, m, earned, prev);
            Back worst = group != null && group.judged() >= fullJ ? group : null;
            for (Back b : back.values()) {
                if (b.judged() >= fullJ && (worst == null || b.judged() > worst.judged())) {
                    worst = b;
                }
            }
            if (worst != null) {
                // the set is as good without it: it goes back, and the others are judged again
                for (String slot : worst.slots()) {
                    undo(gear, set, start, placed, slot);
                }
                continue;
            }
            // a pick that cannot go back even with a partner: against the best other piece of its
            // panel the set can wear there (the next best you could put in), when that is behind it
            for (Pick p : open) {
                Back b = alternative(gear.get(p.row()), set, p, fullJ, m, earned, prev);
                if (b != null) {
                    back.put(p.row(), b);
                }
            }
            open = picks.stream().filter(p -> !back.containsKey(p.row())).toList();
            group = together(set, open, m, earned, prev);
            if (!open.isEmpty()) {
                // each the same factor: the open picks together turn the group's score into the set's
                int n = open.size();
                double from = group == null ? Double.NaN : group.judged();
                if (Double.isNaN(from)) {
                    from = judged(new LinkedHashMap<>(start), m, earned, prev);
                    n = picks.size();
                }
                if (!Double.isNaN(from) && from > 0) {
                    Back each = new Back(fullJ / Math.pow(fullJ / from, 1.0 / n), List.of());
                    open.forEach(p -> back.put(p.row(), each));
                }
            }
            for (Pick p : picks) {
                Back b = back.get(p.row());
                if (b == null || b.judged() <= 0) {
                    continue;
                }
                double rowGain = (fullJ / b.judged() - 1) * 100;
                Gear g = gear.get(p.row());
                gear.set(p.row(), new Gear(g.slot(), g.info(), rowGain, g.source(), g.price(), g.alt(),
                        g.altSource(), g.detail(), g.alternatives(), RoadmapGains.setGain(g.options(), rowGain)));
            }
            return;
        }
    }

    /** The rows that changed a slot of {@code set} since {@code start}, with a step gain (not a target or chosen item). */
    private static List<Pick> picks(List<Gear> gear, Map<String, Damage.Piece> set, Map<String, Damage.Piece> start,
            Map<String, String> placed) {
        List<Pick> out = new ArrayList<>();
        for (int i = 0; i < gear.size(); i++) {
            Gear g = gear.get(i);
            String slot = placed.get(g.slot());
            Damage.Piece pick = slot == null ? null : set.get(slot);
            if (Double.isNaN(g.gain()) || pick == null || !pick.name().equalsIgnoreCase(g.info().name())) {
                continue;
            }
            Damage.Piece was = start.get(slot);
            if (was != null && was.key().equals(pick.key())) {
                continue;
            }
            out.add(new Pick(i, slot, was));
        }
        return out;
    }

    /** {@code set} with {@code slot} as it started ({@code was}; null: empty); null when that cannot be worn. */
    private static Map<String, Damage.Piece> undone(Map<String, Damage.Piece> set, String slot, Damage.Piece was) {
        Map<String, Damage.Piece> trial = new LinkedHashMap<>(set);
        trial.remove(slot);
        if (was != null) {
            if (Exclusive.clash(trial, slot, Exclusive.group(was)) != null) {
                return null;
            }
            trial.put(slot, was);
        }
        return trial;
    }

    private static double judged(Map<String, Damage.Piece> trial, int m, int earned, int[] prev) {
        if (trial == null) {
            return Double.NaN;
        }
        Build.Eval ev = Build.allocate(trial, m, earned);
        return ev == null ? Double.NaN : SetJudge.judged(ev, prev, m);
    }

    /** The set with the pick's slot back as it started; null when that cannot be worn. */
    private static Back alone(Map<String, Damage.Piece> set, Pick p, int m, int earned, int[] prev) {
        double j = judged(undone(set, p.slot(), p.was()), m, earned, prev);
        return Double.isNaN(j) ? null : new Back(j, List.of(p.slot()));
    }

    /**
     * The set with the pick and one other pick back as they started: the best such that can be
     * worn, so the row claims the least it can; null when none can.
     */
    private static Back withPartner(Map<String, Damage.Piece> set, List<Pick> picks, Pick p, int m, int earned,
            int[] prev) {
        Back best = null;
        Map<String, Damage.Piece> one = undone(set, p.slot(), p.was());
        if (one == null) {
            return null;
        }
        for (Pick o : picks) {
            if (o.slot().equals(p.slot())) {
                continue;
            }
            double j = judged(undone(one, o.slot(), o.was()), m, earned, prev);
            if (!Double.isNaN(j) && (best == null || j > best.judged())) {
                best = new Back(j, List.of(p.slot(), o.slot()));
            }
        }
        return best;
    }

    /**
     * The set with the best other piece of the pick's comparison panel in its place that it can
     * wear and that is behind the pick; null when none is.
     */
    private static Back alternative(Gear g, Map<String, Damage.Piece> set, Pick p, double fullJ, int m, int earned,
            int[] prev) {
        Back best = null;
        for (Roadmap.Option o : g.options() == null ? List.<Roadmap.Option>of() : g.options()) {
            if (o.info() == null || o.info().name().equalsIgnoreCase(g.info().name())) {
                continue;
            }
            double j = judged(undone(set, p.slot(), OwnedGear.pieceOf(o.info())), m, earned, prev);
            if (!Double.isNaN(j) && j < fullJ && (best == null || j > best.judged())) {
                best = new Back(j, List.of(p.slot()));
            }
        }
        return best;
    }

    /**
     * The picks that cannot go back alone or with a partner, all back together; null when there are
     * none or that cannot be worn either (the milestone's start set is then the measure). They
     * share what they add, each the same factor, so no row reads the whole group's gain; the
     * slot-by-slot number they had (the order the picks went in) is never kept.
     */
    private static Back together(Map<String, Damage.Piece> set, List<Pick> open, int m, int earned, int[] prev) {
        if (open.isEmpty()) {
            return null;
        }
        Map<String, Damage.Piece> trial = set;
        for (Pick p : open) {
            trial = trial == null ? null : undone(trial, p.slot(), p.was());
        }
        double j = judged(trial, m, earned, prev);
        return Double.isNaN(j) ? null : new Back(j, open.stream().map(Pick::slot).toList());
    }

    /** Puts {@code slot} back as the milestone started and drops the row that changed it. */
    private static void undo(List<Gear> gear, Map<String, Damage.Piece> set, Map<String, Damage.Piece> start,
            Map<String, String> placed, String slot) {
        Damage.Piece was = start.get(slot);
        if (was == null) {
            set.remove(slot);
        } else {
            set.put(slot, was);
        }
        for (var e : placed.entrySet()) {
            if (e.getValue().equals(slot)) {
                String row = e.getKey();
                gear.removeIf(g -> g.slot().equals(row) && !Double.isNaN(g.gain()));
            }
        }
    }
}
