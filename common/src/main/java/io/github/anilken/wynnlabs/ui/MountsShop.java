package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.RoadMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The road map's shopping list: the foods every mount of the generation you work on still needs
 * (the owned ones not yet at their goal and the fresh ones still to buy), summed. Each owned mount
 * counts its own plan (the exact one when calculated, else the estimate), made from its stats with
 * its trough's foods already eaten, so what is queued is never bought twice. What the bank holds
 * is taken off when the player lets it.
 */
final class MountsShop {

    /** One mount still being fed: its name (null for one still to buy) and its plan from after its trough. */
    record Feeding(String name, FeedPlan plan) {}

    /**
     * The list: what to buy, what the bag and the bank cover, whom each food is for (names; a count of fresh
     * ones to buy as the last entry, "+N"), and how many fresh mounts the generation still lacks.
     */
    record Shop(Map<Material, Integer> need, Map<Material, Integer> banked, Map<Material, List<String>> forWhom, int toBuy) {}

    private MountsShop() {}

    /** Every planned food of these mounts, summed. */
    static Map<Material, Integer> foods(List<Feeding> mounts) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (Feeding f : mounts) {
            if (f.plan() != null) {
                f.plan().materials().forEach((m, n) -> out.merge(m, n, Integer::sum));
            }
        }
        return out;
    }

    /** Where a food comes from: the bag first, then the bank, then the market. */
    enum Source { BAG, BANK, BUY }

    /**
     * The stock allotted to the rows in row order ({@code wants}: each row's next food, null for
     * none): the first rows that want a food take it from the bag while the bag holds it, the next
     * from the bank while that lasts, the rest buy it. Null for a row that wants nothing.
     */
    static List<Source> allot(List<Material> wants, ToIntFunction<Material> bag, ToIntFunction<Material> bank) {
        Map<Material, int[]> left = new LinkedHashMap<>();
        List<Source> out = new ArrayList<>();
        for (Material m : wants) {
            if (m == null) {
                out.add(null);
                continue;
            }
            int[] s = left.computeIfAbsent(m, k -> new int[] {bag == null ? 0 : bag.applyAsInt(k), bank == null ? 0 : bank.applyAsInt(k)});
            if (s[0] > 0) {
                s[0]--;
                out.add(Source.BAG);
            } else if (s[1] > 0) {
                s[1]--;
                out.add(Source.BANK);
            } else {
                out.add(Source.BUY);
            }
        }
        return out;
    }

    /**
     * The summed foods split into what to buy and what you already have, taken as the feeder's
     * marks take it: the bag first, then the bank ({@code bag} / {@code bank}: how many each holds;
     * null: not used).
     */
    static Shop shop(List<Feeding> mounts, ToIntFunction<Material> bag, ToIntFunction<Material> bank, int toBuy) {
        Map<Material, Integer> need = new LinkedHashMap<>();
        Map<Material, Integer> banked = new LinkedHashMap<>();
        Map<Material, List<String>> forWhom = new LinkedHashMap<>();
        for (var e : foods(mounts).entrySet()) {
            int fromBag = bag == null ? 0 : Math.min(e.getValue(), bag.applyAsInt(e.getKey()));
            int fromBank = bank == null ? 0 : Math.min(e.getValue() - fromBag, bank.applyAsInt(e.getKey()));
            int have = fromBag + fromBank;
            if (have > 0) {
                banked.put(e.getKey(), have);
            }
            if (e.getValue() - have > 0) {
                need.put(e.getKey(), e.getValue() - have);
            }
            List<String> names = new ArrayList<>();
            int fresh = 0;
            for (Feeding f : mounts) {
                if (f.plan() != null && f.plan().materials().containsKey(e.getKey())) {
                    if (f.name() == null) {
                        fresh++;
                    } else if (!names.contains(f.name())) {
                        names.add(f.name());
                    }
                }
            }
            if (fresh > 0) {
                names.add("+" + fresh);
            }
            forWhom.put(e.getKey(), List.copyOf(names));
        }
        return new Shop(need, banked, forWhom, toBuy);
    }

    /**
     * The mounts of the generation you work on that still eat: the owned ones short of their goal
     * with their own plans, and the fresh ones to buy with the generation's estimate. Without a
     * road map yet, only the mount the page works on ({@code fallback}, its plan).
     */
    static List<Feeding> generation(MountsModel model, RoadMap.Plan plan, MountsModel.Owned cur, FeedPlan fallback, int toBuy) {
        List<RoadMap.Generation> gens = plan == null ? List.of() : plan.generations();
        if (gens.isEmpty()) {
            return fallback == null ? List.of() : List.of(new Feeding(cur == null ? null : cur.mount().name(), fallback));
        }
        List<MountsModel.Owned> owned = model.owned(model.type());
        int i = MountsRoad.currentGeneration(gens, owned);
        List<Feeding> out = new ArrayList<>();
        for (MountsModel.Owned o : MountsRoad.inGeneration(gens, i, owned)) {
            var m = o.mount();
            if (model.goalFor(o).reachedBy(m.queue().applyTo(m.stats()))) {
                continue;                               // done, or done once its trough is eaten
            }
            FeedPlan p = model.feed(o).plan();
            if (p != null) {
                out.add(new Feeding(m.name(), p));
            }
        }
        RoadMap.Generation gen = gens.get(i);
        if (i == 0 && gen.bought() > 0 && gen.feedPlan() != null) {
            for (int k = 0; k < toBuy; k++) {
                out.add(new Feeding(null, gen.feedPlan()));
            }
        }
        return out;
    }
}
