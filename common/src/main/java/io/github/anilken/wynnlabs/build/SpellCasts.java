package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The spells' part of {@link Damage#evaluate}: each spell's cast through the damage pipeline (the
 * tree's cuts, waves, extras and one cast per Totem in its conversion), its mana a cast, and how
 * often it is cast: by default at the fixed rate (rules.model.maxCastsPer5s, the spells by their
 * shares), mana or not. With rules.model.castModel 1 the casts follow the mana ({@link Casting}):
 * the damage spells are cast as the mana back a second and the pool over a fight
 * (rules.model.castWindow, 30 s) pay for, best damage per mana first, at most
 * rules.model.castCapPer5s (4) casts every 5 s; a lasting spell (Totem) is recast as it runs out
 * and its mana comes off first. Off by default: its kinks left the road map's chained search up to
 * 7 % behind a fresh one and the comparison panel out of step with its row.
 */
final class SpellCasts {

    private SpellCasts() {}

    /**
     * The spells' numbers: {@code spellDps} with the major IDs' and the tree's factors,
     * {@code spellBase} before them, {@code dmgPerCast} a cast on average, {@code casts} every
     * 5 s, {@code lastingDps} the lasting spells' damage a second, {@code costCut} the share of the
     * cost the build takes off, {@code spells} rules.model.spells as the tree has them,
     * {@code elScale} what the per-element spell sums ({@code spellEl}) are multiplied by to add up
     * to {@code spellDps}.
     */
    record Out(double spellDps, double spellBase, double dmgPerCast, double casts, double lastingDps, double costCut,
            JsonArray spells, double elScale) {}

    /** One damage spell cast in turn: per cast, its mana, its share in the rules, its element parts, its cooldown. */
    private record Cast(double dmg, double cost, double share, double[] el, double cooldown, int part) {}

    static Out of(Map<String, Double> s, Map<String, Double> shares, double baseDps, double[] skillBonus,
            double multiplier, double intReduction, MajorIds.Active major, double manaPer5, double pool,
            double[] spellEl, double[] lastingEl, List<Damage.SpellPart> parts) {
        AbilityMechanics.Sum ab = TreeModel.abilities();
        double lastingDps = 0;
        double lastingMana = 0;
        double cutSum = 0;
        double cutShare = 0;
        double randomSum = 0;
        JsonElement spellsEl = io.github.anilken.wynnlabs.core.Rules.get().has("model")
                ? io.github.anilken.wynnlabs.core.Rules.get().getAsJsonObject("model").get("spells") : null;
        JsonArray spells = TreeModel.spells(spellsEl != null && spellsEl.isJsonArray() ? spellsEl.getAsJsonArray() : new JsonArray());
        List<Cast> casts = new ArrayList<>();
        for (JsonElement e : spells) {
            JsonObject sp = e.getAsJsonObject();
            double share = sp.has("share") ? sp.get("share").getAsDouble() : 1;
            if (share <= 0) {
                continue;
            }
            String name = sp.has("name") ? sp.get("name").getAsString() : "";
            JsonObject conv = sp.getAsJsonObject("conv");
            double[] c = new double[Damage.ELEMENTS.length];
            for (int i = 0; i < Damage.ELEMENTS.length; i++) {
                c[i] = conv.has(Damage.ELEMENTS[i]) ? conv.get(Damage.ELEMENTS[i]).getAsDouble() : 0;
            }
            double hits = sp.has("hits") ? sp.get("hits").getAsDouble() : 1;
            double duration = sp.has("duration") ? sp.get("duration").getAsDouble() : 0;
            boolean lasting = ClassProfiles.lasting(sp) && duration > 0;
            // a major ID's change to this spell (Fissure on Ice Snake, Furious Effigy on Totem)
            List<MajorIds.Target> fx = major.of(name);
            double durationFactor = 1;
            if (fx != null) {
                hits = MajorIds.apply(fx, c, hits);
                durationFactor = lasting ? MajorIds.duration(fx) : 1;
                duration *= durationFactor;
            }
            // the tree's own hits with the spell (Flying Kick with Charge, Backstab for Multihit)
            hits = AbilityMechanics.addExtras(ab, name, c, hits, major);
            double factor = ab.spell() * ab.damage() * ab.spells().getOrDefault(name, 1.0);
            double[] el = spellEl == null ? null : new double[Damage.ELEMENTS.length];
            double dmg = Damage.hit(s, Damage.split(c, shares), baseDps, skillBonus, true, el, Math.max(1, hits)) * factor;
            if (el != null) {
                for (int i = 0; i < el.length; i++) {
                    el[i] *= factor;
                }
            }
            int idx = sp.has("index") ? sp.get("index").getAsInt() : 0;
            double baseCost = sp.has("cost") ? sp.get("cost").getAsDouble() : 30;
            double cost = Damage.spellCost(s, baseCost, idx, intReduction) * ab.cost();
            // what the build takes off this spell's cost (below 0 when an ID raises it), counted
            // by its share: a cut on a spell you do not cast counts nothing. The major IDs' free
            // casts (Transcendence) take their share off; a lasting spell recast twice as often
            // (Furious Effigy) costs twice the mana
            cutSum += share * (1 - cost * major.cost() / durationFactor / Math.max(1, baseCost));
            cutShare += share;
            if (parts != null) {
                // shown as the game takes it, whole mana rounded down: a Totem at 15 with 39
                // Intelligence comes to 11.97 and costs 11. The mana math keeps the decimals, as
                // WynnBuilder's does (a score in steps would stall the road map's search)
                parts.add(new Damage.SpellPart(name.isEmpty() ? Damage.nth(idx) : name, idx, share, dmg * multiplier,
                        lasting, duration, Math.max(1, Math.floor(cost + 1e-9))));
            }
            if (lasting) {
                lastingDps += dmg * multiplier / duration;
                lastingMana += cost * major.cost() / duration;
                if (el != null) {
                    for (int i = 0; i < el.length; i++) {
                        lastingEl[i] += el[i] * multiplier / duration;
                    }
                }
                continue;
            }
            double cooldown = sp.has("cooldown") ? sp.get("cooldown").getAsDouble() : 0;
            casts.add(new Cast(dmg * multiplier, cost * major.cost(), share, el, cooldown,
                    parts == null ? -1 : parts.size() - 1));
            randomSum += dmg * multiplier;
        }
        double costCut = cutShare > 0 ? cutSum / cutShare : 0;
        // Mana Steal comes from main attack hits, fewer while casting: a share of it (rules.model.castManaSteal)
        double steal = s.getOrDefault("manaSteal", 0.0) * 5.0 / 3.0;
        double[] x = rates(casts, manaPer5 - (1 - Damage.model("castManaSteal", 0.5)) * Math.max(0, steal), pool, lastingMana);
        double spellDps = 0;
        double perSecond = 0;
        for (int i = 0; i < casts.size(); i++) {
            Cast k = casts.get(i);
            spellDps += x[i] * k.dmg();
            perSecond += x[i];
            if (k.el() != null) {
                for (int j = 0; j < spellEl.length; j++) {
                    spellEl[j] += x[i] * k.el()[j] * multiplier;
                }
            }
            if (parts != null && k.part() >= 0) {
                // the share the breakdown splits the casts by: this spell's casts a second
                Damage.SpellPart p = parts.get(k.part());
                parts.set(k.part(), new Damage.SpellPart(p.name(), p.index(), x[i], p.perCast(), p.lasting(),
                        p.duration(), p.cost()));
            }
        }
        double dmgPerCast = perSecond > 0 ? spellDps / perSecond : 0;
        double spellBase = spellDps;
        if (major.madness() > 0 && !spells.isEmpty()) {
            spellDps += randomSum / spells.size() / major.madness();
        }
        spellDps *= major.spell();                          // Sorcery's free second cast, Regicide
        double elScale = spellBase > 0 ? spellDps / spellBase : 1;
        return new Out(spellDps, spellBase, dmgPerCast, perSecond * 5, lastingDps, costCut, spells, elScale);
    }

    /** Casts a second per damage spell: by the mana (rules.model.castModel 1), else the old fixed rate by shares. */
    private static double[] rates(List<Cast> casts, double manaPer5, double pool, double lastingMana) {
        int n = casts.size();
        double[] x = new double[n];
        if (n == 0) {
            return x;
        }
        if (Damage.model("castModel", 0) <= 0) {
            double shareSum = 0;
            for (Cast k : casts) {
                shareSum += k.share();
            }
            double rate = Math.max(0, Damage.model("maxCastsPer5s", 4)) / 5.0;
            for (int i = 0; i < n; i++) {
                x[i] = shareSum > 0 ? casts.get(i).share() / shareSum * rate : 0;
            }
            return x;
        }
        double cap = Math.max(0, Damage.model("castCapPer5s", 4)) / 5.0;
        double spam = Damage.model("spellSpamShare", 1.0);
        double window = Math.max(1, Damage.model("castWindow", 30));
        double mana = Math.max(0, manaPer5 / 5.0 + Math.max(0, pool) / window - lastingMana);
        double[] dmg = new double[n];
        double[] cost = new double[n];
        double[] each = new double[n];
        for (int i = 0; i < n; i++) {
            Cast k = casts.get(i);
            dmg[i] = k.dmg();
            cost[i] = Math.max(1, k.cost());
            each[i] = Math.min(cap * Math.max(0.05, Math.min(1, spam)), k.cooldown() > 0 ? 1 / k.cooldown() : cap);
        }
        return Casting.allocate(dmg, cost, cap, mana, each);
    }
}
