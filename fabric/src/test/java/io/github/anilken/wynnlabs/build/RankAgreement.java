package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The reference benchmarks' fairer measures ({@link ReferenceBuildsBenchmark}, {@link DiscordBuildsBenchmark}):
 * a community build set against the road map's own candidates on equal terms (the build's own
 * tree for both, the same skill point budget, the same goal), and the pairs the community itself
 * compares ("more dps", "better ehp") against the model's order.
 */
final class RankAgreement {

    /** How many of the road map's whole builds at the level (its build window's list) the reference is ranked among. */
    static final int K = 10;

    private RankAgreement() {}

    /** The road map's set at the level and its ranked whole builds there (at most {@link #K}, the set itself usually first). */
    record Candidates(Map<String, Damage.Piece> pick, List<Map<String, Damage.Piece>> sets) {

        static final Candidates NONE = new Candidates(null, List.of());
    }

    /** The road map for the installed class, archetype, goal and level: the milestone's set and the build window's list. */
    static Candidates compute(int level) {
        Roadmap.Computed c = Roadmap.compute(new Roadmap.Inputs(List.of(), Tuning.Choice.PLAIN, true, K), null);
        if (c == null) {
            return Candidates.NONE;
        }
        Map<String, Damage.Piece> set = null;
        for (Roadmap.Step s : c.steps()) {
            if (s.level() == level && !s.build().isEmpty()) {
                set = clean(s.build());
            }
        }
        List<Map<String, Damage.Piece>> sets = new ArrayList<>();
        if (c.builds() != null) {
            c.builds().forEach(b -> sets.add(clean(b.set())));
        }
        return new Candidates(set, sets);
    }

    private static Map<String, Damage.Piece> clean(Map<String, Damage.Piece> in) {
        Map<String, Damage.Piece> out = new LinkedHashMap<>();
        in.forEach((slot, p) -> {
            if (p != null) {
                out.put(slot, p);
            }
        });
        return out;
    }

    /**
     * The reference and the candidates on equal terms: each scored at {@code level} with the tree
     * {@code tree} (the link's own; null: the archetype's plan) and the points the level earns
     * (at most 200, so a tome build gets no extra). {@code refObj} NaN: the reference does not fit
     * that budget. {@code rank}: 1 + the candidates scoring above the reference; {@code pct}: the
     * share of candidates it scores above (ties half); {@code pickObj}: the road map's own set.
     */
    record SameTree(double refObj, double pickObj, double refDps, double refEhp, int k, int rank, double pct) {

        static final SameTree NONE = new SameTree(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, Double.NaN);

        boolean fits() {
            return !Double.isNaN(refObj) && k > 0;
        }

        double ratio() {
            return refObj > 0 && pickObj > 0 ? refObj / pickObj : Double.NaN;
        }

        /** The reference scores at least as high as the weakest of the model's top-K builds. */
        boolean inTopK() {
            return fits() && rank <= k;
        }

        /** The reference scores at least as high as the road map's own set. */
        boolean beatsPick() {
            return fits() && !Double.isNaN(pickObj) && refObj >= pickObj * (1 - 1e-9);
        }
    }

    static int budget(int level) {
        return Math.min(ReferenceBuilds.CAP, ReferenceBuilds.earned(level));
    }

    private static Build.Eval on(Map<String, Damage.Piece> set, int level, TreeModel.Overlay tree) {
        if (set == null || !set.containsKey("WEAPON")) {
            return null;
        }
        return tree != null ? ReferenceBuilds.score(set, level, budget(level), tree) : ReferenceBuilds.score(set, level, budget(level));
    }

    static SameTree rank(Map<String, Damage.Piece> ref, TreeModel.Overlay tree, Candidates c, int level) {
        Build.Eval r = on(ref, level, tree);
        Build.Eval p = on(c.pick(), level, tree);
        List<Double> objs = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        List<Map<String, Damage.Piece>> all = new ArrayList<>();
        if (c.pick() != null) {
            all.add(c.pick());
        }
        all.addAll(c.sets());
        for (Map<String, Damage.Piece> s : all) {
            String key = names(s);
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            Build.Eval e = s == c.pick() ? p : on(s, level, tree);
            if (e != null) {
                objs.add(e.result().objective());
            }
        }
        double pickObj = p == null ? Double.NaN : p.result().objective();
        if (r == null) {
            return new SameTree(Double.NaN, pickObj, Double.NaN, Double.NaN, objs.size(), 0, Double.NaN);
        }
        double ro = r.result().objective();
        int above = 0;
        double below = 0;
        for (double o : objs) {
            if (o > ro * (1 + 1e-9)) {
                above++;
            } else if (o < ro * (1 - 1e-9)) {
                below++;
            } else {
                below += 0.5;
            }
        }
        return new SameTree(ro, pickObj, r.result().spellDps() + r.result().mainDps(), r.result().ehpElemental(), objs.size(),
                1 + above, objs.isEmpty() ? Double.NaN : below / objs.size());
    }

