package io.github.anilken.wynnlabs.mounts;

import java.util.List;

/**
 * A mount's feeding queue: materials waiting (in order) and seconds left on the running timer. The planner plans
 * from the stats as they are; apply {@link #applyTo} first when the queued points should count.
 */
public record MountQueue(List<Material> pending, long secondsLeft) {
    public static final MountQueue EMPTY = new MountQueue(List.of(), 0);

    public MountQueue {
        pending = List.copyOf(pending);
    }

    /** Stats after every queued material is digested. */
    public MountStats applyTo(MountStats s) {
        MountStats out = s;
        for (Material m : pending) {
            out = out.fed(m);
        }
        return out;
    }
}
