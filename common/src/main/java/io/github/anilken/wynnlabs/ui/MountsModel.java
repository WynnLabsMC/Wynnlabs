package io.github.anilken.wynnlabs.ui;

import com.google.gson.Gson;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.FeedGoal;
import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.FeedCache;
import io.github.anilken.wynnlabs.mounts.FeedJob;
import io.github.anilken.wynnlabs.mounts.FeedOptions;
import io.github.anilken.wynnlabs.mounts.FeedPlanner;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.MountBook;
import io.github.anilken.wynnlabs.mounts.MountQueue;
import io.github.anilken.wynnlabs.mounts.MountStat;
import io.github.anilken.wynnlabs.mounts.MountStats;
import io.github.anilken.wynnlabs.mounts.MountType;
import io.github.anilken.wynnlabs.mounts.RoadMap;
import io.github.anilken.wynnlabs.quests.QuestBook;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * What the Mounts tab shows, apart from how: the mounts read so far (as the planner's
 * {@link io.github.anilken.wynnlabs.mounts.Mount}), the options, and the road map and feed plans,
 * each asked of the planner once per input in the background and kept until the input changes.
 * Plans that are still running, or that failed, say so ({@link Pending}); the screen never waits.
 */
final class MountsModel {

    /** A mount read in game, in the planner's terms, with where and when it was seen. */
    record Owned(io.github.anilken.wynnlabs.mounts.Mount mount, MountRead read, MountBook.Where where, long at, int row) {

        Owned(io.github.anilken.wynnlabs.mounts.Mount mount, MountRead read, MountBook.Where where, long at) {
            this(mount, read, where, at, 0);
        }
    }

    /** A plan asked of the planner: its result, or that it is running, or that it failed. */
    record Pending<T>(T value, boolean running, String error) {}

    /** The options menu's settings (saved in config/wynnlabs/mounts-options.json, shared by all characters). */
    static final class Options {
        int trainMin = 1;
        int trainCap;                     // 0: none
        boolean queueSwap;
        boolean silverbull;
        boolean useBank = true;
        boolean buyTm = true;
        String type = "WYVERN";
        /** The road map's target potential, one of {@link RoadMap#TARGETS}. */
        int target = 700;

        int queueSlots() {
            return silverbull ? 7 : 5;
        }

        RoadMap.Options road(MountType t) {
            return new RoadMap.Options(t, trainMin * 60, trainCap <= 0 ? Integer.MAX_VALUE : trainCap, queueSwap);
        }
    }

    /** Training minutes a level and caps the options cycle through. */
    static final int[] TRAIN_MINS = {1, 2, 3, 5};
    static final int[] TRAIN_CAPS = {0, 40, 60, 80, 100};

    private static final Gson GSON = new Gson();
    private static Options options;

    private final Map<String, CompletableFuture<?>> asked = new HashMap<>();
    private final Map<String, Pending<?>> done = new HashMap<>();
    private List<Owned> owned = List.of();
    private int ownedVersion = -1;

    // ---- options ----

    static Options options() {
        if (options == null) {
            options = new Options();
            try {
                Path f = optionsFile();
                if (Files.exists(f)) {
                    Options o = GSON.fromJson(Files.readString(f), Options.class);
                    if (o != null) {
                        options = o;
                    }
                }
            } catch (Throwable t) {
                System.out.println("[WynnLabs] could not read mounts-options.json: " + t);
            }
        }
        return options;
    }

    static void saveOptions() {
        String text = GSON.toJson(options());
        Saver.mark(optionsFile(), () -> text);
    }

    private static Path optionsFile() {
        return Dirs.config().resolve("mounts-options.json");
    }

    MountType type() {
        MountType t = MountType.parse(options().type);
        return t == null ? MountType.WYVERN : t;
    }

    int target() {
        int t = options().target;
        for (int v : RoadMap.TARGETS) {
            if (v == t) {
                return t;
            }
        }
        return 700;
    }

    // ---- the mounts ----

