package io.github.anilken.wynnlabs.mounts;

/**
 * Lower bounds for the feed search.
 *
 * <p>{@code hA/hT/nf}: the reference planner's bound (deficit / best material points, timers of that many feeds
 * from the end). The search orders its queue by it, so equal plans are chosen exactly as the reference does.
 *
 * <p>{@code sA/sT}: a much stronger admissible bound, used only to drop states that can't be on a plan within the
 * cost cap. Full-limits goal: let D be the remaining deficit and U(j) an upper bound on the points the last j feeds
 * can still add (points above a max are lost); the j-th feed from the end starts at a limit sum of at least
 * S_end - U(j), so its timer is at least timer(that). U(j) is the least of: D; per stat min(deficit, j x best points
 * to that stat); and the exact best of 1, 2 or 3 feeds, chained (U(a+b) <= U(a) + U(b)). Feeds above the gate need
 * training: a bound per highest material level T, with T's points for every feed plus (T - best trainable level)
 * x secPerLevel of training counted in full, minimised over T. Custom goals use the reference bound for both.
 * Memoised per (limits, gate level, best trainable level).
 */
final class FeedBound {
    private final FeedProblem p;
    private final int[][][] kStat;
    private final int[][][] kPts;
    private final int[][] mI;
    private final int[] dd = new int[8];
    private final int[] tmp = new int[8];
    private final int[] uj = new int[256];
    private int outT;
    // memo: open addressing on (limits, gateIndex << 8 | base)
    private final int memoBits;
    private final long[] mKey;
    private final int[] mKey2;
    private final int[] mA;
    private final int[] mT;
    private int memoUsed;

    /** Results of the last {@link #eval}: the reference bound (A, timers, feeds) and the strong bound (A, timers). */
    int hA;
    int hT;
    int nf;
    int sA;
    int sT;

    FeedBound(FeedProblem p, int memoBits) {
        this.memoBits = memoBits;
        mKey = new long[1 << memoBits];
        mKey2 = new int[1 << memoBits];
        mA = new int[1 << memoBits];
        mT = new int[1 << memoBits];
        this.p = p;
        int levels = p.usable.length;
        kStat = new int[levels][8][];
        kPts = new int[levels][8][];
        mI = new int[levels][8];
        for (int m = 0; m < p.nMat; m++) {
            int li = p.matLi[m], k = p.matKind[m];
            kStat[li][k] = p.matStat[m];
            kPts[li][k] = p.matPts[m];
            for (int j = 0; j < p.matStat[m].length; j++) {
                int i = p.matStat[m][j];
                mI[li][i] = Math.max(mI[li][i], p.matPts[m][j]);
            }
        }
        java.util.Arrays.fill(mKey2, -1);
    }

    void eval(long lim, long lvl) {
        plain(lim);
        if (p.custom) {
            sA = hA;
            sT = hT;
            return;
        }
        int g = FeedProblem.maxOf(lvl);
        int base = 0;
        for (int t : p.trainable) {
            base = Math.max(base, FeedProblem.at(lvl, t));
        }
        int li0 = 0;
        while (li0 + 1 < p.usable.length && p.usable[li0 + 1] <= g) {
            li0++;
        }
        int key2 = li0 << 8 | (p.boundTraining ? base : 0);
        int h = (int) mix(lim * 31 + key2) & ((1 << memoBits) - 1);
        while (mKey2[h] != -1) {
            if (mKey[h] == lim && mKey2[h] == key2) {
                sA = Math.max(hA, mA[h]);
                sT = Math.max(hT, mT[h]);
                return;
            }
            h = (h + 1) & ((1 << memoBits) - 1);
        }
        full(lim, li0, base);
        if (memoUsed > (1 << memoBits) * 7 / 10) {
            java.util.Arrays.fill(mKey2, -1);
            memoUsed = 0;
            h = (int) mix(lim * 31 + key2) & ((1 << memoBits) - 1);
        }
        mKey[h] = lim;
        mKey2[h] = key2;
        mA[h] = sA;
        mT[h] = sT;
        memoUsed++;
        sA = Math.max(hA, sA);
        sT = Math.max(hT, sT);
    }

    static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    private void plain(long lim) {
        int s = 0, ds = 0, nfStat = 0;
        for (int i = 0; i < 8; i++) {
            int v = FeedProblem.at(lim, i);
            s += v;
            int d = p.need[i] - v;
            if (d > 0) {
                ds += d;
                nfStat = Math.max(nfStat, (d + p.maxOne - 1) / p.maxOne);
            }
        }
        int deficit = Math.max(ds, p.minSum - s);
        if (deficit <= 0) {
            hA = hT = nf = 0;
            return;
        }
        int n = Math.max((deficit + p.maxPts - 1) / p.maxPts, nfStat);
        int t = 0;
        for (int j = 1; j <= n; j++) {
            t += MountRules.timerSec(Math.max(s, s + deficit - j * p.maxPts));
        }
        hA = hT = t;
        nf = n;
    }

