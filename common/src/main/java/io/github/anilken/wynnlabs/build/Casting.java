package io.github.anilken.wynnlabs.build;

import java.util.Arrays;

/**
 * How often each damage spell is cast, from its damage a cast and its mana a cast: the casts a
 * second that deal the most damage within what the mana pays for and how fast you can click.
 * The best damage per mana goes first while mana is short, the best damage per cast while clicks
 * are short, so a 5-mana Multihit is spammed and a 50-mana Meteor waits for mana, the way players
 * play.
 *
 * <p>It is the linear programme: maximise the sum of {@code x[i] * dmg[i]} subject to the casts a
 * second (sum of x) at most {@code cap}, the mana a second (sum of {@code x[i] * cost[i]}) at most
 * {@code mana}, and each spell at most {@code each[i]}. The mana constraint is moved into the
 * objective with a price per mana; for a price the best casts fill the highest
 * {@code dmg - price * cost} first. The answer is exact: between the two prices where the choice
 * changes, the mix of both that spends exactly the mana.
 */
final class Casting {

    private Casting() {}

    /**
     * Casts a second per spell. {@code dmg} damage a cast, {@code cost} mana a cast (above 0),
     * {@code cap} casts a second at most, {@code mana} mana a second to spend, {@code each} the most
     * casts a second of each spell (a cooldown, a share of the cap). A spell with no damage gets none.
     */
    static double[] allocate(double[] dmg, double[] cost, double cap, double mana, double[] each) {
        int n = dmg.length;
        if (n == 0 || cap <= 0) {
            return new double[n];
        }
        double[] free = fill(dmg, cost, 0, cap, each);
        if (spent(free, cost) <= mana + 1e-9) {
            return free;
        }
        // the prices where the choice can change: where a spell stops paying (its damage per
        // mana), and where two spells swap places
        double[] at = new double[n + n * (n - 1) / 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (dmg[i] > 0) {
                at[k++] = dmg[i] / Math.max(1e-9, cost[i]);
            }
            for (int j = i + 1; j < n; j++) {
                double dc = cost[i] - cost[j];
                if (Math.abs(dc) > 1e-12) {
                    double l = (dmg[i] - dmg[j]) / dc;
                    if (l > 0) {
                        at[k++] = l;
                    }
                }
            }
        }
        double[] prices = Arrays.copyOf(at, k);
        Arrays.sort(prices);
        // up the prices: the first choice that spends no more than the mana, mixed with the one
        // before it so the mana is spent exactly. Past the last price nothing pays: none spent
        double[] before = free;
        double lo = 0;
        for (int p = 0; p <= prices.length; p++) {
            double hi = p < prices.length ? prices[p] : lo * 2 + 1;
            double[] x = fill(dmg, cost, (lo + hi) / 2, cap, each);
            double m = spent(x, cost);
            if (m <= mana + 1e-9) {
                double mb = spent(before, cost);
                double a = mb - m > 1e-12 ? Math.max(0, Math.min(1, (mana - m) / (mb - m))) : 0;
                double[] out = new double[n];
                for (int i = 0; i < n; i++) {
                    out[i] = a * before[i] + (1 - a) * x[i];
                }
                return out;
            }
            before = x;
            lo = hi;
        }
        return new double[n];
    }

    /** At {@code price} a mana: the spells worth the most first, each at most {@code each[i]}, {@code cap} in all. */
    private static double[] fill(double[] dmg, double[] cost, double price, double cap, double[] each) {
        int n = dmg.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        // ties go to the earlier spell, so the answer never hangs on the sort
        Arrays.sort(order, (a, b) -> {
            int c = Double.compare(dmg[b] - price * cost[b], dmg[a] - price * cost[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        double[] x = new double[n];
        double left = cap;
        for (int i : order) {
            if (dmg[i] <= 0 || dmg[i] - price * cost[i] <= 0 || left <= 0) {
                break;
            }
            x[i] = Math.max(0, Math.min(each[i], left));
            left -= x[i];
        }
        return x;
    }

    private static double spent(double[] x, double[] cost) {
        double m = 0;
        for (int i = 0; i < x.length; i++) {
            m += x[i] * cost[i];
        }
        return m;
    }
}
