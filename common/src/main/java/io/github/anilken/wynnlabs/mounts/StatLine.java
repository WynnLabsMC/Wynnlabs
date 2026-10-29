package io.github.anilken.wynnlabs.mounts;

/**
 * One stat of a mount: current {@code level} (raised by riding/training), {@code limit} (raised by feeding, the
 * level can't pass it) and {@code max} (the limit can't pass it; fixed for a mount, set at birth).
 */
public record StatLine(int level, int limit, int max) {
    public StatLine {
        if (level < 0 || limit < 0 || max < 0) {
            throw new IllegalArgumentException("negative stat " + level + "/" + limit + "/" + max);
        }
    }
}