    private static String names(Map<String, Damage.Piece> s) {
        StringBuilder sb = new StringBuilder();
        s.forEach((slot, p) -> sb.append(slot).append('=').append(p.name()).append(';'));
        return sb.toString();
    }

    // ---- pairs the community compares ----

    /** What a claim is about: damage, elemental EHP, or the build as a whole (the objective). */
    enum Dim { DPS, EHP, OVERALL }

    /** "{@code later} has more {@code dim} than {@code earlier}" ({@code sign} +1) or less (−1); indices into the caller's rows. */
    record Claim(int later, int earlier, Dim dim, int sign) {}

    private static final String DPS = "(dps|damage|damag|dmg)";
    private static final String EHP = "(ehp|sustain|survivability|hp|health|tankiness)";
    private static final Pattern DPS_UP = Pattern.compile("\\b(more|higher|better|bit more) " + DPS + "|\\bdps (variant|oriented)");
    private static final Pattern DPS_DOWN = Pattern.compile("\\b(less|lower) " + DPS + "\\b");
    private static final Pattern EHP_UP = Pattern.compile("\\b(more|higher|better|even better) " + EHP + "\\b|\\btankier\\b");
    private static final Pattern EHP_DOWN = Pattern.compile("\\b(less|lower|loosing|losing) ehp\\b|\\bglass(y|ier)?\\b");
    private static final Pattern BETTER = Pattern.compile("\\b(slightly |even )?better\\b(?! " + DPS + "| " + EHP + ")|\\bstronger\\b|\\bupgrade\\b");

