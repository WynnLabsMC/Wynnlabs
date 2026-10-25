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
 * What the ability tree's text says about where damage and healing come from, beyond the
 * conversions {@link TreeReading} reads: the spell each node comes with, the heals, the
 * ultimates, and the nodes that change how much a spell or a summon deals or how many there are.
 * Read the way the game words it (Wynntils' data):
 * <ul>
 *   <li>"Totem and Aura will do 40% less damage": those spells' hits, the strongest such line
 *       of one wording (Double Totem 40 %, Triple Totem 50 %: the game gives the whole cut);</li>
 *   <li>"Once Aura reaches its maximum range, it will bounce back and deal its effects a second
 *       time": one more wave of that spell;</li>
 *   <li>"Radiates an aura from your Totem": the spell goes off once per Totem you have, as the
 *       Totem itself does ("Increase your maximum Totems by +1");</li>
 *   <li>"Each Totem will summon its own Effigy. Each additional Effigy summoned will decrease all
 *       Effigy damage by 0.8x": one Effigy per Totem, each hit 0.8× per Effigy past the first;</li>
 *   <li>"Total Heal: 1% (of your max health)" and "Regeneration and Sacrificial Shrine will heal
 *       40% less health": the heals and what cuts them.</li>
 * </ul>
 */
final class SourceText {

    private SourceText() {}

    /** A heal of the tree: {@code fraction} of your max health per cast of {@code spell}, or every {@code every} s while it lasts. */
    record Heal(String name, String spell, double fraction, double every) {}

    /** An ultimate that deals damage: its first damage block per hit, hits per second while it is up (0: one go). */
    record Ultimate(String name, double[] conv, double rate) {}

    /**
     * A node that scales what others deal or heal: {@code targets} (lower case spell or node
     * names) get {@code factor} on each hit or heal; {@code wording} groups the lines that give
     * the whole change between them (the strongest one of a wording counts, different wordings
     * multiply); {@code waves} one more wave of the first target per such node.
     */
    record Scale(String node, List<String> targets, double factor, String wording, int waves, boolean heal) {}

    /** A summon that comes once per {@code per} ("Each Totem will summon its own Effigy"), {@code decay}× per extra one. */
    record Each(String node, String per, double decay) {}

    /**
     * Everything read: node → spell it comes with, the heals, the ultimates, the scaling nodes,
     * the summons that come per something, spell → the noun it goes off once per ("totem") and
     * node → the base resistance it gives (0.05).
     */
    record Reading(Map<String, String> spellOf, List<Heal> heals, List<Ultimate> ultimates, List<Scale> scales,
            Map<String, Each> each, Map<String, String> perNoun, Map<String, Double> resist) {

        static Reading none() {
            return new Reading(Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private static final Pattern HEAL = Pattern.compile("heal: (\\d+(?:\\.\\d+)?)% \\(of your max health");
    private static final Pattern HEAL_WORD = Pattern.compile("\\bheals?\\b");
    private static final Pattern EVERY = Pattern.compile("every (\\d+(?:\\.\\d+)?)s");
    private static final Pattern DO_LESS = Pattern.compile("([a-z' ]+?) will do (\\d+)% (less|more) damage");
    private static final Pattern HEAL_LESS = Pattern.compile("([a-z' ]+?) will heal (\\d+)% (less|more) health");
    private static final Pattern AGAIN = Pattern.compile("deal its effects a second time");
    private static final Pattern FROM_YOUR = Pattern.compile("from your ([a-z]+)");
    private static final Pattern OWN = Pattern.compile("each ([a-z]+) will summon its own ([a-z]+)");
    private static final Pattern DECAY = Pattern.compile("decrease all [a-z]+ damage by (\\d+(?:\\.\\d+)?)x");
    /** "Gain +5% Base Resistance" (Tougher Skin, Mythril Skin): less of every hit you take, for good. */
    private static final Pattern RESIST = Pattern.compile("\\+(\\d+)% base resistance");

    /** Reads the tree's text for the class's spells ({@code spellNames}, as rules.model.spells has them). */
    static Reading read(List<AbilityNode> tree, String cls, Collection<String> spellNames) {
        List<String> spells = new ArrayList<>();
        Map<String, String> byLow = new TreeMap<>();
        for (String s : spellNames) {
            spells.add(s.toLowerCase(Locale.ROOT));
            byLow.put(s.toLowerCase(Locale.ROOT), s);
        }
        Map<String, String> spellOf = new TreeMap<>();
        List<Heal> heals = new ArrayList<>();
        List<Ultimate> ultimates = new ArrayList<>();
        List<Scale> scales = new ArrayList<>();
        Map<String, Each> each = new TreeMap<>();
        Map<String, String> perNoun = new TreeMap<>();
        Map<String, Double> resist = new TreeMap<>();
        for (AbilityNode n : tree) {
            String t = TreeModel.text(n);
            String low = t.toLowerCase(Locale.ROOT);
            boolean spell = TreeModel.spellNode(n, cls) || byLow.containsKey(n.name().toLowerCase(Locale.ROOT));
            boolean ultimate = n.type() != null && n.type().contains("ULTIMATE");
            if (ultimate) {
                double[] conv = TreeReading.firstBlock(t, low);
                if (conv != null) {
                    Matcher ev = EVERY.matcher(low);
                    double rate = ev.find() ? 1 / Math.max(0.05, Double.parseDouble(ev.group(1))) : 0;
                    ultimates.add(new Ultimate(n.name(), conv, rate));
                }
                continue;
            }
            if (spell) {
                Matcher fy = FROM_YOUR.matcher(low);
                if (fy.find()) {
                    perNoun.put(n.name(), fy.group(1).replaceAll("s$", ""));
                }
            } else {
                String s = TreeReading.firstSpell(low, spells, Map.of());
                if (s != null) {
                    spellOf.put(n.name(), byLow.get(s));
                }
            }
            scales(n.name(), low, spells, scales);
            Matcher rs = RESIST.matcher(low);
            if (rs.find()) {
                resist.put(n.name(), Integer.parseInt(rs.group(1)) / 100.0);
            }
            Matcher own = OWN.matcher(low);
            if (own.find()) {
                Matcher dc = DECAY.matcher(low);
                each.put(n.name(), new Each(n.name(), own.group(1), dc.find() ? Double.parseDouble(dc.group(1)) : 1));
            }
            Matcher h = HEAL.matcher(low);
            if (h.find()) {
                String healed = spell ? n.name() : lastSpellBefore(low, spells, h.start(), byLow);
                heals.add(new Heal(n.name(), healed, Double.parseDouble(h.group(1)) / 100.0, healEvery(low)));
            }
        }
        return new Reading(spellOf, heals, ultimates, scales, each, perNoun, resist);
    }

    /** The scaling lines of one node's text into {@code out}. */
    private static void scales(String node, String low, List<String> spells, List<Scale> out) {
        for (boolean heal : new boolean[] {false, true}) {
            Matcher m = (heal ? HEAL_LESS : DO_LESS).matcher(low);
            while (m.find()) {
                String subject = m.group(1).replaceFirst("^.*?(?:\\. |, )", "").trim();
                List<String> targets = new ArrayList<>();
                for (String part : subject.split(" and ")) {
                    targets.add(part.trim());
                }
                double pct = Integer.parseInt(m.group(2)) / 100.0;
                double factor = m.group(3).equals("less") ? 1 - pct : 1 + pct;
                out.add(new Scale(node, targets, factor, String.join("+", targets), 0, heal));
            }
        }
        if (AGAIN.matcher(low).find()) {
            String s = TreeReading.firstSpell(low, spells, Map.of());
            if (s != null) {
                out.add(new Scale(node, List.of(s), 1, "again", 1, false));
            }
        }
    }

    /**
     * The factor the taken nodes ({@code have}) give {@code target} (lower case): per wording the
     * strongest line, the wordings multiplied; and in {@code waves}[0] the extra waves.
     */
    static double factor(List<Scale> scales, Collection<String> have, String target, boolean heal, int[] waves) {
        Map<String, Double> byWording = new TreeMap<>();
        for (Scale s : scales) {
            if (s.heal() != heal || !have.contains(s.node()) || !s.targets().contains(target)) {
                continue;
            }
            if (s.waves() > 0) {
                if (waves != null) {
                    waves[0] += s.waves();
                }
                continue;
            }
            byWording.merge(s.wording(), s.factor(), (a, b) -> Math.abs(b - 1) > Math.abs(a - 1) ? b : a);
        }
        double f = 1;
        for (double v : byWording.values()) {
            f *= v;
        }
        return f;
    }

    /**
     * A rules.model.spells entry ({@code s}, a copy) times its {@code mod} ({factor, how many at
     * once, waves}; null: none): the cast's conversion then covers every wave from every Totem,
     * "hits" counts each of them, "perCount" and "waves" say how it splits.
     */
    static void scale(com.google.gson.JsonObject s, double[] mod) {
        if (mod == null || !s.has("conv") || !s.get("conv").isJsonObject()) {
            return;
        }
        double f = mod[0] * mod[1] * mod[2];
        com.google.gson.JsonObject conv = s.getAsJsonObject("conv");
        for (String el : Damage.ELEMENTS) {
            if (conv.has(el)) {
                conv.addProperty(el, conv.get(el).getAsDouble() * f);
            }
        }
        s.addProperty("perCount", mod[1]);
        s.addProperty("waves", mod[2]);
        s.addProperty("hits", (s.has("hits") ? s.get("hits").getAsDouble() : 1) * mod[1] * mod[2]);
    }

    /** The spell named last before {@code at} in {@code low}; null when none. */
    private static String lastSpellBefore(String low, List<String> spells, int at, Map<String, String> byLow) {
        String best = null;
        int where = -1;
        for (String s : spells) {
            int i = low.lastIndexOf(s, at - 1);
            if (i > where) {
                where = i;
                best = s;
            }
        }
        return best == null ? null : byLow.get(best);
    }

    /** "Every 0.4s, your Totem will heal": the interval of a sentence that heals, else 0 (once per cast). */
    private static double healEvery(String low) {
        for (String sentence : low.split("\\. ")) {
            Matcher ev = EVERY.matcher(sentence);
            if (HEAL_WORD.matcher(sentence).find() && ev.find()) {
                return Math.max(0.05, Double.parseDouble(ev.group(1)));
            }
        }
        return 0;
    }
}