    /** Every mount read on this character, best potential first. */
    List<Owned> owned() {
        if (ownedVersion != MountBook.version) {
            List<Owned> out = new ArrayList<>();
            for (MountBook.Seen s : MountBook.all()) {
                io.github.anilken.wynnlabs.mounts.Mount m = withQueue(convert(s.mount()), s.fed());
                if (m != null) {
                    out.add(new Owned(m, s.mount(), s.where(), s.at(), s.fed() == null ? 0 : s.fed().row()));
                }
            }
            out.sort((a, b) -> Integer.compare(b.mount().potential(), a.mount().potential()));
            owned = List.copyOf(out);
            ownedVersion = MountBook.version;
        }
        return owned;
    }

    List<Owned> owned(MountType t) {
        return owned().stream().filter(o -> o.mount().type() == t).toList();
    }

    /** A read mount in the planner's terms: its eight stats (a horse's Jump Height is its Altitude), or null. */
    static io.github.anilken.wynnlabs.mounts.Mount convert(MountRead r) {
        MountType type = MountType.parse(r.type());
        if (type == null) {
            return null;
        }
        int[] level = new int[MountStat.COUNT];
        int[] limit = new int[MountStat.COUNT];
        int[] max = new int[MountStat.COUNT];
        boolean[] seen = new boolean[MountStat.COUNT];
        for (MountRead.Stat s : r.stats()) {
            MountStat st = MountStat.parse(s.id());
            if (st != null) {
                level[st.ordinal()] = s.level();
                limit[st.ordinal()] = s.limit();
                max[st.ordinal()] = s.max();
                seen[st.ordinal()] = true;
            }
        }
        for (int i = 0; i < MountStat.COUNT; i++) {
            if (!seen[i]) {
                return null;                        // a stat it does not show: not a mount the planner knows
            }
        }
        return new io.github.anilken.wynnlabs.mounts.Mount(type, r.shown(), MountStats.of(level, limit, max),
                MountQueue.EMPTY, r.estimated());
    }

    /** The mount with the queue its feeder row showed last (the items still to eat, the first one's time left). */
    static io.github.anilken.wynnlabs.mounts.Mount withQueue(io.github.anilken.wynnlabs.mounts.Mount m, MountBook.Fed fed) {
        if (m == null || fed == null || fed.pending().isEmpty()) {
            return m;
        }
        List<Material> q = new ArrayList<>();
        for (String name : fed.pending()) {
            Material mat = io.github.anilken.wynnlabs.mounts.MountMaterials.byName(name);
            if (mat != null) {
                q.add(mat);
            }
        }
        long left = fed.remainingNow(System.currentTimeMillis());
        return new io.github.anilken.wynnlabs.mounts.Mount(m.type(), m.name(), m.stats(), new MountQueue(q, Math.max(0, left)),
                m.estimatedMax());
    }

    /** The mount the road map works on now: the best one of the type not yet at its generation's goal. */
    Owned current() {
        Owned best = null;
        for (Owned o : owned(type())) {
            if (!goalFor(o).reachedBy(o.mount().stats())) {
                return o;
            }
            best = best == null ? o : best;
        }
        return best;
    }

    /**
     * What this mount is fed to: its generation's goal on the road map (breed-ready or full limits);
     * breed-ready while the road map is not in yet.
     */
    FeedGoal goalFor(Owned o) {
        RoadMap.Plan plan = road().value();
        if (plan == null || o == null) {
            return FeedGoal.breedReady();
        }
        List<RoadMap.Generation> gens = plan.generations();
        List<Owned> mine = owned(o.mount().type());
        for (int i = 0; i < gens.size(); i++) {
            if (MountsRoad.inGeneration(gens, i, mine).contains(o)) {
                return gens.get(i).goal();
            }
        }
        return gens.isEmpty() ? FeedGoal.breedReady() : gens.get(gens.size() - 1).goal();
    }

    // ---- the road map: cheap estimates, asked without a button ----

    /** Previews: this road map instead of asking the planner (null: ask it). */
    static RoadMap.Plan previewRoad;

    Pending<RoadMap.Plan> road() {
        return road(target());
    }

