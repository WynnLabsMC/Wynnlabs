package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.List;

/** One planning input in flat arrays, shared by the search, the bound, the beam and the replay. */
final class FeedProblem {
    static final int NONE = 15;

    final int[] lim0 = new int[8];
    final int[] lvl0 = new int[8];
    final int[] mx = new int[8];
    final int[] need;
    final int minSum;
    final boolean custom;
    final int targetMask;
    final int spl;
    final int cap;
    final boolean swaps;
    final int[] trainable;
    final long maxTime;
    /** Cost component order: 0 = A (training in full), 1 = B (real time), 2 = feeds, 3 = level sum. */
    final int[] order;
    /** The previous feed's limits are part of the state (real time matters). */
    final boolean exactB;
    final boolean boundTraining;
    final int[] usable;
    final int topLevel;
    // materials in expansion order: levels ascending, then kind order
    final int nMat;
    final int[] matKind;
    final int[] matLi;
    final int[] matL;
    final int[][] matStat;
    final int[][] matPts;
    final int maxPts;
    final int maxOne;

    FeedProblem(MountStats s, FeedGoal goal, FeedOptions o) {
        for (int i = 0; i < 8; i++) {
            lim0[i] = s.limit(i);
            lvl0[i] = s.level(i);
            mx[i] = s.max(i);
            if (mx[i] > 255 || lvl0[i] > 255 || lim0[i] > 255) {
                throw new IllegalArgumentException("stat above 255");
            }
            if (lim0[i] > mx[i]) {
                lim0[i] = mx[i];
            }
        }
        need = goal.need(s);
        custom = !goal.fullLimits();
        minSum = custom ? 8 * goal.minAvgLimit() : 0;
        int tm = 0;
        for (MountStat t : goal.targets().keySet()) {
            tm |= 1 << t.ordinal();
        }
        targetMask = custom ? tm : 0xFF;
        spl = Math.max(0, o.trainSecPerLevel());
        cap = o.trainCap() <= 0 ? Integer.MAX_VALUE : o.trainCap();
        swaps = o.allowQueueSwaps();
        trainable = o.trainFocus() == null ? new int[] {0, 1, 2, 3, 4, 5, 6, 7} : new int[] {o.trainFocus().ordinal()};
        maxTime = o.maxTimeSec() <= 0 ? Long.MAX_VALUE : o.maxTimeSec();
        boolean wall = o.objective() == FeedOptions.Objective.WALL_CLOCK;
        order = wall ? new int[] {1, 0, 2, 3} : new int[] {0, 2, 3, 1};
        exactB = wall || maxTime != Long.MAX_VALUE;
        boundTraining = !wall && spl > 0;
        int hi = 0;
        for (int i = 0; i < 8; i++) {
            hi = Math.max(hi, lvl0[i]);
        }
        for (int t : trainable) {
            hi = Math.max(hi, top(t));
        }
        List<Integer> u = new ArrayList<>();
        for (int L : MountRules.LEVELS) {
            if (L <= hi) {
                u.add(L);
            }
        }
        if (u.isEmpty()) {
            u.add(MountRules.LEVELS[0]);
        }
        usable = u.stream().mapToInt(Integer::intValue).toArray();
        topLevel = usable[usable.length - 1];
        MaterialKind[] kinds = MaterialKind.values();
        nMat = usable.length * kinds.length;
        matKind = new int[nMat];
        matLi = new int[nMat];
        matL = new int[nMat];
        matStat = new int[nMat][];
        matPts = new int[nMat][];
        int n = 0, mp = 0, m1 = 0;
        for (int li = 0; li < usable.length; li++) {
            for (MaterialKind k : kinds) {
                matKind[n] = k.ordinal();
                matLi[n] = li;
                matL[n] = usable[li];
                matStat[n] = k.stats().stream().mapToInt(Enum::ordinal).toArray();
                matPts[n] = k.points(usable[li]);
                int sum = 0;
                for (int p : matPts[n]) {
                    sum += p;
                    m1 = Math.max(m1, p);
                }
                mp = Math.max(mp, sum);
                n++;
            }
        }
        maxPts = mp;
        maxOne = m1;
    }

    int top(int i) {
        return Math.min(mx[i], cap);
    }

    /** Whole levels of training that fit under a timer of d seconds. */
    int covLv(int d) {
        return spl > 0 ? d / spl : 1_000_000_000;
    }

    static long pack(int[] v) {
        long k = 0;
        for (int i = 7; i >= 0; i--) {
            k = (k << 8) | (v[i] & 0xFF);
        }
        return k;
    }

    static int at(long packed, int i) {
        return (int) (packed >>> (8 * i)) & 0xFF;
    }

    static void unpack(long packed, int[] out) {
        for (int i = 0; i < 8; i++) {
            out[i] = (int) (packed >>> (8 * i)) & 0xFF;
        }
    }

    static int sum(long packed) {
        int s = 0;
        for (int i = 0; i < 8; i++) {
            s += (int) (packed >>> (8 * i)) & 0xFF;
        }
        return s;
    }

    static int maxOf(long packed) {
        int s = 0;
        for (int i = 0; i < 8; i++) {
            s = Math.max(s, (int) (packed >>> (8 * i)) & 0xFF);
        }
        return s;
    }

    boolean goalOk(long lim) {
        int s = 0;
        for (int i = 0; i < 8; i++) {
            int v = at(lim, i);
            if (v < need[i]) {
                return false;
            }
            s += v;
        }
        return s >= minSum;
    }

    int progress(long lim) {
        int s = 0;
        for (int i = 0; i < 8; i++) {
            if ((targetMask & (1 << i)) != 0) {
                s += at(lim, i);
            }
        }
        return s;
    }

    /** Limits after feeding material m (capped at max); equal to {@code lim} when nothing changes. */
    long feed(long lim, int m) {
        long out = lim;
        int[] st = matStat[m], p = matPts[m];
        for (int j = 0; j < st.length; j++) {
            int i = st[j];
            int cur = at(out, i);
            int v = Math.min(mx[i], cur + p[j]);
            if (v != cur) {
                out = (out & ~(0xFFL << (8 * i))) | ((long) v << (8 * i));
            }
        }
        return out;
    }

    static long with(long packed, int i, int v) {
        return (packed & ~(0xFFL << (8 * i))) | ((long) v << (8 * i));
    }
}