    /**
     * The claims a link's text makes against the link just before it from the same poster: "less
     * EHP more DPS" is two. A question, or a text that addresses someone else (@), makes none.
     */
    static List<Claim> claims(int later, int earlier, String text) {
        List<Claim> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("?") || t.contains("@") || t.matches("(?s).*\\b(what'?s|which is) better.*")) {
            return out;
        }
        if (DPS_UP.matcher(t).find()) {
            out.add(new Claim(later, earlier, Dim.DPS, 1));
        } else if (DPS_DOWN.matcher(t).find()) {
            out.add(new Claim(later, earlier, Dim.DPS, -1));
        }
        if (EHP_UP.matcher(t).find()) {
            out.add(new Claim(later, earlier, Dim.EHP, 1));
        } else if (EHP_DOWN.matcher(t).find()) {
            out.add(new Claim(later, earlier, Dim.EHP, -1));
        }
        if (out.isEmpty() && BETTER.matcher(t).find()) {
            out.add(new Claim(later, earlier, Dim.OVERALL, 1));
        }
        return out;
    }

    /** A claim's verdict: 1 the model orders the two the same way, 0 the other way, 0.5 a tie; NaN when either is not scored. */
    static double agree(Claim c, SameTree later, SameTree earlier) {
        if (later == null || earlier == null || !later.fits() || !earlier.fits()) {
            return Double.NaN;
        }
        double a = switch (c.dim()) {
            case DPS -> later.refDps() - earlier.refDps();
            case EHP -> later.refEhp() - earlier.refEhp();
            case OVERALL -> later.refObj() - earlier.refObj();
        };
        double scale = switch (c.dim()) {
            case DPS -> Math.max(later.refDps(), earlier.refDps());
            case EHP -> Math.max(later.refEhp(), earlier.refEhp());
            case OVERALL -> Math.max(later.refObj(), earlier.refObj());
        };
        if (Math.abs(a) <= 1e-6 * Math.abs(scale)) {
            return 0.5;
        }
        return c.sign() * a > 0 ? 1 : 0;
    }

    // ---- tables and baseline metrics ----

    /** The same-tree columns' header and separator in a report table. */
    static final String HEAD = " fits budget | same-tree ratio | percentile | in top K | beats pick |";
    static final String SEP = "---|---|---|---|---|";

    private static List<SameTree> fitting(List<SameTree> same) {
        return same.stream().filter(s -> s != null && s.fits()).toList();
    }

    /** A report table row's same-tree cells (see {@link #HEAD}). */
    static String cells(List<SameTree> same) {
        List<SameTree> fit = fitting(same);
        return String.format(Locale.ROOT, " %d/%d | %.3f | %.2f | %.0f %% | %.0f %% |", fit.size(), same.size(),
                ReferenceBuilds.median(fit.stream().map(SameTree::ratio).filter(v -> !Double.isNaN(v)).toList()),
                BenchmarkBaseline.mean(fit.stream().map(SameTree::pct).toList()),
                BenchmarkBaseline.pct(fit.stream().filter(SameTree::inTopK).count(), fit.size()),
                BenchmarkBaseline.pct(fit.stream().filter(SameTree::beatsPick).count(), fit.size()));
    }

    /** A group's baseline metrics: the ratio's median and share within 10 %, and the same-tree measures. */
    static void metrics(BenchmarkBaseline.Metrics m, String prefix, List<Double> ratios, List<SameTree> same) {
        BenchmarkBaseline.Kind ratio = BenchmarkBaseline.Kind.RATIO;
        BenchmarkBaseline.Kind rate = BenchmarkBaseline.Kind.RATE;
        m.put(prefix + ".median_ratio", ratio, ReferenceBuilds.median(ratios), ratios.size());
        m.put(prefix + ".within_10pct", rate, BenchmarkBaseline.pct(ratios.stream().filter(v -> v >= 0.9).count(), ratios.size()),
                ratios.size());
        List<SameTree> fit = fitting(same);
        m.put(prefix + ".fits_budget", rate, BenchmarkBaseline.pct(fit.size(), same.size()), same.size());
        m.put(prefix + ".same_tree_ratio", ratio,
                ReferenceBuilds.median(fit.stream().map(SameTree::ratio).filter(v -> !Double.isNaN(v)).toList()), fit.size());
        m.put(prefix + ".percentile", ratio, BenchmarkBaseline.mean(fit.stream().map(SameTree::pct).toList()), fit.size());
        m.put(prefix + ".in_top_k", rate, BenchmarkBaseline.pct(fit.stream().filter(SameTree::inTopK).count(), fit.size()),
                fit.size());
        m.put(prefix + ".beats_pick", rate, BenchmarkBaseline.pct(fit.stream().filter(SameTree::beatsPick).count(), fit.size()),
                fit.size());
    }

    /**
     * The pairs players compare: a table of each claim and the model's verdict, and the share the
     * model agrees with as "pairs.agreement". {@code same}, {@code klass} and {@code label} are
     * indexed like the claims; pairs of two classes are not compared.
     */
    static void pairs(StringBuilder md, BenchmarkBaseline.Metrics m, List<Claim> claims, List<SameTree> same, List<String> klass,
            List<String> label) {
        md.append("\n## Pairs players compare\n\n");
        md.append("A link whose text says it has more damage, more EHP, less of either, or is simply better than the poster's")
                .append(" link just before it. Both scored on their own trees with the level's points; damage and EHP claims")
                .append(" compare the model's DPS and elemental EHP, \"better\" its whole score.\n\n");
        md.append("| later | earlier | claim | model: later | model: earlier | agrees |\n|---|---|---|---|---|---|\n");
        List<Double> verdicts = new ArrayList<>();
        for (Claim c : claims) {
            SameTree a = same.get(c.later());
            SameTree b = same.get(c.earlier());
            boolean cross = !String.valueOf(klass.get(c.later())).equals(String.valueOf(klass.get(c.earlier())));
            double v = cross ? Double.NaN : agree(c, a, b);
            if (!Double.isNaN(v)) {
                verdicts.add(v);
            }
            md.append(String.format(Locale.ROOT, "| %s | %s | %s %s | %s | %s | %s |%n", label.get(c.later()), label.get(c.earlier()),
                    c.sign() > 0 ? "more" : "less", c.dim().name().toLowerCase(Locale.ROOT), value(c.dim(), a), value(c.dim(), b),
                    cross ? "different class" : Double.isNaN(v) ? "not scored" : v == 1 ? "yes" : v == 0 ? "no" : "tie"));
        }
        double share = verdicts.isEmpty() ? Double.NaN
                : 100.0 * verdicts.stream().mapToDouble(Double::doubleValue).sum() / verdicts.size();
        md.append(String.format(Locale.ROOT, "%nAgreement: %.0f %% of %d scored pairs (%d claims).%n", share, verdicts.size(),
                claims.size()));
        m.put("pairs.agreement", BenchmarkBaseline.Kind.RATE, share, verdicts.size(), 1);
    }

    private static String value(Dim d, SameTree s) {
        if (s == null || !s.fits()) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.0f", switch (d) {
            case DPS -> s.refDps();
            case EHP -> s.refEhp();
            case OVERALL -> s.refObj();
        });
    }

    /** The plain-words header every build report opens its measures with. */
    static String explain() {
        return """
                How to read the measures:

                - **ratio** (kept for continuity): the community build's score divided by the road map's own pick's score. \
                Above 1 means the model likes the community build more than what it picked. It is circular: a weaker pick \
                raises it too.
                - **same-tree ratio**: the same division, but both builds are scored with the community build's own ability \
                tree and the same skill points (what the level earns, at most 200, so builds that lean on tomes get no extra \
                points and may not fit).
                - **percentile**: the community build scored against the road map's top %d whole builds for that class, \
                archetype, goal and level, all on the same tree and points; 1.0 means it beats every one of them, 0.5 that \
                it sits in the middle.
                - **in top K**: how often the community build scores at least as high as the weakest of those builds.
                - **beats pick**: how often it scores at least as high as the road map's own pick.
                - **pair agreement**: where players compare two of their own builds ("more damage", "better EHP", "slightly \
                better"), how often the model orders the two the same way (damage, elemental EHP or the whole score).
                - **over budget**: community builds that need more skill points than the level earns (usually tome builds); \
                they have no same-tree numbers.

                """.formatted(K);
    }
}
