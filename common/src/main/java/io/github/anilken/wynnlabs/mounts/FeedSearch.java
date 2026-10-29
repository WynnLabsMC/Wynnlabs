package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Exact A* over (8 limits, 8 levels[, previous limits]) with the reference planner's rules, cost order and tie
 * order. Two exact speed-ups keep the result identical to a plain run:
 * <ul>
 * <li>a stronger admissible bound ({@link FeedBound});</li>
 * <li>an upper bound from a quick beam pass ({@link FeedBeam}): a child whose priority's first component is above it
 * could never be popped before the goal, so it isn't stored (the reference stored it and never touched it again);</li>
 * <li>with a time cap on a full-limits goal, a node whose best reachable limit sum can't beat the best one found is
 * not expanded.</li>
 * </ul>
 */
final class FeedSearch {
    /** Feeds in order: kind | levelIndex << 3 | trainedStat << 7 (15 = none). */
    static final class Result {
        int[] acts = new int[0];
        boolean goalReached;
        boolean complete;
        long explored;
        int stored;
        long bytes;
        long[] cost = new long[4];
        /** Smallest bound of a dropped child (above the cap), or Long.MAX_VALUE when nothing was dropped. */
        long minPruned = Long.MAX_VALUE;
    }

    /** Stored-state cap (about 45 bytes each): keeps one search under ~100 MB whatever the input. */
    static final int MAX_STORED = 2_000_000;

    private final FeedProblem p;
    final FeedBound bound;
    private final int[] o;

    FeedSearch(FeedProblem p) {
        this(p, 20);
    }

    /** {@code memoBits}: log2 of the bound's memo size (20 = about 20 MB, for the exact search). */
    FeedSearch(FeedProblem p, int memoBits) {
        this.p = p;
        this.bound = new FeedBound(p, memoBits);
        this.o = p.order;
    }

    private int[] hv(long lim, long lvl) {
        bound.eval(lim, lvl);
        return new int[] {bound.hA, bound.hT, bound.nf, 0};
    }

    /** Strong bound of the last {@link #hv} call for cost component k. */
    private int strong(int k) {
        return k == 0 ? bound.sA : k == 1 ? bound.sT : 0;
    }

    private Result cur;
    /** Live progress for a running job (may be null). */
    FeedPlanner.Stats live;
    long liveBase;

    /** Strong lower bound on the real time (B) of any plan reaching the goal. */
    long startBoundB() {
        bound.eval(FeedProblem.pack(p.lim0), FeedProblem.pack(p.lvl0));
        return bound.sT;
    }

    /** Strong lower bound on the primary cost of any plan. */
    long startBound() {
        bound.eval(FeedProblem.pack(p.lim0), FeedProblem.pack(p.lvl0));
        return strong(o[0]);
    }

    Result run(long upper0, long stateBudget, long deadlineNanos, BooleanSupplier cancelled) {
        FeedNodes N = new FeedNodes(p.exactB);
        long startLim = FeedProblem.pack(p.lim0), startLvl = FeedProblem.pack(p.lvl0);
        int start = N.add(startLim, startLvl, 0, 0, 0, 0, -1, 0);
        N.put(start, -1);
        pushNode(N, start, startLim, startLvl, new int[] {0, 0, 0, 0});
        long upper = upper0;
        Result r = new Result();
        cur = r;
        int best = start;
        int bestProg = p.progress(startLim);
        int found = -1;
        boolean progressPrune = !p.custom && p.maxTime != Long.MAX_VALUE;
        int[] seenTag = new int[4096];
        long[] seenKey = new long[4096];
        int stamp = 0;
        int[] stampOf = new int[4096];
        int[] lim = new int[8];
        boolean complete = true;
        while (N.heapSize > 0) {
            int node = N.pop();
            if (N.closed(node)) {
                continue;
            }
            N.close(node);
            r.explored++;
            if ((r.explored & 15) == 0) {
                FeedWorker.tick();
                if (live != null) {
                    live.liveExplored = liveBase + r.explored;
                    live.liveStored = N.size;
                }
            }
            if (r.explored > stateBudget || N.size > MAX_STORED || ((r.explored & 1023) == 0
                    && (System.nanoTime() > deadlineNanos || cancelled.getAsBoolean()))) {
                complete = false;
                break;
            }
            long li = N.lim(node), lv = N.lvl(node);
            int pg = p.progress(li);
            if (pg > bestProg || (pg == bestProg && costLess(N, node, best))) {
                best = node;
                bestProg = pg;
            }
            if (p.goalOk(li)) {
                found = node;
                break;
            }
            int sum = FeedProblem.sum(li);
            int d = MountRules.timerSec(sum);
            if (progressPrune && cannotBeat(N, node, li, sum, d, best, bestProg)) {
                continue;
            }
            int gate = FeedProblem.maxOf(lv);
            int par = N.parent(node);
            long prevLim = par < 0 ? -1 : N.lim(par);
            int prevD = par < 0 ? 0 : MountRules.timerSec(FeedProblem.sum(prevLim));
            int ca = N.a(node), cb = N.b(node), cf = N.feeds(node), cl = N.levelSum(node);
            stamp++;
            FeedProblem.unpack(li, lim);
            for (int m = 0; m < p.nMat; m++) {
                if ((m & 7) == 7) {
                    FeedWorker.tick();
                }
                long nl = p.feed(li, m);
                if (nl == li) {
                    continue;
                }
                int L = p.matL[m];
                int base = p.matKind[m] | p.matLi[m] << 3;
                if (L <= gate) {
                    if (!seen(seenKey, seenTag, stampOf, stamp, nl, 0)) {
                        continue;
                    }
                    upper = push(N, node, nl, lv, ca + d, cb + d, cf + 1, cl + L, base | FeedProblem.NONE << 7, li,
                            upper);
                } else {
                    for (int s : p.trainable) {
                        int from = FeedProblem.at(lv, s);
                        if (L > p.top(s) || L > lim[s] || from >= L) {
                            continue;
                        }
                        if (!seen(seenKey, seenTag, stampOf, stamp, nl, L << 4 | (s + 1))) {
                            continue;
                        }
                        int levels = L - from, c = levels * p.spl;
                        int hd = hidden(s, levels, L, d, prevLim, prevD);
                        upper = push(N, node, nl, FeedProblem.with(lv, s, L), ca + d + c, cb + d + c - hd, cf + 1,
                                cl + L, base | s << 7, li, upper);
                    }
                }
            }
        }
        r.complete = complete;
        r.goalReached = found >= 0;
        int end = found >= 0 ? found : best;
        List<Integer> acts = new ArrayList<>();
        for (int n = end; N.parent(n) >= 0; n = N.parent(n)) {
            acts.add(N.act(n));
        }
        r.acts = new int[acts.size()];
        for (int i = 0; i < r.acts.length; i++) {
            r.acts[i] = acts.get(acts.size() - 1 - i);
        }
        for (int k = 0; k < 4; k++) {
            r.cost[k] = N.cost(end, k);
        }
        r.stored = N.size;
        r.bytes = N.bytes();
        return r;
    }

