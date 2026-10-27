package io.github.anilken.wynnlabs.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The box window's three answers, from what the window already holds (the listings cache and
 * the box verdict's value): is identifying worth it, where your roll sits among the listings,
 * and a price that would put yours near the top of them. Pure: no game, no requests. Information
 * only; nothing is ever listed.
 */
public final class BoxInsight {

    private BoxInsight() {}

    /** Fewer current listings than this: no percentile and no suggested price. */
    public static final int MIN_LISTINGS = 5;
    /** Listings this many roll points under yours still count as alike for the price. */
    public static final double BAND = 5;
    /** The place the suggested price aims for among the alike listings. */
    public static final int TARGET_RANK = 3;

    /** WORTH: the expected price beats the fee; SELL: it does not; FEW: too few prices; NO_COST: the fee is unknown. */
    public enum Answer { WORTH, SELL, FEW, NO_COST }

    /**
     * Identifying a box: the answer, the expected price (the unweighted mean over the outcomes with
     * a price, as the box verdict has it), how many outcomes have one, and the fee's range.
     */
    public record Identify(Answer answer, Double expected, int priced, int outcomes, int costLow, int costHigh) {}

    /**
     * {@code expected} after the market's {@code taxPercent} against the mean fee: worth it when
     * it is more. Fewer than half the outcomes priced (or none) is FEW; no outcomes or no fee is
     * NO_COST, never a guess.
     */
    public static Identify identify(Double expected, int priced, int outcomes, int costLow, int costHigh, double taxPercent) {
        if (outcomes <= 0 || costHigh <= 0) {
            return new Identify(Answer.NO_COST, expected, priced, outcomes, costLow, costHigh);
        }
        if (expected == null || priced * 2 < outcomes) {
            return new Identify(Answer.FEW, expected, priced, outcomes, costLow, costHigh);
        }
        double fee = (costLow + costHigh) / 2.0;
        Answer a = expected * (1 - taxPercent / 100.0) > fee ? Answer.WORTH : Answer.SELL;
        return new Identify(a, expected, priced, outcomes, costLow, costHigh);
    }

    /** The current (not possibly sold) identified listings with an overall roll. */
    static List<BoxMarket.Listing> rolled(List<BoxMarket.Listing> listings) {
        List<BoxMarket.Listing> out = new ArrayList<>();
        for (BoxMarket.Listing l : listings) {
            if (!l.possiblySold() && !l.unidentified() && l.overallRoll() != null) {
                out.add(l);
            }
        }
        return out;
    }

    /** How many current identified listings with a roll there are: what the two answers below count on. */
    public static int current(List<BoxMarket.Listing> listings) {
        return rolled(listings).size();
    }

    /**
     * Where {@code own} % sits: "in the top N %" counting yours among the listings (1 + those
     * rolled higher, out of all of them and yours). Null under {@link #MIN_LISTINGS} listings.
     */
    public static Integer topPercent(List<BoxMarket.Listing> listings, double own) {
        List<BoxMarket.Listing> r = rolled(listings);
        if (r.size() < MIN_LISTINGS) {
            return null;
        }
        int better = 0;
        for (BoxMarket.Listing l : r) {
            if (l.overallRoll() > own) {
                better++;
            }
        }
        return (int) Math.ceil(100.0 * (better + 1) / (r.size() + 1));
    }

    /** A price in emeralds and the place it would take among the alike listings (1 = cheapest). */
    public record Suggestion(int price, int rank) {}

    /**
     * A price that puts yours {@link #TARGET_RANK}th (or better) by price among the listings that
     * rolled like yours (within {@link #BAND} points under it, or above): one emerald under the
     * one now at that place. With none alike (yours beats them all by more), the best-rolled ones
     * stand in. Null under {@link #MIN_LISTINGS} listings.
     */
    public static Suggestion suggest(List<BoxMarket.Listing> listings, double own) {
        List<BoxMarket.Listing> r = rolled(listings);
        if (r.size() < MIN_LISTINGS) {
            return null;
        }
        List<BoxMarket.Listing> alike = new ArrayList<>();
        for (BoxMarket.Listing l : r) {
            if (l.overallRoll() >= own - BAND) {
                alike.add(l);
            }
        }
        if (alike.isEmpty()) {
            r.sort(Comparator.comparing(BoxMarket.Listing::overallRoll).reversed());
            alike = new ArrayList<>(r.subList(0, Math.min(TARGET_RANK, r.size())));
        }
        alike.sort(Comparator.comparingInt(BoxMarket.Listing::price));
        int k = Math.min(TARGET_RANK, alike.size());
        return new Suggestion(Math.max(1, alike.get(k - 1).price() - 1), k);
    }
}
