package io.github.anilken.wynnlabs.mounts;

import java.util.Arrays;

/**
 * Search nodes in flat chunked primitive arrays (about 45 bytes a node with its hash slot and heap entry), a
 * hash index from state key to the node holding it, and a binary heap of (priority, node).
 */
final class FeedNodes {
    private static final int CB = 16;
    private static final int CS = 1 << CB;
    private static final int CM = CS - 1;

    private long[][] lim = new long[16][];
    private long[][] lvl = new long[16][];
    private int[][] a = new int[16][];
    private int[][] b = new int[16][];
    /** feeds << 16 | level sum; bit 31 = closed. */
    private int[][] fl = new int[16][];
    private int[][] parent = new int[16][];
    /** kind | levelIndex << 3 | trained stat << 7 (15 = none). */
    private short[][] act = new short[16][];
    int size;

    // hash index: slot -> node + 1
    private int[] slots = new int[1 << 16];
    private int used;
    private final boolean withParent;

    // heap
    private long[] hk1 = new long[1 << 12];
    private long[] hk2 = new long[1 << 12];
    private int[] hn = new int[1 << 12];
    int heapSize;

    FeedNodes(boolean keyHasParentLimits) {
        this.withParent = keyHasParentLimits;
    }

    int add(long li, long lv, int ca, int cb, int feeds, int lsum, int par, int ac) {
        int n = size++;
        int c = n >>> CB;
        if (c >= lim.length) {
            int len = lim.length * 2;
            lim = Arrays.copyOf(lim, len);
            lvl = Arrays.copyOf(lvl, len);
            a = Arrays.copyOf(a, len);
            b = Arrays.copyOf(b, len);
            fl = Arrays.copyOf(fl, len);
            parent = Arrays.copyOf(parent, len);
            act = Arrays.copyOf(act, len);
        }
        if (lim[c] == null) {
            lim[c] = new long[CS];
            lvl[c] = new long[CS];
            a[c] = new int[CS];
            b[c] = new int[CS];
            fl[c] = new int[CS];
            parent[c] = new int[CS];
            act[c] = new short[CS];
        }
        int o = n & CM;
        lim[c][o] = li;
        lvl[c][o] = lv;
        a[c][o] = ca;
        b[c][o] = cb;
        fl[c][o] = feeds << 16 | lsum;
        parent[c][o] = par;
        act[c][o] = (short) ac;
        return n;
    }

    long lim(int n) {
        return lim[n >>> CB][n & CM];
    }

    long lvl(int n) {
        return lvl[n >>> CB][n & CM];
    }

    int a(int n) {
        return a[n >>> CB][n & CM];
    }

    int b(int n) {
        return b[n >>> CB][n & CM];
    }

    int feeds(int n) {
        return (fl[n >>> CB][n & CM] >>> 16) & 0x7FFF;
    }

    int levelSum(int n) {
        return fl[n >>> CB][n & CM] & 0xFFFF;
    }

    boolean closed(int n) {
        return fl[n >>> CB][n & CM] < 0;
    }

    void close(int n) {
        fl[n >>> CB][n & CM] |= 0x80000000;
    }

    int parent(int n) {
        return parent[n >>> CB][n & CM];
    }

    int act(int n) {
        return act[n >>> CB][n & CM];
    }

    /** Cost component k (0 A, 1 B, 2 feeds, 3 level sum). */
    int cost(int n, int k) {
        return switch (k) {
            case 0 -> a(n);
            case 1 -> b(n);
            case 2 -> feeds(n);
            default -> levelSum(n);
        };
    }

    // ---- hash index ----

    private int hash(long li, long lv, long pl) {
        long h = FeedBound.mix(li * 0x9E3779B97F4A7C15L ^ FeedBound.mix(lv + 0x632BE59BD9B4E019L) ^ (pl * 31));
        return (int) h;
    }

