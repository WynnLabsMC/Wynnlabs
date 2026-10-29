package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.model.AbilityNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ability tree's text read for {@link TreeModel}: what each node does ({@link TreeModel.Read})
 * and the overlay a set of taken nodes makes of it.
 */
final class TreeReading {

    private static final Pattern COST_CHANGE = Pattern.compile("mana cost: ([+-]\\d+)");
    private static final Pattern DAMAGE = Pattern.compile("total damage: (\\d+)% \\(of your dps");
    private static final Pattern EVERY = Pattern.compile("every (\\d+(?:\\.\\d+)?)s");
    /** "(Max 3)", "(Max 5 Blooms)": how many; not a cap of a slow or a bleed ("(Max 60%)", "(Max 6s)"). */
    private static final Pattern MAX = Pattern.compile("\\(max (\\d+)(?![\\d.%s])");
    private static final Pattern SUMMON = Pattern.compile("summons? (a|an|\\d+) (\\w+)");
    private static final Pattern MORE = Pattern.compile("maximum (\\w+) by \\+(\\d+)");
    private static final Pattern MANA = Pattern.compile("(\\d+(?:\\.\\d+)?) mana per second");

    /** Hits per second a summon is counted with at most (a Hound's 0.25 s). */
    private static final double MAX_RATE = 4;

    private TreeReading() {}

    /** The spells rules.model.spells names (the class profile's), or the tree's spell nodes when it has none. */
    static List<String> spellNames(List<AbilityNode> tree, String cls) {
        List<String> out = new ArrayList<>();
        com.google.gson.JsonObject m = io.github.anilken.wynnlabs.core.Rules.get().getAsJsonObject("model");
        com.google.gson.JsonElement sp = m == null ? null : m.get("spells");
        if (sp != null && sp.isJsonArray()) {
            sp.getAsJsonArray().forEach(e -> {
                if (e.isJsonObject() && e.getAsJsonObject().has("name")) {
                    out.add(e.getAsJsonObject().get("name").getAsString());
                }
            });
        }
        if (out.isEmpty()) {
            for (AbilityNode n : tree) {
                if (TreeModel.spellNode(n, cls)) {
                    out.add(n.name());
                }
            }
        }
        return out;
    }

    /** Singular, lower case: "Puppets" → "puppet". */
    private static String noun(String w) {
        String n = w.toLowerCase(Locale.ROOT);
        return n.endsWith("s") && n.length() > 3 ? n.substring(0, n.length() - 1) : n;
    }

