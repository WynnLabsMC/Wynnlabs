package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.model.AbilityNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How many times a spell or the main attack hits, as the ability tree's text changes it (the
 * counts WynnBuilder's tree keeps as "hits", "arrows_per_stream", "bombCount"):
 * <ul>
 *   <li>"Bash will hit a second time": one hit more; "Bash will cast 4 times consecutively": at
 *       least four; "Spin Attack will activate twice": at least two;</li>
 *   <li>"Uproot becomes lashing flames, which hit 3 times": three; "Flaming Tongue gains +2
 *       hits" (a node named after the spell's change), "Increase Multihit's amount of hits by +3",
 *       "+6 arrows per stream": more hits of the same part;</li>
 *   <li>"Arrow Storm will shoot +1 stream", "Smoke Bomb will throw +2 bombs": the whole part
 *       again;</li>
 *   <li>"Doubles Main Attack arrows … Main Attack Damage: -40% (per arrow)", "Triples Main Attack
 *       arrows … -10%", "Your Main Attack will hit twice, but deal -40% damage per hit", "Your Main
 *       Attack will emit +2 extra beams": the main attack's shots, the cut on each and the beams.</li>
 * </ul>
 * The per-hit damage those nodes add or take off is {@link TreeBonuses}': the counts here only
 * say how many hits it lands on.
 */
final class HitCounts {

    private HitCounts() {}

    /**
     * One node's change to a spell's hits: {@code set} the hits of one part (0: none),
     * {@code add} more of them, {@code units} more parts (streams, bombs), {@code atLeast} the
     * hits a cast has at least (0: none).
     */
    record Change(String node, String spell, int set, int add, int units, int atLeast) {}

    /**
     * One node's change to the main attack: {@code shots} it fires at least (0: none),
     * {@code cut} the share each loses, {@code beams} extra relik beams.
     */
    record Main(String node, int shots, double cut, int beams) {}

    /**
     * Everything read: the spells' changes, the main attack's, and whether the tree has a node
     * that sets the main attack's shots (the class profile's guess of it then gives way).
     */
    record Reading(List<Change> spells, List<Main> main, boolean mainKnown) {
        static Reading none() {
            return new Reading(List.of(), List.of(), false);
        }
    }

    private static final Pattern SECOND = Pattern.compile("will hit a second time");
    private static final Pattern CAST_N = Pattern.compile("will cast (\\d+) times");
    private static final Pattern TWICE = Pattern.compile("will activate twice");
    private static final Pattern WHICH_HIT = Pattern.compile("which hits? (\\d+) times");
    private static final Pattern ADD_HITS = Pattern.compile("(?:gains|amount of hits by) \\+(\\d+) hits?\\b|amount of hits by \\+(\\d+)");
    private static final Pattern ARROWS = Pattern.compile("\\+(\\d+) (?:extra )?arrows per stream");
    private static final Pattern UNITS = Pattern.compile("\\+(\\d+) (?:streams?|bombs?)\\b");
    private static final Pattern MAIN_SHOTS = Pattern.compile("(doubles|triples) main attack arrows|main attack will hit (twice)");
    private static final Pattern MAIN_CUT = Pattern.compile("main attack damage: -(\\d+)%|deal -(\\d+)% damage per hit");
    private static final Pattern BEAMS = Pattern.compile("main attack will emit \\+(\\d+) extra beams");

    /** Reads the tree's text for the class's spells ({@code spellNames}, as rules.model.spells has them). */
    static Reading read(List<AbilityNode> tree, String cls, Collection<String> spellNames) {
        List<String> spells = new ArrayList<>();
        spellNames.forEach(s -> spells.add(s.toLowerCase(Locale.ROOT)));
        Map<String, String> byLow = new TreeMap<>();
        spellNames.forEach(s -> byLow.put(s.toLowerCase(Locale.ROOT), s));
        // a node that changes a spell stands for it in other nodes' text ("Flaming Tongue gains +2 hits")
        Map<String, String> via = new TreeMap<>();
        for (AbilityNode n : tree) {
            String low = n.name().toLowerCase(Locale.ROOT);
            if (spells.contains(low) || TreeModel.spellNode(n, cls)) {
                continue;
            }
            String s = TreeReading.firstSpell(TreeModel.text(n).toLowerCase(Locale.ROOT), spells, Map.of());
            if (s != null) {
                via.put(low, s);
            }
        }
        List<Change> changes = new ArrayList<>();
        List<Main> main = new ArrayList<>();
        boolean mainKnown = false;
        for (AbilityNode n : tree) {
            if (TreeModel.spellNode(n, cls) || spells.contains(n.name().toLowerCase(Locale.ROOT))
                    || n.type() != null && n.type().contains("ULTIMATE")) {
                continue;
            }
            String low = TreeModel.text(n).toLowerCase(Locale.ROOT);
            for (String sentence : low.split("\\. ")) {
                change(n.name(), sentence, spells, via, byLow, changes);
            }
            Main m = main(n.name(), low);
            if (m != null) {
                main.add(m);
                mainKnown |= m.shots() > 0;
            }
        }
        return new Reading(changes, main, mainKnown);
    }

