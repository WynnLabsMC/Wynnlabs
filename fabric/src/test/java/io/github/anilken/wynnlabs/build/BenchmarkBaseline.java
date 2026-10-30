package io.github.anilken.wynnlabs.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.game.Fixtures;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The reference benchmarks' committed baseline (reference-builds/baseline.json): per benchmark
 * the key aggregates of the last approved run. A run fails when a metric falls further below its
 * baseline than its tolerance (0.03 for ratios, medians and percentiles; 3 points for rates in
 * per cent). Every gated metric is higher-is-better. The file changes only with -PupdateFixtures,
 * which rewrites the benchmark's section and prints each change. Only aggregate numbers go in
 * (the Discord section holds no links and no names). A narrowed run (levels, goals, the wide
 * search, a model override) is neither checked nor written.
 */
final class BenchmarkBaseline {

    /** A group smaller than this is not kept: one build flipping would move it past any tolerance. */
    static final int MIN_N = 5;

    enum Kind {
        RATIO(0.03), RATE(3.0);

        final double tol;

        Kind(double tol) {
            this.tol = tol;
        }
    }

    record Metric(Kind kind, double value, int n) {}

    /** One run's metrics, by key ("overall.median_ratio", "archetype.MAGE Riftwalker.percentile"). */
    static final class Metrics {
        final Map<String, Metric> map = new TreeMap<>();

        /** Kept when the group has at least {@link #MIN_N} rows and the value is a number. */
        Metrics put(String key, Kind kind, double value, int n) {
            return put(key, kind, value, n, MIN_N);
        }

        /** {@link #put} with its own smallest group ({@code min}): the pairs players compare are few but each one counts. */
        Metrics put(String key, Kind kind, double value, int n, int min) {
            if (n >= min && n > 0 && !Double.isNaN(value) && !Double.isInfinite(value)) {
                map.put(key, new Metric(kind, Math.round(value * 10000) / 10000.0, n));
            }
            return this;
        }
    }

    private BenchmarkBaseline() {}

    static Path file() {
        return Fixtures.root().resolveSibling("reference-builds").resolve("baseline.json");
    }

    static boolean update() {
        return Boolean.getBoolean("wynnlabs.updateFixtures");
    }