    /** Reads every node of the tree for the class's spells ({@code spellNames}, as rules.model.spells has them). */
    static List<TreeModel.Read> read(List<AbilityNode> tree, String cls, Collection<String> spellNames) {
        List<String> spells = new ArrayList<>();
        spellNames.forEach(s -> spells.add(s.toLowerCase(Locale.ROOT)));
        // a node that names no spell may name another node that does: Mana Trap's "Traps" are
        // Basaltic Trap's, which comes from Arrow Bomb. Last words of such nodes, first one wins
        Map<String, String> viaNode = new TreeMap<>();
        for (AbilityNode n : tree) {
            if (spells.contains(n.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            String s = firstSpell(TreeModel.text(n).toLowerCase(Locale.ROOT), spells, Map.of());
            String[] words = n.name().split(" ");
            String last = noun(words[words.length - 1]);
            if (s != null && last.length() >= 4 && !spells.contains(last)) {
                viaNode.putIfAbsent(last, s);
            }
        }
        List<TreeModel.Read> out = new ArrayList<>();
        for (AbilityNode n : tree) {
            String t = TreeModel.text(n);
            String low = t.toLowerCase(Locale.ROOT);
            // a spell's own damage is never passive here: the Totem's ticks are its rules.model.spells
            // entry's (a lasting spell, counted with the summons there), so they are counted once
            boolean spell = TreeModel.spellNode(n, cls) || spells.contains(n.name().toLowerCase(Locale.ROOT));
            boolean ultimate = n.type() != null && n.type().contains("ULTIMATE");
            String costSpell = null;
            double costDelta = 0;
            Matcher cm = COST_CHANGE.matcher(low);
            if (!spell && cm.find()) {
                costSpell = firstSpell(low, spells, viaNode);
                costDelta = Integer.parseInt(cm.group(1).replace("+", ""));
            }
            double[] conv = null;
            double rate = 0;
            int count = 1;
            String noun = null;
            boolean summon = false;
            Matcher dm = DAMAGE.matcher(low);
            Matcher every = EVERY.matcher(low);
            if (!spell && !ultimate && dm.find()) {
                // hits on its own clock: the shortest "every" (a Puppet comes every 3 s, throws every 0.5 s)
                double interval = Double.MAX_VALUE;
                while (every.find()) {
                    interval = Math.min(interval, Double.parseDouble(every.group(1)));
                }
                if (interval < Double.MAX_VALUE && interval > 0) {
                    conv = firstBlock(t, low);
                    rate = Math.min(MAX_RATE, 1 / interval);
                    // how many: "(Max 3)", else "summon 2 …", else one
                    Matcher sm = SUMMON.matcher(low);
                    summon = sm.find();
                    noun = summon ? noun(sm.group(2)) : null;
                    Matcher mx = MAX.matcher(low);
                    count = mx.find() ? Integer.parseInt(mx.group(1))
                            : summon && Character.isDigit(sm.group(1).charAt(0)) ? Integer.parseInt(sm.group(1)) : 1;
                }
            }
            Map<String, Integer> more = new TreeMap<>();
            Matcher mm = MORE.matcher(low);
            while (mm.find()) {
                more.merge(noun(mm.group(1)), Integer.parseInt(mm.group(2)), Integer::sum);
            }
            double mana = 0;
            Matcher mp = MANA.matcher(low);
            if (mp.find()) {
                mana = Double.parseDouble(mp.group(1));
            }
            out.add(new TreeModel.Read(n.name(), n.archetype() == null ? "" : n.archetype(), costSpell, costDelta, conv, rate,
                    count, noun, summon, more, mana));
        }
        return out;
    }

    /**
     * The conversions per hit of a node's first damage block ("Total Damage: 20% (of your DPS)
     * ( Damage: 16%) ( Air: 2%)"), in {@link Damage#ELEMENTS} order; null when it has none. A
     * second block is another part (an explosion) and is left out.
     */
    static double[] firstBlock(String t, String low) {
        Matcher dm = DAMAGE.matcher(low);
        if (!dm.find()) {
            return null;
        }
        int second = low.indexOf("total damage", dm.end());
        String block = t.substring(dm.start(), second < 0 ? t.length() : second);
        double[] conv = new double[Damage.ELEMENTS.length];
        Matcher p = ClassProfiles.PART.matcher(block);
        while (p.find()) {
            String el = p.group(1).equals("Damage") ? "neutral" : p.group(1).toLowerCase(Locale.ROOT);
            int i = List.of(Damage.ELEMENTS).indexOf(el);
            if (i >= 0) {
                conv[i] += Integer.parseInt(p.group(2)) / 100.0;
            }
        }
        return conv;
    }

    /** The spell named first in {@code low} (or named through another node's last word); null when none. */
    static String firstSpell(String low, List<String> spells, Map<String, String> viaNode) {
        String best = null;
        int at = Integer.MAX_VALUE;
        for (String s : spells) {
            int i = low.indexOf(s);
            if (i >= 0 && i < at) {
                at = i;
                best = s;
            }
        }
        if (best == null) {
            for (var e : viaNode.entrySet()) {
                int i = low.indexOf(e.getKey());
                if (i >= 0 && i < at) {
                    at = i;
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    /** The overlay for the nodes in {@code taken} ({@link TreeModel#overlay}), with the tree's passive damage bonuses. */
    static TreeModel.Overlay overlay(int level, List<TreeModel.Read> reads, Collection<String> spellNames, Collection<String> taken,
            double mainAttack, double passiveCap, Map<String, TreeBonuses.Bonus> bonuses) {
        return overlay(level, reads, spellNames, taken, mainAttack, passiveCap, bonuses, SourceText.Reading.none());
    }

    /**
     * {@link #overlay} with what else the tree's text says ({@link SourceText#read}): each summon
     * and area on its own, the spells' waves, per-Totem casts and damage cuts, the heals and the
     * ultimates. {@code passive} stays the element mix of the summons and areas as the score
     * weighs them (capped at {@code passiveCap}), for the class profile's element scores; the
     * damage itself is worked out source by source ({@link Sources}), uncapped, and those sources
     * are in the overlay's key.
     */
    static TreeModel.Overlay overlay(int level, List<TreeModel.Read> reads, Collection<String> spellNames, Collection<String> taken,
            double mainAttack, double passiveCap, Map<String, TreeBonuses.Bonus> bonuses, SourceText.Reading text) {
        TreeBonuses.Sum bonus = TreeBonuses.Sum.of(bonuses, taken);
        Set<String> have = new HashSet<>(taken);
        Map<String, String> byLow = new TreeMap<>();
        spellNames.forEach(s -> byLow.put(s.toLowerCase(Locale.ROOT), s));
        Set<String> known = new LinkedHashSet<>();
        Set<String> unlocked = new LinkedHashSet<>();
        for (TreeModel.Read r : reads) {
            if (byLow.containsKey(r.name().toLowerCase(Locale.ROOT))) {
                known.add(r.name());
                if (have.contains(r.name())) {
                    unlocked.add(r.name());
                }
            }
        }
        Map<String, Double> cost = new TreeMap<>();
        Map<String, Integer> more = new TreeMap<>();
        double mana = 0;
        for (TreeModel.Read r : reads) {
            if (!have.contains(r.name())) {
                continue;
            }
            if (r.costSpell() != null && byLow.containsKey(r.costSpell())) {
                cost.merge(byLow.get(r.costSpell()), r.costDelta(), Double::sum);
            }
            r.more().forEach((k, v) -> more.merge(k, v, Integer::sum));
            mana += r.manaPerSecond();
        }
        double[] passive = new double[Damage.ELEMENTS.length];
        List<Sources.Passive> sources = new ArrayList<>();
        for (TreeModel.Read r : reads) {
            if (!have.contains(r.name()) || r.conv() == null) {
                continue;
            }
            int count = r.count() + (r.noun() == null ? 0 : more.getOrDefault(r.noun(), 0));
            double mult = 1;
            // "Each Totem will summon its own Effigy … 0.8x": one per Totem, weaker past the first
            SourceText.Each each = text.each().get(r.name());
            if (each != null) {
                count = Math.max(1, count) * (1 + more.getOrDefault(each.per(), 0));
                mult = Math.pow(each.decay(), count - 1);
            }
            double share = Sources.fightShare(r.summon());
            double times = r.rate() * count * share;
            // a node that makes this summon's hits stronger ("Your Puppet's knives become stronger")
            double[] add = r.summon() && r.noun() != null ? bonus.summons().get(r.noun()) : null;
            double[] perHit = new double[passive.length];
            for (int i = 0; i < passive.length; i++) {
                perHit[i] = r.conv()[i] + (add == null ? 0 : add[i]);
                passive[i] += perHit[i] * times * mult;
            }
            sources.add(new Sources.Passive(r.name(), r.noun(), r.summon(), text.spellOf().get(r.name()), perHit, r.rate(),
                    count, mult, share));
        }
        double total = 0;
        for (double p : passive) {
            total += p;
        }
        for (int i = 0; i < passive.length; i++) {
            passive[i] = Math.round((passiveCap > 0 && total > passiveCap ? passive[i] * passiveCap / total : passive[i])
                    * 10000) / 10000.0;
        }
        // the spells: damage cuts ("Totem and Aura will do 50% less damage"), more waves, once per Totem
        Map<String, double[]> spellMods = new TreeMap<>();
        for (String s : known) {
            String low = s.toLowerCase(Locale.ROOT);
            int[] waves = {0};
            double f = SourceText.factor(text.scales(), have, low, false, waves);
            String noun = more.containsKey(low) ? low : text.perNoun().get(s);
            int per = noun == null ? 1 : 1 + more.getOrDefault(noun, 0);
            if (f != 1 || waves[0] > 0 || per > 1) {
                spellMods.put(s, new double[] {f, per, 1 + waves[0]});
            }
        }
        // the heals of the nodes taken (a heal of a spell not bought yet waits for it), cut by what
        // cuts them and once per Totem when the spell goes off once per Totem
        List<SourceText.Heal> heals = new ArrayList<>();
        for (SourceText.Heal h : text.heals()) {
            if (have.contains(h.name()) && (h.spell() == null || unlocked.contains(h.spell()) || !known.contains(h.spell()))) {
                double f = SourceText.factor(text.scales(), have, h.name().toLowerCase(Locale.ROOT), true, null);
                double[] mod = h.spell() == null ? null : spellMods.get(h.spell());
                double per = mod == null ? 1 : mod[1];
                heals.add(h.times(f * per));
            }
        }
        List<SourceText.Ultimate> ultimates = new ArrayList<>();
        for (SourceText.Ultimate u : text.ultimates()) {
            if (have.contains(u.name())) {
                ultimates.add(u);
            }
        }
        // "+5% Base Resistance": each node takes its share off every hit you take (WynnBuilder's defMult)
        double hitTaken = 1;
        for (var e : text.resist().entrySet()) {
            if (have.contains(e.getKey())) {
                hitTaken *= 1 - e.getValue();
            }
        }
        return new TreeModel.Overlay(level, cost, known, unlocked, passive, mana * 5, mainAttack, bonus, sources, heals,
                ultimates, spellMods, hitTaken);
    }
}