    /** One sentence's changes to a spell into {@code out}. */
    private static void change(String node, String s, List<String> spells, Map<String, String> via,
            Map<String, String> byLow, List<Change> out) {
        int set = 0;
        int add = 0;
        int units = 0;
        int atLeast = 0;
        int at = -1;
        Matcher m;
        if ((m = SECOND.matcher(s)).find()) {
            add += 1;
            at = m.start();
        }
        if ((m = CAST_N.matcher(s)).find()) {
            atLeast = Integer.parseInt(m.group(1));
            at = at < 0 ? m.start() : at;
        }
        if ((m = TWICE.matcher(s)).find()) {
            atLeast = Math.max(atLeast, 2);
            at = at < 0 ? m.start() : at;
        }
        if ((m = WHICH_HIT.matcher(s)).find()) {
            set = Integer.parseInt(m.group(1));
            at = at < 0 ? m.start() : at;
        }
        m = ADD_HITS.matcher(s);
        while (m.find()) {
            add += Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
            at = at < 0 ? m.start() : at;
        }
        m = ARROWS.matcher(s);
        while (m.find()) {
            add += Integer.parseInt(m.group(1));
            at = at < 0 ? m.start() : at;
        }
        m = UNITS.matcher(s);
        while (m.find()) {
            units += Integer.parseInt(m.group(1));
            at = at < 0 ? m.start() : at;
        }
        if (at < 0) {
            return;
        }
        String spell = subject(s.substring(0, at), spells, via);
        if (spell != null) {
            out.add(new Change(node, byLow.get(spell), set, add, units, atLeast));
        }
    }

    /** The spell named last before the change, by its name or a node that stands for it; null when none. */
    private static String subject(String before, List<String> spells, Map<String, String> via) {
        String best = null;
        int where = -1;
        for (String sp : spells) {
            int i = before.lastIndexOf(sp);
            if (i > where) {
                where = i;
                best = sp;
            }
        }
        for (var e : via.entrySet()) {
            int i = before.lastIndexOf(e.getKey());
            if (i > where) {
                where = i;
                best = e.getValue();
            }
        }
        return best;
    }

    /** One node's change to the main attack; null when it has none. */
    private static Main main(String node, String low) {
        int shots = 0;
        Matcher m = MAIN_SHOTS.matcher(low);
        if (m.find()) {
            shots = "triples".equals(m.group(1)) ? 3 : 2;
        }
        int beams = 0;
        Matcher b = BEAMS.matcher(low);
        if (b.find()) {
            beams = Integer.parseInt(b.group(1));
        }
        if (shots == 0 && beams == 0) {
            return null;
        }
        double cut = 0;
        Matcher c = MAIN_CUT.matcher(low);
        if (shots > 0 && c.find()) {
            cut = Integer.parseInt(c.group(1) != null ? c.group(1) : c.group(2)) / 100.0;
        }
        return new Main(node, shots, cut, beams);
    }

    /** The changes of the nodes taken, per spell: {set, add, units, atLeast}. */
    static Map<String, int[]> sum(Reading r, Collection<String> have) {
        Map<String, int[]> out = new TreeMap<>();
        for (Change c : r.spells()) {
            if (!have.contains(c.node())) {
                continue;
            }
            int[] v = out.computeIfAbsent(c.spell(), k -> new int[4]);
            v[0] = Math.max(v[0], c.set());
            v[1] += c.add();
            v[2] += c.units();
            v[3] = Math.max(v[3], c.atLeast());
        }
        return out;
    }

    /**
     * The main attack's factor from the nodes taken: shots times what each keeps, times the
     * relik's beams over its three ({@code beams} only when a node adds any). 1 for none.
     */
    static double mainFactor(Reading r, Collection<String> have) {
        int shots = 1;
        double cut = 0;
        int beams = 0;
        for (Main m : r.main()) {
            if (have.contains(m.node())) {
                shots = Math.max(shots, m.shots());
                cut += m.cut();
                beams += m.beams();
            }
        }
        return shots * Math.max(0, 1 - cut) * (3 + beams) / 3.0;
    }

    /**
     * A rules.model.spells entry ({@code s}, a copy) with its hits changed by {@code c} ({set,
     * add, units, atLeast}; null: none): the conversion, the whole cast's, scales with them.
     */
    static void apply(com.google.gson.JsonObject s, int[] c) {
        if (c == null || !s.has("conv") || !s.get("conv").isJsonObject()) {
            return;
        }
        double base = s.has("hits") ? s.get("hits").getAsDouble() : 1;
        double per = c[0] > 0 ? c[0] : base;
        per = Math.max(per + c[1], c[3]);
        double hits = per * (1 + c[2]);
        if (hits == base || base <= 0) {
            return;
        }
        com.google.gson.JsonObject conv = s.getAsJsonObject("conv");
        for (String el : Damage.ELEMENTS) {
            if (conv.has(el)) {
                conv.addProperty(el, Math.round(conv.get(el).getAsDouble() * hits / base * 10000) / 10000.0);
            }
        }
        s.addProperty("hits", hits);
    }
}
