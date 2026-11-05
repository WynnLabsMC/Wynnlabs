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

    private static final Map<String, Score> CACHE = new HashMap<>();
    /** Pieces waiting to be worked out, oldest first: {view, stack}. */
    private static final Map<String, Object[]> QUEUE = new java.util.LinkedHashMap<>();
    /** None is not a score: a piece with nothing to work out, kept so it is not asked again. */
    private static final Score NONE = new Score(Double.NaN, List.of());
    private static Object cachedFor;
    private static int cachedVersion;

    /** How long one frame may spend working pieces out, in ns. A method: hot swaps keep no field values. */
    static long budget() {
        return 3_000_000L;
    }

    /**
     * The score of an identified piece: null until it is worked out. A page of listings is worked
     * out a few at a time ({@link #budget} a frame, on the game's own thread) so the game does not
     * stall; asked again a moment later it is there. Null too without a build plan, rolls or a slot.
     */
    public static synchronized Score of(ItemView view, ItemStack stack) {
        if (view == null || !view.isGear() || view.rolled() == null || view.data() == null || view.data().slot() == null) {
            return null;
        }
        Build.Plan p = Build.plan();
        if (p == null || p.current() == null) {
            return null;
        }
        if (p != cachedFor || Rules.version != cachedVersion) {
            CACHE.clear();
            QUEUE.clear();
            cachedFor = p;
            cachedVersion = Rules.version;
        }
        String key = ItemJson.keyOf(stack);
        Score s = CACHE.get(key);
        if (s != null) {
            return s == NONE ? null : s;
        }
        QUEUE.putIfAbsent(key, new Object[] {view, stack.copy()});
        pump(p);
        s = CACHE.get(key);
        return s == null || s == NONE ? null : s;
    }

    /** Works queued pieces out until this frame's {@link #budget} is spent. */
    private static void pump(Build.Plan p) {
        long until = System.nanoTime() + budget();
        var it = QUEUE.entrySet().iterator();
        while (it.hasNext() && System.nanoTime() < until) {
            var e = it.next();
            it.remove();
            Score w;
            try {
                w = work(p, (ItemView) e.getValue()[0], (ItemStack) e.getValue()[1]);
            } catch (Throwable t) {
                w = null;
            }
            CACHE.put(e.getKey(), w == null ? NONE : w);
        }
    }

    private static Score work(Build.Plan p, ItemView view, ItemStack stack) {
        Damage.Piece piece = Damage.piece(view, ItemJson.keyOf(stack), stack);
        if (piece == null) {
            return null;
        }
        Damage.Detail base = p.detailAsWorn();
        double now = base.result().objective();
        if (now <= 0) {
            return null;
        }
        // its slot: a ring in place of whichever of yours it beats more
        List<String> slots = new ArrayList<>();
        if (view.data().slot() == io.github.anilken.wynnlabs.model.Slot.RING) {
            slots.add("RING1");
            slots.add("RING2");
        } else {
            String one = Build.slotOf(view.data().slot(), 0);
            if (one == null) {
                return null;
            }
            slots.add(one);
        }
        String slot = null;
        double with = Double.NEGATIVE_INFINITY;
        for (String sl : slots) {
            Damage.Detail d = p.detailWith(sl, piece, null);
            if (d != null && d.result().objective() > with) {
                with = d.result().objective();
                slot = sl;
            }
        }
        if (slot == null) {
            return new Score(Double.NaN, List.of());
        }
        double gain = (with / now - 1) * 100;
        // each rolled stat's part: the score lost were it at the bottom of its range
        List<Stat> stats = new ArrayList<>();
        for (Roll r : view.rolled().rolls()) {
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
                Damage.Detail d = p.detailWith(slot, piece, extra);
                if (d != null) {
                    part = Math.max(0, (with - d.result().objective()) / now * 100);
                }
            }
            stats.add(new Stat(r.name(), roll(r.value(), range), part));
        }
        stats.sort((a, b) -> Double.compare(b.part(), a.part()));
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
