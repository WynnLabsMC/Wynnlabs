package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.coach.Coach;
import io.github.anilken.wynnlabs.core.Rules;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How much each of the class's spells and the main attack weigh in your damage, for the tree
 * plan ({@link TreePlanner}): a node that adds to a spell is worth as much as that spell is to you.
 * The spells' shares come from the damage the coach measured after each cast (casts × damage per
 * cast) once there are {@link #minCasts} casts, else from the class profile's rotation
 * (rules.model.spells' "share"); the main attack's from rules.model.mainAttackWeight.
 *
 * @param spells the share of each spell (lower case name), adding up to 1; empty: unknown, all alike
 * @param mainAttack the main attack's weight in the build score (0.5 an even one)
 */
record SpellShares(Map<String, Double> spells, double mainAttack) {

    /** All alike: what the plan used before it knew your spells. */
    static SpellShares even() {
        return new SpellShares(Map.of(), 0.5);
    }

    /** Casts measured before they count over the profile's rotation. A method: hot swaps keep no field values. */
    static int minCasts() {
        return 200;
    }

    /** Yours: measured when there is enough, else the class profile's. */
    static SpellShares current() {
        Map<String, Double> m = measured(Coach.measured());
        if (m.isEmpty()) {
            m = profile(Rules.get().getAsJsonObject("model"));
        }
        return new SpellShares(m, Rules.sub("model", "mainAttackWeight", 0.5));
    }

    /** From measured.json's "spells": casts × damage per cast; empty below {@link #minCasts}. */
    static Map<String, Double> measured(JsonObject m) {
        Map<String, Double> raw = new LinkedHashMap<>();
        int casts = 0;
        if (m != null && m.has("spells") && m.get("spells").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("spells").entrySet()) {
                JsonObject s = e.getValue().getAsJsonObject();
                int c = s.has("casts") ? s.get("casts").getAsInt() : 0;
                double per = s.has("perCast") ? s.get("perCast").getAsDouble() : 0;
                casts += c;
                raw.put(e.getKey().toLowerCase(Locale.ROOT), c * Math.max(0, per));
            }
        }
        return casts < minCasts() ? Map.of() : normalised(raw);
    }

    /** From rules.model.spells' "share" (how often the rotation casts each). */
    static Map<String, Double> profile(JsonObject model) {
        Map<String, Double> raw = new LinkedHashMap<>();
        if (model != null && model.has("spells") && model.get("spells").isJsonArray()) {
            for (JsonElement e : model.getAsJsonArray("spells")) {
                JsonObject s = e.getAsJsonObject();
                if (s.has("name") && s.has("share")) {
                    raw.put(s.get("name").getAsString().toLowerCase(Locale.ROOT), Math.max(0, s.get("share").getAsDouble()));
                }
            }
        }
        return normalised(raw);
    }

    private static Map<String, Double> normalised(Map<String, Double> raw) {
        double sum = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            return Map.of();
        }
        Map<String, Double> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(k, v / sum));
        return out;
    }

    /**
     * How a node for {@code spell} weighs against an even share: 1 when unknown, the spell's share
     * times the number of spells otherwise (a spell you never cast 0.25, your main one up to 3).
     */
    double weight(String spell) {
        if (spells.isEmpty() || spell == null) {
            return 1;
        }
        double s = spells.getOrDefault(spell.toLowerCase(Locale.ROOT), 0.0);
        return Math.max(0.25, Math.min(3, s * spells.size()));
    }

    /** The main attack's weight against an even one (0.5): Summoner's 0.2 is 0.4. */
    double mainAttackWeight() {
        return Math.max(0.2, Math.min(2, mainAttack / 0.5));
    }
}
