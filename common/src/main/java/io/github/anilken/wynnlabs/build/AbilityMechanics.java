package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The ability tree's damage beyond spells, summons and "+N%" bonuses, as the score counts it at
 * one level: the nodes' own damage {@link AbilityText} reads (with a spell, on the main attack, in
 * place of a spell), and what the text cannot say, from the shipped table
 * {@code assets/wynnlabs/build/ability-mechanics.json} (its {@code _doc} lists the fields): how
 * often a trigger fires, how many orbs or clones are out, and the archetypes' stacking mechanics
 * (Marks, Corrupted, Distortion, Harmony…) as a steady state, each with the assumption it makes.
 */
final class AbilityMechanics {

    private AbilityMechanics() {}

    /** One node's row of the table. */
    record Row(String target, double count, double rate, double every, double mult, String replace, boolean skip,
            Map<String, Double> addCount, Map<String, Double> scale, double damage, double spell, double main, double raw,
            double cost, double manaPer5, Map<String, Double> spells, Map<String, Double> cooldown) {}

    /**
     * What the nodes taken add up to: per spell its extras (whole, per cast) and whether its own
     * damage goes, the main attack's extras per hit, the summons and the triggers on a clock as
     * {@link Sources.Passive}, the factors on all damage, spells and the main attack, raw damage on
     * every hit, the factor on the spells' mana, mana every 5 s, per-spell factors and the shortest
     * time between two casts of a spell.
     */
    record Sum(Map<String, List<AbilityText.Extra>> spellExtras, Set<String> replaced, List<AbilityText.Extra> main,
            List<Sources.Passive> passives, double damage, double spell, double mainFactor, double raw, double cost,
            double manaPer5, Map<String, Double> spells, Map<String, Double> cooldown) {

        static final Sum NONE = new Sum(Map.of(), Set.of(), List.of(), List.of(), 1, 1, 1, 0, 1, 0, Map.of(), Map.of());

        boolean isEmpty() {
            return this == NONE || spellExtras.isEmpty() && replaced.isEmpty() && main.isEmpty() && passives.isEmpty()
                    && damage == 1 && spell == 1 && mainFactor == 1 && raw == 0 && cost == 1 && manaPer5 == 0
                    && spells.isEmpty() && cooldown.isEmpty();
        }

        /** The extras of {@code spell}; empty when none. */
        List<AbilityText.Extra> of(String spell) {
            List<AbilityText.Extra> l = spellExtras.get(spell);
            return l == null ? List.of() : l;
        }

        /** What the sum is, for cache keys. */
        String key() {
            StringBuilder k = new StringBuilder();
            spellExtras.forEach((s, l) -> {
                k.append(s).append(':');
                l.forEach(e -> conv(k.append(e.node()).append('='), e.conv()).append(';'));
            });
            k.append("|r").append(new java.util.TreeSet<>(replaced)).append("|m");
            main.forEach(e -> conv(k.append(e.node()).append('='), e.conv()).append(';'));
            k.append("|p");
            passives.forEach(p -> conv(k.append(p.name()).append(',').append(Math.round(p.rate() * 1000)).append(',')
                    .append(p.count()).append(','), p.conv()).append(';'));
            k.append('|').append(Math.round(damage * 1000)).append(',').append(Math.round(spell * 1000)).append(',')
                    .append(Math.round(mainFactor * 1000)).append(',').append(Math.round(raw * 10)).append(',')
                    .append(Math.round(cost * 1000)).append(',').append(Math.round(manaPer5 * 10)).append(spells)
                    .append(cooldown);
            return k.toString();
        }

        private static StringBuilder conv(StringBuilder k, double[] c) {
            for (double v : c) {
                k.append(Math.round(v * 1000)).append(',');
            }
            return k;
        }
    }

    private static volatile Map<String, Row> table;

    static Map<String, Row> table() {
        Map<String, Row> t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return t;
    }