    /** The road to {@code target} for the picked type and options (the target menu asks for each). */
    Pending<RoadMap.Plan> road(int target) {
        if (previewRoad != null) {
            return target == previewRoad.targetPotential() ? new Pending<>(previewRoad, false, null)
                    : new Pending<>(null, true, null);
        }
        Options o = options();
        MountType t = type();
        String key = "road|" + target + "|" + t + "|" + o.trainMin + "|" + o.trainCap + "|" + o.queueSwap;
        return ask(key, () -> RoadMap.planAsync(target, o.road(t)));
    }

    // ---- the exact feed plan: only when the player asks ----

    /** Where a mount's feed plan stands. */
    enum FeedState { NONE, RUNNING, CACHED, STALE }

    /**
     * A mount's feed plan as the page shows it: the plan (an estimate, the best so far, or the
     * exact one), its state, when it was calculated, and the states searched while it runs.
     */
    record FeedView(FeedPlan plan, FeedState state, long savedAt, long explored) {}

    /** Previews: this view instead of the planner's (null: the planner's). */
    static FeedView previewView;

    private static boolean cacheAttached;
    /** The exact plan running now (one at a time; it outlives the menu and lands in the cache), and its key. */
    private static FeedJob job;
    private static String jobKey;
    private final Map<String, FeedPlan> estimates = new HashMap<>();

    private static void attachCache() {
        if (!cacheAttached) {
            cacheAttached = true;
            try {
                FeedCache.attach(Dirs.config().resolve("mount-plans.json"));
            } catch (Throwable t) {
                System.out.println("[WynnLabs] mount plans stay in memory: " + t);
            }
        }
    }

    private String keyOf(Owned o, MountStats stats, FeedGoal goal, FeedOptions opts) {
        return o.read().key() + "|" + goal + "|" + opts;
    }

    /** This mount's plan: running, cached (stale or not), or the cheap estimate. Never starts the exact one. */
    FeedView feed(Owned o) {
        if (previewView != null) {
            return previewView;
        }
        attachCache();
        Mount m = o.mount();
        MountStats stats = m.queue().applyTo(m.stats());
        FeedGoal goal = goalFor(o);
        FeedOptions opts = options().road(m.type()).feedOptions();
        String key = keyOf(o, stats, goal, opts);
        if (job != null && key.equals(jobKey) && !job.done()) {
            return new FeedView(job.current(), FeedState.RUNNING, 0, job.stats().explored());
        }
        try {
            FeedCache.Cached c = FeedPlanner.cached(o.read().key(), stats, goal, opts);
            if (c != null) {
                if (!c.stale()) {
                    return new FeedView(c.plan(), FeedState.CACHED, c.savedAtMillis(), 0);
                }
                // changed since: by following the plan (its foods queued or eaten) it is only further
                // along, shown from the step reached; stale only when the mount matches no step of it
                int k = io.github.anilken.wynnlabs.mounts.FeedProgress.of(c.plan(), c.state(), m.stats(), m.queue().pending());
                if (k >= 0) {
                    return new FeedView(advanced(c.plan(), k), FeedState.CACHED, c.savedAtMillis(), 0);
                }
                return new FeedView(c.plan(), FeedState.STALE, c.savedAtMillis(), 0);
            }
            FeedPlan est = estimates.computeIfAbsent(key + "|" + stats, k -> FeedPlanner.estimate(stats, goal, opts));
            return new FeedView(est, FeedState.NONE, 0, 0);
        } catch (Throwable t) {
            return new FeedView(null, FeedState.NONE, 0, 0);
        }
    }

    /** The plan from step {@code k} on (the steps before it are done or queued). */
    static FeedPlan advanced(FeedPlan p, int k) {
        if (k <= 0) {
            return p;
        }
        var rest = p.steps().subList(Math.min(k, p.steps().size()), p.steps().size());
        long left = 0;
        for (var s : rest) {
            left += s.durationSec() - s.coveredSec();
        }
        return new FeedPlan(p.goalReached(), p.proven(), rest, Math.min(p.totalTimeSec(), left), p.feedTimeSec(), p.trainingTimeSec(),
                p.coveredTrainingSec(), p.end(), p.explored(), p.source());
    }

