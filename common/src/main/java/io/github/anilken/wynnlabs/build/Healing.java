package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The healing a build keeps up, for the Healer goal: the heals of the tree you have (the
 * overlay's, {@link SourceText.Heal}: Mage's Heal 15 % of your max health, Orphion's Pulse two
 * more pulses of 20 % for you alone, Sunflare's regeneration, Shaman's Sacrificial Shrine and
 * Regeneration), raised by Healing Efficiency, times how often the mana lets you cast the heal.
 *
 * <p>Casts: what the pool and the mana back over a fight of rules.model.healWindow seconds (90, a raid
 * boss) pay for at the heal's cost after Intelligence and the cost IDs of its slot, never more than
 * rules.model.maxCastsPer5s allows; a recast cuts Orphion's pulses still to come (the wiki's Mage
 * ability tree), so only those that land before it count. Mana Steal only comes from
 * main attack hits (the wiki's Identifications page), which a healer lands less: it counts at
 * rules.model.healManaSteal (half). Each ally gets what a cast heals them, at most half their max
 * health (the Heal's own text), their max health taken as yours; rules.model.healAllies (3, a
 * party of four) of them. With no heal in the tree (an unread tree, a class without one) a cast
 * heals {@link #FALLBACK_FRACTION} of your max health at {@link #FALLBACK_COST} mana, Mage's plain
 * Heal.
 */
final class Healing {

    private Healing() {}

    /** Mage's Heal: 15 % of your max health, 35 mana; the heal of a tree that names none. */
    static final double FALLBACK_FRACTION = 0.15;
    static final double FALLBACK_COST = 35;

    /**
     * {@code spell} the heal cast (null: none known), {@code cost} its mana after the cuts,
     * {@code casts} a second; health a second for you ({@code self}) and for each ally
     * ({@code perAlly}); {@code value} what the score counts: yours and every ally's.
     */
    record Result(String spell, double cost, double casts, double self, double perAlly, double value) {}

    /**
     * {@code health} your max health, {@code manaPer5} the mana back every 5 s with Mana Steal in
     * it whole, {@code pool} your max mana, {@code intReduction} Intelligence's share off every
     * cost, {@code spells} rules.model.spells as the tree has them, {@code heals} the tree's.
     */
    static Result of(Map<String, Double> s, double health, double manaPer5, double pool, double intReduction,
            JsonArray spells, List<SourceText.Heal> heals) {
        double efficiency = Math.max(0, 1 + s.getOrDefault("healingEfficiency", 0.0) / 100.0);
        // the heal cast: the spell the most per-cast healing comes with
        Map<String, Double> perSpell = new TreeMap<>();
        if (heals != null) {
            for (SourceText.Heal h : heals) {
                if (h.every() <= 0 && h.spell() != null) {
                    perSpell.merge(h.spell(), h.fraction(), Double::sum);
                }
            }
        }
        String spell = null;
        for (var e : perSpell.entrySet()) {
            if (spell == null || e.getValue() > perSpell.get(spell)) {
                spell = e.getKey();
            }
        }
        boolean fallback = heals == null || heals.isEmpty();
        double cost = fallback ? Math.max(1, FALLBACK_COST * (1 - intReduction)) : 0;
        if (spell != null) {
            cost = FALLBACK_COST;
            for (JsonElement el : spells) {
                JsonObject sp = el.isJsonObject() ? el.getAsJsonObject() : null;
                if (sp != null && sp.has("name") && spell.equals(sp.get("name").getAsString())) {
                    cost = Damage.spellCost(s, sp.has("cost") ? sp.get("cost").getAsDouble() : FALLBACK_COST,
                            sp.has("index") ? sp.get("index").getAsInt() : 0, intReduction);
                    break;
                }
            }
        }
        // never faster than the model's cast rate
        double cap = Math.max(0, Damage.model("maxCastsPer5s", 4)) / 5.0;
        double steal = s.getOrDefault("manaSteal", 0.0) * 5.0 / 3.0;
        double perSecond = (manaPer5 - (1 - Damage.model("healManaSteal", 0.5)) * steal) / 5.0;
        double casts = cost > 0 ? casts(cost, perSecond, pool, Damage.model("healWindow", 90), cap) : 0;
        // a recast cuts the pulses still to come: those that land before the next cast count
        double interval = casts > 0 ? 1 / casts : Double.POSITIVE_INFINITY;
        double self = 0;
        double perAlly = 0;
        double allyCap = 0.5 * health;
        if (fallback) {
            double one = FALLBACK_FRACTION * health * efficiency;
            self = one * casts;
            perAlly = Math.min(one, allyCap) * casts;
        } else {
            double allyCast = 0;
            for (SourceText.Heal h : heals) {
                double amount = health * h.fraction() * (h.efficiency() ? efficiency : 1);
                if (h.every() > 0) {
                    self += amount / h.every();
                    if (h.allies()) {
                        perAlly += Math.min(amount, allyCap) / h.every();
                    }
                } else if (spell != null && spell.equals(h.spell())) {
                    self += amount * h.landed(interval) * casts;
                    if (h.allies()) {
                        allyCast += amount * h.landed(interval);
                    }
                }
            }
            perAlly += Math.min(allyCast, allyCap) * casts;
        }
        double value = self + Math.max(0, Damage.model("healAllies", 3)) * perAlly;
        return new Result(spell, cost, casts, self, perAlly, value);
    }

    /**
     * Heal casts a second over a fight of {@code window} seconds: the {@code pool} at the start and
     * {@code perSecond} mana back pay for them at {@code cost} each, at most {@code cap} a second.
     */
    static double casts(double cost, double perSecond, double pool, double window, double cap) {
        if (cost <= 0) {
            return cap;
        }
        double w = Math.max(1, window);
        return Math.max(0, Math.min(cap, (Math.max(0, pool) + Math.max(0, perSecond) * w) / (cost * w)));
    }
}
