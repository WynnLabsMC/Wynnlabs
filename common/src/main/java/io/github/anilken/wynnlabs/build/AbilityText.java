package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.model.AbilityNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The damage of ability nodes that are not spells and do not hit on their own clock, read from
 * their text: a hit that comes with a spell ("Hitting mobs with Charge will … knock them away.
 * Total Damage: 400%", Flying Kick), one that comes with another such node (Collide, "Enemies
 * thrown into walls from Flying Kick will explode"), one that replaces the spell's own ("Makes
 * Multihit a single devastating hit", Backstab), a burn over a few seconds ("dealing damage every
 * 0.6 seconds … Duration: 3s", Flaming Uppercut) and an extra on the main attack ("Total Damage:
 * 200% (Main Attack Damage)", Pyrokinesis). Summons and areas that hit every so often are
 * {@link TreeReading}'s; spells' own numbers their rules entry's; "+N%" bonuses {@link TreeBonuses}'.
 * What the text cannot say (how often a trigger happens, how many orbs are out) is in the shipped
 * table {@link AbilityMechanics} reads.
 */
final class AbilityText {

    private AbilityText() {}

    /**
     * One node's own damage: {@code spell} the spell it comes with (null: none known), {@code main}
     * on each main attack hit instead; {@code conv} the whole of it per cast or per hit (every hit,
     * tick and piece in), {@code hits} how many, {@code replace} in place of the spell's own damage.
     */
    record Extra(String node, String spell, boolean main, double[] conv, double hits, boolean replace) {

        Extra with(String spell, boolean main, double count, boolean replace) {
            double[] c = conv.clone();
            double f = count / Math.max(1e-9, hits);
            for (int i = 0; i < c.length; i++) {
                c[i] *= f;
            }
            return new Extra(node, spell, main, c, count, replace);
        }

        Extra times(double f) {
            double[] c = conv.clone();
            for (int i = 0; i < c.length; i++) {
                c[i] *= f;
            }
            return new Extra(node, spell, main, c, hits, replace);
        }
    }

    /** "Total Damage: 400% (of your DPS…)", "Total Damage: 200% (Main Attack Damage)" or "Total Damage: 75% ( Damage…": not a "+" bonus. */
    private static final Pattern BLOCK = Pattern.compile("Total Damage: (\\d+)% \\((of your DPS[^)]*|Main Attack Damage)?\\)?");
    /** A summon or area on its own clock ({@link TreeReading}'s). */
    private static final Pattern CLOCK = Pattern.compile("every (\\d+(?:\\.\\d+)?)s");
    /** A burn: "every 0.6 seconds" with a "Duration: 3s". */
    private static final Pattern BURN = Pattern.compile("every (\\d+(?:\\.\\d+)?) seconds?");
    private static final Pattern DURATION = Pattern.compile("duration: (\\d+(?:\\.\\d+)?)s");
    private static final Pattern PER = Pattern.compile("per ([a-z]+)");
    private static final Pattern HIT_BY = Pattern.compile("hit by (one|two|three|four|five|\\d+) ");
    private static final Pattern TIMES = Pattern.compile("(\\d+) times per second");
    private static final Pattern REPLACE = Pattern.compile("\\binstead\\b|\\bmakes [a-z ]+ a single\\b|\\bcondense [a-z ]+ into\\b");
    private static final List<String> WORDS = List.of("one", "two", "three", "four", "five");

    /** Hits one extra is counted with at most ("75 piercing arrows" mostly miss one target). */
    static final double MAX_HITS = 15;

    /** Reads every node of the tree for the class's spells ({@code spellNames}, as rules.model.spells has them), by node name. */
    static Map<String, Extra> read(List<AbilityNode> tree, String cls, Collection<String> spellNames) {
        List<String> spells = new ArrayList<>();
        Map<String, String> byLow = new TreeMap<>();
        for (String s : spellNames) {
            spells.add(s.toLowerCase(Locale.ROOT));
            byLow.put(s.toLowerCase(Locale.ROOT), s);
        }
        // the spell each non-spell node comes with: named in its text, or through a node it names
        Map<String, String> via = new LinkedHashMap<>();
        Map<String, String> texts = new LinkedHashMap<>();
        for (AbilityNode n : tree) {
            texts.put(n.name(), TreeModel.text(n).toLowerCase(Locale.ROOT));
        }
        for (int round = 0; round < 3; round++) {
            for (AbilityNode n : tree) {
                if (via.containsKey(n.name()) || spells.contains(n.name().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String low = texts.get(n.name());
                String s = TreeReading.firstSpell(low, spells, Map.of());
                if (s == null) {
                    int at = Integer.MAX_VALUE;
                    for (var e : via.entrySet()) {
                        int i = low.indexOf(e.getKey().toLowerCase(Locale.ROOT));
                        if (i >= 0 && i < at && !e.getKey().equals(n.name())) {
                            at = i;
                            s = e.getValue();
                        }
                    }
                }
                if (s != null) {
                    via.put(n.name(), s);
                }
            }
        }
        Map<String, Extra> out = new LinkedHashMap<>();
        for (AbilityNode n : tree) {
            boolean ultimate = n.type() != null && n.type().contains("ULTIMATE");
            if (ultimate || TreeModel.spellNode(n, cls) || byLow.containsKey(n.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Extra e = extra(n.name(), TreeModel.text(n), via.get(n.name()));
            if (e != null) {
                out.put(n.name(), e.spell() == null ? e : new Extra(e.node(), byLow.getOrDefault(e.spell(), e.spell()),
                        e.main(), e.conv(), e.hits(), e.replace()));
            }
        }
        return out;
    }

    /** One node's text read for its own damage; null when it has none this reads. */
    static Extra extra(String name, String t, String spell) {
        String low = t.toLowerCase(Locale.ROOT);
        Matcher b = BLOCK.matcher(t);
        if (!b.find() || CLOCK.matcher(low).find()) {
            return null;                                // none, or a summon or area on its own clock
        }
        String q = b.group(2) == null ? "" : b.group(2).toLowerCase(Locale.ROOT);
        boolean main = q.contains("main attack");
        int next = t.indexOf("Total Damage", b.end());
        double[] conv = parts(t.substring(b.start() + "Total Damage:".length(), next < 0 ? t.length() : next));
        double sum = 0;
        for (double v : conv) {
            sum += v;
        }
        if (sum <= 0) {
            return null;
        }
        double hits = 1;
        Matcher burn = BURN.matcher(low);
        Matcher dur = DURATION.matcher(low);
        Matcher times = TIMES.matcher(low);
        if (burn.find() && dur.find()) {
            hits = Double.parseDouble(dur.group(1)) / Math.max(0.05, Double.parseDouble(burn.group(1)));
        } else if (times.find() && dur.find()) {
            hits = Integer.parseInt(times.group(1)) * Double.parseDouble(dur.group(1));
        } else {
            Matcher per = PER.matcher(q);
            if (per.find()) {
                hits = count(low, per.group(1));
            }
        }
        Matcher hb = HIT_BY.matcher(low);
        if (hb.find()) {
            String w = hb.group(1);
            hits = Math.min(hits, Character.isDigit(w.charAt(0)) ? Integer.parseInt(w) : WORDS.indexOf(w) + 1);
        }
        hits = Math.max(1, Math.min(MAX_HITS, hits));
        for (int i = 0; i < conv.length; i++) {
            conv[i] *= hits;
        }
        boolean replace = spell != null && REPLACE.matcher(low).find();
        return new Extra(name, main ? null : spell, main, conv, hits, replace);
    }

    /** How many of {@code noun} the text gives ("fling 25 shrapnel", "deal 3 strikes", "+3 shurikens"); 1 when none. */
    private static double count(String low, String noun) {
        String stem = noun.endsWith("s") ? noun.substring(0, noun.length() - 1) : noun;
        Matcher m = Pattern.compile("\\+?(\\d+) (?:[a-z]+ )?" + Pattern.quote(stem) + "(?:e?s)?\\b").matcher(low);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** The "( Earth: 20%)" parts of a damage block as fractions, neutral first. */
    private static double[] parts(String block) {
        double[] c = new double[Damage.ELEMENTS.length];
        Matcher p = ClassProfiles.PART.matcher(block);
        while (p.find()) {
            String el = p.group(1).equals("Damage") ? "neutral" : p.group(1).toLowerCase(Locale.ROOT);
            int i = List.of(Damage.ELEMENTS).indexOf(el);
            if (i >= 0) {
                c[i] += Integer.parseInt(p.group(2)) / 100.0;
            }
        }
        return c;
    }
}
