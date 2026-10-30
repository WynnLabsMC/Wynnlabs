package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.build.roadmap.BeamCache;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The community guide's builds (reference-builds/guide.json, from the forum thread "The Ultimate
 * Build Guide") against the mod's own pick: each link decoded, scored with the model at its level
 * for its class and archetype (the archetype's tree plan, the Damage goal), and the road map's set
 * for the same class, archetype and level scored the same way. A ratio near or above 1 means the
 * model rates the community's build as well as its own pick. Opt-in (-PreferenceBuilds): it runs
 * the road map once per class, archetype and level. Writes build/reference-builds/report.md and
 * guide.csv; it fails only when nothing could be scored.
 */
class ReferenceBuildsBenchmark {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
        BeamCache.clear();
    }

    /** One entry's outcome. */
    record Row(int id, String post, String klass, String archetype, String archFrom, String mythic, Boolean crafted,
            String tags, int level, String status, String dropped, int tomes, int need, double refObj, double pickObj,
            double refDps, double pickDps, double refEhp, double pickEhp, double refRegen, double refRegenF,
            double pickRegen, double pickRegenF, double refLife, double pickLife, String refMajor, String pickMajor,
            String pickSet, String url, double refPlanObj, String refTree, String refParts,
            String pickParts) {

        double ratio() {
            return refObj > 0 && pickObj > 0 ? refObj / pickObj : Double.NaN;
        }

        /** The guide build scored with the archetype's plan tree (as the pick is), not its own. */
        double planRatio() {
            return refPlanObj > 0 && pickObj > 0 ? refPlanObj / pickObj : Double.NaN;
        }

        boolean scored() {
            return !Double.isNaN(ratio());
        }

        /** Every piece of the link in the model: no crafted, custom or unknown piece left out. */
        boolean complete() {
            return dropped.isEmpty();
        }
    }

    /** A road map pick, kept for the run per class, archetype and level. */
    record Pick(Map<String, Damage.Piece> set, Build.Eval eval, List<ItemData> items, long millis) {}

    private final Map<String, Pick> picks = new HashMap<>();
    /** Per entry: whether its class came from the link's weapon ("link") or the guide's text ("text"). */
    private final Map<Integer, String> classFrom = new HashMap<>();
    private long pickMillis;

    @Test
    @EnabledIfSystemProperty(named = ReferenceBuilds.PROPERTY, matches = "true")
    void guideBuildsAgainstThePick() throws Exception {
        long t0 = System.nanoTime();
        JsonObject doc = ReferenceBuilds.resource("guide.json");
        List<Row> rows = new ArrayList<>();
        for (JsonElement el : doc.getAsJsonArray("builds")) {
            JsonObject e = el.getAsJsonObject();
            try {
                rows.add(run(e));
            } catch (Throwable t) {
                rows.add(failed(e, "error: " + t.getClass().getSimpleName() + " " + t.getMessage()));
            } finally {
                FixtureState.uninstall();
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        write(doc, rows, ms);
        assertTrue(rows.stream().anyMatch(Row::scored), "no build scored");
    }

    private static String text(JsonObject e, String k) {
        return e.has(k) && !e.get(k).isJsonNull() ? e.get(k).getAsString() : null;
    }

    private static Row failed(JsonObject e, String why) {
        return new Row(e.get("id").getAsInt(), text(e, "post"), text(e, "class"), text(e, "archetype"), "", text(e, "mythic"),
                e.get("crafted").isJsonNull() ? null : e.get("crafted").getAsBoolean(), tags(e), 0, why, "", 0, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, "", "", "", text(e, "url"), Double.NaN, "", "", "");
    }

    /** Why a link did not decode: not a build page at all, text pasted after the link, or a link the decoder cannot read. */
    static String decodeFailure(String url) {
        if (url.matches("(?i)https?://[^#]*/(customizer|crafter|item|custom)(\\.html)?/?#.*")) {
            return "decode: not a build link (item, crafter or customizer page)";
        }
        if (url.matches("https?://[^#]*#C[0-9A-Za-z+\\-]+[^0-9A-Za-z+\\-].*")) {
            return "decode: text pasted after the link";
        }
        return "decode: malformed";
    }

    private static String tags(JsonObject e) {
        List<String> out = new ArrayList<>();
        e.getAsJsonArray("tags").forEach(t -> out.add(t.getAsString()));
        return String.join(" ", out);
    }

    private Row run(JsonObject e) throws Exception {
        String url = text(e, "url");
        WynnBuilderLink.Build b = WynnBuilderLink.decode(url);
        if (b == null) {
            return failed(e, decodeFailure(url));
        }
        ReferenceBuilds.installPlain();
        Klass k = ReferenceBuilds.classOf(b);
        String archFrom = "label";
        classFrom.put(e.get("id").getAsInt(), k == null ? "text" : "link");
        if (k == null && text(e, "class") != null) {
            k = Klass.valueOf(text(e, "class"));
        }
        if (k == null) {
            return failed(e, "no class (crafted or unknown weapon, no archetype in the text)");
        }
        String arch = text(e, "archetype");
        if (arch == null || !ReferenceBuilds.archetypes(k).contains(arch)) {
            archFrom = arch == null ? "none" : "not in " + k + " tree: " + arch;
            arch = "";
        }
        int level = b.level();
        ReferenceBuilds.install(k, level, arch);
        ReferenceBuilds.LinkSet ls = ReferenceBuilds.set(b);
        String dropped = String.join(" ", ls.dropped());
        if (!b.tomes().isEmpty()) {
            dropped = (dropped + " tomes:" + b.tomes().size()).trim();
        }
        if (b.newer()) {
            dropped = (dropped + " newer-data-version:" + b.version()).trim();
        }
        int need = ReferenceBuilds.need(ls.set().values());
        Pick pick = pick(k, arch, level);
        int earned = ReferenceBuilds.earned(level);
        // the guide build with its own tree where the link has one the model reads, else the archetype's plan
        Build.Eval refPlan = ls.hasWeapon() ? ReferenceBuilds.score(ls.set(), level, Math.max(earned, need)) : null;
        Build.Eval ref = ls.hasWeapon() && ls.tree() != null
                ? ReferenceBuilds.score(ls.set(), level, Math.max(earned, need), ls.tree()) : refPlan;
        String status = !ls.hasWeapon() ? "no weapon" : ref == null ? "does not fit: a skill needs over 100 assigned" : need > earned ? "over cap (scored with " + need + ")" : "ok";
        Damage.Result rr = ref == null ? null : ref.result();
        Damage.Result pr = pick.eval() == null ? null : pick.eval().result();
        double refRegen = ReferenceBuilds.regen(ls.set().values());
        double pickRegen = pick.set() == null ? Double.NaN : ReferenceBuilds.regen(pick.set().values());
        List<String> names = new ArrayList<>();
        if (pick.set() != null) {
            pick.set().forEach((slot, p) -> names.add(p == null ? "-" : p.name()));
        }
        Row row = new Row(e.get("id").getAsInt(), text(e, "post"), k.name(), arch, archFrom, text(e, "mythic"),
                e.get("crafted").isJsonNull() ? null : e.get("crafted").getAsBoolean(), tags(e), level, status, dropped,
                b.tomes().size(), need,
                rr == null ? Double.NaN : rr.objective(), pr == null ? Double.NaN : pr.objective(),
                rr == null ? Double.NaN : rr.spellDps() + rr.mainDps(), pr == null ? Double.NaN : pr.spellDps() + pr.mainDps(),
                rr == null ? Double.NaN : rr.ehpElemental(), pr == null ? Double.NaN : pr.ehpElemental(),
                refRegen, ReferenceBuilds.regenFactor(refRegen, level), pickRegen,
                Double.isNaN(pickRegen) ? Double.NaN : ReferenceBuilds.regenFactor(pickRegen, level),
                rr == null || rr.factors().length < 2 ? Double.NaN : rr.factors()[1],
                pr == null || pr.factors().length < 2 ? Double.NaN : pr.factors()[1],
                ReferenceBuilds.majorIds(ls.items()), ReferenceBuilds.majorIds(pick.items()), String.join(" / ", names), url,
                refPlan == null ? Double.NaN : refPlan.result().objective(), ls.tree() != null ? "own" : "plan", parts(rr),
                parts(pr));
        Fixtures.note(String.format(Locale.ROOT, "ref %d %s %s Lv %d: %s ref %.4g pick %.4g", row.id(), k, arch, level, status,
                row.refObj(), row.pickObj()));
        return row;
    }

    /** Where a result's damage comes from: spells, main attack, casts every 5 s, damage factor, life, mana. */
    private static String parts(Damage.Result r) {
        if (r == null) {
            return "";
        }
        double[] f = r.factors();
        return String.format(Locale.ROOT, "spell %.0f main %.0f casts %.2f dmg %.0f life %.3f mana %.3f mp5 %.0f", r.spellDps(),
                r.mainDps(), r.casts(), f.length > 0 ? f[0] : 0, f.length > 1 ? f[1] : 0, f.length > 2 ? f[2] : 0,
                r.manaPer5());
    }

    /** The road map's set for the class, archetype and level (its own milestone at your level), once per run. */
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
        List<ItemData> items = new ArrayList<>();
        if (set != null) {
            set.values().forEach(pc -> {
                ItemData i = ReferenceBuilds.byName(pc.name());
                if (i != null) {
                    items.add(i);
                }
            });
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        pickMillis += ms;
        p = new Pick(set, eval, items, ms);
        picks.put(key, p);
        Fixtures.note("pick " + key + " in " + ms + " ms");
        return p;
    }

    // ---- the report ----

    /** BeamCache.modelVersion (package-private there): which scoring model the numbers are from. */
    private static String modelVersion() {
        try {
            java.lang.reflect.Method m = BeamCache.class.getDeclaredMethod("modelVersion");
            m.setAccessible(true);
            return String.valueOf(m.invoke(null));
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    private static final String[] COLUMNS = {"id", "post", "class", "class_from", "archetype", "archetype_note", "mythic", "crafted", "tags",
        "level", "status", "dropped", "tomes", "min_points", "fits_200", "ref_objective", "pick_objective", "ratio", "ref_dps",
        "pick_dps", "ref_ehp_elemental", "pick_ehp_elemental", "ref_regen", "ref_regen_factor", "pick_regen",
        "pick_regen_factor", "ref_life_factor", "pick_life_factor", "ref_major_ids", "pick_major_ids", "pick_set", "url",
        "ref_tree", "ref_plan_objective", "ratio_plan_tree", "ref_parts", "pick_parts"};

    private void write(JsonObject doc, List<Row> rows, long ms) throws Exception {
        StringBuilder csv = new StringBuilder(String.join(",", COLUMNS)).append('\n');
        for (Row r : rows) {
            Object[] v = {r.id(), r.post(), r.klass(), classFrom.getOrDefault(r.id(), ""), r.archetype(), r.archFrom(), r.mythic(), r.crafted(), r.tags(), r.level(),
                r.status(), r.dropped(), r.tomes(), r.need(), r.level() == 0 ? "" : r.need() <= ReferenceBuilds.CAP, r.refObj(),
                r.pickObj(), r.ratio(), r.refDps(), r.pickDps(), r.refEhp(), r.pickEhp(), r.refRegen(), r.refRegenF(),
                r.pickRegen(), r.pickRegenF(), r.refLife(), r.pickLife(), r.refMajor(), r.pickMajor(), r.pickSet(), r.url(),
                r.refTree(), r.refPlanObj(), r.planRatio(), r.refParts(), r.pickParts()};
            List<String> cells = new ArrayList<>();
            for (Object o : v) {
                cells.add(ReferenceBuilds.csv(o));
            }
            csv.append(String.join(",", cells)).append('\n');
        }
        Files.writeString(ReferenceBuilds.out("guide.csv"), csv, StandardCharsets.UTF_8);

        StringBuilder md = new StringBuilder("# Reference builds: the community guide against the road map's pick\n\n");
        md.append("Source: ").append(doc.get("source").getAsString()).append(" (").append(rows.size())
                .append(" links). Model version ").append(modelVersion()).append(". Run time ").append(ms / 1000)
                .append(" s, of which road map picks ").append(pickMillis / 1000).append(" s for ").append(picks.size())
                .append(" class/archetype/level combinations (the tests' narrow search: jointPool 6, beamWidth 12, beamFinal 4).\n\n");
        md.append("ratio = the guide build's objective / the pick's objective, both scored by `Build.allocateBest` at the link's level with")
                .append(" the Damage goal; the guide build with its link's own ability tree where the link has one (")
                .append(rows.stream().filter(r -> "own".equals(r.refTree())).count())
                .append(" rows), else the archetype's plan; the pick with the archetype's plan. The plan-tree ratio scores the guide")
                .append(" build with the plan too. Calibration rows: guide entries (not replies) that decode")
                .append(" whole (no crafted, custom or unknown piece) and score.\n\n");
        Map<String, Integer> status = new TreeMap<>();
        rows.forEach(r -> status.merge(r.status().replaceAll(" \\(scored with \\d+\\)", ""), 1, Integer::sum));
        md.append("## Decoding and fitting\n\n| status | links |\n|---|---|\n");
        status.forEach((s, n) -> md.append("| ").append(s).append(" | ").append(n).append(" |\n"));
        Map<String, Integer> drops = new TreeMap<>();
        for (Row r : rows) {
            for (String d : r.dropped().split(" ")) {
                if (d.startsWith("tomes:") || d.startsWith("newer-data-version:")) {
                    drops.merge(d.substring(0, d.indexOf(':')), 1, Integer::sum);
                } else if (!d.isEmpty()) {
                    drops.merge(d.replaceAll("^\\w+:", "").replaceAll("\\(.*\\)", ""), 1, Integer::sum);
                }
            }
        }
        md.append("\nPieces and extras left out: ").append(drops).append(". Over WynnBuilder's 200 points (tomes not counted): ")
                .append(rows.stream().filter(r -> r.level() > 0 && r.need() > ReferenceBuilds.CAP).count()).append(".\n\n");

        List<Row> calib = rows.stream().filter(r -> r.scored() && r.complete() && "guide".equals(r.post())).toList();
        md.append("## Aggregate (calibration rows)\n\n");
        table(md, calib);
        md.append("\n## Aggregate (every scored row, partial and reply builds included)\n\n");
        table(md, rows.stream().filter(Row::scored).toList());
        md.append("\n## Splits (calibration rows)\n\n");
        md.append("| rows | n | median ratio | plan-tree ratio | within 10 % or above | median ref/pick DPS | median ref/pick eEHP | worst |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        line(md, "guide build has a major ID", calib.stream().filter(r -> !r.refMajor().isEmpty()).toList());
        line(md, "no major ID", calib.stream().filter(r -> r.refMajor().isEmpty()).toList());
        line(md, "guide build's regen below 0 (regen factor < 1)", calib.stream().filter(r -> r.refRegenF() < 1).toList());
        line(md, "regen 0 or more", calib.stream().filter(r -> r.refRegenF() >= 1).toList());
        line(md, "guide build needs over 200 points (tomes)", calib.stream().filter(r -> r.need() > ReferenceBuilds.CAP).toList());
        md.append("\n## Worst 10 (calibration rows, lowest ratio)\n\n");
        md.append("| id | class | archetype | mythic | Lv | ratio | ref DPS | pick DPS | ref eEHP | pick eEHP | ref regen f | major IDs |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        calib.stream().sorted(Comparator.comparingDouble(Row::ratio)).limit(10).forEach(r -> md.append(String.format(Locale.ROOT,
                "| %d | %s | %s | %s | %d | %.3f | %.0f | %.0f | %.0f | %.0f | %.3f | %s |%n", r.id(), r.klass(), r.archetype(),
                r.mythic(), r.level(), r.ratio(), r.refDps(), r.pickDps(), r.refEhp(), r.pickEhp(), r.refRegenF(), r.refMajor())));
        md.append("\n## Best 5 (calibration rows, highest ratio)\n\n| id | class | archetype | mythic | Lv | ratio |\n|---|---|---|---|---|---|\n");
        calib.stream().sorted(Comparator.comparingDouble(Row::ratio).reversed()).limit(5).forEach(r -> md.append(String.format(
                Locale.ROOT, "| %d | %s | %s | %s | %d | %.3f |%n", r.id(), r.klass(), r.archetype(), r.mythic(), r.level(), r.ratio())));
        Files.writeString(ReferenceBuilds.out("report.md"), md, StandardCharsets.UTF_8);
        Fixtures.note("reference builds: " + ReferenceBuilds.out("report.md"));
    }

    private static void table(StringBuilder md, List<Row> rows) {
        Map<String, List<Row>> by = new TreeMap<>();
        rows.forEach(r -> by.computeIfAbsent(r.klass() + " " + (r.archetype().isEmpty() ? "(none)" : r.archetype()),
                x -> new ArrayList<>()).add(r));
        md.append("| archetype | n | median ratio | plan-tree ratio | within 10 % or above | median ref/pick DPS | median ref/pick eEHP | worst |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        by.forEach((a, list) -> line(md, a, list));
        line(md, "**overall**", rows);
    }

    private static void line(StringBuilder md, String name, List<Row> list) {
        List<Double> ratios = list.stream().map(Row::ratio).toList();
        long ok = ratios.stream().filter(v -> v >= 0.9).count();
        List<Double> dps = list.stream().filter(r -> r.pickDps() > 0).map(r -> r.refDps() / r.pickDps()).toList();
        List<Double> ehp = list.stream().filter(r -> r.pickEhp() > 0).map(r -> r.refEhp() / r.pickEhp()).toList();
        Row worst = list.stream().min(Comparator.comparingDouble(Row::ratio)).orElse(null);
        List<Double> plan = list.stream().map(Row::planRatio).filter(v -> !Double.isNaN(v)).toList();
        md.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.0f %% | %.2f | %.2f | %s |%n", name, list.size(),
                ReferenceBuilds.median(ratios), ReferenceBuilds.median(plan), list.isEmpty() ? 0 : 100.0 * ok / list.size(), ReferenceBuilds.median(dps),
                ReferenceBuilds.median(ehp), worst == null ? "" : "#" + worst.id() + " " + worst.mythic() + " " + String.format(
                        Locale.ROOT, "%.3f", worst.ratio())));
    }
}
