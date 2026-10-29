package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Breeding road map to a target potential: buy base mounts, feed every parent of a generation (to breed-ready or
 * to full limits), pair them and breed; the children are the next generation's parents. One child needs two
 * parents, so a road of G generations starts from 2^G base mounts. Everything here is an estimate: feed plans come
 * from the quick planner and breeding numbers from {@link BreedConstants}. It costs little and runs on its own;
 * the exact feed plan runs only when the player asks ({@link FeedPlanner#start}).
 */
public final class RoadMap {
    private RoadMap() {
    }

    /** Aliases: LOW = target 700, HIGH = target 1000. */
    public enum Standard { LOW, HIGH }

    /**
     * How roads that reach the target are ranked. MOUNTS (default): fewest base mounts (emeralds), then wall clock,
     * then total time. TIME: least total time, then fewest mounts, then wall clock.
     */
    public enum Objective { MOUNTS, TIME }

    /**
     * @param type             mount type to breed
     * @param trainSecPerLevel riding time per trained level (unverified; default 60, shown in the UI)
     * @param trainCap         no stat is trained above this level
     * @param allowQueueSwaps  feed planner queue swaps
     */
    public record Options(MountType type, int trainSecPerLevel, int trainCap, boolean allowQueueSwaps,
                          Objective objective, int feederRows) {
        /** Rows of the enclosure's Mount Feeder: one mount each, fed side by side. */
        public static final int FEEDER_ROWS = 5;

        public Options {
            if (objective == null) {
                objective = Objective.MOUNTS;
            }
            feederRows = feederRows <= 0 ? FEEDER_ROWS : feederRows;
        }

        public Options(MountType type, int trainSecPerLevel, int trainCap, boolean allowQueueSwaps) {
            this(type, trainSecPerLevel, trainCap, allowQueueSwaps, Objective.MOUNTS, FEEDER_ROWS);
        }

        public Options(MountType type, int trainSecPerLevel, int trainCap, boolean allowQueueSwaps,
                       Objective objective) {
            this(type, trainSecPerLevel, trainCap, allowQueueSwaps, objective, FEEDER_ROWS);
        }

        public static Options defaults(MountType type) {
            return new Options(type, 60, Integer.MAX_VALUE, false, Objective.MOUNTS, FEEDER_ROWS);
        }

        public Options withObjective(Objective o) {
            return new Options(type, trainSecPerLevel, trainCap, allowQueueSwaps, o, feederRows);
        }

        public Options withFeederRows(int rows) {
            return new Options(type, trainSecPerLevel, trainCap, allowQueueSwaps, objective, Math.max(1, rows));
        }

        public FeedOptions feedOptions() {
            return FeedOptions.defaults().withTrainSecPerLevel(trainSecPerLevel).withTrainCap(trainCap)
                    .withQueueSwaps(allowQueueSwaps);
        }
    }

    /**
     * One generation: {@code parents} mounts of this generation are fed with {@code feedPlan} (planned for the
     * expected parent) and paired into {@code breeds} breedings.
     *
     * @param index            1-based
     * @param parents          mounts fed this generation (2^(generations-index+1))
     * @param bought           base mounts bought (generation 1 only, else 0)
     * @param breeds           breedings that end this generation (parents / 2)
     * @param goal             what each parent is fed to
     * @param parentStart      expected stats of each parent before feeding
     * @param feedPlan         feed plan for one parent
     * @param parentPotential  potential of each parent
     * @param childAvgLimit    estimated average limit of each child
     * @param childPotential   estimated potential of each child
     * @param feedTimeSec      feed timers, all parents summed
     * @param trainingTimeSec  unhidden training, all parents summed
     * @param breedTimeSec     breeding time of one breeding (they run side by side)
     * @param wallClockSec     generation time in one Mount Feeder: parents fed {@code feederRows} at a time, then the
     *                         breedings, each holding one row (both parents in it) for its 6 h
     * @param materials        materials for all parents of the generation
     */
    public record Generation(int index, int parents, int bought, int breeds, FeedGoal goal, MountStats parentStart,
                             FeedPlan feedPlan, Range parentPotential, Range childAvgLimit, Range childPotential,
                             long feedTimeSec, long trainingTimeSec, long breedTimeSec, long wallClockSec,
                             Map<Material, Integer> materials) {
    }

    /**
     * @param target         the aimed potential as a range (target..target)
     * @param finalPotential estimated potential of the last child (low..high)
     * @param mountsToBuy    base mounts to buy
     * @param totalFeeds     feeds over all generations and parents
     * @param workTimeSec    total time: feed timers + ALL training (hidden or not) + breedings, added up
     * @param wallClockSec   real time in one Mount Feeder: each generation's parents fed {@code feederRows} at a time
     *                       (hidden training is free), then its breedings, also {@code feederRows} at a time
     * @param materials      shopping list: material -> count, by level then kind
     * @param proven         every feed plan in it is proven optimal (false: road maps use estimates)
     * @param targetPotential the potential asked for
     * @param reached        the middle of {@code finalPotential} reaches the target within
     *                       {@link #MAX_GENERATIONS}; when false this is the best road found, not a fake one
     */
    public record Plan(Standard standard, MountType type, List<Generation> generations, Range target,
                       Range finalPotential, int mountsToBuy, int totalFeeds, long workTimeSec, long wallClockSec,
                       Map<Material, Integer> materials, boolean proven, int targetPotential, boolean reached) {
    }

    /** Targets the UI offers. */
    public static final int[] TARGETS = {500, 700, 850, 1000, 1200};
    static final int MAX_GENERATIONS = 6;

    /** LOW = 700, HIGH = 1000. */
    public static int targetOf(Standard standard) {
        return standard == Standard.LOW ? 700 : 1000;
    }

    /** The aimed potential of a standard, as a range. */
    public static Range target(Standard standard) {
        return Range.of(targetOf(standard));
    }

    public static Plan plan(Standard standard, Options options) {
        return plan(targetOf(standard), options);
    }

    public static CompletableFuture<Plan> planAsync(Standard standard, Options options) {
        return planAsync(targetOf(standard), options);
    }

    public static CompletableFuture<Plan> planAsync(int targetPotential, Options options) {
        return FeedPlanner.submit(() -> plan(targetPotential, options));
    }

    /**
     * The best road whose estimated MIDDLE potential reaches the target, ranked by {@link Options#objective()}
     * (default: fewest base mounts, then wall clock, then total time): every generation count up to
     * {@link #MAX_GENERATIONS} and, per generation, parents fed to breed-ready (average limit 20) or to full limits. Feed plans are quick estimates
     * ({@link FeedPlanner#estimate}); the exact plan is the player's call ({@link FeedPlanner#start}). When no road
     * reaches the target, returns the one with the highest middle potential, {@code reached=false}.
     */
    public static Plan plan(int targetPotential, Options options) {
        return plan(targetPotential, options, Breeding.constants());
    }

    static Plan plan(int targetPotential, Options options, BreedConstants k) {
        Plan best = null, highest = null;
        for (int g = 1; g <= MAX_GENERATIONS; g++) {
            boolean[] full = new boolean[g];
            for (int mask = 0; mask < 1 << g; mask++) {
                for (int i = 0; i < g; i++) {
                    full[i] = (mask >> i & 1) != 0;
                }
                if (best != null && options.objective() == Objective.TIME
                        && lowerBound(g, full, k) >= best.workTimeSec()) {
                    continue;
                }
                Plan p = road(g, full, targetPotential, options, k);
                if (p.reached()) {
                    if (best == null || cheaper(p, best, options.objective())) {
                        best = p;
                    }
                } else if (highest == null || p.finalPotential().mid() > highest.finalPotential().mid()
                        || (p.finalPotential().mid() == highest.finalPotential().mid()
                        && cheaper(p, highest, options.objective()))) {
                    highest = p;
                }
            }
            if (best != null && options.objective() == Objective.MOUNTS) {
                break; // more generations only mean more mounts
            }
        }
        return best != null ? best : highest;
    }

    static boolean cheaper(Plan a, Plan b, Objective o) {
        if (o == Objective.MOUNTS) {
            if (a.mountsToBuy() != b.mountsToBuy()) {
                return a.mountsToBuy() < b.mountsToBuy();
            }
            if (a.wallClockSec() != b.wallClockSec()) {
                return a.wallClockSec() < b.wallClockSec();
            }
            return a.workTimeSec() < b.workTimeSec();
        }
        if (a.workTimeSec() != b.workTimeSec()) {
            return a.workTimeSec() < b.workTimeSec();
        }
        if (a.mountsToBuy() != b.mountsToBuy()) {
            return a.mountsToBuy() < b.mountsToBuy();
        }
        return a.wallClockSec() < b.wallClockSec();
    }

    /** Breedings alone: 2^g - 1 of them, whatever the feeding. */
    private static long lowerBound(int g, boolean[] full, BreedConstants k) {
        return ((1L << g) - 1) * k.breedSec();
    }

    /** One road: g generations, generation i fed to full limits when full[i], else to breed-ready. */
    private static Plan road(int gens, boolean[] full, int targetPotential, Options options, BreedConstants k) {
        FeedOptions fo = options.feedOptions();
        List<Generation> out = new ArrayList<>();
        Map<Material, Integer> all = new TreeMap<>(MATERIAL_ORDER);
        MountStats mid = MountStats.fresh(), lo = mid, hi = mid;
        int feeds = 0;
        long work = 0, wall = 0;
        boolean proven = true;
        Range child = Range.of(mid.potential());
        for (int g = 1; g <= gens; g++) {
            FeedGoal goal = full[g - 1] ? FeedGoal.full() : FeedGoal.breedReady();
            FeedPlan fp = FeedPlanner.estimate(mid, goal, fo);
            MountStats midEnd = fp.end();
            MountStats loEnd = fedLike(lo, mid, midEnd, goal), hiEnd = fedLike(hi, mid, midEnd, goal);
            BreedEstimate em = Breeding.estimate(midEnd, midEnd, k);
            BreedEstimate el = Breeding.estimate(loEnd, loEnd, k), eh = Breeding.estimate(hiEnd, hiEnd, k);
            child = new Range(el.potential().low(), eh.potential().high());
            int parents = 1 << (gens - g + 1);
            int breeds = parents / 2;
            Map<Material, Integer> mats = new TreeMap<>(MATERIAL_ORDER);
            fp.materials().forEach((m, n) -> mats.merge(m, n * parents, Integer::sum));
            mats.forEach((m, n) -> all.merge(m, n, Integer::sum));
            feeds += fp.feeds() * parents;
            work += (fp.feedTimeSec() + fp.fullTrainingSec()) * parents + k.breedSec() * breeds;
            long genWall = wallClock(fp.totalTimeSec(), parents, k.breedSec(), breeds, options.feederRows());
            wall += genWall;
            proven &= fp.proven();
            out.add(new Generation(g, parents, g == 1 ? parents : 0, breeds, goal, mid, fp,
                    new Range(loEnd.potential(), hiEnd.potential()),
                    new Range(el.avgLimit().low(), eh.avgLimit().high()), child, fp.feedTimeSec() * parents,
                    fp.trainingTimeSec() * parents, k.breedSec(), genWall,
                    Collections.unmodifiableMap(new LinkedHashMap<>(mats))));
            // the middle child: the planner's start for the next generation, and what "reached" is judged on
            Range midChild = em.potential();
            if (g == gens) {
                child = new Range(Math.min(child.low(), midChild.low()), Math.max(child.high(), midChild.high()));
            }
            mid = em.expected();
            lo = side(el, true);
            hi = side(eh, false);
        }
        int midPotential = mid.potential();
        Standard std = targetPotential == targetOf(Standard.LOW) ? Standard.LOW
                : targetPotential == targetOf(Standard.HIGH) ? Standard.HIGH : null;
        return new Plan(std, options.type(), List.copyOf(out), Range.of(targetPotential), child, 1 << gens, feeds,
                work, wall, Collections.unmodifiableMap(new LinkedHashMap<>(all)), proven, targetPotential,
                midPotential >= targetPotential);
    }

    /**
     * One generation in a feeder with {@code rows} rows: the parents (all the same plan) are fed longest-first, a
     * row at a time, so the feeding takes ceil(parents / rows) plans; then the breedings: both parents share one row
     * for the whole breeding (verified in game), ceil(breeds / rows) rounds. rows = 1 is everything one after
     * another.
     */
    static long wallClock(long planSec, int parents, long breedSec, int breeds, int rows) {
        int r = Math.max(1, rows);
        return (long) ((parents + r - 1) / r) * planSec + (long) ((breeds + r - 1) / r) * breedSec;
    }

    /** By level, then kind order. */
    static final Comparator<Material> MATERIAL_ORDER =
            Comparator.comparingInt(Material::level).thenComparing(Material::kind);

    /** The low or high end of an estimate, as stats (levels at the matching end, never above the limit). */
    private static MountStats side(BreedEstimate e, boolean low) {
        int[] lv = new int[8], li = new int[8], mx = new int[8];
        for (int i = 0; i < 8; i++) {
            BreedEstimate.StatRange s = e.stats().get(i);
            mx[i] = low ? s.max().low() : s.max().high();
            li[i] = Math.min(mx[i], low ? s.limit().low() : s.limit().high());
            lv[i] = Math.min(li[i], low ? s.level().low() : s.level().high());
        }
        return MountStats.of(lv, li, mx);
    }

    /**
     * A scenario parent after feeding: full limits = every limit at its max; breed-ready = the same gains as the
     * planned (middle) parent, capped at max.
     */
    private static MountStats fedLike(MountStats start, MountStats midStart, MountStats midEnd, FeedGoal goal) {
        int[] lv = new int[8], li = new int[8], mx = new int[8];
        for (int i = 0; i < 8; i++) {
            mx[i] = start.max(i);
            li[i] = goal.fullLimits() ? mx[i] : Math.min(mx[i], start.limit(i) + midEnd.limit(i) - midStart.limit(i));
            lv[i] = Math.min(li[i], Math.max(start.level(i), midEnd.level(i)));
        }
        return MountStats.of(lv, li, mx);
    }
}