    private void full(long lim, int li0, int base) {
        int s = 0, d = 0;
        for (int i = 0; i < 8; i++) {
            int v = FeedProblem.at(lim, i);
            s += v;
            dd[i] = Math.max(0, p.need[i] - v);
            d += dd[i];
        }
        if (d <= 0) {
            sA = sT = 0;
            return;
        }
        cover = minCover(d);
        // more points per feed never cost more timer time, so the top level gives the least timers (bestT) and,
        // with training, a level li can only win while bestT + its training stays below the best so far
        int top = p.usable.length - 1;
        timersFor(top, s, d);
        int bestT = outT;
        if (!p.boundTraining || top == li0) {
            sA = sT = bestT;
            return;
        }
        int best = bestT + (p.usable[top] - base) * p.spl;
        timersFor(li0, s, d);
        best = Math.min(best, outT);
        for (int li = li0 + 1; li < top; li++) {
            int train = (p.usable[li] - base) * p.spl;
            if (bestT + train >= best) {
                break;
            }
            timersFor(li, s, d);
            best = Math.min(best, outT + train);
        }
        sA = best;
        sT = bestT;
    }

    private int cover;
    private final int[] rem = new int[8];

    /**
     * Fewest feeds that can fill every remaining deficit, with the top usable level's points for every feed (a
     * lower level never gives more to any stat). Exact small integer cover by iterative deepening.
     */
    private int minCover(int d) {
        int top = p.usable.length - 1;
        int lb = 0;
        for (int i = 0; i < 8; i++) {
            if (dd[i] > 0) {
                if (mI[top][i] == 0) {
                    return 0;
                }
                lb = Math.max(lb, (dd[i] + mI[top][i] - 1) / mI[top][i]);
            }
        }
        lb = Math.max(lb, (d + p.maxPts - 1) / p.maxPts);
        System.arraycopy(dd, 0, rem, 0, 8);
        for (int n = Math.max(1, lb); n < 64; n++) {
            if (coverable(top, 0, n, d)) {
                return n;
            }
        }
        return lb;
    }

    /** Can {@code n} more feeds of kinds >= k fill rem (total remaining deficit d)? */
    private boolean coverable(int li, int k, int n, int d) {
        if (d <= 0) {
            return true;
        }
        if (n == 0 || k >= 8 || (long) n * p.maxPts < d) {
            return false;
        }
        int[] m = mI[li];
        for (int i = 0; i < 8; i++) {
            if (rem[i] > 0 && (long) n * m[i] < rem[i]) {
                return false;
            }
        }
        int[] st = kStat[li][k], pt = kPts[li][k];
        int[] taken = new int[st.length];
        // use kind k once more (stay on k), or move on to the next kind
        int got = 0;
        for (int j = 0; j < st.length; j++) {
            taken[j] = Math.min(rem[st[j]], pt[j]);
            got += taken[j];
        }
        if (got > 0) {
            for (int j = 0; j < st.length; j++) {
                rem[st[j]] -= taken[j];
            }
            boolean ok = coverable(li, k, n - 1, d - got);
            for (int j = 0; j < st.length; j++) {
                rem[st[j]] += taken[j];
            }
            if (ok) {
                return true;
            }
        }
        return coverable(li, k + 1, n, d);
    }

    /** Useful points of feeding kinds a (and b, c when >= 0) at level li, from the deficits dd. */
    private int useful(int li, int a, int b, int c) {
        System.arraycopy(dd, 0, tmp, 0, 8);
        int u = 0;
        u += take(kStat[li][a], kPts[li][a]);
        if (b >= 0) {
            u += take(kStat[li][b], kPts[li][b]);
        }
        if (c >= 0) {
            u += take(kStat[li][c], kPts[li][c]);
        }
        return u;
    }

    private int take(int[] st, int[] pt) {
        int u = 0;
        for (int j = 0; j < st.length; j++) {
            int v = Math.min(tmp[st[j]], pt[j]);
            u += v;
            tmp[st[j]] -= v;
        }
        return u;
    }

    private void timersFor(int li, int s, int d) {
        int u1 = 0, u2 = 0, u3 = 0;
        for (int x = 0; x < 8; x++) {
            u1 = Math.max(u1, useful(li, x, -1, -1));
            for (int y = x; y < 8; y++) {
                u2 = Math.max(u2, useful(li, x, y, -1));
                for (int z = y; z < 8; z++) {
                    u3 = Math.max(u3, useful(li, x, y, z));
                }
            }
        }
        int[] m = mI[li];
        int tt = 0;
        uj[0] = 0;
        for (int j = 1; ; j++) {
            int a = 0;
            for (int i = 0; i < 8; i++) {
                a += Math.min(dd[i], j * m[i]);
            }
            int u = Math.min(d, Math.min(j * u1, a));
            if (j >= 2) {
                u = Math.min(u, u2 + uj[j - 2]);
            }
            if (j == 3) {
                u = Math.min(u, u3);
            }
            if (j >= 3) {
                u = Math.min(u, u3 + uj[j - 3]);
            }
            uj[j] = u;
            tt += MountRules.timerSec(Math.max(s, s + d - u));
            if ((u >= d && j >= cover) || j >= 250) {
                outT = tt;
                return;
            }
        }
    }
}