    /**
     * Compares {@code now} with the baseline's {@code bench} section (or writes it with
     * -PupdateFixtures), appends a "Baseline" section to {@code md}, and returns the regressions
     * (empty when none, or when {@code skip} names why the run is not compared).
     */
    static List<String> check(String bench, String model, Metrics now, StringBuilder md, String skip) throws IOException {
        md.append("\n## Baseline\n\n");
        if (skip != null) {
            md.append("Not compared: ").append(skip).append(".\n");
            Fixtures.note("baseline " + bench + ": not compared (" + skip + ")");
            return List.of();
        }
        Path lock = ReferenceBuilds.out("baseline.lock");
        try (FileChannel ch = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock l = ch.lock()) {
            JsonObject doc = Files.exists(file())
                    ? JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
            Map<String, Metric> before = read(doc, bench);
            List<String> regressions = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, Metric> e : now.map.entrySet()) {
                Metric b = before.get(e.getKey());
                Metric m = e.getValue();
                if (b == null) {
                    lines.add(row(e.getKey(), null, m, "new"));
                    continue;
                }
                double d = m.value() - b.value();
                String verdict = d < -b.kind().tol - 1e-9 ? "**regression**" : Math.abs(d) > 1e-9 ? "changed" : "";
                if (verdict.startsWith("**")) {
                    regressions.add(String.format(Locale.ROOT, "%s %s: %.4f -> %.4f (tolerance %.2f)", bench, e.getKey(),
                            b.value(), m.value(), b.kind().tol));
                }
                if (!verdict.isEmpty() || b.n() != m.n()) {
                    lines.add(row(e.getKey(), b, m, verdict + (b.n() != m.n() ? " (n " + b.n() + " -> " + m.n() + ")" : "")));
                }
            }
            before.forEach((k, b) -> {
                if (!now.map.containsKey(k)) {
                    lines.add(row(k, b, null, "gone"));
                }
            });
            md.append(before.isEmpty() ? "No baseline yet for this benchmark" : "Against the committed baseline")
                    .append(" (reference-builds/baseline.json; tolerance 0.03 for ratios, medians and percentiles, 3 points for")
                    .append(" rates; groups under ").append(MIN_N).append(" rows are not kept). ").append(now.map.size())
                    .append(" metrics, ").append(lines.size()).append(" differ, ").append(regressions.size())
                    .append(" regressions.\n\n");
            if (!lines.isEmpty()) {
                md.append("| metric | baseline | now | change |\n|---|---|---|---|\n");
                lines.forEach(md::append);
            }
            if (update()) {
                write(doc, bench, model, now);
                lines.forEach(x -> Fixtures.note("baseline " + bench + " " + x.replace("|", " ").replaceAll("\\s+", " ").trim()));
                Fixtures.note("baseline " + bench + ": written to " + file() + " (" + lines.size() + " changed)");
                return List.of();
            }
            if (before.isEmpty()) {
                Fixtures.note("baseline " + bench + ": none yet, run with -PupdateFixtures to write it");
            }
            return regressions;
        }
    }

    private static String row(String key, Metric b, Metric m, String verdict) {
        return String.format(Locale.ROOT, "| %s | %s | %s | %s |%n", key, b == null ? "-" : fmt(b), m == null ? "-" : fmt(m), verdict);
    }

    private static String fmt(Metric m) {
        return String.format(Locale.ROOT, m.kind() == Kind.RATE ? "%.1f (n %d)" : "%.4f (n %d)", m.value(), m.n());
    }

    private static Map<String, Metric> read(JsonObject doc, String bench) {
        Map<String, Metric> out = new TreeMap<>();
        if (!doc.has(bench)) {
            return out;
        }
        JsonObject ms = doc.getAsJsonObject(bench).getAsJsonObject("metrics");
        for (Map.Entry<String, JsonElement> e : ms.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            out.put(e.getKey(), new Metric(Kind.valueOf(o.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                    o.get("value").getAsDouble(), o.get("n").getAsInt()));
        }
        return out;
    }

    private static void write(JsonObject doc, String bench, String model, Metrics now) throws IOException {
        JsonObject out = new JsonObject();
        out.addProperty("note", "Reference benchmark baseline (-PreferenceBuilds): aggregate numbers only, rewritten with"
                + " -PupdateFixtures. A run fails when a metric drops below its value by more than 0.03 (ratio kind) or"
                + " 3 points (rate kind, per cent).");
        Map<String, JsonElement> sections = new TreeMap<>();
        doc.entrySet().forEach(e -> {
            if (!e.getKey().equals("note")) {
                sections.put(e.getKey(), e.getValue());
            }
        });
        JsonObject sec = new JsonObject();
        sec.addProperty("modelVersion", model);
        JsonObject ms = new JsonObject();
        now.map.forEach((k, m) -> {
            JsonObject o = new JsonObject();
            o.addProperty("kind", m.kind().name().toLowerCase(Locale.ROOT));
            o.addProperty("value", m.value());
            o.addProperty("n", m.n());
            ms.add(k, o);
        });
        sec.add("metrics", ms);
        sections.put(bench, sec);
        sections.forEach(out::add);
        Gson g = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.writeString(file(), g.toJson(out) + "\n", StandardCharsets.UTF_8);
    }

    /** {@link Kind#RATE} of {@code hits} among {@code n}, in per cent. */
    static double pct(long hits, int n) {
        return n == 0 ? Double.NaN : 100.0 * hits / n;
    }

    static double mean(List<Double> v) {
        List<Double> x = v.stream().filter(d -> !Double.isNaN(d)).toList();
        return x.isEmpty() ? Double.NaN : x.stream().mapToDouble(Double::doubleValue).sum() / x.size();
    }
}
