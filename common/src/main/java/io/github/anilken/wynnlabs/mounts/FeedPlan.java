package io.github.anilken.wynnlabs.mounts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A feed plan.
 *
 * @param goalReached   the plan reaches the goal (otherwise it is the best progress found)
 * @param proven        true only when the search finished: no cheaper plan exists under the rules. A plan cut by the
 *                      state/time budget is returned with {@code proven=false} and must not be shown as optimal.
 * @param steps         FEED/TRAIN steps in order
 * @param totalTimeSec  real time: feed timers + training not hidden under a timer
 * @param feedTimeSec   sum of feed timers
 * @param trainingTimeSec training on top of timers (not hidden)
 * @param coveredTrainingSec training hidden under timers
 * @param end           stats after the plan (levels include the training)
 * @param explored      states expanded by the search (0 for a shipped or trivial plan)
 * @param source        "search", "shipped" (precomputed and checked) or "none" (already at the goal)
 */
public record FeedPlan(boolean goalReached, boolean proven, List<FeedStep> steps, long totalTimeSec, long feedTimeSec,
                       long trainingTimeSec, long coveredTrainingSec, MountStats end, long explored, String source) {
    public FeedPlan {
        steps = List.copyOf(steps);
    }

    public int feeds() {
        int n = 0;
        for (FeedStep s : steps) {
            if (s.isFeed()) {
                n++;
            }
        }
        return n;
    }

    /** Training counted in full (hidden or not). */
    public long fullTrainingSec() {
        return trainingTimeSec + coveredTrainingSec;
    }

    /** Material name -> count, in plan order. */
    public Map<Material, Integer> materials() {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (FeedStep s : steps) {
            if (s.isFeed()) {
                out.merge(s.material(), 1, Integer::sum);
            }
        }
        return out;
    }
}
