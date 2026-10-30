package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Exclusive;
import io.github.anilken.wynnlabs.build.Tomes;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Alternative;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Gear;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Option;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Scored;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Step;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.ShoppingSources;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The endgame milestones' other whole builds: besides the milestone's pick, up to
 * rules.roadmap.alternatives (10) sets the search ended on anyway (the beam's, each start's end,
 * a set around each strong weapon: {@link SetSearch#searchBest}), kept with its result in the
 * beam cache, so no search of their own. Each differs from the pick and from every one kept
 * before it: another weapon, another main element or way of dealing damage (spells against the
 * main attack), or at least {@link #MIN_PIECES} other pieces. When the pick fits only with the
 * guild tome the map assumes ({@link Tomes}), the best of those sets (or the pick with one piece
 * changed) that fits without it is always among them, labelled so.
 */
final class Alternatives {

    /** How many pieces apart two sets with the same weapon and damage must be to count as two builds. */
    static final int MIN_PIECES = 3;
    /** How far apart the main attack's share of the damage must be to count as another way of fighting. */
    private static final double MAIN_SHARE_APART = 0.25;
    /** The sets the beam cache keeps per milestone, and at most this many with one weapon. */
    private static final int POOL = 40;
    private static final int PER_WEAPON = 4;
    private static final String[] ELEMENT_NAMES = {"Neutral", "Earth", "Thunder", "Water", "Fire", "Air"};

    private Alternatives() {}

    /** Whether milestone {@code m} offers other builds: from rules.roadmap.alternativesFrom (Lv 100) on. */
    static boolean wanted(int m) {
        return m >= Rules.sub("roadmap", "alternativesFrom", 100) && Rules.sub("roadmap", "alternatives", 10) >= 1;
    }

    /**
     * What the beam cache keeps of a search's ends: best judged first (the set's key on a tie),
     * each set once, none that clashes, at most {@link #PER_WEAPON} around one weapon (the beam's
     * many near-copies of the best would leave no room for the other weapons) and {@link #POOL} in all.
     */
    static List<Map<String, Damage.Piece>> pool(List<Scored> ends) {
        if (ends == null || ends.isEmpty()) {
            return List.of();
        }
        List<Scored> sorted = new ArrayList<>(ends);
        sorted.removeIf(java.util.Objects::isNull);
        Map<Scored, String> keyOf = new java.util.IdentityHashMap<>();
        sorted.forEach(s -> keyOf.put(s, SetJudge.setKey(s.set())));
        sorted.sort((a, b) -> {
            int by = Double.compare(b.j(), a.j());
            return by != 0 ? by : keyOf.get(a).compareTo(keyOf.get(b));
        });
        java.util.Set<String> seen = new java.util.HashSet<>();
        Map<String, Integer> perWeapon = new java.util.HashMap<>();
        List<Map<String, Damage.Piece>> out = new ArrayList<>();
        for (Scored s : sorted) {
            if (out.size() >= POOL) {
                break;
            }
            if (!seen.add(keyOf.get(s)) || SetJudge.clashes(s.set())) {
                continue;
            }
            String w = weaponName(s.set());
            if (perWeapon.merge(w, 1, Integer::sum) > PER_WEAPON) {
                continue;
            }
            out.add(new LinkedHashMap<>(s.set()));
        }
        return out;
    }

    /** One judged candidate: its set, quick spread, judged score, and (once asked) how it deals damage. */
    private static final class Cand {
        final Map<String, Damage.Piece> set;
        final Build.Eval eval;
        final double j;
        final boolean noTome;
        int[] profile;          // {main element, second element or -1, main attack share in ‰}

        Cand(Map<String, Damage.Piece> set, Build.Eval eval, double j, boolean noTome) {
            this.set = set;
            this.eval = eval;
            this.j = j;
            this.noTome = noTome;
        }
    }

    /**
     * Milestone {@code m}'s cards ({@link Step#alternatives}): the pick first, then the builds of
     * {@code sets} that set themselves apart, best judged first, each with its rows and set
     * ({@link #step}). Empty when none qualifies. {@code pick}: the pick's polished spread.
     */
    static List<Alternative> cards(MilestonePlanner p, MilestonePicks ms, List<Map<String, Damage.Piece>> sets,
            Build.Eval pick, int m, int earned, int[] prev, List<Gear> pickGear) {
        Map<String, Damage.Piece> base = p.baseline;
        int max = (int) Rules.sub("roadmap", "alternatives", 10);
        if (base == null || pick == null || pick.result().objective() <= 0 || max < 1 || sets == null) {
            return List.of();
        }
        double pickJ = SetJudge.judged(base, pick, prev, m);
        String pickKey = SetJudge.setKey(base);
        Cand mine = new Cand(base, pick, pickJ, false);
        List<Cand> all = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>(List.of(pickKey));
        for (Map<String, Damage.Piece> s : sets) {
            Map<String, Damage.Piece> set = withFixed(s, base, p.fixed);
            if (!seen.add(SetJudge.setKey(set)) || SetJudge.clashes(set) || overBudget(p, set)) {
                continue;
            }
            Roadmap.tick();
            Build.Eval e = Build.allocate(set, m, earned);
            if (e != null && e.result().objective() > 0) {
                all.add(new Cand(set, e, SetJudge.judged(set, e, prev, m), false));
            }
        }
        all.sort((a, b) -> {
            int by = Double.compare(b.j, a.j);
            return by != 0 ? by : SetJudge.setKey(a.set).compareTo(SetJudge.setKey(b.set));
        });
        List<Cand> kept = new ArrayList<>();
        for (Cand c : all) {
            if (kept.size() >= max) {
                break;
            }
            if (!apart(c, mine, p.fixed, m)) {
                continue;
            }
            boolean ok = true;
            for (Cand k : kept) {
                ok &= apart(c, k, p.fixed, m);
            }
            if (ok) {
                kept.add(c);
            }
        }
        boolean needsTome = needsTome(base, pick, m, earned);
        if (needsTome) {
            Cand free = withoutTome(p, ms, all, base, m, earned, prev);
            if (free != null) {
                String fk = SetJudge.setKey(free.set);
                kept.removeIf(k -> SetJudge.setKey(k.set).equals(fk));
                kept.add(free);
                kept.sort((a, b) -> Double.compare(b.j, a.j));
                while (kept.size() > max) {
                    for (int i = kept.size() - 1; i >= 0; i--) {
                        if (!kept.get(i).noTome) {
                            kept.remove(i);
                            break;
                        }
                    }
                }
            }
        }
        if (kept.isEmpty()) {
            return List.of();
        }
        List<Alternative> out = new ArrayList<>();
        out.add(card(p, mine, pick, pickJ, needsTome, m, null));
        for (Cand c : kept) {
            java.util.function.Supplier<Alternative> make = () -> {
                Build.Eval best = Build.allocateBest(c.set, m, earned);
                Build.Eval e = best != null ? best : c.eval;
                double j = SetJudge.judged(c.set, e, prev, m);
                Step st = step(p, ms, c.set, e, m, earned, prev, pickGear);
                return card(p, new Cand(c.set, e, j, c.noTome), e, pickJ, !c.noTome && needsTome(c.set, e, m, earned), m, st);
            };
            out.add(c.noTome ? without(make) : make.get());
        }
        return List.copyOf(out);
    }

    /** {@code s} with the chosen items (Locks) in their slots, as every set of the milestone holds them. */
    private static Map<String, Damage.Piece> withFixed(Map<String, Damage.Piece> s, Map<String, Damage.Piece> base,
            java.util.Set<String> fixed) {
        Map<String, Damage.Piece> out = new LinkedHashMap<>(s);
        for (String f : fixed) {
            out.put(f, base.get(f));
        }
        return out;
    }

    private static boolean overBudget(MilestonePlanner p, Map<String, Damage.Piece> set) {
        return p.budget > 0 && CandidateRules.spend(p.state, set, p.fixed, p.owned) > p.budget;
    }

    /** Whether two builds count as two: another weapon, {@link #MIN_PIECES} pieces apart, or another way of dealing damage. */
    private static boolean apart(Cand a, Cand b, java.util.Set<String> fixed, int m) {
        if (!weaponName(a.set).equalsIgnoreCase(weaponName(b.set))) {
            return true;
        }
        if (BuildRanking.distance(a.set, b.set, fixed) >= MIN_PIECES) {
            return true;
        }
        int[] x = profile(a, m);
        int[] y = profile(b, m);
        return x[0] != y[0] || Math.abs(x[2] - y[2]) >= MAIN_SHARE_APART * 1000;
    }

    /** {main element, second element or -1, main attack's share of the damage in ‰}, worked out once. */
    private static int[] profile(Cand c, int m) {
        if (c.profile == null) {
            Damage.Breakdown b = Damage.breakdown(c.set, c.eval.withTome(), m);
            double total = b.total();
            int first = 0;              // Neutral: no elemental damage at all
            int second = -1;
            for (int i = 1; i < Damage.ELEMENTS.length; i++) {
                if (b.element(i) > (first == 0 ? 0 : b.element(first))) {
                    first = i;
                }
            }
            for (int i = 1; i < Damage.ELEMENTS.length; i++) {
                if (i != first && first > 0 && b.element(i) >= 0.5 * b.element(first)
                        && (second < 0 || b.element(i) > b.element(second))) {
                    second = i;
                }
            }
            c.profile = new int[] {first, second, total > 0 ? (int) Math.round(b.mainTotal() / total * 1000) : 0};
        }
        return c.profile;
    }

    /** The set fits only with the guild tome its spread assumes. */
    private static boolean needsTome(Map<String, Damage.Piece> set, Build.Eval e, int m, int earned) {
        return e.tome() != null && e.tome().assumed() && without(() -> Build.allocate(set, m, earned)) == null;
    }

    /**
     * The best build that fits without the assumed guild tome: among the search's sets and the
     * pick with one piece changed (each slot's best rules.roadmap.tomeAltPool, and what it held
     * before the milestone), judged without the tome. Null when none fits.
     */
    private static Cand withoutTome(MilestonePlanner p, MilestonePicks ms, List<Cand> all, Map<String, Damage.Piece> base,
            int m, int earned, int[] prev) {
        return without(() -> {
            List<Map<String, Damage.Piece>> tries = new ArrayList<>();
            all.forEach(c -> tries.add(c.set));
            int limit = (int) Rules.sub("roadmap", "tomeAltPool", 20);
            for (String slot : Roadmap.BEAM_ORDER) {
                if (p.fixed.contains(slot)) {
                    continue;
                }
                Damage.Piece had = ms.startSet == null ? null : ms.startSet.get(slot);
                if (had != null && !SetJudge.same(base.get(slot), had)) {
                    tries.add(SetJudge.with(base, slot, had));
                }
                Slot t = slot.startsWith("RING") ? Slot.RING : slot.equals("WEAPON") ? null : Slot.valueOf(slot);
                List<ItemData> ranked = t == null ? SetJudge.weaponPool(ms.pools) : ms.pools.get(t);
                for (int i = 0; ranked != null && i < ranked.size() && i < limit; i++) {
                    ItemData info = ranked.get(i);
                    if (info.requirements().level() > m || !CandidateRules.panelAllows(info, m)
                            || Exclusive.clash(base, slot, Exclusive.group(info)) != null) {
                        continue;
                    }
                    Damage.Piece piece = OwnedGear.pieceOf(info);
                    if (!SetJudge.same(base.get(slot), piece)) {
                        tries.add(SetJudge.with(base, slot, piece));
                    }
                }
            }
            Cand best = null;
            for (Map<String, Damage.Piece> s : tries) {
                if (SetJudge.clashes(s) || overBudget(p, s)) {
                    continue;
                }
                Roadmap.tick();
                Build.Eval e = Build.allocate(s, m, earned);
                if (e == null || e.result().objective() <= 0) {
                    continue;
                }
                double j = SetJudge.judged(s, e, prev, m);
                if (best == null || j > best.j || j == best.j && SetJudge.setKey(s).compareTo(SetJudge.setKey(best.set)) < 0) {
                    best = new Cand(s, e, j, true);
                }
            }
            return best;
        });
    }

    /** One card: its label, how far it is below the pick, its numbers, what you have of it and what the rest costs. */
    private static Alternative card(MilestonePlanner p, Cand c, Build.Eval e, double pickJ, boolean needsTome, int m, Step st) {
        int[] prof = profile(c, m);
        String weapon = weaponName(c.set);
        String label = (weapon.isEmpty() ? "?" : weapon) + " · " + ELEMENT_NAMES[prof[0]]
                + (prof[1] > 0 ? "/" + ELEMENT_NAMES[prof[1]] : "") + (c.noTome ? " · " + L.t("wynnlabs.road.alt_no_tome") : "");
        double gap = st == null || pickJ <= 0 ? 0 : (c.j / pickJ - 1) * 100;
        int owned = 0;
        int pieces = 0;
        int unpriced = 0;
        double price = 0;
        boolean priced = false;
        java.util.Set<String> handsHeld = new java.util.HashSet<>();
        for (var en : c.set.entrySet()) {
            Damage.Piece q = en.getValue();
            if (q == null) {
                continue;
            }
            pieces++;
            boolean ring = en.getKey().startsWith("RING");
            boolean have = q.stack() != null ? !ring || handsHeld.add(q.key())
                    : !ring && p.owned.contains(q.name()) && !OwnedGear.buysOwned(q);
            if (have) {
                owned++;
            } else if (!p.fixed.contains(en.getKey())) {
                Prices.Price pr = p.state.price(q.name());
                if (pr != null && pr.typical() != null) {
                    price += pr.typical();
                    priced = true;
                } else {
                    unpriced++;
                }
            }
        }
        Damage.Result r = e.result();
        Tomes.Tome tome = e.tome() != null && e.tome().assumed() ? e.tome() : null;
        return new Alternative(label, weapon, gap, r.spellDps(), r.ehpElemental(), r.healPerSecond(), owned, pieces,
                priced ? price : unpriced > 0 ? Double.NaN : 0, unpriced, tome, needsTome, c.noTome, st);
    }

    /**
     * The milestone's rows as if {@code set} were its pick: a row for each slot it changes from the
     * set the milestone started with (the pick's own row where it holds the pick's piece, else the
     * piece judged against that slot going back), and each row's number and reasons over what
     * you wear ({@link RoadmapGains#versus}), as the pick's are. Abilities and places come from the
     * pick ({@link Step#as}).
     */
    private static Step step(MilestonePlanner p, MilestonePicks ms, Map<String, Damage.Piece> set, Build.Eval eval, int m,
            int earned, int[] prev, List<Gear> pickGear) {
        Map<String, String> placed = new LinkedHashMap<>(p.placed);
        Map<String, Damage.Piece> start = ms.startSet == null ? Map.of() : ms.startSet;
        List<Gear> gear = new ArrayList<>();
        Build.Eval full = Build.allocate(set, m, earned);
        for (String slot : Roadmap.BEAM_ORDER) {
            Damage.Piece want = set.get(slot);
            Damage.Piece had = start.get(slot);
            if (want == null || had != null && had.key().equals(want.key())) {
                continue;
            }
            Gear same = null;
            Damage.Piece pickHere = p.baseline.get(slot);
            if (pickHere != null && pickHere.key().equals(want.key())) {
                for (Gear g : pickGear) {
                    if (slot.equals(p.placed.get(g.slot())) && g.info().name().equalsIgnoreCase(want.name())) {
                        same = g;
                    }
                }
            }
            if (same != null) {
                gear.add(same);
                continue;
            }
            ItemData info = Roadmap.findGear(want.name());
            if (info == null) {
                continue;
            }
            String row = null;
            for (var pe : placed.entrySet()) {
                if (pe.getValue().equals(slot)) {
                    row = pe.getKey();
                }
            }
            if (row == null) {
                row = slot.equals("WEAPON") ? info.slot().name() : !slot.startsWith("RING") ? slot
                        : placed.containsKey(Slot.RING.name()) ? Roadmap.RING2 : Slot.RING.name();
            }
            Map<String, Damage.Piece> back = new LinkedHashMap<>(set);
            if (had == null) {
                back.remove(slot);
            } else {
                back.put(slot, had);
            }
            Build.Eval before = Build.allocate(back, m, earned);
            double gain = before == null || full == null ? 0
                    : (SetJudge.judged(set, full, prev, m) / SetJudge.judged(back, before, prev, m) - 1) * 100;
            Option first = before == null ? null : RoadmapRows.option(info, want, slot, back, before, m, earned);
            List<String> detail = before == null || full == null ? List.of() : RoadmapGains.explain(before, full);
            String src = ShoppingSources.sourceOf(info);
            gear.add(new Gear(row, info, Double.isFinite(gain) ? gain : 0, src == null ? "?" : ShoppingSources.where(src),
                    Prices.buyText(info.name()), null, null, detail, List.of(), first == null ? List.of() : List.of(first)));
            RoadmapRows.place(placed, row, slot);
        }
        Roadmap.Versus vs = p.wornNow == null ? new Roadmap.Versus(Map.of(), Map.of())
                : RoadmapGains.versus(set, p.wornNow, placed, m, earned);
        return new Step(m, List.copyOf(gear), List.of(), List.of(), 0, vs.gain(), vs.why(),
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(set)), eval);
    }

    /** {@code body} with no guild tome assumed (your real tomes still count), as TomeRows judges. */
    private static <T> T without(java.util.function.Supplier<T> body) {
        Tomes.Scope s = Tomes.active();
        return Tomes.with(s == null ? null : new Tomes.Scope(s.real(), false), body);
    }

    private static String weaponName(Map<String, Damage.Piece> set) {
        Damage.Piece w = set.get("WEAPON");
        return w == null ? "" : w.name();
    }
}
