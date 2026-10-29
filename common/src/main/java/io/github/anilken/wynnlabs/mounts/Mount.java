package io.github.anilken.wynnlabs.mounts;

/**
 * A mount as read from the game: type, display name, stats and (when known) its queue.
 * {@code estimatedMax} is true when the maxes were estimated rather than read.
 */
public record Mount(MountType type, String name, MountStats stats, MountQueue queue, boolean estimatedMax) {
    public Mount {
        if (queue == null) {
            queue = MountQueue.EMPTY;
        }
    }

    /** A freshly bought mount of this type. */
    public static Mount fresh(MountType type) {
        return new Mount(type, type.display(), MountStats.fresh(), MountQueue.EMPTY, false);
    }

    public int potential() {
        return stats.potential();
    }

    public double averageLimit() {
        return stats.averageLimit();
    }

    public boolean breedReady() {
        return stats.breedReady();
    }
}
