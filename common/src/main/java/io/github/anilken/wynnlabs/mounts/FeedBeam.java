package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quick feasible plan: a beam over feed counts, ranked like the exact search. Its cost is only used as an upper
 * bound (and as the fallback when the exact search runs out of budget); it is never presented as optimal.
 */
final class FeedBeam {
    private record St(long lim, long lvl, long prevLim, long[] cost, long[] f, int[] acts) {
    }

    private final FeedProblem p;
    private final FeedBound bound;
    private final FeedSearch rules;

    FeedBeam(FeedProblem p, FeedSearch rules) {
        this.p = p;
        this.bound = rules.bound;
        this.rules = rules;
    }

    /** Best goal plan found as acts, or null. {@code costOut} gets its cost (A, B, feeds, level sum). */
    int[] run(int width, long[] costOut) {
        int[] o = p.order;
        Comparator<St> byF = (x, y) -> {
            for (int k = 0; k < 4; k++) {
                if (x.f[k] != y.f[k]) {
                    return Long.compare(x.f[k], y.f[k]);
                }
            }
            return 0;
        };
        List<St> beam = new ArrayList<>();
        beam.add(st(FeedProblem.pack(p.lim0), FeedProblem.pack(p.lvl0), -1, new long[4], new int[0]));
        St best = null;
        int[] lim = new int[8];
        for (int layer = 0; layer < 60 && !beam.isEmpty(); layer++) {
            Map<Long, St> next = new HashMap<>();
            for (St n : beam) {
                FeedWorker.tick();
                if (p.goalOk(n.lim)) {
                    if (best == null || costLess(n.cost, best.cost, o)) {
                        best = n;
                    }
                    continue;
                }
                if (best != null && n.f[0] >= best.cost[o[0]]) {
                    continue;
                }
                int sum = FeedProblem.sum(n.lim);
                int d = MountRules.timerSec(sum);
                int gate = FeedProblem.maxOf(n.lvl);
                int prevD = n.prevLim == -1 ? 0 : MountRules.timerSec(FeedProblem.sum(n.prevLim));
                FeedProblem.unpack(n.lim, lim);
                for (int m = 0; m < p.nMat; m++) {
                    if ((m & 7) == 7) {
                        FeedWorker.tick();
                    }
                    long nl = p.feed(n.lim, m);
                    if (nl == n.lim) {
                        continue;
                    }
                    int L = p.matL[m];
                    int base = p.matKind[m] | p.matLi[m] << 3;
                    if (L <= gate) {
                        offer(next, n, nl, n.lvl, d, d, L, base | FeedProblem.NONE << 7, o);
                    } else {
                        for (int s : p.trainable) {
                            int from = FeedProblem.at(n.lvl, s);
                            if (L > p.top(s) || L > lim[s] || from >= L) {
                                continue;
                            }
                            int levels = L - from, c = levels * p.spl;
                            int hd = rules.hidden(s, levels, L, d, n.prevLim, prevD);
                            offer(next, n, nl, FeedProblem.with(n.lvl, s, L), d + c, d + c - hd, L, base | s << 7, o);
                        }
                    }
                }
            }
            List<St> all = new ArrayList<>(next.values());
            all.sort(byF);
            beam = all.size() > width ? new ArrayList<>(all.subList(0, width)) : all;
        }
        if (best == null) {
            return null;
        }
        System.arraycopy(best.cost, 0, costOut, 0, 4);
        return best.acts;
    }

    private void offer(Map<Long, St> next, St n, long nl, long nlv, int da, int db, int L, int act, int[] o) {
        long[] c = {n.cost[0] + da, n.cost[1] + db, n.cost[2] + 1, n.cost[3] + L};
        if (c[1] > p.maxTime) {
            return;
        }
        long key = FeedBound.mix(nl) ^ nlv * 0x9E3779B97F4A7C15L ^ (p.exactB ? n.lim * 31 : 0);
        St old = next.get(key);
        if (old != null && !costLess(c, old.cost, o)) {
            return;
        }
        int[] acts = java.util.Arrays.copyOf(n.acts, n.acts.length + 1);
        acts[n.acts.length] = act;
        next.put(key, st(nl, nlv, n.lim, c, acts));
    }

    private St st(long lim, long lvl, long prevLim, long[] c, int[] acts) {
        bound.eval(lim, lvl);
        long[] h = {bound.hA, bound.hT, bound.nf, 0};
        int[] o = p.order;
        long[] f = new long[4];
        for (int k = 0; k < 4; k++) {
            f[k] = c[o[k]] + h[o[k]];
        }
        return new St(lim, lvl, prevLim, c, f, acts);
    }

    private static boolean costLess(long[] x, long[] y, int[] o) {
        for (int k : o) {
            if (x[k] != y[k]) {
                return x[k] < y[k];
            }
        }
        return false;
    }
}
