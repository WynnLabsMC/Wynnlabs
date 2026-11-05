package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.StatRange;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How good a rolled piece is for you: the piece put in its own slot of what you wear (a weapon in
 * place of yours, a ring in place of the weaker of yours), its skill points worked out again, and
 * the build score compared with what you wear now: "+12%". Each rolled stat's part is what the
 * score loses when that stat drops to the bottom of its range, so the stats that matter to how
 * you play come first and one that changes nothing counts 0. Worked out once per piece (its rolls)
 * and build plan.
 */
public final class StatWeights {

    private StatWeights() {}

    /** One stat of the piece: its name, its roll (0 to 1, the higher end better) and its part of the gain, in points of %. */
    public record Stat(String name, double roll, double part) {}

    /** The piece for you: the build score's change with it in its slot, in % (NaN: not known), and its stats, biggest part first. */
    public record Score(double gain, List<Stat> stats) {}

    private static final Map<String, Score> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> QUEUED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** None is not a score (the map holds no null): a piece with nothing to work out, not asked again. */
    private static final Score NONE = new Score(Double.NaN, List.of());
    /** One low-priority worker, as the build plan's own: the game's frames never wait for it. */
    private static final java.util.concurrent.ExecutorService WORKER = io.github.anilken.wynnlabs.core.Workers.singleLow("stat-weights");
    private static volatile Object cachedFor;
    private static volatile int cachedVersion;
    /** The score of what you wear, per plan: the base every listing is compared with. */
    private static volatile double baseFor = Double.NaN;

    /**
     * The score of an identified piece, worked out on a worker thread: null until it is ready (a
     * moment later it is there), and null without a build plan, rolls or a slot to put it in. The
     * piece is read here, on the game's thread; only the model runs on the worker.
     */
    public static Score of(ItemView view, ItemStack stack) {
        if (view == null || !view.isGear() || view.rolled() == null || view.data() == null || view.data().slot() == null) {
            return null;
        }
        Build.Plan p = Build.plan();
        if (p == null || p.current() == null) {
            return null;
        }
        if (p != cachedFor || Rules.version != cachedVersion) {
            CACHE.clear();
            QUEUED.clear();
            baseFor = Double.NaN;
            cachedFor = p;
            cachedVersion = Rules.version;
        }
        String key = ItemJson.keyOf(stack);
        Score s = CACHE.get(key);
        if (s != null) {
            return s == NONE ? null : s;
        }
        if (QUEUED.add(key)) {
            Damage.Piece piece = Damage.piece(view, key, stack);
            List<String> slots = slotsOf(view);
            List<Roll> rolls = List.copyOf(view.rolled().rolls());
            io.github.anilken.wynnlabs.model.Klass cls = io.github.anilken.wynnlabs.game.Wyn.classType();
            WORKER.execute(() -> {
                Score w = null;
                Damage.CLASS_FOR_THREAD.set(cls);
                try {
                    w = piece == null || slots.isEmpty() ? null : work(p, piece, slots, rolls);
                } catch (Throwable t) {
                    w = null;
                } finally {
                    Damage.CLASS_FOR_THREAD.remove();
                }
                if (p == cachedFor) {
                    CACHE.put(key, w == null ? NONE : w);
                }
                QUEUED.remove(key);
            });
        }
        return null;
    }

    /** The slots a piece can go in: its own, or either ring slot. */
    private static List<String> slotsOf(ItemView view) {
        if (view.data().slot() == io.github.anilken.wynnlabs.model.Slot.RING) {
            return List.of("RING1", "RING2");
        }
        String one = Build.slotOf(view.data().slot(), 0);
        return one == null ? List.of() : List.of(one);
    }

    private static Score work(Build.Plan p, Damage.Piece piece, List<String> slots, List<Roll> rolls) {
        double now = baseFor;
        if (Double.isNaN(now)) {
            now = p.detailAsWorn().result().objective();
            baseFor = now;
        }
        if (now <= 0) {
            return null;
        }
        // its slot (a ring in place of whichever of yours it beats more), its skill points worked out once
        String slot = null;
        int[] points = null;
        double with = Double.NEGATIVE_INFINITY;
        for (String sl : slots) {
            int[] pts = p.pointsWith(sl, piece);
            if (pts == null) {
                continue;
            }
            double o = p.detailWith(sl, piece, null, pts).result().objective();
            if (o > with) {
                with = o;
                slot = sl;
                points = pts;
            }
        }
        if (slot == null) {
            return null;
        }
        double gain = (with / now - 1) * 100;
        // each rolled stat's part: the score lost were it at the bottom of its range (same points)
        List<Stat> stats = new ArrayList<>();
        for (Roll r : rolls) {
            StatRange range = r.range();
            if (range == null || range.fixed()) {
                continue;
            }
            int lo = Math.min(range.min(), range.max());
            double drop = r.value() - lo;
            double part = 0;
            if (drop != 0) {
                Map<String, Double> less = new HashMap<>();
                less.put(r.key(), -drop);
                Damage.Piece extra = new Damage.Piece(null, "stat-weight", "stat-weight", null, 0, less, Map.of(), 0,
                        Map.of("neutral", 1.0), 0);
                part = Math.max(0, (with - p.detailWith(slot, piece, extra, points).result().objective()) / now * 100);
            }
            stats.add(new Stat(r.name(), roll(r.value(), range), part));
        }
        stats.sort((x, y) -> Double.compare(y.part(), x.part()));
        return new Score(gain, stats);
    }

    /**
     * Where a value sits in its range, the higher end 1. A cost cut is kept positive ("-25% 3rd
     * Spell Cost" is 25), so the higher end is the better one for every stat; {@code inverted}
     * only says how the game shows it.
     */
    static double roll(int value, StatRange range) {
        int lo = Math.min(range.min(), range.max());
        int hi = Math.max(range.min(), range.max());
        if (hi == lo) {
            return 1;
        }
        return Math.max(0, Math.min(1, (value - lo) / (double) (hi - lo)));
    }
}
