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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The major IDs a build wears, as the score counts them. What each one does is in the shipped
 * table {@code assets/wynnlabs/build/major-ids.json} (its {@code _doc} lists the fields): a
 * build-wide factor on spell, main attack or summon damage, on the spells' mana cost or on max
 * health, a change to one spell, summon or area the model scores, or a new summon. A piece carries
 * its major ID as the pseudo-stat {@link #KEY}{@code <name>}; two copies of one count once,
 * different ones add up. An effect that needs a tree node ("with Fireworks") counts only while
 * the tree overlay in force has that node.
 */
public final class MajorIds {

    /** Pseudo-stat on a piece: "_major:Sorcery" = 1. */
    public static final String KEY = "_major:";

    private MajorIds() {}

    /** One spell, summon or area's change: per hit, once a cast, in place of its hits, times, and a lasting spell's clock. */
    record Target(double[] add, double[] cast, double[] set, double hits, double mult, double duration, double ticks,
            String requires) {}

    /** A summon a major ID adds: per hit, hits a second, how many, and main attack % each. */
    record Summon(String name, double[] conv, double rate, int count, double mainAttack) {}

    /** One major ID's row of the table. */
    record Effect(String name, String status, String requires, double spell, double main, double passive, double cost,
            double health, double madness, Map<String, Target> targets, Summon summon) {}

    /**
     * The major IDs of one build together: the factors multiplied, the changes per target in
     * the order of the table's names, the summons and the random spell's clock (0: none).
     */
    record Active(List<String> names, double spell, double main, double passive, double cost, double health,
            double madness, Map<String, List<Target>> targets, List<Summon> summons) {

        static final Active NONE = new Active(List.of(), 1, 1, 1, 1, 1, 0, Map.of(), List.of());

        boolean isEmpty() {
            return names.isEmpty();
        }

        /** The changes to the spell, summon or area {@code name}; null when none. */
        List<Target> of(String name) {
            return targets.isEmpty() ? null : targets.get(name);
        }
    }

    private static volatile Map<String, Effect> table;
    /** Active by the names worn and the nodes they need that the tree has. */
    private static final Map<String, Active> MEMO = new ConcurrentHashMap<>();

    /** The table, read once. */
    static Map<String, Effect> table() {
        Map<String, Effect> t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return t;
    }

    /** The tree nodes some major ID's effect needs: an overlay's cache key names those it has. */
    static Set<String> requiredNodes() {
        Set<String> out = new TreeSet<>();
        for (Effect e : table().values()) {
            if (e.requires() != null) {
                out.add(e.requires());
            }
            e.targets().values().forEach(t -> {
                if (t.requires() != null) {
                    out.add(t.requires());
                }
            });
        }
        return out;
    }

    /** The major IDs in a build's stats ({@link Damage#totals}) with what they do under the tree {@code taken} (null: no tree). */
    static Active of(Map<String, Double> s, Collection<String> taken) {
        List<String> names = null;
        for (var e : s.entrySet()) {
            if (e.getKey().startsWith(KEY) && e.getValue() > 0) {
                if (names == null) {
                    names = new ArrayList<>(2);
                }
                names.add(e.getKey().substring(KEY.length()));
            }
        }
        if (names == null) {
            return Active.NONE;
        }
        names.sort(null);
        Map<String, Effect> t = table();
        StringBuilder k = new StringBuilder(String.join("|", names)).append('#');
        for (String n : names) {
            Effect e = t.get(n);
            if (e != null) {
                for (String r : needs(e)) {
                    k.append(taken != null && taken.contains(r) ? '1' : '0');
                }
            }
        }
        final List<String> worn = names;
        return MEMO.computeIfAbsent(k.toString(), key -> combine(worn, taken));
    }

    private static List<String> needs(Effect e) {
        List<String> out = new ArrayList<>();
        if (e.requires() != null) {
            out.add(e.requires());
        }
        e.targets().values().forEach(t -> {
            if (t.requires() != null) {
                out.add(t.requires());
            }
        });
        return out;
    }

    private static Active combine(List<String> names, Collection<String> taken) {
        double spell = 1;
        double main = 1;
        double passive = 1;
        double cost = 1;
        double health = 1;
        double madness = 0;
        Map<String, List<Target>> targets = new HashMap<>();
        List<Summon> summons = new ArrayList<>();
        for (String n : names) {
            Effect e = table().get(n);
            if (e == null || (e.requires() != null && (taken == null || !taken.contains(e.requires())))) {
                continue;
            }
            spell *= e.spell();
            main *= e.main();
            passive *= e.passive();
            cost *= e.cost();
            health *= e.health();
            if (e.madness() > 0) {
                madness = madness == 0 ? e.madness() : Math.min(madness, e.madness());
            }
            e.targets().forEach((name, t) -> {
                if (t.requires() == null || (taken != null && taken.contains(t.requires()))) {
                    targets.computeIfAbsent(name, x -> new ArrayList<>()).add(t);
                }
            });
            if (e.summon() != null) {
                summons.add(e.summon());
            }
        }
        return new Active(List.copyOf(names), spell, main, passive, cost, health, madness, Map.copyOf(targets),
                List.copyOf(summons));
    }

    /**
     * A spell's conversion for the whole cast ({@code c}, of {@code hits} hits) with these changes:
     * {@code set} replaces each hit, {@code add} joins each hit, {@code cast} the cast once, then
     * {@code mult}; a lasting spell's {@code ticks} more hits. Returns the hits it ends with.
     */
    static double apply(List<Target> ts, double[] c, double hits) {
        double h = Math.max(1, hits);
        for (Target t : ts) {
            if (t.set() != null) {
                h = t.hits() > 0 ? t.hits() : h;
                for (int i = 0; i < c.length; i++) {
                    c[i] = t.set()[i] * h;
                }
            }
            double ticks = t.ticks();
            for (int i = 0; i < c.length; i++) {
                c[i] = ((c[i] + (t.add() == null ? 0 : t.add()[i] * h)) * ticks + (t.cast() == null ? 0 : t.cast()[i]))
                        * t.mult();
            }
            h *= ticks;
        }
        return h;
    }

    /** The factor on a lasting spell's duration (Totem: Furious Effigy 0.5, Peaceful Effigy 2). */
    static double duration(List<Target> ts) {
        double d = 1;
        for (Target t : ts) {
            d *= t.duration();
        }
        return d;
    }

    // ---- the table ----------------------------------------------------------------------------

    private static Map<String, Effect> load() {
        Map<String, Effect> out = new HashMap<>();
        try (InputStream in = MajorIds.class.getResourceAsStream("/assets/wynnlabs/build/major-ids.json")) {
            if (in != null) {
                JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : doc.getAsJsonObject("majorIds").entrySet()) {
                    out.put(e.getKey(), effect(e.getKey(), e.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the major ID table: " + e);
        }
        return Map.copyOf(out);
    }

    static Effect effect(String name, JsonObject o) {
        Map<String, Target> targets = new HashMap<>();
        if (o.has("targets")) {
            for (var t : o.getAsJsonObject("targets").entrySet()) {
                JsonObject j = t.getValue().getAsJsonObject();
                targets.put(t.getKey(), new Target(conv(j, "add"), conv(j, "cast"), conv(j, "set"), num(j, "hits", 0),
                        num(j, "mult", 1), num(j, "duration", 1), num(j, "ticks", 1), text(j, "requires")));
            }
        }
        Summon summon = null;
        if (o.has("summon")) {
            JsonObject j = o.getAsJsonObject("summon");
            summon = new Summon(text(j, "name"), conv(j, "conv"), num(j, "rate", 1), (int) num(j, "count", 1),
                    num(j, "mainAttack", 0));
        }
        return new Effect(name, text(o, "status"), text(o, "requires"), num(o, "spell", 1), num(o, "main", 1),
                num(o, "passive", 1), num(o, "cost", 1), num(o, "health", 1), num(o, "madness", 0), Map.copyOf(targets),
                summon);
    }

    private static double[] conv(JsonObject o, String key) {
        if (!o.has(key)) {
            return null;
        }
        JsonObject c = o.getAsJsonObject(key);
        double[] out = new double[Damage.ELEMENTS.length];
        for (int i = 0; i < out.length; i++) {
            JsonElement v = c.get(Damage.ELEMENTS[i]);
            out[i] = v == null ? 0 : v.getAsDouble();
        }
        return out;
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
