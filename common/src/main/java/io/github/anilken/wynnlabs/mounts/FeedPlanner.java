package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Exact feed planner.
 *
 * <p>Rules (each checked by replaying 108 reference plans step for step):
 * <ul>
 * <li>Gate: a material of level L can be fed only when the highest current stat level is at least L.</li>
 * <li>Points: see {@link MaterialKind}; a limit never passes its max.</li>
 * <li>Timer: from the limit sum before the feed ({@link MountRules#timerSec}); the last feed's timer counts.</li>
 * <li>Training is just in time: right before the first feed above the gate one stat is trained to exactly that
 * level; its current limit must already reach it. It is hidden under the previous feed's timer (whole levels) when
 * that stat's limit before the previous feed already reached it; a queue swap (option) hides it under the feed's
 * own timer instead when that hides more.</li>
 * <li>Objective (default): feed timers plus training counted in full; ties: fewer feeds, lower material levels,
 * less real time.</li>
 * </ul>
 * A plan is {@code proven} only when the search finished inside its budget.
 */
public final class FeedPlanner {
    static final int BEAM_WIDTH = 100;
    /** Width of the quick estimate (a small beam: a real, valid plan, not proven). */
    static final int ESTIMATE_WIDTH = 12;
    private static final Map<String, FeedPlan> ESTIMATES = new LinkedHashMap<>(16, 0.75f, true);

    private FeedPlanner() {
    }

    /** Exact plan on the calling thread at full speed (tests, tools). In game use {@link #start}. */
    public static FeedPlan plan(MountStats start, FeedGoal goal, FeedOptions options) {
        return plan(start, goal, options, () -> false);
    }

    /** Exact plan on the calling thread; {@code cancelled} is polled and stops the search (result then not proven). */
    public static FeedPlan plan(MountStats start, FeedGoal goal, FeedOptions options, BooleanSupplier cancelled) {
        Stats st = new Stats();
        return plan(start, goal, options, cancelled, st);
    }

    /** What to plan: which mount (any stable id, used for the cache), its stats now, the goal and options. */
    public record Request(String mountId, MountStats stats, FeedGoal goal, FeedOptions options) {
    }

    /**
     * Starts the exact search for one mount, only when the player asks. It runs on the low-priority planner thread
     * in short slices (see {@link #pacing}); {@link FeedJob#current()} has a quick estimate within a moment and the
     * exact plan when done. The result (or, when cancelled, the best so far) is cached for {@link #cached}.
     */
    public static FeedJob start(Request req) {
        FeedJob job = new FeedJob();
        FeedWorker.submit(FeedWorker.NOW, () -> {
            try {
                if (job.cancelled()) {
                    job.finish(null);
                    return;
                }
                job.offer(estimate(req.stats(), req.goal(), req.options()));
                Stats st = job.stats();
                FeedOptions o = req.options();
                FeedPlan exact = plan(req.stats(), req.goal(),
                        o.withBudget(o.stateBudget(), Math.max(o.timeBudgetMs(), 120_000)), job::cancelled, st);
                FeedPlan best = exact.proven() || job.current() == null || better(exact, job.current())
                        ? exact : job.current();
                FeedCache.store(FeedCache.key(req.mountId(), req.goal(), o), req.stats(), acts(best), best.proven(),
                        best.goalReached());
                job.finish(best);
            } catch (Throwable t) {
                job.fail(t);
            }
        });
        return job;
    }

    /** The last cached plan for this mount, goal and options (with its stats and save time), or null. */
    public static FeedCache.Cached cached(String mountId, MountStats now, FeedGoal goal, FeedOptions options) {
        return FeedCache.get(FeedCache.key(mountId, goal, options), now, goal, options);
    }

    /** Same as {@code start(new Request("", ...)).result()}. */
    public static CompletableFuture<FeedPlan> planAsync(MountStats start, FeedGoal goal, FeedOptions options) {
        return start(new Request("", start, goal, options)).result();
    }

    /** Planner thread pacing: work at most {@code sliceMs}, then sleep {@code pauseMs} (defaults 8 and 12). */
    public static void pacing(int sliceMs, int pauseMs) {
        FeedWorker.pacing(sliceMs, pauseMs);
    }

    /**
     * A cheap valid plan (a small beam search), {@code proven=false}, source "estimate": for the target picker,
     * future generations and the first moment of a job. Remembered per input.
     */
    public static FeedPlan estimate(MountStats start, FeedGoal goal, FeedOptions options) {
        String key = start.stats() + "|" + FeedCache.key("", goal, options);
        synchronized (ESTIMATES) {
            FeedPlan hit = ESTIMATES.get(key);
            if (hit != null) {
                return hit;
            }
        }
        FeedProblem p = new FeedProblem(start, goal, options);
        FeedPlan out;
        if (p.goalOk(FeedProblem.pack(p.lim0))) {
            out = build(p, new int[0], true, true, 0, "none");
        } else {
            FeedSearch search = new FeedSearch(p, 12);
            int[] beam = new FeedBeam(p, search).run(ESTIMATE_WIDTH, new long[4]);
            out = build(p, beam == null ? new int[0] : beam, false, false, 0, "estimate");
        }
        synchronized (ESTIMATES) {
            ESTIMATES.put(key, out);
            if (ESTIMATES.size() > 256) {
                ESTIMATES.remove(ESTIMATES.keySet().iterator().next());
            }
        }
        return out;
    }

    private static boolean better(FeedPlan a, FeedPlan b) {
        if (a.goalReached() != b.goalReached()) {
            return a.goalReached();
        }
        long x = a.feedTimeSec() + a.fullTrainingSec(), y = b.feedTimeSec() + b.fullTrainingSec();
        return x != y ? x < y : a.feeds() < b.feeds();
    }

    private static int[] acts(FeedPlan plan) {
        List<Integer> out = new ArrayList<>();
        MountStat trained = null;
        for (FeedStep s : plan.steps()) {
            if (s.isFeed()) {
                int li = levelIndex(s.material().level());
                out.add(s.material().kind().ordinal() | li << 3
                        | (trained == null ? FeedProblem.NONE : trained.ordinal()) << 7);
                trained = null;
            } else if (trained == null) {
                trained = s.stat();
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Search statistics of one run (tests, the debug API, job progress). */
    public static final class Stats {
        long explored;
        int stored;
        long bytes;
        long upper;
        long beamA = -1;
        long beamMs;
        int passes;
        boolean complete;
        volatile long liveExplored;
        volatile int liveStored;

        /** States expanded so far (live while a job runs). */
        public long explored() {
            return Math.max(explored, liveExplored);
        }

        /** States held now (live while a job runs). */
        public int stored() {
            return Math.max(stored, liveStored);
        }
    }

    static FeedPlan plan(MountStats start, FeedGoal goal, FeedOptions options, BooleanSupplier cancelled, Stats st) {
        FeedProblem p = new FeedProblem(start, goal, options);
        long deadline = System.nanoTime() + Math.max(1, options.timeBudgetMs()) * 1_000_000L;
        if (p.goalOk(FeedProblem.pack(p.lim0))) {
            st.complete = true;
            return build(p, new int[0], true, true, 0, "none");
        }
        FeedSearch search = new FeedSearch(p);
        search.live = st;
        long[] beamCost = new long[4];
        long tb = System.nanoTime();
        int[] beam = new FeedBeam(p, search).run(BEAM_WIDTH, beamCost);
        st.beamMs = (System.nanoTime() - tb) / 1_000_000;
        long upper = Long.MAX_VALUE;
        if (beam != null) {
            Sim s = simulate(p, beam);
            if (s.valid && p.goalOk(FeedProblem.pack(s.lim))) {
                upper = p.order[0] == 0 ? s.a : s.b;
                st.beamA = s.a;
            } else {
                beam = null;
            }
        }
        // Cost-capped passes: a pass with cap >= the optimum returns exactly the uncapped result (states above the
        // cap can't be on an optimal plan); a pass that finds no goal while it dropped states raises the cap.
        long cap = Math.min(upper, search.startBound());
        if (p.maxTime != Long.MAX_VALUE && search.startBoundB() > p.maxTime) {
            cap = Long.MAX_VALUE; // the goal can't fit in the time cap: one uncapped pass for the best progress
        }
        long slack = 900;
        long budget = Math.max(1, options.stateBudget());
        FeedSearch.Result r;
        for (;;) {
            search.liveBase = st.explored;
            r = search.run(cap, budget - st.explored, deadline, cancelled);
            st.explored += r.explored;
            st.stored = Math.max(st.stored, r.stored);
            st.bytes = Math.max(st.bytes, r.bytes);
            st.passes++;
            if (r.goalReached || !r.complete || r.minPruned == Long.MAX_VALUE || cap >= upper) {
                break;
            }
            cap = Math.min(upper, Math.max(r.minPruned, cap + slack));
            slack *= 2;
        }
        st.upper = cap;
        st.complete = r.complete;
        if (r.complete) {
            return build(p, r.acts, r.goalReached, true, r.explored, "search");
        }
        if (beam != null) {
            return build(p, beam, true, false, r.explored, "search");
        }
        return build(p, r.acts, r.goalReached, false, r.explored, "search");
    }

    static CompletableFuture<RoadMap.Plan> submit(java.util.function.Supplier<RoadMap.Plan> job) {
        CompletableFuture<RoadMap.Plan> f = new CompletableFuture<>();
        FeedWorker.submit(FeedWorker.ROAD, () -> {
            try {
                f.complete(job.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /** A stored plan replayed from its stats. */
    static FeedPlan rebuild(MountStats start, FeedGoal goal, FeedOptions options, int[] acts, boolean proven,
                            String source) {
        return build(new FeedProblem(start, goal, options), acts, true, proven, 0, source);
    }

    /** One planned feed: material and, when it is above the gate, the stat trained right before it (else null). */
    public record Feed(Material material, MountStat trained) {
    }

    /** Replays a feed list with the game rules into a plan (not proven); null steps if the list breaks a rule. */
    public static FeedPlan replay(MountStats start, List<Feed> feeds, FeedOptions options) {
        FeedProblem p = new FeedProblem(start, FeedGoal.full(), options);
        int[] acts = new int[feeds.size()];
        for (int i = 0; i < acts.length; i++) {
            Feed f = feeds.get(i);
            int li = levelIndex(f.material().level());
            if (li < 0) {
                return null;
            }
            acts[i] = f.material().kind().ordinal() | li << 3
                    | (f.trained() == null ? FeedProblem.NONE : f.trained().ordinal()) << 7;
        }
        Sim s = simulate(p, acts);
        if (!s.valid) {
            return null;
        }
        return build(p, acts, false, false, 0, "replay");
    }

    private static int levelIndex(int level) {
        for (int i = 0; i < MountRules.LEVELS.length; i++) {
            if (MountRules.LEVELS[i] == level) {
                return i;
            }
        }
        return -1;
    }

    static final class Sim {
        boolean valid = true;
        long a;
        long b;
        int[] lim = new int[8];
        int[] lvl = new int[8];
        List<FeedStep> steps = new ArrayList<>();
    }

    /** The reference replay: durations, training placement, hiding and swaps. */
    static Sim simulate(FeedProblem p, int[] acts) {
        Sim r = new Sim();
        int[] lim = p.lim0.clone(), lvl = p.lvl0.clone();
        int[] prev = null;
        int prevD = 0;
        for (int act : acts) {
            MaterialKind kind = MaterialKind.values()[act & 7];
            int L = MountRules.LEVELS[(act >> 3) & 15];
            int s = (act >> 7) & 15;
            Material mat = MountMaterials.of(kind, L);
            int sum = 0, gate = 0;
            for (int i = 0; i < 8; i++) {
                sum += lim[i];
                gate = Math.max(gate, lvl[i]);
            }
            int d = MountRules.timerSec(sum);
            if (L > gate) {
                if (s == FeedProblem.NONE || L > p.top(s) || L > lim[s] || lvl[s] >= L) {
                    r.valid = false;
                    break;
                }
                int levels = L - lvl[s], c = levels * p.spl;
                int hN = prev != null && L <= prev[s] ? Math.min(levels, p.covLv(prevD)) * p.spl : 0;
                int hS = p.swaps ? Math.min(levels, p.covLv(d)) * p.spl : 0;
                MountStat stat = MountStat.of(s);
                if (hS > hN) {
                    int pre = (c - hS) / (p.spl == 0 ? 1 : p.spl);
                    if (pre > 0) {
                        r.steps.add(FeedStep.train(stat, lvl[s] + pre, c - hS, 0));
                    }
                    r.steps.add(FeedStep.feed(mat, d, true));
                    r.steps.add(FeedStep.train(stat, L, 0, hS));
                    r.b += c - hS;
                } else {
                    r.steps.add(FeedStep.train(stat, L, c - hN, hN));
                    r.steps.add(FeedStep.feed(mat, d, false));
                    r.b += c - hN;
                }
                r.a += c;
                lvl[s] = L;
            } else {
                r.steps.add(FeedStep.feed(mat, d, false));
            }
            r.a += d;
            r.b += d;
            prev = lim.clone();
            prevD = d;
            int[] pts = kind.points(L);
            for (int j = 0; j < pts.length; j++) {
                int i = kind.stats().get(j).ordinal();
                lim[i] = Math.min(p.mx[i], lim[i] + pts[j]);
            }
        }
        r.lim = lim;
        r.lvl = lvl;
        return r;
    }

    private static FeedPlan build(FeedProblem p, int[] acts, boolean goalHint, boolean proven, long explored,
                                  String source) {
        Sim s = simulate(p, acts);
        long feed = 0, train = 0, covered = 0;
        for (FeedStep st : s.steps) {
            if (st.isFeed()) {
                feed += st.durationSec();
            } else {
                train += st.durationSec();
                covered += st.coveredSec();
            }
        }
        boolean reached = s.valid && p.goalOk(FeedProblem.pack(s.lim));
        MountStats end = MountStats.of(s.lvl, s.lim, p.mx);
        return new FeedPlan(reached, proven && s.valid, s.steps, feed + train, feed, train, covered, end, explored,
                source);
    }
}