    /** The button: the exact plan for this mount, on the planner's slow thread (a running one stops). */
    void startFeed(Owned o) {
        Mount m = o.mount();
        MountStats stats = m.queue().applyTo(m.stats());
        FeedGoal goal = goalFor(o);
        FeedOptions opts = options().road(m.type()).feedOptions();
        if (job != null && !job.done()) {
            job.cancel();                               // a running one stops ("Calculate all" goes on after it)
        }
        attachCache();
        jobKey = keyOf(o, stats, goal, opts);
        job = FeedPlanner.start(new FeedPlanner.Request(o.read().key(), stats, goal, opts));
    }

    /** Cancel: the job keeps its best plan so far (saved, not proven); "Calculate all" stops too. */
    static void cancelFeed() {
        if (queue != null) {
            queue.clear();
        }
        if (job != null && !job.done()) {
            job.cancel();
        }
    }

    /** Mounts waiting for their exact plan after "Calculate all", calculated one at a time. */
    private static java.util.ArrayDeque<Owned> queue;

    /** "Calculate all": the exact plan of each of these with none yet, one after the other on the slow thread. */
    void startAll(List<Owned> mounts) {
        cancelFeed();
        queue = new java.util.ArrayDeque<>(mounts);
        pump();
    }

    /** Starts the next queued mount once the running job is done (each frame the page is drawn). */
    void pump() {
        while (queue != null && !queue.isEmpty() && (job == null || job.done())) {
            Owned o = queue.poll();
            if (feed(o).state() == FeedState.NONE) {
                startFeed(o);
            }
        }
    }

    /** How many mounts "Calculate all" still has to do (the running one not counted). */
    static int queued() {
        return queue == null ? 0 : queue.size();
    }

    @SuppressWarnings("unchecked")
    private <T> Pending<T> ask(String key, java.util.function.Supplier<CompletableFuture<T>> start) {
        Pending<?> d = done.get(key);
        if (d != null) {
            return (Pending<T>) d;
        }
        CompletableFuture<?> f = asked.get(key);
        if (f == null) {
            try {
                f = start.get();
            } catch (Throwable t) {
                f = CompletableFuture.failedFuture(t);
            }
            asked.put(key, f);
        }
        if (!f.isDone()) {
            return new Pending<>(null, true, null);
        }
        Pending<T> out;
        try {
            out = new Pending<>((T) f.join(), false, null);
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            out = new Pending<>(null, false, c.getClass().getSimpleName());
        }
        done.put(key, out);
        asked.remove(key);
        return out;
    }

    // ---- what a material costs and where it is ----

    /** How many of this material the bank holds (0 when not, or the bank was never read). */
    static int inBank(Material m) {
        try {
            Vault.Group g = Vault.find(m.name());
            return g == null ? 0 : g.total();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** A fresh Wyvern at the Mount Merchant: 1,536 emeralds (24 EB), read from its tooltip in game. */
    static final int WYVERN_PRICE = 1536;

    /** A fresh mount's merchant price in emeralds, or -1 while unknown (Horse, Adasaur). */
    static double mountPrice(MountType t) {
        return t == MountType.WYVERN ? WYVERN_PRICE : -1;
    }

    /** The material's lowest market price in emeralds, or -1 when unknown. */
    static double price(Material m) {
        try {
            Prices.Price p = Prices.of(m.name());
            return p == null || p.lowest() == null ? -1 : p.lowest();
        } catch (Throwable t) {
            return -1;
        }
    }

    // ---- unlocks ----

    /** Whether this character can own the type: one was read, or its quest is done. */
    boolean unlocked(MountType t, String quest, int level) {
        if (!owned(t).isEmpty()) {
            return true;
        }
        QuestBook.Entry q = QuestBook.get(quest);
        return q != null && q.status() != null && q.status().toUpperCase(java.util.Locale.ROOT).contains("COMPLETED")
                && GameState.current().level() >= level;
    }
}
