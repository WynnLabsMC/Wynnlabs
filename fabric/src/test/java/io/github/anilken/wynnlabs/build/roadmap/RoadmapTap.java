package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.model.Slot;

/**
 * Tests outside this package: the road map's trace ({@link Roadmap#trace}) switched on with a
 * listener, or off with null. The leveling benchmark reads why a community pick was skipped.
 */
public final class RoadmapTap {

    private RoadmapTap() {}

    /** One trace line: the milestone, the slot type, the line. */
    public interface Line {
        void accept(int m, Slot slot, String line);
    }

    public static void trace(Line listener) {
        Roadmap.trace = listener == null ? null : listener::accept;
    }
}