    /** Hidden part of a training step (the larger of hiding under the previous timer and a queue swap). */
    int hidden(int s, int levels, int L, int d, long prevLim, int prevD) {
        int hN = prevLim != -1 && L <= FeedProblem.at(prevLim, s) ? Math.min(levels, p.covLv(prevD)) * p.spl : 0;
        int hS = p.swaps ? Math.min(levels, p.covLv(d)) * p.spl : 0;
        return Math.max(hN, hS);
    }

    private static boolean seen(long[] key, int[] tag, int[] stampOf, int stamp, long nl, int t) {
        int mask = key.length - 1;
        int h = (int) FeedBound.mix(nl * 131 + t) & mask;
        while (stampOf[h] == stamp) {
            if (key[h] == nl && tag[h] == t) {
                return false;
            }
            h = (h + 1) & mask;
        }
        stampOf[h] = stamp;
        key[h] = nl;
        tag[h] = t;
        return true;
    }

    private long push(FeedNodes N, int parent, long nl, long nlv, int ca, int cb, int cf, int cl, int ac,
                      long parentLim, long upper) {
        if (cb > p.maxTime) {
            return upper;
        }
        int[] c = {ca, cb, cf, cl};
        int old = N.find(nl, nlv, parentLim);
        if (old >= 0 && !less(c, N, old)) {
            return upper;
        }
        int[] h = hv(nl, nlv);
        long f0 = (long) c[o[0]] + strong(o[0]);
        if (f0 > upper) {
            cur.minPruned = Math.min(cur.minPruned, f0);
            return upper;
        }
        if (old >= 0) {
            N.close(old);
        }
        int n = N.add(nl, nlv, ca, cb, cf, cl, parent, ac);
        N.put(n, parentLim);
        pushPri(N, n, c, h);
        return upper;
    }

    private void pushNode(FeedNodes N, int n, long li, long lv, int[] c) {
        pushPri(N, n, c, hv(li, lv));
    }

    private void pushPri(FeedNodes N, int n, int[] c, int[] h) {
        long f0 = (long) c[o[0]] + h[o[0]], f1 = (long) c[o[1]] + h[o[1]];
        long f2 = (long) c[o[2]] + h[o[2]], f3 = (long) c[o[3]] + h[o[3]];
        N.push(f0 << 32 | f1, f2 << 32 | f3, n);
    }

    private boolean less(int[] c, FeedNodes N, int old) {
        for (int k : o) {
            int x = c[k], y = N.cost(old, k);
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    private boolean costLess(FeedNodes N, int x, int y) {
        for (int k : o) {
            int a = N.cost(x, k), b = N.cost(y, k);
            if (a != b) {
                return a < b;
            }
        }
        return false;
    }

    /**
     * Full-limits goal with a time cap: every later feed costs at least the current timer, so at most
     * floor(timeLeft / timer) feeds fit; if even that many can't lift the limit sum past the best found (or tie it
     * more cheaply, costs only grow), nothing below this node can be chosen.
     */
    private boolean cannotBeat(FeedNodes N, int node, long li, int sum, int d, int best, int bestProg) {
        long left = p.maxTime - N.b(node);
        long k = left / d;
        long gain = 0, dsum = 0;
        for (int i = 0; i < 8; i++) {
            int def = p.mx[i] - FeedProblem.at(li, i);
            dsum += def;
        }
        gain = Math.min(dsum, k * p.maxPts);
        long reach = sum + gain;
        return reach < bestProg || (reach == bestProg && !costLess(N, node, best));
    }
}
