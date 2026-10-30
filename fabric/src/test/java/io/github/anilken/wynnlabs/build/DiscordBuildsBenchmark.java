package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.build.roadmap.BeamCache;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recent community builds against the mod's own pick: a local, git-ignored list of WynnBuilder
 * links (common/src/local/data/discord-builds.json, looked up from the project folder and the ones
 * above it, so a worktree finds the main checkout's copy). Each link is decoded, its class and
 * archetype read from its own ability tree, scored at its level with that tree (the Damage goal),
 * and set against the road map's pick for the same class, archetype and level (once per key, the
 * tests' narrow search). Opt-in (-PreferenceBuilds) and skipped when the file is missing. Writes
 * build/reference-builds/report-discord.md and discord.csv; the data itself never enters the repo.
 */
class DiscordBuildsBenchmark {

    static final String DATA = "common/src/local/data/discord-builds.json";

    @AfterEach
    void reset() {
        FixtureState.uninstall();
        BeamCache.clear();
    }

    /** One link's outcome; {@code status} is "ok" or why it was left out. */
    record Row(int index, String time, String klass, String archetype, String treeFrom, int level, String version,
            boolean newer, boolean crafted, boolean custom, int tomes, String status, String dropped, double refObj,
            double refPlanObj, double pickObj, double refDps, double pickDps, double refEhp, double pickEhp, String url) {

        double ratio() {
            return refObj > 0 && pickObj > 0 ? refObj / pickObj : Double.NaN;
        }

        /** The link's items scored with the archetype's plan instead of its own tree, against the same pick. */
        double planRatio() {
            return refPlanObj > 0 && pickObj > 0 ? refPlanObj / pickObj : Double.NaN;
        }

        boolean scored() {
            return !Double.isNaN(ratio());
        }

        /** No crafted or custom piece: every slot the link fills is a plain item. */
        boolean plain() {
            return !crafted && !custom;
        }
    }

    private record Pick(Build.Eval eval, long millis) {}

    private final Map<String, Pick> picks = new HashMap<>();
    private long pickMillis;

    static Path data() {
        Path dir = Path.of(System.getProperty("wynnlabs.projectDir", ".")).toAbsolutePath();
        for (; dir != null; dir = dir.getParent()) {
            Path f = dir.resolve(DATA);
            if (Files.isRegularFile(f)) {
                return f;
            }
        }
        return null;
    }

    @Test
    @EnabledIfSystemProperty(named = ReferenceBuilds.PROPERTY, matches = "true")
    void discordBuildsAgainstThePick() throws Exception {
        Path f = data();
        Assumptions.assumeTrue(f != null, "no local " + DATA);
        long t0 = System.nanoTime();
        JsonObject doc = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Row> rows = new ArrayList<>();
        int i = 0;
        for (JsonElement el : doc.getAsJsonArray("builds")) {
            JsonObject e = el.getAsJsonObject();
            String url = text(e, "url");
            try {
                rows.add(run(i, text(e, "time"), url));
            } catch (Throwable t) {
                rows.add(junk(i, text(e, "time"), url, "error: " + t.getClass().getSimpleName() + " " + t.getMessage()));
            } finally {
                FixtureState.uninstall();
            }
            i++;
        }
        write(rows, (System.nanoTime() - t0) / 1_000_000);
        assertTrue(rows.stream().anyMatch(Row::scored), "no build scored");
    }

    private static String text(JsonObject e, String k) {
        return e.has(k) && !e.get(k).isJsonNull() ? e.get(k).getAsString() : null;
    }

    private static Row junk(int index, String time, String url, String why) {
        return new Row(index, time, "", "", "", 0, "", false, false, false, 0, why, "", Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, url);
    }

    private static boolean empty(WynnBuilderLink.Piece p) {
        return p.kind() == WynnBuilderLink.Kind.ITEM && (p.name() == null || p.name().startsWith("No "));
    }

    private Row run(int index, String time, String url) throws Exception {
        if (url == null || !url.matches("(?i)https?://[^#]*/builder(\\.html)?/?#.*")) {
            return junk(index, time, url, "junk: not a builder link");
        }
        WynnBuilderLink.Build b = WynnBuilderLink.decode(url);
        if (b == null) {
            return junk(index, time, url, "junk: " + ReferenceBuildsBenchmark.decodeFailure(url).replace("decode: ", ""));
        }
        if (b.pieces().stream().allMatch(DiscordBuildsBenchmark::empty)) {
            return junk(index, time, url, "junk: empty build");
        }
        boolean crafted = b.pieces().stream().anyMatch(p -> p.kind() == WynnBuilderLink.Kind.CRAFTED);
        boolean custom = b.pieces().stream().anyMatch(p -> p.kind() == WynnBuilderLink.Kind.CUSTOM);
        ReferenceBuilds.installPlain();
        Klass k = ReferenceBuilds.classOf(b);
        if (k == null) {
            return new Row(index, time, "", "", "", b.level(), b.version(), b.newer(), crafted, custom, b.tomes().size(),
                    "no class (crafted, custom or unknown weapon)", "", Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, url);
        }
        WynnBuilderTree.Tree tree = WynnBuilderTree.of(b, k);
        String arch = "";
        if (tree != null) {
            List<String> known = ReferenceBuilds.archetypes(k);
            int best = 0;
            for (Map.Entry<String, Integer> en : tree.archetypes().entrySet()) {
                if (known.contains(en.getKey()) && en.getValue() > best) {
                    best = en.getValue();
                    arch = en.getKey();
                }
            }
        }
        int level = b.level();
        ReferenceBuilds.install(k, level, arch);
        ReferenceBuilds.LinkSet ls = ReferenceBuilds.set(b);
        TreeModel.Overlay own = LinkImport.overlay(LinkImport.of(GameState.current(), b));
        String treeFrom = own != null ? "link" : "plan";
        int need = ReferenceBuilds.need(ls.set().values());
        int earned = ReferenceBuilds.earned(level);
        Build.Eval ref = null;
        Build.Eval refPlan = null;
        if (ls.hasWeapon()) {
            int budget = Math.max(earned, need);
            refPlan = ReferenceBuilds.score(ls.set(), level, budget);
            ref = own != null ? TreeModel.with(own, () -> Build.allocateBest(ls.set(), level, budget)) : refPlan;
        }
        Pick pick = pick(k, arch, level);
        String status = !ls.hasWeapon() ? "no weapon in the model" : ref == null ? "does not fit: a skill needs over 100 assigned" : "ok";
        Damage.Result rr = ref == null ? null : ref.result();
        Damage.Result pr = pick.eval() == null ? null : pick.eval().result();
        Row row = new Row(index, time, k.name(), arch, treeFrom, level, b.version(), b.newer(), crafted, custom,
                b.tomes().size(), status, String.join(" ", ls.dropped()),
                rr == null ? Double.NaN : rr.objective(), refPlan == null ? Double.NaN : refPlan.result().objective(), pr == null ? Double.NaN : pr.objective(),
                rr == null ? Double.NaN : rr.spellDps() + rr.mainDps(), pr == null ? Double.NaN : pr.spellDps() + pr.mainDps(),
                rr == null ? Double.NaN : rr.ehpElemental(), pr == null ? Double.NaN : pr.ehpElemental(), url);
        Fixtures.note(String.format(Locale.ROOT, "discord %d %s %s Lv %d: %s ref %.4g pick %.4g", index, k, arch, level,
                status, row.refObj(), row.pickObj()));
        return row;
    }

    /** The road map's set for the class, archetype and level, scored with the archetype's plan; once per key. */
    private Pick pick(Klass k, String arch, int level) {
        String key = k + "|" + arch + "|" + level;
        Pick p = picks.get(key);
        if (p != null) {
            return p;
        }
        long t0 = System.nanoTime();
        BeamCache.clear();
        Roadmap.Computed c = Roadmap.compute(new Roadmap.Inputs(List.of(), Tuning.Choice.PLAIN, true, 0), null);
        Map<String, Damage.Piece> set = null;
        if (c != null) {
            for (Roadmap.Step s : c.steps()) {
                if (s.level() == level && !s.build().isEmpty()) {
                    set = new LinkedHashMap<>();
                    for (Map.Entry<String, Damage.Piece> en : s.build().entrySet()) {
                        if (en.getValue() != null) {
                            set.put(en.getKey(), en.getValue());
                        }
                    }
                }
            }
        }
        Build.Eval eval = set == null ? null : ReferenceBuilds.score(set, level, ReferenceBuilds.earned(level));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        pickMillis += ms;
        p = new Pick(eval, ms);
        picks.put(key, p);
        Fixtures.note("pick " + key + " in " + ms + " ms");
        return p;
    }

    // ---- the report ----

    private static final String[] COLUMNS = {"index", "time", "class", "archetype", "tree", "level", "version", "newer",
        "crafted", "custom", "tomes", "status", "dropped", "ref_objective", "ref_plan_objective", "pick_objective", "ratio", "plan_ratio", "ref_dps", "pick_dps",
        "ref_ehp_elemental", "pick_ehp_elemental", "url"};

    private void write(List<Row> rows, long ms) throws Exception {
        StringBuilder csv = new StringBuilder(String.join(",", COLUMNS)).append('\n');
        for (Row r : rows) {
            Object[] v = {r.index(), r.time(), r.klass(), r.archetype(), r.treeFrom(), r.level(), r.version(), r.newer(),
                r.crafted(), r.custom(), r.tomes(), r.status(), r.dropped(), r.refObj(), r.refPlanObj(), r.pickObj(), r.ratio(), r.planRatio(), r.refDps(),
                r.pickDps(), r.refEhp(), r.pickEhp(), r.url()};
            List<String> cells = new ArrayList<>();
            for (Object o : v) {
                cells.add(ReferenceBuilds.csv(o));
            }
            csv.append(String.join(",", cells)).append('\n');
        }
        Files.writeString(ReferenceBuilds.out("discord.csv"), csv, StandardCharsets.UTF_8);

        List<Row> scored = rows.stream().filter(Row::scored).toList();
        StringBuilder md = new StringBuilder("# Reference builds: recent community builds against the road map's pick\n\n");
        md.append("Source: the local ").append(DATA).append(" (").append(rows.size()).append(" links, ").append(scored.size())
                .append(" scored). Run time ").append(ms / 1000).append(" s, of which road map picks ").append(pickMillis / 1000)
                .append(" s for ").append(picks.size()).append(" class/archetype/level combinations (the tests' narrow search).\n\n");
        md.append("ratio = the link's objective (its own tree when it has one, else the archetype's plan) / the pick's objective")
                .append(" (the archetype's plan), both at the link's level with the Damage goal. Class from the link's weapon,")
                .append(" archetype from the most nodes its tree takes.\n\n");
        Map<String, Integer> status = new TreeMap<>();
        rows.forEach(r -> status.merge(r.status(), 1, Integer::sum));
        md.append("## Links\n\n| status | links |\n|---|---|\n");
        status.forEach((s, n) -> md.append("| ").append(s).append(" | ").append(n).append(" |\n"));
        md.append("\nScored with the link's own tree: ").append(scored.stream().filter(r -> r.treeFrom().equals("link")).count())
                .append("; with the archetype plan (no tree read): ")
                .append(scored.stream().filter(r -> r.treeFrom().equals("plan")).count()).append(".\n\n");

        md.append("## Per archetype (every scored link)\n\n");
        table(md, scored, r -> r.klass() + " " + (r.archetype().isEmpty() ? "(none)" : r.archetype()));
        md.append("\n## Per archetype (no crafted or custom piece)\n\n");
        table(md, scored.stream().filter(Row::plain).toList(), r -> r.klass() + " " + (r.archetype().isEmpty() ? "(none)" : r.archetype()));
        md.append("\n## By data version\n\n");
        table(md, scored, r -> r.version() + (r.newer() ? " (newer than the table)" : ""));
        md.append("\n## Crafted or custom\n\n");
        table(md, scored, r -> r.plain() ? "no crafted/custom" : "crafted or custom");
        md.append("\n## By level\n\n");
        table(md, scored, r -> r.level() >= 106 ? "106" : r.level() >= 100 ? "100-105" : "below 100");
        Files.writeString(ReferenceBuilds.out("report-discord.md"), md, StandardCharsets.UTF_8);
        Fixtures.note("discord builds: " + ReferenceBuilds.out("report-discord.md"));
    }

    private static void table(StringBuilder md, List<Row> rows, java.util.function.Function<Row, String> by) {
        Map<String, List<Row>> groups = new TreeMap<>();
        rows.forEach(r -> groups.computeIfAbsent(by.apply(r), x -> new ArrayList<>()).add(r));
        md.append("| group | n | median ratio | within 10 % or above | median ratio, archetype plan | median ref/pick DPS | median ref/pick eEHP |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        groups.forEach((g, list) -> line(md, g, list));
        line(md, "**overall**", rows);
    }

    private static void line(StringBuilder md, String name, List<Row> list) {
        Predicate<Row> ok = r -> r.ratio() >= 0.9;
        List<Double> dps = list.stream().filter(r -> r.pickDps() > 0).map(r -> r.refDps() / r.pickDps()).toList();
        List<Double> ehp = list.stream().filter(r -> r.pickEhp() > 0).map(r -> r.refEhp() / r.pickEhp()).toList();
        md.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.0f %% | %.3f | %.2f | %.2f |%n", name, list.size(),
                ReferenceBuilds.median(list.stream().map(Row::ratio).toList()),
                list.isEmpty() ? 0 : 100.0 * list.stream().filter(ok).count() / list.size(),
                ReferenceBuilds.median(list.stream().map(Row::planRatio).filter(v -> !Double.isNaN(v)).toList()), ReferenceBuilds.median(dps),
                ReferenceBuilds.median(ehp)));
    }
}
