package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Slot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The mastery tomes that suit a build, worked out by the damage model: a suggestion only, the
 * road map's search never sees them and the build's pieces stay as they are. Tomes are
 * untradable, so there are no prices, and the list ships in {@code tomes.json} (the game API's,
 * middle rolls): nothing is fetched. Each kind has its slots; the best tome overall takes a
 * slot, then the next is scored on top of it. What adds nothing the model can see (sprint,
 * stealing) ends up as "not scored". A class of its own (safe to hot swap in).
 */
public final class TomeAdvice {

    /** The tomes start here; below it a build gets no list. */
    public static final int MIN_LEVEL = 60;
    /** A tome has to add this much to count as a gain. */
    private static final double MIN_GAIN = 0.0005;

    /** The tome slots per kind, as the game has them. */
    public enum Kind {
        WEAPON(2), ARMOUR(4), GUILD(1), LOOTRUN(1), MARATHON(2), MYSTICISM(2), EXPERTISE(2);

        public final int slots;

        Kind(int slots) {
            this.slots = slots;
        }
    }

    /** A tome; {@code copies}: how many of it can be worn, the game has that many twins. */
    public record Tome(String name, Kind kind, int level, String source, int copies, Map<String, Double> stats) {}

    /** A line of the list: {@code gain} a fraction, NaN when not scored; {@code counted}: the build's score has it already. */
    public record Row(Tome tome, double gain, boolean owned, boolean counted) {}

    /** {@code total}: what the scored rows add together. */
    public record Advice(double total, List<Row> rows, List<Row> unscored) {}

    // no initializers: hot swap safe
    private static volatile List<Tome> catalog;
    private static volatile String memoKey;
    private static volatile Advice memo;

    private TomeAdvice() {}

    /** The tomes the mod knows. */
    public static List<Tome> catalog() {
        List<Tome> c = catalog;
        if (c == null) {
            c = load();
            catalog = c;
        }
        return c;
    }