    /** The sum for the nodes in {@code have}, from the tree's {@code extras} ({@link AbilityText#read}) and the table. */
    static Sum of(Map<String, AbilityText.Extra> extras, Collection<String> have) {
        if (io.github.anilken.wynnlabs.core.Rules.sub("model", "abilityMechanics", 1.0) <= 0) {
            return Sum.NONE;                            // off (rules.model.abilityMechanics 0): as before
        }
        Map<String, Row> t = table();
        Set<String> taken = new HashSet<>(have);
        Map<String, Double> addCount = new HashMap<>();
        Map<String, Double> scale = new HashMap<>();
        for (String n : taken) {
            Row r = t.get(n);
            if (r != null) {
                r.addCount().forEach((k, v) -> addCount.merge(k, v, Double::sum));
                r.scale().forEach((k, v) -> scale.merge(k, v, (a, b) -> a * b));
            }
        }
        Map<String, List<AbilityText.Extra>> spellExtras = new TreeMap<>();
        Set<String> replaced = new java.util.TreeSet<>();
        List<AbilityText.Extra> main = new ArrayList<>();
        List<Sources.Passive> passives = new ArrayList<>();
        double damage = 1, spell = 1, mainF = 1, raw = 0, cost = 1, mana = 0;
        Map<String, Double> spells = new TreeMap<>();
        Map<String, Double> cooldown = new TreeMap<>();
        // in the tree's order: the same answer whatever order the nodes were taken in
        List<String> names = new ArrayList<>(extras.keySet());
        for (String n : t.keySet()) {
            if (!extras.containsKey(n)) {
                names.add(n);
            }
        }
        for (String n : names) {
            if (!taken.contains(n)) {
                continue;
            }
            Row r = t.get(n);
            AbilityText.Extra e = extras.get(n);
            if (r != null) {
                damage *= r.damage();
                spell *= r.spell();
                mainF *= r.main();
                raw += r.raw();
                cost *= r.cost();
                mana += r.manaPer5();
                r.spells().forEach((k, v) -> spells.merge(k, v, (a, b) -> a * b));
                r.cooldown().forEach((k, v) -> cooldown.merge(k, v, Math::max));
                if (r.replace() != null) {
                    replaced.add(r.replace());
                }
                if (r.skip()) {
                    continue;
                }
            }
            if (e == null) {
                continue;
            }
            double count = (r != null && r.count() > 0 ? r.count() : e.hits()) + addCount.getOrDefault(n, 0.0);
            double f = (r == null ? 1 : r.mult()) * scale.getOrDefault(n, 1.0);
            String target = r != null && r.target() != null ? r.target() : e.main() ? "main" : e.spell();
            if (target == null) {
                continue;                               // nothing to hang it on and no clock for it
            }
            switch (target) {
                case "main" -> main.add(e.with(null, true, count, false).times(f));
                // a summon: each one's hit, {rate} a second, {count} of them, out a share of the fight
                case "summon" -> passives.add(new Sources.Passive(n, null, true, e.spell(),
                        e.with(null, false, 1, false).times(f).conv(), Math.max(0, r.rate()),
                        (int) Math.max(1, Math.round(count)), 1, Sources.fightShare(true)));
                // a trigger on a clock: the whole of it once every {every} seconds of a fight
                case "periodic" -> passives.add(new Sources.Passive(n, null, false, e.spell(), e.times(f).conv(),
                        1 / Math.max(0.5, r.every()), 1, 1, 1));
                case "none" -> { }
                default -> {
                    AbilityText.Extra x = e.with(target, false, count, e.replace() || r != null && target.equals(r.replace()))
                            .times(f);
                    spellExtras.computeIfAbsent(target, k -> new ArrayList<>()).add(x);
                    if (x.replace()) {
                        replaced.add(target);
                    }
                }
            }
        }
        if (spellExtras.isEmpty() && replaced.isEmpty() && main.isEmpty() && passives.isEmpty() && damage == 1
                && spell == 1 && mainF == 1 && raw == 0 && cost == 1 && mana == 0 && spells.isEmpty() && cooldown.isEmpty()) {
            return Sum.NONE;
        }
        return new Sum(spellExtras, replaced, main, passives, damage, spell, mainF, raw, cost, mana, spells, cooldown);
    }

    /**
     * {@code c} (a spell's conversion for the whole cast, {@code hits} hits) with the extras of
     * {@code spell} added, each changed by the major IDs worn ({@code major}: Tackle on Collide).
     * Returns the hits it ends with.
     */
    static double addExtras(Sum sum, String spell, double[] c, double hits, MajorIds.Active major) {
        double h = hits;
        for (AbilityText.Extra e : sum.of(spell)) {
            double[] x = e.conv();
            List<MajorIds.Target> fx = major.of(e.node());
            if (fx != null) {
                x = x.clone();
                MajorIds.apply(fx, x, e.hits());
            }
            for (int i = 0; i < c.length; i++) {
                c[i] += x[i];
            }
            h += e.hits();
        }
        return h;
    }

    /** The main attack's extras per hit ({@code mc}, already split by the weapon's elements) with the major IDs' changes. */
    static void addMain(Sum sum, double[] mc, java.util.Map<String, Double> shares, MajorIds.Active major) {
        for (AbilityText.Extra e : sum.main()) {
            double[] x = e.conv();
            List<MajorIds.Target> fx = major.of(e.node());
            if (fx != null) {
                x = x.clone();
                MajorIds.apply(fx, x, e.hits());
            }
            double[] s = Damage.split(x, shares);
            for (int i = 0; i < mc.length; i++) {
                mc[i] += s[i];
            }
        }
    }

    // ---- the table ----------------------------------------------------------------------------

    private static Map<String, Row> load() {
        Map<String, Row> out = new java.util.LinkedHashMap<>();
        try (InputStream in = AbilityMechanics.class.getResourceAsStream("/assets/wynnlabs/build/ability-mechanics.json")) {
            if (in != null) {
                JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var cls : doc.getAsJsonObject("nodes").entrySet()) {
                    for (var e : cls.getValue().getAsJsonObject().entrySet()) {
                        out.put(e.getKey(), row(e.getValue().getAsJsonObject()));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the ability mechanics table: " + e);
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    static Row row(JsonObject o) {
        return new Row(text(o, "target"), num(o, "count", 0), num(o, "rate", 1), num(o, "every", 10), num(o, "mult", 1),
                text(o, "replace"), o.has("skip") && o.get("skip").getAsBoolean(), map(o, "addCount"), map(o, "scale"),
                num(o, "damage", 1), num(o, "spell", 1), num(o, "main", 1), num(o, "raw", 0), num(o, "cost", 1),
                num(o, "manaPer5", 0), map(o, "spells"), map(o, "cooldown"));
    }

    private static Map<String, Double> map(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            return Map.of();
        }
        Map<String, Double> m = new TreeMap<>();
        for (var e : o.getAsJsonObject(key).entrySet()) {
            m.put(e.getKey(), e.getValue().getAsDouble());
        }
        return m;
    }

    private static double num(JsonObject o, String key, double fallback) {
        JsonElement v = o.get(key);
        return v == null || v.isJsonNull() ? fallback : v.getAsDouble();
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
