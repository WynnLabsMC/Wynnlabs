package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cursed builds thread's links (reference-builds/cursed.json): deliberately odd builds, a
 * robustness check only, never part of the calibration. Every link is decoded (failures counted
 * by reason), its skill points worked out against WynnBuilder's 200 and the set scored without a
 * throw, NaN or infinity. Where a post's template gives the author's own EHP or damage number and
 * the post has one link, the mod's number is set beside it. Opt-in (-PreferenceBuilds); writes
 * build/reference-builds/cursed.md and cursed.csv.
 */
class CursedBuildsCheck {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    record Compare(String post, String credit, String what, double author, double mod, String note, String text) {
        double ratio() {
            return mod / author;
        }
    }

    private final Map<String, Integer> counts = new TreeMap<>();
    private final List<Compare> compares = new ArrayList<>();
    private final List<String> csv = new ArrayList<>();
    private final List<String> broken = new ArrayList<>();

    private void count(String what) {
        counts.merge(what, 1, Integer::sum);
    }

    @Test
    @EnabledIfSystemProperty(named = ReferenceBuilds.PROPERTY, matches = "true")
    void cursedBuildsDecodeFitAndScore() throws Exception {
        long t0 = System.nanoTime();
        JsonObject doc = ReferenceBuilds.resource("cursed.json");
        Set<String> seen = new LinkedHashSet<>();
        csv.add("post,credit,url,result,class,archetype,level,min_points,fits_200,objective,ehp,ehp_elemental,dps,dropped");
        for (JsonElement el : doc.getAsJsonArray("posts")) {
            JsonObject p = el.getAsJsonObject();
            List<String> links = new ArrayList<>();
            p.getAsJsonArray("links").forEach(l -> links.add(l.getAsString()));
            JsonObject fields = p.has("fields") ? p.getAsJsonObject("fields") : new JsonObject();
            for (String url : links) {
                if (!seen.add(url)) {
                    continue;
                }
                try {
                    check(p, url, fields, links.size() == 1);
                } catch (Throwable t) {
                    count("score: threw " + t.getClass().getSimpleName());
                    broken.add(url + " " + t);
                } finally {
                    FixtureState.uninstall();
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        write(doc, seen.size(), ms);
        assertTrue(broken.isEmpty(), "threw or gave NaN/infinity: " + broken);
    }

    private void check(JsonObject p, String url, JsonObject fields, boolean only) throws Exception {
        String post = p.get("post").getAsString();
        String credit = p.get("credit").getAsString();
        WynnBuilderLink.Build b = WynnBuilderLink.decode(url);
        if (b == null) {
            String why = ReferenceBuildsBenchmark.decodeFailure(url);
            if (why.endsWith("malformed")) {
                Matcher m = Pattern.compile("#(\\d+)_").matcher(url);
                why += m.find() ? " (old form, version " + m.group(1) + ")" : url.contains("#C") ? " (current form)" : "";
            }
            count(why);
            csv.add(String.join(",", ReferenceBuilds.csv(post), ReferenceBuilds.csv(credit), ReferenceBuilds.csv(url), why));
            return;
        }
        count("decoded");
        if (b.newer()) {
            count("decode: newer data version than the mod's table (read with its newest widths)");
        }
        if (b.pieces().stream().anyMatch(x -> x.kind() == WynnBuilderLink.Kind.ITEM && x.name() == null)) {
            count("decode: unknown item id");
        }
        if (b.pieces().stream().anyMatch(x -> x.kind() == WynnBuilderLink.Kind.CRAFTED)) {
            count("pieces: crafted left out");
        }
        if (b.pieces().stream().anyMatch(x -> x.kind() == WynnBuilderLink.Kind.CUSTOM)) {
            count("pieces: custom left out");
        }
        if (!b.tomes().isEmpty()) {
            count("pieces: tomes left out");
        }
        ReferenceBuilds.installPlain();
        Klass k = ReferenceBuilds.classOf(b);
        if (k == null) {
            count("no class (crafted, custom or unknown weapon)");
            csv.add(String.join(",", ReferenceBuilds.csv(post), ReferenceBuilds.csv(credit), ReferenceBuilds.csv(url), "no class"));
            return;
        }
        String arch = archetypeIn(fields.has("Archetype(s)") ? fields.get("Archetype(s)").getAsString() : "", k);
        int level = Math.max(1, b.level());
        ReferenceBuilds.install(k, level, arch);
        ReferenceBuilds.LinkSet ls = ReferenceBuilds.set(b);
        int need = ReferenceBuilds.need(ls.set().values());
        if (need > ReferenceBuilds.CAP) {
            count("skill points: over 200 (needs tomes or does not fit)");
        }
        if (!ls.hasWeapon()) {
            count("score: no weapon left to score");
            return;
        }
        int earned = Math.max(ReferenceBuilds.earned(level), need);
        Build.Eval e = ReferenceBuilds.score(ls.set(), level, earned);
        if (e == null) {
            count("score: does not fit, a skill needs over 100 assigned");
            return;
        }
        Damage.Result r = e.result();
        if (!Double.isFinite(r.objective()) || !Double.isFinite(r.ehp()) || !Double.isFinite(r.spellDps() + r.mainDps())) {
            count("score: NaN or infinity");
            broken.add(url + " objective " + r.objective() + " ehp " + r.ehp());
            return;
        }
        count("score: finite");
        csv.add(String.join(",", ReferenceBuilds.csv(post), ReferenceBuilds.csv(credit), ReferenceBuilds.csv(url), "scored",
                k.name(), ReferenceBuilds.csv(arch), String.valueOf(level), String.valueOf(need),
                String.valueOf(need <= ReferenceBuilds.CAP), ReferenceBuilds.csv(r.objective()), ReferenceBuilds.csv(r.ehp()),
                ReferenceBuilds.csv(r.ehpElemental()), ReferenceBuilds.csv(r.spellDps() + r.mainDps()),
                ReferenceBuilds.csv(String.join(" ", ls.dropped()))));
        if (only) {
            compare(post, credit, fields, ls, e, level, b);
        }
    }

    /** The first of the class's archetypes the text names ("Lightbender" for Light Bender); "" for none. */
    static String archetypeIn(String text, Klass k) throws Exception {
        String t = text.toLowerCase(Locale.ROOT).replace(" ", "");
        String best = "";
        int at = Integer.MAX_VALUE;
        for (String a : ReferenceBuilds.archetypes(k)) {
            int i = t.indexOf(a.toLowerCase(Locale.ROOT).replace(" ", ""));
            if (i >= 0 && i < at) {
                best = a;
                at = i;
            }
        }
        return best;
    }

    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(k|m)?\\b", Pattern.CASE_INSENSITIVE);

    /** The first number in the text ("44.4k" 44400, "43559.75"), NaN for none. */
    static double number(String text) {
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            double v = Double.parseDouble(m.group(1).replace(",", m.group(1).matches("\\d{1,3},\\d{3}") ? "" : "."));
            String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
            v *= unit.equals("k") ? 1e3 : unit.equals("m") ? 1e6 : 1;
            if (v >= 100) {
                return v;           // "1 cosmic", "20 winded" are no EHP or damage
            }
        }
        return Double.NaN;
    }

    private static final Pattern BEFORE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(k|m)?\\s*(?:[a-z]+\\s+)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * The author's number for the source named at {@code at}: the one just before the name ("20k
     * bashes"), else the first one after it ("Multihit: 28k"), never one in brackets after it
     * ("(78k with buffs)"). NaN for none.
     */
    static double near(String text, int at, int len) {
        Matcher m = BEFORE.matcher(text.substring(Math.max(0, at - 14), at));
        if (m.find()) {
            double v = number(m.group());
            if (!Double.isNaN(v)) {
                return v;
            }
        }
        String after = text.substring(Math.min(text.length(), at + len));
        int bracket = after.indexOf('(');
        int stop = Math.min(after.length(), 30);
        if (bracket >= 0 && bracket < stop) {
            stop = bracket;
        }
        return number(after.substring(0, stop));
    }

    /** What may explain a gap: what the post or the link adds that the mod leaves out. */
    private static String causes(String text, WynnBuilderLink.Build b, ReferenceBuilds.LinkSet ls, JsonObject fields) {
        List<String> c = new ArrayList<>();
        String t = text.toLowerCase(Locale.ROOT);
        if (t.matches(".*\\b(with|buff|buffs|scream|winded|bloom|aura|totem|boost|corrupted|stacks?)\\b.*")) {
            c.add("buffs named");
        }
        String tomes = fields.has("Tomes") ? fields.get("Tomes").getAsString().toLowerCase(Locale.ROOT) : "";
        if (!b.tomes().isEmpty() || tomes.startsWith("y")) {
            c.add("tomes");
        }
        if (!ls.dropped().isEmpty()) {
            c.add("left out: " + String.join(" ", ls.dropped()));
        }
        if (!b.version().startsWith("2")) {
            c.add("old link (data " + b.version() + "): items changed since");
        }
        return String.join("; ", c);
    }

    private void compare(String post, String credit, JsonObject fields, ReferenceBuilds.LinkSet ls, Build.Eval e, int level,
            WynnBuilderLink.Build b) {
        if (fields.has("EHP")) {
            String text = fields.get("EHP").getAsString();
            double author = number(text);
            if (!Double.isNaN(author)) {
                compares.add(new Compare(post, credit, "EHP", author, e.result().ehp(), causes(text, b, ls, fields), text));
            }
        }
        if (fields.has("Primary Damage")) {
            String text = fields.get("Primary Damage").getAsString();
            String lower = text.toLowerCase(Locale.ROOT);
            Damage.Detail d = ReferenceBuilds.detail(ls.set(), e.allocation(), level);
            for (Sources.Source s : d.breakdown().sources()) {
                String name = s.kind().equals("main") ? "melee" : s.name().toLowerCase(Locale.ROOT);
                int at = lower.indexOf(name);
                if (at < 0 && s.kind().equals("main")) {
                    at = lower.indexOf("main attack");
                }
                if (at < 0) {
                    continue;
                }
                double author = near(text, at, name.length());
                double mod = s.perCast() > 0 ? s.perCast() : s.perHit();
                boolean dps = lower.substring(at).matches("^[^,;]*\\bdps\\b.*");
                if (dps) {
                    mod = s.dps();
                }
                if (!Double.isNaN(author) && mod > 0) {
                    compares.add(new Compare(post, credit, "damage: " + s.name() + (dps ? " DPS" : ""), author, mod,
                            causes(text, b, ls, fields), text));
                    break;
                }
            }
        }
    }

    private void write(JsonObject doc, int links, long ms) throws Exception {
        Files.writeString(ReferenceBuilds.out("cursed.csv"), String.join("\n", csv) + "\n", StandardCharsets.UTF_8);
        StringBuilder md = new StringBuilder("# Cursed builds: robustness check\n\n");
        md.append("Source: ").append(doc.get("source").getAsString()).append(". ").append(links)
                .append(" unique links, run time ").append(ms / 1000).append(" s. Not part of any calibration aggregate.\n\n");
        md.append("| result | links |\n|---|---|\n");
        counts.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        if (!broken.isEmpty()) {
            md.append("\nThrew or not finite:\n\n");
            broken.forEach(x -> md.append("- ").append(x).append('\n'));
        }
        Map<String, List<Compare>> by = new LinkedHashMap<>();
        compares.forEach(c -> by.computeIfAbsent(c.what().startsWith("EHP") ? "EHP" : "damage", x -> new ArrayList<>()).add(c));
        md.append("\n## The authors' numbers against the mod's (one-link template posts)\n\n");
        md.append("ratio = mod / author. EHP: the mod's WynnBuilder-style EHP (Defence and Agility, no buffs, no tomes);")
                .append(" damage: the named source's per-cast (spell) or per-hit (melee) number, or its DPS when the post says DPS.\n\n");
        for (Map.Entry<String, List<Compare>> en : by.entrySet()) {
            List<Double> errs = en.getValue().stream().map(c -> Math.abs(Math.log(c.ratio()))).toList();
            List<Double> ratios = en.getValue().stream().map(Compare::ratio).toList();
            long within = ratios.stream().filter(v -> v >= 0.9 && v <= 1.1).count();
            md.append(String.format(Locale.ROOT, "### %s: %d posts, median ratio %.3f, median error %.0f %%, within 10 %%: %d%n%n",
                    en.getKey(), en.getValue().size(), ReferenceBuilds.median(ratios),
                    100 * (Math.exp(ReferenceBuilds.median(errs)) - 1), within));
            md.append("| post | by | what | author | mod | ratio | likely cause | author's text |\n|---|---|---|---|---|---|---|---|\n");
            en.getValue().stream().sorted(Comparator.comparingDouble(c -> -Math.abs(Math.log(c.ratio())))).limit(10)
                    .forEach(c -> md.append(String.format(Locale.ROOT, "| %s | %s | %s | %.0f | %.0f | %.2f | %s | %s |%n",
                            c.post(), c.credit(), c.what(), c.author(), c.mod(), c.ratio(), c.note(),
                            c.text().replace("|", "/").replaceAll("\\s+", " "))));
            md.append('\n');
        }
        Files.writeString(ReferenceBuilds.out("cursed.md"), md, StandardCharsets.UTF_8);
        Fixtures.note("cursed builds: " + ReferenceBuilds.out("cursed.md") + " " + counts);
    }
}
