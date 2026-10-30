package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.build.roadmap.BeamCache;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.build.roadmap.RoadmapTap;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The community leveling list (reference-builds/leveling.json, from the forum thread "Meta gear
 * for leveling") against the road map's picks for a fresh character: per class and level bracket,
 * the shaman-44 fixture played as the class with nothing owned, the thread's meta archetype chosen,
 * the road map worked out for that level alone, and per slot whether a listed item is the pick or
 * among the slot's top three options. A miss is scored by swapping the listed item into the pick's
 * set; whole listed builds (the xp, morph and non-morph tracks) are scored against the pick too.
 * The listed items' sources are checked against the item data's. Opt-in (-PreferenceBuilds);
 * writes build/reference-builds/leveling-report.md, leveling.csv and leveling-sources.csv.
 * Levels and goals can be narrowed with -Dwynnlabs.levelingLevels / wynnlabs.levelingGoals.
 */
class LevelingMetaBenchmark {

    private static final Klass[] CLASSES = {Klass.MAGE, Klass.ARCHER, Klass.WARRIOR, Klass.ASSASSIN, Klass.SHAMAN};
    /** The tracks the thread's spell archetypes level with; the melee track is scored as a whole set only. */
    private static final List<String> SPELL_TRACKS = List.of("xp power leveling", "non-morph", "morph");
    private static final String[] ARMOUR = {"helmet", "chestplate", "leggings", "boots", "ring", "bracelet", "necklace"};
    private static final String[] ORDER = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "RING1", "RING2", "BRACELET", "NECKLACE"};

    @AfterEach
    void reset() {
        RoadmapTap.trace(null);
        FixtureState.uninstall();
        BeamCache.clear();
    }

    /** One listed item the thread offers for a slot at a level. */
    record Meta(String item, String slot, int level, String track, String build, String kind, List<String> tags,
            List<String> notes, String source) {}

    /** One class/goal/level/slot comparison. */
    record Row(String klass, String arch, String goal, int level, String slot, String metas, String pick, String top3,
            boolean hit, boolean inTop3, String bestMeta, double metaObj, double pickObj, double metaXp, double pickXp,
            String causes, int rank, int cands) {

        double ratio() {
            return metaObj > 0 && pickObj > 0 ? metaObj / pickObj : Double.NaN;
        }

        /** 1 / the listed item's rank among the slot's candidates (0 when not ranked). */
        double reciprocal() {
            return rank > 0 ? 1.0 / rank : 0;
        }
    }

    /** How many of a slot's other options (best gain first) the listed item is ranked against, besides the pick. */
    private static final int OPTIONS = 5;

    /** A whole listed build against the pick. */
    record SetRow(String klass, String goal, int level, String track, String build, double refObj, double pickObj,
            double refDps, double pickDps, double refEhp, double pickEhp, double refXp, double pickXp, String note) {

        double ratio() {
            return refObj > 0 && pickObj > 0 ? refObj / pickObj : Double.NaN;
        }
    }

    private final Map<String, ItemData> db = new HashMap<>();
    private final Map<Klass, FixtureState> states = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<SetRow> setRows = new ArrayList<>();
    private final List<String[]> sources = new ArrayList<>();
    private final Map<String, String> archetypeOf = new LinkedHashMap<>();
    private long roadmapMillis;
    private int roadmaps;

    @Test
    @EnabledIfSystemProperty(named = ReferenceBuilds.PROPERTY, matches = "true")
    void levelingPicksAgainstTheCommunityList() throws Exception {
        long t0 = System.nanoTime();
        JsonObject doc = ReferenceBuilds.resource("leveling.json");
        ReferenceBuilds.installPlain();
        GameState.current().allGear().forEach(i -> db.putIfAbsent(i.name(), i));
        List<Integer> levels = ints(System.getProperty("wynnlabs.levelingLevels",
                "10,15,20,25,30,35,40,45,50,55,60,65,70,75,80,85,90,95,100,105"));
        List<String> goals = List.of(System.getProperty("wynnlabs.levelingGoals", "DAMAGE,XP").split(","));
        checkSources(doc);
        FixtureState.uninstall();
        for (Klass k : CLASSES) {
            archetypeOf.put(k.name(), archetype(k, doc));
        }
        for (String goal : goals) {
            for (Klass k : CLASSES) {
                for (int level : levels) {
                    try {
                        compare(doc, k, archetypeOf.get(k.name()), goal, level);
                    } catch (Throwable t) {
                        Fixtures.note("leveling " + k + " " + goal + " Lv " + level + ": " + t);
                        rows.add(new Row(k.name(), archetypeOf.get(k.name()), goal, level, "-", "", "", "", false, false, "",
                                Double.NaN, Double.NaN, Double.NaN, Double.NaN, "error: " + t.getClass().getSimpleName()
                                + " " + t.getMessage(), 0, 0));
                    } finally {
                        RoadmapTap.trace(null);
                        FixtureState.uninstall();
                    }
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        List<String> regressions = write(doc, levels, goals, ms);
        assertTrue(rows.stream().anyMatch(r -> !Double.isNaN(r.pickObj())), "no road map scored");
        assertTrue(regressions.isEmpty(), "below the baseline (an intended change? approve it with -PupdateFixtures):\n"
                + String.join("\n", regressions));
    }

    static List<Integer> ints(String s) {
        List<Integer> out = new ArrayList<>();
        for (String p : s.split(",")) {
            out.add(Integer.parseInt(p.trim()));
        }
        return out;
    }

    // ---- the list ----

    /** The tree archetype holding most of the nodes the thread lists for the class (Assassin's "Hybrid" becomes one of its parts). */
    private static String archetype(Klass k, JsonObject doc) throws Exception {
        JsonObject a = doc.getAsJsonObject("archetypes").getAsJsonObject(k.name());
        if (a == null) {
            return "";
        }
        List<String> trees = ReferenceBuilds.archetypes(k);
        String named = a.get("archetype").getAsString();
        if (trees.contains(named)) {
            return named;
        }
        Set<String> nodes = new java.util.HashSet<>();
        a.getAsJsonArray("nodes").forEach(n -> nodes.add(n.getAsString().toLowerCase(Locale.ROOT)));
        Map<String, Integer> count = new TreeMap<>();
        for (AbilityNode n : ReferenceBuilds.tree(k)) {
            if (n.archetype() != null && nodes.contains(n.name().toLowerCase(Locale.ROOT))) {
                count.merge(n.archetype(), 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> ranked = count.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).toList();
        if (ranked.isEmpty()) {
            return "";
        }
        // a tie ("Hybrid": Shadestepper and Trickster nodes alike) is played as the two combined
        if (ranked.size() > 1 && ranked.get(1).getValue().equals(ranked.get(0).getValue())) {
            return ranked.get(0).getKey() + "+" + ranked.get(1).getKey();
        }
        return ranked.get(0).getKey();
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static List<String> strs(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (o.has(k) && o.get(k).isJsonArray()) {
            o.getAsJsonArray(k).forEach(e -> {
                if (!e.isJsonNull()) {
                    out.add(e.getAsString());
                }
            });
        }
        return out;
    }

    /** The class's listed weapons for a bracket: those of the last ten levels, else the newest below it. */
    private List<Meta> weapons(JsonObject doc, Klass k, int level) {
        List<Meta> all = new ArrayList<>();
        for (JsonElement el : doc.getAsJsonArray("weapons")) {
            JsonObject w = el.getAsJsonObject();
            String item = str(w, "item");
            if (!k.name().equals(str(w, "class")) || item == null || w.get("fruma").getAsBoolean()) {
                continue;
            }
            ItemData info = db.get(item);
            int lv = w.get("level").getAsInt();
            if (info == null || info.level() > level || lv > level) {
                continue;
            }
            JsonObject acq = w.getAsJsonObject("acquisition");
            all.add(new Meta(item, "weapon", lv, "weapons", "", "weapon", strs(w, "tags"), strs(w, "notes"), str(acq, "source")));
        }
        List<Meta> recent = all.stream().filter(m -> m.level() > level - 10).toList();
        if (!recent.isEmpty()) {
            return recent;
        }
        int top = all.stream().mapToInt(Meta::level).max().orElse(-1);
        return all.stream().filter(m -> m.level() == top).toList();
    }

    /** The builds a track has at {@code level}: those at its newest build level not above it. */
    private static List<JsonObject> activeBuilds(JsonObject doc, String track, int level) {
        int best = -1;
        for (JsonElement el : doc.getAsJsonArray("builds")) {
            JsonObject b = el.getAsJsonObject();
            if (track.equals(str(b, "track")) && b.get("level").getAsInt() <= level) {
                best = Math.max(best, b.get("level").getAsInt());
            }
        }
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement el : doc.getAsJsonArray("builds")) {
            JsonObject b = el.getAsJsonObject();
            if (track.equals(str(b, "track")) && b.get("level").getAsInt() == best) {
                out.add(b);
            }
        }
        return out;
    }

    /** The listed armour and accessories for a slot at a level: the tracks' active builds and their upgrades and alternates so far. */
    private List<Meta> armour(JsonObject doc, String slot, int level, List<String> tracks) {
        List<Meta> out = new ArrayList<>();
        for (String track : tracks) {
            Set<String> labels = new LinkedHashSet<>();
            activeBuilds(doc, track, level).forEach(b -> labels.add(str(b, "label")));
            for (JsonElement el : doc.getAsJsonArray("armour")) {
                JsonObject a = el.getAsJsonObject();
                String item = str(a, "item");
                if (item == null || !track.equals(str(a, "track")) || !labels.contains(str(a, "build"))
                        || !slot.equals(str(a, "slot")) || a.get("level").getAsInt() > level) {
                    continue;
                }
                ItemData info = db.get(item);
                if (info == null || info.level() > level) {
                    continue;
                }
                JsonObject acq = a.getAsJsonObject("acquisition");
                out.add(new Meta(item, slot, a.get("level").getAsInt(), track, str(a, "build"), str(a, "kind"), strs(a, "tags"),
                        strs(a, "notes"), str(acq, "source")));
            }
        }
        return out;
    }

    // ---- the road map ----

    private void install(Klass k, int level, String arch, String goal) throws Exception {
        FixtureState s = states.get(k);
        if (s == null) {
            s = FixtureState.load(Fixtures.root().resolve("shaman-44")).as(k).withTree(ReferenceBuilds.tree(k), List.of());
            states.put(k, s);
        }
        s.fresh().atLevel(level).install("en");
        TreePlanner.choose(arch);
        Setup.setGoal(goal);
    }

    /** The build slots a listed slot fills. */
    private static List<String> buildSlots(String slot) {
        return switch (slot) {
            case "weapon" -> List.of("WEAPON");
            case "ring" -> List.of("RING1", "RING2");
            default -> List.of(slot.toUpperCase(Locale.ROOT));
        };
    }

    /** The road map rows ({@link Roadmap.Gear#slot}) a listed slot has. */
    private static List<String> gearRows(String slot, Klass k) {
        return switch (slot) {
            case "weapon" -> List.of(Slot.weaponOf(k).name());
            case "ring" -> List.of("RING", Roadmap.RING2);
            default -> List.of(slot.toUpperCase(Locale.ROOT));
        };
    }

    private void compare(JsonObject doc, Klass k, String arch, String goal, int level) throws Exception {
        install(k, level, arch, goal);
        Map<String, List<Meta>> metas = new LinkedHashMap<>();
        metas.put("weapon", weapons(doc, k, level));
        for (String s : ARMOUR) {
            metas.put(s, armour(doc, s, level, SPELL_TRACKS));
        }
        // why each listed item was skipped or how it scored, from the road map's trace at this level
        Map<String, List<String>> why = new HashMap<>();
        Set<String> watched = new java.util.HashSet<>();
        metas.values().forEach(l -> l.forEach(m -> watched.add(m.item())));
        RoadmapTap.trace((m, t, line) -> {
            if (m != level) {
                return;
            }
            int cut = line.indexOf(" Lv");
            String name = cut > 0 ? line.substring(0, cut) : "";
            if (watched.contains(name)) {
                synchronized (why) {
                    List<String> l = why.computeIfAbsent(name, x -> new ArrayList<>());
                    if (l.size() < 6 && !l.contains(line)) {
                        l.add(line);
                    }
                }
            }
        });
        long t0 = System.nanoTime();
        BeamCache.clear();
        Roadmap.Computed c = Roadmap.compute(new Roadmap.Inputs(List.of(), Tuning.Choice.PLAIN, true, 0), null);
        RoadmapTap.trace(null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        roadmapMillis += ms;
        roadmaps++;
        Roadmap.Step step = null;
        if (c != null) {
            for (Roadmap.Step s : c.steps()) {
                if (s.level() == level) {
                    step = s;
                }
            }
        }
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        if (step != null) {
            step.build().forEach((slot, p) -> {
                if (p != null) {
                    set.put(slot, p);
                }
            });
        }
        int earned = ReferenceBuilds.earned(level);
        Build.Eval pickEval = set.containsKey("WEAPON") ? ReferenceBuilds.score(set, level, earned) : null;
        double pickObj = pickEval == null ? Double.NaN : pickEval.result().objective();
        Fixtures.note(String.format(Locale.ROOT, "leveling %s %s %s Lv %d: road map in %d ms, objective %.4g", k, arch, goal, level,
                ms, pickObj));
        for (Map.Entry<String, List<Meta>> e : metas.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            rows.add(slotRow(k, arch, goal, level, e.getKey(), e.getValue(), step, set, pickObj, earned, why));
        }
        // whole listed builds against the pick, keeping the pick's weapon
        if (pickEval != null) {
            for (String track : List.of("xp power leveling", "non-morph", "morph", "melee builds")) {
                for (JsonObject b : activeBuilds(doc, track, level)) {
                    setRows.add(wholeSet(doc, k, goal, level, track, b, set, pickEval, earned));
                }
            }
        }
    }

    private Row slotRow(Klass k, String arch, String goal, int level, String slot, List<Meta> metas, Roadmap.Step step,
            Map<String, Damage.Piece> set, double pickObj, int earned, Map<String, List<String>> why) {
        Set<String> names = new LinkedHashSet<>();
        metas.forEach(m -> names.add(m.item()));
        List<String> picked = new ArrayList<>();
        for (String bs : buildSlots(slot)) {
            if (set.get(bs) != null) {
                picked.add(set.get(bs).name());
            }
        }
        Set<String> top = new LinkedHashSet<>(picked);
        // the slot's candidates the listed item is ranked among: the pick and the slot's best other options
        Set<String> cands = new LinkedHashSet<>(picked);
        if (step != null) {
            for (Roadmap.Gear g : step.gear()) {
                if (!gearRows(slot, k).contains(g.slot())) {
                    continue;
                }
                top.add(g.info().name());
                cands.add(g.info().name());
                if (g.options() != null) {
                    List<String> ranked = g.options().stream().sorted(Comparator.comparingDouble((Roadmap.Option o) -> -o.gain()))
                            .map(o -> o.info().name()).filter(n -> !picked.contains(n)).toList();
                    ranked.stream().limit(2).forEach(top::add);
                    ranked.stream().limit(OPTIONS).forEach(cands::add);
                }
            }
        }
        boolean hit = picked.stream().anyMatch(names::contains);
        boolean inTop = hit || top.stream().anyMatch(names::contains);
        // the best listed item swapped into the pick's set (each ring slot tried)
        String best = "";
        double bestObj = Double.NaN;
        double bestXp = Double.NaN;
        List<String> causes = new ArrayList<>();
        Map<String, String> fit = new LinkedHashMap<>();
        if (!hit && !Double.isNaN(pickObj)) {
            for (String n : names) {
                double o = swap(set, slot, db.get(n), level, earned);
                fit.put(n, Double.isNaN(o) ? "no fit" : "ok");
                if (!Double.isNaN(o) && (Double.isNaN(bestObj) || o > bestObj)) {
                    bestObj = o;
                    best = n;
                }
            }
            if (best.isEmpty() && !names.isEmpty()) {
                best = names.iterator().next();
            }
            causes.addAll(causes(k, arch, level, slot, best, metas, set, fit, why, Double.isNaN(bestObj) ? Double.NaN : bestObj / pickObj));
        } else if (hit) {
            best = picked.stream().filter(names::contains).findFirst().orElse("");
            bestObj = pickObj;
        }
        if (!best.isEmpty() && db.get(best) != null) {
            bestXp = xp(Damage.piece(db.get(best)));
        }
        double pickXp = picked.isEmpty() ? Double.NaN : picked.stream().mapToDouble(n -> {
            for (String bs : buildSlots(slot)) {
                Damage.Piece p = set.get(bs);
                if (p != null && p.name().equals(n)) {
                    return xp(p);
                }
            }
            return 0;
        }).max().orElse(0);
        // the listed item's rank among the slot's candidates, each swapped into the pick's set and scored the same way
        int rank = 0;
        int scored = 0;
        if (!Double.isNaN(pickObj)) {
            int above = 0;
            for (String c : cands) {
                if (names.contains(c) || db.get(c) == null) {
                    continue;
                }
                double o = picked.contains(c) ? pickObj : swap(set, slot, db.get(c), level, earned);
                if (Double.isNaN(o)) {
                    continue;
                }
                scored++;
                if (Double.isNaN(bestObj) || o > bestObj * (1 + 1e-9)) {
                    above++;
                }
            }
            rank = 1 + above;
        }
        return new Row(k.name(), arch, goal, level, slot, String.join(" | ", names), String.join(" | ", picked),
                String.join(" | ", top), hit, inTop, best, bestObj, pickObj, bestXp, pickXp, String.join("; ", causes), rank,
                scored + 1);
    }

    /** The pick's set with {@code info} in the slot (each ring slot tried), scored at the level: the best objective, NaN when it never fits. */
    private static double swap(Map<String, Damage.Piece> set, String slot, ItemData info, int level, int earned) {
        double o = Double.NaN;
        if (info == null) {
            return o;
        }
        for (String bs : buildSlots(slot)) {
            Map<String, Damage.Piece> trial = new LinkedHashMap<>(set);
            trial.put(bs, Damage.piece(info));
            Build.Eval ev = ReferenceBuilds.score(trial, level, earned);
            if (ev != null && (Double.isNaN(o) || ev.result().objective() > o)) {
                o = ev.result().objective();
            }
        }
        return o;
    }

    private static double xp(Damage.Piece p) {
        return p.stats().getOrDefault("combatExperience", 0.0) + p.stats().getOrDefault("xpBonus", 0.0);
    }

    /** Likely reasons the road map passed a listed item over, as short tags. */
    private List<String> causes(Klass k, String arch, int level, String slot, String best, List<Meta> metas,
            Map<String, Damage.Piece> set, Map<String, String> fit, Map<String, List<String>> why, double ratio) {
        List<String> out = new ArrayList<>();
        ItemData info = db.get(best);
        if (info == null) {
            return out;
        }
        if (fit.values().stream().allMatch("no fit"::equals)) {
            out.add("skill points (no listed item fits the pick's set)");
        }
        List<String> lines = why.getOrDefault(best, List.of());
        if (lines.isEmpty()) {
            out.add("not traced (never a candidate at this level)");
        } else {
            lines.stream().filter(l -> l.contains("skip")).findFirst().ifPresent(l -> out.add("trace: "
                    + l.substring(l.indexOf(':') + 1).trim()));
        }
        double pickXp = 0;
        for (String bs : buildSlots(slot)) {
            if (set.get(bs) != null) {
                pickXp = Math.max(pickXp, xp(set.get(bs)));
            }
        }
        double metaXp = xp(Damage.piece(info));
        if (metaXp > pickXp + 4) {
            out.add(String.format(Locale.ROOT, "xp bonus (%+.0f%% vs pick)", metaXp - pickXp));
        }
        String origin = io.github.anilken.wynnlabs.market.ShoppingSources.origin(info);
        String type = origin.split(": ", 2)[0];
        if (!info.restriction().equals("NONE")) {
            out.add("untradable (" + info.restriction().toLowerCase(Locale.ROOT) + ", " + type + ")");
        } else if (!type.equals("NORMAL_MOB_DROP") && !type.equals("LOOT_CHEST") && !type.equals("MERCHANT")) {
            out.add("source " + type);
        }
        for (String bad : io.github.anilken.wynnlabs.core.Rules.list("shopping", "excludeSources")) {
            if (origin.startsWith(bad)) {
                out.add("source excluded by rules (" + bad + ")");
            }
        }
        if (io.github.anilken.wynnlabs.market.ShoppingSources.questPending(GameState.current(), info) != null) {
            out.add("quest not done in the fixture's book");
        }
        info.set().ifPresent(s -> out.add("set piece (" + s.name() + ")"));
        if (level - info.level() > io.github.anilken.wynnlabs.core.Rules.sub("shopping", "milestoneWindow", 20)) {
            out.add("below the milestone window (Lv " + info.level() + ")");
        }
        Meta m = metas.stream().filter(x -> x.item().equals(best)).findFirst().orElse(null);
        if (m != null) {
            if (m.tags().contains("Melee") && !m.tags().contains("Spell")) {
                out.add("melee item for a spell archetype");
            }
            if (m.notes().stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).contains("powder"))) {
                out.add("thread counts on high tier powders");
            }
            if (m.tags().contains("Utility") || m.tags().contains("Special")) {
                out.add("utility/special pick (" + String.join(",", m.tags()) + ")");
            }
        }
        if (!Double.isNaN(ratio) && ratio >= 0.97 && ratio <= 1.02) {
            out.add("near tie");
        }
        return out;
    }

    private SetRow wholeSet(JsonObject doc, Klass k, String goal, int level, String track, JsonObject b,
            Map<String, Damage.Piece> pickSet, Build.Eval pickEval, int earned) {
        List<String> pieces = strs(b, "pieces");
        String label = str(b, "label");
        Map<String, Damage.Piece> ref = new LinkedHashMap<>();
        ref.put("WEAPON", pickSet.get("WEAPON"));
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < ORDER.length && i < pieces.size(); i++) {
            String name = pieces.get(i).replaceAll("\\s*\\[[^\\]]*\\]\\s*$", "").trim();
            ItemData info = db.get(name);
            if (info == null) {
                info = db.values().stream().filter(x -> x.name().equalsIgnoreCase(name)).findFirst().orElse(null);
            }
            if (info == null || info.level() > level) {
                missing.add(name + (info == null ? "" : " (Lv " + info.level() + ")"));
                ref.put(ORDER[i], pickSet.get(ORDER[i]));
            } else {
                ref.put(ORDER[i], Damage.piece(info));
            }
        }
        // the track's upgrades so far ("20: Boots => Ado Saki")
        for (JsonElement el : doc.getAsJsonArray("armour")) {
            JsonObject a = el.getAsJsonObject();
            if (track.equals(str(a, "track")) && label.equals(str(a, "build")) && "upgrade".equals(str(a, "kind"))
                    && a.get("level").getAsInt() <= level && str(a, "item") != null && db.get(str(a, "item")) != null
                    && db.get(str(a, "item")).level() <= level) {
                String s = str(a, "slot");
                ref.put(s.equals("ring") ? "RING1" : s.toUpperCase(Locale.ROOT), Damage.piece(db.get(str(a, "item"))));
            }
        }
        int need = ReferenceBuilds.need(ref.values());
        Build.Eval ev = ReferenceBuilds.score(ref, level, Math.max(earned, need));
        String note = (missing.isEmpty() ? "" : "pick kept for " + String.join(", ", missing))
                + (need > earned ? (missing.isEmpty() ? "" : "; ") + "needs " + need + " points of " + earned : "")
                + (ev == null ? (need > earned || !missing.isEmpty() ? "; " : "") + "does not fit" : "");
        Damage.Result r = ev == null ? null : ev.result();
        Damage.Result p = pickEval.result();
        return new SetRow(k.name(), goal, level, track, label, r == null ? Double.NaN : r.objective(), p.objective(),
                r == null ? Double.NaN : r.spellDps() + r.mainDps(), p.spellDps() + p.mainDps(),
                r == null ? Double.NaN : r.ehpElemental(), p.ehpElemental(), r == null ? Double.NaN : r.xp(), p.xp(), note);
    }

    // ---- sources ----

    /** The thread's acquisition notes against the item data's sources (what the road map shows as "from"). */
    private void checkSources(JsonObject doc) {
        Map<String, String[]> seen = new LinkedHashMap<>();
        for (String part : List.of("weapons", "armour")) {
            for (JsonElement el : doc.getAsJsonArray(part)) {
                JsonObject e = el.getAsJsonObject();
                String item = str(e, "item");
                if (item == null || seen.containsKey(item)) {
                    continue;
                }
                JsonObject acq = e.getAsJsonObject("acquisition");
                String src = str(acq, "source");
                Boolean tradable = acq.get("tradable").isJsonNull() ? null : acq.get("tradable").getAsBoolean();
                String notes = String.join(" ", strs(e, "notes"));
                ItemData info = db.get(item);
                if (info == null) {
                    continue;
                }
                List<String> types = new ArrayList<>();
                List<String> where = new ArrayList<>();
                GameState.current().obtain(info).forEach(o -> {
                    types.add(o.type());
                    where.add(o.type() + o.name().map(n -> ": " + n).orElse(""));
                });
                List<String> issues = new ArrayList<>();
                String text = ((src == null ? "" : src) + " " + notes).toLowerCase(Locale.ROOT);
                if (src != null || !notes.isBlank()) {
                    expect(issues, text, types, "world event", "WORLD_EVENT");
                    expect(issues, text, types, "altar", "BOSS_ALTAR");
                    if (text.contains("dungeon merchant") || text.matches(".*merchant, \\d+ runs.*")) {
                        expectAny(issues, types, "dungeon merchant", "DUNGEON_MERCHANT");
                    } else if (text.contains("merchant")) {
                        expectAny(issues, types, "merchant", "MERCHANT", "DUNGEON_MERCHANT");
                    }
                    if (text.contains("quest reward") || text.contains("quest is completed") || text.contains("if the quest")) {
                        expectAny(issues, types, "quest", "QUEST", "MERCHANT");
                    }
                    if (text.contains("hive") && where.stream().noneMatch(w -> w.contains("Qira"))) {
                        issues.add("thread: Qira Hive (" + src + "), data: " + (types.isEmpty() ? "none" : String.join("/", types)));
                    }
                }
                if (Boolean.FALSE.equals(tradable) && info.restriction().equals("NONE")) {
                    issues.add("thread says untradable, data says tradable");
                }
                if (types.isEmpty()) {
                    issues.add("no source in the item data (road map: UNKNOWN)");
                }
                String origin = io.github.anilken.wynnlabs.market.ShoppingSources.origin(info);
                for (String bad : io.github.anilken.wynnlabs.core.Rules.list("shopping", "excludeSources")) {
                    if (origin.startsWith(bad)) {
                        issues.add("hidden by the default rules (" + bad + ")");
                    }
                }
                seen.put(item, new String[] {item, part.equals("weapons") ? str(e, "class") : str(e, "slot"),
                    String.valueOf(info.level()), src == null ? "" : src, tradable == null ? "" : tradable.toString(),
                    info.restriction(), String.join(" / ", where), origin, String.join("; ", issues)});
            }
        }
        sources.addAll(seen.values());
    }

    private static void expect(List<String> issues, String text, List<String> types, String word, String type) {
        if (text.contains(word) && !types.contains(type)) {
            issues.add("thread: " + word + ", data: " + (types.isEmpty() ? "none" : String.join("/", types)));
        }
    }

    private static void expectAny(List<String> issues, List<String> types, String word, String... any) {
        for (String t : any) {
            if (types.contains(t)) {
                return;
            }
        }
        issues.add("thread: " + word + ", data: " + (types.isEmpty() ? "none" : String.join("/", types)));
    }

    // ---- the report ----

    private List<String> write(JsonObject doc, List<Integer> levels, List<String> goals, long ms) throws Exception {
        String[] cols = {"class", "archetype", "goal", "level", "slot", "listed", "pick", "top3", "hit", "in_top3", "best_listed",
            "listed_objective", "pick_objective", "ratio", "listed_xp", "pick_xp", "causes", "rank", "candidates"};
        StringBuilder csv = new StringBuilder(String.join(",", cols)).append('\n');
        for (Row r : rows) {
            Object[] v = {r.klass(), r.arch(), r.goal(), r.level(), r.slot(), r.metas(), r.pick(), r.top3(), r.hit(), r.inTop3(),
                r.bestMeta(), r.metaObj(), r.pickObj(), r.ratio(), r.metaXp(), r.pickXp(), r.causes(), r.rank(), r.cands()};
            line(csv, v);
        }
        Files.writeString(ReferenceBuilds.out("leveling.csv"), csv, StandardCharsets.UTF_8);
        StringBuilder sets = new StringBuilder("class,goal,level,track,build,ref_objective,pick_objective,ratio,ref_dps,pick_dps,"
                + "ref_ehp_elemental,pick_ehp_elemental,ref_xp,pick_xp,note\n");
        for (SetRow s : setRows) {
            line(sets, new Object[] {s.klass(), s.goal(), s.level(), s.track(), s.build(), s.refObj(), s.pickObj(), s.ratio(),
                s.refDps(), s.pickDps(), s.refEhp(), s.pickEhp(), s.refXp(), s.pickXp(), s.note()});
        }
        Files.writeString(ReferenceBuilds.out("leveling-sets.csv"), sets, StandardCharsets.UTF_8);
        StringBuilder src = new StringBuilder("item,class_or_slot,item_level,thread_source,thread_tradable,restriction,data_sources,"
                + "road_map_origin,issues\n");
        sources.forEach(s -> line(src, s));
        Files.writeString(ReferenceBuilds.out("leveling-sources.csv"), src, StandardCharsets.UTF_8);

        StringBuilder md = new StringBuilder("# Leveling: the community list against the road map\n\n");
        md.append("Source: ").append(doc.get("source").getAsString()).append(" (main post by ").append(doc.get("author").getAsString())
                .append("). Model version ").append(modelVersion()).append(". Levels ").append(levels).append(", goals ").append(goals)
                .append(". Run time ").append(ms / 1000).append(" s, of which ").append(roadmaps).append(" road maps ")
                .append(roadmapMillis / 1000).append(" s, ").append(Boolean.getBoolean("wynnlabs.fullBeam") ? "the game's wide search"
                        : "the tests' narrow search (jointPool 6, beamWidth 12; -PfullBeam=true for the game's)").append(".\n\n");
        md.append("Archetypes (the thread's meta, as the tree names them): ").append(archetypeOf).append(".\n\n");
        md.append("hit = a listed item is the road map's pick for the slot; top 3 = the pick or one of the slot's two best other")
                .append(" options. ratio = the best listed item swapped into the pick's set / the pick's set (misses only;")
                .append(" `Build.allocateBest` at the level with the archetype's tree plan).\n\n");
        md.append("How to read the measures:\n\n")
                .append("- **hit**: how often the road map picks one of the thread's items for the slot.\n")
                .append("- **top 3**: how often a listed item is the pick or one of the slot's two best other options.\n")
                .append("- **rank**: the listed item's place among the slot's candidates (the pick and the slot's ").append(OPTIONS)
                .append(" best other options), each swapped into the pick's set and scored the same way; 1 means the model")
                .append(" rates the listed item at least as high as all of them. **1/rank** averages 1 / that place (1.0: always")
                .append(" first, 0.5: second on average); a listed item that does not fit counts as last.\n")
                .append("- **ratio**: the best listed item swapped into the pick's set / the pick's set, misses only.\n\n");
        for (String goal : goals) {
            List<Row> g = rows.stream().filter(r -> r.goal().equals(goal) && !r.slot().equals("-")).toList();
            md.append("## Goal ").append(goal).append("\n\n### By class\n\n");
            table(md, g, Row::klass);
            md.append("\n### By slot\n\n");
            table(md, g, Row::slot);
            md.append("\n### Class by slot (hit / top 3)\n\n| class |");
            List<String> slots = new ArrayList<>(List.of("weapon"));
            slots.addAll(List.of(ARMOUR));
            slots.forEach(x -> md.append(' ').append(x).append(" |"));
            md.append("\n|---|").append("---|".repeat(slots.size())).append('\n');
            for (Klass k : CLASSES) {
                md.append("| ").append(k.name()).append(" |");
                for (String x : slots) {
                    List<Row> c = g.stream().filter(r -> r.klass().equals(k.name()) && r.slot().equals(x)).toList();
                    md.append(c.isEmpty() ? " - |" : String.format(Locale.ROOT, " %.0f / %.0f %% |",
                            100.0 * c.stream().filter(Row::hit).count() / c.size(),
                            100.0 * c.stream().filter(Row::inTop3).count() / c.size()));
                }
                md.append('\n');
            }
            md.append("\n### By level\n\n");
            table(md, g, r -> String.format(Locale.ROOT, "%03d", r.level()));
            md.append("\n### Causes among misses (not in top 3)\n\n| cause | misses |\n|---|---|\n");
            Map<String, Integer> causes = new TreeMap<>();
            g.stream().filter(r -> !r.inTop3()).forEach(r -> {
                for (String c : r.causes().split("; ")) {
                    if (!c.isBlank()) {
                        causes.merge(c.replaceAll("\\(.*\\)", "(…)").replaceAll("trace: .*", "trace: skipped"), 1, Integer::sum);
                    }
                }
            });
            causes.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n"));
            md.append("\n### Worst 15 misses (lowest ratio)\n\n| class | Lv | slot | listed | pick | ratio | causes |\n|---|---|---|---|---|---|---|\n");
            g.stream().filter(r -> !r.inTop3() && !Double.isNaN(r.ratio())).sorted(Comparator.comparingDouble(Row::ratio)).limit(15)
                    .forEach(r -> md.append(String.format(Locale.ROOT, "| %s | %d | %s | %s | %s | %.3f | %s |%n", r.klass(), r.level(),
                            r.slot(), r.bestMeta(), r.pick(), r.ratio(), r.causes())));
            md.append("\n### Misses the model rates higher than its pick (ratio > 1.02)\n\n| class | Lv | slot | listed | pick | ratio | causes |\n|---|---|---|---|---|---|---|\n");
            g.stream().filter(r -> !r.hit() && r.ratio() > 1.02).sorted(Comparator.comparingDouble(Row::ratio).reversed()).limit(20)
                    .forEach(r -> md.append(String.format(Locale.ROOT, "| %s | %d | %s | %s | %s | %.3f | %s |%n", r.klass(), r.level(),
                            r.slot(), r.bestMeta(), r.pick(), r.ratio(), r.causes())));
            md.append("\n### Whole listed builds against the pick\n\n| track | n | median ratio | within 10 % or above | median ref/pick xp |\n|---|---|---|---|---|\n");
            Map<String, List<SetRow>> byTrack = new TreeMap<>();
            setRows.stream().filter(s -> s.goal().equals(goal) && !Double.isNaN(s.ratio()))
                    .forEach(s -> byTrack.computeIfAbsent(s.track(), x -> new ArrayList<>()).add(s));
            byTrack.forEach((t, l) -> md.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.0f %% | %.2f |%n", t, l.size(),
                    ReferenceBuilds.median(l.stream().map(SetRow::ratio).toList()),
                    100.0 * l.stream().filter(s -> s.ratio() >= 0.9).count() / l.size(),
                    ReferenceBuilds.median(l.stream().filter(s -> s.pickXp() > 0).map(s -> s.refXp() / s.pickXp()).toList()))));
            md.append('\n');
        }
        md.append("## Sources: the thread's notes against the item data\n\n");
        long withIssues = sources.stream().filter(s -> !s[8].isEmpty()).count();
        md.append(sources.size()).append(" listed items checked, ").append(withIssues).append(" with a remark.\n\n");
        md.append("| item | for | Lv | thread | data | remark |\n|---|---|---|---|---|---|\n");
        sources.stream().filter(s -> !s[8].isEmpty()).forEach(s -> md.append("| ").append(s[0]).append(" | ").append(s[1])
                .append(" | ").append(s[2]).append(" | ").append(s[3]).append(" | ").append(s[6]).append(" | ").append(s[8]).append(" |\n"));
        BenchmarkBaseline.Metrics m = new BenchmarkBaseline.Metrics();
        for (String goal : goals) {
            List<Row> g = rows.stream().filter(r -> r.goal().equals(goal) && !r.slot().equals("-")).toList();
            metrics(m, goal + ".overall", g);
            for (Klass k : CLASSES) {
                metrics(m, goal + ".class." + k.name(), g.stream().filter(r -> r.klass().equals(k.name())).toList());
            }
            Map<String, List<Row>> bySlot = new TreeMap<>();
            g.forEach(r -> bySlot.computeIfAbsent(r.slot(), x -> new ArrayList<>()).add(r));
            bySlot.forEach((s, l) -> metrics(m, goal + ".slot." + s, l));
            Map<String, List<Double>> byTrack = new TreeMap<>();
            setRows.stream().filter(s -> s.goal().equals(goal) && !Double.isNaN(s.ratio()))
                    .forEach(s -> byTrack.computeIfAbsent(s.track(), x -> new ArrayList<>()).add(s.ratio()));
            byTrack.forEach((t, l) -> m.put(goal + ".whole_set." + t.replace(' ', '_') + ".median_ratio", BenchmarkBaseline.Kind.RATIO,
                    ReferenceBuilds.median(l), l.size()));
        }
        boolean narrowed = System.getProperty("wynnlabs.levelingLevels") != null || System.getProperty("wynnlabs.levelingGoals") != null
                || Boolean.getBoolean("wynnlabs.fullBeam");
        List<String> regressions = BenchmarkBaseline.check("leveling", modelVersion(), m, md,
                narrowed ? "a narrowed run (levels, goals or the wide search)"
                        : Files.exists(ReferenceBuilds.out("model-override.json")) ? "a model override is in force" : null);
        Files.writeString(ReferenceBuilds.out("leveling-report.md"), md, StandardCharsets.UTF_8);
        Fixtures.note("leveling: " + ReferenceBuilds.out("leveling-report.md"));
        return regressions;
    }

    private static void line(StringBuilder sb, Object[] v) {
        List<String> cells = new ArrayList<>();
        for (Object o : v) {
            cells.add(ReferenceBuilds.csv(o));
        }
        sb.append(String.join(",", cells)).append('\n');
    }

    private static void table(StringBuilder md, List<Row> rows, java.util.function.Function<Row, String> key) {
        Map<String, List<Row>> by = new TreeMap<>();
        rows.forEach(r -> by.computeIfAbsent(key.apply(r), x -> new ArrayList<>()).add(r));
        md.append("| group | n | hit | top 3 | 1/rank | median rank | median ratio (misses) | worst miss |\n|---|---|---|---|---|---|---|---|\n");
        by.forEach((k, l) -> stats(md, k, l));
        stats(md, "**all**", rows);
    }

    private static List<Row> ranked(List<Row> l) {
        return l.stream().filter(r -> r.rank() > 0).toList();
    }

    private static void stats(StringBuilder md, String name, List<Row> l) {
        long hit = l.stream().filter(Row::hit).count();
        long top = l.stream().filter(Row::inTop3).count();
        List<Double> ratios = l.stream().filter(r -> !r.inTop3() && !Double.isNaN(r.ratio())).map(Row::ratio).toList();
        Row worst = l.stream().filter(r -> !r.inTop3() && !Double.isNaN(r.ratio())).min(Comparator.comparingDouble(Row::ratio)).orElse(null);
        List<Row> ranked = ranked(l);
        md.append(String.format(Locale.ROOT, "| %s | %d | %.0f %% | %.0f %% | %.2f | %.1f | %.3f | %s |%n", name, l.size(),
                l.isEmpty() ? 0 : 100.0 * hit / l.size(), l.isEmpty() ? 0 : 100.0 * top / l.size(),
                BenchmarkBaseline.mean(ranked.stream().map(Row::reciprocal).toList()),
                ReferenceBuilds.median(ranked.stream().map(r -> (double) r.rank()).toList()), ReferenceBuilds.median(ratios),
                worst == null ? "" : worst.klass() + " Lv " + worst.level() + " " + worst.slot() + " " + worst.bestMeta()
                        + String.format(Locale.ROOT, " %.3f", worst.ratio())));
    }

    /** A group's baseline metrics: hit and top-3 rates and the mean 1/rank. */
    private static void metrics(BenchmarkBaseline.Metrics m, String prefix, List<Row> l) {
        m.put(prefix + ".hit", BenchmarkBaseline.Kind.RATE, BenchmarkBaseline.pct(l.stream().filter(Row::hit).count(), l.size()), l.size());
        m.put(prefix + ".top3", BenchmarkBaseline.Kind.RATE, BenchmarkBaseline.pct(l.stream().filter(Row::inTop3).count(), l.size()),
                l.size());
        List<Row> ranked = ranked(l);
        m.put(prefix + ".reciprocal_rank", BenchmarkBaseline.Kind.RATIO,
                BenchmarkBaseline.mean(ranked.stream().map(Row::reciprocal).toList()), ranked.size());
    }

    private static String modelVersion() {
        try {
            java.lang.reflect.Method m = BeamCache.class.getDeclaredMethod("modelVersion");
            m.setAccessible(true);
            return String.valueOf(m.invoke(null));
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }
}
