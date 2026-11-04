package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Skill point solving for {@link Build}: the fewest points a set of pieces needs, what a set
 * holds and could reach per skill, and the slot holding each thread's {@link Build.Memo}.
 */
final class SkillSolver {

    private SkillSolver() {}

    /**
     * The fewest points you must assign per skill to wear all these pieces, the way Wynncraft
     * equips them: one at a time, each checked against what is on before it, and at the end
     * against everything worn but its own bonus (a later penalty pops it off). A piece's own
     * positive bonus never counts for itself; its own penalty does (Coeur Brise: Defence 12 with
     * -3 needs 15 assigned, seen in game). Weapons and crafted gear never help other pieces.
     * Exact over every equip order: the pieces that give points (and any that never help but
     * carry a penalty in a skill they need) are ordered by a search over the subsets already on,
     * keeping per subset only the point vectors no other one beats; every other piece goes on
     * last, where its check is never stricter than the final one. Gives the least total, as
     * WynnBuilder does (a Lv 121 raid build with eight point pieces: 186, where a greedy order
     * that raised every requirement of a stuck piece gave 263).
     */
    static int[] minPoints(java.util.Collection<Damage.Piece> pieces) {
        SkillKind[] kinds = SkillKind.values();
        int n = kinds.length;
        List<Damage.Piece> list = new ArrayList<>();
        for (Damage.Piece p : pieces) {
            if (p != null) {
                list.add(p);
            }
        }
        double[] total = new double[n];
        for (Damage.Piece p : list) {
            if (p.helpsRequirements()) {
                for (int i = 0; i < n; i++) {
                    total[i] += bonus(p, kinds[i]);
                }
            }
        }
        // the order-free part: every piece against all the others once everything is on
        int[] base = new int[n];
        List<Damage.Piece> movers = new ArrayList<>();
        for (Damage.Piece p : list) {
            boolean helps = p.helpsRequirements();
            boolean moves = false;
            for (var e : p.reqs().entrySet()) {
                int i = e.getKey().ordinal();
                double b = bonus(p, e.getKey());
                double own = helps ? Math.max(0, b) : 0;
                base[i] = Math.max(base[i], (int) Math.ceil(e.getValue() - (total[i] - own)));
                moves |= !helps && b < 0;           // its own penalty counts: where it goes on matters
            }
            if (helps) {
                for (SkillKind sk : kinds) {
                    moves |= bonus(p, sk) > 0;
                }
            }
            if (moves) {
                movers.add(p);
            }
        }
        int m = movers.size();
        if (m == 0) {
            return base;
        }
        // the movers, most points given first: one order to try at once, and the bound to beat
        movers.sort(java.util.Comparator.comparingDouble(p -> -given(p)));
        double[][] give = new double[m][n];
        int[][] reqAt = new int[m][];
        double[][] reqNeed = new double[m][];
        for (int k = 0; k < m; k++) {
            Damage.Piece p = movers.get(k);
            if (p.helpsRequirements()) {
                for (int i = 0; i < n; i++) {
                    give[k][i] = bonus(p, kinds[i]);
                }
            }
            reqAt[k] = new int[p.reqs().size()];
            reqNeed[k] = new double[p.reqs().size()];
            int j = 0;
            for (var e : p.reqs().entrySet()) {
                reqAt[k][j] = e.getKey().ordinal();
                reqNeed[k][j] = e.getValue() - Math.min(0, bonus(p, e.getKey()));
                j++;
            }
        }
        int[] best = base.clone();
        double[] prefix = new double[n];
        for (int k = 0; k < m; k++) {
            raise(best, reqAt[k], reqNeed[k], prefix);
            for (int i = 0; i < n; i++) {
                prefix[i] += give[k][i];
            }
        }
        int bestSum = sum(best);
        if (bestSum == sum(base) || m > MAX_ORDER_PIECES) {
            return best;                            // the order-free bound is met: nothing is cheaper
        }
        // every order: per subset of movers already on, the point vectors no other one beats
        int full = (1 << m) - 1;
        double[][] pre = new double[full + 1][];
        pre[0] = new double[n];
        @SuppressWarnings("unchecked")
        List<int[]>[] front = new List[full + 1];
        front[0] = new ArrayList<>(List.of(base));
        for (int s = 0; s < full; s++) {
            if (s > 0) {
                int low = Integer.numberOfTrailingZeros(s);
                pre[s] = pre[s & (s - 1)].clone();
                for (int i = 0; i < n; i++) {
                    pre[s][i] += give[low][i];
                }
            }
            if (front[s] == null) {
                continue;
            }
            for (int k = 0; k < m; k++) {
                if ((s & (1 << k)) != 0) {
                    continue;
                }
                int t = s | (1 << k);
                for (int[] v : front[s]) {
                    int[] w = v.clone();
                    raise(w, reqAt[k], reqNeed[k], pre[s]);
                    int ws = sum(w);
                    if (ws >= bestSum) {
                        continue;                   // no better than an order already known
                    }
                    if (t == full) {
                        best = w;
                        bestSum = ws;
                    } else {
                        if (front[t] == null) {
                            front[t] = new ArrayList<>();
                        }
                        keep(front[t], w);
                    }
                }
            }
            front[s] = null;
        }
        return best;
    }

    /**
     * More ordered pieces than a build can hold (eight point pieces and a weapon) and only the
     * most-given-first order is tried: never reached by a real set.
     */
    private static final int MAX_ORDER_PIECES = 12;

    /** Raises {@code need} to what one piece asks with the bonuses {@code prefix} already on. */
    private static void raise(int[] need, int[] at, double[] req, double[] prefix) {
        for (int j = 0; j < at.length; j++) {
            int i = at[j];
            need[i] = Math.max(need[i], (int) Math.ceil(req[j] - prefix[i]));
        }
    }

