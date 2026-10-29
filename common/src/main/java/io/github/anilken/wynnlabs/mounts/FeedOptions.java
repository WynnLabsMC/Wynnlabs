package io.github.anilken.wynnlabs.mounts;

/**
 * Feed planner options.
 *
 * @param trainSecPerLevel seconds of riding per trained level (unverified in game; default 60)
 * @param trainFocus       the only stat that may be trained, or null for any
 * @param trainCap         no stat is trained above this level
 * @param allowQueueSwaps  allow "start with a level-1 filler, train under its timer, swap in the real material"
 * @param maxTimeSec       cap on the reported (wall-clock) total; Long.MAX_VALUE for none
 * @param objective        what "cheapest" means
 * @param stateBudget      the search stops after this many expanded states (the plan is then not proven)
 * @param timeBudgetMs     the search stops after this long (the plan is then not proven)
 */
public record FeedOptions(int trainSecPerLevel, MountStat trainFocus, int trainCap, boolean allowQueueSwaps,
                          long maxTimeSec, Objective objective, long stateBudget, long timeBudgetMs) {

    public enum Objective {
        /** Feed timers plus training counted in full (WynnToolZ-compatible); ties: fewer feeds, lower levels, less real time. */
        TRAINING_IN_FULL,
        /** Real wall-clock time first (hidden training is free), then training in full. */
        WALL_CLOCK
    }

    public static final long DEFAULT_STATE_BUDGET = 6_000_000L;
    public static final long DEFAULT_TIME_BUDGET_MS = 20_000L;

    public static FeedOptions defaults() {
        return new FeedOptions(60, null, Integer.MAX_VALUE, false, Long.MAX_VALUE, Objective.TRAINING_IN_FULL,
                DEFAULT_STATE_BUDGET, DEFAULT_TIME_BUDGET_MS);
    }

    public FeedOptions withTrainSecPerLevel(int v) {
        return new FeedOptions(v, trainFocus, trainCap, allowQueueSwaps, maxTimeSec, objective, stateBudget, timeBudgetMs);
    }

    public FeedOptions withTrainFocus(MountStat v) {
        return new FeedOptions(trainSecPerLevel, v, trainCap, allowQueueSwaps, maxTimeSec, objective, stateBudget, timeBudgetMs);
    }

    public FeedOptions withTrainCap(int v) {
        return new FeedOptions(trainSecPerLevel, trainFocus, v, allowQueueSwaps, maxTimeSec, objective, stateBudget, timeBudgetMs);
    }

    public FeedOptions withQueueSwaps(boolean v) {
        return new FeedOptions(trainSecPerLevel, trainFocus, trainCap, v, maxTimeSec, objective, stateBudget, timeBudgetMs);
    }

    public FeedOptions withMaxTimeSec(long v) {
        return new FeedOptions(trainSecPerLevel, trainFocus, trainCap, allowQueueSwaps, v, objective, stateBudget, timeBudgetMs);
    }

    public FeedOptions withObjective(Objective v) {
        return new FeedOptions(trainSecPerLevel, trainFocus, trainCap, allowQueueSwaps, maxTimeSec, v, stateBudget, timeBudgetMs);
    }

    public FeedOptions withBudget(long states, long millis) {
        return new FeedOptions(trainSecPerLevel, trainFocus, trainCap, allowQueueSwaps, maxTimeSec, objective, states, millis);
    }
}