    private static List<Tome> load() {
        List<Tome> out = new ArrayList<>();
        try (InputStream in = TomeAdvice.class.getResourceAsStream("/assets/wynnlabs/build/tomes.json")) {
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                Map<String, Double> stats = new HashMap<>();
                o.getAsJsonObject("stats").entrySet().forEach(s -> stats.put(s.getKey(), s.getValue().getAsDouble()));
                out.add(new Tome(o.get("name").getAsString(), Kind.valueOf(o.get("kind").getAsString()), o.get("level").getAsInt(),
                        o.get("source").getAsString(), o.get("copies").getAsInt(), Map.copyOf(stats)));
            }
        } catch (Exception e) {
            return List.of();
        }
        return List.copyOf(out);
    }

    /** The game marks a tome's twins with a sign after the name. */
    public static String plain(String name) {
        return name.replace("֎", "").trim();
    }

    /** The list for a road map milestone's set, kept while nothing about it changes. */
    public static Advice forSet(Map<String, Damage.Piece> set, Build.Eval eval, int level, TreeModel.Overlay tree) {
        if (level < MIN_LEVEL || set.get("WEAPON") == null || eval == null) {
            return null;
        }
        return memo(set, eval, level, tree);
    }

    /** The list for a saved build, spread as the build's page spreads it. */
    public static Advice forPlan(Plans.Build plan, TreeModel.Overlay tree) {
        if (plan == null || plan.level() < MIN_LEVEL) {
            return null;
        }
        String key = "plan|" + plan.id() + '|' + plan.updated() + '|' + plan.level() + '|' + owned() + '|' + (tree == null ? "" : tree.key());
        if (key.equals(memoKey)) {
            return memo;
        }
        Map<String, Damage.Piece> set = BuildStats.piecesOf(plan);
        if (set.get("WEAPON") == null) {
            return null;
        }
        int level = plan.level();
        Build.Eval eval;
        if (plan.points() != null) {
            int[] points = plan.points().stream().mapToInt(p -> p == null ? 0 : p).toArray();
            eval = new Build.Eval(points, null);
        } else {
            Build.Eval e = Build.allocate(set, level, Math.max(0, (Math.min(level, 101) - 1) * 2));
            eval = e != null ? e : new Build.Eval(Build.minPoints(set.values()), null);
        }
        Advice a = TreeModel.with(tree, () -> advise(set, eval, level, owned()));
        memoKey = key;
        memo = a;
        return a;
    }

    private static Advice memo(Map<String, Damage.Piece> set, Build.Eval eval, int level, TreeModel.Overlay tree) {
        StringBuilder key = new StringBuilder().append(level).append('|').append(Arrays.toString(eval.allocation()));
        set.forEach((slot, p) -> key.append('|').append(p == null ? "" : p.name()));
        key.append('|').append(eval.tome() == null ? "" : Arrays.toString(eval.tome().points())).append(owned());
        key.append('|').append(tree == null ? "" : tree.key());
        String k = key.toString();
        if (!k.equals(memoKey)) {
            memo = TreeModel.with(tree, () -> advise(set, eval, level, owned()));
            memoKey = k;
        }
        return memo;
    }

    private static Set<String> owned() {
        Set<String> out = new java.util.TreeSet<>();
        GameState.current().tomes().forEach(n -> out.add(plain(n)));
        return out;
    }

    /** The list for {@code set} with {@code eval}'s spread at {@code level}; {@code owned} by plain name. */
    static Advice advise(Map<String, Damage.Piece> set, Build.Eval eval, int level, Set<String> owned) {
        List<Damage.Piece> worn = new ArrayList<>();
        set.forEach((slot, p) -> {
            if (!"WEAPON".equals(slot) && p != null) {
                worn.add(p);
            }
        });
        Damage.Piece weapon = set.get("WEAPON");
        int[] points = Tomes.plus(eval.allocation(), eval.tome());
        double base = Damage.evaluate(worn, weapon, points, level).objective();
        if (base <= 0) {
            return new Advice(0, List.of(), List.of());
        }
        Map<Kind, Integer> open = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) {
            open.put(k, k.slots);
        }
        List<Row> rows = new ArrayList<>();
        // the guild tome the score has: the one assumed, or yours (its points are in the spread)
        Tome guild = guildCounted(eval, owned);
        if (guild != null) {
            open.put(Kind.GUILD, 0);
            rows.add(new Row(guild, Double.NaN, owned.contains(guild.name()), true));
        }
        List<Tome> left = new ArrayList<>(catalog().stream().filter(t -> t.level() <= level).toList());
        Map<Tome, Damage.Piece> pieces = new HashMap<>();
        Map<String, Integer> used = new HashMap<>();
        double now = base;
        while (true) {
            Tome best = null;
            double bestScore = now * (1 + MIN_GAIN);
            for (Tome t : left) {
                if (open.get(t.kind()) <= 0 || used.getOrDefault(t.name(), 0) >= t.copies()) {
                    continue;
                }
                worn.add(pieces.computeIfAbsent(t, TomeAdvice::piece));
                double score = Damage.evaluate(worn, weapon, points, level).objective();
                worn.remove(worn.size() - 1);
                if (score > bestScore || score == bestScore && best != null && owned.contains(t.name())) {
                    best = t;
                    bestScore = score;
                }
            }
            if (best == null) {
                break;
            }
            worn.add(pieces.get(best));
            open.merge(best.kind(), -1, Integer::sum);
            used.merge(best.name(), 1, Integer::sum);
            rows.add(new Row(best, bestScore / now - 1, owned.contains(best.name()), false));
            now = bestScore;
        }
        rows.sort(Comparator.comparing((Row r) -> r.tome().kind()).thenComparing(r -> r.counted() ? 1 : 0)
                .thenComparing(r -> -r.gain()));
        return new Advice(now / base - 1, List.copyOf(rows), unscored(left, open, used, owned));
    }

    /** The slots nothing scored fills: your tomes first, then the highest level. */
    private static List<Row> unscored(List<Tome> left, Map<Kind, Integer> open, Map<String, Integer> used, Set<String> owned) {
        List<Row> out = new ArrayList<>();
        for (Kind k : Kind.values()) {
            left.stream().filter(t -> t.kind() == k && !used.containsKey(t.name()))
                    .sorted(Comparator.comparing((Tome t) -> !owned.contains(t.name()))
                            .thenComparing(t -> -t.level()).thenComparing(Tome::name))
                    .limit(open.get(k)).forEach(t -> out.add(new Row(t, Double.NaN, owned.contains(t.name()), false)));
        }
        return List.copyOf(out);
    }

    private static Tome guildCounted(Build.Eval eval, Set<String> owned) {
        String assumed = eval.tome() == null ? null : eval.tome().name();
        for (Tome t : catalog()) {
            if (t.kind() == Kind.GUILD && (t.name().equals(assumed) || owned.contains(t.name()))) {
                return t;
            }
        }
        return null;
    }

    /** A tome as a piece of gear: the model sums its stats with the rest. */
    private static Damage.Piece piece(Tome t) {
        return new Damage.Piece(null, t.name(), t.name(), Slot.RING, t.level(), t.stats(), Map.of(), 0, Map.of(), 0, false);
    }
}