    /** Adds {@code v} to the vectors kept for a subset unless one of them is no worse in every skill. */
    private static void keep(List<int[]> front, int[] v) {
        for (int[] u : front) {
            if (atMost(u, v)) {
                return;
            }
        }
        front.removeIf(u -> atMost(v, u));
        front.add(v);
    }

    private static boolean atMost(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) {
                return false;
            }
        }
        return true;
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) {
            s += v;
        }
        return s;
    }

    /** The points a piece gives toward others' requirements, penalties left out. */
    private static double given(Damage.Piece p) {
        double g = 0;
        if (p.helpsRequirements()) {
            for (SkillKind sk : SkillKind.values()) {
                g += Math.max(0, bonus(p, sk));
            }
        }
        return g;
    }

    /**
     * The fewest points per skill for this exact equip order: each piece checked against the
     * points plus the bonuses of the pieces before it (its own penalty counts, its own bonus not),
     * and at the end against everything worn but its own bonus (a later penalty pops it off).
     */
    static int[] orderNeed(List<Damage.Piece> order) {
        int n = SkillKind.values().length;
        double[] prefix = new double[n];
        int[] need = new int[n];
        for (Damage.Piece p : order) {
            for (var e : p.reqs().entrySet()) {
                int i = e.getKey().ordinal();
                double own = Math.min(0, bonus(p, e.getKey()));
                need[i] = Math.max(need[i], (int) Math.ceil(e.getValue() - prefix[i] - own));
            }
            if (p.helpsRequirements()) {
                for (SkillKind sk : SkillKind.values()) {
                    prefix[sk.ordinal()] += bonus(p, sk);
                }
            }
        }
        for (Damage.Piece p : order) {
            for (var e : p.reqs().entrySet()) {
                int i = e.getKey().ordinal();
                double own = p.helpsRequirements() ? Math.max(0, bonus(p, e.getKey())) : 0;
                need[i] = Math.max(need[i], (int) Math.ceil(e.getValue() - (prefix[i] - own)));
            }
        }
        for (int i = 0; i < n; i++) {
            need[i] = Math.max(0, need[i]);
        }
        return need;
    }

    /**
     * What a set holds per skill toward requirements: the assigned points plus the bonuses of
     * the pieces whose bonuses count for others (indexed by SkillKind.ordinal()).
     */
    static double[] cover(Map<String, Damage.Piece> build, int[] alloc) {
        double[] c = new double[alloc.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = alloc[i];
        }
        for (Damage.Piece p : build.values()) {
            if (p != null && p.helpsRequirements()) {
                for (SkillKind s : SkillKind.values()) {
                    c[s.ordinal()] += bonus(p, s);
                }
            }
        }
        return c;
    }

    static double bonus(Damage.Piece p, SkillKind s) {
        return p.stats().getOrDefault(RAW_KEY[s.ordinal()], 0.0);
    }

    /** "rawStrength" … per skill, built once: bonus() runs millions of times for a road map. */
    private static final String[] RAW_KEY = java.util.Arrays.stream(SkillKind.values())
            .map(s -> "raw" + s.name().charAt(0) + s.name().substring(1).toLowerCase(java.util.Locale.ROOT))
            .toArray(String[]::new);

    /**
     * The most points per skill a set could get from gear other than a build's own pieces:
     * each armour and accessory slot's best bonus there among {@code pieces} (the two rings
     * twice), plus your tomes. For {@link Build#whyNot}.
     */
    static int[] reach(java.util.Collection<Damage.Piece> pieces) {
        int n = SkillKind.values().length;
        Map<String, double[]> bySlot = new java.util.HashMap<>();
        for (Damage.Piece p : pieces) {
            if (p == null || p.type() == null || p.type().isWeapon() || !p.helpsRequirements()) {
                continue;
            }
            double[] b = bySlot.computeIfAbsent(p.type().name(), k -> new double[n]);
            for (SkillKind s : SkillKind.values()) {
                b[s.ordinal()] = Math.max(b[s.ordinal()], bonus(p, s));
            }
        }
        int[] out = new int[n];
        for (SkillKind s : SkillKind.values()) {
            double sum = 0;
            for (var e : bySlot.entrySet()) {
                sum += e.getValue()[s.ordinal()] * (e.getKey().equals(Slot.RING.name()) ? 2 : 1);
            }
            out[s.ordinal()] = (int) Math.floor(sum) + Wyn.tomePoints(s);
        }
        return out;
    }

    /** The memo's slot per thread, made on first use (a field set where it is declared stays null after a hot swap). */
    private static volatile ThreadLocal<Build.Memo> memoSlot;

    private static ThreadLocal<Build.Memo> memoSlot() {
        ThreadLocal<Build.Memo> t = memoSlot;
        if (t == null) {
            synchronized (SkillSolver.class) {
                if (memoSlot == null) {
                    memoSlot = new ThreadLocal<>();
                }
                t = memoSlot;
            }
        }
        return t;
    }

    /** The memo in force on this thread, or null. */
    static Build.Memo memo() {
        ThreadLocal<Build.Memo> t = memoSlot;
        return t == null ? null : t.get();
    }

    /** Runs {@code work} with {@code m} as this thread's memo (null: none), the one before restored after. */
    static <T> T withMemo(Build.Memo m, java.util.function.Supplier<T> work) {
        ThreadLocal<Build.Memo> t = memoSlot();
        Build.Memo before = t.get();
        t.set(m);
        try {
            return work.get();
        } finally {
            if (before == null) {
                t.remove();
            } else {
                t.set(before);
            }
        }
    }
}
