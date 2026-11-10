package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.search.BoxMarket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How deep an item's market is: its current listings summed per price, cheapest first, and
 * whether only a few sit at the lowest price before a wall of dearer ones (someone holding the
 * rest high). Listings marked possibly sold are left out. Pure.
 */
public final class Depth {

    /** A price and how many items are listed at it. */
    public record Step(int price, int amount) {}

    /** A listing this much above the lowest still counts as the lowest price. */
    private static final double SAME = 1.25;
    /** A wall: the next price at least this many times the lowest. */
    private static final double WALL = 2.0;
    /** ...and the cheap ones at most this share of everything listed. */
    private static final double FEW = 0.10;

    private Depth() {}

    /** The listings summed per price, cheapest first. */
    public static List<Step> ladder(List<BoxMarket.Listing> listings) {
        Map<Integer, Integer> by = new TreeMap<>();
        for (BoxMarket.Listing l : listings) {
            if (!l.possiblySold() && l.price() > 0) {
                by.merge(l.price(), Math.max(1, l.amount()), Integer::sum);
            }
        }
        List<Step> out = new ArrayList<>();
        by.forEach((p, a) -> out.add(new Step(p, a)));
        return out;
    }

    /**
     * How many items sit at the lowest price when they are only a few before a wall (the next
     * price at least twice the lowest, the cheap ones at most a tenth of all); 0 otherwise.
     */
    public static int fewCheap(List<Step> ladder) {
        if (ladder.size() < 2) {
            return 0;
        }
        int lowest = ladder.get(0).price();
        int cheap = 0;
        int all = 0;
        int next = 0;
        for (Step s : ladder) {
            all += s.amount();
            if (s.price() <= lowest * SAME) {
                cheap += s.amount();
            } else if (next == 0) {
                next = s.price();
            }
        }
        return next >= lowest * WALL && cheap <= all * FEW ? cheap : 0;
    }

    /**
     * The price to go by: the lowest, or when only a few sit there before a wall, the first price
     * past them (one cheap stray listing does not set a stack's price). 0 for an empty ladder.
     */
    public static int price(List<Step> ladder) {
        if (ladder.isEmpty()) {
            return 0;
        }
        int lowest = ladder.get(0).price();
        if (fewCheap(ladder) == 0) {
            return lowest;
        }
        for (Step s : ladder) {
            if (s.price() > lowest * SAME) {
                return s.price();
            }
        }
        return lowest;
    }
}
