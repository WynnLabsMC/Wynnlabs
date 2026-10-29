package io.github.anilken.wynnlabs.mounts;

/**
 * One step of a feed plan.
 * <ul>
 * <li>FEED: {@code material} goes in; {@code durationSec} is its timer. With {@code swap} the feed is started with a
 * level-1 filler of the same kind, then the real material is swapped in once the training below is done.</li>
 * <li>TRAIN: ride to raise {@code stat} to {@code toLevel}; {@code durationSec} is the time spent on top of timers,
 * {@code coveredSec} the part hidden under a running feed timer.</li>
 * </ul>
 */
public record FeedStep(Kind kind, Material material, MountStat stat, int toLevel, long durationSec, long coveredSec,
                       boolean swap) {
    public enum Kind { FEED, TRAIN }

    public static FeedStep feed(Material m, long durationSec, boolean swap) {
        return new FeedStep(Kind.FEED, m, null, 0, durationSec, 0, swap);
    }

    public static FeedStep train(MountStat stat, int toLevel, long durationSec, long coveredSec) {
        return new FeedStep(Kind.TRAIN, null, stat, toLevel, durationSec, coveredSec, false);
    }

    public boolean isFeed() {
        return kind == Kind.FEED;
    }
}
