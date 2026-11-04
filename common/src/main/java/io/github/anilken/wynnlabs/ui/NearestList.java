package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.Workers;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.Discoveries;
import io.github.anilken.wynnlabs.quests.MiniQuests;
import io.github.anilken.wynnlabs.quests.Nearest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The nearest list's latest ranking ({@link Nearest}) and its worker. The candidates are gathered
 * on the game thread (cheap list reads, kept a second); the ranking runs on one lowest-priority
 * thread, {@code wynnlabs-nearest}, which first fetches the world route map when it is missing
 * (ranking by straight line meanwhile). Asks are coalesced: at most one job waits, and it takes
 * the newest ask. Nothing ever waits for a job: the quests tab and /nearest show the latest result.
 */
public final class NearestList {

    private static final ExecutorService WORKER = Workers.singleLow("nearest");
    private static final AtomicBoolean QUEUED = new AtomicBoolean();
    private static volatile Nearest.Request pending;
    private static volatile Nearest.Result result;
    private static volatile boolean running;
    /** The preview's result: shown as it is, no job started. */
    private static volatile boolean frozen;

    private NearestList() {}

    /**
     * The latest result, a new one asked for when it no longer answers the player here (moved
     * more than {@link Nearest#MOVE} blocks, content done, level, the over-level choice or the map
     * changed); null before the first one. Game thread; never waits.
     */
    public static Nearest.Result now(boolean over) {
        if (frozen) {
            return result;
        }
        GameState gs = GameState.current();
        double[] at = gs.position();
        Nearest.Request q = new Nearest.Request(at[0], at[1], gs.level(), over, candidates(gs), RouteReach.hash(), gs.now());
        Nearest.Result r = result;
        if (!Nearest.fresh(r, q)) {
            ask(q);
        }
        return r;
    }

    /** Whether a newer result is being worked out. */
    public static boolean computing() {
        return running || QUEUED.get();
    }

    /** /nearest: the latest result as JSON, at most {@code limit} entries; asks for a new one without waiting. Game thread. */
    public static JsonObject json(int limit, boolean over) {
        Nearest.Result r = now(over);
        boolean other = r != null && r.over() != over;
        return Nearest.json(other ? null : r, limit, computing() || r == null || other, RouteReach.state());
    }

    /** The J menu preview: this result shown as it is, nothing worked out. */
    static void preview(Nearest.Result r) {
        frozen = r != null;
        result = r;
    }

    /** The candidates now: quests, mini-quest posts, the placed kinds and the secret discoveries, kept a second. */
    private static List<Nearest.Candidate> candidates(GameState gs) {
        return QuestsCache.kept("nearest-candidates", 1000, () -> {
            List<io.github.anilken.wynnlabs.quests.Content.Line> places = new ArrayList<>();
            for (ContentType t : Nearest.PLACED) {
                places.addAll(QuestsTypes.lines(t));
            }
            Set<String> found = Discoveries.foundInGame();
            String character = gs.characterId();
            return Nearest.gather(gs.quests(), name -> {
                MiniQuests.Mini m = MiniQuests.post(name);
                return m == null ? null : m.at();
            }, places, Discoveries.english(), e -> Discoveries.done(e, found, character));
        });
    }

    private static void ask(Nearest.Request q) {
        pending = q;
        if (QUEUED.compareAndSet(false, true)) {
            WORKER.execute(NearestList::work);
        }
    }

    /** The job: the newest ask, straight-line first while the map is not ready, then by route once it is. */
    private static void work() {
        running = true;
        QUEUED.set(false);
        Nearest.Request q = pending;
        try {
            if (q == null || Nearest.fresh(result, q)) {
                return;
            }
            String state = RouteReach.state();
            if (!"READY".equals(state)) {
                result = Nearest.compute(q, null, state);
            }
            Nearest.Router router = RouteReach.fetch();
            Nearest.Request withMap = new Nearest.Request(q.x(), q.z(), q.level(), q.over(), q.all(), RouteReach.hash(), q.now());
            result = Nearest.compute(withMap, router, RouteReach.state());
        } catch (Throwable t) {
            // the straight-line list stands for this ask, so a failing search is not asked again every frame
            System.out.println("[WynnLabs] nearest: the ranking failed: " + t);
            result = Nearest.compute(q, null, "FAILED");
        } finally {
            running = false;
        }
    }
}
