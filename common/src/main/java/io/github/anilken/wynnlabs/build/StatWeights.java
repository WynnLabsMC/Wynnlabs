package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.StatRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How good a rolled piece is for you: each identification's roll (0 to 100 % of its range, the
 * higher, better end 100) weighed by how much that stat moves your build. The weight is the build score's
 * gain from the stat's whole range added to what you wear, as the damage page's what-ifs work it
 * out; the strongest stat of the piece weighs 1, a stat that changes nothing 0. So a Ritualist's
 * Aura Cost counts in full and a Healing Efficiency roll not at all.
 */
public final class StatWeights {

    private StatWeights() {}

    /** One stat of the piece: its name, its roll (0 to 1) and its weight (0 to 1). */
    public record Stat(String name, double roll, double weight) {}

    /** The piece for you: the weighed roll (0 to 1; NaN when no stat counts) and its stats. */
    public record Score(double value, List<Stat> stats) {}

    private static final Map<String, Map<String, Double>> CACHE = new HashMap<>();
    private static Object cachedFor;
    private static int cachedVersion;

    /** The score of an identified piece; null without a build plan or rolls. */
    public static synchronized Score of(ItemView view) {
        if (view == null || !view.isGear() || view.rolled() == null || view.data() == null) {
            return null;
        }
        Build.Plan p = Build.plan();
        if (p == null || p.current() == null) {
            return null;
        }
        if (p != cachedFor || Rules.version != cachedVersion) {
            CACHE.clear();
            cachedFor = p;
            cachedVersion = Rules.version;
        }
        Map<String, Double> weights = CACHE.computeIfAbsent(view.data().name(), k -> weights(p, view.rolled().rolls()));
        List<Stat> stats = new ArrayList<>();
        double sum = 0;
        double total = 0;
        for (Roll r : view.rolled().rolls()) {
            StatRange range = r.range();
            if (range == null || range.fixed()) {
                continue;
            }
            double roll = roll(r.value(), range);
            double w = weights.getOrDefault(r.key(), 0.0);
            stats.add(new Stat(r.name(), roll, w));
            sum += roll * w;
            total += w;
        }
        return new Score(total > 0 ? sum / total : Double.NaN, stats);
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

    /** Each rolled stat's gain to your build score from its whole range, the largest scaled to 1. */
    private static Map<String, Double> weights(Build.Plan p, List<Roll> rolls) {
        Damage.Detail base = p.currentDetail(null, null);
        double score = base.result().objective();
        Map<String, Double> gain = new LinkedHashMap<>();
        double top = 0;
        for (Roll r : rolls) {
            StatRange range = r.range();
            if (range == null || range.fixed() || gain.containsKey(r.key())) {
                continue;
            }
            double delta = Math.abs(range.max() - range.min());   // toward the better (higher) end
            Map<String, Double> stats = new HashMap<>();
            stats.put(r.key(), delta);
            Damage.Piece extra = new Damage.Piece(null, "stat-weight", "stat-weight", null, 0, stats, Map.of(), 0,
                    Map.of("neutral", 1.0), 0);
            double g = score > 0 ? p.currentDetail(null, extra).result().objective() / score - 1 : 0;
            g = Math.max(0, g);
            gain.put(r.key(), g);
            top = Math.max(top, g);
        }
        Map<String, Double> out = new HashMap<>();
        for (var e : gain.entrySet()) {
            out.put(e.getKey(), top > 0 ? e.getValue() / top : 0);
        }
        return out;
    }
}