    private long parentLim(int n) {
        int par = parent(n);
        return par < 0 ? -1 : lim(par);
    }

    /** Node holding this key, or -1. {@code pl} = the previous feed's limits (only used with parent keys). */
    int find(long li, long lv, long pl) {
        int mask = slots.length - 1;
        long pk = withParent ? pl : 0;
        int h = hash(li, lv, pk) & mask;
        for (int s; (s = slots[h]) != 0; h = (h + 1) & mask) {
            int n = s - 1;
            if (lim(n) == li && lvl(n) == lv && (!withParent || parentLim(n) == pl)) {
                return n;
            }
        }
        return -1;
    }

    /** Points this key at node n (insert or replace). */
    void put(int n, long pl) {
        if ((used + 1) * 10L > slots.length * 6L) {
            rehash();
        }
        long li = lim(n), lv = lvl(n);
        int mask = slots.length - 1;
        int h = hash(li, lv, withParent ? pl : 0) & mask;
        for (int s; (s = slots[h]) != 0; h = (h + 1) & mask) {
            int o = s - 1;
            if (lim(o) == li && lvl(o) == lv && (!withParent || parentLim(o) == pl)) {
                slots[h] = n + 1;
                return;
            }
        }
        slots[h] = n + 1;
        used++;
    }

    private void rehash() {
        int[] old = slots;
        slots = new int[old.length * 2];
        int mask = slots.length - 1;
        for (int s : old) {
            if (s != 0) {
                int n = s - 1;
                int h = hash(lim(n), lvl(n), withParent ? parentLim(n) : 0) & mask;
                while (slots[h] != 0) {
                    h = (h + 1) & mask;
                }
                slots[h] = s;
            }
        }
    }

    long bytes() {
        long chunks = 0;
        for (long[] c : lim) {
            if (c != null) {
                chunks++;
            }
        }
        return chunks * CS * (8 + 8 + 4 + 4 + 4 + 4 + 2) + slots.length * 4L + hk1.length * 20L;
    }

    // ---- heap of (k1, k2, node): k1 = f0 << 32 | f1, k2 = f2 << 32 | f3; ties by node (= creation order) ----

    void push(long k1, long k2, int node) {
        if (heapSize == hk1.length) {
            hk1 = Arrays.copyOf(hk1, heapSize * 2);
            hk2 = Arrays.copyOf(hk2, heapSize * 2);
            hn = Arrays.copyOf(hn, heapSize * 2);
        }
        int i = heapSize++;
        while (i > 0) {
            int q = (i - 1) >> 1;
            if (!less(k1, k2, node, hk1[q], hk2[q], hn[q])) {
                break;
            }
            hk1[i] = hk1[q];
            hk2[i] = hk2[q];
            hn[i] = hn[q];
            i = q;
        }
        hk1[i] = k1;
        hk2[i] = k2;
        hn[i] = node;
    }

    /** Pops the smallest entry and returns its node. */
    int pop() {
        int top = hn[0];
        int last = --heapSize;
        long k1 = hk1[last], k2 = hk2[last];
        int nd = hn[last];
        int i = 0;
        for (;;) {
            int l = 2 * i + 1;
            if (l >= heapSize) {
                break;
            }
            int r = l + 1;
            int m = r < heapSize && less(hk1[r], hk2[r], hn[r], hk1[l], hk2[l], hn[l]) ? r : l;
            if (!less(hk1[m], hk2[m], hn[m], k1, k2, nd)) {
                break;
            }
            hk1[i] = hk1[m];
            hk2[i] = hk2[m];
            hn[i] = hn[m];
            i = m;
        }
        if (heapSize > 0) {
            hk1[i] = k1;
            hk2[i] = k2;
            hn[i] = nd;
        }
        return top;
    }

    private static boolean less(long a1, long a2, int an, long b1, long b2, int bn) {
        return a1 != b1 ? a1 < b1 : a2 != b2 ? a2 < b2 : an < bn;
    }
}
