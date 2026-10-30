package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared steps of the reference build checks ({@link ReferenceBuildsBenchmark},
 * {@link CursedBuildsCheck}): read a resource, decode a WynnBuilder link into the model's pieces,
 * set the shaman-44 fixture up as the link's class, archetype and level, and score a set the way
 * the road map does (the archetype's tree plan at that level, the greedy spread polished).
 */
final class ReferenceBuilds {

    static final String PROPERTY = "wynnlabs.referenceBuilds";
    /** WynnBuilder's skill point budget: 200 at Lv 101 and above, tomes not counted. */
    static final int CAP = 200;

    private ReferenceBuilds() {}

    static JsonObject resource(String name) throws IOException {
        Path f = Fixtures.root().resolveSibling("reference-builds").resolve(name);
        return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static Path out(String name) throws IOException {
        Path f = Path.of(System.getProperty("wynnlabs.projectDir", "."), "build", "reference-builds", name);
        Files.createDirectories(f.getParent());
        return f;
    }

    /** The skill points earned by a level (the road map's own count). */
    static int earned(int level) {
        return Math.max(0, (Math.min(level, 101) - 1) * 2);
    }

    private static final Map<Klass, List<AbilityNode>> TREES = new HashMap<>();

    static List<AbilityNode> tree(Klass k) throws IOException {
        List<AbilityNode> t = TREES.get(k);
        if (t == null) {
            t = TreeModelTest.tree(k.name().toLowerCase(Locale.ROOT));
            TREES.put(k, t);
        }
        return t;
    }

    /** The archetypes the class's tree has. */
    static List<String> archetypes(Klass k) throws IOException {
        List<String> out = new ArrayList<>();
        for (AbilityNode n : tree(k)) {
            if (n.archetype() != null && !out.contains(n.archetype())) {
                out.add(n.archetype());
            }
        }
        return out;
    }

    /** The fixture as nobody in particular: the item database to find a link's weapon in. */
    static void installPlain() throws IOException {
        if (plain == null) {
            plain = FixtureState.load(Fixtures.root().resolve("shaman-44"));
        }
        plain.install("en");
    }

    private static FixtureState plain;
    /** One loaded fixture per class: reading shaman-44 again for every link took most of the run. */
    private static final Map<Klass, FixtureState> AS = new HashMap<>();

    /**
     * The shaman-44 fixture played as {@code k} at {@code level}, no node read as unlocked (the
     * plan from the first node), {@code archetype} chosen the way the player chooses it ("" for
     * none) and the Damage goal. Choosing goes through TreePlanner.choose: Rules.update alone
     * left the fixture's Summoner in place; with no archetype the class profile is applied too.
     */
    static void install(Klass k, int level, String archetype) throws IOException {
        FixtureState s = AS.get(k);
        if (s == null) {
            s = FixtureState.load(Fixtures.root().resolve("shaman-44")).as(k).withTree(tree(k), List.of());
            AS.put(k, s);
        }
        s.atLevel(level).install("en");
        TreePlanner.choose(archetype);
        if (archetype.isEmpty()) {
            // choose("") leaves the class profile alone, so the fixture's Shaman spells stayed for other classes
            ClassProfiles.apply("", false);
        }
        Setup.setGoal("DAMAGE");
    }

    /** The class a link's weapon is for; null when the weapon is not in the database. */
    static Klass classOf(WynnBuilderLink.Build b) {
        for (WynnBuilderLink.Piece p : b.pieces()) {
            if (p.slot() != WynnBuilderLink.WEAPON || p.kind() != WynnBuilderLink.Kind.ITEM) {
                continue;
            }
            ItemData info = byName(p.name());
            if (info == null && p.alias() != null) {
                info = byName(p.alias());
            }
            if (info != null) {
                Klass k = info.requirements().klass().orElse(info.slot().klass());
                return k == Klass.NONE ? null : k;
            }
        }
        return null;
    }

    static ItemData byName(String name) {
        if (name == null) {
            return null;
        }
        return GameState.current().allGear().filter(i -> i.name().equals(name)).findFirst().orElse(null);
    }

    /** A link turned into the model's set: the pieces kept by build slot, the items, and what was left out ("slot:why"). */
    record LinkSet(Map<String, Damage.Piece> set, List<ItemData> items, List<String> dropped) {
        boolean hasWeapon() {
            return set.containsKey("WEAPON");
        }
    }

    private static final String[] SLOT_NAMES = {"helmet", "chestplate", "leggings", "boots", "ring1", "ring2", "bracelet",
        "necklace", "weapon"};

    static LinkSet set(WynnBuilderLink.Build b) {
        LinkImport.Result r = LinkImport.of(GameState.current(), b);
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        List<ItemData> items = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        int rings = 0;
        for (LinkImport.Row row : r.rows()) {
            if (row.why() != LinkImport.Why.KEPT) {
                dropped.add(SLOT_NAMES[row.piece().slot()] + ":" + row.why().name().toLowerCase(Locale.ROOT)
                        + (row.why() == LinkImport.Why.UNKNOWN && row.piece().name() != null ? "(" + row.piece().name() + ")" : ""));
                continue;
            }
            Slot k = Locks.kind(row.info());
            String slot = k.isWeapon() ? "WEAPON" : Build.slotOf(k, k == Slot.RING ? rings++ : 0);
            set.put(slot, Damage.piece(row.info()));
            items.add(row.info());
        }
        return new LinkSet(set, items, dropped);
    }

    static int need(Collection<Damage.Piece> pieces) {
        int sum = 0;
        for (int v : Build.minPoints(pieces)) {
            sum += v;
        }
        return sum;
    }

    /** The set scored at {@code level} with the chosen archetype's tree there; null when it does not fit {@code earned}. */
    static Build.Eval score(Map<String, Damage.Piece> set, int level, int earned) {
        TreeModel.Run run = TreeModel.run();
        TreeModel.Overlay o = run == null ? null : run.at(level);
        return TreeModel.with(o, () -> Build.allocateBest(set, level, earned));
    }

    /** {@link #score}'s set with its breakdown source by source (spells, main attack, summons). */
    static Damage.Detail detail(Map<String, Damage.Piece> set, int[] allocation, int level) {
        TreeModel.Run run = TreeModel.run();
        TreeModel.Overlay o = run == null ? null : run.at(level);
        List<Damage.Piece> armour = new ArrayList<>();
        set.forEach((slot, p) -> {
            if (!slot.equals("WEAPON") && p != null) {
                armour.add(p);
            }
        });
        return TreeModel.with(o, () -> Damage.detail(armour, set.get("WEAPON"), allocation, level));
    }

    /** Health regen a second from the pieces' IDs (raw raised by %, half the life steal), as the survival factor counts it. */
    static double regen(Collection<Damage.Piece> pieces) {
        double raw = 0, pct = 0, steal = 0;
        for (Damage.Piece p : pieces) {
            if (p == null) {
                continue;
            }
            raw += p.stats().getOrDefault("healthRegenRaw", 0.0);
            pct += p.stats().getOrDefault("healthRegen", 0.0);
            steal += p.stats().getOrDefault("lifeSteal", 0.0);
        }
        return raw * (1 + pct / 100.0) + 0.5 * steal;
    }

    /** The regen part of the survival factor for {@code regen} at {@code level} (Damage.evaluate's formula, today's weights). */
    static double regenFactor(double regen, int level) {
        double scale = Damage.model("hpBase", 5) + Damage.model("hpPerLevel", 5) * level;
        return regen < 0
                ? Math.max(0.3, 1 + Damage.model("negativeRegenWeight", 2.0) * regen / scale)
                : 1 + Damage.model("regenWeight", 0.2) * Math.min(1, regen / scale);
    }

    /** The major IDs of the items ("Name:MAJOR"), comma-free. */
    static String majorIds(Collection<ItemData> items) {
        List<String> out = new ArrayList<>();
        for (ItemData i : items) {
            i.base().majorId().ifPresent(m -> out.add(m));
        }
        return String.join(" ", out);
    }

    static String csv(Object v) {
        String s = v == null ? "" : v instanceof Double d ? fmt(d) : String.valueOf(v);
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    static String fmt(double d) {
        if (Double.isNaN(d)) {
            return "";
        }
        return Math.abs(d) >= 100 ? String.format(Locale.ROOT, "%.0f", d) : String.format(Locale.ROOT, "%.3f", d);
    }

    static double median(List<Double> v) {
        if (v.isEmpty()) {
            return Double.NaN;
        }
        List<Double> s = new ArrayList<>(v);
        s.sort(Double::compare);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }
}
